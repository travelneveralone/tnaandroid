package com.travelneveralone.tna

import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Port of TNAiOS Features/Catalog (CatalogView + Views/TourCards). Same blocks, same order.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(app: AppModel) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val searching = app.search.isNotBlank()
    val chips = CatalogFilter.available(app.tours)
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                app.loadCatalog().join()
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(TnaColor.bg),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            full { CatalogHeader(app) }
            when (app.catalogState) {
                "loading" -> full { CatalogSkeleton() }
                "failed" -> full {
                    EmptyState(
                        Icons.Filled.WifiOff,
                        title = null,
                        text = app.catalogError ?: "Не вышло загрузить каталог",
                        action = "Повторить",
                    ) { app.loadCatalog() }
                }
                else -> {
                    app.campaign?.takeIf { it.showsBanner }?.let { offer -> full { CampaignBanner(offer) } }
                    if (!searching) {
                        full { CompassBanner { app.sheet = Sheet.Compass } }
                        if (app.tours.isNotEmpty()) full { FeaturedCarousel(app) }
                    }
                    full { SearchField(app.search, onChange = { app.search = it }, onDone = { focus.clearFocus() }) }
                    if (chips.size > 1) {
                        full {
                            Row(
                                Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) { chips.forEach { chip -> Chip(chip.title, chip == app.filter) { app.filter = chip } } }
                        }
                    }
                    val tours = app.filteredTours
                    if (tours.isEmpty() && searching) {
                        full { NoResults() }
                    } else {
                        items(tours, key = { it.backendId ?: it.id }) { tour ->
                            SmallTourCard(tour, app.campaign) { focus.clearFocus(); app.openTour = tour }
                        }
                    }
                    if (app.role.canBookAndEarn) full { IndividualTourCard(app.isProd) { app.sheet = Sheet.Individual } }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun CatalogHeader(app: AppModel) {
    Column(Modifier.statusBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mark + name + the English name underneath: reads as a brand, not a slogan.
            BrandMark(52, app.isProd)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ПУТЕШЕСТВУЙ", style = displayStyle(24), maxLines = 1)
                Text("НЕ ОДИН", style = displayBrushStyle(24), maxLines = 1, modifier = Modifier.offset(y = (-3).dp))
                Text(
                    "TRAVEL NEVER ALONE",
                    color = TnaColor.subtle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Filled.CalendarMonth) { app.loadSchedule(); app.sheet = Sheet.Schedule }
            Spacer(Modifier.width(12.dp))
            CircleIconButton(Icons.Filled.NotificationsActive, tint = TnaColor.accent) { app.loadNews(); app.sheet = Sheet.News }
        }
        Gap(14)
        Text("Авторские туры по России и миру", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CampaignBanner(offer: CampaignOffer) {
    Card {
        Text(offer.name.ifBlank { "Акция" }.uppercase(), color = TnaColor.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (offer.title.isNotBlank()) Text(offer.title, color = TnaColor.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (offer.text.isNotBlank()) Text(offer.text, color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CompassBanner(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = TnaColor.accent, spotColor = TnaColor.accent)
            .clip(shape)
            .background(TnaColor.accentBrush)
            .pressable(0.98f, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧭", fontSize = 30.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Не знаешь, куда хочешь?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Подберём тур за 30 секунд", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TnaColor.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CatalogSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(Modifier.bleed(20.dp).horizontalScroll(rememberScrollState(), enabled = false).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) { SkeletonBlock(Modifier.width(290.dp).height(400.dp), corner = 28) }
        }
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonBlock(Modifier.weight(1f).height(210.dp), corner = 22)
                SkeletonBlock(Modifier.weight(1f).height(210.dp), corner = 22)
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(app: AppModel) {
    val featured = app.featuredTours
    val state = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val onTop = app.tab == AppTab.Tours && app.openTour == null && app.openChat == null && app.sheet == null &&
        app.miniProfile == null && !app.recapShown && !app.tripModeShown && app.cropSource == null
    val video = FeaturedVideoPolicy.system(context, onTop)
    androidx.compose.runtime.LaunchedEffect(featured.mapNotNull { it.videoUrl }, app.network.isOnline, app.network.isConstrained) {
        app.prepareVideos()
    }
    Column(Modifier.bleed(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Ближайшие заезды", "#скоро_в_путь")
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(featured, key = { it.backendId ?: it.id }) { tour ->
                FeaturedCard(
                    tour,
                    app.campaign,
                    videoFile = if (video.mountsClip) tour.videoUrl?.let { app.videoFiles[it] } else null,
                    mayPlay = video.mayPlay,
                ) { app.openTour = tour }
            }
        }
    }
}

@Composable
fun FeaturedCard(tour: Tour, campaign: CampaignOffer?, videoFile: java.io.File? = null, mayPlay: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .width(290.dp)
            .height(400.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .pressable(0.97f, onClick = onClick),
    ) {
        TourCover(tour.imageUrl, tour.colors, Modifier.matchParentSize())
        if (videoFile != null) {
            LoopingVideo(videoFile, mayPlay, Modifier.matchParentSize())
            // Same bottom scrim as the photo cover, so text stays readable over the clip.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))))
        }
        if (tour.imageUrl == null && videoFile == null) SymbolWatermark(tour.symbol, 170, 70, -80, -12f)
        val plate = SalePlate.make(tour, campaign?.until)
        if (plate != null) {
            SalePlateView(plate, featured = true)
        } else {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tour.departures.firstOrNull()?.takeIf { it.spotsLeft in 1..3 }?.let { hot ->
                    FireBadge("Осталось ${hot.spotsLeft} ${pluralRU(hot.spotsLeft, "место", "места", "мест")}")
                }
                Text(tour.name.uppercase(), style = displayStyle(40, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tour.tagline, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${tour.days} ${pluralRU(tour.days, "день", "дня", "дней")}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    SalePriceLabel(tour, onDark = true)
                }
            }
        }
    }
}

@Composable
fun SmallTourCard(tour: Tour, campaign: CampaignOffer?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val plate = SalePlate.make(tour, campaign?.until)
    Box(
        Modifier
            .fillMaxWidth()
            // Sale cards keep the photo as a frame around the black plate, so they grow.
            .then(if (plate != null) Modifier.aspectRatio(0.72f) else Modifier.height(210.dp))
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .pressable(0.97f, onClick = onClick),
    ) {
        TourCover(tour.imageUrl, tour.colors, Modifier.matchParentSize())
        if (tour.imageUrl == null) SymbolWatermark(tour.symbol, 90, 40, -55, -10f, 0.13f)
        if (plate != null) {
            SalePlateView(plate, featured = false)
        } else {
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(tour.name.uppercase(), style = displayStyle(24, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${tour.days} ${pluralRU(tour.days, "день", "дня", "дней")} · ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    SalePriceLabel(tour, onDark = true, compact = true)
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                if (tour.seasonText.isNotBlank()) {
                    Text(
                        tour.seasonText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val hot = tour.departures.minByOrNull { it.spotsLeft }
                if (hot != null && hot.spotsLeft <= 2) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(TnaColor.fire), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

/// Black plate on the cover: handle, destination, one white price, campaign pill.
@Composable
private fun SalePlateView(plate: SalePlate, featured: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(if (featured) 10.dp else 6.dp)
            .clip(RoundedCornerShape(if (featured) 22.dp else 16.dp))
            .background(Color.hex(0x111111))
            .padding(if (featured) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (featured) 10.dp else 6.dp),
    ) {
        Row {
            Text("@travelneveralone", color = Color.White.copy(alpha = 0.8f), fontSize = if (featured) 12.sp else 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (plate.season.isNotBlank()) Text(plate.season, color = Color.White.copy(alpha = 0.8f), fontSize = if (featured) 12.sp else 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(plate.name, style = displayStyle(if (featured) 34 else 22, Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                .padding(horizontal = 10.dp, vertical = if (featured) 10.dp else 7.dp),
        ) {
            Text(plate.daysLine, color = Color.hex(0x111111), fontSize = if (featured) 11.sp else 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
            Text(Money.rubles(plate.price), style = displayStyle(if (featured) 28 else 20, Color.hex(0x111111)), maxLines = 1)
            Text(
                buildAnnotatedString {
                    append("вместо ")
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(Money.rubles(plate.base)) }
                    append(" за место в туре")
                },
                color = Color.hex(0x5C5C5C),
                fontSize = if (featured) 11.sp else 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        Row(
            Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .padding(horizontal = 10.dp, vertical = if (featured) 8.dp else 6.dp),
        ) {
            Text(plate.mark, color = Color.White, fontSize = if (featured) 10.sp else 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            if (plate.until.isNotBlank()) Text(plate.until, color = Color.White, fontSize = if (featured) 10.sp else 8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

/// Catalogue price. A live sale keeps the old «от» struck through.
@Composable
fun SalePriceLabel(tour: Tour, onDark: Boolean = false, compact: Boolean = false, prominent: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            tour.priceText,
            style = if (prominent) displayStyle(24, if (onDark) Color.White else TnaColor.ink)
            else TextStyle(fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Bold, color = if (onDark) Color.White else TnaColor.ink),
            maxLines = 1,
        )
        if (tour.hasSale) {
            Spacer(Modifier.width(6.dp))
            Text(
                tour.basePriceText,
                color = if (onDark) Color.White.copy(alpha = 0.7f) else TnaColor.subtle,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(TnaColor.surface)
                .border(1.dp, TnaColor.stroke, CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(TnaColor.accent),
                // Results filter live; the keyboard's search key only puts it away.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("Куда хочешь? Байкал, лето, вулканы…", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Cancel, "Очистить", tint = TnaColor.subtle, modifier = Modifier.size(18.dp).pressable { onChange("") })
            }
        }
        // An explicit «hide keyboard» while typing: people look for a button first.
        AnimatedVisibility(focused, enter = fadeIn() + slideInHorizontally { it }, exit = fadeOut() + slideOutHorizontally { it }) {
            Box(
                Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(TnaColor.surface)
                    .border(1.dp, TnaColor.stroke, CircleShape).pressable(0.9f, onClick = onDone),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.KeyboardHide, "Скрыть клавиатуру", tint = TnaColor.ink, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun NoResults() {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Explore, null, tint = TnaColor.subtle, modifier = Modifier.size(34.dp))
        Gap(10)
        Text("Такого тура пока нет", color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "Но мы можем собрать его под тебя — жми на карточку ниже",
            color = TnaColor.subtle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IndividualTourCard(prod: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(TnaColor.ink).pressable(0.98f, onClick = onClick),
    ) {
        BrandMark(
            130,
            prod,
            Modifier.align(Alignment.BottomStart).offset(x = 130.dp, y = (-46).dp).rotate(-14f).alpha(0.25f),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#путешествуй_по_своим_правилам", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("ИНДИВИДУАЛЬНЫЙ ТУР", style = displayStyle(28, Color.White))
            Text(
                "Свой маршрут для себя, компании или корпоратива — выбери место, остальное организуем мы",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                Modifier.padding(top = 6.dp).clip(CircleShape).background(Color.White).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Подобрать авторский тур", color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TnaColor.ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}
