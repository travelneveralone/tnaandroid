package com.travelneveralone.tna

import java.time.LocalDate
import java.time.Period
import java.util.UUID

// Domain models for the features ported in the second pass: polls, public profiles,
// year recap, trip pack and identity documents. Mirrors TNAiOS Core/Models.

/// Live tally from `poll_state` (iOS `PollState`).
data class PollState(
    val total: Int,
    val closed: Boolean,
    val my: List<String>,
    val options: List<Opt>,
) {
    data class Opt(val id: String, val count: Int, val voters: List<String>)

    fun count(id: String): Int = options.firstOrNull { it.id == id }?.count ?: 0
    fun voters(id: String): List<String> = options.firstOrNull { it.id == id }?.voters.orEmpty()
    fun fraction(id: String): Float = if (total > 0) count(id).toFloat() / total else 0f
}

/// Poll being composed in the chat (iOS `PollDraft`).
data class PollDraftOption(val id: UUID = UUID.randomUUID(), val text: String = "")

data class PollDraft(
    val question: String = "",
    val descr: String = "",
    val options: List<PollDraftOption> = listOf(PollDraftOption(), PollDraftOption()),
    val multiple: Boolean = false,
    val showNames: Boolean = true,
    val addOptions: Boolean = false,
    val revote: Boolean = true,
    val shuffle: Boolean = false,
    val quiz: Boolean = false,
    val correct: Set<UUID> = emptySet(),
    val hasDeadline: Boolean = false,
    val closesAt: java.time.Instant = java.time.Instant.now().plusSeconds(24 * 3600),
) {
    private val filled: List<PollDraftOption> get() = options.filter { it.text.isNotBlank() }
    val canAddOption: Boolean get() = options.size < MAX_OPTIONS
    val isValid: Boolean
        get() {
            if (question.isBlank() || filled.size < 2) return false
            if (quiz) return filled.any { correct.contains(it.id) }
            return true
        }

    fun build(): PollData {
        val picked = filled
        val opts = picked.mapIndexed { i, d -> PollOption("o${i + 1}", d.text.trim()) }
        val correctIds = if (quiz) picked.mapIndexedNotNull { i, d -> if (correct.contains(d.id)) "o${i + 1}" else null } else emptyList()
        return PollData(
            question = question.trim(),
            description = descr.trim().ifBlank { null },
            options = opts,
            multiple = if (quiz) false else multiple,
            anonymous = !showNames,
            addOptions = if (quiz) false else addOptions,
            revote = if (quiz) false else revote,
            quiz = quiz,
            correct = correctIds,
            shuffle = shuffle,
            closesAt = if (hasDeadline) closesAt.toString() else null,
            closed = false,
        )
    }

    companion object {
        const val MAX_OPTIONS = 12
    }
}

/// Role badge next to a name (iOS `staffTag`).
fun staffTag(isChatAdmin: Boolean, globalRole: String): String? {
    if (isChatAdmin) return "гид"
    return when (globalRole) {
        "guide", "coordinator" -> "гид"
        "curator" -> "куратор"
        "manager", "admin" -> "менеджер"
        else -> null
    }
}

/// Who opened a mini-profile and what we already know about them.
data class MiniProfile(
    val userId: UUID,
    val name: String,
    val city: String = "",
    val role: String = "client",
    val avatarUrl: String? = null,
    val isChatAdmin: Boolean = false,
) {
    val roleTag: String? get() = staffTag(isChatAdmin, role)
}

/// `public_profile()` (0079): someone else's passport, privacy-filtered on the server.
data class PublicProfile(
    val name: String = "",
    val city: String = "",
    val role: String = "client",
    val avatarUrl: String? = null,
    val memberSinceYear: Int? = null,
    val tours: Int = 0,
    val countries: Int = 0,
    val km: Int = 0,
    val days: Int = 0,
    val maxAltitude: Int = 0,
    val achievements: List<Achievement> = emptyList(),
    val stamps: List<Stamp> = emptyList(),
    val togetherCount: Int = 0,
    val togetherAsGuide: Boolean = false,
    val togetherTours: List<String> = emptyList(),
    val departuresLed: Int? = null,
    val directions: List<String> = emptyList(),
) {
    val isStaff: Boolean get() = staffTag(false, role) != null
    val level: TravelerLevel get() = TravelerLevel.forTours(tours)
    val statItems: List<StatItem>
        get() = buildList {
            if (tours > 0) add(StatItem("Тур", "Тура", "Туров", tours, "", "flag.checkered"))
            if (km > 0) add(StatItem("Километр", "Километра", "Километров", km, "", "point.topleft.down.to.point.bottomright.curvepath.fill"))
            if (days > 0) add(StatItem("День в пути", "Дня в пути", "Дней в пути", days, "", "sun.horizon.fill"))
            if (maxAltitude > 0) add(StatItem.fixed("Высшая точка", maxAltitude, "м", "mountain.2.fill"))
        }
    val hasTravelStats: Boolean get() = statItems.isNotEmpty()
    val memberSinceText: String?
        get() {
            val year = memberSinceYear?.takeIf { it > 2000 } ?: return null
            return if (isStaff) "в команде TNA с $year" else "с TNA с $year"
        }
    val togetherText: String?
        get() {
            if (togetherCount <= 0) return null
            if (togetherAsGuide) {
                return if (togetherCount == 1) "Вёл твою поездку"
                else "Вёл $togetherCount ${pluralRU(togetherCount, "твою поездку", "твои поездки", "твоих поездок")}"
            }
            return "Ездили вместе в $togetherCount ${pluralRU(togetherCount, "поездке", "поездках", "поездках")}"
        }
}

/// `my_year_recap()`: «Мой год с TNA». Null when the year has no finished trips.
data class YearRecap(
    val year: String,
    val tours: Int,
    val km: Int,
    val newAchievements: Int,
    val topDestination: String,
    val topDestinationColors: List<Long>,
    val stamps: List<Stamp>,
)

fun newAchievementsCaption(n: Int): String =
    "${pluralRU(n, "новое", "новых", "новых")} ${pluralRU(n, "достижение", "достижения", "достижений")}"

/// `trip_pack()`: the traveller's current/next trip, cached for offline use.
data class TripPlan(
    val tourName: String,
    val dateRange: String,
    val statusLine: String,
    val colors: List<Long>,
    val schedule: List<TripScheduleItem>,
    val contacts: List<TripContact>,
    val dayIndex: Int?,
)

data class TripScheduleItem(val time: String, val title: String, val icon: String)

object TripPackMapper {
    private val ru = java.util.Locale("ru")
    private val dayMonth = java.time.format.DateTimeFormatter.ofPattern("d MMM", ru)

    fun statusLine(phase: String, dayIndex: Int?, totalDays: Int, todayTitle: String?, start: LocalDate?, today: LocalDate = LocalDate.now()): String =
        when (phase) {
            "active" -> {
                if (dayIndex == null) "Тур идёт"
                else {
                    val base = "День $dayIndex из $totalDays"
                    todayTitle?.let { "$base · $it" } ?: base
                }
            }
            "ended" -> "Тур завершён"
            else -> {
                if (start == null) "Скоро в путь"
                else {
                    val days = maxOf(0, java.time.temporal.ChronoUnit.DAYS.between(today, start).toInt())
                    val whenText = dayMonth.format(start)
                    if (days == 0) "Старт сегодня · $whenText"
                    else "Старт $whenText · через $days ${pluralRU(days, "день", "дня", "дней")}"
                }
            }
        }

    fun icon(title: String): String {
        val t = title.lowercase()
        return when {
            listOf("перелёт", "прилёт", "вылет", "самол").any { t.contains(it) } -> "airplane"
            listOf("треккинг", "поход", "восхожд", "хайк").any { t.contains(it) } -> "figure.hiking"
            listOf("вулкан", "гор", "перевал").any { t.contains(it) } -> "mountain.2.fill"
            listOf("море", "океан", "пляж", "бухт").any { t.contains(it) } -> "water.waves"
            listOf("баня", "термал", "источник").any { t.contains(it) } -> "flame.fill"
            listOf("обед", "ужин", "завтрак", "гастро").any { t.contains(it) } -> "fork.knife"
            listOf("переезд", "трансфер", "выезд", "дорог").any { t.contains(it) } -> "car.fill"
            listOf("отдых", "свобод").any { t.contains(it) } -> "sparkles"
            else -> "mappin.and.ellipse"
        }
    }
}

/// Identity document kinds stored on profiles and companions (0032/0033).
enum class DocKind(val raw: String, val title: String) {
    PassportRF("passport_rf", "Внутренний паспорт"),
    PassportIntl("passport_intl", "Загранпаспорт"),
    BirthCert("birth_cert", "Свидетельство о рождении");

    val hasExpiry: Boolean get() = this == PassportIntl
    val hasInternalFields: Boolean get() = this == PassportRF

    companion object {
        /// Russian domestic passport is issued from 14; younger travellers default to a birth certificate.
        const val RF_PASSPORT_MIN_AGE = 14

        fun fromRaw(raw: String?): DocKind? = entries.firstOrNull { it.raw == raw }

        fun ageYears(birthdayIso: String?, at: LocalDate = LocalDate.now()): Int? {
            val birth = birthdayIso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return Period.between(birth, at).years
        }

        /// When no document is chosen yet and the person is under 14, suggest a birth certificate.
        fun suggestedKind(birthdayIso: String?, current: DocKind?, number: String,
                          at: LocalDate = LocalDate.now()): DocKind? {
            if (current != null || number.isNotBlank()) return current
            val age = ageYears(birthdayIso, at) ?: return current
            return if (age < RF_PASSPORT_MIN_AGE) BirthCert else current
        }
    }
}

data class IdentityDoc(
    val type: String = "",
    val number: String = "",
    val citizenship: String = "",
    val issued: String? = null,
    val expiry: String? = null,
    val issuedBy: String = "",
    val registration: String = "",
) {
    val kind: DocKind? get() = DocKind.fromRaw(type)
    val isEmpty: Boolean get() = number.isBlank() && type.isBlank()

    /// Under 14 with no document chosen → birth certificate.
    fun withSuggestedKind(birthdayIso: String?, at: LocalDate = LocalDate.now()): IdentityDoc {
        val suggested = DocKind.suggestedKind(birthdayIso, kind, number, at) ?: return this
        return if (suggested == kind) this else copy(type = suggested.raw)
    }
}

/// Citizenship list: post-Soviet countries pinned on top, the rest alphabetically.
object Citizenship {
    private val priorityCodes = listOf("RUS", "BLR", "KAZ", "UKR", "ARM", "AZE", "KGZ", "MDA", "TJK", "UZB", "GEO", "TKM")
    val defaultValue: String get() = CountryNamesRU.map["RUS"] ?: "Россия"
    val priority: List<String> get() = priorityCodes.mapNotNull { CountryNamesRU.map[it] }
    val others: List<String> get() = CountryNamesRU.map.values.filter { it !in priority }.sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale("ru"))) { it })
    val all: List<String> get() = priority + others
}

/// «Как тебя видят другие» options (iOS `NameDisplay`).
object NameDisplay {
    fun variants(last: String, first: String, patronymic: String): List<String> {
        val l = last.trim()
        val f = first.trim()
        val p = patronymic.trim()
        val out = mutableListOf<String>()
        if (f.isNotEmpty()) out += f
        if (f.isNotEmpty() && p.isNotEmpty()) out += "$f $p"
        if (l.isNotEmpty() && f.isNotEmpty()) out += "$l $f"
        if (l.isNotEmpty() && f.isNotEmpty()) out += "$l ${f.first()}."
        if (f.isNotEmpty() && l.isNotEmpty()) out += "$f ${l.first()}."
        return out.distinct()
    }
}

enum class EmailVerifyResult(val userMessage: String) {
    Ok("Почта подтверждена ✓"),
    WrongCode("Неверный код"),
    Expired("Код истёк - нужен новый"),
    TooManyAttempts("Слишком много попыток"),
    InvalidEmail("Неверный адрес почты"),
    SendFailed("Не удалось отправить письмо"),
    Error("Что-то пошло не так - можно повторить");

    companion object {
        fun from(ok: Boolean?, error: String?): EmailVerifyResult = when {
            ok == true -> Ok
            error == "wrong_code" -> WrongCode
            error == "expired" -> Expired
            error == "too_many_attempts" -> TooManyAttempts
            error == "invalid_email" -> InvalidEmail
            error == "send_failed" || error == "no_code" -> SendFailed
            else -> Error
        }
    }
}

/// Avatar crop geometry (iOS AvatarCropSheet): the photo fills a circle of diameter d,
/// zoomed by scale and shifted by (x, y) from the circle centre.
object AvatarCrop {
    const val MAX_SCALE = 6f

    fun fillScale(d: Float, w: Float, h: Float, scale: Float): Float =
        if (w <= 0f || h <= 0f) 1f else maxOf(d / w, d / h) * scale

    /// Keeps the photo covering the whole circle.
    fun clamp(x: Float, y: Float, d: Float, w: Float, h: Float, scale: Float): Pair<Float, Float> {
        val s = fillScale(d, w, h, scale)
        val limitX = maxOf((w * s - d) / 2, 0f)
        val limitY = maxOf((h * s - d) / 2, 0f)
        return x.coerceIn(-limitX, limitX) to y.coerceIn(-limitY, limitY)
    }

    /// The circle's square in source pixels: left, top, side.
    fun cropRect(d: Float, w: Float, h: Float, scale: Float, x: Float, y: Float): Triple<Float, Float, Float> {
        val s = fillScale(d, w, h, scale)
        val left = (w * s / 2 - x - d / 2) / s
        val top = (h * s / 2 - y - d / 2) / s
        return Triple(left, top, d / s)
    }
}

/// `chat_participants()` row: who is in a group chat.
data class ChatParticipant(
    val id: UUID,
    val name: String,
    val city: String,
    val role: String,
    val isAdmin: Boolean,
    val muted: Boolean,
    val avatarUrl: String? = null,
    /// Blocked in this trip chat by the team (0157): out of the chat until unblocked.
    val isBlocked: Boolean = false,
    /// Who blocked them (name), for the "removed from chat" section.
    val blockedBy: String = "",
) {
    val roleTag: String? get() = staffTag(isAdmin, role)
    /// Partners are faceless representatives - never a personal name.
    val shownName: String get() = if (role == "partner") ChatNames.PARTNER else name
}

/// Who may be blocked in a trip chat (0157) - iOS `ChatModeration`. The server is
/// the authority (`can_block_in_chat` / `set_chat_member_blocked`); this only decides
/// which rows get the action, so the menu never offers something the server refuses.
object ChatModeration {
    /// Only travellers can be blocked - never yourself, staff or partners.
    fun canBlock(p: ChatParticipant, me: UUID?, canBlockHere: Boolean): Boolean =
        canBlockHere && p.id != me && !p.isBlocked && p.role == "client"

    /// Any team member who may block here may also bring someone back.
    fun canUnblock(p: ChatParticipant, canBlockHere: Boolean): Boolean = canBlockHere && p.isBlocked
}

/// Chat display rules shared by bubbles, the participant list and @-mentions.
object ChatNames {
    const val PARTNER = "Представитель TNA"

    /// Name above a bubble: partner → «Представитель TNA»; unnamed staff → role word.
    fun author(message: Message, participant: ChatParticipant?): String {
        if (!message.asCompany && participant?.role == "partner") return PARTNER
        val name = message.author.ifBlank { "Участник" }
        val tag = if (message.asCompany) null else participant?.roleTag
        if (tag != null && name == "Участник") return if (tag == "менеджер") "Менеджер" else "Гид"
        return name
    }

    /// The active "@" token at the end of the draft; null when not mentioning.
    fun mentionQuery(draft: String): String? {
        val at = draft.lastIndexOf('@')
        if (at < 0) return null
        if (at > 0 && !draft[at - 1].isWhitespace()) return null
        val after = draft.substring(at + 1)
        return if (after.any { it.isWhitespace() }) null else after
    }

    fun mentionMatches(query: String?, people: List<ChatParticipant>, me: UUID?): List<ChatParticipant> {
        val q = query?.lowercase() ?: return emptyList()
        return people.filter { p ->
            p.id != me && (q.isEmpty() || p.name.lowercase().startsWith(q) || p.name.lowercase().split(" ").any { it.startsWith(q) })
        }.take(8)
    }

    /// Replaces the trailing "@query" with "@Name ".
    fun insertMention(draft: String, name: String): String {
        val at = draft.lastIndexOf('@')
        return if (at < 0) draft else draft.substring(0, at) + "@$name "
    }
}

/// Splits a request note into its summary and the companion blocks, so the booking
/// detail can show each companion as a separate collapsible row.
class ParsedBookingNote(note: String) {
    val summary: String
    val companions: List<Pair<String, List<String>>>

    init {
        val at = note.indexOf("👥")
        if (at >= 0) {
            summary = note.substring(0, at).trim()
            companions = parse(note.substring(at))
        } else {
            summary = note.trim()
            companions = emptyList()
        }
    }

    private fun parse(block: String): List<Pair<String, List<String>>> {
        val out = mutableListOf<Pair<String, List<String>>>()
        var name: String? = null
        var details = mutableListOf<String>()
        for (raw in block.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("👥")) continue
            // Visual rule between people in the chat / amo text.
            // Old notes in the database still use the long dash: accept both.
            if (line.all { it == '\u2014' || it == '-' }) continue
            val dot = line.indexOf('.')
            // A companion header looks like "1. Фамилия Имя".
            if (dot > 0 && line.substring(0, dot).all { it.isDigit() }) {
                name?.let { out += it to details }
                name = line.substring(dot + 1).trim()
                details = mutableListOf()
            } else {
                details += line
            }
        }
        name?.let { out += it to details }
        return out
    }
}
