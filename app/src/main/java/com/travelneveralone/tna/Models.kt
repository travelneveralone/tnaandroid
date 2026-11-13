package com.travelneveralone.tna

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Brand hex without alpha, matching the iOS `TNA` palette. */
object Palette {
    const val bg = 0xF7F2E9L
    const val elevated = 0xEDE6D9L
    const val ink = 0x2E3A5CL
    const val accent = 0xED7D4DL
    const val accentSoft = 0xF09B7CL
    const val subtle = 0x79809BL
    val hands = listOf(0xF09B7CL, 0xF5C752L, 0x7CC5AFL, 0x2C4A9FL, 0xE85C68L)
}

data class DayPlan(
    val id: UUID = UUID.randomUUID(),
    val day: Int,
    val title: String,
    val details: String,
)

data class Departure(
    val id: UUID = UUID.randomUUID(),
    val label: String,
    val spotsLeft: Int,
    val startsOn: LocalDate? = null,
    val backendId: UUID? = null,
) {
    val isSoldOut: Boolean get() = spotsLeft <= 0
    val isUpcoming: Boolean get() = startsOn?.let { !it.isBefore(Dates.todayUtc()) } ?: true
}

enum class TourRegion(val title: String) {
    Russia("Россия"),
    World("Мир");

    companion object {
        fun fromRaw(raw: String): TourRegion = entries.firstOrNull { it.title == raw } ?: Russia
    }
}

data class Tour(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val tagline: String,
    val hashtag: String,
    val region: TourRegion,
    val days: Int,
    val km: Int,
    val priceFrom: Int,
    val difficulty: Int,
    val maxAltitude: Int,
    val symbol: String,
    val colors: List<Long>,
    val departures: List<Departure>,
    val program: List<DayPlan>,
    val includes: List<String>,
    val backendId: UUID? = null,
    val imageUrl: String? = null,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    var salePriceFrom: Int? = null,
    var saleLabel: String? = null,
) {
    val heroImages: List<String> get() = images.ifEmpty { listOfNotNull(imageUrl) }
    val displayPrice: Int get() = salePriceFrom ?: priceFrom
    val priceText: String get() = "от ${Money.grouped(displayPrice)} ₽"
    val basePriceText: String get() = "от ${Money.grouped(priceFrom)} ₽"
    val hasSale: Boolean get() = salePriceFrom?.let { it < priceFrom } == true

    val seasons: Set<String>
        get() = departures.mapNotNull { dep ->
            dep.startsOn?.let { seasonName(it) } ?: seasonFromLabel(dep.label)
        }.toSet()

    val seasonText: String
        get() {
            val order = listOf("Зима", "Весна", "Лето", "Осень")
            val present = order.filter { seasons.contains(it) }
            return when {
                present.isEmpty() -> ""
                present.size >= 4 -> "Круглый год"
                else -> present.joinToString(" · ")
            }
        }
}

data class Trip(
    val id: UUID,
    val tourId: UUID,
    val tourName: String,
    val colors: List<Long>,
    val symbol: String,
    val label: String,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val status: String,
    val role: String,
    val chatId: UUID?,
    val imageUrl: String? = null,
) {
    val isUpcoming: Boolean get() = endsOn?.let { !it.isBefore(Dates.todayUtc()) } ?: true
    val isRunning: Boolean
        get() {
            val today = Dates.todayUtc()
            val start = startsOn ?: return false
            return !start.isAfter(today) && (endsOn?.let { !it.isBefore(today) } ?: true)
        }
    val isGuide: Boolean get() = role == "guide" || role == "coordinator"
    val countdown: String
        get() {
            val start = startsOn ?: return ""
            val days = java.time.temporal.ChronoUnit.DAYS.between(Dates.todayUtc(), start).toInt()
            return when {
                days > 0 -> "через $days ${pluralRU(days, "день", "дня", "дней")}"
                days == 0 -> "старт сегодня"
                else -> "в пути"
            }
        }
}

data class TourReview(
    val id: UUID,
    val author: String,
    val rating: Int,
    val text: String,
    val date: String,
    val images: List<String> = emptyList(),
    val reply: String? = null,
    val tourName: String? = null,
    val authorAvatarUrl: String? = null,
)

data class ReviewableTrip(
    val id: UUID,
    val tourId: UUID,
    val tourName: String,
    val label: String,
    val endsOn: String,
    val reviewed: Boolean,
)

data class DepartureItem(
    val id: UUID,
    val tourId: UUID,
    val tourName: String,
    val season: String,
    val region: TourRegion,
    val colors: List<Long>,
    val label: String,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val spotsLeft: Int,
    val status: String,
) {
    val isSoldOut: Boolean get() = spotsLeft <= 0 || status == "full"
    val month: LocalDate get() = startsOn.withDayOfMonth(1)
}

data class NotificationPrefs(
    var allEnabled: Boolean = true,
    var news: Boolean = true,
    var tours: Boolean = true,
    var chat: Boolean = true,
    var achievements: Boolean = true,
    var promos: Boolean = true,
    var analytics: Boolean = true,
)

sealed class NewsLink {
    data class TourLink(val id: UUID) : NewsLink()
    data class UrlLink(val url: String) : NewsLink()
}

/** news.link_type/link_id → a tap target; a broken tour id drops the link (iOS NewsDTO). */
fun newsLink(type: String?, id: String?): NewsLink? = when (type) {
    "tour" -> id?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { NewsLink.TourLink(it) }
    "url" -> id?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { NewsLink.UrlLink(it) }
    else -> null
}

/** The item's show window (0141, show_until is exclusive) has ended at [now]. */
fun newsWindowOver(showUntil: String?, now: java.time.Instant): Boolean {
    val end = parseInstant(showUntil) ?: return false
    return !end.isAfter(now)
}

data class NewsItem(
    val id: UUID,
    val title: String,
    val body: String,
    val imageUrl: String?,
    val date: String,
    val link: NewsLink?,
    val isPinned: Boolean = false,
)

data class TravelCompanion(
    val id: UUID = UUID.randomUUID(),
    val lastName: String,
    val firstName: String,
    val patronymic: String = "",
    val birthday: String? = null,
    val document: String = "",
) {
    val fullName: String
        get() = listOf(lastName, firstName, patronymic).filter { it.isNotBlank() }.joinToString(" ")
}

data class BookingRequest(
    val tourName: String,
    val departureLabel: String,
    val name: String,
    val phone: String,
    val peopleCount: Int = 1,
    val headline: String = "Заявка на тур",
    val showsPeople: Boolean = true,
    val companions: List<TravelCompanion> = emptyList(),
    val details: List<String> = emptyList(),
    val departureId: UUID? = null,
) {
    val subject: String
        get() = listOf(tourName, departureLabel).filter { it.isNotEmpty() }.joinToString(" · ")
}

enum class BookingStatus(val label: String, val color: Long) {
    Requested("Заявка отправлена", 0xF6A609),
    Confirmed("Подтверждена", 0x3D9BFF),
    Prepaid("Внесена предоплата", 0x8B5CF6),
    Paid("Оплачена", 0x35B36B),
    Completed("Завершена", 0x7A8794),
    Cancelled("Отменена", 0xE0534E);

    companion object {
        val pipeline = listOf(Requested, Confirmed, Prepaid, Paid, Completed)
        fun fromRaw(raw: String): BookingStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Requested
    }
}

data class BookingSummary(
    val id: UUID,
    val title: String,
    val subtitle: String,
    val status: BookingStatus,
    val date: String,
    val peopleCount: Int = 0,
    val note: String? = null,
    val codeDiscount: Int = 0,
    val bonusSpent: Int = 0,
) {
    val moneyNote: String? get() = BonusCopy.bookingNote(codeDiscount, bonusSpent)
}

data class ReturningCustomer(val name: String, val tripsCount: Int)

data class Achievement(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val details: String,
    val icon: String,
    val earned: Boolean,
    val color: Long,
    val howTo: String = "",
    val earnedDate: String? = null,
)

data class Stamp(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val year: String,
    val icon: String,
    val color: Long,
    val angle: Double,
)

data class VisitedPlace(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val color: Long,
)

data class LeaderboardEntry(
    val id: UUID = UUID.randomUUID(),
    val rank: Int,
    val name: String,
    val km: Int,
    val tours: Int,
    val isMe: Boolean,
    val avatarUrl: String? = null,
    val userId: UUID? = null,
)

data class StatItem(
    val one: String,
    val few: String,
    val many: String,
    val value: Int,
    val unit: String = "",
    val icon: String,
) {
    val title: String get() = pluralRU(value, one, few, many)

    companion object {
        fun fixed(title: String, value: Int, unit: String = "", icon: String) =
            StatItem(title, title, title, value, unit, icon)
    }
}

data class PassportData(
    val userName: String,
    val memberSince: String,
    val level: String,
    val levelProgress: Double,
    val nextLevelHint: String,
    val stats: List<StatItem>,
    val stamps: List<Stamp>,
    val achievements: List<Achievement>,
    val places: List<VisitedPlace>,
    val leaderboard: List<LeaderboardEntry>,
) {
    val isLoaded: Boolean get() = userName.isNotBlank() || stats.isNotEmpty()

    companion object {
        /// Nothing loaded yet (or a guest). Never fall back to `Mock.passport` in the live app.
        val empty = PassportData(
            userName = "", memberSince = "", level = "", levelProgress = 0.0, nextLevelHint = "",
            stats = emptyList(), stamps = emptyList(), achievements = emptyList(),
            places = emptyList(), leaderboard = emptyList(),
        )
    }
}

/// Completed-tour ladder. Copy matches the iOS passport; the server only returns counts.
enum class TravelerLevel(val title: String, val threshold: Int, val nextHint: String) {
    Novice("Новичок", 0, "Съезди в первый тур, чтобы стать «Путешественником»"),
    Traveler("Путешественник", 1, "Ещё пара туров — и ты «Первопроходец»"),
    Pioneer("Первопроходец", 3, "7 туров с TNA открывают уровень «Легенда TNA» — скидка и ранний доступ к новинкам"),
    Legend("Легенда TNA", 7, "Высший уровень достигнут. Спасибо, что с TNA!");

    fun progress(tours: Int): Double {
        val next = entries.getOrNull(ordinal + 1) ?: return 1.0
        val span = (next.threshold - threshold).toDouble()
        if (span <= 0) return 1.0
        return ((tours - threshold) / span).coerceIn(0.0, 1.0)
    }

    companion object {
        fun forTours(tours: Int): TravelerLevel =
            entries.lastOrNull { tours >= it.threshold } ?: Novice
    }
}

object PassportCopy {
    private val stampAngles = listOf(-8.0, 5.0, -4.0, 9.0, -6.0, 3.0, -10.0)

    fun memberSince(year: Int?): String =
        if (year != null && year > 2000) "с TNA с $year" else ""

    fun stampAngle(index: Int): Double = stampAngles[index % stampAngles.size]

    /// Others are first name + last initial. The caller keeps the full name.
    fun shortName(name: String, isMe: Boolean): String {
        val trimmed = name.trim()
        if (isMe) return trimmed
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        if (parts.size < 2) return trimmed
        return "${parts[0]} ${parts[1].first()}."
    }

    fun monthYear(iso: String?): String? {
        val instant = parseInstant(iso) ?: return null
        return DateTimeFormatter.ofPattern("LLL yyyy", java.util.Locale("ru"))
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    }

    fun stats(tours: Int, countries: Int, km: Int, days: Int, maxAltitude: Int): List<StatItem> = listOf(
        StatItem("Тур", "Тура", "Туров", tours, "", "flag"),
        StatItem("Страна", "Страны", "Стран", countries, "", "globe"),
        StatItem("Километр", "Километра", "Километров", km, "", "path"),
        StatItem("День в пути", "Дня в пути", "Дней в пути", days, "", "sun"),
        StatItem.fixed("Высшая точка", maxAltitude, "м", "mountain"),
    )
}

data class ProfileDraft(
    val lastName: String = "",
    val firstName: String = "",
    val patronymic: String = "",
    val city: String = "",
    val birthday: String = "",
) {
    val displayName: String
        get() = listOf(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
}

enum class MessageStatus { Sent, Queued, Failed }
enum class MessageKind { Text, Image, File, Audio, Poll }

data class PollOption(val id: String, val text: String)

data class PollData(
    val question: String,
    val description: String? = null,
    val options: List<PollOption>,
    val multiple: Boolean = false,
    val anonymous: Boolean = false,
    val addOptions: Boolean = false,
    val revote: Boolean = true,
    val quiz: Boolean = false,
    val correct: List<String> = emptyList(),
    val shuffle: Boolean = false,
    val closesAt: String? = null,
    val closed: Boolean = false,
    val rev: Int = 0,
)

data class MessageReaction(val emoji: String, val count: Int, val mine: Boolean)

object Reactions {
    val set = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏")
}

data class Message(
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val isMine: Boolean,
    val author: String,
    val time: String,
    val status: MessageStatus = MessageStatus.Sent,
    val kind: MessageKind = MessageKind.Text,
    val mediaUrl: String? = null,
    val mediaName: String? = null,
    val durationMs: Int? = null,
    val edited: Boolean = false,
    val reactions: List<MessageReaction> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val authorId: UUID? = null,
    val replyTo: UUID? = null,
    val albumId: UUID? = null,
    val pinnedAt: Instant? = null,
    val poll: PollData? = null,
    val asCompany: Boolean = false,
    val authorAvatarUrl: String? = null,
)

fun List<Message>.albumGrouped(): List<List<Message>> {
    val rows = mutableListOf<List<Message>>()
    var i = 0
    while (i < size) {
        val message = this[i]
        if (message.albumId != null && message.kind == MessageKind.Image) {
            val group = mutableListOf(message)
            var j = i + 1
            while (j < size && this[j].kind == MessageKind.Image && this[j].albumId == message.albumId) {
                group += this[j]
                j++
            }
            if (group.size >= 2) {
                rows += group
                i = j
                continue
            }
        }
        rows += listOf(message)
        i++
    }
    return rows
}

enum class ChatThreadKind { Support, Trip, Community, Direct }

data class ChatThread(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val subtitle: String,
    val lastMessage: String,
    val time: String,
    val unread: Int,
    val icon: String,
    val colors: List<Long>,
    val isPinned: Boolean,
    val kind: ChatThreadKind = ChatThreadKind.Trip,
    val lastActivity: Instant? = null,
    val otherUserId: UUID? = null,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val isSupport: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
) {
    companion object {
        val supportPlaceholder = ChatThread(
            title = "Поддержка TNA",
            subtitle = "Аня · на связи в рабочее время",
            lastMessage = "",
            time = "",
            unread = 0,
            icon = "headset",
            colors = listOf(0xFF8F5E, 0xE63E00),
            isPinned = true,
            kind = ChatThreadKind.Support,
            isSupport = true,
        )
    }
}

enum class CountryStatus(val title: String, val color: Long) {
    Lives("Живу", 0x2F9E73),
    Visited("Был", Palette.accent),
    Wants("Хочу", 0x6C56C4);

    val isBeen: Boolean get() = this == Lives || this == Visited

    companion object {
        fun fromRaw(raw: String): CountryStatus? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

data class CampaignSale(val priceFrom: Int, val label: String)

data class CampaignOffer(
    val name: String,
    val title: String,
    val text: String,
    val icon: String?,
    val until: String?,
    val sales: Map<UUID, CampaignSale>,
) {
    val showsBanner: Boolean
        get() = title.isNotBlank() || text.isNotBlank()
}

object CampaignApplication {
    fun apply(offer: CampaignOffer?, tours: MutableList<Tour>) {
        for (index in tours.indices) {
            val tour = tours[index]
            val id = tour.backendId
            val sale = id?.let { offer?.sales?.get(it) }
            if (sale != null && sale.priceFrom < tour.priceFrom) {
                tour.salePriceFrom = sale.priceFrom
                tour.saleLabel = sale.label
            } else {
                tour.salePriceFrom = null
                tour.saleLabel = null
            }
        }
    }
}

data class BonusSummary(
    val available: Int,
    val pending: Int,
    val bound: Boolean,
    val expiresOn: String? = null,
) {
    val profileLine: String?
        get() {
            val parts = mutableListOf<String>()
            if (available > 0) parts += "доступно ${Money.rubles(available)}"
            if (pending > 0) parts += "ожидает ${Money.rubles(pending)}"
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }

    val expiresLine: String?
        get() {
            val date = expiresOn?.takeIf { available > 0 && it.isNotEmpty() } ?: return null
            return "Сгорят ${BonusCopy.shortDate(date)}"
        }
}

data class BonusLine(
    val id: UUID,
    val kind: String,
    val amount: Int,
    val remaining: Int,
    val state: String,
    val expiresOn: String?,
    val title: String,
) {
    val text: String get() = BonusCopy.line(this)
}

enum class ReferralResult(val userMessage: String) {
    Ok("Код друга принят! 🎉"),
    Already("Код уже был применён раньше"),
    NotFound("Такого кода нет"),
    OwnCode("Свой собственный код вводить нельзя"),
    Unauthorized("Код принимается только после входа в аккаунт"),
    Existing("Код друга — для первой поездки с TNA, а ты уже ездил с нами. Убери код, и заявка уйдёт"),
    Error("Не получилось — можно повторить");

    companion object {
        fun fromRaw(raw: String?): ReferralResult = when (raw) {
            "ok" -> Ok
            "already" -> Already
            "notfound" -> NotFound
            "self" -> OwnCode
            "unauthorized" -> Unauthorized
            "existing" -> Existing
            else -> Error
        }
    }
}

enum class CertificateCheck {
    Applicable,
    NotFound,
    AlreadyRedeemed,
    Expired,
    Cancelled,
    PendingPayment,
    Error;

    val userMessage: String?
        get() = when (this) {
            Applicable -> null
            NotFound -> "Сертификат не найден: неверный номер или PIN"
            AlreadyRedeemed -> "Этот сертификат уже использован"
            Expired -> "Срок действия сертификата истёк"
            Cancelled -> "Сертификат отменён"
            PendingPayment -> "Сертификат ещё не оплачен"
            Error -> "Не удалось проверить — можно повторить"
        }
}

data class GiftCertificate(
    val id: UUID,
    val code: String?,
    val amount: Int,
    val recipientName: String,
    val wish: String,
    val status: String,
    val paymentUrl: String? = null,
    val pin: String = "",
    val expiresOn: String? = null,
) {
    val amountText: String get() = Money.rubles(amount)
    val statusTitle: String
        get() = when (status) {
            "paid" -> "Активен"
            "redeemed" -> "Использован"
            "expired" -> "Срок истёк"
            "cancelled" -> "Отменён"
            else -> "Ждёт оплаты"
        }
    val expiresText: String?
        get() = Dates.parseDay(expiresOn)?.let {
            "действует до " + java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("ru")).format(it)
        }
    val shareText: String
        get() {
            val lines = mutableListOf(
                "🎁 Подарочный сертификат Travel Never Alone",
                "Номер: ${code.orEmpty()}",
                "PIN для активации: $pin",
                "Номинал: $amountText",
            )
            if (recipientName.isNotBlank()) lines += "Для: $recipientName"
            if (wish.isNotBlank()) lines += "«$wish»"
            expiresText?.let { lines += it.replaceFirstChar { c -> c.uppercase() } }
            lines += "Обменять на путешествие: travelneveralone.com"
            return lines.joinToString("\n")
        }
}

data class ProfileExtras(
    val lastName: String = "",
    val firstName: String = "",
    val patronymic: String = "",
    val birthday: String? = null,
    val city: String = "",
    val email: String = "",
    val document: String = "",
)

data class TripContact(val id: UUID, val name: String, val phone: String, val sort: Int)

enum class UserRole {
    Client, Guide, Curator, Partner, Manager, Admin;

    val isStaffLimited: Boolean get() = this == Guide || this == Curator || this == Partner
    val canBookAndEarn: Boolean get() = this == Client
    val canDirectMessage: Boolean get() = this != Partner
    val isManagement: Boolean get() = this == Admin || this == Manager
    val isOpsOnly: Boolean get() = this == Manager
    val hasTravelProfile: Boolean get() = !isOpsOnly

    fun mayStartDirectMessage(target: UserRole): Boolean {
        if (this == Partner || target == Partner) return false
        if (target == Admin) return false
        if (this == Client) return target == Guide || target == Curator || target == Manager
        return true
    }

    companion object {
        fun fromRaw(raw: String?): UserRole {
            val value = raw.orEmpty()
            if (value.isEmpty()) return Client
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Client
        }
    }
}

data class DirectMessagePolicy(val roles: List<UserRole>?) {
    fun allows(from: UserRole, to: UserRole): Boolean {
        val allowed = roles
        if (allowed == null) return from.mayStartDirectMessage(to)
        return allowed.contains(to)
    }
}

object Dates {
    fun todayUtc(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    fun parseDay(raw: String?): LocalDate? {
        val value = raw?.take(10) ?: return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

object Money {
    fun grouped(value: Int): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        return if (negative) "-$grouped" else grouped
    }

    fun rubles(value: Int): String = grouped(value) + " ₽"
}

fun pluralRU(n: Int, one: String, few: String, many: String): String {
    val rem100 = kotlin.math.abs(n) % 100
    val rem10 = kotlin.math.abs(n) % 10
    if (rem100 in 11..14) return many
    if (rem10 == 1) return one
    if (rem10 in 2..4) return few
    return many
}

fun seasonName(date: LocalDate): String = when (date.monthValue) {
    12, 1, 2 -> "Зима"
    3, 4, 5 -> "Весна"
    6, 7, 8 -> "Лето"
    else -> "Осень"
}

fun seasonFromLabel(label: String): String? {
    val months = listOf(
        "янв" to "Зима", "фев" to "Зима", "дек" to "Зима",
        "мар" to "Весна", "апр" to "Весна", "мая" to "Весна", "май" to "Весна",
        "июн" to "Лето", "июл" to "Лето", "авг" to "Лето",
        "сен" to "Осень", "окт" to "Осень", "ноя" to "Осень",
    )
    val lower = label.lowercase()
    var best: Pair<Int, String>? = null
    for ((token, season) in months) {
        val idx = lower.indexOf(token)
        if (idx >= 0 && (best == null || idx < best.first)) best = idx to season
    }
    return best?.second
}

object PhoneNumber {
    fun digits(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("8")) d = "7" + d.drop(1)
        if (d.length == 10) d = "7$d"
        return d
    }

    fun e164(raw: String): String {
        val d = digits(raw)
        return if (d.isEmpty()) "" else "+$d"
    }

    fun pretty(raw: String): String {
        val d = digits(raw)
        if (d.length != 11 || !d.startsWith("7")) return e164(raw)
        val a = d.drop(1)
        return "+7 ${a.substring(0, 3)} ${a.substring(3, 6)}-${a.substring(6, 8)}-${a.substring(8, 10)}"
    }
}

object ChatUnread {
    fun flag(otherLatest: Instant?, newest: Instant?, newestIsMine: Boolean, lastRead: Instant): Int {
        if (otherLatest != null) return if (otherLatest.isAfter(lastRead)) 1 else 0
        if (newest != null && newest.isAfter(lastRead) && !newestIsMine) return 1
        return 0
    }
}

object BonusCopy {
    fun shortDate(iso: String): String {
        val parts = iso.split("-")
        if (parts.size != 3 || parts[0].length < 2) return iso
        return "${parts[2]}.${parts[1]}.${parts[0].takeLast(2)}"
    }

    fun line(item: BonusLine): String {
        val parts = mutableListOf<String>()
        val tour = item.title.trim()
        if (tour.isNotEmpty()) parts += tour
        parts += reason(item.kind)
        parts += money(item.amount, item.remaining)
        parts += state(item.state)
        if (item.state == "available" && !item.expiresOn.isNullOrEmpty()) {
            parts += "до ${shortDate(item.expiresOn)}"
        }
        return parts.joinToString(" · ")
    }

    fun bookingNote(discount: Int, spent: Int): String? = when {
        discount > 0 && spent > 0 ->
            "Скидка по коду ${Money.rubles(discount)}, бонусами закрыто ${Money.rubles(spent)}"
        discount > 0 -> "Скидка по коду ${Money.rubles(discount)}"
        spent > 0 -> "Бонусами закрыто ${Money.rubles(spent)}"
        else -> null
    }

    private fun reason(kind: String) = when (kind) {
        "referral_first" -> "за первую поездку друга"
        "referral_repeat" -> "за поездку друга"
        "legacy" -> "старая программа лояльности"
        "manual" -> "от команды TNA"
        else -> "за свою поездку"
    }

    private fun state(value: String) = when (value) {
        "pending" -> "ожидает"
        "available" -> "доступно"
        "spent" -> "списано"
        "expired" -> "сгорело"
        "reversed" -> "снято"
        else -> value
    }

    private fun money(amount: Int, remaining: Int): String {
        if (remaining > 0 && remaining != amount) {
            return "${Money.rubles(remaining)} из ${Money.rubles(amount)}"
        }
        return Money.rubles(amount)
    }
}

/// «Нашлась неточность?» topics; each opens the support chat with a prefilled draft.
enum class DataIssue(val title: String, val prefill: String) {
    Trips("Поездки", "Здравствуйте! В приложении неточность в моих поездках: "),
    Bonuses("Бонусы", "Здравствуйте! Кажется, неверно посчитаны мои бонусы: "),
    Personal("Имя или личные данные", "Здравствуйте! В моих данных в приложении ошибка: "),
    Other("Другое", "Здравствуйте! В приложении неточность: "),
}
