package com.travelneveralone.tna

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class TnaRepository(
    private val api: Postgrest,
    private val auth: AuthSession,
) {
    suspend fun fetchTours(): List<Tour> {
        val today = Dates.todayUtc().toString()
        val columns = "id,slug,name,tagline,hashtag,region,days,km,price_from," +
            "difficulty,max_altitude,symbol,color_start,color_end,image_url,video_url,includes," +
            "tour_days(day_number,title,details),departures(id,label,spots_left,starts_on)," +
            "tour_images(url,sort)"
        val raw = api.get("tours", listOf(
            "select" to columns,
            "is_active" to "eq.true",
            "departures.status" to "in.(open,full)",
            "departures.starts_on" to "gte.$today",
            "departures.order" to "starts_on.asc",
            "order" to "sort",
        ), ttlSec = 120)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            el.jsonObjectOrNull()?.let { Mappers.tour(it) }
        }
    }

    suspend fun fetchCampaign(): CampaignOffer? {
        val raw = runCatching { api.rpc("app_campaign", ttlSec = 300) }.getOrNull() ?: return null
        if (raw.isBlank() || raw == "null") return null
        val obj = runCatching { json.parseToJsonElement(raw).jsonObjectOrNull() }.getOrNull() ?: return null
        val sales = mutableMapOf<UUID, CampaignSale>()
        obj["tours"]?.arrayOrEmpty()?.forEach { el ->
            val row = el.jsonObjectOrNull() ?: return@forEach
            val id = row.str("tour_id")?.let(UUID::fromString) ?: return@forEach
            val price = row.int("price_from") ?: return@forEach
            val base = row.int("base") ?: return@forEach
            if (price < base) sales[id] = CampaignSale(price, row.str("label").orEmpty())
        }
        return CampaignOffer(
            name = obj.str("name").orEmpty(),
            title = obj.str("title").orEmpty(),
            text = obj.str("text").orEmpty(),
            icon = obj.str("icon"),
            until = obj.str("until"),
            sales = sales,
        )
    }

    suspend fun submitBooking(request: BookingRequest) {
        val body = buildJsonObject {
            put("name", request.name)
            put("phone", request.phone)
            put("people_count", request.peopleCount)
            put("note", request.details.ifEmpty { listOf(request.subject) }.joinToString("\n"))
            request.departureId?.let { put("departure_id", it.toString()) }
            put("source", "app")
        }.toString()
        api.post("bookings", body, prefer = "return=minimal")
    }

    suspend fun joinWaitlist(departureId: UUID, name: String, phone: String) {
        api.rpc("join_waitlist", buildJsonObject {
            put("p_departure", departureId.toString())
            put("p_name", name)
            put("p_phone", phone)
        }.toString())
    }

    suspend fun fetchBookings(): List<BookingSummary> {
        val raw = api.get("bookings", listOf(
            "select" to "id,note,status,people_count,created_at,departure_id,departures(label,tours(name))",
            "order" to "created_at.desc",
        ), ttlSec = 60)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val dep = row["departures"]?.jsonObjectOrNull()
            val tourName = dep?.get("tours")?.jsonObjectOrNull()?.str("name")
            val note = row.str("note").orEmpty()
            BookingSummary(
                id = UUID.fromString(row.str("id")),
                title = tourName ?: note.lineSequence().firstOrNull().orEmpty().ifBlank { "Заявка" },
                subtitle = dep?.str("label") ?: "индивидуальный запрос",
                status = BookingStatus.fromRaw(row.str("status").orEmpty()),
                date = row.str("created_at")?.let { displayTime(it) }.orEmpty(),
                peopleCount = row.int("people_count") ?: 0,
                note = note,
            )
        }
    }

    suspend fun fetchNews(): List<NewsItem> {
        val raw = api.get("news", listOf(
            "select" to "id,title,body,image_url,created_at,link_tour_id,link_url,is_pinned",
            "is_published" to "eq.true",
            "order" to "is_pinned.desc,created_at.desc",
        ), ttlSec = 120)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val link = when {
                row.str("link_tour_id") != null -> NewsLink.TourLink(UUID.fromString(row.str("link_tour_id")))
                row.str("link_url") != null -> NewsLink.UrlLink(row.str("link_url")!!)
                else -> null
            }
            NewsItem(
                id = UUID.fromString(row.str("id")),
                title = row.str("title").orEmpty(),
                body = row.str("body").orEmpty(),
                imageUrl = row.str("image_url"),
                date = row.str("created_at")?.let { displayTime(it) }.orEmpty(),
                link = link,
                isPinned = row.bool("is_pinned") == true,
            )
        }
    }

    suspend fun fetchReviews(tourId: UUID): List<TourReview> = reviews(
        listOf(
            "select" to "id,rating,text,created_at,reply,author:public_profiles(name,avatar_url)",
            "tour_id" to "eq.${tourId}",
            "is_published" to "eq.true",
            "order" to "created_at.desc",
        )
    )

    suspend fun fetchAllReviews(): List<TourReview> = reviews(
        listOf(
            "select" to "id,rating,text,created_at,reply,author:public_profiles(name,avatar_url),tours(name)",
            "is_published" to "eq.true",
            "order" to "created_at.desc",
            "limit" to "60",
        )
    )

    private suspend fun reviews(query: List<Pair<String, String>>): List<TourReview> {
        val raw = api.get("reviews", query, ttlSec = 300)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val author = row["author"]?.jsonObjectOrNull()
            TourReview(
                id = UUID.fromString(row.str("id")),
                author = author?.str("name") ?: "Путешественник",
                rating = row.int("rating") ?: 5,
                text = row.str("text").orEmpty(),
                date = row.str("created_at")?.take(10).orEmpty(),
                reply = row.str("reply"),
                tourName = row["tours"]?.jsonObjectOrNull()?.str("name"),
                authorAvatarUrl = author?.str("avatar_url"),
            )
        }
    }

    suspend fun fetchDepartures(): List<DepartureItem> {
        val today = Dates.todayUtc().toString()
        val raw = api.get("departures", listOf(
            "select" to "id,label,starts_on,ends_on,spots_left,status,tours(id,name,region,color_start,color_end)",
            "ends_on" to "gte.$today",
            "status" to "in.(open,full,in_progress)",
            "order" to "starts_on.asc",
        ), ttlSec = 300)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val tour = row["tours"]?.jsonObjectOrNull() ?: return@mapNotNull null
            val start = Dates.parseDay(row.str("starts_on")) ?: return@mapNotNull null
            val end = Dates.parseDay(row.str("ends_on")) ?: return@mapNotNull null
            DepartureItem(
                id = UUID.fromString(row.str("id")),
                tourId = UUID.fromString(tour.str("id")),
                tourName = tour.str("name").orEmpty(),
                season = seasonName(start),
                region = TourRegion.fromRaw(tour.str("region").orEmpty()),
                colors = listOf(Mappers.hex(tour.str("color_start")), Mappers.hex(tour.str("color_end"))),
                label = row.str("label").orEmpty(),
                startsOn = start,
                endsOn = end,
                spotsLeft = row.int("spots_left") ?: 0,
                status = row.str("status").orEmpty(),
            )
        }
    }

    suspend fun fetchTrips(): List<Trip> {
        val raw = runCatching { api.rpc("my_trips", ttlSec = 120) }.getOrNull() ?: return emptyList()
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            Trip(
                id = UUID.fromString(row.str("id") ?: row.str("departure_id") ?: return@mapNotNull null),
                tourId = UUID.fromString(row.str("tour_id") ?: return@mapNotNull null),
                tourName = row.str("tour_name") ?: row.str("name").orEmpty(),
                colors = listOf(Mappers.hex(row.str("color_start")), Mappers.hex(row.str("color_end"))),
                symbol = row.str("symbol").orEmpty(),
                label = row.str("label").orEmpty(),
                startsOn = Dates.parseDay(row.str("starts_on")),
                endsOn = Dates.parseDay(row.str("ends_on")),
                status = row.str("status").orEmpty(),
                role = row.str("role_in_trip") ?: row.str("role").orEmpty(),
                chatId = row.str("chat_id")?.let(UUID::fromString),
                imageUrl = row.str("image_url"),
            )
        }
    }

    suspend fun myRole(): String = runCatching {
        api.rpc("my_role").trim().trim('"')
    }.getOrDefault("client")

    suspend fun myDmRoles(): List<String>? = runCatching {
        val raw = api.rpc("my_dm_roles")
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull()

    suspend fun myCode(): String? = runCatching { api.rpc("my_referral_code").trim().trim('"').ifBlank { null } }.getOrNull()

    suspend fun bonusSummary(): BonusSummary? = runCatching {
        val obj = json.parseToJsonElement(api.rpc("my_bonus_summary")).jsonObjectOrNull() ?: return null
        BonusSummary(
            available = obj.int("available") ?: 0,
            pending = obj.int("pending") ?: 0,
            bound = obj.bool("bound") == true,
            expiresOn = obj.str("expires_on"),
        )
    }.getOrNull()

    suspend fun fetchPassport(displayName: String): PassportData {
        val raw = api.rpc("my_passport", ttlSec = 300)
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return Mock.passport.copy(userName = displayName)
        val stats = obj["stats"]?.jsonObjectOrNull()
        val tours = stats?.int("tours") ?: 0
        val level = TravelerLevel.forTours(tours)
        val stamps = obj["stamps"]?.arrayOrEmpty()?.mapIndexedNotNull { index, el ->
            val row = el.jsonObjectOrNull() ?: return@mapIndexedNotNull null
            Stamp(
                title = row.str("title").orEmpty(),
                year = row.str("year").orEmpty(),
                icon = row.str("icon") ?: "mountain",
                color = Mappers.hex(row.str("color")),
                angle = PassportCopy.stampAngle(index),
            )
        }.orEmpty()
        val achievements = obj["achievements"]?.arrayOrEmpty()?.mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val details = row.str("details").orEmpty()
            Achievement(
                name = row.str("name").orEmpty(),
                details = details,
                icon = row.str("icon") ?: "star",
                earned = row.bool("earned") == true,
                color = Mappers.hex(row.str("color")),
                howTo = details,
                earnedDate = PassportCopy.monthYear(row.str("earned_at")),
            )
        }.orEmpty()
        val places = obj["places"]?.arrayOrEmpty()?.mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val name = row.str("name") ?: return@mapNotNull null
            VisitedPlace(
                name = name,
                latitude = row.dbl("lat") ?: 0.0,
                longitude = row.dbl("lng") ?: 0.0,
                color = Mappers.hex(row.str("color")),
            )
        }.orEmpty()
        val board = obj["leaderboard"]?.arrayOrEmpty()?.mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val km = row.int("km") ?: 0
            if (km <= 0) return@mapNotNull null
            val mine = row.bool("is_me") == true
            LeaderboardEntry(
                rank = row.int("rank") ?: 0,
                name = PassportCopy.shortName(row.str("name") ?: "—", mine),
                km = km,
                tours = row.int("tours") ?: 0,
                isMe = mine,
                avatarUrl = row.str("avatar"),
                userId = row.str("user_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            )
        }.orEmpty()
        return PassportData(
            userName = displayName,
            memberSince = PassportCopy.memberSince(obj.int("member_since_year")),
            level = level.title,
            levelProgress = level.progress(tours),
            nextLevelHint = level.nextHint,
            stats = PassportCopy.stats(
                tours = tours,
                countries = stats?.int("countries") ?: 0,
                km = stats?.int("km") ?: 0,
                days = stats?.int("days") ?: 0,
                maxAltitude = stats?.int("max_altitude") ?: 0,
            ),
            stamps = stamps,
            achievements = achievements,
            places = places,
            leaderboard = board,
        )
    }

    suspend fun fetchThreads(): List<ChatThread> {
        if (!api.isOffline) runCatching { api.rpc("ensure_manager_chat") }
        val me = auth.currentUserId()
        val membership = if (me == null) "chat_members" else "chat_members!inner"
        val query = mutableListOf(
            "select" to "id,kind,title,unread_from_others,messages(text,created_at,user_id),$membership(last_read_at,muted,archived),members:chat_members(user_id,profiles:public_profiles(name,avatar_url)),departures(tours(symbol,color_start,color_end,image_url))",
            "messages.order" to "created_at.desc",
            "messages.limit" to "1",
            "order" to "created_at.desc",
        )
        if (me != null) query += "chat_members.user_id" to "eq.$me"
        val raw = api.get("chats", query, ttlSec = null)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            thread(el.jsonObjectOrNull(), me)
        }
    }

    private fun thread(row: JsonObject?, me: UUID?): ChatThread? {
        row ?: return null
        val id = row.str("id")?.let(UUID::fromString) ?: return null
        val kind = row.str("kind").orEmpty()
        val last = row["messages"]?.arrayOrEmpty()?.firstOrNull()?.jsonObjectOrNull()
        val member = row["chat_members"]?.arrayOrEmpty()?.firstOrNull()?.jsonObjectOrNull()
        val others = row["members"]?.arrayOrEmpty()?.mapNotNull { it.jsonObjectOrNull() }.orEmpty()
        val other = others.firstOrNull { it.str("user_id") != me?.toString() }
        val otherName = other?.get("profiles")?.jsonObjectOrNull()?.str("name").orEmpty()
        val isDirect = kind == "direct"
        val isManager = kind == "manager"
        val tour = row["departures"]?.jsonObjectOrNull()?.get("tours")?.jsonObjectOrNull()
        val lastRead = parseInstant(member?.str("last_read_at")) ?: Instant.EPOCH
        val newest = parseInstant(last?.str("created_at"))
        val newestMine = last?.str("user_id") != null && last.str("user_id") == me?.toString()
        val otherAt = parseInstant(row.str("unread_from_others"))
        val threadKind = when {
            isManager -> ChatThreadKind.Support
            isDirect -> ChatThreadKind.Direct
            kind == "group" -> ChatThreadKind.Trip
            else -> ChatThreadKind.Community
        }
        val colors = if (tour?.str("color_start") != null && tour.str("color_end") != null) {
            listOf(Mappers.hex(tour.str("color_start")), Mappers.hex(tour.str("color_end")))
        } else when (kind) {
            "manager" -> listOf(0xFF8F5EL, 0xE63E00L)
            "group" -> listOf(0xFF6B3DL, 0x8B1E00L)
            else -> listOf(0x53C7F0L, 0x0D47A1L)
        }
        return ChatThread(
            id = id,
            title = if (isDirect) otherName.ifBlank { "Личный чат" } else row.str("title").orEmpty(),
            subtitle = when (kind) {
                "manager" -> "Аня · отвечаем быстро"
                "group" -> "групповой чат"
                "direct" -> "личные сообщения"
                else -> "сообщество TNA"
            },
            lastMessage = last?.str("text").orEmpty(),
            time = displayTime(last?.str("created_at")),
            unread = ChatUnread.flag(otherAt, newest, newestMine, lastRead),
            icon = tour?.str("symbol") ?: when (kind) {
                "manager" -> "headset"
                "direct" -> "person"
                else -> "globe"
            },
            colors = colors,
            isPinned = isManager,
            kind = threadKind,
            lastActivity = newest,
            otherUserId = if (isDirect) other?.str("user_id")?.let(UUID::fromString) else null,
            avatarUrl = if (isDirect) other?.get("profiles")?.jsonObjectOrNull()?.str("avatar_url") else null,
            coverUrl = if (kind == "group") tour?.str("image_url") else null,
            isSupport = isManager,
            muted = member?.bool("muted") == true,
            archived = member?.bool("archived") == true,
        )
    }

    suspend fun history(chatId: UUID, limit: Int = 50): List<Message> {
        val me = auth.currentUserId()?.toString()
        val raw = api.get("messages", listOf(
            "select" to "id,text,kind,media_url,media_name,duration_ms,edited_at,reply_to,album_id,pinned_at,poll,as_company,created_at,user_id," +
                "author:public_profiles!user_id(name,avatar_url),message_reactions(emoji,user_id)",
            "chat_id" to "eq.$chatId",
            "order" to "created_at.desc",
            "limit" to "$limit",
        ), ttlSec = null)
        val mapped = json.parseToJsonElement(raw).arrayOrEmpty()
            .mapNotNull { message(it.jsonObjectOrNull(), me) }
            .reversed()
        return signChatMedia(mapped)
    }

    suspend fun sendText(chatId: UUID, text: String, messageId: UUID = UUID.randomUUID()) {
        api.post("messages", buildJsonObject {
            put("id", messageId.toString())
            put("chat_id", chatId.toString())
            put("text", text)
            put("kind", "text")
        }.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        runCatching {
            api.function("notify-message", buildJsonObject { put("message_id", messageId.toString()) }.toString())
        }
    }

    suspend fun sendImage(chatId: UUID, bytes: ByteArray, messageId: UUID = UUID.randomUUID()) {
        val jpeg = compressJpeg(bytes)
        val path = "${chatId.toString().lowercase()}/${messageId.toString().lowercase()}.jpg"
        val url = api.upload("chat-media", path, jpeg, "image/jpeg")
        api.post("messages", buildJsonObject {
            put("id", messageId.toString())
            put("chat_id", chatId.toString())
            put("text", "")
            put("kind", "image")
            put("media_url", url)
        }.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        runCatching {
            api.function("notify-message", buildJsonObject { put("message_id", messageId.toString()) }.toString())
        }
    }

    suspend fun react(messageId: UUID, emoji: String, remove: Boolean) {
        if (remove) {
            api.delete("message_reactions", listOf("message_id" to "eq.${messageId.toString().lowercase()}"))
        } else {
            api.post("message_reactions", buildJsonObject {
                put("message_id", messageId.toString())
                put("emoji", emoji)
            }.toString(), prefer = "resolution=merge-duplicates,return=minimal")
        }
    }

    suspend fun votePoll(messageId: UUID, optionId: String): Boolean {
        val raw = api.rpc("vote_poll", buildJsonObject {
            put("p_message", messageId.toString())
            put("p_options", buildJsonArray { add(JsonPrimitive(optionId)) })
        }.toString())
        return raw.trim() == "true"
    }

    suspend fun ensureSupport(): ChatThread {
        val raw = api.rpc("ensure_manager_chat").trim().trim('"')
        val id = UUID.fromString(raw)
        return ChatThread.supportPlaceholder.copy(id = id)
    }

    suspend fun markRead(chatId: UUID) {
        runCatching { api.rpc("mark_chat_read", buildJsonObject { put("p_chat", chatId.toString()) }.toString()) }
    }

    private fun message(row: JsonObject?, me: String?): Message? {
        row ?: return null
        val author = row["author"]?.jsonObjectOrNull()
        val created = row.str("created_at")
        val user = row.str("user_id")
        return Message(
            id = UUID.fromString(row.str("id")),
            text = row.str("text").orEmpty(),
            isMine = row.bool("as_company") != true && user != null && user == me,
            author = if (row.bool("as_company") == true) "TNA" else author?.str("name") ?: "",
            time = displayTime(created),
            kind = when (row.str("kind")) {
                "image" -> MessageKind.Image
                "file" -> MessageKind.File
                "audio" -> MessageKind.Audio
                "poll" -> MessageKind.Poll
                else -> MessageKind.Text
            },
            mediaUrl = row.str("media_url"),
            edited = row.str("edited_at") != null,
            reactions = reactions(row["message_reactions"]?.arrayOrEmpty(), me),
            createdAt = parseInstant(created) ?: Instant.now(),
            poll = poll(row["poll"]?.jsonObjectOrNull()),
            authorId = user?.let(UUID::fromString),
            replyTo = row.str("reply_to")?.let(UUID::fromString),
            albumId = row.str("album_id")?.let(UUID::fromString),
            pinnedAt = parseInstant(row.str("pinned_at")),
            asCompany = row.bool("as_company") == true,
            authorAvatarUrl = author?.str("avatar_url"),
        )
    }

    suspend fun setCountry(code: String, status: CountryStatus?) {
        api.rpc("set_country_status", buildJsonObject {
            put("p_code", code.uppercase())
            put("p_status", status?.name?.lowercase() ?: "")
        }.toString())
    }

    suspend fun myCountries(): Map<String, CountryStatus> {
        val raw = runCatching { api.rpc("my_countries") }.getOrNull() ?: return emptyMap()
        val map = mutableMapOf<String, CountryStatus>()
        json.parseToJsonElement(raw).arrayOrEmpty().forEach { el ->
            val row = el.jsonObjectOrNull() ?: return@forEach
            val code = row.str("code")?.uppercase() ?: return@forEach
            CountryStatus.fromRaw(row.str("status").orEmpty())?.let { map[code] = it }
        }
        return map
    }

    suspend fun tnaCodes(): Set<String> {
        val raw = runCatching { api.rpc("tna_country_codes", "{}", ttlSec = 300) }.getOrNull() ?: return emptySet()
        return json.parseToJsonElement(raw).arrayOrEmpty()
            .mapNotNull { it.jsonPrimitiveOrNull()?.uppercase() }
            .toSet()
    }

    suspend fun applyReferral(code: String): ReferralResult {
        val raw = runCatching {
            api.rpc("apply_referral", buildJsonObject { put("p_code", code.trim()) }.toString())
        }.getOrNull()
        return ReferralResult.fromRaw(raw?.trim()?.trim('"'))
    }

    suspend fun fetchProfile(): ProfileDraft {
        val obj = json.parseToJsonElement(api.rpc("my_profile")).jsonObjectOrNull() ?: return ProfileDraft()
        return ProfileDraft(
            lastName = obj.str("last_name").orEmpty(),
            firstName = obj.str("first_name").orEmpty(),
            patronymic = obj.str("patronymic").orEmpty(),
            city = obj.str("city").orEmpty(),
            birthday = obj.str("birthday").orEmpty(),
        )
    }

    suspend fun saveProfile(draft: ProfileDraft) {
        val uid = auth.currentUserId() ?: return
        api.patch("profiles", listOf("id" to "eq.${uid.toString().lowercase()}"), buildJsonObject {
            put("last_name", draft.lastName.trim().ifBlank { null })
            put("first_name", draft.firstName.trim().ifBlank { null })
            put("patronymic", draft.patronymic.trim().ifBlank { null })
            put("city", draft.city.trim().ifBlank { null })
            put("birthday", draft.birthday.trim().ifBlank { null })
        }.toString())
    }

    suspend fun fetchPrefs(): NotificationPrefs {
        val raw = api.get("notification_prefs", listOf(
            "select" to "all_enabled,news,tours,chat,achievements,promos,analytics",
        ), ttlSec = null)
        val row = json.parseToJsonElement(raw).arrayOrEmpty().firstOrNull()?.jsonObjectOrNull() ?: return NotificationPrefs()
        return NotificationPrefs(
            allEnabled = row.bool("all_enabled") != false,
            news = row.bool("news") != false,
            tours = row.bool("tours") != false,
            chat = row.bool("chat") != false,
            achievements = row.bool("achievements") != false,
            promos = row.bool("promos") != false,
            analytics = row.bool("analytics") != false,
        )
    }

    suspend fun savePrefs(prefs: NotificationPrefs) {
        val uid = auth.currentUserId() ?: return
        api.post("notification_prefs", buildJsonObject {
            put("user_id", uid.toString())
            put("all_enabled", prefs.allEnabled)
            put("news", prefs.news)
            put("tours", prefs.tours)
            put("chat", prefs.chat)
            put("achievements", prefs.achievements)
            put("promos", prefs.promos)
            put("analytics", prefs.analytics)
        }.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun fetchCompanions(): List<TravelCompanion> {
        val raw = api.get("travel_companions", listOf(
            "select" to "id,full_name,last_name,first_name,patronymic,birthday",
            "order" to "created_at.desc",
        ), ttlSec = null)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val id = row.str("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@mapNotNull null
            var last = row.str("last_name").orEmpty()
            var first = row.str("first_name").orEmpty()
            if (last.isBlank() && first.isBlank()) {
                val parts = row.str("full_name").orEmpty().split(" ").filter { it.isNotBlank() }
                last = parts.getOrNull(0).orEmpty()
                first = parts.drop(1).joinToString(" ")
            }
            if (last.isBlank() && first.isBlank()) return@mapNotNull null
            TravelCompanion(
                id = id,
                lastName = last,
                firstName = first,
                patronymic = row.str("patronymic").orEmpty(),
                birthday = row.str("birthday"),
            )
        }
    }

    suspend fun saveCompanion(person: TravelCompanion) {
        val full = person.fullName
        api.post("travel_companions", buildJsonObject {
            put("id", person.id.toString())
            put("full_name", full)
            put("last_name", person.lastName.trim().ifBlank { null })
            put("first_name", person.firstName.trim().ifBlank { null })
            put("patronymic", person.patronymic.trim().ifBlank { null })
            put("birthday", person.birthday?.trim()?.ifBlank { null })
        }.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun deleteCompanion(id: UUID) {
        api.delete("travel_companions", listOf("id" to "eq.$id"))
    }

    suspend fun buyCertificate(amount: Int, recipient: String, wish: String, buyerName: String, buyerPhone: String): String? {
        val raw = api.function("buy-certificate", buildJsonObject {
            put("action", "create")
            put("amount", amount)
            put("recipient_name", recipient.trim())
            put("wish", wish.trim())
            put("buyer_name", buyerName.trim())
            put("buyer_phone", buyerPhone.trim())
        }.toString())
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return null
        return obj.str("payment_url") ?: obj["certificate"]?.jsonObjectOrNull()?.str("payment_url")
    }

    private fun reactions(rows: JsonArray?, me: String?): List<MessageReaction> {
        if (rows == null || rows.isEmpty()) return emptyList()
        val order = mutableListOf<String>()
        val counts = mutableMapOf<String, Int>()
        val mine = mutableMapOf<String, Boolean>()
        rows.forEach { el ->
            val row = el.jsonObjectOrNull() ?: return@forEach
            val emoji = row.str("emoji") ?: return@forEach
            if (emoji !in counts) order += emoji
            counts[emoji] = (counts[emoji] ?: 0) + 1
            if (row.str("user_id") == me) mine[emoji] = true
        }
        return order.map { MessageReaction(it, counts[it] ?: 0, mine[it] == true) }
    }

    private fun poll(row: JsonObject?): PollData? {
        row ?: return null
        val options = row["options"]?.arrayOrEmpty()?.mapNotNull { el ->
            val option = el.jsonObjectOrNull() ?: return@mapNotNull null
            val id = option.str("id") ?: return@mapNotNull null
            PollOption(id, option.str("text").orEmpty())
        }.orEmpty()
        if (row.str("question").isNullOrBlank() && options.isEmpty()) return null
        return PollData(
            question = row.str("question").orEmpty(),
            description = row.str("description"),
            options = options,
            multiple = row.bool("multiple") == true,
            anonymous = row.bool("anonymous") == true,
            addOptions = row.bool("add_options") == true,
            revote = row.bool("revote") != false,
            quiz = row.bool("quiz") == true,
            closed = row.bool("closed") == true,
            rev = row.int("rev") ?: 0,
        )
    }

    private suspend fun signChatMedia(messages: List<Message>): List<Message> {
        val marker = "/storage/v1/object/public/chat-media/"
        val paths = messages.mapNotNull { message ->
            val url = message.mediaUrl ?: return@mapNotNull null
            val index = url.indexOf(marker)
            if (index < 0) null else url.substring(index + marker.length).substringBefore('?')
        }.distinct()
        if (paths.isEmpty()) return messages
        val signed = runCatching { api.signedUrls("chat-media", paths) }.getOrDefault(emptyMap())
        if (signed.isEmpty()) return messages
        return messages.map { message ->
            val url = message.mediaUrl ?: return@map message
            val index = url.indexOf(marker)
            if (index < 0) return@map message
            val path = url.substring(index + marker.length).substringBefore('?')
            signed[path]?.let { message.copy(mediaUrl = it) } ?: message
        }
    }

    private fun compressJpeg(bytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val max = 1600
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > max) {
            val scale = max.toFloat() / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    fun dayLabel(date: java.time.LocalDate): String =
        DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ru")).format(date)
}

fun monthTitle(date: java.time.LocalDate): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", java.util.Locale("ru"))
        .format(date.atStartOfDay(ZoneId.of("UTC")))
        .replaceFirstChar { it.titlecase(java.util.Locale("ru")) }
