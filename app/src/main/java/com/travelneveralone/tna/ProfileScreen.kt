package com.travelneveralone.tna

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// Port of TNAiOS Features/Profile/ProfileView.

@Composable
fun ProfileScreen(app: AppModel) {
    LaunchedEffect(app.account.isRegistered) { app.loadProfileBits() }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tna.ui", Context.MODE_PRIVATE) }
    var nudgeDismissed by remember { mutableStateOf(prefs.getBoolean("profile.nudge.dismissed", false)) }
    var logoutAsk by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    var issueAsk by remember { mutableStateOf(false) }
    val registered = app.account.isRegistered
    Column(
        Modifier.fillMaxSize().background(TnaColor.bg).verticalScroll(rememberScrollState()).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProfileHeader(app)
        if (registered && !nudgeDismissed) {
            ProfileNudge(app.role, onDismiss = {
                nudgeDismissed = true
                prefs.edit().putBoolean("profile.nudge.dismissed", true).apply()
            }) {
                nudgeDismissed = true
                prefs.edit().putBoolean("profile.nudge.dismissed", true).apply()
                app.sheet = Sheet.EditProfile
            }
        }
        if (registered && app.role.hasTravelProfile) TripsBlock(app)
        if (app.role.canBookAndEarn) PromoCard(app)
        SettingsList(app, onIssue = { issueAsk = true })
        if (registered) {
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.surface)
                    .border(1.dp, TnaColor.stroke, RoundedCornerShape(16.dp)).pressable(0.99f) { logoutAsk = true }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Выйти из аккаунта", color = TnaColor.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.fillMaxWidth().pressable(enabled = !app.deleting) { deleteAsk = true }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (app.deleting) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = TnaColor.subtle, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (app.deleting) "Удаляю…" else "Удалить аккаунт", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TNA - заботливый проводник в мир путешествий", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("v ${appVersion(context)}", color = TnaColor.subtle.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (logoutAsk) {
        val lockWhat = app.appLock.kind.title
        ConfirmDialog(
            title = "Выйти из аккаунта?",
            // With the biometric lock on the session is kept aside, so coming
            // back takes a fingerprint and not a paid SMS.
            text = if (app.appLock.isEnabled && lockWhat != null) "Брони и история останутся на сервере. Вернуться можно по $lockWhat, без кода из SMS."
            else "Брони и история останутся на сервере - вход по тому же номеру вернёт их.",
            confirm = "Выйти",
            onConfirm = { logoutAsk = false; app.signOut(keepQuickSignIn = app.appLock.isEnabled) },
            onDismiss = { logoutAsk = false },
        )
    }
    if (deleteAsk) {
        ConfirmDialog(
            title = "Удалить аккаунт навсегда?",
            text = "Аккаунт и личные данные будут удалены без возможности восстановления. Это действие нельзя отменить.",
            confirm = "Удалить аккаунт",
            onConfirm = { deleteAsk = false; app.deleteAccount() },
            onDismiss = { deleteAsk = false },
        )
    }
    app.deleteError?.let { error ->
        AlertDialog(
            onDismissRequest = { app.deleteError = null },
            confirmButton = { TextButton(onClick = { app.deleteError = null }) { Text("Ок", color = TnaColor.accent) } },
            title = { Text("Ошибка") },
            text = { Text(error) },
            containerColor = TnaColor.bg,
        )
    }
    if (issueAsk) {
        AlertDialog(
            onDismissRequest = { issueAsk = false },
            title = { Text("Что не так?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Выбери, о чём речь, - откроется чат с менеджером, он проверит и поправит. Часть поездок и бонусов перенесена из старой базы, там бывают ошибки.",
                        color = TnaColor.subtle,
                        fontSize = 13.sp,
                    )
                    Gap(6)
                    DataIssue.entries.forEach { issue ->
                        Text(
                            issue.title,
                            color = TnaColor.accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().pressable { issueAsk = false; app.openSupportChat(issue.prefill) }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { issueAsk = false }) { Text("Отмена", color = TnaColor.subtle) } },
            containerColor = TnaColor.bg,
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = TnaColor.subtle) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = TnaColor.danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = TnaColor.ink) } },
        containerColor = TnaColor.bg,
    )
}

@Suppress("UNUSED_PARAMETER")
// "1.0-beta (261119.1435)": the number in brackets is the build testers report.
private fun appVersion(context: Context): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.TNA_BUILD})"

@Composable
private fun ProfileHeader(app: AppModel) {
    val registered = app.account.isRegistered
    val name = if (registered) app.account.name.ifBlank { "Путешественник" } else "Гость"
    val badge = when (app.role) {
        UserRole.Client -> app.passport.level.ifBlank { TravelerLevel.Novice.title }
        UserRole.Guide -> "Гид TNA"
        UserRole.Curator -> "Куратор TNA"
        UserRole.Partner -> "Партнёр TNA"
        UserRole.Manager, UserRole.Admin -> "Команда TNA"
    }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { app.pickAvatar(it) }
    }
    Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.pressable(0.95f, enabled = registered) { picker.launch("image/*") }) {
            Avatar(if (registered) app.avatarUrl else null, name, 76, mine = true)
            if (app.avatarBusy) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                }
            } else if (registered) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(TnaColor.surface).padding(3.dp).clip(CircleShape).background(TnaColor.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PhotoCamera, "Сменить фото", tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name.uppercase(), style = displayStyle(30), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (registered) {
                Row(
                    Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (app.role.canBookAndEarn) Icons.Filled.MilitaryTech else Icons.Filled.VerifiedUser, null, tint = TnaColor.accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(badge, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (app.account.phone.isNotBlank()) {
                    Text(PhoneNumber.pretty(app.account.phone), color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    Modifier.clip(CircleShape).background(TnaColor.accentBrush).pressable(0.95f) { app.requireAccount {} }.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PersonAdd, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Войти / регистрация", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (registered) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.12f)).pressable(0.9f) { app.sheet = Sheet.EditProfile },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.EditNote, "Редактировать профиль", tint = TnaColor.accent, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun ProfileNudge(role: UserRole, onDismiss: () -> Unit, onFill: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(shape).background(TnaColor.accent.copy(alpha = 0.10f))
            .border(1.dp, TnaColor.accent.copy(alpha = 0.25f), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = TnaColor.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Заполни профиль", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Close, "Скрыть", tint = TnaColor.subtle, modifier = Modifier.size(16.dp).pressable(0.9f, onClick = onDismiss))
        }
        Text(
            if (role == UserRole.Partner || !role.hasTravelProfile) "ФИО, дата рождения и город - базовая информация о тебе для команды TNA."
            else "Дата рождения, город, почта и документ - чтобы билеты оформлялись за пару касаний.",
            color = TnaColor.subtle,
            fontSize = 13.sp,
        )
        Text(
            "Заполнить",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(TnaColor.accentBrush).pressable(0.97f, onClick = onFill).padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun TripsBlock(app: AppModel) {
    val upcoming = app.trips.filter { it.isUpcoming }.sortedBy { it.startsOn ?: java.time.LocalDate.MAX }
    val next = upcoming.firstOrNull() ?: return
    val others = upcoming.drop(1)
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(if (next.isRunning) "Поездка сейчас" else "Ближайшая поездка", if (next.isRunning) "#в_пути" else "#скоро")
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(220.dp).clip(shape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), shape).pressable(0.98f) { app.openProgram(next) },
            ) {
                TourCover(next.imageUrl, next.colors, Modifier.matchParentSize(), focus = next.imageFocus)
                if (next.imageUrl == null) SymbolWatermark(next.symbol, 120, 110, -60, -10f, 0.13f)
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text(
                        when {
                            next.isGuide -> "ТЫ ГИД"
                            next.isRunning -> "СЕЙЧАС В ПУТИ"
                            else -> "ПОДТВЕРЖДЕНО"
                        },
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(CircleShape).background(Color.hex(if (next.isGuide) 0x8FD0FF else 0x9CFF57)).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(next.tourName.uppercase(), style = displayStyle(36, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (next.countdown.isBlank()) next.label else "${next.label} · ${next.countdown}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TripAction("Программа", Icons.AutoMirrored.Filled.ListAlt, Modifier.weight(1f)) { app.openProgram(next) }
                TripAction("Чат группы", Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f)) {
                    next.chatId?.let { app.openGroupChat(it) } ?: app.openTripMode()
                }
            }
        }
        if (others.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Остальные поездки", "#ещё_${others.size}")
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { trip ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.surface)
                                .pressable(0.98f) { trip.chatId?.let { app.openGroupChat(it) } ?: app.openTripMode() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val colors = trip.colors.map { Color.hex(it) }.let { if (it.size >= 2) it else listOf(TnaColor.ink, TnaColor.accent) }
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
                                Icon(sfIcon(trip.symbol), null, tint = Color.White, modifier = Modifier.size(18.dp))
                                if (trip.imageUrl != null) AsyncImage(model = trip.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(trip.tourName, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(if (trip.countdown.isBlank()) trip.label else "${trip.label} · ${trip.countdown}", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            if (trip.isGuide) {
                                Pill("ГИД", TnaColor.accent)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripAction(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(TnaColor.accent.copy(alpha = 0.12f)).pressable(0.97f, onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/// Dashed accent outline used by the promo card.
private fun Modifier.dashedCard(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(20.dp.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
    )
}

@Composable
private fun PromoCard(app: AppModel) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Зови друзей", "#не_один_же")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!app.account.isRegistered) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TnaColor.surface).dashedCard(TnaColor.accent.copy(alpha = 0.5f))
                        .pressable(0.98f) { app.requireAccount {} }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Другу 6 000 ₽ на первую поездку, тебе 6 000 ₽ когда он вернётся", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Войти, чтобы получить код", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Filled.PersonAdd, null, tint = TnaColor.accent, modifier = Modifier.size(24.dp))
                }
                return@Column
            }
            val code = app.referralCode
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TnaColor.surface).dashedCard(TnaColor.accent.copy(alpha = 0.5f))
                    .pressable(0.98f) {
                        if (code.isBlank()) return@pressable
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TNA", code))
                        copied = true
                    }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Другу 6 000 ₽ на первую поездку, тебе 6 000 ₽ когда он вернётся. Дальше ему 5 000 ₽, тебе 3 000 ₽", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(code.ifBlank { "…" }, style = displayStyle(26))
                }
                Icon(
                    if (copied) Icons.Filled.CheckCircle else Icons.Filled.ContentCopy,
                    "Скопировать код",
                    tint = if (copied) Color.hex(0x66BB6A) else TnaColor.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (code.isNotBlank()) {
                    Row(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(TnaColor.accentBrush).pressable(0.97f) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Присоединяйся к TNA. По коду $code - 6 000 ₽ на первую поездку.")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Поделиться", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(TnaColor.surface)
                        .border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp)).pressable(0.97f) { entering = true },
                    contentAlignment = Alignment.Center,
                ) { Text("Ввести код друга", color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            app.referralNote?.let { Text(it, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            app.bonus?.profileLine?.let { Text(it, color = TnaColor.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            app.bonus?.expiresLine?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            if (app.bonusLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    app.bonusLines.forEach { Text(it.text, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
    if (entering) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { entering = false },
            title = { Text("Код друга", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Введи код того, кто тебя пригласил", color = TnaColor.subtle)
                    BookingField("Например, SLAV-3F9A", input, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters) { input = it }
                }
            },
            confirmButton = { TextButton(onClick = { entering = false; app.applyFriendCode(input) }) { Text("Применить", color = TnaColor.accent, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { entering = false }) { Text("Отмена", color = TnaColor.ink) } },
            containerColor = TnaColor.bg,
        )
    }
}

@Composable
private fun SettingsList(app: AppModel, onIssue: () -> Unit) {
    val context = LocalContext.current
    val registered = app.account.isRegistered
    val lockScope = rememberCoroutineScope()
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)) {
        ProfileRow(Icons.Filled.Notifications, "Уведомления") { app.requireAccount { app.sheet = Sheet.Notifications } }
        // App lock: signed-in users on devices with biometrics.
        val lockTitle = app.appLock.kind.title
        if (app.appLock.isOfferable && lockTitle != null) {
            ProfileDivider()
            ProfileToggleRow(app.appLock.kind.icon, "Вход по $lockTitle", app.appLock.isEnabled) { on ->
                lockScope.launch { app.appLock.setEnabled(on) }
            }
        }
        // Several phones per person: any of them signs into this account.
        // Everyone signs in by phone, staff included.
        if (registered) {
            ProfileDivider()
            ProfileRow(Icons.Filled.Smartphone, "Номера для входа") { app.sheet = Sheet.LoginPhones }
        }
        if (registered && app.role.canBookAndEarn) {
            ProfileDivider()
            ProfileRow(Icons.Filled.CreditCard, "Мои брони и оплаты") { app.loadBookings(); app.sheet = Sheet.MyBookings }
            ProfileDivider()
            ProfileRow(Icons.Filled.Group, "Попутчики") { app.loadCompanions(); app.sheet = Sheet.Companions }
            ProfileDivider()
            ProfileRow(Icons.Filled.Public, "Мои страны") { app.loadCountries(); app.sheet = Sheet.Countries }
        }
        ProfileDivider()
        ProfileRow(Icons.Filled.Description, "Условия бронирования") { app.sheet = Sheet.Terms }
        ProfileDivider()
        ProfileRow(Icons.Filled.Gavel, "Пользовательское соглашение") { open("https://travelneveralone.com/agreement") }
        ProfileDivider()
        ProfileRow(Icons.Filled.Policy, "Политика конфиденциальности") { open("https://travelneveralone.com/policy") }
        ProfileDivider()
        ProfileRow(Icons.Filled.FormatQuote, "Отзывы путешественников") { app.loadReviews(null); app.sheet = Sheet.Reviews }
        if (app.role.canBookAndEarn) {
            ProfileDivider()
            ProfileRow(Icons.Filled.CardGiftcard, "Подарочные сертификаты") { app.requireAccount { app.sheet = Sheet.Certificate } }
        }
        if (registered) {
            ProfileDivider()
            ProfileRow(Icons.Filled.ReportProblem, "Нашлась неточность?", onClick = onIssue)
        }
        ProfileDivider()
        ProfileRow(Icons.Filled.AutoAwesome, "О нас") { app.sheet = Sheet.About }
        ProfileDivider()
        ProfileRow(Icons.Filled.Phone, "Контакты") { app.sheet = Sheet.Contacts }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(0.99f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(title, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (detail != null) {
            Text(detail, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(TnaColor.stroke))
}
