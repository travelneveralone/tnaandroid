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
    /** Focus point of the cover ([imageUrl]) - the crop keeps it in frame (TNA-57). */
    val imageFocus: ImageFocus = ImageFocus.Center,
    /** Focus point of each gallery photo; a photo missing here is cropped around the centre. */
    val photoFocus: Map<String, ImageFocus> = emptyMap(),
    val videoUrl: String? = null,
    var salePriceFrom: Int? = null,
    var saleLabel: String? = null,
    /** «Что взять с собой» (tours.packing_list, 0173): the trip checklist. Empty hides the section. */
    val packingList: List<String> = emptyList(),
) {
    val heroImages: List<String> get() = images.ifEmpty { listOfNotNull(imageUrl) }

    /** Where to crop [url]: its own gallery focus, else the cover focus when it is the cover, else the centre. */
    fun focusFor(url: String): ImageFocus = photoFocus[url] ?: if (url == imageUrl) imageFocus else ImageFocus.Center
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
    /** Focus point of that cover (TNA-57). */
    val imageFocus: ImageFocus = ImageFocus.Center,
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
    val phone: String = "",
    val doc: IdentityDoc = IdentityDoc(),
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
    /** A certificate that passed the PIN check: linked to the request (TNA-120). */
    val certificateCode: String? = null,
    val certificatePin: String? = null,
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

data class ReturningCustomer(val name: String, val tripsCount: Int) {
    companion object {
        /// What `my_welcome()` (TNACloud 0176) answered: a greeting needs a name
        /// and at least one trip, anything else is a new person (iOS twin).
        fun from(name: String?, trips: Int?): ReturningCustomer? {
            val clean = name.orEmpty().trim()
            if (clean.isEmpty() || trips == null || trips <= 0) return null
            return ReturningCustomer(clean, trips)
        }
    }
}

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
    Traveler("Путешественник", 1, "Ещё пара туров - и ты «Первопроходец»"),
    Pioneer("Первопроходец", 3, "7 туров с TNA открывают уровень «Легенда TNA» - скидка и ранний доступ к новинкам"),
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
    val email: String = "",
    val emailVerified: Boolean = false,
    val doc: IdentityDoc = IdentityDoc(),
) {
    val displayName: String
        get() = listOf(lastName, firstName).filter { it.isNotBlank() }.joinToString(" ")
}

enum class MessageStatus { Sent, Queued, Failed }
enum class MessageKind { Text, Image, File, Audio, Poll, Location }

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
    /// Point of a `kind == Location` message (TNACloud 0158).
    val location: LocationData? = null,
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
    /** Empty 1:1 shells stay off the list until someone actually sends. */
    val belongsInChatList: Boolean
        get() = kind != ChatThreadKind.Direct || lastActivity != null

    companion object {
        val supportPlaceholder = ChatThread(
            title = "Служба заботы TNA",
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

/** Success-screen line when THIS submission bound a friend's code (TNA-186). */
const val REFERRAL_ACCEPTED_NOTE = "Код друга принят! 🎉 Скидка 6 000 ₽ на первую поездку - её видно в «Моих бронях»."

enum class ReferralResult(val userMessage: String) {
    Ok("Код друга принят! 🎉"),
    Already("Код уже был применён раньше"),
    NotFound("Такого кода нет"),
    OwnCode("Свой собственный код вводить нельзя"),
    Unauthorized("Код принимается только после входа в аккаунт"),
    Existing("Код друга - для первой поездки с TNA, а ты уже ездил с нами. Убери код, и заявка уйдёт"),
    Error("Не получилось - можно повторить");

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
            Error -> "Не удалось проверить - можно повторить"
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
    /** Goes with the certificate picture (iOS shareMessage): the picture has
     *  no PIN, so the number and PIN travel here. */
    val shareMessage: String
        get() = listOf(
            "🎁 Тебе подарок - путешествие с Travel Never Alone!",
            "Сертификат: ${code.orEmpty()}",
            "PIN для активации: $pin",
            "Выбрать тур: travelneveralone.com",
        ).joinToString("\n")
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

    /** Booking, passport/achievements, countries map, companions, bookings and
     *  gift certificates. Staff travel with us too (TNA-60): everyone except
     *  partners (iOS `UserRole.canBookAndEarn`). */
    val canBookAndEarn: Boolean get() = this != Partner
    /** Referral promo - the server issues referral codes to clients only. */
    val canRefer: Boolean get() = this == Client
    val canDirectMessage: Boolean get() = this != Partner
    val isManagement: Boolean get() = this == Admin || this == Manager

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

/**
 * How a departure's dates read to a client: always with the year, so a February
 * tour can't be mistaken for last or next February (TNA-67). Mirrors iOS
 * `DepartureDates`, SQL `departure_label()` (0168) and the admin's
 * `departureLabel()` - keep all four in step.
 */
object DepartureDates {
    /** "1–8 мая" form: genitive "мая", not "май". */
    private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    /** «3 янв 2027», «21–23 фев 2027», «28 фев – 3 мар 2027», «30 дек 2026 – 2 янв 2027». */
    fun label(start: LocalDate, end: LocalDate?): String {
        val b = end ?: start
        val nameA = months[start.monthValue - 1]
        val nameB = months[b.monthValue - 1]
        return when {
            start == b -> "${start.dayOfMonth} $nameA ${start.year}"
            start.year == b.year && start.monthValue == b.monthValue -> "${start.dayOfMonth}–${b.dayOfMonth} $nameB ${b.year}"
            start.year == b.year -> "${start.dayOfMonth} $nameA – ${b.dayOfMonth} $nameB ${b.year}"
            else -> "${start.dayOfMonth} $nameA ${start.year} – ${b.dayOfMonth} $nameB ${b.year}"
        }
    }

    /** From the dates when the start is known, else the server text (old cache, fixtures). */
    fun label(start: LocalDate?, end: LocalDate?, fallback: String): String =
        if (start == null) fallback else label(start, end)

    /** The mark goes in front with a capital first letter: «Сакура · 28 мар – 7 апр 2027». */
    fun tagged(label: String, tag: String?): String {
        val mark = tag?.trim().orEmpty()
        if (mark.isEmpty()) return label
        val head = mark.substring(0, 1).uppercase() + mark.substring(1)
        return if (label.isEmpty()) head else "$head · $label"
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

/**
 * Canonical phone forms (iOS `PhoneNumber`). A number written with '+' is already
 * international and stays as is (+81 90… is Japan, not "8 → 7"); without '+' the
 * legacy Russian rules apply: leading 8 → 7, bare 10 digits get 7.
 */
object PhoneNumber {
    fun digits(raw: String): String {
        var d = raw.filter { it in '0'..'9' }
        if ('+' in raw) return d
        if (d.length == 11 && d.startsWith("8")) d = "7" + d.drop(1)
        if (d.length == 10) d = "7$d"
        return d
    }

    fun e164(raw: String): String {
        val d = digits(raw)
        return if (d.isEmpty()) "" else "+$d"
    }

    /** Display in the country's own style: "+7 999 000-00-00", "+31 6 12345678". Unknown code → E.164. */
    fun pretty(raw: String): String {
        val d = digits(raw)
        val (country, national) = PhoneInput.resolve(d, null) ?: return e164(raw)
        if (national.isEmpty()) return e164(raw)
        return "${country.dialCodeDisplay} ${PhoneInput.format(national, country.mask)}"
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

// MARK: - Location in chats (TNACloud 0158, iOS LocationData / LocationText / LiveLocationSharer)

/// A point shared in a chat. A one-off pin has no `liveUntil`; a live location
/// has it and keeps moving until then (or until its author stops it).
data class LocationData(
    val lat: Double,
    val lng: Double,
    val accuracy: Double? = null,
    val liveUntil: Instant? = null,
    val updatedAt: Instant? = null,
    val stopped: Boolean = false,
) {
    /// Shared as a live location (running or finished).
    val isLiveKind: Boolean get() = liveUntil != null

    /// Still moving at `now`: live, not stopped, end time ahead.
    fun isLive(now: Instant = Instant.now()): Boolean =
        !stopped && liveUntil != null && liveUntil.isAfter(now)
}

/// One device fix.
data class GeoPoint(val lat: Double, val lng: Double, val accuracy: Double? = null, val at: Instant = Instant.now()) {
    /// Great-circle distance in metres (haversine).
    fun distanceTo(other: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(other.lat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/// How long a live location runs (Telegram's set).
enum class LivePeriod(val seconds: Long, val title: String) {
    Minutes15(15 * 60, "15 минут"),
    Hour1(3600, "1 час"),
    Hours8(8 * 3600, "8 часов"),
}

/// Texts for the location bubble, the map card and the banner - same as iOS.
object LocationText {
    fun label(location: LocationData): String =
        if (location.isLiveKind) "📍 Трансляция геопозиции" else "📍 Геопозиция"

    fun updated(updatedAt: Instant?, now: Instant = Instant.now()): String? {
        updatedAt ?: return null
        val s = maxOf(0L, java.time.Duration.between(updatedAt, now).seconds)
        if (s < 60) return "обновлено только что"
        val m = s / 60
        if (m < 60) return "обновлено $m мин назад"
        return "обновлено ${m / 60} ч назад"
    }

    fun remaining(until: Instant, now: Instant = Instant.now()): String {
        val total = maxOf(0L, java.time.Duration.between(now, until).seconds / 60)
        val h = total / 60
        val m = total % 60
        if (h == 0L) return "ещё ${maxOf(m, 1L)} мин"
        if (m == 0L) return "ещё $h ч"
        return "ещё $h ч $m мин"
    }

    /// Footer title + subtitle at `now`.
    fun status(location: LocationData, now: Instant = Instant.now()): Pair<String, String?> {
        val until = location.liveUntil
        if (location.isLive(now) && until != null) {
            val bits = listOfNotNull(updated(location.updatedAt, now), remaining(until, now))
            return "Трансляция геопозиции" to bits.joinToString(" · ")
        }
        if (location.isLiveKind) return "Трансляция завершена" to null
        val accuracy = location.accuracy?.takeIf { it > 0 }?.let { "точность ~${Math.round(it)} м" }
        return "Геопозиция" to accuracy
    }

    fun coordinates(location: LocationData): String =
        String.format(java.util.Locale.US, "%.6f, %.6f", location.lat, location.lng)
}

/// When a live session sends a fix: never more often than `minInterval`; then
/// when the person moved `minDistance`, or the `heartbeat` is due.
object LiveThrottle {
    const val MIN_INTERVAL_SEC = 10L
    const val MIN_DISTANCE_M = 30.0
    const val HEARTBEAT_SEC = 60L

    fun shouldSend(point: GeoPoint, last: GeoPoint?): Boolean {
        last ?: return true
        val elapsed = java.time.Duration.between(last.at, point.at).seconds
        if (elapsed >= HEARTBEAT_SEC) return true
        if (elapsed < MIN_INTERVAL_SEC) return false
        return point.distanceTo(last) >= MIN_DISTANCE_M
    }
}

/// Markers for the chat map: every location still live (one per author, the
/// newest) plus the tapped one, if any.
fun chatMapPoints(messages: List<Message>, focus: Message? = null, now: Instant = Instant.now()): List<Message> {
    val live = messages.filter { it.kind == MessageKind.Location && it.location?.isLive(now) == true }
    val all = if (focus != null && live.none { it.id == focus.id }) live + focus else live
    val seen = mutableSetOf<String>()
    return all.sortedByDescending { it.createdAt }.filter { m ->
        if (m.id == focus?.id) true else seen.add(m.authorId?.toString() ?: m.author)
    }
}

/// Keeps the local chat in the order the server stores it.
object ChatOrder {
    /// The server stamps a row when it lands, not when it was typed: a message
    /// sent late (bad network, a retry) sits AFTER whatever others sent meanwhile.
    /// Mark it sent and move it there, or the next history load reshuffles the
    /// chat (TNA-150, same as iOS). sortedBy is stable, so ties keep their order.
    fun landed(messages: List<Message>, id: UUID, at: Instant): List<Message> =
        messages.map {
            if (it.id != id) it
            else it.copy(status = MessageStatus.Sent, createdAt = if (it.createdAt < at) at else it.createdAt)
        }.sortedBy { it.createdAt }
}
