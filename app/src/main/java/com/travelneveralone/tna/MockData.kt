package com.travelneveralone.tna

import java.time.LocalDate
import java.util.UUID

/** Fixture catalog. Mirrors TNAiOS `MockData.swift` and `seed.sql`. */
object Mock {
    const val userName = "Слава"
    const val userLevel = "Легенда TNA"

    val anyaId: UUID = UUID.fromString("00000000-0000-0000-0000-00000000a11a")
    val slavaId: UUID = UUID.fromString("00000000-0000-0000-0000-00000000a11b")
    val marinaId: UUID = UUID.fromString("00000000-0000-0000-0000-00000000a11c")

    private fun tour(
        name: String, tagline: String, hashtag: String, region: TourRegion,
        days: Int, km: Int, price: Int, difficulty: Int, altitude: Int, symbol: String,
        colors: List<Long>, departures: List<Pair<String, Int>>, program: List<DayPlan>,
        includes: List<String>,
    ) = Tour(
        name = name, tagline = tagline, hashtag = hashtag, region = region,
        days = days, km = km, priceFrom = price, difficulty = difficulty, maxAltitude = altitude,
        symbol = symbol, colors = colors,
        departures = departures.map { Departure(label = it.first, spotsLeft = it.second) },
        program = program, includes = includes,
    )

    val tours: List<Tour> = listOf(
        tour(
            "Камчатка", "Вулканы, океан и медведи", "#TNA_камчатка", TourRegion.Russia,
            11, 1400, 189_900, 4, 1830, "mountain.2.fill", listOf(0xFF6B3D, 0x8B1E00),
            listOf("12–22 авг" to 12, "26 авг – 5 сен" to 12),
            listOf(
                DayPlan(day = 1, title = "Прилёт и Тихий океан", details = "Встреча в аэропорту, Халактырский пляж с чёрным вулканическим песком, welcome-ужин с крабом."),
                DayPlan(day = 2, title = "Вулкан Горелый", details = "Треккинг к кратерному озеру бирюзового цвета. 9 км, набор 900 м."),
                DayPlan(day = 3, title = "Мутновский вулкан", details = "Фумарольные поля, ледник и кислотное озеро в кратере."),
                DayPlan(day = 4, title = "Океанская рыбалка", details = "Выход в Авачинскую бухту, ловим палтуса, смотрим сивучей."),
                DayPlan(day = 5, title = "Вертолёт в Долину гейзеров", details = "Опционально: полёт над вулканами, кальдера Узон."),
            ),
            listOf("Проживание", "Трансферы 4×4", "Гид-вулканолог", "Питание на маршрутах", "Страховка"),
        ),
        tour(
            "Байкал", "Самый большой каток мира", "#TNA_байкал", TourRegion.Russia,
            7, 900, 98_500, 2, 456, "snowflake", listOf(0x53C7F0, 0x0D47A1),
            listOf("12–18 фев" to 12, "26 фев – 4 мар" to 12),
            listOf(
                DayPlan(day = 1, title = "Иркутск → Листвянка", details = "Музей Байкала, первый выход на лёд."),
                DayPlan(day = 2, title = "Ольхон", details = "Хивус по льду, мыс Бурхан и скала Шаманка."),
                DayPlan(day = 3, title = "Лёд и пузыри", details = "Ищем метановые пузыри, гроты и торосы. Коньки по прозрачному льду."),
                DayPlan(day = 4, title = "Мыс Хобой", details = "Северная оконечность Ольхона, ледяные наплески."),
            ),
            listOf("Проживание", "Хивус", "Коньки", "Баня на льду", "Фотограф"),
        ),
        tour(
            "Алтай", "Марс, бирюзовая Катунь и перевалы", "#TNA_алтай", TourRegion.Russia,
            9, 1600, 112_000, 3, 2840, "leaf.fill", listOf(0x7CB342, 0x1B5E20),
            listOf("4–12 июл" to 12, "18–26 июл" to 12),
            listOf(
                DayPlan(day = 1, title = "Горно-Алтайск → Чемал", details = "Чемальская ГЭС, остров Патмос."),
                DayPlan(day = 2, title = "Чуйский тракт", details = "Один из красивейших автомаршрутов мира, перевал Чике-Таман."),
                DayPlan(day = 3, title = "Марс-1 и Марс-2", details = "Разноцветные горы Кызыл-Чина."),
                DayPlan(day = 4, title = "Гейзерное озеро", details = "Бирюзовое озеро с узорами на дне."),
            ),
            listOf("Проживание", "Джипы", "Гид", "Завтраки и ужины", "Сплав по Катуни"),
        ),
        tour(
            "Сокотра", "Инопланетный остров в Индийском океане", "#TNA_сокотра", TourRegion.World,
            8, 700, 255_000, 3, 1500, "tree.fill", listOf(0x26C6DA, 0x00695C),
            listOf("1–8 мая" to 12),
            listOf(
                DayPlan(day = 1, title = "Прилёт на остров", details = "Кемп на берегу океана, первые драконовые деревья."),
                DayPlan(day = 2, title = "Плато Диксам", details = "Лес драконовых деревьев, каньон Дирхур."),
                DayPlan(day = 3, title = "Дюны Архер", details = "Белоснежные дюны у бирюзовой лагуны."),
                DayPlan(day = 4, title = "Пещера Хок", details = "3 км сталактитов и подземных озёр."),
            ),
            listOf("Перелёт из Абу-Даби", "Кемпинг full-service", "Джипы", "Повар", "Виза"),
        ),
        tour(
            "Кольский", "Аврора, Териберка и край земли", "#TNA_кольский", TourRegion.Russia,
            5, 600, 58_000, 2, 1200, "sparkles", listOf(0x9575CD, 0x283593),
            listOf("5–9 дек" to 12, "19–23 дек" to 12),
            listOf(
                DayPlan(day = 1, title = "Мурманск → Териберка", details = "Кладбище кораблей, Северный Ледовитый океан."),
                DayPlan(day = 2, title = "Охота за сиянием", details = "Ночной выезд за авророй с фотографом."),
                DayPlan(day = 3, title = "Хибины", details = "Снегоходы и горнолыжка в Кировске."),
            ),
            listOf("Проживание", "Охота за сиянием", "Снегоходы", "Фотограф", "Термокостюмы"),
        ),
        tour(
            "Япония", "Сакура, Фудзи и неон Токио", "#TNA_япония", TourRegion.World,
            10, 1100, 320_000, 1, 2300, "camera.macro", listOf(0xF48FB1, 0xAD1457),
            listOf("28 мар – 7 апр" to 12),
            listOf(
                DayPlan(day = 1, title = "Токио", details = "Сибуя, Синдзюку, изакаи."),
                DayPlan(day = 2, title = "Фудзи", details = "Озеро Кавагутико, пагода Чурейто."),
                DayPlan(day = 3, title = "Киото", details = "Фусими Инари на рассвете, Арасияма."),
            ),
            listOf("Отели", "JR Pass", "Гид", "Чайная церемония", "Онсэн"),
        ),
        tour(
            "ЮАР", "Сафари, два океана и Кейптаун", "#TNA_юар", TourRegion.World,
            12, 2400, 298_000, 2, 1085, "pawprint.fill", listOf(0xFFA726, 0xBF360C),
            listOf("10–22 окт" to 12),
            listOf(
                DayPlan(day = 1, title = "Кейптаун", details = "Столовая гора, набережная V&A."),
                DayPlan(day = 2, title = "Мыс Доброй Надежды", details = "Пингвины Боулдерс, маяк Кейп-Пойнт."),
                DayPlan(day = 3, title = "Сафари", details = "Большая пятёрка в частном заповеднике."),
            ),
            listOf("Отели и лоджи", "Сафари", "Винные дегустации", "Гид", "Страховка"),
        ),
    )

    val passport = PassportData(
        userName = userName,
        memberSince = "с TNA с 2024",
        level = userLevel,
        levelProgress = 1.0,
        nextLevelHint = "Высший уровень достигнут. Спасибо, что с TNA!",
        stats = listOf(
            StatItem("Тур", "Тура", "Туров", 7, icon = "flag"),
            StatItem("Страна", "Страны", "Стран", 3, icon = "globe"),
            StatItem("Километр", "Километра", "Километров", 23_500, icon = "path"),
            StatItem("День в пути", "Дня в пути", "Дней в пути", 62, icon = "sun"),
            StatItem.fixed("Высшая точка", 4_200, "м", "mountain"),
        ),
        stamps = listOf(
            Stamp(title = "БАЙКАЛ", year = "2024", icon = "snowflake", color = 0x53C7F0, angle = -8.0),
            Stamp(title = "КАМЧАТКА", year = "2024", icon = "mountain", color = 0xFF6B3D, angle = 5.0),
            Stamp(title = "АЛТАЙ", year = "2025", icon = "leaf", color = 0x7CB342, angle = 9.0),
            Stamp(title = "СОКОТРА", year = "2025", icon = "tree", color = 0x26C6DA, angle = -6.0),
            Stamp(title = "КОЛЬСКИЙ", year = "2026", icon = "sparkles", color = 0x9575CD, angle = 3.0),
        ),
        achievements = listOf(
            Achievement(name = "Первый шаг", details = "Первый тур с TNA", icon = "walk", earned = true, color = 0x7CB342, howTo = "Съезди в свой первый тур с TNA - и коллекция началась.", earnedDate = "мар 2024"),
            Achievement(name = "Байкальский лёд", details = "Зимний Байкал", icon = "snow", earned = true, color = 0x53C7F0, howTo = "Пройди по прозрачному льду Байкала в зимнем туре.", earnedDate = "фев 2026"),
            Achievement(name = "Вулканолог", details = "Подняться на вулкан", icon = "flame", earned = true, color = 0xFF6B3D, howTo = "Поднимись на действующий вулкан - например, на Камчатке.", earnedDate = "авг 2025"),
            Achievement(name = "Все сезоны", details = "Тур в каждый сезон", icon = "calendar", earned = false, color = 0x26A69A, howTo = "Съезди в тур зимой, весной, летом и осенью."),
            Achievement(name = "Амбассадор", details = "Привести друга", icon = "people", earned = false, color = 0xFFA726, howTo = "Приведи друга в тур по своему промокоду."),
            Achievement(name = "Легенда TNA", details = "10 туров с TNA", icon = "crown", earned = false, color = 0xFFCA28, howTo = "Соверши 10 туров с TNA и стань легендой."),
        ),
        places = listOf(
            VisitedPlace(name = "Байкал", latitude = 53.2, longitude = 107.3, color = 0x53C7F0),
            VisitedPlace(name = "Камчатка", latitude = 53.0, longitude = 158.6, color = 0xFF6B3D),
            VisitedPlace(name = "Алтай", latitude = 50.7, longitude = 86.2, color = 0x7CB342),
        ),
        leaderboard = listOf(
            LeaderboardEntry(rank = 1, name = "Марина К.", km = 41_200, tours = 12, isMe = false, userId = marinaId),
            LeaderboardEntry(rank = 2, name = "Дмитрий В.", km = 33_800, tours = 9, isMe = false),
            LeaderboardEntry(rank = 3, name = "Слава", km = 23_500, tours = 7, isMe = true, userId = slavaId),
            LeaderboardEntry(rank = 4, name = "Аня П.", km = 21_100, tours = 8, isMe = false, userId = anyaId),
            LeaderboardEntry(rank = 5, name = "Игорь С.", km = 18_400, tours = 5, isMe = false),
        ),
    )

    val threads = listOf(
        ChatThread(
            id = UUID.fromString("00000000-0000-0000-0000-00000000c0de"),
            title = "Поддержка TNA",
            subtitle = "Аня · отвечаем в рабочее время",
            lastMessage = "Привет! По Камчатке остались 2 места на август 🌋",
            time = "14:02", unread = 2, icon = "headset",
            colors = listOf(0xFF8F5E, 0xE63E00), isPinned = true,
            kind = ChatThreadKind.Support, isSupport = true,
        ),
        ChatThread(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
            title = "Камчатка · 12–22 авг",
            subtitle = "9 участников",
            lastMessage = "Миша: кто-нибудь берёт дрон?",
            time = "13:47", unread = 5, icon = "mountain.2.fill",
            colors = listOf(0xFF6B3D, 0x8B1E00), isPinned = true,
        ),
        ChatThread(
            id = UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
            title = "Сообщество TNA",
            subtitle = "1 284 путешественника",
            lastMessage = "Лена: выложила фото с Сокотры, гляньте!",
            time = "12:30", unread = 0, icon = "globe",
            colors = listOf(0x53C7F0, 0x0D47A1), isPinned = false,
            kind = ChatThreadKind.Community,
        ),
    )

    val managerChat = listOf(
        Message(text = "Привет, Слава! 👋 Видела, ты смотрел Камчатку на август", isMine = false, author = "Аня", time = "13:58"),
        Message(text = "Привет! Да, думаю над заездом 12–22", isMine = true, author = "Слава", time = "14:00"),
        Message(text = "Отличный выбор! Там сейчас 2 места. Гид - Паша, который водил тебя на Алтае 😉", isMine = false, author = "Аня", time = "14:01"),
        Message(text = "Привет! По Камчатке остались 2 места на август 🌋 Бронировать?", isMine = false, author = "Аня", time = "14:02"),
    )

    val bookings = listOf(
        BookingSummary(UUID.fromString("00000000-0000-0000-0000-0000000b0001"), "Камчатка", "12–22 авг", BookingStatus.Confirmed, "3 июл 2026"),
        BookingSummary(UUID.fromString("00000000-0000-0000-0000-0000000b0002"), "Индивидуальный тур: Марокко", "индивидуальный запрос", BookingStatus.Requested, "28 июн 2026"),
        BookingSummary(UUID.fromString("00000000-0000-0000-0000-0000000b0003"), "Байкал", "фев 2026", BookingStatus.Completed, "10 фев 2026"),
    )

    val reviews = listOf(
        TourReview(UUID.fromString("00000000-0000-0000-0000-0000000c0001"), "Марина", 5, "Лучшее путешествие в жизни. Гид - космос, всё продумано до мелочей.", "авг 2025"),
        TourReview(UUID.fromString("00000000-0000-0000-0000-0000000c0002"), "Дмитрий", 5, "Вулканы, океан, медведи - всё как обещали. Обязательно вернусь.", "сен 2025"),
        TourReview(UUID.fromString("00000000-0000-0000-0000-0000000c0003"), "Ольга", 4, "Насыщенно и красиво. Немного устали от переездов, но оно того стоит.", "июл 2025"),
    )

    val news: List<NewsItem> = listOf(
        NewsItem(UUID.fromString("00000000-0000-0000-0000-0000000e0001"), "Открыт набор: Марокко, апрель 2027", "Пустыня, Атлас и синий Шефшауэн. Маленькая группа, ранняя цена до конца месяца.", null, "3 дн", null),
        NewsItem(UUID.fromString("00000000-0000-0000-0000-0000000d0002"), "Камчатка: набор на август", "Заезд 12–22 августа. Если думал - самое время.", null, "1 нед", NewsLink.TourLink(tours.first().id)),
        NewsItem(UUID.fromString("00000000-0000-0000-0000-0000000d0003"), "Мы в Telegram", "Живые отчёты из туров и анонсы новых направлений.", null, "2 нед", NewsLink.UrlLink("https://t.me/travelneveralone")),
    )

    val schedule: List<DepartureItem> by lazy {
        fun item(name: String, label: String, start: String, end: String): DepartureItem {
            val tour = tours.first { it.name == name }
            val starts = LocalDate.parse(start)
            return DepartureItem(
                id = UUID.randomUUID(), tourId = tour.backendId ?: tour.id, tourName = tour.name,
                season = seasonName(starts), region = tour.region, colors = tour.colors, label = label,
                startsOn = starts, endsOn = LocalDate.parse(end), spotsLeft = 12, status = "open",
            )
        }
        listOf(
            item("Камчатка", "12–22 авг", "2026-08-12", "2026-08-22"),
            item("Камчатка", "26 авг – 5 сен", "2026-08-26", "2026-09-05"),
            item("Байкал", "12–18 фев", "2027-02-12", "2027-02-18"),
            item("Алтай", "4–12 июл", "2026-07-04", "2026-07-12"),
            item("Сокотра", "1–8 мая", "2027-05-01", "2027-05-08"),
            item("Кольский", "5–9 дек", "2026-12-05", "2026-12-09"),
            item("Япония", "28 мар – 7 апр", "2027-03-28", "2027-04-07"),
            item("ЮАР", "10–22 окт", "2026-10-10", "2026-10-22"),
        )
    }

    val testLogins = listOf(
        TestLogin("Клиент", "+7 999 000 00 06", "000000"),
        TestLogin("Слава", "+7 999 000 00 00", "000000"),
        TestLogin("Аня", "+7 999 000 00 01", "000000"),
        TestLogin("Гид", "+7 999 000 00 02", "000000"),
        TestLogin("Куратор", "+7 999 000 00 03", "000000"),
        TestLogin("Партнёр", "+7 999 000 00 04", "000000"),
        TestLogin("Менеджер", "+7 999 000 00 05", "000000"),
    )
}

data class TestLogin(val label: String, val phone: String, val code: String)
