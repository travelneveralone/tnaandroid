package com.travelneveralone.tna

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
    var loginPhone by mutableStateOf("+7 ")
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
            messages = messages.map { if (it.id == target.id) it.copy(text = text, edited = true) else it }
            viewModelScope.launch { runCatching { repo.editMessage(target.id, text) } }
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
        messages = messages.filter { it.id != message.id }
        if (message.status == MessageStatus.Failed) return
        viewModelScope.launch { runCatching { repo.deleteMessage(message.id) } }
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

    fun vote(messageId: UUID, optionId: String) {
        viewModelScope.launch {
            runCatching { repo.votePoll(messageId, optionId) }
            val id = openChat?.id ?: return@launch
            messages = runCatching { repo.history(id) }.getOrDefault(messages)
        }
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
    }

    fun loadProfileBits() = viewModelScope.launch {
        if (!account.isRegistered) return@launch
        trips = runCatching { repo.fetchTrips() }.getOrDefault(emptyList())
        referralCode = runCatching { repo.myCode() }.getOrNull().orEmpty()
        bonus = runCatching { repo.bonusSummary() }.getOrNull()
        bonusLines = repo.bonusLines()
        runCatching { repo.fetchProfile() }.onSuccess { draft ->
            profile = draft
            if (draft.displayName.isNotBlank()) account.name = draft.displayName
        }
        runCatching { repo.fetchPrefs() }.onSuccess { prefsState = it }
        avatarUrl = repo.currentAvatarUrl()
        loadCompanions()
    }

    fun loadBookings() = viewModelScope.launch {
        bookings = runCatching { repo.fetchBookings() }.getOrElse { if (AppConfig.useMock) Mock.bookings else bookings }
    }

    fun loadNews() = viewModelScope.launch {
        news = runCatching { repo.fetchNews() }.getOrElse { if (AppConfig.useMock) Mock.news else news }
    }

    fun loadReviews(tourId: UUID? = null) = viewModelScope.launch {
        reviews = runCatching {
            if (tourId != null) repo.fetchReviews(tourId) else repo.fetchAllReviews()
        }.getOrElse { if (AppConfig.useMock) Mock.reviews else emptyList() }
    }

    fun loadSchedule() = viewModelScope.launch {
        schedule = runCatching { repo.fetchDepartures() }.getOrElse { if (AppConfig.useMock) Mock.schedule else schedule }
    }

    fun loadCountries() = viewModelScope.launch {
        countries = runCatching { repo.myCountries() }.getOrDefault(emptyMap())
        tnaCountryCodes = runCatching { repo.tnaCodes() }.getOrDefault(emptySet())
    }

    fun loadCompanions() = viewModelScope.launch {
        val loaded = runCatching { repo.fetchCompanions() }.getOrNull()
        if (loaded != null) companions = loaded else if (companions.isEmpty()) companions = cachedCompanions()
    }

    fun addCompanion(last: String, first: String) {
        val person = TravelCompanion(lastName = last.trim(), firstName = first.trim())
        companions = listOf(person) + companions
        persistCompanions(companions)
        viewModelScope.launch {
            val saved = runCatching { repo.saveCompanion(person); repo.fetchCompanions() }.getOrNull()
            if (saved != null) {
                companions = saved
                persistCompanions(saved)
            }
        }
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

    fun saveProfile(draft: ProfileDraft) {
        profile = draft
        if (draft.displayName.isNotBlank()) account.name = draft.displayName
        viewModelScope.launch { runCatching { repo.saveProfile(draft) } }
    }

    fun uploadAvatar(bytes: ByteArray) {
        avatarBusy = true
        viewModelScope.launch {
            val url = repo.uploadAvatar(bytes)
            if (url != null && repo.setAvatarUrl(url)) avatarUrl = url
            avatarBusy = false
        }
    }

    fun removeAvatar() {
        viewModelScope.launch { if (repo.setAvatarUrl(null)) avatarUrl = null }
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
        sheet = Sheet.Login
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
                    loginStep = 0
                    loginCode = ""
                    loadRole()
                    loadProfileBits()
                    loadChats()
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

    fun signOut() {
        auth.signOut()
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
            .onFailure { deleteError = "Не удалось удалить — можно повторить позже." }
        deleting = false
    }

    private fun companionPrefs() =
        getApplication<Application>().getSharedPreferences("tna.companions", Context.MODE_PRIVATE)

    private fun persistCompanions(list: List<TravelCompanion>) {
        val raw = list.joinToString("\n") { "${it.id}\t${it.lastName}\t${it.firstName}\t${it.patronymic}" }
        companionPrefs().edit().putString("rows", raw).apply()
    }

    private fun cachedCompanions(): List<TravelCompanion> =
        companionPrefs().getString("rows", null).orEmpty().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return@mapNotNull null
            TravelCompanion(id = id, lastName = parts[1], firstName = parts[2], patronymic = parts.getOrElse(3) { "" })
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
