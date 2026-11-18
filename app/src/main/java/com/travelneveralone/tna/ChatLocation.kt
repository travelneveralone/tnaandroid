package com.travelneveralone.tna

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

// Location in chats - the Android side of iOS LocationService + LiveLocationSharer.
//
// A live location must keep moving after the chat screen closes and while the
// app is in the background. On Android that means a foreground service of
// type "location" with an ongoing notification ("stop" right there). The
// sessions live in LiveLocationHub (process-wide, persisted), the service only
// feeds them device fixes and ends them at their `until`.

/// Runtime permission check for coarse or fine location.
fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/// One current fix: a fresh cached one if there is, otherwise the first update
/// from GPS or the network within 15 s, otherwise the last known one.
object DeviceLocation {
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): GeoPoint? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null && System.currentTimeMillis() - cached.time < 15_000 && cached.accuracy <= 100f) {
            return cached.toPoint()
        }
        if (providers.isEmpty()) return cached?.toPoint()
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                providers.forEach { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return (fresh ?: cached)?.toPoint()
    }
}

fun Location.toPoint() = GeoPoint(latitude, longitude, if (hasAccuracy()) accuracy.toDouble() else null, Instant.ofEpochMilli(time))

/// Process-wide owner of the caller's running live locations (iOS LiveLocationSharer).
object LiveLocationHub {
    data class Session(val messageId: UUID, val chatId: UUID, val until: Instant)

    /// Set by AppModel; the service talks to the backend through it.
    var repo: TnaRepository? = null
    var sessions by mutableStateOf<List<Session>>(emptyList())
        private set

    internal val lastSent = mutableMapOf<UUID, GeoPoint>()
    internal val inFlight = mutableSetOf<UUID>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private const val PREFS = "tna.live"
    private const val KEY = "sessions"

    fun session(chatId: UUID?): Session? = sessions.firstOrNull { it.chatId == chatId }
    fun isSharing(messageId: UUID): Boolean = sessions.any { it.messageId == messageId }

    /// Starts tracking a just-sent live location. One per chat: an older one in
    /// the same chat is stopped (on the server too).
    fun start(context: Context, messageId: UUID, chatId: UUID, until: Instant, from: GeoPoint?) {
        session(chatId)?.takeIf { it.messageId != messageId }?.let { old ->
            scope.launch { stop(context, old.messageId) }
        }
        sessions = (sessions.filter { it.messageId != messageId } + Session(messageId, chatId, until)).sortedBy { it.until }
        if (from != null) lastSent[messageId] = from
        save(context)
        LiveLocationService.ensure(context)
    }

    /// Ends a session now - locally and on the server.
    suspend fun stop(context: Context, messageId: UUID) {
        end(context, messageId)
        repo?.stopLiveLocation(messageId)
    }

    fun end(context: Context, messageId: UUID) {
        sessions = sessions.filter { it.messageId != messageId }
        lastSent.remove(messageId)
        inFlight.remove(messageId)
        save(context)
        if (sessions.isEmpty()) LiveLocationService.stop(context)
    }

    /// On launch: drop what expired while the app was gone, restart the rest.
    fun resume(context: Context) {
        val now = Instant.now()
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        sessions = raw.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 3) return@mapNotNull null
            runCatching { Session(UUID.fromString(p[0]), UUID.fromString(p[1]), Instant.ofEpochMilli(p[2].toLong())) }.getOrNull()
        }.filter { it.until.isAfter(now) }.sortedBy { it.until }
        save(context)
        if (sessions.isNotEmpty() && hasLocationPermission(context)) LiveLocationService.ensure(context)
    }

    /// Drops sessions past their end.
    fun pruneExpired(context: Context, now: Instant = Instant.now()) {
        sessions.filter { !it.until.isAfter(now) }.forEach { end(context, it.messageId) }
    }

    /// Fans one device fix out to every session that is due for it.
    suspend fun handle(context: Context, point: GeoPoint) {
        pruneExpired(context)
        val repo = repo ?: return
        for (session in sessions) {
            val id = session.messageId
            if (id in inFlight || !LiveThrottle.shouldSend(point, lastSent[id])) continue
            inFlight += id
            val verdict = repo.updateLiveLocation(id, point)
            inFlight -= id
            when (verdict) {
                true -> lastSent[id] = point
                false -> end(context, id)          // over on the server
                null -> Unit                       // no answer - next fix retries
            }
        }
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, sessions.joinToString("\n") { "${it.messageId}|${it.chatId}|${it.until.toEpochMilli()}" })
            .apply()
    }
}

/// Foreground service that keeps live locations moving in the background.
class LiveLocationService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lm: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!startInForeground()) { stopSelf(); return }
        requestUpdates()
        // Expiry + notification refresh. Fixes stop arriving while standing still,
        // so the end time is checked on a clock, not on location callbacks.
        scope.launch {
            while (true) {
                LiveLocationHub.pruneExpired(this@LiveLocationService)
                if (LiveLocationHub.sessions.isEmpty()) { stopSelf(); break }
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
                delay(30_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val ids = LiveLocationHub.sessions.map { it.messageId }
            scope.launch {
                ids.forEach { LiveLocationHub.stop(this@LiveLocationService, it) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lm?.removeUpdates(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val point = location.toPoint()
        scope.launch { LiveLocationHub.handle(this@LiveLocationService, point) }
    }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        if (!hasLocationPermission(this)) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = manager
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                runCatching { manager.requestLocationUpdates(provider, 5_000L, 15f, this, Looper.getMainLooper()) }
            }
        }
    }

    /// false when Android refuses (no location permission any more).
    private fun startInForeground(): Boolean {
        ensureChannel()
        return runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
    }

    private fun buildNotification(): Notification {
        val soonest = LiveLocationHub.sessions.firstOrNull()
        val text = soonest?.let { "Участники чата видят, где ты · ${LocationText.remaining(it.until)}" }
            ?: "Участники чата видят, где ты"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LiveLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Ты транслируешь геопозицию")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Остановить", stop)
            .build()
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Трансляция геопозиции", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "live_location"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.travelneveralone.tna.STOP_LIVE_LOCATION"

        fun ensure(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveLocationService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveLocationService::class.java))
        }
    }
}

/// osmdroid needs a user agent (OSM tile policy) and a cache folder once per process.
object OsmSetup {
    @Volatile private var done = false

    fun ensure(context: Context) {
        if (done) return
        done = true
        val config = org.osmdroid.config.Configuration.getInstance()
        config.load(context, context.getSharedPreferences("tna.osm", Context.MODE_PRIVATE))
        config.userAgentValue = BuildConfig.APPLICATION_ID
        val base = File(context.cacheDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
    }
}
