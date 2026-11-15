package com.travelneveralone.tna

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Port of TNAiOS Features/TourDetail (TourDetailView, TourDetailComponents, ReviewComposer).

/// Screen state for one tour (iOS TourReviewsViewModel + the view's own @State).
class TourDetailModel(private val repo: TnaRepository, val tour: Tour) {
    enum class Reviews { Loading, Loaded, Empty, Failed }

    var reviewsState by mutableStateOf(Reviews.Loading)
    var reviews by mutableStateOf<List<TourReview>>(emptyList())
    var pendingTrips by mutableStateOf<List<ReviewableTrip>>(emptyList())
    var myDepartureIds by mutableStateOf<Set<UUID>>(emptySet())
    var subscribed by mutableStateOf(false)
    var subscribing by mutableStateOf(false)
    var subscribeError by mutableStateOf<String?>(null)
    var submittingReview by mutableStateOf(false)

    val tourKey: UUID get() = tour.backendId ?: tour.id
    val pendingTrip: ReviewableTrip? get() = pendingTrips.firstOrNull()
    val canReview: Boolean get() = pendingTrip != null

    fun isMember(dep: Departure): Boolean = dep.backendId?.let { myDepartureIds.contains(it) } == true

    suspend fun loadReviews() {
        val id = tour.backendId
        if (id == null) {
            reviewsState = if (AppConfig.useMock) Reviews.Loaded else Reviews.Empty
            reviews = if (AppConfig.useMock) Mock.reviews else emptyList()
            return
        }
        reviewsState = Reviews.Loading
        reviewsState = runCatching { repo.fetchReviews(id) }.fold(
            onSuccess = { reviews = it; if (it.isEmpty()) Reviews.Empty else Reviews.Loaded },
            onFailure = { Reviews.Failed },
        )
    }

    suspend fun loadPersonal(registered: Boolean) {
        if (!registered) {
            myDepartureIds = emptySet()
            pendingTrips = emptyList()
            subscribed = false
            return
        }
        myDepartureIds = runCatching { repo.fetchTrips() }.getOrDefault(emptyList()).map { it.id }.toSet()
        val id = tour.backendId
        pendingTrips = if (id == null) emptyList() else runCatching { repo.reviewableTrips() }.getOrDefault(emptyList())
            .filter { it.tourId == id && !it.reviewed }
            .sortedByDescending { it.endsOn }
        if (tour.departures.isEmpty()) subscribed = repo.subscribedTourIds().contains(tourKey)
    }

    suspend fun toggleSubscription(online: Boolean) {
        if (subscribing) return
        if (!online) {
            subscribeError = "Нет сети — действие доступно со связью"
            return
        }
        val target = !subscribed
        subscribing = true
        subscribeError = null
        runCatching { repo.setSubscription(tourKey, target) }
            .onSuccess { subscribed = target }
            .onFailure { subscribeError = "Не получилось — можно повторить" }
        subscribing = false
    }

    suspend fun submitReview(rating: Int, text: String, photos: List<ByteArray>): Boolean {
        val id = tour.backendId ?: return false
        val trip = pendingTrip ?: return false
        submittingReview = true
        val urls = photos.take(3).mapNotNull { repo.uploadReviewImage(it) }
        val ok = runCatching { repo.submitReview(id, trip.id, rating, text, urls) }.isSuccess
        if (ok) pendingTrips = pendingTrips.filter { it.id != trip.id }
        submittingReview = false
        return ok
    }
}

@Composable
fun TourDetailScreen(app: AppModel, tour: Tour) {
    val model = remember(tour.id) { TourDetailModel(app.repo, tour) }
    val canBook = app.role.canBookAndEarn
    var selected by remember(tour.id) { mutableStateOf(if (canBook) tour.departures.firstOrNull() else null) }
    var customDates by remember(tour.id) { mutableStateOf(tour.departures.isEmpty()) }
    var composing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val compactVisible = scroll.value > with(density) { 320.dp.toPx() }
    val scope = rememberCoroutineScope()

    BackHandler { app.openTour = null }
    LaunchedEffect(tour.id) { model.loadReviews() }
    LaunchedEffect(tour.id, app.account.isRegistered) { model.loadPersonal(app.account.isRegistered) }

    Box(Modifier.fillMaxSize().background(TnaColor.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(tour, scroll.value)
            Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                StatsRow(tour)
                DeparturesSection(
                    app, model, selected, customDates,
                    onSelect = { selected = it; customDates = false },
                    onCustom = { customDates = true },
                )
                if (tour.program.isNotEmpty()) ProgramSection(tour, if (customDates) null else selected)
                if (tour.includes.isNotEmpty()) IncludesSection(tour)
                ReviewsSection(model) { app.requireAccount { composing = true } }
                InfoLinks(
                    onTerms = { app.sheet = Sheet.Terms },
                    onReviews = { app.loadReviews(null); app.sheet = Sheet.Reviews },
                )
                Spacer(Modifier.height(if (canBook) 120.dp else 40.dp))
            }
        }
        // Floating back button over the hero, replaced by the compact bar once scrolled.
        AnimatedVisibility(!compactVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape).pressable(0.9f) { app.openTour = null },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        AnimatedVisibility(compactVisible, enter = fadeIn(), exit = fadeOut()) { CompactBar(tour) { app.openTour = null } }
        if (canBook) {
            BookingBar(
                tour = tour,
                customDates = customDates,
                member = !customDates && selected?.let { model.isMember(it) } == true,
                soldOut = selected?.isSoldOut == true,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                when {
                    customDates -> app.beginBooking(tour, null)
                    else -> app.beginBooking(tour, selected ?: tour.departures.firstOrNull())
                }
            }
        }
    }
    if (composing) {
        ReviewComposerSheet(model) { composing = false }
    }
}

@Composable
private fun Hero(tour: Tour, scrollY: Int) {
    val images = tour.heroImages
    Box(Modifier.fillMaxWidth().height(420.dp).clipToBounds()) {
        // Light parallax: the photo moves slower than the page.
        Box(Modifier.matchParentSize().graphicsLayer { translationY = scrollY * 0.4f }) {
            HeroCarousel(images, tour.colors)
            if (images.isEmpty()) SymbolWatermark(tour.symbol, 230, 90, -130, -12f, 0.13f)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tour.name.uppercase(), style = displayStyle(56, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tour.tagline.isNotBlank()) Text(tour.tagline, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/// Swipeable photos with auto-advance every 4 s and page dots (iOS TourHeroCarousel).
@Composable
private fun HeroCarousel(images: List<String>, colors: List<Long>) {
    TourGradient(colors, Modifier.fillMaxSize()) {
        if (images.size == 1) {
            AsyncImage(model = images[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (images.size > 1) {
            // A long virtual strip so the loop never hits an edge.
            val loops = 1000
            val pager = rememberPagerState(initialPage = images.size * (loops / 2)) { images.size * loops }
            LaunchedEffect(pager.settledPage) {
                delay(4000)
                pager.animateScrollToPage(pager.currentPage + 1)
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.matchParentSize().semantics { contentDescription = "Фотографии тура. Смахните влево или вправо" },
            ) { page ->
                AsyncImage(
                    model = images[page % images.size],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val current = pager.currentPage % images.size
                images.indices.forEach { i ->
                    Box(
                        Modifier.height(6.dp).width(if (i == current) 18.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == current) 1f else 0.5f))
                    )
                }
            }
        }
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))
            )
        )
    }
}

@Composable
private fun CompactBar(tour: Tour, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(TnaColor.bg)) {
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TnaColor.ink, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tour.name.uppercase(), color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                if (tour.tagline.isNotBlank()) Text(tour.tagline, color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
    }
}

@Composable
private fun StatsRow(tour: Tour) {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill(Icons.Filled.CalendarMonth, "${tour.days}", "дней", Modifier.weight(1f))
        StatPill(Icons.Filled.Timeline, Money.grouped(tour.km), "км", Modifier.weight(1f))
        StatPill(Icons.Filled.Landscape, Money.grouped(tour.maxAltitude), "м высота", Modifier.weight(1f))
        StatPill(Icons.Filled.LocalFireDepartment, "•".repeat(tour.difficulty.coerceIn(0, 5)), "сложность", Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(TnaColor.surface).border(1.dp, TnaColor.stroke, RoundedCornerShape(16.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(17.dp))
        Text(value, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = TnaColor.subtle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/// 1.5 dp dashed rounded outline (custom-dates chip, «Свои даты» button).
fun Modifier.dashedOutline(color: Color, corner: Int = 16): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(corner.dp.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
    )
}

@Composable
private fun DeparturesSection(
    app: AppModel,
    model: TourDetailModel,
    selected: Departure?,
    customDates: Boolean,
    onSelect: (Departure) -> Unit,
    onCustom: () -> Unit,
) {
    val tour = model.tour
    val canBook = app.role.canBookAndEarn
    val hasDates = tour.departures.isNotEmpty()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Даты заездов", if (hasDates) "#выбери_свою" else "#скоро_откроем")
        if (hasDates) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tour.departures.forEach { dep ->
                    val isSelected = !customDates && selected?.id == dep.id
                    val member = model.isMember(dep)
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .clip(shape)
                            .background(if (isSelected) TnaColor.ink else TnaColor.surface)
                            .then(if (isSelected) Modifier else Modifier.border(1.dp, TnaColor.stroke, shape))
                            .pressable(enabled = canBook) { onSelect(dep) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(dep.label, color = if (isSelected) Color.White else TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val note = when {
                            member -> "Ты едешь ✓"
                            dep.isSoldOut -> "мест нет"
                            dep.spotsLeft <= 3 -> "🔥 ${dep.spotsLeft} ${pluralRU(dep.spotsLeft, "место", "места", "мест")}"
                            else -> "${dep.spotsLeft} ${pluralRU(dep.spotsLeft, "место", "места", "мест")}"
                        }
                        val noteColor = when {
                            member -> if (isSelected) Color.White else Color.hex(0x2E9E5B)
                            isSelected -> Color.White.copy(alpha = 0.75f)
                            else -> TnaColor.subtle
                        }
                        Text(note, color = noteColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (canBook) {
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .clip(shape)
                            .background(if (customDates) TnaColor.accentBrush else androidx.compose.ui.graphics.Brush.linearGradient(listOf(TnaColor.surface, TnaColor.surface)))
                            .then(if (customDates) Modifier else Modifier.dashedOutline(TnaColor.accent.copy(alpha = 0.5f)))
                            .pressable(onClick = onCustom)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = if (customDates) Color.White else TnaColor.ink, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Свои даты", color = if (customDates) Color.White else TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("частный тур", color = if (customDates) Color.White.copy(alpha = 0.75f) else TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TnaColor.surface)
                    .border(1.dp, TnaColor.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Собираем новые заезды", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (canBook) "Соберём этот тур под твои даты — или напишем, как только откроем новый заезд."
                    else "Заезды по этому туру пока не открыты.",
                    color = TnaColor.subtle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (canBook) {
                    Row(
                        Modifier.fillMaxWidth().dashedOutline(TnaColor.accent.copy(alpha = 0.5f)).pressable {
                            onCustom(); app.beginBooking(tour, null)
                        }.padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = TnaColor.ink, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Свои даты", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("частный тур", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.elevated)
                            .pressable {
                                app.requireAccount { scope.launch { model.toggleSubscription(app.network.isOnline) } }
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tint = if (model.subscribed) Color.hex(0x2E9E5B) else TnaColor.ink
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            when {
                                model.subscribing -> CircularProgressIndicator(Modifier.size(16.dp), color = TnaColor.ink, strokeWidth = 2.dp)
                                model.subscribed -> Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(18.dp))
                                else -> Icon(Icons.Filled.NotificationsActive, null, tint = tint, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (model.subscribed) "Сообщим о новых датах" else "Сообщить о новых датах", color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    model.subscribeError?.let { Text(it, color = Color.hex(0xC0392B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM, EE", Locale("ru"))

@Composable
private fun ProgramSection(tour: Tour, departure: Departure?) {
    var expanded by remember(tour.id) { mutableStateOf(setOf<UUID>()) }
    val color = tour.colors.firstOrNull()?.let { Color.hex(it) } ?: TnaColor.accent
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Программа", "#день_за_днём")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tour.program.forEach { day ->
                val date = departure?.startsOn?.plusDays((day.day - 1).toLong())
                DayCard(day, color, expanded.contains(day.id), date?.let { dayFormat.format(it) }) {
                    expanded = if (expanded.contains(day.id)) expanded - day.id else expanded + day.id
                }
            }
        }
    }
}

/// One programme day: numbered circle, title, date; tap to unfold the details.
@Composable
fun DayCard(day: DayPlan, color: Color, expanded: Boolean, dateText: String?, isToday: Boolean = false, onTap: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isToday) TnaColor.accent.copy(alpha = 0.08f) else TnaColor.surface)
            .border(if (isToday) 2.dp else 1.dp, if (isToday) TnaColor.accent else TnaColor.stroke, shape)
            .pressable(0.98f, onClick = onTap)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (isToday) TnaColor.accent else color), contentAlignment = Alignment.Center) {
                Text("${day.day}", style = displayStyle(20, Color.White))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(day.title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (dateText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dateText, color = if (isToday) TnaColor.accent else TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (isToday) {
                            Spacer(Modifier.width(6.dp))
                            Pill("Сегодня", TnaColor.accent, filled = true)
                        }
                    }
                }
            }
            Icon(Icons.Filled.ExpandMore, null, tint = TnaColor.subtle, modifier = Modifier.size(20.dp).rotate(chevron))
        }
        if (expanded && day.details.isNotBlank()) {
            Text(day.details, color = TnaColor.subtle, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun IncludesSection(tour: Tour) {
    val color = tour.colors.firstOrNull()?.let { Color.hex(it) } ?: TnaColor.accent
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Что включено", "#всё_продумано")
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TnaColor.surface).padding(horizontal = 16.dp)) {
            tour.includes.forEachIndexed { index, item ->
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(item, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (index < tour.includes.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
            }
        }
    }
}

@Composable
private fun ReviewsSection(model: TourDetailModel, onWrite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Отзывы", "#честно")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (model.reviewsState) {
                TourDetailModel.Reviews.Loading -> repeat(2) { SkeletonBlock(Modifier.fillMaxWidth().height(92.dp), corner = 20) }
                TourDetailModel.Reviews.Failed -> ReviewsPlaceholder("Не удалось загрузить отзывы")
                TourDetailModel.Reviews.Empty -> ReviewsPlaceholder(if (model.canReview) "Пока нет отзывов — будь первым!" else "Пока нет отзывов")
                TourDetailModel.Reviews.Loaded -> model.reviews.forEach { ReviewCard(it) }
            }
            if (model.canReview) {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.padding(top = 4.dp).fillMaxWidth().clip(shape).background(TnaColor.accentBrush).pressable(onClick = onWrite).padding(vertical = 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EditNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Оставить отзыв", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    model.pendingTrip?.label?.takeIf { it.isNotBlank() }?.let {
                        Text("о поездке $it", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewsPlaceholder(text: String) {
    Text(text, color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
}

/// Published review: author, date, stars, text, photos, TNA's reply.
@Composable
fun ReviewCard(review: TourReview, showTour: Boolean = false) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(review.authorAvatarUrl, review.author, 32)
            Spacer(Modifier.width(10.dp))
            Text(review.author, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            Text(review.date, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (showTour && !review.tourName.isNullOrBlank()) {
            Text(review.tourName, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Stars(review.rating, 14)
        if (review.text.isNotBlank()) Text(review.text, color = TnaColor.ink, fontSize = 15.sp)
        if (review.images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                review.images.forEachIndexed { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(TnaColor.elevated).pressable { viewer = index },
                    )
                }
            }
        }
        review.reply?.let { reply ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TnaColor.accent.copy(alpha = 0.08f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = TnaColor.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ответ TNA", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(reply, color = TnaColor.ink, fontSize = 14.sp)
            }
        }
    }
    viewer?.let { ImageViewerDialog(review.images, it) { viewer = null } }
}

@Composable
private fun InfoLinks(onTerms: () -> Unit, onReviews: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoLinkButton(Icons.Filled.Description, "Условия бронирования", Modifier.weight(1f), onTerms)
        InfoLinkButton(Icons.Filled.FormatQuote, "Отзывы участников", Modifier.weight(1f), onReviews)
    }
}

@Composable
fun InfoLinkButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.heightIn(min = 60.dp).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = TnaColor.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun BookingBar(tour: Tour, customDates: Boolean, member: Boolean, soldOut: Boolean, modifier: Modifier, onBook: () -> Unit) {
    Column(modifier.fillMaxWidth().background(TnaColor.bg.copy(alpha = 0.96f))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SalePriceLabel(tour, prominent = true)
                Text("предоплата 20%", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            val title = when {
                customDates -> "Оставить заявку"
                member -> "Ты в этом заезде"
                soldOut -> "Лист ожидания"
                else -> "Забронировать"
            }
            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .height(54.dp)
                    .clip(shape)
                    .background(if (member) androidx.compose.ui.graphics.Brush.linearGradient(listOf(TnaColor.subtle, TnaColor.subtle)) else TnaColor.accentBrush)
                    .pressable(enabled = !member, onClick = onBook)
                    .padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center,
            ) { Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewComposerSheet(model: TourDetailModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rating by remember { mutableIntStateOf(5) }
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        photos = uris.take(3).mapNotNull { uri -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TnaColor.bg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Оставить отзыв", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (sent) "Готово" else "Отмена", color = TnaColor.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable(onClick = onDismiss))
            }
            if (sent) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Verified, null, tint = TnaColor.accent, modifier = Modifier.size(54.dp))
                    Gap(12)
                    Text("Спасибо! Отзыв появится после модерации.", color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            } else {
                model.pendingTrip?.let { trip ->
                    Text(if (trip.label.isBlank()) trip.tourName else "${trip.tourName} · ${trip.label}", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text("Оценка", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (1..5).forEach { i ->
                        Icon(
                            if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "$i",
                            tint = TnaColor.accent,
                            modifier = Modifier.size(32.dp).pressable(0.9f) { rating = i },
                        )
                    }
                }
                Text("Отзыв", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(color = TnaColor.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(TnaColor.accent),
                    modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(14.dp)).background(TnaColor.surface)
                        .border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp)).padding(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.forEach { bytes ->
                        AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    if (photos.size < 3) {
                        Column(
                            Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(TnaColor.accent.copy(alpha = 0.1f)).pressable { picker.launch("image/*") },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.PhotoCamera, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
                            Text("Фото", color = TnaColor.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (failed) Text("Не удалось отправить — можно повторить.", color = TnaColor.danger, fontSize = 13.sp)
                PrimaryButton(
                    if (model.submittingReview) "Отправляю…" else "Отправить",
                    enabled = text.isNotBlank() && !model.submittingReview,
                ) {
                    failed = false
                    scope.launch {
                        if (model.submitReview(rating, text.trim(), photos)) sent = true else failed = true
                    }
                }
            }
        }
    }
}
