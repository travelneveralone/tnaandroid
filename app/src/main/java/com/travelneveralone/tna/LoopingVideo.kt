package com.travelneveralone.tna

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// Ports of TNAiOS Catalog/FeaturedVideoPolicy, Core/DesignSystem/LoopingVideo,
// Core/Services/TourVideoCache: a silent looping clip over a carousel card.

/// When a «Ближайшие заезды» card may show and run its clip.
data class FeaturedVideoPolicy(
    /// Tours tab selected and nothing (tour, chat, sheet, full-screen cover) on top.
    val screenOnTop: Boolean,
    /// Off when the person removed animations in accessibility settings.
    val systemAllowsAutoplay: Boolean,
    val lowPowerMode: Boolean,
) {
    /// With autoplay off the card keeps its photo: a frozen first frame looks worse.
    val mountsClip: Boolean get() = systemAllowsAutoplay && !lowPowerMode
    val mayPlay: Boolean get() = mountsClip && screenOnTop

    companion object {
        fun system(context: Context, screenOnTop: Boolean): FeaturedVideoPolicy {
            val scale = runCatching {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            }.getOrDefault(1f)
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return FeaturedVideoPolicy(screenOnTop, scale > 0f, power?.isPowerSaveMode == true)
        }
    }
}

object VideoGeometry {
    /// A card must show at least this much of itself to play: 290-dp cards with a
    /// 16-dp gap can't both reach 75 %, so even mid-swipe only one loop runs.
    const val VISIBLE_THRESHOLD = 0.75f

    /// Share of a w×h box that is visible, 0…1.
    fun visibleFraction(width: Float, height: Float, visibleWidth: Float, visibleHeight: Float): Float {
        val area = width * height
        if (area <= 0f) return 0f
        return (maxOf(visibleWidth, 0f) * maxOf(visibleHeight, 0f) / area).coerceIn(0f, 1f)
    }

    /// TextureView stretches the video to the view; this scale turns it into aspect-fill.
    fun fillScale(viewW: Float, viewH: Float, videoW: Float, videoH: Float): Pair<Float, Float> {
        if (viewW <= 0f || viewH <= 0f || videoW <= 0f || videoH <= 0f) return 1f to 1f
        val s = maxOf(viewW / videoW, viewH / videoH)
        return (videoW * s / viewW) to (videoH * s / viewH)
    }
}

/// Disk cache for tour clips: one file per remote URL, named by its SHA-256, in the
/// app cache (the OS may purge it - the card then shows its photo until the next download).
class TourVideoCache(private val directory: File) {
    // Own client with honest timeouts: a clip is decoration and must never block anything.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<File?>>()

    init { directory.mkdirs() }

    fun cachedFile(remote: String): File? = file(remote).takeIf { it.exists() && it.length() > 0 }

    /// Two cards (or a reload mid-download) never fetch the same clip twice.
    suspend fun download(remote: String): File? = coroutineScope {
        cachedFile(remote)?.let { return@coroutineScope it }
        val job = lock.withLock {
            inFlight.getOrPut(remote) { async(Dispatchers.IO) { fetch(remote) } }
        }
        val result = job.await()
        lock.withLock { inFlight.remove(remote) }
        result
    }

    fun prune(keeping: Set<String>) {
        val keep = keeping.map(::fileName).toSet()
        // A clip still downloading lives in a .part file, so nothing in flight is hit.
        directory.listFiles()?.filter { it.name !in keep && !it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private fun file(remote: String) = File(directory, fileName(remote))

    private fun fetch(remote: String): File? = runCatching {
        val destination = file(remote)
        val temp = File(directory, destination.name + ".part")
        client.newCall(Request.Builder().url(remote).build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body ?: return@runCatching null
            temp.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        if (!temp.renameTo(destination)) {
            temp.delete()
            return@runCatching null
        }
        destination
    }.getOrNull()

    companion object {
        /// Stable across launches; keeps the container extension.
        fun fileName(remote: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(remote.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val ext = remote.substringBefore('?').substringAfterLast('.', "").lowercase()
            return "$digest.${if (ext in listOf("mp4", "mov", "m4v")) ext else "mp4"}"
        }
    }
}

/// A silent, endlessly looping clip from a local file. Transparent until the first
/// frame is on screen, so the cover photo shows through and the clip fades in over it.
/// Plays only while [mayPlay], the activity is resumed and the view is mostly visible.
@Composable
fun LoopingVideo(file: File, mayPlay: Boolean, modifier: Modifier = Modifier) {
    var fraction by remember { mutableFloatStateOf(0f) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val player = remember(file) { LoopingPlayer(file) }
    DisposableEffect(player) { onDispose { player.release() } }
    val alpha = remember(file) { Animatable(0f) }
    LaunchedEffect(player) {
        player.firstFrame.await()
        alpha.animateTo(1f, tween(400))
    }
    val shouldRun = mayPlay && resumed && fraction >= VideoGeometry.VISIBLE_THRESHOLD
    LaunchedEffect(player, shouldRun) { player.setRunning(shouldRun) }
    AndroidView(
        factory = { context -> TextureView(context).also { player.attach(it) } },
        modifier = modifier
            .alpha(alpha.value)
            .onGloballyPositioned { coords ->
                // boundsInWindow is already clipped by the LazyRow and the screen.
                val visible = coords.boundsInWindow()
                fraction = VideoGeometry.visibleFraction(
                    coords.size.width.toFloat(), coords.size.height.toFloat(), visible.width, visible.height,
                )
            },
    )
}

/// MediaPlayer on a TextureView, muted. MediaPlayer never requests audio focus by
/// itself, so a looping card does not stop the person's music or podcast.
private class LoopingPlayer(private val file: File) : TextureView.SurfaceTextureListener {
    private var view: TextureView? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var wantRun = false
    val firstFrame = CompletableDeferred<Unit>()

    fun attach(textureView: TextureView) {
        view = textureView
        textureView.surfaceTextureListener = this
        textureView.surfaceTexture?.takeIf { textureView.isAvailable }?.let(::open)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = open(texture)
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = applyCrop()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    private fun open(texture: SurfaceTexture) {
        if (player != null) return
        val s = Surface(texture)
        surface = s
        player = runCatching {
            MediaPlayer().apply {
                setSurface(s)
                setVolume(0f, 0f)
                isLooping = true
                setScreenOnWhilePlaying(false)
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    prepared = true
                    applyCrop()
                    if (wantRun) runCatching { mp.start() }
                }
                setOnVideoSizeChangedListener { _, _, _ -> applyCrop() }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) firstFrame.complete(Unit)
                    false
                }
                // A broken clip just stays invisible over the photo.
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        }.getOrNull()
    }

    fun setRunning(run: Boolean) {
        wantRun = run
        val p = player ?: return
        if (!prepared) return
        runCatching { if (run && !p.isPlaying) p.start() else if (!run && p.isPlaying) p.pause() }
    }

    private fun applyCrop() {
        val v = view ?: return
        val p = player ?: return
        if (!prepared) return
        val (sx, sy) = VideoGeometry.fillScale(v.width.toFloat(), v.height.toFloat(), p.videoWidth.toFloat(), p.videoHeight.toFloat())
        v.setTransform(Matrix().apply { setScale(sx, sy, v.width / 2f, v.height / 2f) })
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        prepared = false
        surface?.release()
        surface = null
    }
}
