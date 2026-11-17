package com.travelneveralone.tna

import kotlinx.coroutines.withContext
import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.UUID

enum class AppTab(val title: String, val analytics: String) {
    Tours("Туры", "Catalog"),
    Chats("Чаты", "Chats"),
    Passport("Паспорт", "Passport"),
    Profile("Профиль", "Profile"),
}

sealed interface Sheet {
    data class Booking(val tour: Tour, val departure: Departure?) : Sheet
    data object Login : Sheet
    data object News : Sheet
    data object MyBookings : Sheet
    data object Terms : Sheet
    data object Reviews : Sheet
    data object Contacts : Sheet
    data object About : Sheet
    data object Individual : Sheet
    data object Certificate : Sheet
    data object Notifications : Sheet
    data object Companions : Sheet
    data object Countries : Sheet
    data object Compass : Sheet
    data object Schedule : Sheet
    data class TripMode(val trip: Trip) : Sheet
    data class Program(val tour: Tour, val trip: Trip? = null) : Sheet
    data object EditProfile : Sheet
}

class AppModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tna.ui", Context.MODE_PRIVATE)
    val network = NetworkMonitor(app)
    val account = AccountStore(app)
    private val auth = AuthSession(app, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    private val api = Postgrest(
        baseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        token = { auth.token() },
        cache = DiskCache(app.cacheDir),
        online = { network.isOnline },
    )
    val repo = TnaRepository(api, auth)
    /// Optional biometric lock over the signed-in session (iOS `AppLock`).
    val biometrics = AndroidBiometrics(app)
    val appLock = AppLock(PrefsFlagStore(app, "tna.lock"), biometrics, isRegistered = { account.isRegistered })
    /// One-time «Входить по отпечатку?» after a phone sign-in.
    var lockOfferShown by mutableStateOf(false)
    /// A sign-out with the biometric lock on left the account on this device:
    /// the login sheet offers biometrics instead of an SMS code.
    var canQuickSignIn by mutableStateOf(auth.hasQuickSignIn && !account.isRegistered)
        private set
    /// The user chose the phone flow over biometrics on the login sheet.
    var quickDismissed by mutableStateOf(false)
        private set
    /// The login sheet opens on the biometric step while this holds. A phone
    /// already typed for another number (a booking) goes by SMS instead.
    val showsQuickSignIn: Boolean
        get() = canQuickSignIn && !quickDismissed && appLock.canConfirmQuickSignIn &&
            (loginNational.isEmpty() || account.quickPhone.isEmpty() ||
                PhoneNumber.digits(loginPhone) == PhoneNumber.digits(account.quickPhone))
    val isProd = BuildConfig.IS_PROD

    var showSplash by mutableStateOf(true)
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
    var tab by mutableStateOf(AppTab.Tours)
    var role by mutableStateOf(UserRole.Client)
    var dmRoles by mutableStateOf<List<UserRole>?>(null)
    var startupStalled by mutableStateOf(false)

    var tours by mutableStateOf<List<Tour>>(emptyList())
    var campaign by mutableStateOf<CampaignOffer?>(null)
    var catalogState by mutableStateOf("loading")
    var catalogError by mutableStateOf<String?>(null)
    var filter by mutableStateOf(CatalogFilter.All)
    var search by mutableStateOf("")

    var openTour by mutableStateOf<Tour?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var openChat by mutableStateOf<ChatThread?>(null)
    var threads by mutableStateOf<List<ChatThread>>(emptyList())
    var messages by mutableStateOf<List<Message>>(emptyList())
    var passport by mutableStateOf(PassportData.empty)
    var bookings by mutableStateOf<List<BookingSummary>>(emptyList())
    var news by mutableStateOf<List<NewsItem>>(emptyList())
    var reviews by mutableStateOf<List<TourReview>>(emptyList())
    var schedule by mutableStateOf<List<DepartureItem>>(emptyList())
    var trips by mutableStateOf<List<Trip>>(emptyList())
    var referralCode by mutableStateOf("")
    var bonus by mutableStateOf<BonusSummary?>(null)
    var bonusLines by mutableStateOf<List<BonusLine>>(emptyList())
    var referralNote by mutableStateOf<String?>(null)
    var countries by mutableStateOf<Map<String, CountryStatus>>(emptyMap())
    var tnaCountryCodes by mutableStateOf<Set<String>>(emptySet())
    var companions by mutableStateOf<List<TravelCompanion>>(emptyList())
    var prefsState by mutableStateOf(NotificationPrefs())
    var profile by mutableStateOf(ProfileDraft())
    var avatarUrl by mutableStateOf<String?>(null)
    var avatarBusy by mutableStateOf(false)
    var chatDraft by mutableStateOf("")
    var reactionFor by mutableStateOf<UUID?>(null)
    /** Login phone: country chip + national digits (see PhoneField). */
    var loginCountry by mutableStateOf(PhoneCountry.preferred(deviceRegion()))
    var loginNational by mutableStateOf("")
    /** "+79161234567". Setting it splits any typed or stored number into country + national. */
    var loginPhone: String
        get() = PhoneInput.e164(loginNational, loginCountry)
        set(value) {
            val (country, national) = PhoneInput.parse(value, loginCountry)
            loginCountry = country
            loginNational = national
        }
    var loginCode by mutableStateOf("")
    var loginStep by mutableStateOf(0)
    var loginError by mutableStateOf<String?>(null)
    var loginBusy by mutableStateOf(false)
    var form by mutableStateOf<BookingForm?>(null)

    val visibleTabs: List<AppTab>
        get() = if (role.canBookAndEarn) AppTab.entries else AppTab.entries.filter { it != AppTab.Passport }

    val chatBadge: Int get() = threads.filter { !it.archived }.sumOf { it.unread }
    val filteredTours: List<Tour> get() = filterTours(tours, filter, search)
    val policy: DirectMessagePolicy get() = DirectMessagePolicy(dmRoles)

    fun finishOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun finishSplash() { showSplash = false }

    fun boot() {
        // Nothing on the network holds the launch: the catalog starts right away, in
        // parallel with the session check, and serves the cache after 5 s (iOS rule).
        loadCatalog()
        viewModelScope.launch {
            val reachable = runCatching { auth.token() != null && network.isOnline }.getOrDefault(false)
            if (reachable) {
                val anon = auth.sessionIsAnonymous()
                if (anon == false) {
                    account.isRegistered = true
                    auth.currentPhone()?.let { account.phone = PhoneNumber.pretty(it) }
                    loadProfileBits()
                }
            } else startupStalled = true
            loadRole()
            loadChats()
        }
    }

    // MARK: carousel clips

    private val videoCache = TourVideoCache(java.io.File(app.cacheDir, "TourVideos"))
    /// Remote clip URL → local file; only cards with a file get a moving cover.
    var videoFiles by mutableStateOf<Map<String, java.io.File>>(emptyMap())
        private set
    val featuredTours: List<Tour> get() = tours.take(4)

    /// Cached clips show right away; new ones wait for a non-cellular network.
    fun prepareVideos() = viewModelScope.launch {
        if (AppConfig.useMock) return@launch
        val live = tours.mapNotNull { it.videoUrl }.toSet()
        withContext(kotlinx.coroutines.Dispatchers.IO) { videoCache.prune(live) }
        videoFiles = videoFiles.filterKeys { it in live }
        val wanted = featuredTours.mapNotNull { it.videoUrl }
        for (remote in wanted) {
            if (videoFiles[remote] == null) videoCache.cachedFile(remote)?.let { videoFiles = videoFiles + (remote to it) }
        }
        if (!network.isOnline || network.isConstrained) return@launch
        for (remote in wanted) {
            if (videoFiles[remote] == null) videoCache.download(remote)?.let { videoFiles = videoFiles + (remote to it) }
        }
    }

    fun loadCatalog() = viewModelScope.launch {
        if (tours.isEmpty()) catalogState = "loading"
        if (AppConfig.useMock) {
            tours = Mock.tours
            catalogState = "loaded"
            return@launch
        }
        runCatching { repo.fetchTours() }
            .onSuccess { loaded ->
                val mutable = loaded.toMutableList()
                val offer = runCatching { repo.fetchCampaign() }.getOrNull()
                CampaignApplication.apply(offer, mutable)
                tours = mutable
                campaign = offer
                if (!CatalogFilter.available(mutable).contains(filter)) filter = CatalogFilter.All
                catalogState = "loaded"
            }
            .onFailure { error ->
                if (tours.isNotEmpty()) catalogState = "loaded"
                else {
                    catalogState = "failed"
                    catalogError = if (!network.isOnline) "Нет сети, а сохранённого каталога пока нет" else error.message
                }
            }
    }

    fun loadChats() = viewModelScope.launch {
        if (AppConfig.useMock) {
            if (threads.isEmpty()) threads = Mock.threads
            return@launch
        }
        // A failed load keeps what is on screen (the disk cache already served what it
        // had). The support row is drawn by the screen even when the list is empty.
        runCatching { repo.fetchThreads() }.onSuccess { threads = it }
    }

    fun openThread(thread: ChatThread) {
        // Writing to support is an action, so it needs an account (as on iOS).
        if (thread.isSupport && !account.isRegistered && !AppConfig.useMock) {
            requireAccount { openThread(thread) }
            return
        }
        openChat = thread
        messages = emptyList()
        viewModelScope.launch {
            // Never read or write the placeholder: resolve the real support thread first.
            val live = resolveThread(thread)
            if (live.id == ChatThread.supportPlaceholder.id) return@launch
            loadChatExtras(live)
            messages = runCatching { repo.history(live.id) }.getOrElse {
                if (AppConfig.useMock && live.isSupport) Mock.managerChat else emptyList()
            }
            runCatching { repo.markRead(live.id) }
        }
    }

    var replyingTo by mutableStateOf<Message?>(null)
    var editingMessage by mutableStateOf<Message?>(null)

    fun startReply(message: Message) {
        editingMessage = null
        replyingTo = message
    }

    fun startEditing(message: Message) {
        replyingTo = null
        editingMessage = message
        chatDraft = message.text
    }

    fun cancelCompose() {
        if (editingMessage != null) chatDraft = ""
        replyingTo = null
        editingMessage = null
    }

    fun sendChat() {
        val thread = openChat ?: return
        val text = chatDraft.trim()
        if (text.isEmpty()) return
        chatDraft = ""
        editingMessage?.let { target ->
            editingMessage = null
            val before = messages.firstOrNull { it.id == target.id }
            messages = messages.map { if (it.id == target.id) it.copy(text = text, edited = true) else it }
            viewModelScope.launch {
                runCatching { repo.editMessage(target.id, text) }.onFailure {
                    // Roll back so the bubble never shows text the server does not have.
                    if (before != null) messages = messages.map { m -> if (m.id == target.id) before else m }
                    chatNotice = "Не удалось изменить сообщение"
                }
            }
            return
        }
        val reply = replyingTo
        replyingTo = null
        val local = Message(
            text = text, isMine = true, author = account.name.ifBlank { "Я" }, time = displayTime(java.time.Instant.now().toString()),
            status = MessageStatus.Queued, replyTo = reply?.id,
        )
        messages = messages + local
        deliver(thread, local)
    }

    private fun deliver(thread: ChatThread, local: Message) {
        viewModelScope.launch {
            val live = resolveThread(thread)
            runCatching { repo.sendText(live.id, local.text, local.id, local.replyTo) }
                .onSuccess { messages = messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Sent) else it } }
                .onFailure { messages = messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Failed) else it } }
        }
    }

    /// Re-send a text that failed (same id, so the server never sees it twice).
    fun retry(message: Message) {
        val thread = openChat ?: return
        if (message.kind != MessageKind.Text) return
        messages = messages.map { if (it.id == message.id) it.copy(status = MessageStatus.Queued) else it }
        deliver(thread, message)
    }

    fun deleteMessage(message: Message) {
        val snapshot = messages
        messages = messages.filter { it.id != message.id }
        if (message.status == MessageStatus.Failed) return
        viewModelScope.launch {
            runCatching { repo.deleteMessage(message.id) }.onFailure {
                if (message.status != MessageStatus.Queued) {
                    messages = snapshot
                    chatNotice = "Не удалось удалить сообщение"
                }
            }
        }
    }

    fun toggleMute(thread: ChatThread) {
        val next = !thread.muted
        updateThread(thread.id) { it.copy(muted = next) }
        viewModelScope.launch { runCatching { repo.setChatMuted(thread.id, next) } }
    }

    fun toggleArchive(thread: ChatThread) {
        val next = !thread.archived
        updateThread(thread.id) { it.copy(archived = next) }
        viewModelScope.launch { runCatching { repo.setChatArchived(thread.id, next) } }
    }

    private fun updateThread(id: UUID, change: (ChatThread) -> ChatThread) {
        threads = threads.map { if (it.id == id) change(it) else it }
        openChat?.takeIf { it.id == id }?.let { openChat = change(it) }
    }

    fun sendImage(bytes: ByteArray) {
        val thread = openChat ?: return
        val local = Message(text = "", isMine = true, author = account.name.ifBlank { "Я" }, time = "сейчас", status = MessageStatus.Queued, kind = MessageKind.Image)
        messages = messages + local
        viewModelScope.launch {
            val live = resolveThread(thread)
            runCatching { repo.sendImage(live.id, bytes, local.id) }
                .onSuccess {
                    val fresh = runCatching { repo.history(live.id) }.getOrNull()
                    messages = fresh ?: messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Sent) else it }
                }
                .onFailure { messages = messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Failed) else it } }
        }
    }

    fun react(messageId: UUID, emoji: String) {
        val current = messages.firstOrNull { it.id == messageId } ?: return
        val mine = current.reactions.firstOrNull { it.emoji == emoji }?.mine == true
        reactionFor = null
        viewModelScope.launch {
            runCatching { repo.react(messageId, emoji, mine) }
            val id = openChat?.id ?: return@launch
            messages = runCatching { repo.history(id) }.getOrDefault(messages)
        }
    }

    // MARK: chat extras (polls, pins, attachments) - iOS ChatViewModel parity

    var pollStates by mutableStateOf<Map<UUID, PollState>>(emptyMap())
    var pinned by mutableStateOf<List<Message>>(emptyList())
    var chatStaff by mutableStateOf(false)
    var uploading by mutableStateOf(false)
    var chatNotice by mutableStateOf<String?>(null)

    var participants by mutableStateOf<List<ChatParticipant>>(emptyList())
    var participantsShown by mutableStateOf(false)
    /// Authors the caller has blocked - their messages are hidden.
    var blocked by mutableStateOf<Set<UUID>>(emptySet())
    var myUserId by mutableStateOf<UUID?>(null)

    /// Pins, the staff flag and the member list for the open chat.
    fun loadChatExtras(thread: ChatThread) = viewModelScope.launch {
        pinned = emptyList()
        chatStaff = false
        participants = emptyList()
        if (thread.id == ChatThread.supportPlaceholder.id) return@launch
        if (blocked.isEmpty()) blocked = repo.blockedIds()
        pinned = repo.fetchPinned(thread.id)
        if (thread.kind == ChatThreadKind.Trip) {
            chatStaff = repo.isChatStaff(thread.id)
            myUserId = auth.currentUserId()
            participants = repo.chatParticipants(thread.id)
        }
    }

    fun participant(id: UUID?): ChatParticipant? = id?.let { uid -> participants.firstOrNull { it.id == uid } }

    fun setMemberMuted(person: ChatParticipant, muted: Boolean) = viewModelScope.launch {
        val chat = openChat?.id ?: return@launch
        repo.setMemberMuted(chat, person.id, muted)
        participants = repo.chatParticipants(chat)
    }

    fun removeMember(person: ChatParticipant) = viewModelScope.launch {
        val chat = openChat?.id ?: return@launch
        repo.removeMember(chat, person.id)
        participants = repo.chatParticipants(chat)
    }

    fun reportMessage(message: Message) = viewModelScope.launch {
        repo.reportMessage(message.id)
        chatNotice = "Жалоба отправлена. Спасибо - мы проверим."
    }

    fun blockAuthor(message: Message) = viewModelScope.launch {
        val author = message.authorId ?: return@launch
        repo.blockUser(author)
        blocked = blocked + author
        chatNotice = "Пользователь заблокирован"
    }

    /// Header of a 1:1 chat opens the other person's mini-profile.
    fun openMiniProfile(userId: UUID) = viewModelScope.launch {
        repo.miniProfile(userId)?.let { miniProfile = it }
    }

    fun refreshPoll(messageId: UUID) = viewModelScope.launch {
        repo.pollState(messageId)?.let { pollStates = pollStates + (messageId to it) }
    }

    fun vote(messageId: UUID, options: List<String>) {
        viewModelScope.launch {
            val ok = repo.votePoll(messageId, options)
            if (!ok) chatNotice = "Не удалось проголосовать: опрос закрыт или изменился"
            refreshPoll(messageId)
        }
    }

    fun addPollOption(messageId: UUID, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addPollOption(messageId, text)
            reloadOpenChat()
            refreshPoll(messageId)
        }
    }

    fun closePoll(messageId: UUID) {
        viewModelScope.launch {
            repo.closePoll(messageId)
            reloadOpenChat()
            refreshPoll(messageId)
        }
    }

    fun createPoll(poll: PollData) {
        val thread = openChat ?: return
        if (!network.isOnline) {
            chatNotice = "Опросы отправляются только онлайн"
            return
        }
        viewModelScope.launch {
            val live = resolveThread(thread)
            runCatching { repo.createPoll(live.id, poll) }
                .onFailure { chatNotice = "Не удалось создать опрос" }
            reloadOpenChat()
        }
    }

    fun setPinned(message: Message, pin: Boolean) {
        viewModelScope.launch {
            repo.setPinned(message.id, pin)
            val id = openChat?.id ?: return@launch
            pinned = repo.fetchPinned(id)
            reloadOpenChat()
        }
    }

    /// Photo-less attachments: voice notes and files. Online only, like iOS.
    fun sendAttachment(bytes: ByteArray, kind: MessageKind, ext: String, contentType: String, name: String? = null, durationMs: Int? = null) {
        val thread = openChat ?: return
        if (!network.isOnline) {
            chatNotice = "Вложения отправляются только онлайн"
            return
        }
        uploading = true
        viewModelScope.launch {
            val live = resolveThread(thread)
            runCatching { repo.sendAttachment(live.id, bytes, kind, ext, contentType, name, durationMs) }
                .onFailure { chatNotice = "Не удалось отправить вложение" }
            uploading = false
            reloadOpenChat()
        }
    }

    private suspend fun reloadOpenChat() {
        val id = openChat?.id ?: return
        runCatching { repo.history(id) }.onSuccess { fresh ->
            val pending = messages.filter { it.status != MessageStatus.Sent && fresh.none { row -> row.id == it.id } }
            messages = fresh + pending
        }
    }

    // MARK: mini-profile and direct messages

    var miniProfile by mutableStateOf<MiniProfile?>(null)

    fun openMiniProfile(profile: MiniProfile) {
        miniProfile = profile
    }

    /// «Написать»: find-or-create the 1:1 chat and open it.
    fun writeTo(profile: MiniProfile) {
        miniProfile = null
        requireAccount {
            viewModelScope.launch {
                val chatId = repo.ensureDirectChat(profile.userId)
                if (chatId == null) {
                    chatNotice = "Не получилось открыть переписку"
                    return@launch
                }
                openTour = null
                sheet = null
                tab = AppTab.Chats
                loadChats().join()
                val thread = threads.firstOrNull { it.id == chatId } ?: ChatThread(
                    id = chatId, title = profile.name, subtitle = "личные сообщения", lastMessage = "", time = "",
                    unread = 0, icon = "person.fill", colors = emptyList(), isPinned = false,
                    kind = ChatThreadKind.Direct, otherUserId = profile.userId, avatarUrl = profile.avatarUrl,
                )
                openThread(thread)
            }
        }
    }

    fun mayWriteTo(profile: MiniProfile): Boolean = policy.allows(role, UserRole.fromRaw(profile.role))

    // MARK: year recap and trip pack

    var recap by mutableStateOf<YearRecap?>(null)
    var recapShown by mutableStateOf(false)
    var tripPlan by mutableStateOf<TripPlan?>(null)
    var tripModeShown by mutableStateOf(false)

    fun openTripMode() {
        tripModeShown = true
        viewModelScope.launch { repo.tripPack()?.let { tripPlan = it } }
    }


    private suspend fun resolveThread(thread: ChatThread): ChatThread {
        if (!thread.isSupport || thread.id != ChatThread.supportPlaceholder.id) return thread
        val real = runCatching { repo.ensureSupport() }.getOrNull() ?: return thread
        openChat = real
        threads = threads.map { if (it.id == ChatThread.supportPlaceholder.id && it.isSupport) real else it }
        return real
    }

    fun loadRole() = viewModelScope.launch {
        val raw = runCatching { repo.myRole() }.getOrDefault("client")
        role = UserRole.fromRaw(raw)
        dmRoles = runCatching { repo.myDmRoles() }.getOrNull()?.map { UserRole.fromRaw(it) }
        if (!role.canBookAndEarn && tab == AppTab.Passport) tab = AppTab.Tours
    }

    fun loadPassport() = viewModelScope.launch {
        // The passport is personal: a guest gets the sign-in wall, never sample data.
        if (!account.isRegistered) {
            passport = PassportData.empty
            return@launch
        }
        val name = account.name.ifBlank { profile.displayName }
        runCatching { repo.fetchPassport(name) }.onSuccess { passport = it }
        recap = repo.yearRecap()
    }

    fun loadProfileBits() = viewModelScope.launch {
        if (!account.isRegistered) return@launch
        trips = runCatching { repo.fetchTrips() }.getOrDefault(emptyList())
        referralCode = runCatching { repo.myCode() }.getOrNull().orEmpty()
        bonus = runCatching { repo.bonusSummary() }.getOrNull()
        bonusLines = repo.bonusLines()
        runCatching { repo.fetchProfile() }.onSuccess { draft ->
            profile = draft
            if (account.name.isBlank() && draft.displayName.isNotBlank()) account.name = draft.displayName
        }
        runCatching { repo.fetchPrefs() }.onSuccess { prefsState = it }
        avatarUrl = repo.currentAvatarUrl()
        loadCompanions()
    }

    /// "loading", "loaded" or the empty-state message to show.
    var bookingsState by mutableStateOf("loading")
    /// Trips already travelled and still unreviewed («Как прошёл тур?»).
    var pendingReviews by mutableStateOf<List<ReviewableTrip>>(emptyList())

    fun loadBookings() = viewModelScope.launch {
        if (AppConfig.useMock) {
            bookings = Mock.bookings
            bookingsState = "loaded"
            return@launch
        }
        runCatching { repo.fetchBookings() }
            .onSuccess { fresh ->
                bookings = fresh
                bookingsState = if (fresh.isEmpty()) "Здесь появятся твои брони и оплаты" else "loaded"
            }
            .onFailure {
                bookingsState = when {
                    bookings.isNotEmpty() -> "loaded"
                    !network.isOnline -> "Нет сети - брони появятся при подключении"
                    else -> "Не удалось загрузить. Потяни, чтобы обновить"
                }
            }
        pendingReviews = runCatching { repo.reviewableTrips() }.getOrDefault(emptyList()).filter { !it.reviewed }
    }

    /// "loading" | "loaded" | "failed" - an error shows only when there is nothing cached.
    var newsState by mutableStateOf("loaded")
    var scheduleState by mutableStateOf("loaded")

    fun loadNews() = viewModelScope.launch {
        if (news.isEmpty()) newsState = "loading"
        runCatching { repo.fetchNews() }
            .onSuccess { news = it; newsState = "loaded" }
            .onFailure {
                if (AppConfig.useMock) news = Mock.news
                newsState = if (news.isEmpty()) "failed" else "loaded"
            }
    }

    fun loadReviews(tourId: UUID? = null) = viewModelScope.launch {
        reviews = runCatching {
            if (tourId != null) repo.fetchReviews(tourId) else repo.fetchAllReviews()
        }.getOrElse { if (AppConfig.useMock) Mock.reviews else emptyList() }
    }

    fun loadSchedule() = viewModelScope.launch {
        if (schedule.isEmpty()) scheduleState = "loading"
        runCatching { repo.fetchDepartures() }
            .onSuccess { schedule = it; scheduleState = "loaded" }
            .onFailure {
                if (AppConfig.useMock) schedule = Mock.schedule
                scheduleState = if (schedule.isEmpty()) "failed" else "loaded"
            }
    }

    fun loadCountries() = viewModelScope.launch {
        // Offline: keep what is already on screen instead of blanking the map.
        countries = runCatching { repo.myCountries() }.getOrDefault(countries)
        tnaCountryCodes = runCatching { repo.tnaCodes() }.getOrDefault(tnaCountryCodes)
    }

    fun loadCompanions() = viewModelScope.launch {
        val loaded = runCatching { repo.fetchCompanions() }.getOrNull()
        if (loaded != null) companions = loaded else if (companions.isEmpty()) companions = cachedCompanions()
    }

    /// Saves one companion; false keeps the editor open with «Не удалось сохранить».
    suspend fun saveCompanion(person: TravelCompanion): Boolean {
        if (runCatching { repo.saveCompanion(person) }.isFailure) return false
        companions = runCatching { repo.fetchCompanions() }.getOrNull()
            ?: (listOf(person) + companions.filter { it.id != person.id })
        persistCompanions(companions)
        return true
    }

    fun removeCompanion(id: UUID) {
        companions = companions.filter { it.id != id }
        persistCompanions(companions)
        viewModelScope.launch { runCatching { repo.deleteCompanion(id) } }
    }

    fun savePrefs(next: NotificationPrefs) {
        prefsState = next
        viewModelScope.launch { runCatching { repo.savePrefs(next) } }
    }

    /// Name first (it is what chats, rating and roster show), then the rest of the profile.
    /// Returns a user-facing error or null.
    suspend fun saveProfileNow(draft: ProfileDraft, displayName: String): String? {
        val nameError = runCatching { repo.setName(displayName) }.exceptionOrNull()
        if (nameError == null) account.name = displayName
        val saved = runCatching { repo.saveProfile(draft) }.isSuccess
        if (saved) profile = draft
        return when {
            nameError != null -> "Имя не сохранилось: ${nameError.message ?: "нет связи"}"
            !saved -> "Не удалось сохранить - нет связи"
            else -> null
        }
    }

    /// A picked photo goes through the crop screen before upload (iOS AvatarCropSheet).
    var cropSource by mutableStateOf<android.graphics.Bitmap?>(null)

    fun pickAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { decodeUpright(bytes) }
            if (bitmap != null) cropSource = bitmap else chatNotice = "Не удалось прочитать фото"
        }
    }

    /// Avatar upload/remove failure, shown as an alert (iOS EditProfileSheet).
    var avatarError by mutableStateOf<String?>(null)

    fun uploadAvatar(bytes: ByteArray) {
        avatarBusy = true
        viewModelScope.launch {
            val url = repo.uploadAvatar(bytes)
            when {
                url == null -> avatarError = "Фото не загрузилось - нет связи"
                !repo.setAvatarUrl(url) -> avatarError = "Фото загрузилось, но не прописалось в профиль"
                else -> avatarUrl = url
            }
            avatarBusy = false
        }
    }

    fun removeAvatar() {
        avatarBusy = true
        viewModelScope.launch {
            if (repo.setAvatarUrl(null)) avatarUrl = null else avatarError = "Не удалось убрать фото"
            avatarBusy = false
        }
    }

    fun applyFriendCode(code: String) {
        val input = code.trim()
        if (input.isEmpty()) return
        viewModelScope.launch {
            referralNote = runCatching { repo.applyReferral(input) }.getOrDefault(ReferralResult.Error).userMessage
            bonus = runCatching { repo.bonusSummary() }.getOrNull()
        }
    }

    /// «Нашлась неточность?»: open support with a prefilled draft.
    fun openSupportChat(prefill: String) {
        sheet = null
        requireAccount {
            tab = AppTab.Chats
            chatDraft = prefill
            openThread(threads.firstOrNull { it.isSupport } ?: ChatThread.supportPlaceholder)
        }
    }

    /// Group chat of a trip, by chat id (profile trip card «Чат группы»).
    fun openGroupChat(chatId: UUID) {
        sheet = null
        tab = AppTab.Chats
        val known = threads.firstOrNull { it.id == chatId }
        if (known != null) {
            openThread(known)
            return
        }
        viewModelScope.launch {
            loadChats().join()
            threads.firstOrNull { it.id == chatId }?.let { openThread(it) }
        }
    }


    fun beginBooking(tour: Tour, departure: Departure?) {
        val next = BookingForm(
            tour, departure,
            name = account.name,
            phone = if (account.phone.isBlank()) "+7 " else account.phone,
        )
        form = next
        sheet = Sheet.Booking(tour, departure)
        // Prefill the full ФИО from the profile; hide the friend-code field once bound.
        if (account.isRegistered) {
            viewModelScope.launch {
                runCatching { repo.fetchProfile() }.getOrNull()?.let { draft ->
                    val full = listOf(draft.lastName, draft.firstName, draft.patronymic).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                    if (full.isNotEmpty()) next.name = full
                }
                runCatching { repo.bonusSummary() }.getOrNull()?.let { next.alreadyReferred = it.bound }
                if (companions.isEmpty()) loadCompanions()
            }
        }
    }

    fun checkCertificate(cert: CertificateRedemption) {
        if (!cert.canCheck) return
        cert.isChecking = true
        viewModelScope.launch {
            val (result, amount) = repo.checkCertificate(cert.code, cert.pin)
            cert.result = result
            cert.amount = amount
            cert.isChecking = false
        }
    }

    fun submitBooking() {
        val current = form ?: return
        if (!current.canSubmit) return
        current.isSubmitting = true
        current.errorMessage = null
        viewModelScope.launch {
            val result = runCatching {
                // A friend's code the server rejects stops the booking, so the discount
                // is never dropped silently. ok / already both mean the account is bound.
                val code = current.friendCode.trim()
                if (code.isNotEmpty() && !current.alreadyReferred && !current.isWaitlist) {
                    val applied = repo.applyReferral(code)
                    if (applied != ReferralResult.Ok && applied != ReferralResult.Already) {
                        throw TnaException(applied.userMessage)
                    }
                    current.alreadyReferred = true
                }
                val departure = current.departure
                if (current.isWaitlist && departure != null) {
                    repo.joinWaitlist(departure.backendId ?: departure.id, current.name.trim(), PhoneNumber.e164(current.phone))
                } else {
                    repo.submitBooking(current.request())
                }
            }
            current.isSubmitting = false
            result.onSuccess { current.isDone = true }.onFailure { current.errorMessage = it.message }
        }
    }

    /// Success screen «В чат с Аней»: close the sheet and open the support thread.
    fun openManagerChat() {
        sheet = null
        openTour = null
        tab = AppTab.Chats
        openThread(threads.firstOrNull { it.isSupport } ?: ChatThread.supportPlaceholder)
    }

    /// Run [action] now if signed in; otherwise open the login sheet and run it after
    /// a successful sign-in (iOS `requireAccount`).
    private var afterLogin: (() -> Unit)? = null

    fun requireAccount(action: () -> Unit) {
        if (account.isRegistered || AppConfig.useMock) {
            action()
            return
        }
        afterLogin = action
        loginError = null
        loginStep = 0
        quickDismissed = false
        sheet = Sheet.Login
    }

    /// Biometrics, then the session a sign-out kept comes back. Never sends an
    /// SMS (iOS `LoginView.quickSignIn`).
    fun quickSignIn() {
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            // Cancelled or failed biometrics: stay on the step, the button retries
            // and the phone flow is right below. No error for a cancel.
            if (!appLock.confirmQuickSignIn()) {
                loginBusy = false
                return@launch
            }
            val keptPhone = account.quickPhone
            when (auth.quickSignIn()) {
                QuickSignInResult.SignedIn -> {
                    account.isRegistered = true
                    account.phone = keptPhone.ifEmpty { auth.currentPhone()?.let { PhoneNumber.pretty(it) }.orEmpty() }
                    account.quickPhone = ""
                    canQuickSignIn = false
                    // The lock was on at sign-out and biometrics have just passed.
                    appLock.restoreAfterQuickSignIn()
                    loginStep = 0
                    loginCode = ""
                    loadRole()
                    loadProfileBits()
                    loadChats()
                    // No "done" step here: the sheet closes, a pending action takes over.
                    val pending = afterLogin
                    afterLogin = null
                    sheet = null
                    pending?.invoke()
                }
                QuickSignInResult.Offline -> loginError = "Нет связи. Попробуй ещё раз"
                QuickSignInResult.Expired, QuickSignInResult.Unavailable -> {
                    // The server dropped that session: one SMS and the account is back.
                    account.quickPhone = ""
                    canQuickSignIn = false
                    if (loginNational.isEmpty() && keptPhone.isNotEmpty()) loginPhone = keptPhone
                    loginError = "Вход по биометрии больше не действует. Нужен код из SMS"
                }
            }
            loginBusy = false
        }
    }

    /// Login sheet: «Войти по другому номеру».
    fun leaveQuickSignIn() {
        quickDismissed = true
        loginError = null
    }

    fun requestCode() {
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            runCatching { auth.requestCode(loginPhone) }
                .onSuccess { loginStep = 1 }
                .onFailure { loginError = it.message }
            loginBusy = false
        }
    }

    fun verifyCode(onDone: () -> Unit = {}) {
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            runCatching { auth.verify(loginPhone, loginCode) }
                .onSuccess {
                    account.isRegistered = true
                    account.phone = PhoneNumber.pretty(loginPhone)
                    // An SMS sign-in replaces whatever an earlier sign-out kept.
                    account.quickPhone = ""
                    canQuickSignIn = false
                    loginStep = 0
                    loginCode = ""
                    loadRole()
                    loadProfileBits()
                    loadChats()
                    if (appLock.shouldOffer) {
                        // Let the login sheet close first, like iOS.
                        viewModelScope.launch { delay(700); lockOfferShown = appLock.shouldOffer }
                    }
                    // A pending action (book, write to support…) takes over the sheet;
                    // otherwise the login sheet shows its «Готово» step.
                    val pending = afterLogin
                    afterLogin = null
                    if (pending != null) {
                        sheet = null
                        pending()
                    } else {
                        onDone()
                    }
                }
                .onFailure { loginError = "Неверный код" }
            loginBusy = false
        }
    }

    /// [keepQuickSignIn]: the biometric lock is on. The session is set aside
    /// (not dropped) so this person comes back with biometrics instead of an
    /// SMS that costs money. Everything else is wiped all the same.
    fun signOut(keepQuickSignIn: Boolean = false) {
        blocked = emptySet()
        val keptPhone = account.phone
        auth.signOut(keepForQuickSignIn = keepQuickSignIn && account.isRegistered)
        canQuickSignIn = auth.hasQuickSignIn
        account.quickPhone = if (canQuickSignIn) keptPhone else ""
        appLock.reset()
        account.isRegistered = false
        account.name = ""
        account.phone = ""
        role = UserRole.Client
        trips = emptyList()
        avatarUrl = null
        referralCode = ""
        bonus = null
        bonusLines = emptyList()
        passport = PassportData.empty
        recap = null
        tripPlan = null
        threads = emptyList()
        loadChats()
    }

    var deleting by mutableStateOf(false)
    var deleteError by mutableStateOf<String?>(null)

    fun deleteAccount() = viewModelScope.launch {
        deleting = true
        deleteError = null
        runCatching { auth.deleteAccount() }
            .onSuccess { signOut() }
            .onFailure { deleteError = "Не удалось удалить - можно повторить позже." }
        deleting = false
    }

    private fun companionPrefs() =
        getApplication<Application>().getSharedPreferences("tna.companions", Context.MODE_PRIVATE)

    // Offline copy of companions: one tab-separated row each, docs included.
    private fun persistCompanions(list: List<TravelCompanion>) {
        fun clean(v: String?) = v.orEmpty().replace('\t', ' ').replace('\n', ' ')
        val raw = list.joinToString("\n") { c ->
            listOf(
                c.id.toString(), c.lastName, c.firstName, c.patronymic, c.birthday, c.phone,
                c.doc.type, c.doc.number, c.doc.citizenship, c.doc.issued, c.doc.expiry, c.doc.issuedBy, c.doc.registration,
            ).joinToString("\t") { clean(it) }
        }
        companionPrefs().edit().putString("rows", raw).apply()
    }

    private fun cachedCompanions(): List<TravelCompanion> =
        companionPrefs().getString("rows", null).orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return@mapNotNull null
            fun at(i: Int) = parts.getOrElse(i) { "" }
            TravelCompanion(
                id = id,
                lastName = at(1),
                firstName = at(2),
                patronymic = at(3),
                birthday = at(4).ifBlank { null },
                phone = at(5),
                doc = IdentityDoc(
                    type = at(6), number = at(7), citizenship = at(8),
                    issued = at(9).ifBlank { null }, expiry = at(10).ifBlank { null },
                    issuedBy = at(11), registration = at(12),
                ),
            )
        }.toList()

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    /// Trip card «Программа»: the tour's day-by-day plan dated from this departure.
    fun openProgram(trip: Trip) {
        val tour = tours.firstOrNull { it.backendId == trip.tourId || it.id == trip.tourId }
        if (tour != null) {
            sheet = Sheet.Program(tour, trip)
            return
        }
        viewModelScope.launch {
            if (tours.isEmpty()) loadCatalog().join()
            tours.firstOrNull { it.backendId == trip.tourId || it.id == trip.tourId }?.let { sheet = Sheet.Program(it, trip) }
        }
    }

    fun openTourById(id: UUID) {
        val tour = tours.firstOrNull { it.backendId == id || it.id == id } ?: return
        openTour = tour
        sheet = null
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = AppModel(app) as T
        }
    }
}

/**
 * Where the user's number most likely comes from: the SIM's country, then the
 * network's, then the system region. No permission needed.
 */
private fun AndroidViewModel.deviceRegion(): String? {
    val telephony = getApplication<Application>().getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    return listOfNotNull(
        runCatching { telephony?.simCountryIso }.getOrNull(),
        runCatching { telephony?.networkCountryIso }.getOrNull(),
        java.util.Locale.getDefault().country,
    ).firstOrNull { it.length == 2 }?.uppercase()
}
