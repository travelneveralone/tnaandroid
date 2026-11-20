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
            "difficulty,max_altitude,symbol,color_start,color_end,image_url,image_focus_x,image_focus_y," +
            "video_url,includes,packing_list," +
            "tour_days(day_number,title,details),departures(id,label,spots_left,starts_on,ends_on,tag)," +
            "tour_images(url,sort,focus_x,focus_y)"
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

    /// Same pipeline as iOS `LiveBookingService.submit`: insert the booking, hand it to
    /// amoCRM (edge function), and drop a scannable summary into the support chat.
    suspend fun submitBooking(request: BookingRequest) {
        val companionNote = if (request.companions.isEmpty()) "" else "\n" + CompanionFormat.message(request.companions)
        val body = buildJsonObject {
            put("name", request.name)
            put("phone", request.phone)
            put("people_count", request.peopleCount)
            put("note", "${request.headline}: ${request.subject}$companionNote")
            request.departureId?.let { put("departure_id", it.toString()) }
            put("source", "app")
        }.toString()
        val created = api.post("bookings", body, prefer = "return=representation")
        val bookingId = json.parseToJsonElement(created).arrayOrEmpty().firstOrNull()?.jsonObjectOrNull()?.str("id")
        // Fire-and-forget: a failed push leaves amo status null for the admin retry.
        if (bookingId != null) {
            runCatching { api.function("amocrm-booking", buildJsonObject { put("booking_id", bookingId) }.toString()) }
        }
        runCatching {
            val chatId = api.rpc("ensure_manager_chat").trim().trim('"')
            val message = buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("chat_id", chatId)
                put("text", supportText(request))
            }.toString()
            api.post("messages", message, prefer = "resolution=ignore-duplicates")
        }
    }

    /// Gift certificate number + PIN, verified on the server (check_certificate RPC).
    suspend fun checkCertificate(code: String, pin: String): Pair<CertificateCheck, Int> = runCatching {
        val raw = api.rpc("check_certificate", buildJsonObject {
            put("p_code", code.trim())
            put("p_pin", pin.trim())
        }.toString())
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return@runCatching CertificateCheck.Error to 0
        when (obj.str("status")) {
            "paid" -> CertificateCheck.Applicable to (obj.dbl("amount")?.toInt() ?: 0)
            "redeemed" -> CertificateCheck.AlreadyRedeemed to 0
            "expired" -> CertificateCheck.Expired to 0
            "cancelled" -> CertificateCheck.Cancelled to 0
            "pending" -> CertificateCheck.PendingPayment to 0
            null -> CertificateCheck.Error to 0
            else -> CertificateCheck.NotFound to 0
        }
    }.getOrDefault(CertificateCheck.Error to 0)

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
        // Same contract as iOS LiveNewsService: news.link_type/link_id/pinned
        // (0108), server-clock show window via the on_air computed field (0141).
        // RLS hides items outside their dates from clients; managers' policy
        // returns every row, so the filter is needed for them.
        val raw = api.get("news", listOf(
            "select" to "id,title,body,image_url,link_type,link_id,published_at,created_at,pinned,show_until",
            "is_published" to "eq.true",
            "on_air" to "is.true",
            "order" to "pinned.desc,published_at.desc.nullslast,created_at.desc",
            "limit" to "100",
        ), ttlSec = 300)
        // A cached response can outlive an item's end date: drop those by the device clock too.
        val now = Instant.now()
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            if (newsWindowOver(row.str("show_until"), now)) return@mapNotNull null
            NewsItem(
                id = UUID.fromString(row.str("id")),
                title = row.str("title").orEmpty(),
                body = row.str("body").orEmpty(),
                imageUrl = row.str("image_url"),
                date = displayDay(row.str("published_at") ?: row.str("created_at")),
                link = newsLink(row.str("link_type"), row.str("link_id")),
                isPinned = row.bool("pinned") == true,
            )
        }
    }

    suspend fun fetchReviews(tourId: UUID): List<TourReview> = reviews(
        listOf(
            "select" to "id,rating,text,created_at,images,reply,author:public_profiles(name,avatar_url)",
            "tour_id" to "eq.${tourId}",
            "is_published" to "eq.true",
            "order" to "created_at.desc",
        )
    )

    suspend fun fetchAllReviews(): List<TourReview> = reviews(
        listOf(
            "select" to "id,rating,text,created_at,images,reply,author:public_profiles(name,avatar_url),tours(name)",
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
                author = author?.str("name")?.takeIf { it.isNotBlank() } ?: "Путешественник",
                rating = row.int("rating") ?: 5,
                text = row.str("text").orEmpty(),
                date = displayDay(row.str("created_at")),
                images = row["images"]?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
                reply = row.str("reply")?.trim()?.takeIf { it.isNotEmpty() },
                tourName = row["tours"]?.jsonObjectOrNull()?.str("name"),
                authorAvatarUrl = author?.str("avatar_url"),
            )
        }
    }

    suspend fun fetchDepartures(): List<DepartureItem> {
        val today = Dates.todayUtc().toString()
        val raw = api.get("departures", listOf(
            "select" to "id,label,starts_on,ends_on,spots_left,status,tag,tours(id,name,region,color_start,color_end)",
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
                label = DepartureDates.tagged(DepartureDates.label(start, end), row.str("tag")),
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
                // Built from the dates so the year is there even in an old cache.
                label = DepartureDates.label(
                    Dates.parseDay(row.str("starts_on")), Dates.parseDay(row.str("ends_on")), row.str("label").orEmpty(),
                ),
                startsOn = Dates.parseDay(row.str("starts_on")),
                endsOn = Dates.parseDay(row.str("ends_on")),
                status = row.str("status").orEmpty(),
                role = row.str("role_in_trip") ?: row.str("role").orEmpty(),
                chatId = row.str("chat_id")?.let(UUID::fromString),
                imageUrl = row.str("image_url"),
                imageFocus = ImageFocus.of(row.dbl("image_focus_x"), row.dbl("image_focus_y")),
            )
        }
    }

    /// Tours this user asked to hear about when dates open (0077).
    suspend fun subscribedTourIds(): Set<UUID> = runCatching {
        json.parseToJsonElement(api.rpc("my_tour_subscriptions")).arrayOrEmpty()
            .mapNotNull { it.jsonPrimitiveOrNull()?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() } }
            .toSet()
    }.getOrDefault(emptySet())

    suspend fun setSubscription(tourId: UUID, on: Boolean) {
        val body = buildJsonObject {
            put("p_tour", tourId.toString())
            put("p_on", on)
        }.toString()
        api.rpc("set_tour_subscription", body)
    }

    /// Ended departures this traveller was on. Not cached on purpose: a cached
    /// "you may review" survives the review and offers a form the DB then rejects.
    suspend fun reviewableTrips(): List<ReviewableTrip> {
        val raw = api.rpc("my_reviewable_trips")
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            ReviewableTrip(
                id = row.str("departure_id")?.let(UUID::fromString) ?: return@mapNotNull null,
                tourId = row.str("tour_id")?.let(UUID::fromString) ?: return@mapNotNull null,
                tourName = row.str("tour_name").orEmpty(),
                label = row.str("label").orEmpty(),
                endsOn = row.str("ends_on").orEmpty(),
                reviewed = row.bool("reviewed") == true,
            )
        }
    }

    /// `departure_id` is checked by the insert policy (0069): roster + ended trip.
    suspend fun submitReview(tourId: UUID, departureId: UUID, rating: Int, text: String, images: List<String>) {
        val body = buildJsonObject {
            put("tour_id", tourId.toString())
            put("departure_id", departureId.toString())
            put("rating", rating)
            put("text", text)
            put("images", buildJsonArray { images.forEach { add(it) } })
        }.toString()
        api.post("reviews", body)
    }

    /// Review photos live in their own public bucket (not chat-media, which is private).
    suspend fun uploadReviewImage(bytes: ByteArray): String? = runCatching {
        api.upload("review-media", "${UUID.randomUUID()}.jpg", compressJpeg(bytes), "image/jpeg")
    }.getOrNull()

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

    /// Bonus ledger rows (`my_bonus_lines`); wording stays on the client (BonusCopy).
    suspend fun bonusLines(): List<BonusLine> = runCatching {
        json.parseToJsonElement(api.rpc("my_bonus_lines", ttlSec = 60)).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            BonusLine(
                id = row.str("id")?.let(UUID::fromString) ?: UUID.randomUUID(),
                kind = row.str("kind").orEmpty(),
                amount = row.int("amount") ?: 0,
                remaining = row.int("remaining") ?: 0,
                state = row.str("state").orEmpty(),
                expiresOn = row.str("expires_on"),
                title = row.str("title") ?: row.str("tour_name").orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    private fun stamps(el: kotlinx.serialization.json.JsonElement?): List<Stamp> =
        el?.arrayOrEmpty()?.mapIndexedNotNull { index, item ->
            val row = item.jsonObjectOrNull() ?: return@mapIndexedNotNull null
            Stamp(
                title = row.str("title").orEmpty(),
                year = row.str("year").orEmpty(),
                icon = row.str("icon") ?: "mountain",
                color = Mappers.hex(row.str("color")),
                angle = PassportCopy.stampAngle(index),
            )
        }.orEmpty()

    private fun achievements(el: kotlinx.serialization.json.JsonElement?): List<Achievement> =
        el?.arrayOrEmpty()?.mapNotNull { item ->
            val row = item.jsonObjectOrNull() ?: return@mapNotNull null
            val details = row.str("details").orEmpty()
            Achievement(
                name = row.str("name").orEmpty(),
                details = details,
                icon = row.str("icon") ?: "star",
                earned = row.bool("earned") != false,
                color = Mappers.hex(row.str("color")),
                howTo = details,
                earnedDate = PassportCopy.monthYear(row.str("earned_at")),
            )
        }.orEmpty()

    /// «Мой год с TNA». Null when the year has no finished trips (never a recap of zeroes).
    suspend fun yearRecap(year: Int? = null): YearRecap? = runCatching {
        val raw = api.rpc("my_year_recap", buildJsonObject { put("p_year", year) }.toString(), ttlSec = 300)
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return@runCatching null
        val tours = obj.int("tours") ?: 0
        if (tours <= 0) return@runCatching null
        YearRecap(
            year = (obj.int("year") ?: java.time.LocalDate.now().year).toString(),
            tours = tours,
            km = obj.int("km") ?: 0,
            newAchievements = obj.int("new_achievements") ?: 0,
            topDestination = obj.str("top_destination").orEmpty(),
            topDestinationColors = obj["top_colors"]?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull()?.let(Mappers::hex) }.orEmpty(),
            stamps = stamps(obj["stamps"]),
        )
    }.getOrNull()

    /// Someone else's passport (0079), privacy-filtered by the server.
    suspend fun publicProfile(userId: UUID): PublicProfile? = runCatching {
        val raw = api.rpc("public_profile", buildJsonObject { put("p_user", userId.toString()) }.toString(), ttlSec = 300)
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return@runCatching null
        val stats = obj["stats"]?.jsonObjectOrNull()
        val together = obj["together"]?.jsonObjectOrNull()
        val staff = obj["staff"]?.jsonObjectOrNull()
        PublicProfile(
            name = obj.str("name").orEmpty(),
            city = obj.str("city").orEmpty(),
            role = obj.str("role") ?: "client",
            avatarUrl = obj.str("avatar"),
            memberSinceYear = obj.int("member_since_year"),
            tours = stats?.int("tours") ?: 0,
            countries = stats?.int("countries") ?: 0,
            km = stats?.int("km") ?: 0,
            days = stats?.int("days") ?: 0,
            maxAltitude = stats?.int("max_altitude") ?: 0,
            achievements = achievements(obj["achievements"]),
            stamps = stamps(obj["stamps"]),
            togetherCount = together?.int("count") ?: 0,
            togetherAsGuide = together?.bool("as_guide") == true,
            togetherTours = together?.get("tours")?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
            departuresLed = staff?.int("departures_led"),
            directions = staff?.get("directions")?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
        )
    }.getOrNull()

    suspend fun publicCountries(userId: UUID): Map<String, CountryStatus> = runCatching {
        val raw = api.rpc("user_countries_public", buildJsonObject { put("p_user", userId.toString()) }.toString(), ttlSec = 300)
        countryMap(raw)
    }.getOrDefault(emptyMap())

    suspend fun tnaCodes(userId: UUID): Set<String> = runCatching {
        val raw = api.rpc("tna_country_codes", buildJsonObject { put("p_user", userId.toString()) }.toString(), ttlSec = 300)
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { it.jsonPrimitiveOrNull()?.uppercase() }.toSet()
    }.getOrDefault(emptySet())

    private fun countryMap(raw: String): Map<String, CountryStatus> {
        val map = mutableMapOf<String, CountryStatus>()
        json.parseToJsonElement(raw).arrayOrEmpty().forEach { el ->
            val row = el.jsonObjectOrNull() ?: return@forEach
            val code = row.str("code")?.uppercase() ?: return@forEach
            CountryStatus.fromRaw(row.str("status").orEmpty())?.let { map[code] = it }
        }
        return map
    }

    /// Find-or-create the 1:1 chat with another member; returns its id.
    suspend fun ensureDirectChat(userId: UUID): UUID? = runCatching {
        UUID.fromString(api.rpc("ensure_direct_chat", buildJsonObject { put("p_other", userId.toString()) }.toString()).trim().trim('"'))
    }.getOrNull()

    /// Guide / curator / manager of this trip chat: may pin and create polls.
    suspend fun chatParticipants(chatId: UUID): List<ChatParticipant> = runCatching {
        val raw = api.rpc("chat_participants", buildJsonObject { put("p_chat", chatId.toString()) }.toString())
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val id = row.str("user_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@mapNotNull null
            ChatParticipant(
                id = id,
                name = row.str("name").orEmpty(),
                city = row.str("city").orEmpty(),
                role = row.str("role") ?: "client",
                isAdmin = row.bool("is_admin") == true,
                muted = row.bool("muted") == true,
                avatarUrl = row.str("avatar"),
            )
        }
    }.getOrDefault(emptyList())

    suspend fun setMemberMuted(chatId: UUID, userId: UUID, muted: Boolean) {
        runCatching {
            api.rpc("set_chat_member_muted", buildJsonObject {
                put("p_chat", chatId.toString()); put("p_user", userId.toString()); put("p_muted", muted)
            }.toString())
        }
    }

    /// Team (guide, curator, manager, admin) may remove travellers from this trip chat (0157).
    suspend fun canBlockInChat(chatId: UUID): Boolean = runCatching {
        api.rpc("can_block_in_chat", buildJsonObject { put("p_chat", chatId.toString()) }.toString()).trim() == "true"
    }.getOrDefault(false)

    /// People the team removed from this chat; empty unless the caller may block here.
    suspend fun blockedMembers(chatId: UUID): List<ChatParticipant> = runCatching {
        val raw = api.rpc("chat_blocked_members", buildJsonObject { put("p_chat", chatId.toString()) }.toString())
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val id = row.str("user_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@mapNotNull null
            ChatParticipant(
                id = id,
                name = row.str("name").orEmpty(),
                city = row.str("city").orEmpty(),
                role = row.str("role") ?: "client",
                isAdmin = false,
                muted = false,
                avatarUrl = row.str("avatar"),
                isBlocked = true,
                blockedBy = row.str("blocked_by").orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    /// Block (`true`) or bring back (`false`) a traveller. `false` on failure.
    suspend fun setMemberBlocked(chatId: UUID, userId: UUID, blocked: Boolean): Boolean = runCatching {
        api.rpc("set_chat_member_blocked", buildJsonObject {
            put("p_chat", chatId.toString()); put("p_user", userId.toString()); put("p_blocked", blocked)
        }.toString()).trim() == "true"
    }.getOrDefault(false)

    /// Short privacy-safe card of another user (header of a 1:1 chat).
    suspend fun miniProfile(userId: UUID): MiniProfile? = runCatching {
        val obj = json.parseToJsonElement(
            api.rpc("chat_mini_profile", buildJsonObject { put("p_user", userId.toString()) }.toString()),
        ).jsonObjectOrNull() ?: return@runCatching null
        MiniProfile(userId, obj.str("name").orEmpty(), obj.str("city").orEmpty(), obj.str("role") ?: "client", obj.str("avatar"))
    }.getOrNull()

    // Moderation: report a message, block an author (their messages disappear for the caller).
    suspend fun reportMessage(messageId: UUID) {
        runCatching {
            api.post("message_reports", buildJsonObject { put("message_id", messageId.toString()) }.toString(), prefer = "resolution=ignore-duplicates")
        }
    }

    suspend fun blockUser(userId: UUID) {
        runCatching {
            api.post("user_blocks", buildJsonObject { put("blocked_id", userId.toString()) }.toString(), prefer = "resolution=ignore-duplicates")
        }
    }

    suspend fun blockedIds(): Set<UUID> = runCatching {
        json.parseToJsonElement(api.get("user_blocks", listOf("select" to "blocked_id"), ttlSec = null)).arrayOrEmpty()
            .mapNotNull { it.jsonObjectOrNull()?.str("blocked_id")?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() } }.toSet()
    }.getOrDefault(emptySet())

    suspend fun isChatStaff(chatId: UUID): Boolean = runCatching {
        api.rpc("is_chat_staff", buildJsonObject { put("p_chat", chatId.toString()) }.toString()).trim() == "true"
    }.getOrDefault(false)

    suspend fun setPinned(messageId: UUID, pinned: Boolean) {
        runCatching {
            api.rpc("set_message_pinned", buildJsonObject {
                put("p_message", messageId.toString())
                put("p_pinned", pinned)
            }.toString())
        }
    }

    /// Every pinned message of the chat, newest pin first, independent of the history window.
    suspend fun fetchPinned(chatId: UUID): List<Message> = runCatching {
        val me = auth.currentUserId()?.toString()
        val raw = api.get("messages", listOf(
            "select" to "id,text,kind,media_url,media_name,duration_ms,edited_at,reply_to,album_id,pinned_at,poll,location,as_company,created_at,user_id," +
                "author:public_profiles!user_id(name,avatar_url),message_reactions(emoji,user_id)",
            "chat_id" to "eq.$chatId",
            "pinned_at" to "not.is.null",
            "order" to "pinned_at.desc",
        ), ttlSec = null)
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { message(it.jsonObjectOrNull(), me) }
    }.getOrDefault(emptyList())

    suspend fun createPoll(chatId: UUID, poll: PollData): UUID {
        val id = UUID.randomUUID()
        api.post("messages", buildJsonObject {
            put("id", id.toString())
            put("chat_id", chatId.toString())
            put("text", poll.question)
            put("kind", "poll")
            put("poll", buildJsonObject {
                put("question", poll.question)
                put("description", poll.description)
                put("options", buildJsonArray {
                    poll.options.forEach { o -> add(buildJsonObject { put("id", o.id); put("text", o.text) }) }
                })
                put("multiple", poll.multiple)
                put("anonymous", poll.anonymous)
                put("add_options", poll.addOptions)
                put("revote", poll.revote)
                put("quiz", poll.quiz)
                put("correct", buildJsonArray { poll.correct.forEach { add(JsonPrimitive(it)) } })
                put("shuffle", poll.shuffle)
                put("closes_at", poll.closesAt)
                put("closed", false)
                put("rev", 0)
            })
        }.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        runCatching { api.function("notify-message", buildJsonObject { put("message_id", id.toString()) }.toString()) }
        return id
    }

    // MARK: location (0158)

    /// Posts a one-off point or a live location (online only, like polls).
    /// The server stamps updated_at/stopped and caps the live period at 8 h.
    suspend fun sendLocation(chatId: UUID, location: LocationData): UUID {
        val id = UUID.randomUUID()
        api.post("messages", buildJsonObject {
            put("id", id.toString())
            put("chat_id", chatId.toString())
            put("text", LocationText.label(location))
            put("kind", "location")
            put("location", buildJsonObject {
                put("lat", location.lat)
                put("lng", location.lng)
                location.accuracy?.let { put("accuracy", it) }
                location.liveUntil?.let { put("live_until", it.toString()) }
            })
        }.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        runCatching { api.function("notify-message", buildJsonObject { put("message_id", id.toString()) }.toString()) }
        return id
    }

    /// Moves a live point. true = accepted, false = the sharing is over on the
    /// server (stop tracking), null = no answer (keep sharing, retry next fix).
    suspend fun updateLiveLocation(messageId: UUID, point: GeoPoint): Boolean? = runCatching {
        api.rpc("update_live_location", buildJsonObject {
            put("p_message", messageId.toString())
            put("p_lat", point.lat)
            put("p_lng", point.lng)
            point.accuracy?.let { put("p_accuracy", it) }
        }.toString()).trim() == "true"
    }.getOrNull()

    suspend fun stopLiveLocation(messageId: UUID) {
        runCatching { api.rpc("stop_live_location", buildJsonObject { put("p_message", messageId.toString()) }.toString()) }
    }

    suspend fun votePoll(messageId: UUID, options: List<String>): Boolean = runCatching {
        api.rpc("vote_poll", buildJsonObject {
            put("p_message", messageId.toString())
            put("p_options", buildJsonArray { options.forEach { add(JsonPrimitive(it)) } })
        }.toString()).trim() == "true"
    }.getOrDefault(false)

    suspend fun addPollOption(messageId: UUID, text: String) {
        runCatching {
            api.rpc("add_poll_option", buildJsonObject { put("p_message", messageId.toString()); put("p_text", text.trim()) }.toString())
        }
    }

    suspend fun closePoll(messageId: UUID) {
        runCatching { api.rpc("close_poll", buildJsonObject { put("p_message", messageId.toString()) }.toString()) }
    }

    suspend fun pollState(messageId: UUID): PollState? = runCatching {
        val raw = api.rpc("poll_state", buildJsonObject { put("p_message", messageId.toString()) }.toString())
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return@runCatching null
        PollState(
            total = obj.int("total") ?: 0,
            closed = obj.bool("closed") == true,
            my = obj["my"]?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
            options = obj["options"]?.arrayOrEmpty()?.mapNotNull { el ->
                val o = el.jsonObjectOrNull() ?: return@mapNotNull null
                PollState.Opt(
                    id = o.str("id") ?: return@mapNotNull null,
                    count = o.int("count") ?: 0,
                    voters = o["voters"]?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
                )
            }.orEmpty(),
        )
    }.getOrNull()

    /// Voice note or file into chat-media, then the message row (iOS `sendAttachment`).
    suspend fun sendAttachment(
        chatId: UUID,
        bytes: ByteArray,
        kind: MessageKind,
        ext: String,
        contentType: String,
        name: String? = null,
        durationMs: Int? = null,
        messageId: UUID = UUID.randomUUID(),
    ) {
        val path = "${chatId.toString().lowercase()}/${messageId.toString().lowercase()}.$ext"
        val url = api.upload("chat-media", path, bytes, contentType)
        api.post("messages", buildJsonObject {
            put("id", messageId.toString())
            put("chat_id", chatId.toString())
            put("text", "")
            put("kind", kind.name.lowercase())
            put("media_url", url)
            put("media_name", name)
            put("duration_ms", durationMs)
        }.toString(), prefer = "resolution=ignore-duplicates,return=minimal")
        runCatching { api.function("notify-message", buildJsonObject { put("message_id", messageId.toString()) }.toString()) }
    }

    /// Offline trip pack (`trip_pack`): cached 10 min, stale copy served when offline.
    suspend fun tripPack(): TripPlan? = runCatching {
        val raw = api.rpc("trip_pack", ttlSec = 600)
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return@runCatching null
        val start = Dates.parseDay(obj.str("starts_on"))
        val end = Dates.parseDay(obj.str("ends_on"))
        val itinerary = obj["itinerary"]?.arrayOrEmpty()?.mapNotNull { el ->
            val d = el.jsonObjectOrNull() ?: return@mapNotNull null
            (d.int("day") ?: return@mapNotNull null) to d.str("title").orEmpty()
        }.orEmpty()
        val dayIndex = obj.int("day_index")
        TripPlan(
            tourName = obj.str("tour_name").orEmpty(),
            dateRange = DepartureDates.label(start, end, obj.str("label").orEmpty()),
            statusLine = TripPackMapper.statusLine(
                phase = obj.str("phase") ?: "upcoming",
                dayIndex = dayIndex,
                totalDays = obj.int("total_days") ?: itinerary.size,
                todayTitle = itinerary.firstOrNull { it.first == dayIndex }?.second,
                start = start,
            ),
            colors = listOf(Mappers.hex(obj.str("color_start") ?: "FF6B3D"), Mappers.hex(obj.str("color_end") ?: "8B1E00")),
            schedule = itinerary.map { (day, title) -> TripScheduleItem("День $day", title, TripPackMapper.icon(title)) },
            contacts = obj["contacts"]?.arrayOrEmpty()?.mapIndexedNotNull { index, el ->
                val c = el.jsonObjectOrNull() ?: return@mapIndexedNotNull null
                TripContact(UUID.randomUUID(), c.str("role").orEmpty(), c.str("phone").orEmpty(), index)
            }.orEmpty(),
            dayIndex = dayIndex,
        )
    }.getOrNull()

    suspend fun fetchPassport(displayName: String): PassportData {
        val raw = api.rpc("my_passport", ttlSec = 300)
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: throw TnaException("Паспорт не загрузился")
        val stats = obj["stats"]?.jsonObjectOrNull()
        val tours = stats?.int("tours") ?: 0
        val level = TravelerLevel.forTours(tours)
        val stamps = stamps(obj["stamps"])
        val achievements = achievements(obj["achievements"])
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
                name = PassportCopy.shortName(row.str("name") ?: "-", mine),
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
            "select" to "id,kind,title,unread_from_others,messages(text,created_at,user_id),$membership(last_read_at,muted,archived),members:chat_members(user_id,profiles:public_profiles!user_id(name,avatar_url)),departures(tours(symbol,color_start,color_end,image_url))",
            "messages.order" to "created_at.desc",
            "messages.limit" to "1",
            "order" to "created_at.desc",
        )
        if (me != null) query += "chat_members.user_id" to "eq.$me"
        val raw = api.get("chats", query, ttlSec = null)
        return json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            thread(el.jsonObjectOrNull(), me)
        }.filter { it.belongsInChatList }
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
            "select" to "id,text,kind,media_url,media_name,duration_ms,edited_at,reply_to,album_id,pinned_at,poll,location,as_company,created_at,user_id," +
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

    suspend fun sendText(chatId: UUID, text: String, messageId: UUID = UUID.randomUUID(), replyTo: UUID? = null) {
        api.post("messages", buildJsonObject {
            put("id", messageId.toString())
            put("chat_id", chatId.toString())
            put("text", text)
            put("kind", "text")
            replyTo?.let { put("reply_to", it.toString()) }
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


    suspend fun editMessage(id: UUID, text: String) {
        api.patch("messages", listOf("id" to "eq.${id.toString().lowercase()}"), buildJsonObject {
            put("text", text)
            put("edited_at", Instant.now().toString())
        }.toString())
    }

    suspend fun deleteMessage(id: UUID) {
        api.delete("messages", listOf("id" to "eq.${id.toString().lowercase()}"))
    }

    suspend fun setChatMuted(chatId: UUID, muted: Boolean) {
        api.rpc("set_chat_muted", buildJsonObject { put("p_chat", chatId.toString()); put("p_val", muted) }.toString())
    }

    suspend fun setChatArchived(chatId: UUID, archived: Boolean) {
        api.rpc("set_chat_archived", buildJsonObject { put("p_chat", chatId.toString()); put("p_val", archived) }.toString())
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
                "location" -> MessageKind.Location
                else -> MessageKind.Text
            },
            mediaUrl = row.str("media_url"),
            mediaName = row.str("media_name"),
            durationMs = row.int("duration_ms"),
            edited = row.str("edited_at") != null,
            reactions = reactions(row["message_reactions"]?.arrayOrEmpty(), me),
            createdAt = parseInstant(created) ?: Instant.now(),
            poll = poll(row["poll"]?.jsonObjectOrNull()),
            location = location(row["location"]?.jsonObjectOrNull()),
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
            email = obj.str("email").orEmpty(),
            emailVerified = obj.bool("email_verified") == true,
            doc = identityDoc(obj),
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
            putDoc(draft.doc)
        }.toString())
    }

    /// Display name: RPC mirrors it into customers.name; if the RPC is unavailable, write
    /// profiles.name directly (that is what chats, rating and roster read), like iOS.
    suspend fun setName(name: String) {
        try {
            api.rpc("set_my_name", buildJsonObject { put("p_name", name) }.toString())
        } catch (e: Exception) {
            val uid = auth.currentUserId() ?: throw e
            api.patch("profiles", listOf("id" to "eq.${uid.toString().lowercase()}"), buildJsonObject {
                put("name", name)
            }.toString())
        }
    }

    /// One path per user, overwritten; `?v=` busts the CDN and image caches.
    suspend fun uploadAvatar(bytes: ByteArray): String? = runCatching {
        val uid = auth.currentUserId() ?: return@runCatching null
        val url = api.upload("avatars", "${uid.toString().lowercase()}/avatar.jpg", compressJpeg(bytes, 512), "image/jpeg")
        "$url?v=${System.currentTimeMillis() / 1000}"
    }.getOrNull()

    suspend fun setAvatarUrl(url: String?): Boolean = runCatching {
        val uid = auth.currentUserId() ?: return@runCatching false
        api.patch("profiles", listOf("id" to "eq.${uid.toString().lowercase()}"), buildJsonObject {
            put("avatar_url", url)
        }.toString())
        true
    }.getOrDefault(false)

    suspend fun currentAvatarUrl(): String? = runCatching {
        val uid = auth.currentUserId() ?: return@runCatching null
        val raw = api.get("profiles", listOf("select" to "avatar_url", "id" to "eq.${uid.toString().lowercase()}"), ttlSec = 3600)
        json.parseToJsonElement(raw).arrayOrEmpty().firstOrNull()?.jsonObjectOrNull()?.str("avatar_url")
    }.getOrNull()

    /// Login numbers through the login-phones edge function (TNACloud 0153).
    /// Refusals come back with a ready Russian sentence and are shown as is.
    val loginPhones: LoginPhonesBackend = object : LoginPhonesBackend {
        override suspend fun list(): List<LoginPhone>? =
            LoginPhonesWire.list(api.functionBody("login-phones", buildJsonObject { put("action", "list") }.toString()))

        override suspend fun sendCode(phone: String): LoginPhoneOutcome = call("send") { put("phone", phone) }

        override suspend fun confirm(code: String): LoginPhoneOutcome = call("confirm") { put("code", code) }

        override suspend fun remove(phone: String): LoginPhoneOutcome = call("remove") { put("phone", phone) }

        private suspend fun call(action: String, fill: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): LoginPhoneOutcome =
            LoginPhonesWire.outcome(api.functionBody("login-phones", buildJsonObject {
                put("action", action)
                fill()
            }.toString()))
    }

    /// Email is verified by a code (verify-email edge function), never saved directly.
    suspend fun requestEmailCode(email: String): EmailVerifyResult = verifyEmail(buildJsonObject {
        put("action", "request")
        put("email", email.trim())
    }.toString())

    suspend fun confirmEmailCode(code: String): EmailVerifyResult = verifyEmail(buildJsonObject {
        put("action", "confirm")
        put("code", code.trim())
    }.toString())

    private suspend fun verifyEmail(body: String): EmailVerifyResult = runCatching {
        val obj = json.parseToJsonElement(api.function("verify-email", body)).jsonObjectOrNull()
        EmailVerifyResult.from(obj?.bool("ok"), obj?.str("error"))
    }.getOrDefault(EmailVerifyResult.Error)

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
            "select" to "id,full_name,last_name,first_name,patronymic,birthday,phone,doc_type,doc_number,doc_citizenship,doc_issued,doc_expiry,doc_issued_by,doc_registration",
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
                phone = row.str("phone").orEmpty(),
                doc = identityDoc(row),
            )
        }
    }

    private fun identityDoc(row: JsonObject) = IdentityDoc(
        type = row.str("doc_type").orEmpty(),
        number = row.str("doc_number").orEmpty(),
        citizenship = row.str("doc_citizenship").orEmpty(),
        issued = row.str("doc_issued"),
        expiry = row.str("doc_expiry"),
        issuedBy = row.str("doc_issued_by").orEmpty(),
        registration = row.str("doc_registration").orEmpty(),
    )

    /// Document columns as iOS writes them: domestic-only fields are cleared for other
    /// kinds, expiry only for a foreign passport, citizenship defaults to Russia.
    private fun kotlinx.serialization.json.JsonObjectBuilder.putDoc(doc: IdentityDoc) {
        val kind = doc.kind
        put("doc_type", doc.type.trim().ifBlank { null })
        put("doc_number", doc.number.trim().ifBlank { null })
        put("doc_citizenship", doc.citizenship.trim().ifBlank { Citizenship.defaultValue })
        put("doc_issued", doc.issued?.trim()?.ifBlank { null })
        put("doc_expiry", if (kind?.hasExpiry == true) doc.expiry?.trim()?.ifBlank { null } else null)
        put("doc_issued_by", if (kind?.hasInternalFields == true) doc.issuedBy.trim().ifBlank { null } else null)
        put("doc_registration", if (kind?.hasInternalFields == true) doc.registration.trim().ifBlank { null } else null)
    }

    suspend fun saveCompanion(person: TravelCompanion) {
        api.post("travel_companions", buildJsonObject {
            put("id", person.id.toString())
            put("full_name", person.fullName)
            put("last_name", person.lastName.trim().ifBlank { null })
            put("first_name", person.firstName.trim().ifBlank { null })
            put("patronymic", person.patronymic.trim().ifBlank { null })
            put("birthday", person.birthday?.trim()?.ifBlank { null })
            put("phone", person.phone.trim().ifBlank { null })
            putDoc(person.doc)
        }.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun deleteCompanion(id: UUID) {
        api.delete("travel_companions", listOf("id" to "eq.$id"))
    }

    suspend fun buyCertificate(amount: Int, recipient: String, wish: String, buyerName: String, buyerPhone: String): GiftCertificate? {
        val raw = api.function("buy-certificate", buildJsonObject {
            put("action", "create")
            put("amount", amount)
            put("recipient_name", recipient.trim())
            put("wish", wish.trim())
            put("buyer_name", buyerName.trim())
            put("buyer_phone", buyerPhone.trim())
        }.toString())
        val obj = json.parseToJsonElement(raw).jsonObjectOrNull() ?: return null
        val cert = obj["certificate"]?.jsonObjectOrNull()?.let { certificate(it) } ?: return null
        return cert.copy(paymentUrl = obj.str("payment_url") ?: cert.paymentUrl)
    }

    suspend fun certificateStatus(id: UUID): GiftCertificate? = runCatching {
        val raw = api.function("buy-certificate", buildJsonObject {
            put("action", "status")
            put("id", id.toString())
        }.toString())
        json.parseToJsonElement(raw).jsonObjectOrNull()?.get("certificate")?.jsonObjectOrNull()?.let { certificate(it) }
    }.getOrNull()

    suspend fun myCertificates(): List<GiftCertificate> = runCatching {
        val raw = api.get("gift_certificates", listOf(
            "select" to "id,code,pin,amount,status,recipient_name,wish,expires_on,payment_url",
            "order" to "created_at.desc",
        ), ttlSec = null)
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { it.jsonObjectOrNull()?.let(::certificate) }
    }.getOrDefault(emptyList())

    private fun certificate(row: JsonObject): GiftCertificate? {
        val id = row.str("id")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return GiftCertificate(
            id = id,
            code = row.str("code"),
            amount = row.dbl("amount")?.toInt() ?: 0,
            recipientName = row.str("recipient_name").orEmpty(),
            wish = row.str("wish").orEmpty(),
            status = row.str("status").orEmpty().ifBlank { "pending" },
            paymentUrl = row.str("payment_url"),
            pin = row.str("pin").orEmpty(),
            expiresOn = row.str("expires_on"),
        )
    }

    /// Emergency contacts of a departure; cached and stale-if-error so they work offline.
    suspend fun departureContacts(departureId: UUID): List<TripContact> = runCatching {
        val raw = api.get("departure_contacts", listOf(
            "select" to "id,name,phone,sort",
            "departure_id" to "eq.${departureId.toString().lowercase()}",
            "order" to "sort.asc",
        ), ttlSec = 3600)
        json.parseToJsonElement(raw).arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            TripContact(
                id = row.str("id")?.let(UUID::fromString) ?: UUID.randomUUID(),
                name = row.str("name").orEmpty(),
                phone = row.str("phone").orEmpty(),
                sort = row.int("sort") ?: 0,
            )
        }
    }.getOrDefault(emptyList())

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

    private fun location(row: JsonObject?): LocationData? {
        row ?: return null
        val lat = row.dbl("lat") ?: return null
        val lng = row.dbl("lng") ?: return null
        return LocationData(
            lat = lat,
            lng = lng,
            accuracy = row.dbl("accuracy"),
            liveUntil = parseInstant(row.str("live_until")),
            updatedAt = parseInstant(row.str("updated_at")),
            stopped = row.bool("stopped") == true,
        )
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
            correct = row["correct"]?.arrayOrEmpty()?.mapNotNull { it.jsonPrimitiveOrNull() }.orEmpty(),
            shuffle = row.bool("shuffle") == true,
            closesAt = row.str("closes_at"),
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

    private fun compressJpeg(bytes: ByteArray, max: Int = 1600): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
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

/// Scannable multi-line summary for the support / amo chat (iOS `supportText`).
fun supportText(r: BookingRequest): String {
    val lines = mutableListOf("📝 ${r.headline}", "")
    if (r.details.isEmpty()) {
        lines += r.subject.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    } else {
        lines += r.details
    }
    if (r.showsPeople) lines += "Человек: ${r.peopleCount}"
    lines += "Контакт: ${r.name}"
    lines += "Тел: ${PhoneNumber.pretty(r.phone)}"
    if (r.companions.isNotEmpty()) {
        lines += ""
        lines += CompanionFormat.message(r.companions)
    }
    return lines.joinToString("\n")
}

/// «Кто едет» block for the manager (iOS `CompanionFormat`), with the fields Android stores.
object CompanionFormat {
    fun message(list: List<TravelCompanion>): String {
        if (list.isEmpty()) return ""
        val header = "👥 Кто едет (${list.size})"
        val blocks = list.mapIndexed { i, c -> block(i + 1, c) }
        return (listOf(header) + blocks).joinToString("\n--------\n")
    }

    /// One person: name, then ДР → тел → гражданство → документ → выдан / кем / прописка → до.
    fun block(index: Int, c: TravelCompanion): String {
        val lines = mutableListOf("$index. ${c.fullName.trim().ifEmpty { "Без имени" }}")
        fun row(label: String, value: String?) {
            val v = value?.trim().orEmpty()
            if (v.isNotEmpty()) lines += "$label: $v"
        }
        c.birthday?.let { row("ДР", dateStr(it)) }
        if (c.phone.isNotBlank()) row("Тел", PhoneNumber.pretty(c.phone))
        row("Гражд", c.doc.citizenship)
        c.doc.kind?.let { kind ->
            val num = c.doc.number.trim()
            row(docLabel(kind), num.ifEmpty { kind.title })
            c.doc.issued?.let { row("Выдан", dateStr(it)) }
            if (kind.hasInternalFields) {
                row("Кем", c.doc.issuedBy)
                row("Прописка", c.doc.registration)
            }
            if (kind.hasExpiry) c.doc.expiry?.let { row("До", dateStr(it)) }
        }
        return lines.joinToString("\n")
    }

    /// One-line summary for the picker row.
    fun line(c: TravelCompanion): String {
        val parts = mutableListOf(c.fullName.trim().ifEmpty { "Без имени" })
        c.birthday?.let { parts += "р. " + dateStr(it) }
        if (c.phone.isNotBlank()) parts += PhoneNumber.pretty(c.phone)
        val kind = c.doc.kind
        if (kind != null && c.doc.number.isNotBlank()) parts += "${docLabel(kind)} ${c.doc.number.trim()}"
        return parts.joinToString(" · ")
    }

    private fun docLabel(kind: DocKind) = when (kind) {
        DocKind.PassportRF -> "Паспорт РФ"
        DocKind.PassportIntl -> "Загран"
        DocKind.BirthCert -> "Свид. о рожд."
    }

    private fun dateStr(iso: String): String =
        Dates.parseDay(iso)?.let { "%02d.%02d.%04d".format(it.dayOfMonth, it.monthValue, it.year) } ?: iso
}
