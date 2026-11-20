package com.travelneveralone.tna

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.Instant
import java.util.UUID

// Location in chats - UI (iOS LocationBubble, LocationShareSheet, ChatMapView).
// Maps are OpenStreetMap through osmdroid: no Google Play services, no API key.

/// A marker on a map: initial letter in a circle (accent while live).
data class MapPin(val id: UUID, val lat: Double, val lng: Double, val label: String, val live: Boolean)

/// Re-ticks every 20 s so "updated N min ago" and "N min left" stay true.
@Composable
private fun rememberNow(key: Any?): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(key) {
        while (true) {
            delay(20_000)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun OsmMap(
    modifier: Modifier,
    center: Pair<Double, Double>?,
    zoom: Double,
    pins: List<MapPin>,
    interactive: Boolean,
    showMe: Boolean = false,
    /// The map recentres on `center` only when this key changes (not on every move).
    recenterKey: Any? = center,
    onPinTap: (UUID) -> Unit = {},
) {
    val context = LocalContext.current
    remember { OsmSetup.ensure(context.applicationContext) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(interactive)
                isClickable = interactive
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                if (showMe && hasLocationPermission(ctx)) {
                    overlays.add(MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        if (center == null) enableFollowLocation()
                    })
                }
                overlays.add(CopyrightOverlay(ctx))
                onResume()
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            pins.forEach { pin ->
                map.overlays.add(Marker(map).apply {
                    position = org.osmdroid.util.GeoPoint(pin.lat, pin.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = pinIcon(map, pin)
                    title = pin.label
                    setOnMarkerClickListener { _, _ -> onPinTap(pin.id); true }
                })
            }
            if (center != null && map.tag != recenterKey) {
                map.controller.setCenter(org.osmdroid.util.GeoPoint(center.first, center.second))
                map.tag = recenterKey
            }
            map.invalidate()
        },
        onRelease = { map ->
            map.onPause()
            map.onDetach()
        },
    )
}

/// Circle with the author's initial, white ring; accent while live.
private fun pinIcon(map: MapView, pin: MapPin): BitmapDrawable {
    val density = map.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(r, r, r, paint)
    paint.color = (if (pin.live) TnaColor.accent else TnaColor.subtle).toArgb()
    canvas.drawCircle(r, r, r - 2.5f * density, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 15 * density
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    val letter = pin.label.trim().take(1).uppercase().ifEmpty { "?" }
    canvas.drawText(letter, r, r - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(map.resources, bitmap)
}

// MARK: - Bubble

@Composable
fun LocationBubble(
    message: Message,
    canStop: Boolean,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onLongPress: () -> Unit,
) {
    val loc = message.location ?: return
    val now = rememberNow(message.id)
    val live = loc.isLive(now)
    val (title, subtitle) = LocationText.status(loc, now)
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.width(260.dp).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)) {
            Box(Modifier.fillMaxWidth().height(140.dp)) {
                OsmMap(
                    Modifier.fillMaxSize(),
                    center = loc.lat to loc.lng,
                    zoom = 16.0,
                    pins = listOf(MapPin(message.id, loc.lat, loc.lng, message.author, live)),
                    interactive = false,
                )
                // Catches taps above the map view: the map itself stays still in the list.
                Box(Modifier.fillMaxSize().pressableLong(onClick = onOpen, onLongClick = onLongPress))
            }
            Row(
                Modifier.fillMaxWidth().pressableLong(onClick = onOpen, onLongClick = onLongPress).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    when {
                        live -> Icons.Filled.MyLocation
                        loc.isLiveKind -> Icons.Filled.LocationOff
                        else -> Icons.Filled.LocationOn
                    },
                    null,
                    tint = if (live) TnaColor.accent else TnaColor.subtle,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle?.let { "$it · ${message.time}" } ?: message.time,
                        color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (live && canStop) {
                    Text(
                        "Остановить",
                        color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.12f))
                            .pressable(onClick = onStop).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Share sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareSheet(app: AppModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<GeoPoint?>(null) }
    var choosePeriod by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocationPermission(context)
        asked = true
    }
    // The ongoing "you are sharing" notification needs this on Android 13+;
    // without it the sharing still runs, just without the notification.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!granted) permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(granted) { if (granted) me = DeviceLocation.current(context) }
    val live = LiveLocationHub.session(app.openChat?.id)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TnaColor.bg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Отмена", color = TnaColor.subtle, fontSize = 16.sp, modifier = Modifier.pressable(onClick = onDismiss))
                Text("Геопозиция", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(56.dp))
            }
            Box(Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)) {
                if (granted) {
                    val point = me
                    OsmMap(
                        Modifier.fillMaxSize(),
                        center = point?.let { it.lat to it.lng },
                        zoom = 16.0,
                        pins = emptyList(),
                        interactive = true,
                        showMe = true,
                    )
                    if (point == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = TnaColor.accent)
                } else {
                    Box(Modifier.fillMaxSize().background(TnaColor.elevated))
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!granted && asked) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Нет доступа к геопозиции", color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Разреши его в настройках, чтобы отправлять свою точку в чат.", color = TnaColor.subtle, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Text("Открыть настройки", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        })
                    }
                } else {
                    ShareRow(Icons.Filled.LocationOn, "Отправить мою геопозицию", "Одна точка - где ты сейчас", filled = true, busy = app.locationBusy, enabled = granted) {
                        app.sendLocation(null)
                        onDismiss()
                    }
                    if (live != null) {
                        ShareRow(Icons.Filled.LocationOff, "Остановить трансляцию", LocationText.remaining(live.until), filled = false, enabled = true) {
                            app.stopLiveLocation(live.messageId)
                            onDismiss()
                        }
                    } else {
                        ShareRow(Icons.Filled.MyLocation, "Транслировать геопозицию", "15 минут, час или 8 часов", filled = false, enabled = granted && !app.locationBusy) {
                            choosePeriod = true
                        }
                    }
                }
            }
        }
    }

    if (choosePeriod) {
        AlertDialog(
            onDismissRequest = { choosePeriod = false },
            containerColor = TnaColor.surface,
            title = { Text("Транслировать геопозицию", color = TnaColor.ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Участники чата будут видеть, где ты, пока идёт трансляция - даже если приложение свёрнуто.", color = TnaColor.subtle, fontSize = 14.sp)
                    LivePeriod.entries.forEach { period ->
                        TextButton(onClick = {
                            choosePeriod = false
                            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            app.sendLocation(period)
                            onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(period.title, color = TnaColor.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosePeriod = false }) { Text("Отмена", color = TnaColor.subtle) } },
        )
    }
}

@Composable
private fun ShareRow(icon: ImageVector, title: String, subtitle: String, filled: Boolean, busy: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
            .pressable(0.98f, enabled = enabled && !busy, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (filled) TnaColor.accent else TnaColor.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (filled) Color.White else TnaColor.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TnaColor.ink.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TnaColor.subtle, fontSize = 13.sp)
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = TnaColor.accent, strokeWidth = 2.dp)
    }
}

// MARK: - Chat map

@Composable
fun ChatMapDialog(app: AppModel, focus: Message?, authorName: (Message) -> String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val now = rememberNow(focus?.id)
    val currentFocus = focus?.let { f -> app.messages.firstOrNull { it.id == f.id } ?: f }
    val points = chatMapPoints(app.messages, currentFocus, now)
    var selected by remember { mutableStateOf(focus?.id ?: points.firstOrNull()?.id) }
    val start = remember { (currentFocus ?: points.firstOrNull())?.location }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(TnaColor.bg)) {
            OsmMap(
                Modifier.fillMaxSize(),
                center = start?.let { it.lat to it.lng },
                zoom = 15.0,
                pins = points.mapNotNull { m ->
                    m.location?.let { MapPin(m.id, it.lat, it.lng, if (m.isMine) "Я" else authorName(m), it.isLive(now)) }
                },
                interactive = true,
                showMe = true,
                recenterKey = "start",
                onPinTap = { selected = it },
            )
            Row(
                Modifier.fillMaxWidth().background(TnaColor.surface.copy(alpha = 0.96f)).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(56.dp))
                Text("Геопозиция", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Готово", color = TnaColor.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable(onClick = onDismiss))
            }
            val m = points.firstOrNull { it.id == selected }
            val loc = m?.location
            if (m != null && loc != null) {
                val (title, subtitle) = LocationText.status(loc, now)
                val mineLive = m.isMine && loc.isLive(now) && LiveLocationHub.isSharing(m.id)
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)).background(TnaColor.surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (m.isMine) "Ты" else authorName(m), color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(title, subtitle).joinToString(" · "), color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Directions for every point except my own running broadcast;
                        // my one-off pin (hotel, bus) still gets them - same as iOS.
                        if (!mineLive) {
                            MapButton(Icons.Filled.Directions, "Маршрут", filled = true, modifier = Modifier.weight(1f)) {
                                val uri = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}")
                                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Маршрут")) }
                            }
                        }
                        if (mineLive) {
                            MapButton(Icons.Filled.LocationOff, "Остановить трансляцию", filled = false, modifier = Modifier.weight(1f)) {
                                app.stopLiveLocation(m.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, title: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (filled) TnaColor.accent else TnaColor.accent.copy(alpha = 0.12f))
            .pressable(0.97f, onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (filled) Color.White else TnaColor.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = if (filled) Color.White else TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Banner

/// Above the composer while anyone in this chat shares a live location.
@Composable
fun LiveLocationBanner(app: AppModel, chatId: UUID, authorName: (Message) -> String, onOpenMap: () -> Unit) {
    val now = rememberNow(chatId)
    val mine = LiveLocationHub.session(chatId)?.takeIf { it.until.isAfter(now) }
    val others = chatMapPoints(app.messages, null, now).filter { !it.isMine }
    if (mine == null && others.isEmpty()) return
    val text = if (mine != null) {
        val left = LocationText.remaining(mine.until, now)
        if (others.isEmpty()) "Ты транслируешь геопозицию · $left" else "Ты и ещё ${others.size} на карте · $left"
    } else {
        val first = authorName(others[0])
        if (others.size == 1) "$first транслирует геопозицию" else "$first и ещё ${others.size - 1} на карте"
    }
    Row(
        Modifier.fillMaxWidth().pressable(0.99f, onClick = onOpenMap).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.MyLocation, null, tint = TnaColor.accent, modifier = Modifier.size(16.dp))
        Text(text, color = TnaColor.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (mine != null) {
            Text("Стоп", color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { app.stopLiveLocation(mine.messageId) })
        }
    }
}
