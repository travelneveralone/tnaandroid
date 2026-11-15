package com.travelneveralone.tna

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Port of TNAiOS App/ (RootView, SplashView, TabBarView) and Features/Onboarding.

@Composable
fun Root(app: AppModel) {
    LaunchedEffect(Unit) {
        // Network work runs beside the splash, never before it: the splash lives
        // exactly as long as its animation.
        app.boot()
        delay(2200)
        app.finishSplash()
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Box(Modifier.fillMaxSize().background(TnaColor.bg)) {
        if (app.onboarded) {
            when (app.tab) {
                AppTab.Tours -> CatalogScreen(app)
                AppTab.Chats -> ChatsScreen(app)
                AppTab.Passport -> PassportScreen(app)
                AppTab.Profile -> ProfileScreen(app)
            }
            val hideBar = app.openTour != null || app.openChat != null || app.showSplash || imeVisible || app.recapShown || app.tripModeShown || app.cropSource != null
            AnimatedVisibility(
                !hideBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { TabBar(app) }
        }
        AnimatedVisibility(!app.onboarded && !app.showSplash, enter = fadeIn(), exit = fadeOut()) {
            Onboarding(app.isProd) { app.finishOnboarding() }
        }
        app.openTour?.let { TourDetailScreen(app, it) }
        app.openChat?.let { ChatScreen(app, it) }
        // «Офлайн» = no network; «Нет связи» = network up, server silent (screens from cache).
        val notice = when {
            !app.network.isOnline -> "Офлайн" to Icons.Filled.WifiOff
            app.startupStalled -> "Нет связи" to Icons.Filled.SignalWifiBad
            else -> null
        }
        AnimatedVisibility(
            notice != null && !app.showSplash && app.openChat == null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            notice?.let { (title, icon) -> OfflineBanner(title, icon) }
        }
        AnimatedVisibility(app.showSplash, enter = fadeIn(), exit = fadeOut()) { Splash(app.isProd) }
        app.sheet?.let { sheet -> SheetHost(app, sheet) { app.sheet = null } }
        // Full-screen covers sit above tabs and sheets, like iOS fullScreenCover.
        val recap = app.recap
        if (app.recapShown && recap != null) YearRecapScreen(app, recap)
        if (app.tripModeShown) TripModeScreen(app)
        app.miniProfile?.let { MiniProfileSheet(app, it) }
        app.cropSource?.let { AvatarCropScreen(app, it) }
        app.avatarError?.let { message ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { app.avatarError = null },
                confirmButton = { androidx.compose.material3.TextButton({ app.avatarError = null }) { Text("OK") } },
                title = { Text("Ошибка") },
                text = { Text(message) },
                containerColor = TnaColor.bg,
            )
        }
    }
}

@Composable
private fun OfflineBanner(title: String, icon: ImageVector) {
    Row(
        Modifier.statusBarsPadding().padding(top = 4.dp).shadow(6.dp, CircleShape, ambientColor = TnaColor.ink, spotColor = TnaColor.ink)
            .clip(CircleShape).background(TnaColor.ink.copy(alpha = 0.9f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/// iOS SplashView: the mark spins in, the two lines rise out of a blur, the tag fades in.
@Composable
private fun Splash(prod: Boolean) {
    val mark = remember { Animatable(0f) }
    val line1 = remember { Animatable(0f) }
    val line2 = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch { mark.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f)) }
        scope.launch { delay(300); line1.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 150f)) }
        scope.launch { delay(500); line2.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 150f)) }
        scope.launch { delay(900); tag.animateTo(1f, tween(500)) }
    }
    Box(
        Modifier.fillMaxSize().background(TnaColor.ink).background(
            Brush.radialGradient(listOf(TnaColor.accent.copy(alpha = 0.25f), Color.Transparent), radius = 900f)
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(
                96,
                prod,
                Modifier.padding(bottom = 14.dp).scale(0.4f + 0.6f * mark.value).alpha(mark.value.coerceIn(0f, 1f)).rotate(-40f * (1 - mark.value)),
            )
            Text(
                "ПУТЕШЕСТВУЙ",
                style = displayStyle(50, Color.hex(Palette.bg)),
                modifier = Modifier.alpha(line1.value.coerceIn(0f, 1f)).offset(y = (24 * (1 - line1.value)).dp).blur((8 * (1 - line1.value)).coerceAtLeast(0f).dp),
            )
            Text(
                "НЕ ОДИН",
                style = displayBrushStyle(68),
                modifier = Modifier.alpha(line2.value.coerceIn(0f, 1f)).offset(y = (24 * (1 - line2.value)).dp).blur((8 * (1 - line2.value)).coerceAtLeast(0f).dp),
            )
            Text(
                "#tna_travel",
                color = Color.hex(Palette.bg).copy(alpha = 0.55f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp).alpha(tag.value),
            )
        }
    }
}

/// iOS OnboardingView: three swipeable pages, background changes per page.
@Composable
private fun Onboarding(prod: Boolean, onFinish: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val background = when (page) {
        0 -> Brush.linearGradient(listOf(TnaColor.ink, TnaColor.ink))
        1 -> Brush.linearGradient(listOf(Color.hex(0xFF6B3D), Color.hex(0x8B1E00)))
        else -> Brush.linearGradient(listOf(Color.hex(0x2C4A9F), Color.hex(0x16264F)))
    }
    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            when (index) {
                0 -> OnboardingPage(
                    top = { BrandMark(110, prod) },
                    line1 = "ПУТЕШЕСТВУЙ", line2 = "НЕ ОДИН",
                    caption = "Компания для путешествий есть всегда! Люди, объединённые интересом и открытостью к новому.",
                )
                1 -> OnboardingPage(
                    top = { Icon(Icons.Filled.Landscape, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(96.dp)) },
                    line1 = "14+", line2 = "НАПРАВЛЕНИЙ",
                    caption = "От льда Байкала до драконовых деревьев Сокотры. Всё продумано до мелочей — просто выбери тур.",
                    accent = Color.hex(0xF5C752),
                )
                else -> OnboardingPage(
                    top = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(96.dp)) },
                    line1 = "ПАСПОРТ", line2 = "ПУТЕШЕСТВЕННИКА",
                    caption = "Штампы за каждый тур, достижения, километры и рейтинг. Собери карту своих покорений.",
                )
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                val width by animateDpAsState(if (index == page) 22.dp else 7.dp, spring(dampingRatio = 0.7f), label = "dot")
                Box(Modifier.height(7.dp).width(width).clip(CircleShape).background(Color.White.copy(alpha = if (index == page) 1f else 0.3f)))
            }
        }
        Box(
            Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.hex(Palette.bg))
                .pressable { if (page < 2) scope.launch { pager.animateScrollToPage(page + 1) } else onFinish() },
            contentAlignment = Alignment.Center,
        ) { Text(if (page < 2) "Дальше" else "Поехали!", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Text(
            "Пропустить",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp, bottom = 8.dp).alpha(if (page < 2) 1f else 0f)
                .pressable(enabled = page < 2, onClick = onFinish),
        )
    }
}

@Composable
private fun OnboardingPage(top: @Composable () -> Unit, line1: String, line2: String, caption: String, accent: Color? = null) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.padding(bottom = 30.dp)) { top() }
        Text(line1, style = displayStyle(46, Color.White), textAlign = TextAlign.Center)
        Text(line2, style = if (accent != null) displayStyle(40, accent) else displayBrushStyle(40), textAlign = TextAlign.Center)
        Text(
            caption,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 14.dp),
        )
    }
}

/// iOS TabBarView: solid navy floating bar, the selected tab sits on a soft pill.
@Composable
private fun TabBar(app: AppModel) {
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(TnaColor.bg.copy(alpha = 0f), TnaColor.bg.copy(alpha = 0.75f))))
            .navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 4.dp),
    ) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = TnaColor.ink, spotColor = TnaColor.ink).clip(shape).background(TnaColor.ink)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape).padding(6.dp),
        ) {
            app.visibleTabs.forEach { tab ->
                val selected = app.tab == tab
                // accentSoft, not accent: the lighter orange keeps 5:1 contrast on navy.
                val tint by animateColorAsState(if (selected) TnaColor.accentSoft else Color.White.copy(alpha = 0.72f), label = "tint")
                val pill by animateColorAsState(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent, label = "pill")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(pill).pressable(0.92f) { app.tab = tab }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        Icon(tab.icon(), tab.title, tint = tint, modifier = Modifier.size(22.dp))
                        if (tab == AppTab.Chats && app.chatBadge > 0) {
                            Box(Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-7).dp)) { CountBadge(app.chatBadge, ring = TnaColor.ink) }
                        }
                    }
                    Text(tab.title, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.Tours -> Icons.Filled.Public
    AppTab.Chats -> Icons.AutoMirrored.Filled.Chat
    AppTab.Passport -> Icons.AutoMirrored.Filled.MenuBook
    AppTab.Profile -> Icons.Filled.AccountCircle
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(app: AppModel, sheet: Sheet, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // iOS uses the darker cream for request forms and feeds, the page cream elsewhere.
    val container = when (sheet) {
        is Sheet.Booking, Sheet.News, Sheet.Schedule, Sheet.About, Sheet.Notifications -> TnaColor.elevated
        else -> TnaColor.bg
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = container,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box(Modifier.navigationBarsPadding()) {
            when (sheet) {
                is Sheet.Booking -> BookingSheet(app)
                Sheet.Login -> LoginSheet(app)
                Sheet.News -> NewsSheet(app)
                Sheet.MyBookings -> BookingsSheet(app)
                Sheet.Terms -> TermsSheet()
                Sheet.Reviews -> ReviewsSheet(app)
                Sheet.Contacts -> ContactsSheet(app)
                Sheet.About -> AboutSheet(app)
                Sheet.Individual -> IndividualSheet(app)
                Sheet.Certificate -> CertificateSheet(app)
                Sheet.EditProfile -> EditProfileSheet(app)
                Sheet.Notifications -> NotificationsSheet(app)
                Sheet.Companions -> CompanionsSheet(app)
                Sheet.Countries -> CountriesSheet(app)
                Sheet.Compass -> CompassSheet(app)
                Sheet.Schedule -> ScheduleSheet(app)
                is Sheet.TripMode -> TripSheet(app, sheet.trip)
                is Sheet.Program -> ProgramSheet(app, sheet.tour, sheet.trip)
            }
        }
    }
}
