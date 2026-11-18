package com.travelneveralone.tna

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RateReview
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// Ports of the iOS sheets (Auth, News, MyBookings, Info/*, Profile/*, Compass, Schedule, Trip).
// Every sheet scrolls itself; SheetHost only provides the bottom sheet and its colour.

/// Standard sheet body: scrolls, 24 dp side insets, the same top gap iOS uses.
@Composable
fun SheetColumn(spacing: Int = 18, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

@Composable
private fun TwoLineTitle(top: String, bottom: String, topSize: Int = 34, bottomSize: Int = 24) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(top, style = displayStyle(topSize))
        Text(bottom, style = displayBrushStyle(bottomSize))
    }
}

// MARK: - Login

/// Digits in an SMS code (`GOTRUE_SMS_OTP_LENGTH`). Mock accepts "0000", so
/// «Войти» still works from four digits.
private const val LOGIN_CODE_LENGTH = 6

/// Every code is a paid SMS, so the code step never asks for one it does not
/// need (iOS `LoginView`, [CodeCooldown] in AppModel): «Отправить код ещё раз»
/// shows after the server's 60 s pause with a live countdown until then;
/// «Изменить номер» keeps the typed number, and the same number again reuses
/// its code; a full code signs in without a tap.
@Composable
fun LoginSheet(app: AppModel) {
    val testLogins = if (app.isProd) emptyList() else Mock.testLogins
    var done by remember { mutableStateOf(false) }
    // Re-read every second so the resend countdown ticks.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    if (done) {
        LaunchedEffect(Unit) { delay(5000); app.sheet = null }
        SheetColumn {
            Text("ГОТОВО", style = displayStyle(34))
            Text("Добро пожаловать в TNA! Теперь брони и чат сохранятся за тобой.", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            PrimaryButton("Отлично") { app.sheet = null }
        }
        return
    }
    // A sign-out with the biometric lock on kept the account: the sheet opens on
    // a biometric sign-in (no SMS), the phone flow is one tap away (iOS LoginView).
    val quick = app.loginStep == 0 && app.showsQuickSignIn
    if (quick) {
        // The prompt comes up by itself, like in a banking app.
        LaunchedEffect(Unit) { app.quickSignIn() }
        SheetColumn(spacing = 20) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ВХОД", style = displayStyle(34))
                Text(
                    if (app.account.quickPhone.isEmpty()) "С возвращением! Код из SMS не понадобится"
                    else "С возвращением! Аккаунт ${app.account.quickPhone}, код из SMS не понадобится",
                    color = TnaColor.subtle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            val what = app.appLock.kind.title ?: "биометрии"
            PrimaryButton(
                if (app.loginBusy) "Входим…" else "Войти по $what",
                enabled = !app.loginBusy,
                icon = app.appLock.kind.icon,
            ) { app.quickSignIn() }
            Text(
                "Войти по другому номеру",
                color = TnaColor.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressable { if (!app.loginBusy) app.leaveQuickSignIn() },
            )
            app.loginError?.let { Text(it, color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        return
    }
    SheetColumn(spacing = 20) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ВХОД", style = displayStyle(34))
            Text(
                if (app.loginStep == 0) "Номер нужен, чтобы сохранять брони и находить твои прошлые поездки"
                else "Отправили код на ${PhoneNumber.pretty(app.loginPhone)}",
                color = TnaColor.subtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (app.loginStep == 0) {
            PhoneField(app.loginCountry, app.loginNational, autoFocus = true, onChange = { country, national ->
                app.loginCountry = country
                app.loginNational = national
            })
            if (testLogins.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    testLogins.forEach { login ->
                        Text(
                            login.label,
                            color = TnaColor.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.12f))
                                .pressable { app.loginPhone = login.phone; app.loginCode = login.code }.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            val complete = PhoneInput.isComplete(app.loginNational, app.loginCountry)
            val live = complete && app.codeCooldown.hasLiveCode(app.loginPhone, now)
            val wait = if (complete) app.codeCooldown.secondsUntilNewCode(app.loginPhone, now) else 0L
            when {
                // Back from «Изменить номер» with the same number: its code is
                // still good, a second SMS would only cost money.
                live -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Ввести код", icon = Icons.AutoMirrored.Filled.ArrowForward) { app.requestCode() }
                    Text("Код на этот номер уже отправлен, новый не нужен", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                wait > 0 -> PrimaryButton(
                    "Повторная отправка через ${CodeCooldown.countdown(wait)}",
                    enabled = false,
                    icon = Icons.Filled.Timer,
                ) {}
                else -> PrimaryButton(
                    if (app.loginBusy) "Отправляем…" else "Получить код",
                    enabled = !app.loginBusy && complete,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                ) { app.requestCode() }
            }
            ConsentFooter()
        } else {
            LoginCodeField(app.loginCode) { typed ->
                val digits = typed.filter { it in '0'..'9' }.take(LOGIN_CODE_LENGTH)
                app.loginCode = digits
                if (digits.length == LOGIN_CODE_LENGTH && !app.loginBusy && !app.loginIsTestNumber) {
                    app.verifyCode { done = true }
                }
            }
            PrimaryButton(
                if (app.loginBusy) "Проверяем…" else "Войти",
                enabled = !app.loginBusy && app.loginCode.length >= 4,
                icon = Icons.Filled.Check,
            ) { app.verifyCode { done = true } }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val wait = app.codeCooldown.secondsUntilNewCode(app.loginPhone, now)
                if (wait > 0) {
                    Text("Повторная отправка возможна через ${CodeCooldown.countdown(wait)}", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } else {
                    Text(
                        if (app.loginBusy) "Отправляем…" else "Отправить код ещё раз",
                        color = TnaColor.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable(enabled = !app.loginBusy) { app.requestCode(forceNew = true) },
                    )
                }
                Text(
                    "Изменить номер",
                    color = TnaColor.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressable(enabled = !app.loginBusy) { app.changeLoginNumber() },
                )
            }
        }
        app.loginError?.let { Text(it, color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/// The SMS code: digits only, focused at once so the keyboard is up.
@Composable
private fun LoginCodeField(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = TnaColor.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(TnaColor.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("Код из SMS", color = TnaColor.subtle, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                inner()
            }
        },
    )
}

// MARK: - News

@Composable
fun NewsSheet(app: AppModel) {
    var opened by remember { mutableStateOf<NewsItem?>(null) }
    val context = LocalContext.current
    opened?.let { item ->
        SheetColumn(spacing = 16) {
            Text("← Новости", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { opened = null })
            item.imageUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)).background(TnaColor.surface))
            }
            Text(item.date, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(item.title, style = displayStyle(28))
            if (item.body.isNotBlank()) Text(item.body, color = TnaColor.ink.copy(alpha = 0.85f), fontSize = 16.sp)
            when (val link = item.link) {
                is NewsLink.TourLink -> PrimaryButton("Открыть тур", icon = Icons.AutoMirrored.Filled.OpenInNew) { app.openTourById(link.id) }
                is NewsLink.UrlLink -> PrimaryButton("Подробнее", icon = Icons.Filled.Language) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                null -> {}
            }
        }
        return
    }
    SheetColumn(spacing = 16) {
        Text("НОВОСТИ", style = displayStyle(28))
        if (app.news.isEmpty()) {
            when (app.newsState) {
                "loading" -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TnaColor.accent) }
                "failed" -> EmptyState(Icons.Filled.Newspaper, null, "Не удалось загрузить новости")
                else -> EmptyState(Icons.Filled.Newspaper, null, "Пока новостей нет")
            }
        }
        app.news.forEach { item ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface)
                    .then(if (item.isPinned) Modifier.border(1.dp, TnaColor.accent.copy(alpha = 0.5f), shape) else Modifier)
                    .pressable(0.98f) { opened = item }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item.imageUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(TnaColor.elevated))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Text(item.title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(item.date, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (item.body.isNotBlank()) Text(item.body, color = TnaColor.subtle, fontSize = 14.sp, maxLines = 2)
                if (item.isPinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PushPin, null, tint = TnaColor.accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Закреплено", color = TnaColor.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (item.link) {
                            is NewsLink.TourLink -> "Открыть тур"
                            is NewsLink.UrlLink -> "Подробнее"
                            null -> "Читать"
                        },
                        color = TnaColor.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TnaColor.accent, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// MARK: - My bookings

@Composable
fun BookingsSheet(app: AppModel) {
    var selected by remember { mutableStateOf<BookingSummary?>(null) }
    selected?.let { booking ->
        BookingDetail(booking) { selected = null }
        return
    }
    var reviewing by remember { mutableStateOf<TourDetailModel?>(null) }
    SheetColumn {
        Text("МОИ БРОНИ", style = displayStyle(34))
        // Above the bookings: the one thing here that asks for an action.
        app.pendingReviews.forEach { trip ->
            val tour = app.tours.firstOrNull { it.backendId == trip.tourId } ?: return@forEach
            val shape = RoundedCornerShape(18.dp)
            Row(
                Modifier.fillMaxWidth().pressable(0.98f) {
                    reviewing = TourDetailModel(app.repo, tour).also { it.pendingTrips = listOf(trip) }
                }.clip(shape).background(TnaColor.accent.copy(alpha = 0.08f)).border(1.dp, TnaColor.accent.copy(alpha = 0.25f), shape).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.RateReview, null, tint = TnaColor.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Как прошёл тур?", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(if (trip.label.isBlank()) trip.tourName else "${trip.tourName} · ${trip.label}", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
            }
        }
        when (app.bookingsState) {
            "loading" -> if (app.bookings.isEmpty()) repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(92.dp)) }
            "loaded" -> Unit
            else -> if (app.bookings.isEmpty()) EmptyState(Icons.Filled.Luggage, null, app.bookingsState)
        }
        app.bookings.forEach { booking ->
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
                    .pressable(0.98f) { selected = booking }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(booking.title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (booking.subtitle.isNotBlank()) Text(booking.subtitle, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                booking.moneyNote?.let { Text(it, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (booking.peopleCount > 0) {
                        Icon(Icons.Filled.Groups, null, tint = TnaColor.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(peopleText(booking.peopleCount), color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(booking.date, color = TnaColor.subtle.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    StatusPill(booking.status)
                }
            }
        }
    }
    reviewing?.let { model ->
        ReviewComposerSheet(model) {
            reviewing = null
            app.loadBookings()
        }
    }
}

private fun peopleText(n: Int) = "$n ${pluralRU(n, "человек", "человека", "человек")}"

@Composable
private fun StatusPill(status: BookingStatus) {
    val color = Color.hex(status.color)
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(status.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BookingDetail(booking: BookingSummary, onBack: () -> Unit) {
    SheetColumn(spacing = 22) {
        Text("← Мои брони", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable(onClick = onBack))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(booking.title, style = displayStyle(24))
            if (booking.subtitle.isNotBlank()) Text(booking.subtitle, color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            booking.moneyNote?.let { Text(it, color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (booking.peopleCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, null, tint = TnaColor.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(peopleText(booking.peopleCount), color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Заявка от ${booking.date}", color = TnaColor.subtle.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (booking.status == BookingStatus.Cancelled) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.hex(BookingStatus.Cancelled.color).copy(alpha = 0.12f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.hex(BookingStatus.Cancelled.color), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Бронь отменена", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            val pipeline = BookingStatus.pipeline
            val current = pipeline.indexOf(booking.status).coerceAtLeast(0)
            Column {
                pipeline.forEachIndexed { index, step ->
                    val color = Color.hex(step.color)
                    Row {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(if (index <= current) color else TnaColor.elevated), contentAlignment = Alignment.Center) {
                                if (index < current) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                else if (index == current) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                            }
                            if (index < pipeline.lastIndex) Box(Modifier.width(2.dp).height(32.dp).background(if (index < current) color.copy(alpha = 0.5f) else TnaColor.stroke))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(step.label, color = if (index <= current) TnaColor.ink else TnaColor.subtle, fontSize = 15.sp, fontWeight = if (index == current) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        val companions = booking.note?.let { ParsedBookingNote(it).companions }.orEmpty()
        if (companions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Попутчики (${companions.size})", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                companions.forEach { (name, details) ->
                    var open by remember(name) { mutableStateOf(false) }
                    val shape = RoundedCornerShape(14.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).pressable(0.99f) { open = !open }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (details.isNotEmpty()) Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                        }
                        if (open) {
                            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                details.forEach { Text(it, color = TnaColor.subtle, fontSize = 13.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Info

@Composable
fun TermsSheet() {
    val steps = listOf(
        Triple(Icons.Filled.TouchApp, "Заявка", "Выбор путешествия и контактные данные - дальше менеджер связывается в рабочее время."),
        Triple(Icons.Filled.Description, "Всё официально", "Составляем договор и гарантируем, что поездка состоится в соответствии с выбранным маршрутом."),
        Triple(Icons.Filled.Payments, "Предоплата", "Всего 20% от стоимости тура при бронировании, 30% - за 50 дней до тура, остальное - в первый день тура."),
        Triple(Icons.AutoMirrored.Filled.Chat, "Чат участников", "Добавим в чат с другими участниками, поделимся важной информацией и будем ждать встречи."),
    )
    SheetColumn(spacing = 20) {
        TwoLineTitle("УСЛОВИЯ", "БРОНИРОВАНИЯ")
        Text("Забронируй любое путешествие по предоплате 20%", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { index, (icon, title, details) ->
                val hand = TnaColor.hands[index % TnaColor.hands.size]
                val shape = RoundedCornerShape(20.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp)) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(hand.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = hand, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${index + 1}", style = displayStyle(16, TnaColor.subtle))
                            Spacer(Modifier.width(8.dp))
                            Text(title.uppercase(), style = displayStyle(18))
                        }
                        Text(details, color = TnaColor.subtle, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Text(
            "Услуга не является туроператорской деятельностью. Под туром подразумевается разовая помощь в организации поездки по договору поручения. ИП Козикова Анна Петровна.",
            color = TnaColor.subtle.copy(alpha = 0.8f),
            fontSize = 11.sp,
        )
    }
}

@Composable
fun ReviewsSheet(app: AppModel) {
    SheetColumn(spacing = 20) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ЧТО ПИШУТ ЛЮДИ", style = displayStyle(30))
            Text("О ПУТЕШЕСТВИЯХ С НАМИ", style = displayBrushStyle(20))
        }
        if (app.reviews.isEmpty()) {
            EmptyState(Icons.Filled.StarRate, "Пока нет отзывов", "Будь первым - расскажи о своей поездке после возвращения.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { app.reviews.forEach { ReviewCard(it, showTour = true) } }
        }
    }
}

@Composable
fun ContactsSheet(app: AppModel) {
    val context = LocalContext.current
    data class Contact(val icon: ImageVector, val color: Color, val title: String, val value: String, val url: String)
    val contacts = listOf(
        Contact(Icons.Filled.Phone, TnaColor.hands[2], "Офис", "+7 495 151 69 80", "tel:+74951516980"),
        Contact(Icons.Filled.Phone, TnaColor.hands[2], "Мобильный", "+7 901 078 61 04", "tel:+79010786104"),
        Contact(Icons.AutoMirrored.Filled.Send, TnaColor.hands[3], "Чат с менеджером", "t.me/TNAtours", "https://t.me/TNAtours"),
        Contact(Icons.AutoMirrored.Filled.Send, TnaColor.hands[3], "Канал с анонсами", "t.me/travelneveralone", "https://t.me/travelneveralone"),
        Contact(Icons.Filled.Email, TnaColor.hands[0], "Почта", "support@travelneveralone.com", "mailto:support@travelneveralone.com"),
        Contact(Icons.Filled.Language, TnaColor.hands[4], "Сайт", "travelneveralone.com", "https://travelneveralone.com"),
    )
    SheetColumn(spacing = 20) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(52, app.isProd)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("КОНТАКТЫ", style = displayStyle(34))
                Text("Компания для путешествий есть всегда!", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contacts.forEach { c ->
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
                        .pressable(0.98f) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(c.icon, null, tint = c.color, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.title, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(c.value, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TnaColor.subtle, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text("TNA - заботливый проводник в мир путешествий", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
fun AboutSheet(app: AppModel) {
    SheetColumn(spacing = 22) {
        Text("О НАС", style = displayStyle(30))
        Text(
            "Travel Never Alone - авторские туры от Ани и команды. Мы возим небольшими группами в места силы: Камчатка, Байкал, Кольский, Сокотра и не только. Наш принцип - «путешествуй не один»: с нами уезжают в одиночку, а возвращаются со своими людьми.",
            color = TnaColor.subtle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        if (app.role.canBookAndEarn) {
            Text("ЧЕМ ЕЩЁ ПОМОЖЕМ", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OfferCard(Icons.Filled.Map, "Индивидуальный тур", "Маршрут под тебя: свои даты, темп и компания.") { app.sheet = Sheet.Individual }
                OfferCard(Icons.Filled.Groups, "Корпоративный тур", "Выезд для команды: тимбилдинг с характером.") {
                    app.openSupportChat("Здравствуйте! Интересует корпоративный тур для команды 🏔")
                }
                OfferCard(Icons.Filled.CardGiftcard, "Подарочный сертификат", "Подари путешествие: номинал выбираешь сам.") {
                    app.requireAccount { app.sheet = Sheet.Certificate }
                }
            }
        }
    }
}

@Composable
private fun OfferCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).pressable(0.98f, onClick = onClick).padding(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TnaColor.subtle, fontSize = 13.sp)
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Написать в чат", color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = TnaColor.accent, modifier = Modifier.size(11.dp))
            }
        }
    }
}

// MARK: - Individual tour

/// iOS IndividualRequestViewModel: destination, own dates, people, referral, certificate.
private class IndividualRequest {
    var destination by mutableStateOf("")
    var name by mutableStateOf("")
    var phone by mutableStateOf("+7 ")
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    var datesUnknown by mutableStateOf(false)
    var peopleCount by mutableIntStateOf(1)
    var friendCode by mutableStateOf("")
    var alreadyReferred by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var done by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val cert = CertificateRedemption()

    val hasRange: Boolean
        get() {
            val s = startDate ?: return false
            val e = endDate ?: return false
            return !e.isBefore(s)
        }
    val canSubmit: Boolean
        get() = destination.isNotBlank() && name.trim().length >= 2 && phone.count { it.isDigit() } >= 10 && (datesUnknown || hasRange) && !submitting
    val datesText: String
        get() = if (datesUnknown) "по договорённости" else {
            val s = startDate
            val e = endDate
            if (s != null && e != null) BookingForm.rangeLabel(s, e) else "не выбраны"
        }
}

@Composable
fun IndividualSheet(app: AppModel) {
    val form = remember { IndividualRequest() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (app.account.phone.isNotBlank()) form.phone = app.account.phone
        form.name = app.account.name
        if (app.account.isRegistered) {
            runCatching { app.repo.fetchProfile() }.getOrNull()?.let { d ->
                val full = listOf(d.lastName, d.firstName, d.patronymic).filter { it.isNotBlank() }.joinToString(" ")
                if (full.isNotBlank()) form.name = full
            }
            runCatching { app.repo.bonusSummary() }.getOrNull()?.let { form.alreadyReferred = it.bound }
        }
    }
    if (form.done) {
        Column(Modifier.fillMaxWidth().padding(24.dp).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BrandMark(90, app.isProd)
            Text("ЗАЯВКА У АНИ!", style = displayStyle(30))
            Text("Соберём маршрут «${form.destination}» и свяжемся в рабочее время", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Gap(20)
            PrimaryButton("Отлично!", icon = Icons.Filled.ThumbUp) { app.sheet = null }
        }
        return
    }
    SheetColumn {
        TwoLineTitle("ИНДИВИДУАЛЬНЫЙ", "ТУР", 32, 32)
        Text("Выбери место, а всё остальное организуем мы. Маршрут под твои интересы - для себя, своей компании или корпоратива.", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingField("Куда хочешь?", form.destination) { form.destination = it }
            CustomDatesBox(
                unknown = form.datesUnknown,
                onUnknown = { form.datesUnknown = it },
                start = form.startDate,
                end = form.endDate,
                onRange = { s, e -> form.startDate = s; form.endDate = e },
                unknownNote = "Ничего страшного - подберём даты вместе, когда обсудим маршрут.",
            )
            PeopleStepper(form.peopleCount, 1..30, null) { form.peopleCount = it }
            BookingField("Имя", form.name) { form.name = it }
            BookingField("Телефон", form.phone, keyboard = KeyboardType.Phone) { form.phone = it }
            if (!form.alreadyReferred) {
                BookingField("Код друга", form.friendCode, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters) { form.friendCode = it }
                Text("6 000 ₽ скидкой на первую поездку. Код можно назвать и менеджеру.", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            CertificateBox(form.cert) { app.checkCertificate(form.cert) }
        }
        form.error?.let { Text(it, color = Color.hex(0xE85C68), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        SubmitButton("Подобрать авторский тур", busy = form.submitting, enabled = form.canSubmit) {
            form.submitting = true
            form.error = null
            scope.launch {
                val result = runCatching {
                    val code = form.friendCode.trim()
                    if (code.isNotEmpty() && !form.alreadyReferred) {
                        val applied = app.repo.applyReferral(code)
                        if (applied != ReferralResult.Ok && applied != ReferralResult.Already) throw TnaException(applied.userMessage)
                        form.alreadyReferred = true
                    }
                    val details = listOfNotNull("Направление: ${form.destination}", "Даты: ${form.datesText}", form.cert.detailsLine)
                    app.repo.submitBooking(
                        BookingRequest(
                            tourName = "Индивидуальный тур: ${form.destination}",
                            departureLabel = form.datesText,
                            name = form.name.trim(),
                            phone = PhoneNumber.e164(form.phone),
                            peopleCount = form.peopleCount.coerceIn(1, 30),
                            details = details,
                        )
                    )
                }
                form.submitting = false
                result.onSuccess { form.done = true }.onFailure { form.error = it.message ?: "Заявка не ушла. Проверь связь и отправь ещё раз." }
            }
        }
        Text("#путешествуй_по_своим_правилам", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ConsentFooter()
    }
}

// MARK: - Gift certificate

private class CertificatePurchase {
    var amount by mutableStateOf("15000")
    var recipient by mutableStateOf("")
    var wish by mutableStateOf("")
    var name by mutableStateOf("")
    var phone by mutableStateOf("+7 ")
    var stage by mutableStateOf("form")
    var certificate by mutableStateOf<GiftCertificate?>(null)
    var mine by mutableStateOf<List<GiftCertificate>>(emptyList())
    var submitting by mutableStateOf(false)
    var checking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val amountValue: Int get() = amount.filter { it.isDigit() }.toIntOrNull() ?: 0
    val canSubmit: Boolean
        get() = amountValue in 1_000..1_000_000 && recipient.trim().length >= 2 && name.trim().length >= 2 && phone.count { it.isDigit() } >= 10 && !submitting
}

@Composable
fun CertificateSheet(app: AppModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { CertificatePurchase() }
    var viewing by remember { mutableStateOf<GiftCertificate?>(null) }
    LaunchedEffect(Unit) {
        state.name = app.account.name
        if (app.account.phone.isNotBlank()) state.phone = app.account.phone
        state.mine = app.repo.myCertificates()
    }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun check() {
        val id = state.certificate?.id ?: return
        if (state.checking) return
        state.checking = true
        scope.launch {
            app.repo.certificateStatus(id)?.let { fresh ->
                state.certificate = fresh
                when (fresh.status) {
                    "paid" -> { state.stage = "done"; state.mine = app.repo.myCertificates() }
                    "pending" -> {}
                    else -> { state.stage = "form"; state.error = "Оплата не прошла" }
                }
            }
            state.checking = false
        }
    }
    viewing?.let { cert ->
        SheetColumn {
            Text("← Назад", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { viewing = null })
            CertificateCard(cert, app.isProd)
            if (cert.status == "paid") ShareCertificate(cert)
        }
        return
    }
    when (state.stage) {
        "awaiting" -> Column(Modifier.fillMaxWidth().padding(24.dp).padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator(color = TnaColor.accent)
            Text("ЖДЁМ ОПЛАТУ", style = displayStyle(30))
            Text("Страница оплаты открылась в браузере. Вернись в приложение после оплаты - сертификат активируется сам.", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Gap(20)
            PrimaryButton(if (state.checking) "Проверяем…" else "Я оплатил(а)", enabled = !state.checking, icon = Icons.Filled.CheckCircle) { check() }
            Text("Открыть страницу оплаты ещё раз", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.pressable { state.certificate?.paymentUrl?.let { open(it) } }.padding(6.dp))
        }
        "done" -> SheetColumn {
            state.certificate?.let { cert ->
                CertificateCard(cert, app.isProd)
                if (cert.status == "paid") ShareCertificate(cert)
                else {
                    Text("Оплата ещё не подтвердилась. Проверить можно через минуту.", color = TnaColor.subtle, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(if (state.checking) "Проверяем…" else "Проверить оплату", enabled = !state.checking, icon = Icons.Filled.Refresh) { check() }
                }
            }
            PrimaryButton("Готово", icon = Icons.Filled.ThumbUp) { app.sheet = null }
        }
        else -> SheetColumn {
            TwoLineTitle("ПОДАРОЧНЫЙ", "СЕРТИФИКАТ 🎁", 32, 32)
            Text("Электронный сертификат с уникальным номером и PIN-кодом. Оплати онлайн - и сразу получишь карточку, которую можно отправить получателю. Действует 1 год.", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Номинал", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TnaColor.surface).border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = state.amount,
                        onValueChange = { state.amount = it.filter { c -> c.isDigit() }.take(7) },
                        singleLine = true,
                        textStyle = displayStyle(32),
                        cursorBrush = SolidColor(TnaColor.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Text("₽", style = displayStyle(32, TnaColor.subtle))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10_000, 15_000, 25_000, 50_000, 100_000).forEach { p ->
                        Chip(Money.grouped(p), state.amountValue == p) { state.amount = p.toString() }
                    }
                }
            }
            BookingField("Кому (имя на сертификате)", state.recipient) { state.recipient = it }
            BookingField("Пожелание (необязательно)", state.wish) { state.wish = it }
            Text("Твои контакты (для чека)", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            BookingField("Имя", state.name) { state.name = it }
            BookingField("Телефон", state.phone, keyboard = KeyboardType.Phone) { state.phone = it }
            state.error?.let { Text(it, color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            SubmitButton("Оплатить ${Money.grouped(state.amountValue)} ₽", busy = state.submitting, enabled = state.canSubmit) {
                state.submitting = true
                state.error = null
                scope.launch {
                    val cert = runCatching {
                        app.repo.buyCertificate(state.amountValue, state.recipient, state.wish, state.name, PhoneNumber.e164(state.phone))
                    }.getOrNull()
                    state.submitting = false
                    when {
                        cert == null -> state.error = "Не получилось оформить - можно повторить"
                        cert.status == "paid" -> { state.certificate = cert; state.stage = "done" }
                        cert.paymentUrl != null -> { state.certificate = cert; state.stage = "awaiting"; open(cert.paymentUrl) }
                        else -> state.error = "Оплата не открылась - можно повторить"
                    }
                    state.mine = app.repo.myCertificates()
                }
            }
            if (state.mine.isNotEmpty()) {
                Text("Мои сертификаты", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                state.mine.forEach { cert ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.surface).pressable(0.98f) { viewing = cert }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CardGiftcard, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cert.code ?: "-", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(cert.amountText, cert.statusTitle, cert.expiresText).joinToString(" · "), color = TnaColor.subtle, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                    }
                }
            }
            ConsentFooter()
        }
    }
    // Returning from the browser: check the payment like iOS does on scenePhase.active.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && state.stage == "awaiting") check()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun CertificateCard(cert: GiftCertificate, prod: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(TnaColor.ink, Color.hex(0x16264F)))).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(40, prod)
            Spacer(Modifier.weight(1f))
            Text(if (cert.status == "paid") "АКТИВЕН" else "ЖДЁТ ОПЛАТЫ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
        Text("ПОДАРОЧНЫЙ СЕРТИФИКАТ", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(cert.amountText, style = displayBrushStyle(44))
        if (cert.recipientName.isNotBlank()) Text("Для: ${cert.recipientName}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (cert.wish.isNotBlank()) Text("«${cert.wish}»", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        Row {
            Column(Modifier.weight(1f)) {
                Text("Номер", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(cert.code ?: "-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column {
                Text("PIN", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(cert.pin.ifBlank { "-" }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(listOfNotNull(cert.statusTitle, cert.expiresText).joinToString(" · "), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun ShareCertificate(cert: GiftCertificate) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Поделиться", icon = Icons.Filled.Share) {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, cert.shareText) }
            context.startActivity(Intent.createChooser(send, null))
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(TnaColor.surface).border(1.dp, TnaColor.stroke, RoundedCornerShape(16.dp)).pressable {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Подарочный сертификат TNA ${cert.code.orEmpty()}")
                    putExtra(Intent.EXTRA_TEXT, cert.shareText)
                }
                runCatching { context.startActivity(mail) }
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Email, null, tint = TnaColor.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Отправить по почте", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// MARK: - Notifications

@Composable
fun NotificationsSheet(app: AppModel) {
    val p = app.prefsState
    SheetColumn(spacing = 16) {
        Text("УВЕДОМЛЕНИЯ", style = displayStyle(28))
        PrefsCard { ToggleRow("Все уведомления", "Главный выключатель", p.allEnabled) { app.savePrefs(p.copy(allEnabled = it)) } }
        Column(Modifier.alpha(if (p.allEnabled) 1f else 0.45f)) {
            PrefsCard {
                ToggleRow("Новости", null, p.news, p.allEnabled) { app.savePrefs(p.copy(news = it)) }
                RowDividerFull()
                ToggleRow("Туры и заезды", null, p.tours, p.allEnabled) { app.savePrefs(p.copy(tours = it)) }
                RowDividerFull()
                ToggleRow("Сообщения в чатах", null, p.chat, p.allEnabled) { app.savePrefs(p.copy(chat = it)) }
                RowDividerFull()
                ToggleRow("Достижения", null, p.achievements, p.allEnabled) { app.savePrefs(p.copy(achievements = it)) }
                RowDividerFull()
                ToggleRow("Акции и скидки", null, p.promos, p.allEnabled) { app.savePrefs(p.copy(promos = it)) }
            }
        }
        Text("Полностью отключить пуши можно в настройках Android.", color = TnaColor.subtle, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Text("ДАННЫЕ", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        PrefsCard {
            ToggleRow("Помогать улучшать приложение", "Обезличенная статистика: какие экраны открываются и где приложение тормозит", p.analytics) {
                app.savePrefs(p.copy(analytics = it))
            }
        }
        Text("Мы не собираем переписку, телефон, имя и точное местоположение - только то, как работает само приложение. Выключишь - перестанем.", color = TnaColor.subtle, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun PrefsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(horizontal = 14.dp), content = content)
}

@Composable
private fun RowDividerFull() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, on: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = TnaColor.subtle, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = on,
            onCheckedChange = change,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = TnaColor.accent, checkedThumbColor = Color.White, uncheckedTrackColor = TnaColor.elevated, uncheckedBorderColor = TnaColor.stroke),
        )
    }
}

// MARK: - Companions

// MARK: - Countries

// MARK: - Compass

@Composable
fun CompassSheet(app: AppModel) {
    var step by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(QuizAnswers()) }
    var phase by remember { mutableStateOf("quiz") }
    val ranked = remember(phase) { if (phase == "quiz") emptyList() else TourMatchEngine.rank(app.tours, answers) }
    LaunchedEffect(phase) { if (phase == "spinning") { delay(1800); phase = "result" } }
    fun advance() { if (step < 3) step++ else phase = "spinning" }
    fun open(tour: Tour) { app.sheet = null; app.openTour = tour }
    when (phase) {
        "spinning" -> Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val spin = rememberInfiniteTransition(label = "compass")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Restart), label = "angle")
            Icon(Icons.Filled.Explore, null, tint = TnaColor.accent, modifier = Modifier.size(110.dp).rotate(angle))
            Text("Ищем твоё место силы…", color = TnaColor.subtle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        "result" -> SheetColumn {
            val best = ranked.firstOrNull()
            if (best == null) {
                EmptyState(Icons.Filled.Explore, "Каталог ещё грузится", "Попробуй через минуту.")
                return@SheetColumn
            }
            Text("ТВОЙ ТУР", style = displayStyle(30), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(26.dp)).pressable(0.98f) { open(best.tour) }) {
                TourCover(best.tour.imageUrl, best.tour.colors, Modifier.matchParentSize())
                if (best.tour.imageUrl == null) SymbolWatermark(best.tour.symbol, 130, 90, -60, -12f, 0.13f)
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("совпадение ${best.percent}%", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clip(CircleShape).background(Color.hex(0x9CFF57)).padding(horizontal = 10.dp, vertical = 5.dp))
                    Text(best.tour.name.uppercase(), style = displayStyle(40, Color.White))
                    Text(best.tour.tagline, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ranked.drop(1).take(2).forEach { m ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(TnaColor.surface).border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp))
                            .pressable { open(m.tour) }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(m.tour.name, color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${m.percent}%", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            PrimaryButton("Смотреть тур", icon = Icons.AutoMirrored.Filled.ArrowForward) { open(best.tour) }
            Text("Пройти ещё раз", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().pressable { answers = QuizAnswers(); step = 0; phase = "quiz" })
        }
        else -> SheetColumn(spacing = 22) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧭 TNA КОМПАС", style = displayStyle(28), modifier = Modifier.weight(1f))
                Text("${step + 1}/4", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            ProgressBar((step + 1) / 4f)
            val question = listOf("Какой вайб зовёт?", "Когда едем?", "Насколько жёстко?", "Бюджет?")[step]
            Text(question, style = displayStyle(34))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> QuizVibe.entries.forEach { v -> QuizOption("${v.emoji} ${v.title}", answers.vibe == v) { answers = answers.copy(vibe = v); advance() } }
                    1 -> QuizSeason.entries.forEach { v -> QuizOption(v.title, answers.season == v) { answers = answers.copy(season = v); advance() } }
                    2 -> QuizEffort.entries.forEach { v -> QuizOption(v.title, answers.effort == v) { answers = answers.copy(effort = v); advance() } }
                    else -> QuizBudget.entries.forEach { v -> QuizOption(v.title, answers.budget == v) { answers = answers.copy(budget = v); advance() } }
                }
            }
        }
    }
}

@Composable
private fun QuizOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) TnaColor.ink else TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
            .pressable(0.98f, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Color.White else TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = if (selected) Color.White else TnaColor.subtle, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Schedule

@Composable
fun ScheduleSheet(app: AppModel) {
    var season by remember { mutableStateOf<String?>(null) }
    val seasons = listOf("Лето", "Осень", "Зима", "Весна").filter { s -> app.schedule.any { it.season == s } }
    val rows = app.schedule.filter { season == null || it.season == season }
    SheetColumn(spacing = 20) {
        Text("КАЛЕНДАРЬ", style = displayStyle(28))
        if (seasons.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeasonChip("Все", season == null) { season = null }
                seasons.forEach { s -> SeasonChip(s, season == s) { season = s } }
            }
        }
        if (app.schedule.isEmpty()) {
            when (app.scheduleState) {
                "loading" -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TnaColor.accent) }
                "failed" -> EmptyState(Icons.Filled.CalendarMonth, null, "Не удалось загрузить календарь")
                else -> EmptyState(Icons.Filled.CalendarMonth, null, "Пока нет заездов")
            }
        }
        rows.groupBy { it.month }.toSortedMap().forEach { (month, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(monthTitle(month).uppercase(), color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                items.sortedBy { it.startsOn }.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.surface)
                            .pressable(0.98f) { app.openTourById(item.tourId) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val colors = item.colors.map { Color.hex(it) }.let { if (it.size >= 2) it else listOf(TnaColor.accent, TnaColor.accent) }
                        Box(Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(colors)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.tourName, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(item.label, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            when {
                                item.isSoldOut -> "нет мест"
                                item.spotsLeft <= 3 -> "🔥 осталось ${item.spotsLeft}"
                                else -> "мест: ${item.spotsLeft}"
                            },
                            color = if (item.isSoldOut) TnaColor.subtle else TnaColor.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(title: String, active: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (active) Color.White else TnaColor.ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(if (active) TnaColor.accent else TnaColor.surface).pressable(0.95f, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

// MARK: - Trip and programme

@Composable
fun TripSheet(app: AppModel, trip: Trip) {
    val tour = app.tours.firstOrNull { it.backendId == trip.tourId || it.id == trip.tourId }
    if (tour != null) {
        ProgramSheet(app, tour, trip)
    } else {
        SheetColumn {
            Text(trip.tourName.uppercase(), style = displayStyle(32))
            Text(listOf(trip.label, trip.countdown).filter { it.isNotBlank() }.joinToString(" · "), color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            EmptyState(Icons.Filled.Map, null, "Программа скоро появится")
        }
    }
}

/// Day-by-day plan of a tour, dated from the traveller's departure, with SOS contacts.
@Composable
fun ProgramSheet(app: AppModel, tour: Tour, trip: Trip? = null) {
    val start = trip?.startsOn
    val today = LocalDate.now()
    var expanded by remember(tour.id) {
        mutableStateOf(tour.program.filter { start?.plusDays((it.day - 1).toLong()) == today }.map { it.id }.toSet())
    }
    var contacts by remember { mutableStateOf<List<TripContact>>(emptyList()) }
    LaunchedEffect(trip?.id) {
        if (trip != null && app.account.isRegistered) contacts = app.repo.departureContacts(trip.id)
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            TourCover(tour.imageUrl, tour.colors, Modifier.matchParentSize())
            if (tour.imageUrl == null) SymbolWatermark(tour.symbol, 150, 80, -60, -10f)
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tour.name.uppercase(), style = displayStyle(40, Color.White), maxLines = 2)
                val line = listOfNotNull(trip?.label?.takeIf { it.isNotBlank() }, "${tour.days} ${pluralRU(tour.days, "день", "дня", "дней")}", if (tour.km > 0) "${Money.grouped(tour.km)} км" else null)
                Text(line.joinToString(" · "), color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (contacts.isNotEmpty()) {
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
                contacts.forEachIndexed { index, c ->
                    Row(Modifier.fillMaxWidth().pressable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(PhoneNumber.pretty(c.phone), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Phone, "Позвонить", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < contacts.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                }
                Text("Работает без интернета - сохранено на устройстве", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        SectionTitle("Программа заезда", "#день_за_днём")
        if (tour.program.isEmpty()) {
            EmptyState(Icons.Filled.Map, null, "Программа скоро появится")
        } else {
            val color = tour.colors.firstOrNull()?.let { Color.hex(it) } ?: TnaColor.accent
            val format = java.time.format.DateTimeFormatter.ofPattern("d MMM, EE", java.util.Locale("ru"))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tour.program.forEach { day ->
                    val date = start?.plusDays((day.day - 1).toLong())
                    DayCard(day, color, expanded.contains(day.id), date?.let { format.format(it) }, isToday = date == today) {
                        expanded = if (expanded.contains(day.id)) expanded - day.id else expanded + day.id
                    }
                }
            }
        }
    }
}

