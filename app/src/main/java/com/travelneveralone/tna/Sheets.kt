package com.travelneveralone.tna

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BookingSheet(app: AppModel) {
    val form = app.form
    if (form == null) {
        Text("Заявка", style = displayStyle(28))
        return
    }
    var name by remember { mutableStateOf(form.name) }
    var phone by remember { mutableStateOf(form.phone) }
    var datesUnknown by remember { mutableStateOf(form.datesUnknown) }
    form.name = name
    form.phone = phone
    form.datesUnknown = datesUnknown
    val ready = name.trim().length >= 2 && phone.count { it.isDigit() } >= 10 &&
        (!form.isCustomDates || datesUnknown)
    Text(if (form.isWaitlist) "ЛИСТ ОЖИДАНИЯ" else "ЗАЯВКА", style = displayStyle(28))
    Text(form.tour.name, color = TnaColor.subtle)
    Text(form.departureText, fontWeight = FontWeight.Bold, color = TnaColor.ink)
    form.seatLimitNote?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp) }
    Gap(12)
    Field("Имя", name) { name = it }
    Gap(8)
    Field("Телефон", phone) { phone = it }
    if (form.isCustomDates) {
        Gap(8)
        Row(Modifier.fillMaxWidth().clickable { datesUnknown = !datesUnknown }) {
            Text(if (datesUnknown) "☑" else "☐", color = TnaColor.accent)
            Text("  Даты обсудим с менеджером", color = TnaColor.ink)
        }
    }
    app.bookingError?.let { Text(it, color = Color.hex(0xE0534E), modifier = Modifier.padding(top = 8.dp)) }
    Gap(16)
    if (app.bookingDone) {
        Text("Заявка ушла. Менеджер ответит в рабочее время.", color = TnaColor.ink, fontWeight = FontWeight.SemiBold)
    } else {
        PrimaryButton(if (app.bookingBusy) "Отправляем…" else if (form.isWaitlist) "Встать в лист" else "Отправить заявку", ready && !app.bookingBusy) {
            app.submitBooking()
        }
    }
}

@Composable
fun LoginSheet(app: AppModel) {
    Text(if (app.loginStep == 0) "ВХОД" else "КОД ИЗ СМС", style = displayStyle(28))
    Text("Номер телефона. Код придёт сообщением.", color = TnaColor.subtle)
    Gap(12)
    if (app.loginStep == 0) {
        Field("Телефон", app.loginPhone) { app.loginPhone = it }
        if (!app.isProd) {
            Gap(10)
            Text("Быстрый вход на бете", color = TnaColor.subtle, fontSize = 12.sp)
            Mock.testLogins.forEach { login ->
                Text(login.label, color = TnaColor.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp).clickable {
                    app.loginPhone = login.phone
                    app.loginCode = login.code
                })
            }
        }
        Gap(12)
        PrimaryButton(if (app.loginBusy) "…" else "Получить код", !app.loginBusy) { app.requestCode() }
    } else {
        Field("Код", app.loginCode) { app.loginCode = it }
        Gap(12)
        PrimaryButton(if (app.loginBusy) "…" else "Войти", app.loginCode.length >= 4 && !app.loginBusy) { app.verifyCode() }
    }
    app.loginError?.let { Text(it, color = Color.hex(0xE0534E), modifier = Modifier.padding(top = 8.dp)) }
}

@Composable
fun NewsSheet(app: AppModel) {
    Text("НОВОСТИ", style = displayStyle(28))
    val context = LocalContext.current
    app.news.forEach { item ->
        Gap(12)
        Card(Modifier.clickable {
            when (val link = item.link) {
                is NewsLink.TourLink -> app.openTourById(link.id)
                is NewsLink.UrlLink -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                null -> Unit
            }
        }) {
            if (item.isPinned) Text("ЗАКРЕПЛЕНО", color = TnaColor.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(item.title, fontWeight = FontWeight.Bold, color = TnaColor.ink)
            Text(item.body, color = TnaColor.subtle)
            Text(item.date, color = TnaColor.subtle, fontSize = 12.sp)
        }
    }
    if (app.news.isEmpty()) Text("Пока пусто", color = TnaColor.subtle)
}

@Composable
fun BookingsSheet(app: AppModel) {
    Text("МОИ БРОНИ", style = displayStyle(28))
    if (app.bookings.isEmpty()) Text("Заявок пока нет", color = TnaColor.subtle, modifier = Modifier.padding(top = 8.dp))
    app.bookings.forEach { booking ->
        Gap(10)
        Card {
            Text(booking.title, fontWeight = FontWeight.Bold, color = TnaColor.ink)
            Text(booking.subtitle, color = TnaColor.subtle)
            Text(booking.status.label, color = Color.hex(booking.status.color), fontWeight = FontWeight.SemiBold)
            booking.moneyNote?.let { Text(it, color = TnaColor.ink, fontSize = 13.sp) }
        }
    }
}

@Composable
fun TermsSheet() {
    Text("УСЛОВИЯ", style = displayStyle(32))
    Text("БРОНИРОВАНИЯ", style = displayStyle(24, TnaColor.accent))
    Text("Забронируй любое путешествие по предоплате 20%", color = TnaColor.subtle, modifier = Modifier.padding(top = 8.dp))
    listOf(
        "Заявка" to "Выбор путешествия и контактные данные — дальше менеджер связывается в рабочее время.",
        "Всё официально" to "Составляем договор и гарантируем, что поездка состоится в соответствии с выбранным маршрутом.",
        "Предоплата" to "Всего 20% от стоимости тура при бронировании, 30% — за 50 дней до тура, остальное — в первый день тура.",
        "Чат участников" to "Добавим в чат с другими участниками, поделимся важной информацией и будем ждать встречи.",
    ).forEach { (title, body) ->
        Gap(12)
        Text(title, fontWeight = FontWeight.Bold, color = TnaColor.ink)
        Text(body, color = TnaColor.subtle)
    }
}

@Composable
fun ReviewsSheet(app: AppModel) {
    Text("ОТЗЫВЫ", style = displayStyle(28))
    app.reviews.forEach { review ->
        Gap(10)
        Card {
            Row(Modifier.fillMaxWidth()) {
                Text(review.author, fontWeight = FontWeight.Bold, color = TnaColor.ink, modifier = Modifier.weight(1f))
                Stars(review.rating)
            }
            review.tourName?.let { Text(it, color = TnaColor.accent, fontSize = 13.sp) }
            Text(review.text, color = TnaColor.ink)
        }
    }
}

@Composable
fun ContactsSheet() {
    Text("КОНТАКТЫ", style = displayStyle(32))
    Text("Компания для путешествий есть всегда!", color = TnaColor.subtle)
    val context = LocalContext.current
    val rows = listOf(
        "Офис" to ("+7 495 151 69 80" to "tel:+74951516980"),
        "Мобильный" to ("+7 901 078 61 04" to "tel:+79010786104"),
        "Чат с менеджером" to ("t.me/TNAtours" to "https://t.me/TNAtours"),
        "Канал с анонсами" to ("t.me/travelneveralone" to "https://t.me/travelneveralone"),
        "Почта" to ("support@travelneveralone.com" to "mailto:support@travelneveralone.com"),
        "Сайт" to ("travelneveralone.com" to "https://travelneveralone.com"),
    )
    rows.forEach { (title, value) ->
        Gap(10)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value.second)))
        }.padding(14.dp)) {
            Text(title, color = TnaColor.subtle, fontSize = 12.sp)
            Text(value.first, fontWeight = FontWeight.Bold, color = TnaColor.ink)
        }
    }
}

@Composable
fun AboutSheet(app: AppModel) {
    Text("О НАС", style = displayStyle(30))
    Text(
        "Travel Never Alone — авторские туры от Ани и команды. Мы возим небольшими группами в места силы: Камчатка, Байкал, Кольский, Сокотра и не только. Наш принцип — «путешествуй не один»: с нами уезжают в одиночку, а возвращаются со своими людьми.",
        color = TnaColor.subtle,
    )
    if (app.role.canBookAndEarn) {
        Gap(16)
        Text("ЧЕМ ЕЩЁ ПОМОЖЕМ", style = displayStyle(20))
        SettingsRow("Индивидуальный тур", "Маршрут под тебя: свои даты, темп и компания.") { app.sheet = Sheet.Individual }
        SettingsRow("Корпоративный тур", "Выезд для команды: тимбилдинг с характером.") { app.sheet = Sheet.Individual }
        SettingsRow("Подарочный сертификат", "Подари путешествие: номинал выбираешь сам.") { app.sheet = Sheet.Certificate }
    }
}

@Composable
fun IndividualSheet(app: AppModel) {
    var name by remember { mutableStateOf(app.account.name) }
    var phone by remember { mutableStateOf(if (app.account.phone.isBlank()) "+7 " else app.account.phone) }
    var wish by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    Text("ИНДИВИДУАЛЬНЫЙ", style = displayStyle(28))
    Text("Расскажи, куда и когда — менеджер соберёт маршрут.", color = TnaColor.subtle)
    Gap(12)
    Field("Имя", name) { name = it }
    Gap(8)
    Field("Телефон", phone) { phone = it }
    Gap(8)
    Field("Куда хочется", wish) { wish = it }
    Gap(16)
    if (sent) Text("Заявка ушла. Ответим в рабочее время.", color = TnaColor.ink)
    else PrimaryButton("Отправить", name.trim().length >= 2 && phone.count { it.isDigit() } >= 10) {
        val tour = app.tours.firstOrNull() ?: Mock.tours.first()
        val request = BookingRequest(tourName = "Индивидуальный тур", departureLabel = wish, name = name.trim(), phone = PhoneNumber.e164(phone), headline = "Индивидуальный тур", showsPeople = false, details = listOf("Запрос: $wish"))
        sent = true
        app.launch { app.repo.submitBooking(request) }
    }
}

@Composable
fun CertificateSheet() {
    var amount by remember { mutableStateOf("15000") }
    var name by remember { mutableStateOf("") }
    var wish by remember { mutableStateOf("") }
    Text("СЕРТИФИКАТ", style = displayStyle(28))
    Text("Номинал, имя получателя и пожелание. Оплату подтвердит менеджер.", color = TnaColor.subtle)
    Gap(12)
    Field("Сумма, ₽", amount) { amount = it.filter { ch -> ch.isDigit() } }
    Gap(8)
    Field("Кому", name) { name = it }
    Gap(8)
    Field("Пожелание", wish) { wish = it }
    Gap(16)
    Text("Покупка уходит менеджеру так же, как в iOS, когда эквайринг настроен на бэкенде. Сумма: ${Money.grouped(amount.toIntOrNull() ?: 0)} ₽.", color = TnaColor.subtle)
    if (wish.isNotBlank()) Text("«$wish»", color = TnaColor.ink, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun NotificationsSheet(app: AppModel) {
    Text("УВЕДОМЛЕНИЯ", style = displayStyle(28))
    val prefs = app.prefsState
    ToggleRow("Все", prefs.allEnabled) { app.prefsState = prefs.copy(allEnabled = it) }
    ToggleRow("Новости", prefs.news) { app.prefsState = prefs.copy(news = it) }
    ToggleRow("Туры и заезды", prefs.tours) { app.prefsState = prefs.copy(tours = it) }
    ToggleRow("Чаты", prefs.chat) { app.prefsState = prefs.copy(chat = it) }
    ToggleRow("Достижения", prefs.achievements) { app.prefsState = prefs.copy(achievements = it) }
    ToggleRow("Акции", prefs.promos) { app.prefsState = prefs.copy(promos = it) }
    ToggleRow("Помогать улучшать приложение", prefs.analytics) { app.prefsState = prefs.copy(analytics = it) }
    Text("Пуши на Android появятся, когда бэкенд примет FCM-токен. Сейчас канал доставки — APNs, как в iOS.", color = TnaColor.subtle, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun CompanionsSheet(app: AppModel) {
    var last by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    Text("ПОПУТЧИКИ", style = displayStyle(28))
    Text("Сохраняем людей, с которыми едешь, чтобы не диктовать документы каждый раз.", color = TnaColor.subtle)
    app.companions.forEach { person ->
        Gap(8)
        Text(person.fullName, fontWeight = FontWeight.Bold, color = TnaColor.ink)
    }
    Gap(12)
    Field("Фамилия", last) { last = it }
    Gap(8)
    Field("Имя", first) { first = it }
    Gap(12)
    PrimaryButton("Добавить", last.isNotBlank() && first.isNotBlank()) {
        app.companions = app.companions + TravelCompanion(lastName = last.trim(), firstName = first.trim())
        last = ""; first = ""
    }
}

@Composable
fun CountriesSheet(app: AppModel) {
    var query by remember { mutableStateOf("") }
    Text("МОИ СТРАНЫ", style = displayStyle(28))
    Text("Живу, был или хочу. Те же статусы, что на карте в iOS.", color = TnaColor.subtle)
    Gap(8)
    Field("Страна", query) { query = it }
    val rows = CountryNamesRU.map.entries
        .filter { query.isBlank() || it.value.contains(query, ignoreCase = true) }
        .sortedBy { it.value }
        .take(40)
    rows.forEach { (code, name) ->
        val status = app.countries[code]
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = TnaColor.ink, modifier = Modifier.weight(1f))
            CountryStatus.entries.forEach { option ->
                Text(
                    option.title,
                    color = if (status == option) Color.White else TnaColor.ink,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (status == option) Color.hex(option.color) else TnaColor.elevated)
                        .clickable {
                            val next = if (status == option) null else option
                            app.countries = if (next == null) app.countries - code else app.countries + (code to next)
                            app.launch { app.repo.setCountry(code, next) }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun CompassSheet(app: AppModel) {
    var step by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(QuizAnswers()) }
    Text("КОМПАС", style = displayStyle(28))
    if (step < 4) {
        val title = listOf("Какой вайб?", "Когда?", "Насколько бодро?", "Бюджет")[step]
        Text(title, color = TnaColor.subtle)
        Gap(8)
        when (step) {
            0 -> QuizVibe.entries.forEach { option ->
                Choice(option.emoji + "  " + option.title) { answers = answers.copy(vibe = option); step++ }
            }
            1 -> QuizSeason.entries.forEach { option ->
                Choice(option.title) { answers = answers.copy(season = option); step++ }
            }
            2 -> QuizEffort.entries.forEach { option ->
                Choice(option.title) { answers = answers.copy(effort = option); step++ }
            }
            else -> QuizBudget.entries.forEach { option ->
                Choice(option.title) { answers = answers.copy(budget = option); step++ }
            }
        }
    } else {
        val ranked = TourMatchEngine.rank(if (app.tours.isEmpty()) Mock.tours else app.tours, answers)
        ranked.take(3).forEach { match ->
            Gap(8)
            Card(Modifier.clickable { app.openTour = match.tour; app.sheet = null }) {
                Text("${match.percent}%", style = displayStyle(28, TnaColor.accent))
                Text(match.tour.name, fontWeight = FontWeight.Bold, color = TnaColor.ink)
                Text(match.tour.tagline, color = TnaColor.subtle)
            }
        }
    }
}

@Composable
private fun Choice(title: String, onClick: () -> Unit) {
    Text(
        title,
        color = TnaColor.ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(onClick = onClick).padding(14.dp),
    )
}

@Composable
fun ScheduleSheet(app: AppModel) {
    Text("РАСПИСАНИЕ", style = displayStyle(28))
    val groups = app.schedule.groupBy { it.month }
    groups.forEach { (month, rows) ->
        Gap(12)
        Text(monthTitle(month), fontWeight = FontWeight.Bold, color = TnaColor.accent)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().clickable {
                app.openTourById(row.tourId)
            }.padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.tourName, fontWeight = FontWeight.Bold, color = TnaColor.ink)
                    Text("${row.label} · ${row.season}", color = TnaColor.subtle, fontSize = 13.sp)
                }
                Text(if (row.isSoldOut) "нет мест" else "${row.spotsLeft}", color = TnaColor.ink)
            }
        }
    }
}

@Composable
fun TripSheet(app: AppModel, trip: Trip) {
    Text(if (trip.countdown == "в пути") "В ТУРЕ" else "ПОЕЗДКА", style = displayStyle(28))
    Text(trip.tourName, style = displayStyle(24, TnaColor.accent))
    Text(trip.label, color = TnaColor.subtle)
    Text(trip.countdown, fontWeight = FontWeight.Bold, color = TnaColor.ink)
    Gap(12)
    val tour = app.tours.firstOrNull { it.backendId == trip.tourId || it.id == trip.tourId }
    if (tour != null) {
        PrimaryButton("Программа") { app.sheet = Sheet.Program(tour) }
        Gap(8)
    }
    trip.chatId?.let { id ->
        PrimaryButton("Чат группы") {
            app.sheet = null
            app.tab = AppTab.Chats
            app.openThread(app.threads.firstOrNull { it.id == id } ?: ChatThread(
                id = id, title = trip.tourName, subtitle = "групповой чат", lastMessage = "", time = "",
                unread = 0, icon = trip.symbol, colors = trip.colors, isPinned = false, kind = ChatThreadKind.Trip,
            ))
        }
    }
}

@Composable
fun ProgramSheet(tour: Tour) {
    Text("ПРОГРАММА", style = displayStyle(28))
    Text(tour.name, color = TnaColor.accent, fontWeight = FontWeight.Bold)
    tour.program.forEach { day ->
        Gap(10)
        Text("День ${day.day}. ${day.title}", fontWeight = FontWeight.Bold, color = TnaColor.ink)
        Text(day.details, color = TnaColor.subtle)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Text(label, color = TnaColor.subtle, fontSize = 12.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = androidx.compose.ui.text.TextStyle(color = TnaColor.ink, fontSize = 16.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(12.dp),
    )
}

@Composable
private fun ToggleRow(title: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { change(!on) }.padding(vertical = 10.dp)) {
        Text(title, color = TnaColor.ink, modifier = Modifier.weight(1f))
        Text(if (on) "вкл" else "выкл", color = if (on) TnaColor.accent else TnaColor.subtle, fontWeight = FontWeight.Bold)
    }
}

