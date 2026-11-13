package com.travelneveralone.tna

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Root(app: AppModel) {
    LaunchedEffect(Unit) {
        delay(2200)
        app.finishSplash()
        app.boot()
    }
    Box(Modifier.fillMaxSize().background(TnaColor.bg)) {
        if (app.onboarded) {
            when (app.tab) {
                AppTab.Tours -> CatalogScreen(app)
                AppTab.Chats -> ChatsScreen(app)
                AppTab.Passport -> PassportScreen(app)
                AppTab.Profile -> ProfileScreen(app)
            }
            val hideBar = app.openTour != null || app.openChat != null
            if (!hideBar && !app.showSplash) {
                TabBar(app, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
            if (!app.network.isOnline && app.openChat == null && !app.showSplash) {
                Text(
                    if (app.startupStalled) "Сервер не ответил — показываем сохранённое" else "Нет сети — работаем офлайн",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .background(TnaColor.ink)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                )
            }
        }
        AnimatedVisibility(!app.onboarded && !app.showSplash, enter = fadeIn(), exit = fadeOut()) {
            Onboarding { app.finishOnboarding() }
        }
        AnimatedVisibility(app.showSplash, enter = fadeIn(), exit = fadeOut()) {
            Splash(app.isProd)
        }
        app.openTour?.let { TourDetailScreen(app, it) }
        app.openChat?.let { ChatScreen(app, it) }
        app.sheet?.let { sheet ->
            SheetHost(app, sheet) { app.sheet = null }
        }
    }
}

@Composable
private fun Splash(prod: Boolean) {
    Box(Modifier.fillMaxSize().background(TnaColor.ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(96, prod)
            Gap(18)
            Text("ПУТЕШЕСТВУЙ", style = displayStyle(46, Color.hex(Palette.bg)))
            Text("НЕ ОДИН", style = displayStyle(60, TnaColor.accent))
            Gap(12)
            Text("#tna_travel", color = Color.hex(Palette.bg).copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    val pages = listOf(
        Triple("ПУТЕШЕСТВУЙ", "НЕ ОДИН", "Компания для путешествий есть всегда! Люди, объединённые интересом и открытостью к новому."),
        Triple("14+", "НАПРАВЛЕНИЙ", "От льда Байкала до драконовых деревьев Сокотры. Всё продумано до мелочей — просто выбери тур."),
        Triple("ПАСПОРТ", "ПУТЕШЕСТВЕННИКА", "Штампы, километры и достижения. Каждая поездка остаётся с тобой."),
    )
    var page by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val bg = when (page) {
        0 -> TnaColor.ink
        1 -> Color.hex(0x8B1E00)
        else -> Color.hex(0x16264F)
    }
    Column(
        Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(pages[page].first, style = displayStyle(42, Color.White), textAlign = TextAlign.Center)
        Text(pages[page].second, style = displayStyle(52, if (page == 1) Color.hex(0xF5C752) else TnaColor.accent), textAlign = TextAlign.Center)
        Gap(16)
        Text(pages[page].third, color = Color.White.copy(0.8f), textAlign = TextAlign.Center, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .height(7.dp)
                        .width(if (index == page) 22.dp else 7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(if (index == page) 1f else 0.3f))
                )
            }
        }
        Gap(18)
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(TnaColor.bg).clickable {
                if (page < 2) page++ else onFinish()
            },
            contentAlignment = Alignment.Center,
        ) { Text(if (page < 2) "Дальше" else "Поехали!", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TnaColor.ink) }
        if (page < 2) {
            Text("Пропустить", color = Color.White.copy(0.6f), modifier = Modifier.padding(top = 12.dp).clickable(onClick = onFinish))
        }
    }
}

@Composable
private fun TabBar(app: AppModel, modifier: Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(TnaColor.ink)
            .padding(6.dp),
    ) {
        app.visibleTabs.forEach { tab ->
            val selected = app.tab == tab
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (selected) Color.White.copy(0.1f) else Color.Transparent)
                    .clickable { app.tab = tab }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Icon(tab.icon(), null, tint = if (selected) TnaColor.accentSoft else Color.White.copy(0.72f))
                    if (tab == AppTab.Chats && app.chatBadge > 0) {
                        Text(
                            if (app.chatBadge > 99) "99+" else "${app.chatBadge}",
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.hex(0xE0534E)).padding(horizontal = 4.dp),
                        )
                    }
                }
                Text(tab.title, color = if (selected) TnaColor.accentSoft else Color.White.copy(0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun AppTab.icon(): ImageVector = when (this) {
    AppTab.Tours -> Icons.Filled.Public
    AppTab.Chats -> Icons.AutoMirrored.Filled.Chat
    AppTab.Passport -> Icons.AutoMirrored.Filled.MenuBook
    AppTab.Profile -> Icons.Filled.Person
}

@Composable
fun CatalogScreen(app: AppModel) {
    val chips = CatalogFilter.available(app.tours)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(TnaColor.bg).padding(horizontal = 16.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(44, app.isProd)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ПУТЕШЕСТВУЙ", style = displayStyle(22))
                        Text("НЕ ОДИН", style = displayStyle(28, TnaColor.accent))
                    }
                    Text("новости", color = TnaColor.ink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        app.loadNews(); app.sheet = Sheet.News
                    })
                }
                Gap(18)
                when (app.catalogState) {
                    "loading" -> Text("Собираем маршруты…", color = TnaColor.subtle)
                    "failed" -> Column {
                        Text(app.catalogError ?: "Не вышло загрузить каталог", color = TnaColor.ink)
                        Gap(8)
                        PrimaryButton("Повторить") { app.loadCatalog() }
                    }
                }
                app.campaign?.takeIf { it.showsBanner }?.let { offer ->
                    Card {
                        Text(offer.title.ifBlank { offer.name }, fontWeight = FontWeight.Bold, color = TnaColor.ink, fontSize = 18.sp)
                        if (offer.text.isNotBlank()) Text(offer.text, color = TnaColor.subtle)
                        offer.until?.let { Text("до ${BonusCopy.shortDate(it)}", color = TnaColor.accent, fontWeight = FontWeight.SemiBold) }
                    }
                    Gap(12)
                }
                if (app.search.isBlank() && app.catalogState == "loaded") {
                    CompassBanner { app.sheet = Sheet.Compass }
                    Gap(12)
                    if (app.tours.isNotEmpty()) {
                        Text("БЛИЖАЙШИЕ ЗАЕЗДЫ", style = displayStyle(20))
                        Gap(8)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            app.tours.take(4).forEach { tour ->
                                PosterCard(
                                    tour.name, tour.tagline, tour.priceText, tour.colors, tour.imageUrl, tour.symbol,
                                    badge = tour.departures.firstOrNull()?.let { if (it.spotsLeft in 1..4) "осталось ${it.spotsLeft}" else null },
                                    modifier = Modifier.width(260.dp),
                                ) { app.openTour = tour }
                            }
                        }
                        Gap(16)
                    }
                }
                SearchField(app.search) { app.search = it }
                if (chips.size > 1) {
                    Gap(10)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { chip -> Chip(chip.title, chip == app.filter) { app.filter = chip } }
                    }
                }
                Gap(8)
                Row {
                    Text("РАСПИСАНИЕ", color = TnaColor.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                        app.loadSchedule(); app.sheet = Sheet.Schedule
                    })
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (app.catalogState == "loaded") {
            items(app.filteredTours, key = { it.backendId ?: it.id }) { tour ->
                PosterCard(tour.name, tour.seasonText.ifBlank { tour.tagline }, tour.priceText, tour.colors, tour.imageUrl, tour.symbol, modifier = Modifier.height(210.dp)) {
                    app.openTour = tour
                }
            }
            if (app.role.canBookAndEarn) {
                item(span = { GridItemSpan(2) }) {
                    Card(Modifier.clickable { app.sheet = Sheet.Individual }) {
                        Text("СВОЙ МАРШРУТ", style = displayStyle(22))
                        Text("Индивидуальный тур: даты, темп и компания — под тебя.", color = TnaColor.subtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassBanner(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TnaColor.ink).clickable(onClick = onClick).padding(18.dp)
    ) {
        Text("TNA КОМПАС", style = displayStyle(26, Color.White))
        Text("30 секунд — и маршрут, который тебе подходит", color = Color.White.copy(0.75f))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = TnaColor.ink, fontSize = 16.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text("Куда хочется?", color = TnaColor.subtle)
            inner()
        },
    )
}

@Composable
fun TourDetailScreen(app: AppModel, tour: Tour) {
    LaunchedEffect(tour.backendId) { tour.backendId?.let { app.loadReviews(it) } }
    Column(Modifier.fillMaxSize().background(TnaColor.bg).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            Cover(tour.heroImages.firstOrNull(), tour.colors, Modifier.fillMaxSize())
            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.statusBarsPadding().padding(16.dp).clickable { app.openTour = null })
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(tour.hashtag, color = Color.White.copy(0.8f), fontWeight = FontWeight.SemiBold)
                Text(tour.name.uppercase(), style = displayStyle(40, Color.White))
                Text(tour.tagline, color = Color.White)
            }
        }
        Column(Modifier.padding(20.dp)) {
            Text(tour.priceText, style = displayStyle(32, TnaColor.accent))
            if (tour.hasSale) Text(tour.basePriceText, color = TnaColor.subtle)
            Gap(8)
            Text("${tour.days} ${pluralRU(tour.days, "день", "дня", "дней")} · ${Money.grouped(tour.km)} км · сложность ${tour.difficulty}/5", color = TnaColor.subtle)
            if (tour.seasonText.isNotBlank()) Text(tour.seasonText, color = TnaColor.ink, fontWeight = FontWeight.SemiBold)
            Gap(16)
            Text("ЗАЕЗДЫ", style = displayStyle(22))
            Gap(8)
            if (tour.departures.isEmpty()) {
                Text("Ближайших дат нет — можно запросить свои.", color = TnaColor.subtle)
            }
            tour.departures.forEach { dep ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable {
                        if (app.role.canBookAndEarn) app.beginBooking(tour, dep)
                    }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(dep.label, fontWeight = FontWeight.Bold, color = TnaColor.ink)
                        Text(if (dep.isSoldOut) "Лист ожидания" else "осталось ${dep.spotsLeft} ${pluralRU(dep.spotsLeft, "место", "места", "мест")}", color = TnaColor.subtle, fontSize = 13.sp)
                    }
                    if (app.role.canBookAndEarn) Text(if (dep.isSoldOut) "В лист" else "Бронь", color = TnaColor.accent, fontWeight = FontWeight.Bold)
                }
            }
            if (app.role.canBookAndEarn) {
                Gap(8)
                PrimaryButton("Свои даты") { app.beginBooking(tour, null) }
            }
            if (tour.program.isNotEmpty()) {
                Gap(20)
                Text("ПРОГРАММА", style = displayStyle(22))
                tour.program.forEach { day ->
                    Gap(10)
                    Text("День ${day.day}. ${day.title}", fontWeight = FontWeight.Bold, color = TnaColor.ink)
                    Text(day.details, color = TnaColor.subtle)
                }
            }
            if (tour.includes.isNotEmpty()) {
                Gap(20)
                Text("ВКЛЮЧЕНО", style = displayStyle(22))
                tour.includes.forEach { Text("· $it", color = TnaColor.ink, modifier = Modifier.padding(top = 4.dp)) }
            }
            Gap(20)
            Text("ОТЗЫВЫ", style = displayStyle(22))
            val mine = app.reviews
            if (mine.isEmpty()) Text("Пока нет опубликованных отзывов", color = TnaColor.subtle, modifier = Modifier.padding(top = 8.dp))
            mine.take(6).forEach { review ->
                Gap(10)
                Card {
                    Row { Text(review.author, fontWeight = FontWeight.Bold, color = TnaColor.ink); Spacer(Modifier.weight(1f)); Stars(review.rating) }
                    Text(review.text, color = TnaColor.ink)
                    review.reply?.let { Text("TNA: $it", color = TnaColor.subtle, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

@Composable
fun ChatsScreen(app: AppModel) {
    LaunchedEffect(app.tab) { if (app.tab == AppTab.Chats) app.loadChats() }
    LazyColumn(Modifier.fillMaxSize().background(TnaColor.bg), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Text("ЧАТЫ", style = displayStyle(36), modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
        }
        val visible = app.threads.filter { !it.archived }
        items(visible.size) { index ->
            val thread = visible[index]
            Row(
                Modifier.fillMaxWidth().clickable { app.openThread(thread) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(
                        androidx.compose.ui.graphics.Brush.linearGradient(thread.colors.map { Color.hex(it) })
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text(symbolEmoji(thread.icon), fontSize = 22.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(thread.title, fontWeight = FontWeight.Bold, color = TnaColor.ink, maxLines = 1)
                    Text(thread.lastMessage.ifBlank { thread.subtitle }, color = TnaColor.subtle, maxLines = 1, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(thread.time, color = TnaColor.subtle, fontSize = 12.sp)
                    if (thread.unread > 0) {
                        Text("${thread.unread}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(TnaColor.accent).padding(horizontal = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(app: AppModel, thread: ChatThread) {
    LaunchedEffect(thread.id) {
        while (true) {
            delay(4000)
            val fresh = runCatching { app.repo.history(thread.id) }.getOrNull() ?: continue
            val pending = app.messages.filter { it.status != MessageStatus.Sent && fresh.none { row -> row.id == it.id } }
            if (fresh.isNotEmpty() || pending.isNotEmpty()) app.messages = fresh + pending
        }
    }
    Column(Modifier.fillMaxSize().background(TnaColor.bg)) {
        Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 24.sp, modifier = Modifier.clickable { app.openChat = null }, color = TnaColor.ink)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(thread.title, fontWeight = FontWeight.Bold, color = TnaColor.ink)
                Text(thread.subtitle, color = TnaColor.subtle, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(app.messages.size) { index ->
                val message = app.messages[index]
                val mine = message.isMine
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    if (!mine && thread.kind != ChatThreadKind.Support) {
                        Text(message.author, color = TnaColor.subtle, fontSize = 11.sp)
                    }
                    Text(
                        message.text.ifBlank { message.kind.name },
                        color = if (mine) Color.White else TnaColor.ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (mine) TnaColor.ink else Color.White)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    val mark = when (message.status) {
                        MessageStatus.Queued -> "ждёт сеть"
                        MessageStatus.Failed -> "не ушло"
                        else -> message.time
                    }
                    Text(mark, color = TnaColor.subtle, fontSize = 10.sp)
                }
            }
        }
        Row(Modifier.navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = app.chatDraft,
                onValueChange = { app.chatDraft = it },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = TnaColor.ink),
                decorationBox = { inner ->
                    if (app.chatDraft.isEmpty()) Text("Сообщение", color = TnaColor.subtle)
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            Text("➤", color = Color.White, fontSize = 20.sp, modifier = Modifier.clip(CircleShape).background(TnaColor.accent).padding(12.dp).clickable { app.sendChat() })
        }
    }
}

@Composable
fun PassportScreen(app: AppModel) {
    LaunchedEffect(Unit) { app.loadPassport() }
    val data = app.passport
    LazyColumn(Modifier.fillMaxSize().background(TnaColor.bg), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                Text("ПАСПОРТ", style = displayStyle(36))
                Text(data.userName, style = displayStyle(28, TnaColor.accent))
                Text(data.memberSince, color = TnaColor.subtle)
                Gap(8)
                Text(data.level, fontWeight = FontWeight.Bold, color = TnaColor.ink, fontSize = 18.sp)
                Text(data.nextLevelHint, color = TnaColor.subtle)
                Gap(16)
            }
        }
        items(data.stats.size) { index ->
            val stat = data.stats[index]
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp)) {
                Text(Money.grouped(stat.value) + if (stat.unit.isBlank()) "" else " ${stat.unit}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TnaColor.ink)
                Spacer(Modifier.weight(1f))
                Text(stat.title, color = TnaColor.subtle)
            }
        }
        item { Gap(12); Text("ШТАМПЫ", style = displayStyle(22), modifier = Modifier.padding(horizontal = 20.dp)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                data.stamps.forEach { stamp ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(92.dp).clip(CircleShape).background(Color.hex(stamp.color).copy(0.15f)).padding(8.dp)) {
                        Text(stamp.title, fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.hex(stamp.color), textAlign = TextAlign.Center)
                        Text(stamp.year, fontSize = 11.sp, color = TnaColor.subtle)
                    }
                }
            }
        }
        item { Text("ДОСТИЖЕНИЯ", style = displayStyle(22), modifier = Modifier.padding(horizontal = 20.dp)) }
        items(data.achievements.size) { index ->
            val item = data.achievements[index]
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (item.earned) Color.White else TnaColor.elevated).padding(14.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold, color = if (item.earned) TnaColor.ink else TnaColor.subtle)
                Text(item.details, color = TnaColor.subtle, fontSize = 13.sp)
                if (!item.earned && item.howTo.isNotBlank()) Text(item.howTo, color = TnaColor.subtle, fontSize = 12.sp)
                item.earnedDate?.let { Text(it, color = TnaColor.accent, fontSize = 12.sp) }
            }
        }
        item { Gap(8); Text("РЕЙТИНГ", style = displayStyle(22), modifier = Modifier.padding(horizontal = 20.dp)) }
        items(data.leaderboard.size) { index ->
            val row = data.leaderboard[index]
            Text(
                "${row.rank}. ${row.name} · ${Money.grouped(row.km)} км · ${row.tours}",
                color = if (row.isMe) TnaColor.accent else TnaColor.ink,
                fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ProfileScreen(app: AppModel) {
    LaunchedEffect(app.account.isRegistered) { app.loadProfileBits() }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(TnaColor.bg).verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        Text("ПРОФИЛЬ", style = displayStyle(36), modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(if (app.account.isRegistered) app.account.name.ifBlank { "Путешественник" } else "Гость", style = displayStyle(28))
            Text(if (app.account.isRegistered) app.account.phone.ifBlank { "телефон подтверждён" } else "Войди, чтобы бронировать и копить штампы", color = TnaColor.subtle)
            if (!app.account.isRegistered) {
                Gap(12)
                PrimaryButton("Войти по телефону") { app.loginError = null; app.sheet = Sheet.Login }
            }
        }
        if (app.account.isRegistered && app.role.hasTravelProfile) {
            app.trips.filter { it.isUpcoming }.take(1).forEach { trip ->
                Gap(16)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(if (trip.countdown == "в пути") "СЕЙЧАС В ПУТИ" else "БЛИЖАЙШАЯ ПОЕЗДКА", style = displayStyle(20))
                    Gap(8)
                    PosterCard(trip.tourName, trip.label, trip.countdown, trip.colors, trip.imageUrl, trip.symbol) {
                        app.sheet = Sheet.TripMode(trip)
                    }
                }
            }
        }
        if (app.role.canBookAndEarn && app.account.isRegistered) {
            Gap(16)
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionTitle("Зови друзей", "#не_один_же")
                Gap(8)
                Card {
                    Text(app.referralCode.ifBlank { "код появится после входа" }, style = displayStyle(28, TnaColor.accent))
                    app.bonus?.profileLine?.let { Text(it, color = TnaColor.ink) }
                    app.bonus?.expiresLine?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp) }
                    Text("Друг вводит код при первой заявке — вам обоим бонусы.", color = TnaColor.subtle, fontSize = 13.sp)
                }
            }
        }
        Gap(8)
        val rows = buildList {
            add("Уведомления" to { app.sheet = Sheet.Notifications })
            if (app.role.hasTravelProfile) {
                add("Мои брони и оплаты" to { app.loadBookings(); app.sheet = Sheet.MyBookings })
                add("Попутчики" to { app.sheet = Sheet.Companions })
                add("Мои страны" to { app.loadCountries(); app.sheet = Sheet.Countries })
            }
            add("Условия бронирования" to { app.sheet = Sheet.Terms })
            add("Пользовательское соглашение" to {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://travelneveralone.com")))
            })
            add("Политика конфиденциальности" to {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://travelneveralone.com")))
            })
            add("Отзывы путешественников" to { app.loadReviews(null); app.sheet = Sheet.Reviews })
            if (app.role.canBookAndEarn) add("Подарочные сертификаты" to { app.sheet = Sheet.Certificate })
            add("О нас" to { app.sheet = Sheet.About })
            add("Контакты" to { app.sheet = Sheet.Contacts })
        }
        rows.forEach { (title, action) ->
            SettingsRow(title, onClick = action)
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(TnaColor.stroke))
        }
        if (app.account.isRegistered) {
            Gap(16)
            Text("Выйти", color = Color.hex(0xE0534E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp).clickable { app.signOut() })
            Gap(12)
            Text("Удалить аккаунт", color = TnaColor.subtle, modifier = Modifier.padding(horizontal = 20.dp).clickable { app.deleteAccount() })
        }
        Gap(24)
        Text("Travel Never Alone", color = TnaColor.subtle, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(app: AppModel, sheet: Sheet, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = TnaColor.bg) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
            when (sheet) {
                is Sheet.Booking -> BookingSheet(app)
                Sheet.Login -> LoginSheet(app)
                Sheet.News -> NewsSheet(app)
                Sheet.MyBookings -> BookingsSheet(app)
                Sheet.Terms -> TermsSheet()
                Sheet.Reviews -> ReviewsSheet(app)
                Sheet.Contacts -> ContactsSheet()
                Sheet.About -> AboutSheet(app)
                Sheet.Individual -> IndividualSheet(app)
                Sheet.Certificate -> CertificateSheet()
                Sheet.Notifications -> NotificationsSheet(app)
                Sheet.Companions -> CompanionsSheet(app)
                Sheet.Countries -> CountriesSheet(app)
                Sheet.Compass -> CompassSheet(app)
                Sheet.Schedule -> ScheduleSheet(app)
                is Sheet.TripMode -> TripSheet(app, sheet.trip)
                is Sheet.Program -> ProgramSheet(sheet.tour)
            }
        }
    }
}
