package com.travelneveralone.tna

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

// Port of TNAiOS Features/Booking (BookingSheet, RequestFormBoxes, RangeCalendar).

@Composable
fun BookingSheet(app: AppModel) {
    val form = app.form ?: return
    if (form.isDone) {
        BookingSuccess(app, form)
        return
    }
    Column(Modifier.fillMaxWidth().background(TnaColor.elevated)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        form.isWaitlist -> "ЛИСТ ОЖИДАНИЯ"
                        form.isCustomDates -> "ЧАСТНЫЙ ТУР"
                        else -> "ЗАЯВКА НА ТУР"
                    },
                    style = displayStyle(30),
                )
                Text("${form.tour.name} · ${form.departureText}", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (form.isCustomDates) {
                    CustomDatesBox(
                        unknown = form.datesUnknown,
                        onUnknown = { form.datesUnknown = it },
                        start = form.startDate,
                        end = form.endDate,
                        onRange = { s, e -> form.startDate = s; form.endDate = e },
                        tourDays = form.tour.days,
                    )
                }
                BookingField("Имя", form.name) { form.name = it }
                BookingField("Телефон", form.phone, keyboard = KeyboardType.Phone) { form.phone = it }
                if (!form.isWaitlist) {
                    PeopleStepper(form.peopleCount, 1..form.maxPeople, form.seatLimitNote) { form.peopleCount = it }
                }
                if (form.canAttachCompanions && app.account.isRegistered) CompanionsRow(app, form)
                if (form.showsReferralField) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BookingField("Код друга", form.friendCode, capitalization = KeyboardCapitalization.Characters) { form.friendCode = it }
                        Text("6 000 ₽ скидкой на первую поездку. Код можно назвать и менеджеру.", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (form.canUseCertificate) CertificateBox(form.cert, onLoad = { app.loadMyCertificates(form.cert) }, onPick = { app.pickCertificate(form.cert, it) }) { app.checkCertificate(form.cert) }
            }
            form.errorMessage?.let { Text(it, color = TnaColor.fire, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TnaColor.surface).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (form.isWaitlist) Icons.Filled.NotificationsActive else Icons.Filled.Schedule, null, tint = TnaColor.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (form.isWaitlist) "Мест сейчас нет. Встанешь в лист - напишем, как только освободится место."
                    else "Менеджер напишет в рабочее время и расскажет всё о туре",
                    color = TnaColor.subtle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubmitButton(
                title = if (form.isWaitlist) "Встать в лист ожидания" else "Отправить заявку",
                busy = form.isSubmitting,
                enabled = form.canSubmit,
            ) { app.submitBooking() }
            ConsentFooter()
        }
    }
}

/// PrimaryButton with a spinner while the request is in flight.
@Composable
fun SubmitButton(title: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(TnaColor.accentBrush)
            .then(if (enabled || busy) Modifier else Modifier.background(TnaColor.bg.copy(alpha = 0.5f)))
            .pressable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BookingSuccess(app: AppModel, form: BookingForm) {
    Column(
        Modifier.fillMaxWidth().background(TnaColor.elevated).padding(horizontal = 24.dp).padding(top = 30.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
            Box(Modifier.size(170.dp).clip(CircleShape).border(2.dp, Color.hex(0x66BB6A).copy(alpha = 0.4f), CircleShape))
            Box(Modifier.size(140.dp).clip(CircleShape).background(Color.hex(0x2E7D32).copy(alpha = 0.18f)))
            Icon(Icons.Filled.Verified, null, tint = Color.hex(0x66BB6A), modifier = Modifier.size(72.dp))
        }
        Text(if (form.isWaitlist) "ТЫ В СПИСКЕ!" else "ЗАЯВКА ОТПРАВЛЕНА!", style = displayStyle(30), textAlign = TextAlign.Center)
        Text(
            if (form.isWaitlist) "Напишем, как только освободится место на «${form.tour.name} · ${form.departureText}»"
            else "Аня уже видит твою заявку на «${form.tour.name} · ${form.departureText}» и скоро напишет",
            color = TnaColor.subtle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Gap(12)
        if (app.account.isRegistered) {
            PrimaryButton("В чат с Аней", icon = Icons.AutoMirrored.Filled.Chat) { app.openManagerChat() }
        } else {
            Text("Подтверди номер, чтобы видеть статус заявки и писать Ане", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            PrimaryButton("Подтвердить номер", icon = Icons.Filled.VerifiedUser) {
                app.loginPhone = form.phone
                app.loginStep = 0
                app.loginError = null
                app.sheet = Sheet.Login
            }
        }
        Text("Позже", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { app.sheet = null }.padding(8.dp))
    }
}

/// Label above, value field below, on a white rounded card (iOS `BookingField`).
@Composable
fun BookingField(
    title: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(TnaColor.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun PeopleStepper(count: Int, range: IntRange, note: String?, onChange: (Int) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val atMax = count >= range.last
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Сколько человек", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("$count", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            StepButton(Icons.Filled.Remove, count > range.first) { onChange((count - 1).coerceAtLeast(range.first)) }
            Spacer(Modifier.width(18.dp))
            StepButton(Icons.Filled.Add, count < range.last) { onChange((count + 1).coerceAtMost(range.last)) }
        }
        if (note != null) {
            Text(note, color = if (atMax) TnaColor.accent else TnaColor.subtle, fontSize = 12.sp, fontWeight = if (atMax) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(TnaColor.elevated).pressable(0.9f, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) TnaColor.accent else TnaColor.subtle.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) }
}

@Composable
private fun CompanionsRow(app: AppModel, form: BookingForm) {
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
                .pressable(0.99f) { picking = !picking }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Group, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Кто едет", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(form.companionSummary, color = if (form.companions.isEmpty()) TnaColor.subtle else TnaColor.accent, fontSize = 12.sp, maxLines = 1)
                Text("Не обязательно сейчас - можно прислать позже в чате", color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Icon(if (picking) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(picking) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(shape).background(TnaColor.surface)) {
                if (app.companions.isEmpty()) {
                    Text(
                        "Попутчиков пока нет. Добавь их в профиле → «Попутчики», и они появятся здесь.",
                        color = TnaColor.subtle,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                app.companions.forEachIndexed { index, person ->
                    val chosen = form.companions.any { it.id == person.id }
                    Row(
                        Modifier.fillMaxWidth().pressable(0.99f) {
                            form.applyCompanions(if (chosen) form.companions.filter { it.id != person.id } else form.companions + person)
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (chosen) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (chosen) TnaColor.accent else TnaColor.subtle.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(person.fullName.ifBlank { "Без имени" }, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (index < app.companions.lastIndex) RowDivider()
                }
            }
        }
    }
}

@Composable
fun ConsentFooter() {
    val linkStyle = TextLinkStyles(SpanStyle(color = TnaColor.accent))
    Text(
        buildAnnotatedString {
            append("Отправка заявки означает согласие с ")
            withLink(LinkAnnotation.Url("https://travelneveralone.com/agreement", linkStyle)) { append("пользовательским соглашением") }
            append(" и ")
            withLink(LinkAnnotation.Url("https://travelneveralone.com/policy", linkStyle)) { append("политикой конфиденциальности") }
        },
        color = TnaColor.subtle,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/// Own dates: either a picked range or an honest "don't know yet". Nothing preselected.
@Composable
fun CustomDatesBox(
    unknown: Boolean,
    onUnknown: (Boolean) -> Unit,
    start: LocalDate?,
    end: LocalDate?,
    onRange: (LocalDate?, LocalDate?) -> Unit,
    tourDays: Int = 0,
    unknownNote: String = "Ничего страшного - подберём даты вместе в чате после заявки.",
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Пока не знаю даты", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = unknown,
                onCheckedChange = onUnknown,
                colors = SwitchDefaults.colors(checkedTrackColor = TnaColor.accent, checkedThumbColor = Color.White),
            )
        }
        if (unknown) {
            Text(unknownNote, color = TnaColor.subtle, fontSize = 13.sp)
        } else {
            RangeCalendar(start, end, tourDays, onRange)
        }
    }
}

private val monthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val monthsShort = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val weekdays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

/// Inline month grid: tap the first day, then the last; collapses once the range is set.
@Composable
fun RangeCalendar(start: LocalDate?, end: LocalDate?, tourDays: Int, onRange: (LocalDate?, LocalDate?) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(start ?: LocalDate.now())) }
    var expanded by remember { mutableStateOf(start == null) }
    var pickingStart by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().pressable(0.99f) { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, null, tint = TnaColor.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            val text = when {
                start == null -> "Выбери даты"
                end == null -> "с ${start.dayOfMonth} ${monthsShort[start.monthValue - 1]}"
                else -> BookingForm.rangeLabel(start, end)
            }
            Text(text, color = if (start == null) TnaColor.subtle else TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp).rotate(chevron))
        }
        val hint = when {
            start == null -> if (tourDays > 0) "Тур занимает $tourDays ${pluralRU(tourDays, "день", "дня", "дней")} - отметь начало и конец" else "Отметь день начала и день окончания"
            end == null -> "Теперь отметь день окончания"
            else -> null
        }
        hint?.let { Text(it, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${monthNames[month.monthValue - 1]} ${month.year}", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { month = month.minusMonths(1) }
                Spacer(Modifier.width(8.dp))
                NavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) { month = month.plusMonths(1) }
            }
            Row {
                weekdays.forEach { Text(it, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            val lead = month.atDay(1).dayOfWeek.value - 1
            val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { day -> DayCell(day, start, end, today, Modifier.weight(1f)) { picked ->
                            if (start != null && end == null && !pickingStart && !picked.isBefore(start)) {
                                onRange(start, picked)
                                pickingStart = true
                                expanded = false
                            } else {
                                onRange(picked, null)
                                pickingStart = false
                            }
                        } }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, start: LocalDate?, end: LocalDate?, today: LocalDate, modifier: Modifier, onTap: (LocalDate) -> Unit) {
    if (day == null) {
        Spacer(modifier.height(40.dp))
        return
    }
    val isEndpoint = day == start || day == end
    val inRange = start != null && end != null && day.isAfter(start) && day.isBefore(end)
    val past = day.isBefore(today)
    Box(
        modifier
            .height(40.dp)
            .then(if (inRange) Modifier.background(TnaColor.accent.copy(alpha = 0.16f)) else Modifier)
            .pressable(0.92f, enabled = !past) { onTap(day) },
        contentAlignment = Alignment.Center,
    ) {
        if (isEndpoint) Box(Modifier.size(38.dp).clip(CircleShape).background(TnaColor.accent))
        Text(
            "${day.dayOfMonth}",
            color = when {
                isEndpoint -> Color.White
                past -> TnaColor.subtle.copy(alpha = 0.4f)
                else -> TnaColor.ink
            },
            fontSize = 15.sp,
            fontWeight = if (isEndpoint) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun NavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(TnaColor.elevated).pressable(0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
    }
}

/// Collapsible certificate number + PIN, verified on the server.
@Composable
fun CertificateBox(
    cert: CertificateRedemption,
    onLoad: () -> Unit = {},
    onPick: (GiftCertificate) -> Unit = {},
    onCheck: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onLoad() }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().pressable(0.99f) { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CardGiftcard, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("У меня есть сертификат", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp).rotate(chevron))
        }
        if (expanded) {
            if (cert.mine.isNotEmpty()) {
                Text("Твои сертификаты", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                cert.mine.forEach { mine ->
                    val picked = cert.isPicked(mine)
                    val pickShape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(pickShape)
                            .background(if (picked) TnaColor.accent.copy(alpha = 0.12f) else TnaColor.bg)
                            .border(1.dp, if (picked) TnaColor.accent else TnaColor.stroke, pickShape)
                            .pressable(0.99f, enabled = !cert.isChecking) { onPick(mine) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (picked) Icons.Filled.CheckCircle else Icons.Filled.CardGiftcard, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(mine.code ?: "", color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        Text(mine.amountText, color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text("или введи номер и PIN вручную", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            BookingField("Номер (TNA-XXXX-XXXX)", cert.code, capitalization = KeyboardCapitalization.Characters) { cert.updateCode(it) }
            BookingField("PIN", cert.pin, keyboard = KeyboardType.NumberPassword) { cert.updatePin(it) }
            val status = cert.statusText
            if (status != null) {
                Text(status, color = if (cert.applied) Color.hex(0x2E9E5B) else TnaColor.fire, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TnaColor.accent.copy(alpha = if (cert.canCheck) 0.12f else 0.06f))
                        .pressable(enabled = cert.canCheck, onClick = onCheck).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (cert.isChecking) "Проверяем…" else "Проверить сертификат",
                        color = TnaColor.accent.copy(alpha = if (cert.canCheck || cert.isChecking) 1f else 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
