package com.travelneveralone.tna

import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Port of TNAiOS Features/Passport (PassportView + Views/PassportComponents).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(app: AppModel) {
    LaunchedEffect(app.account.isRegistered, app.account.name) { app.loadPassport() }
    // The passport is personal: hidden entirely until the traveller signs in.
    if (!app.account.isRegistered) {
        PassportLocked { app.requireAccount {} }
        return
    }
    val data = app.passport
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Achievement?>(null) }
    var allShown by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; app.loadPassport().join(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(TnaColor.bg),
    ) {
        if (!data.isLoaded) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth().height(110.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(240.dp))
                SkeletonBlock(Modifier.fillMaxWidth().height(180.dp))
            }
            return@PullToRefreshBox
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item { PassportHeader(app, data) }
            item { PassportMap(data.places) }
            item { StatsGrid(data.stats) }
            item { LevelCard(data) }
            item { StampsSection(data.stamps) }
            item { AchievementsSection(data.achievements, onOpen = { selected = it }, onAll = { allShown = true }) }
            item { LeaderboardSection(data.leaderboard) { entry -> entry.userId?.let { app.openMiniProfile(MiniProfile(it, entry.name, avatarUrl = entry.avatarUrl)) } } }
        }
    }
    selected?.let { AchievementDetailSheet(it) { selected = null } }
    if (allShown) AllAchievementsSheet(data.achievements) { allShown = false }
}

@Composable
private fun PassportLocked(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(TnaColor.bg).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Badge, null, tint = TnaColor.accent, modifier = Modifier.size(56.dp))
        Gap(20)
        Text("ПАСПОРТ", style = displayStyle(34))
        Text("ПУТЕШЕСТВЕННИКА", style = displayBrushStyle(22))
        Gap(8)
        Text(
            "Войди, чтобы открыть свою карту покорений, штампы, уровень, достижения и рейтинг.",
            color = TnaColor.subtle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Gap(20)
        CapsuleButton("Войти / регистрация", Icons.Filled.PersonAdd, onClick = onLogin)
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun PassportHeader(app: AppModel, data: PassportData) {
    val name = app.account.name.ifBlank { data.userName }
    var sharing by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ПАСПОРТ", style = displayStyle(40), modifier = Modifier.weight(1f))
            app.recap?.let { recap ->
                Row(
                    Modifier.pressable { app.recapShown = true }.clip(CircleShape).background(TnaColor.accentBrush)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Итоги ${recap.year}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            CircleIconButton(Icons.Filled.Share) { sharing = true }
        }
        Text("ПУТЕШЕСТВЕННИКА", style = displayBrushStyle(26))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(app.avatarUrl, name, 28, mine = true)
            Spacer(Modifier.width(8.dp))
            Text(name, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (data.memberSince.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text("· ${data.memberSince}", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
    if (sharing) ShareCardDialog("Мой паспорт TNA", onDismiss = { sharing = false }) { PassportShareCard(data, name) }
}

/// Visited places on a flat world map cropped to fit them (iOS: MapKit; same framing rule).
@Composable
private fun PassportMap(places: List<VisitedPlace>) {
    val context = LocalContext.current
    var countries by remember { mutableStateOf<List<GeoCountry>>(emptyList()) }
    LaunchedEffect(Unit) { countries = withContext(Dispatchers.Default) { WorldAtlas.load(context) } }
    val region = remember(places) { MapRegion.fitting(places) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(240.dp).clip(shape).border(1.dp, TnaColor.stroke, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.hex(0xDCE8F1))
            fun project(lon: Float, lat: Float): Offset = Offset(
                ((lon - region.minLon) / region.lonSpan * size.width).toFloat(),
                ((1 - (lat - region.minLat) / region.latSpan) * size.height).toFloat(),
            )
            countries.forEach { country ->
                country.rings.forEach { ring ->
                    val path = Path()
                    var i = 0
                    while (i < ring.size) {
                        val p = project(ring[i], ring[i + 1])
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        i += 2
                    }
                    path.close()
                    drawPath(path, Color.hex(0xF7F4EE))
                    drawPath(path, Color.hex(0xC9D3DC), style = Stroke(1f))
                }
            }
            places.forEach { place ->
                val c = project(place.longitude.toFloat(), place.latitude.toFloat())
                val color = Color.hex(place.color)
                drawCircle(color.copy(alpha = 0.3f), radius = 13.dp.toPx(), center = c)
                drawCircle(Color.White, radius = 7.dp.toPx(), center = c)
                drawCircle(color, radius = 5.dp.toPx(), center = c)
            }
        }
        if (places.isNotEmpty()) {
            Text(
                "${places.size} ${pluralRU(places.size, "точка", "точки", "точек")} покорено",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/// Same framing as iOS `region(fitting:)`: Russia by default, 1.6× margin, ≥ 4° span.
data class MapRegion(val minLon: Double, val minLat: Double, val lonSpan: Double, val latSpan: Double) {
    companion object {
        fun fitting(places: List<VisitedPlace>): MapRegion {
            val first = places.firstOrNull() ?: return centered(52.0, 96.0, 60.0, 120.0)
            if (places.size == 1) return centered(first.latitude, first.longitude, 6.0, 6.0)
            val lats = places.map { it.latitude }
            val lons = places.map { it.longitude }
            return centered(
                (lats.min() + lats.max()) / 2,
                (lons.min() + lons.max()) / 2,
                maxOf((lats.max() - lats.min()) * 1.6, 4.0),
                maxOf((lons.max() - lons.min()) * 1.6, 4.0),
            )
        }

        private fun centered(lat: Double, lon: Double, latSpan: Double, lonSpan: Double) =
            MapRegion(lon - lonSpan / 2, lat - latSpan / 2, lonSpan, latSpan)
    }
}

@Composable
private fun StatsGrid(stats: List<StatItem>) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { stat ->
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.weight(1f).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(vertical = 16.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(sfIcon(stat.icon), null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingNumber(stat.value, 26)
                            if (stat.unit.isNotBlank()) {
                                Spacer(Modifier.width(2.dp))
                                Text(stat.unit, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                        Text(stat.title, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/// A number that rolls up to its value when it appears (iOS `RollingNumber`).
@Composable
fun RollingNumber(value: Int, size: Int, color: Color = TnaColor.ink) {
    val anim = remember(value) { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), spring(dampingRatio = 0.9f, stiffness = 40f)) }
    Text(Money.grouped(anim.value.toInt()), style = displayStyle(size, color), maxLines = 1)
}

@Composable
private fun LevelCard(data: PassportData) {
    val shape = RoundedCornerShape(24.dp)
    val progress = remember(data.levelProgress) { Animatable(0f) }
    LaunchedEffect(data.levelProgress) { progress.animateTo(data.levelProgress.toFloat(), spring(dampingRatio = 0.9f, stiffness = 60f)) }
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = TnaColor.accent, spotColor = TnaColor.accent)
            .clip(shape).background(TnaColor.accentBrush).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("УРОВЕНЬ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(data.level.uppercase(), style = displayStyle(28, Color.White))
            }
            Icon(Icons.Filled.MilitaryTech, null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(progress.value.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(Color.White))
        }
        Text(data.nextLevelHint, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StampsSection(stamps: List<Stamp>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Штампы", "#мои_покорения")
        if (stamps.isEmpty()) {
            Text(
                "Штампов пока нет. После первого тура здесь появится твой первый штамп.",
                color = TnaColor.subtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) { stamps.forEach { StampView(it) } }
        }
    }
}

/// Visa-style stamp: glyph, place, year inside a solid + dashed ring, slightly tilted.
@Composable
fun StampView(stamp: Stamp) {
    val color = Color.hex(stamp.color)
    Column(
        Modifier.size(96.dp).rotate(stamp.angle.toFloat()).alpha(0.92f).stampBorder(color).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(sfIcon(stamp.icon), null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(stamp.title, color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, textAlign = TextAlign.Center)
        Text(stamp.year, color = color.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AchievementsSection(all: List<Achievement>, onOpen: (Achievement) -> Unit, onAll: () -> Unit) {
    val earned = all.filter { it.earned }
    val preview = earned + all.filter { !it.earned }.take(2)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Достижения", "${earned.size} из ${all.size}")
        AchievementGrid(preview, Modifier.padding(horizontal = 20.dp), onOpen)
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.accent.copy(alpha = 0.12f))
                .pressable(0.98f, onClick = onAll).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Все достижения", color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AchievementGrid(items: List<Achievement>, modifier: Modifier, onOpen: (Achievement) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { AchievementCard(it, Modifier.weight(1f)) { onOpen(it) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AchievementCard(achievement: Achievement, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = Color.hex(achievement.color)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .alpha(if (achievement.earned) 1f else 0.65f)
            .clip(shape)
            .background(TnaColor.surface)
            .border(1.dp, if (achievement.earned) color.copy(alpha = 0.35f) else TnaColor.stroke, shape)
            .pressable(0.97f, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (achievement.earned) color.copy(alpha = 0.18f) else TnaColor.elevated), contentAlignment = Alignment.Center) {
            Icon(if (achievement.earned) sfIcon(achievement.icon) else Icons.Filled.Lock, null, tint = if (achievement.earned) color else TnaColor.subtle, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(achievement.name, color = if (achievement.earned) TnaColor.ink else TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(achievement.details, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

@Composable
private fun LeaderboardSection(rows: List<LeaderboardEntry>, onOpen: (LeaderboardEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Рейтинг", "#кто_дальше_уехал")
        if (rows.isEmpty()) {
            Text(
                "Рейтинг пока пуст. Съезди в тур — и попадёшь в таблицу первым!",
                color = TnaColor.subtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row -> LeaderboardRow(row) { onOpen(row) } }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, onTap: () -> Unit) {
    val medal = when (entry.rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> null
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .pressable(0.98f, enabled = !entry.isMe && entry.userId != null, onClick = onTap)
            .clip(shape)
            .background(if (entry.isMe) TnaColor.accent.copy(alpha = 0.1f) else TnaColor.surface)
            .border(1.dp, if (entry.isMe) TnaColor.accent.copy(alpha = 0.4f) else TnaColor.stroke, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(medal ?: "${entry.rank}", color = TnaColor.subtle, fontSize = if (medal != null) 22.sp else 16.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
        Spacer(Modifier.width(14.dp))
        Avatar(entry.avatarUrl, entry.name, 40, mine = entry.isMe)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(if (entry.isMe) "${entry.name} (ты)" else entry.name, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${entry.tours} ${pluralRU(entry.tours, "тур", "тура", "туров")}", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text("${Money.grouped(entry.km)} км", color = if (entry.isMe) TnaColor.accent else TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailSheet(achievement: Achievement, onDismiss: () -> Unit) {
    val color = Color.hex(achievement.color)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TnaColor.bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(if (achievement.earned) color.copy(alpha = 0.18f) else TnaColor.elevated), contentAlignment = Alignment.Center) {
                Icon(if (achievement.earned) sfIcon(achievement.icon) else Icons.Filled.Lock, null, tint = if (achievement.earned) color else TnaColor.subtle, modifier = Modifier.size(42.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(achievement.name, style = displayStyle(28), textAlign = TextAlign.Center)
                val badgeColor = if (achievement.earned) color else TnaColor.subtle
                Row(
                    Modifier.clip(CircleShape).background(badgeColor.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (achievement.earned) Icons.Filled.Verified else Icons.Filled.Lock, null, tint = badgeColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (achievement.earned) achievement.earnedDate?.let { "Получено · $it" } ?: "Получено" else "Ещё не получена",
                        color = badgeColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                achievement.howTo.ifBlank { achievement.details },
                color = TnaColor.subtle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllAchievementsSheet(all: List<Achievement>, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<Achievement?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TnaColor.bg) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("ДОСТИЖЕНИЯ", style = displayStyle(34))
            Text("${all.count { it.earned }} из ${all.size} собрано", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            AchievementGrid(all, Modifier.padding(top = 4.dp)) { selected = it }
        }
    }
    selected?.let { AchievementDetailSheet(it) { selected = null } }
}
