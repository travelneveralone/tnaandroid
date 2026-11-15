package com.travelneveralone.tna

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Port of TNAiOS Countries/CountriesMapView + CountriesViewModel (Vinski-style "been" map).

/// Pure rules of the countries map: tap cycle, auto-TNA, counters, filters.
object CountryMapRules {
    /// Map tap: nothing → был → живу → хочу → nothing.
    fun next(current: CountryStatus?): CountryStatus? = when (current) {
        null -> CountryStatus.Visited
        CountryStatus.Visited -> CountryStatus.Lives
        CountryStatus.Lives -> CountryStatus.Wants
        CountryStatus.Wants -> null
    }

    /// A country visited with TNA counts as «был» unless the person set something else.
    fun display(statuses: Map<String, CountryStatus>, tna: Set<String>): Map<String, CountryStatus> =
        statuses + tna.filter { it !in statuses }.associateWith { CountryStatus.Visited }

    fun beenCount(statuses: Map<String, CountryStatus>, tna: Set<String>): Int =
        display(statuses, tna).values.count { it.isBeen }

    fun wantsCount(statuses: Map<String, CountryStatus>): Int = statuses.values.count { it == CountryStatus.Wants }

    /// Search wins over the legend filter; with all statuses on, the whole list shows.
    fun filtered(
        all: List<Pair<String, String>>,
        display: Map<String, CountryStatus>,
        enabled: Set<CountryStatus>,
        search: String,
    ): List<Pair<String, String>> {
        val q = search.trim().lowercase()
        if (q.isNotEmpty()) return all.filter { it.second.lowercase().contains(q) }
        if (enabled.size == CountryStatus.entries.size) return all
        return all.filter { (code, _) -> display[code]?.let { it in enabled } == true }
    }

    val allCountries: List<Pair<String, String>> by lazy {
        val collator = java.text.Collator.getInstance(java.util.Locale("ru"))
        CountryNamesRU.map.entries.map { it.key to it.value }.sortedWith(compareBy(collator) { it.second })
    }
}

private val ocean = Color.hex(0x9FC7DD).copy(alpha = 0.35f)
private val land = Color.hex(0xD8D0C2)

/// Equirectangular world map (2:1). Status colours the fill; TNA countries get a white outline.
@Composable
fun WorldMapCanvas(
    statuses: Map<String, CountryStatus>,
    tna: Set<String>,
    modifier: Modifier = Modifier,
    onTap: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var countries by remember { mutableStateOf<List<GeoCountry>>(emptyList()) }
    LaunchedEffect(Unit) { countries = withContext(Dispatchers.Default) { WorldAtlas.load(context) } }
    val shape = RoundedCornerShape(16.dp)
    Canvas(
        modifier.fillMaxWidth().aspectRatio(2f).clip(shape).background(ocean).border(1.dp, TnaColor.stroke, shape)
            .pointerInput(countries, onTap) {
                if (onTap == null) return@pointerInput
                detectTapGestures { p ->
                    WorldAtlas.countryAt(countries, p.x, p.y, size.width.toFloat(), size.height.toFloat())?.let(onTap)
                }
            },
    ) {
        countries.forEach { country ->
            val path = Path()
            country.rings.forEach { ring ->
                for (i in ring.indices step 2) {
                    val x = (ring[i] + 180f) / 360f * size.width
                    val y = (90f - ring[i + 1]) / 180f * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            drawPath(path, statuses[country.code]?.let { Color.hex(it.color) } ?: land)
            drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(0.3.dp.toPx()))
            if (country.code in tna) drawPath(path, Color.White, style = Stroke(1.4.dp.toPx()))
        }
    }
}

/// Map with the expand button; legend filters are applied by the caller.
@Composable
private fun ExpandableMap(statuses: Map<String, CountryStatus>, tna: Set<String>, onTap: ((String) -> Unit)?) {
    var full by remember { mutableStateOf(false) }
    Box {
        WorldMapCanvas(statuses, tna, onTap = onTap)
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f)).border(1.dp, TnaColor.stroke, CircleShape)
                .pressable(0.9f) { full = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.OpenInFull, "На весь экран", tint = TnaColor.ink, modifier = Modifier.size(15.dp)) }
    }
    if (full) CountryMapFullScreen(statuses, tna) { full = false }
}

@Composable
private fun StatsRow(statuses: Map<String, CountryStatus>, tna: Set<String>) {
    val wants = CountryMapRules.wantsCount(statuses)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatChip("Стран", CountryMapRules.beenCount(statuses, tna), TnaColor.ink, Modifier.weight(1f))
        StatChip("С TNA", tna.size, TnaColor.accent, Modifier.weight(1f))
        if (wants > 0) StatChip("Хочу", wants, Color.hex(CountryStatus.Wants.color), Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(title: String, value: Int, color: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$value", style = displayStyle(24, color))
        Text(title, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/// Someone else's map in their mini-profile: counters + read-only map.
@Composable
fun CountriesCompact(statuses: Map<String, CountryStatus>, tna: Set<String>) {
    val display = CountryMapRules.display(statuses, tna)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatsRow(statuses, tna)
        ExpandableMap(display, tna, onTap = null)
    }
}

@Composable
fun CountriesSheet(app: AppModel) {
    var search by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(CountryStatus.entries.toSet()) }
    var showTna by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { app.loadCountries() }
    fun set(code: String, next: CountryStatus?) {
        val c = code.uppercase()
        app.countries = if (next == null) app.countries - c else app.countries + (c to next)
        app.launch { app.repo.setCountry(c, next) }
    }
    val display = CountryMapRules.display(app.countries, app.tnaCountryCodes)
    val mapStatuses = display.filterValues { it in enabled }
    val mapTna = if (showTna) app.tnaCountryCodes else emptySet()
    SheetColumn(spacing = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("МОИ СТРАНЫ", style = displayStyle(28))
            Text(
                "Отмечай, где живёшь, где был и куда хочешь — нажми на страну на карте или в списке. Кнопка в углу карты открывает её на весь экран с зумом.",
                color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            )
        }
        ExpandableMap(mapStatuses, mapTna) { code -> set(code, CountryMapRules.next(app.countries[code.uppercase()])) }
        StatsRow(app.countries, app.tnaCountryCodes)
        // Legend doubles as a filter: tap a chip to hide that status (dimmed = hidden).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountryStatus.entries.forEach { s ->
                val on = s in enabled
                LegendChip(s.title, on, if (on) Color.hex(s.color).copy(alpha = 0.14f) else TnaColor.surface, {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color.hex(s.color)))
                }) { enabled = if (on) enabled - s else enabled + s }
            }
            LegendChip("с TNA", showTna, TnaColor.surface, {
                Box(Modifier.size(10.dp).clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.5f)).border(1.5.dp, Color.White, CircleShape))
            }) { showTna = !showTna }
        }
        SearchBox(search) { search = it }
        CountryMapRules.filtered(CountryMapRules.allCountries, display, enabled, search).forEach { (code, name) ->
            CountryRow(code, name, app.countries[code], display[code], code in app.tnaCountryCodes) { set(code, it) }
        }
    }
}

@Composable
private fun LegendChip(title: String, on: Boolean, fill: Color, dot: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.pressable(0.95f, onClick = onClick).clip(CircleShape).background(fill).border(1.dp, TnaColor.stroke, CircleShape)
            .graphicsLayer { alpha = if (on) 1f else 0.5f }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dot()
        Spacer(Modifier.width(5.dp))
        Text(title, color = TnaColor.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sfIcon("magnifyingglass"), null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Поиск страны", color = TnaColor.subtle.copy(alpha = 0.6f), fontSize = 16.sp)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = TnaColor.ink, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TnaColor.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CountryRow(code: String, name: String, status: CountryStatus?, shown: CountryStatus?, tna: Boolean, onSet: (CountryStatus?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().pressable(0.99f) { menu = true }.clip(shape).background(TnaColor.surface)
                .border(1.dp, TnaColor.stroke, shape).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(shown?.let { Color.hex(it.color) } ?: TnaColor.subtle.copy(alpha = 0.25f)))
            Spacer(Modifier.width(10.dp))
            Text(name, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (tna) {
                Spacer(Modifier.width(6.dp))
                Text("TNA", color = TnaColor.accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(shown?.title.orEmpty(), color = shown?.let { Color.hex(it.color) } ?: TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = TnaColor.surface) {
            CountryStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.title) },
                    leadingIcon = { if (status == s) Icon(Icons.Filled.Check, null, tint = TnaColor.accent) else Box(Modifier.size(10.dp).clip(CircleShape).border(1.5.dp, TnaColor.subtle, CircleShape)) },
                    onClick = { menu = false; onSet(if (status == s) null else s) },
                )
            }
            if (status != null) {
                DropdownMenuItem(
                    text = { Text("Убрать", color = TnaColor.danger) },
                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = TnaColor.danger) },
                    onClick = { menu = false; onSet(null) },
                )
            }
        }
    }
}

/// Full-screen, pinch-to-zoom / drag-to-pan world map. Double-tap resets.
@Composable
fun CountryMapFullScreen(statuses: Map<String, CountryStatus>, tna: Set<String>, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) {
                WorldMapCanvas(statuses, tna)
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)).pressable(0.9f, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
