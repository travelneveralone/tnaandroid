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
    data class Program(val tour: Tour) : Sheet
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
    var passport by mutableStateOf(Mock.passport)
    var bookings by mutableStateOf<List<BookingSummary>>(emptyList())
    var news by mutableStateOf<List<NewsItem>>(emptyList())
    var reviews by mutableStateOf<List<TourReview>>(emptyList())
    var schedule by mutableStateOf<List<DepartureItem>>(emptyList())
    var trips by mutableStateOf<List<Trip>>(emptyList())
    var referralCode by mutableStateOf("")
    var bonus by mutableStateOf<BonusSummary?>(null)
    var countries by mutableStateOf<Map<String, CountryStatus>>(emptyMap())
    var tnaCountryCodes by mutableStateOf<Set<String>>(emptySet())
    var companions by mutableStateOf<List<TravelCompanion>>(emptyList())
    var prefsState by mutableStateOf(NotificationPrefs())
    var profile by mutableStateOf(ProfileDraft())
    var chatDraft by mutableStateOf("")
    var reactionFor by mutableStateOf<UUID?>(null)
    var certificateNote by mutableStateOf<String?>(null)
    var certificateBusy by mutableStateOf(false)
    var loginPhone by mutableStateOf("+7 ")
    var loginCode by mutableStateOf("")
    var loginStep by mutableStateOf(0)
    var loginError by mutableStateOf<String?>(null)
    var loginBusy by mutableStateOf(false)
    var bookingBusy by mutableStateOf(false)
    var bookingDone by mutableStateOf(false)
    var bookingError by mutableStateOf<String?>(null)
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
            loadCatalog()
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
                else if (!network.isOnline) {
                    tours = Mock.tours
                    catalogState = "loaded"
                } else {
                    catalogState = "failed"
                    catalogError = error.message
                }
            }
    }

    fun loadChats() = viewModelScope.launch {
        if (AppConfig.useMock || !network.isOnline && threads.isEmpty()) {
            if (threads.isEmpty()) threads = Mock.threads
        }
        runCatching { repo.fetchThreads() }.onSuccess { loaded ->
            threads = if (loaded.none { it.isSupport }) listOf(ChatThread.supportPlaceholder) + loaded else loaded
        }.onFailure {
            if (threads.isEmpty()) threads = Mock.threads
        }
    }

    fun openThread(thread: ChatThread) {
        openChat = thread
        viewModelScope.launch {
            if (thread.isSupport && thread.id == ChatThread.supportPlaceholder.id) {
                loadChats()
                threads.firstOrNull { it.isSupport }?.let { openChat = it }
            }
            val id = openChat?.id ?: return@launch
            messages = runCatching { repo.history(id) }.getOrElse {
                if (thread.isSupport) Mock.managerChat else emptyList()
            }
            runCatching { repo.markRead(id) }
        }
    }

    fun sendChat() {
        val thread = openChat ?: return
        val text = chatDraft.trim()
        if (text.isEmpty()) return
        chatDraft = ""
        val local = Message(text = text, isMine = true, author = account.name.ifBlank { "Я" }, time = "сейчас", status = MessageStatus.Queued)
        messages = messages + local
        viewModelScope.launch {
            val live = resolveThread(thread)
            runCatching { repo.sendText(live.id, text, local.id) }
                .onSuccess { messages = messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Sent) else it } }
                .onFailure { messages = messages.map { if (it.id == local.id) it.copy(status = MessageStatus.Failed) else it } }
        }
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
        val name = account.name.ifBlank { profile.displayName }
        passport = runCatching { repo.fetchPassport(name) }.getOrElse {
            val fallback = if (!network.isOnline && passport.stats.isEmpty()) Mock.passport else passport
            fallback.copy(userName = name.ifBlank { fallback.userName })
        }
    }

    fun loadProfileBits() = viewModelScope.launch {
        if (!account.isRegistered) return@launch
        trips = runCatching { repo.fetchTrips() }.getOrDefault(emptyList())
        referralCode = runCatching { repo.myCode() }.getOrNull().orEmpty()
        bonus = runCatching { repo.bonusSummary() }.getOrNull()
        runCatching { repo.fetchProfile() }.onSuccess { draft ->
            profile = draft
            if (draft.displayName.isNotBlank()) account.name = draft.displayName
        }
        runCatching { repo.fetchPrefs() }.onSuccess { prefsState = it }
        loadCompanions()
    }

    fun loadBookings() = viewModelScope.launch {
        bookings = runCatching { repo.fetchBookings() }.getOrElse { if (!network.isOnline) Mock.bookings else emptyList() }
    }

    fun loadNews() = viewModelScope.launch {
        news = runCatching { repo.fetchNews() }.getOrElse { Mock.news }
    }

    fun loadReviews(tourId: UUID? = null) = viewModelScope.launch {
        reviews = runCatching {
            if (tourId != null) repo.fetchReviews(tourId) else repo.fetchAllReviews()
        }.getOrElse { Mock.reviews }
    }

    fun loadSchedule() = viewModelScope.launch {
        schedule = runCatching { repo.fetchDepartures() }.getOrElse { Mock.schedule }
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

    fun buyCertificate(amount: Int, recipient: String, wish: String) {
        certificateBusy = true
        certificateNote = null
        viewModelScope.launch {
            val url = runCatching {
                repo.buyCertificate(amount, recipient, wish, account.name, account.phone)
            }.getOrNull()
            certificateBusy = false
            certificateNote = if (url.isNullOrBlank()) "Заявка ушла. Если оплата не открылась, менеджер пришлёт ссылку." else url
        }
    }

    fun beginBooking(tour: Tour, departure: Departure?) {
        bookingDone = false
        bookingError = null
        form = BookingForm(
            tour, departure,
            name = account.name,
            phone = if (account.phone.isBlank()) "+7 " else account.phone,
        )
        sheet = Sheet.Booking(tour, departure)
    }

    fun submitBooking() {
        val current = form ?: return
        if (!current.canSubmit) return
        bookingBusy = true
        viewModelScope.launch {
            val result = runCatching {
                val code = current.friendCode.trim()
                if (code.isNotEmpty()) {
                    val applied = repo.applyReferral(code)
                    if (applied != ReferralResult.Ok && applied != ReferralResult.Already) {
                        throw TnaException(applied.userMessage)
                    }
                }
                val departure = current.departure
                if (departure?.isSoldOut == true && departure.backendId != null) {
                    repo.joinWaitlist(departure.backendId, current.name.trim(), PhoneNumber.e164(current.phone))
                } else {
                    repo.submitBooking(current.request())
                }
            }
            bookingBusy = false
            result.onSuccess { bookingDone = true }.onFailure { bookingError = it.message }
        }
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

    fun verifyCode() {
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            runCatching { auth.verify(loginPhone, loginCode) }
                .onSuccess {
                    account.isRegistered = true
                    account.phone = PhoneNumber.pretty(loginPhone)
                    sheet = null
                    loginStep = 0
                    loadRole()
                    loadProfileBits()
                }
                .onFailure { loginError = it.message }
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
    }

    fun deleteAccount() = viewModelScope.launch {
        runCatching { auth.deleteAccount() }
            .onSuccess {
                account.isRegistered = false
                account.name = ""
                account.phone = ""
            }
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
