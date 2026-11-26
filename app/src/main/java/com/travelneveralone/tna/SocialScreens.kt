package com.travelneveralone.tna

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// Ports of TNAiOS Profile/MiniProfileSheet, Passport/YearRecapView + PassportShareCard,
// Trip/TripView (offline trip pack).

// MARK: - Mini profile

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MiniProfileSheet(app: AppModel, profile: MiniProfile) {
    var full by remember(profile.userId) { mutableStateOf<PublicProfile?>(null) }
    var loading by remember(profile.userId) { mutableStateOf(true) }
    var countries by remember(profile.userId) { mutableStateOf<Map<String, CountryStatus>>(emptyMap()) }
    var countriesLoaded by remember(profile.userId) { mutableStateOf(false) }
    var tnaCodes by remember(profile.userId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(profile.userId) {
        full = app.repo.publicProfile(profile.userId)
        loading = false
        countries = app.repo.publicCountries(profile.userId)
        tnaCodes = app.repo.tnaCodes(profile.userId)
        countriesLoaded = true
    }
    val p = full
    val name = p?.name?.takeIf { it.isNotBlank() } ?: profile.name
    val city = p?.city?.takeIf { it.isNotBlank() } ?: profile.city
    val staff = (p?.isStaff == true) || profile.roleTag != null
    val canWrite = app.account.isRegistered && app.mayWriteTo(profile.copy(role = p?.role ?: profile.role))
    ModalBottomSheet(
        onDismissRequest = { app.miniProfile = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TnaColor.bg,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Cover + avatar overlapping it.
                Box(Modifier.fillMaxWidth().height(240.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp)
                            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
                            .background(if (staff) Brush.linearGradient(listOf(TnaColor.ink, Color.hex(0x4A5B87))) else TnaColor.accentBrush),
                    ) {
                        Icon(Icons.Filled.Public, null, tint = Color.White.copy(alpha = 0.12f), modifier = Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-40).dp).size(220.dp))
                    }
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                            .shadow(12.dp, CircleShape, ambientColor = TnaColor.ink, spotColor = TnaColor.ink)
                            .clip(CircleShape).background(TnaColor.bg).padding(5.dp),
                    ) { Avatar(p?.avatarUrl ?: profile.avatarUrl, name, 104) }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = (-20).dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = displayStyle(28), textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.roleTag?.let { Pill(it, TnaColor.accent) }
                        if (p != null && !staff && p.tours > 0) Pill(p.level.title, Color.hex(0x6C63C7))
                    }
                    if (city.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Place, null, tint = TnaColor.subtle, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(city, color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    p?.memberSinceText?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
                p?.togetherText?.let { text ->
                    Row(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TnaColor.accent.copy(alpha = 0.08f))
                            .border(1.dp, TnaColor.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SupervisorAccount, null, tint = TnaColor.accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(text, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            val together = p?.togetherTours.orEmpty()
                            if (together.isNotEmpty()) Text(together.joinToString(" · "), color = TnaColor.subtle, fontSize = 12.sp)
                        }
                    }
                }
                val led = p?.departuresLed
                if (p != null && led != null) {
                    Column(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(TnaColor.ink, Color.hex(0x4A5B87)))).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Backpack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                if (led > 0) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        RollingNumber(led, 26, Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text(pluralRU(led, "заезд", "заезда", "заездов"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                                    }
                                    Text("провёл с TNA", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                } else {
                                    Text("КОМАНДА TNA", style = displayStyle(20, Color.White))
                                    Text("на связи по любому вопросу о поездке", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                                }
                            }
                        }
                        if (p.directions.isNotEmpty()) {
                            Text("НАПРАВЛЕНИЯ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                p.directions.forEach { Pill(it, Color.White, onDark = true) }
                            }
                        }
                    }
                }
                if (p != null && !staff && p.hasTravelStats) {
                    val progress = p.level.progress(p.tours).toFloat()
                    Column(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TnaColor.accentBrush).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("УРОВЕНЬ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(p.level.title.uppercase(), style = displayStyle(24, Color.White))
                            }
                            Icon(Icons.Filled.MilitaryTech, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                if (p != null && p.hasTravelStats) {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        p.statItems.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { stat ->
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(TnaColor.surface)
                                            .border(1.dp, TnaColor.stroke, RoundedCornerShape(18.dp)).padding(vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(sfIcon(stat.icon), null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            RollingNumber(stat.value, 24)
                                            if (stat.unit.isNotBlank()) Text(" ${stat.unit}", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                                        }
                                        Text(stat.title, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (countriesLoaded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Карта стран")
                        Box(Modifier.padding(horizontal = 20.dp)) { CountriesCompact(countries, tnaCodes) }
                    }
                }
                if (p != null && p.achievements.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Достижения", "${p.achievements.size}")
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            p.achievements.forEach { a ->
                                val color = Color.hex(a.color)
                                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(62.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(sfIcon(a.icon), null, tint = color, modifier = Modifier.size(26.dp))
                                    }
                                    Text(a.name, color = TnaColor.ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                                    a.earnedDate?.let { Text(it, color = TnaColor.subtle, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
                if (p != null && p.stamps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Штампы", "#где_был")
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            p.stamps.forEach { StampView(it) }
                        }
                    }
                }
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(24.dp), color = TnaColor.accent, strokeWidth = 2.dp)
                if (p != null && !p.hasTravelStats && p.achievements.isEmpty() && p.stamps.isEmpty() && p.departuresLed == null) {
                    Text(
                        "Поездок с TNA пока нет - но карта стран уже рассказывает, где человек был.",
                        color = TnaColor.subtle,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
            if (canWrite) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PrimaryButton("Написать сообщение", icon = Icons.AutoMirrored.Filled.Send) { app.writeTo(profile) }
                }
            }
        }
    }
}

// MARK: - Share cards

/// Renders [content] in a dialog and shares it as a PNG (iOS ImageRenderer + ShareLink).
/// [text] goes along with the picture (iOS ShareLink message).
@Composable
fun ShareCardDialog(title: String, onDismiss: () -> Unit, text: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Scrolls: the 9:16 certificate card is taller than a small phone.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }) { content() }
            PrimaryButton(if (busy) "Готовим…" else "Поделиться", enabled = !busy, icon = Icons.Filled.Share) {
                busy = true
                scope.launch {
                    shareLayer(context, layer, title, text)
                    busy = false
                }
            }
            Text("Закрыть", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable(onClick = onDismiss).padding(8.dp))
        }
    }
}

private suspend fun shareLayer(context: Context, layer: GraphicsLayer, title: String, text: String? = null) {
    runCatching {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "tna-${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}

/// Story-ready passport card (iOS PassportShareCard).
@Composable
fun PassportShareCard(data: PassportData, name: String) {
    val display = name.trim().ifEmpty { data.userName }
    Column(
        Modifier.width(360.dp).clip(RoundedCornerShape(28.dp)).background(TnaColor.bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TNA", style = displayStyle(20), modifier = Modifier.border(2.dp, TnaColor.ink, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(Modifier.weight(1f))
            Text("#путешествуй_не_один", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column {
            Text("ПАСПОРТ", style = displayStyle(38))
            Text(display.uppercase(), style = displayBrushStyle(38), maxLines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            data.stats.take(3).forEach { stat ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(TnaColor.surface).padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Money.grouped(stat.value), style = displayStyle(30))
                    Text(stat.title.lowercase(), color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            data.stamps.take(4).forEach { stamp ->
                val color = Color.hex(stamp.color)
                Column(
                    Modifier.size(64.dp).rotate(stamp.angle.toFloat()).stampBorder(color).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(sfIcon(stamp.icon), null, tint = color, modifier = Modifier.size(18.dp))
                    Text(stamp.title, color = color, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MilitaryTech, null, tint = TnaColor.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(data.level, color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Text("travelneveralone.com", color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/// Gift certificate as a story-ready picture (iOS CertificateShareCard).
/// No PIN on it: people post the picture publicly; the PIN goes in the
/// share text next to it (GiftCertificate.shareMessage).
@Composable
fun CertificateShareCard(cert: GiftCertificate, prod: Boolean) {
    Column(Modifier.width(360.dp).height(640.dp).background(TnaColor.bg).padding(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44, prod)
            Spacer(Modifier.weight(1f))
            Text("#путешествуй_не_один", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text("ТЕБЕ ПОДАРОК", style = displayStyle(40))
        Text("путешествие с Travel Never Alone", color = TnaColor.subtle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Column(
            Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(TnaColor.accentBrush).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("ПОДАРОЧНЫЙ СЕРТИФИКАТ", style = displayStyle(18, Color.White.copy(alpha = 0.9f)))
            Text(cert.amountText, style = displayStyle(52, Color.White), maxLines = 1)
            if (cert.recipientName.isNotBlank()) Text("Кому: ${cert.recipientName}", color = Color.White.copy(alpha = 0.95f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (cert.wish.isNotBlank()) Text("«${cert.wish}»", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 4)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.35f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cert.code.orEmpty(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                cert.expiresText?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CardGiftcard, null, tint = TnaColor.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Выбери тур и активируй сертификат в приложении", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("travelneveralone.com", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

// MARK: - Year recap

/// «Мой год с TNA»: full-screen, blocks appear one by one, then a share card.
@Composable
fun YearRecapScreen(app: AppModel, recap: YearRecap) {
    var stage by remember { mutableStateOf(0) }
    var sharing by remember { mutableStateOf(false) }
    BackHandler { app.recapShown = false }
    LaunchedEffect(Unit) { for (step in 1..4) { delay(450); stage = step } }
    val night = Brush.verticalGradient(listOf(TnaColor.ink, Color.hex(0x16264F)))
    Box(Modifier.fillMaxSize().background(night).background(Brush.radialGradient(listOf(TnaColor.accent.copy(alpha = 0.25f), Color.Transparent), radius = 1100f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.padding(top = 40.dp).appear(stage >= 1), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("МОЙ ГОД", style = displayStyle(44, Color.hex(Palette.bg)))
                Text("С TNA", style = displayBrushStyle(56))
                Text(recap.year, color = Color.hex(Palette.bg).copy(alpha = 0.5f), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.padding(horizontal = 24.dp).appear(stage >= 2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RecapStat(recap.tours, "${pluralRU(recap.tours, "тур", "тура", "туров")} за год", TnaColor.hands[1], Modifier.weight(1f))
                RecapStat(recap.km, pluralRU(recap.km, "километр", "километра", "километров"), TnaColor.hands[2], Modifier.weight(1f))
                RecapStat(recap.newAchievements, newAchievementsCaption(recap.newAchievements), TnaColor.hands[0], Modifier.weight(1f))
            }
            if (recap.topDestination.isNotBlank()) {
                Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(130.dp).clip(RoundedCornerShape(24.dp)).appear(stage >= 3)) {
                    TourGradient(recap.topDestinationColors, Modifier.matchParentSize())
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text("Топ-направление", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(recap.topDestination.uppercase(), style = displayStyle(38, Color.White))
                    }
                }
            }
            Row(Modifier.appear(stage >= 4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recap.stamps.take(3).forEach { Box(Modifier.size(78.dp).scale(0.8f)) { StampView(it) } }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).appear(stage >= 4)) {
                PrimaryButton("Поделиться итогами", icon = Icons.Filled.Share) { sharing = true }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp).size(40.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f)).pressable(0.9f) { app.recapShown = false },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
    if (sharing) ShareCardDialog("TNA ${recap.year}", onDismiss = { sharing = false }) { RecapShareCard(recap, app.isProd) }
}

/// Fade + rise in once [shown] flips (the staged reveal of the recap screen).
@Composable
private fun Modifier.appear(shown: Boolean): Modifier {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(shown) { if (shown) anim.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 200f)) }
    return this.alpha(anim.value.coerceIn(0f, 1f)).offset(y = (20 * (1 - anim.value)).dp)
}

@Composable
private fun RecapStat(value: Int, caption: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f)).padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RollingNumber(value, 34, color)
        Text(caption, color = Color.hex(Palette.bg).copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecapShareCard(recap: YearRecap, prod: Boolean) {
    Column(
        Modifier.width(360.dp).clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(TnaColor.ink, Color.hex(0x16264F)))).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandMark(54, prod)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("МОЙ ГОД", style = displayStyle(36, Color.hex(Palette.bg)))
            Text("С TNA", style = displayBrushStyle(46))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "${recap.tours}" to "${pluralRU(recap.tours, "тур", "тура", "туров")} за год",
                Money.grouped(recap.km) to pluralRU(recap.km, "километр", "километра", "километров"),
                "${recap.newAchievements}" to newAchievementsCaption(recap.newAchievements),
            ).forEach { (value, caption) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = displayStyle(28, Color.White))
                    Text(caption, color = Color.hex(Palette.bg).copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
        }
        if (recap.topDestination.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = Color.hex(0xF5C752), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(recap.topDestination, color = Color.hex(0xF5C752), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text("travelneveralone.com", color = Color.hex(Palette.bg).copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Trip mode (offline trip pack)

@Composable
fun TripModeScreen(app: AppModel) {
    val plan = app.tripPlan
    val context = LocalContext.current
    BackHandler { app.tripModeShown = false }
    Box(Modifier.fillMaxSize().background(TnaColor.bg)) {
        if (plan == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = TnaColor.accent)
                Gap(12)
                Text("Загружаем поездку…", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    TourGradient(plan.colors, Modifier.matchParentSize()) { SymbolWatermark("mountain.2.fill", 180, 90, -70, -10f) }
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ТЫ В ТУРЕ", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(CircleShape).background(Color.hex(0x9CFF57)).padding(horizontal = 10.dp, vertical = 5.dp))
                            Spacer(Modifier.width(8.dp))
                            Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DownloadForOffline, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Доступно офлайн", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(plan.tourName.uppercase(), style = displayStyle(44, Color.White), maxLines = 1)
                        Text(plan.statusLine, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (plan.schedule.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionTitle("Программа", plan.dayIndex?.let { "#день_$it" } ?: "#день_за_днём")
                        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TnaColor.surface).padding(16.dp)) {
                            plan.schedule.forEachIndexed { index, item ->
                                TimelineRow(item, isNow = plan.dayIndex?.let { item.time == "День $it" } == true, isLast = index == plan.schedule.lastIndex)
                            }
                        }
                    }
                }
                if (plan.contacts.isNotEmpty()) {
                    Column(
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color.hex(0xC62828), Color.hex(0x7B1010)))).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalHospital, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("ВАЖНЫЕ КОНТАКТЫ", style = displayStyle(20, Color.White))
                        }
                        plan.contacts.forEachIndexed { index, c ->
                            Row(Modifier.fillMaxWidth().pressable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(PhoneNumber.pretty(c.phone), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Phone, "Позвонить", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (index < plan.contacts.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                        }
                        Text("Работает без интернета - сохранено на устройстве", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp).size(40.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)).pressable(0.9f) { app.tripModeShown = false },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun TimelineRow(item: TripScheduleItem, isNow: Boolean, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (isNow) TnaColor.accent else TnaColor.elevated), contentAlignment = Alignment.Center) {
                Icon(sfIcon(item.icon), null, tint = if (isNow) Color.White else TnaColor.subtle, modifier = Modifier.size(15.dp))
            }
            if (!isLast) Box(Modifier.width(2.dp).height(26.dp).background(TnaColor.stroke))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(item.time, color = if (isNow) TnaColor.accent else TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(item.title, color = TnaColor.ink, fontSize = 15.sp, fontWeight = if (isNow) FontWeight.Bold else FontWeight.SemiBold)
        }
        if (isNow) {
            Text("СЕЙЧАС", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(TnaColor.accent).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

