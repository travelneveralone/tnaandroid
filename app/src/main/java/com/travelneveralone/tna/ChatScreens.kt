package com.travelneveralone.tna

import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Download
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// Port of TNAiOS Features/Chats (ChatsView, ChatView, Views/ChatComponents).

// MARK: - Thread list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(app: AppModel) {
    LaunchedEffect(app.tab, app.account.isRegistered) { if (app.tab == AppTab.Chats) app.loadChats() }
    // The list has no realtime subscription: poll while it is on screen (iOS: 15 s).
    LaunchedEffect(app.tab) {
        while (app.tab == AppTab.Chats) {
            delay(15_000)
            if (app.openChat == null) app.loadChats()
        }
    }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var archiveShown by remember { mutableStateOf(false) }
    val visible = app.threads.filter { !it.archived && it.belongsInChatList }
    val archived = app.threads.filter { it.archived && it.belongsInChatList }
    // Support is reachable 24/7: the real thread once loaded, the placeholder before that.
    val support = visible.firstOrNull { it.isSupport } ?: ChatThread.supportPlaceholder
    val trips = visible.filter { !it.isSupport && it.kind != ChatThreadKind.Direct }.sortedByDescending { it.lastActivity }
    val direct = visible.filter { !it.isSupport && it.kind == ChatThreadKind.Direct }.sortedByDescending { it.lastActivity }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; app.loadChats().join(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(TnaColor.bg),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.statusBarsPadding().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ЧАТЫ", style = displayStyle(40), modifier = Modifier.weight(1f))
                    // Archive lives as an icon in the corner, not a fold at the bottom of the list.
                    if (archived.isNotEmpty()) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, CircleShape)
                                .pressable(0.9f) { archiveShown = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Archive, "Архив чатов", tint = TnaColor.subtle, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            item { ThreadButton(app, support) }
            if (trips.isNotEmpty()) {
                item { ChatSectionTitle("Заезды") }
                items(trips, key = { it.id }) { ThreadButton(app, it) }
            }
            if (direct.isNotEmpty()) {
                item { ChatSectionTitle("Личные сообщения") }
                items(direct, key = { it.id }) { ThreadButton(app, it) }
            }
        }
    }
    if (archiveShown) {
        ModalBottomSheet(onDismissRequest = { archiveShown = false }, containerColor = TnaColor.bg) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Архив", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Готово", color = TnaColor.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { archiveShown = false })
                }
                if (archived.isEmpty()) {
                    Text("Архив пуст", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 40.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                }
                archived.forEach { thread -> ThreadButton(app, thread) { archiveShown = false } }
            }
        }
    }
}

@Composable
private fun ChatSectionTitle(title: String) {
    Text(
        title.uppercase(),
        color = TnaColor.subtle,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

/// Row + long-press menu (mute / archive); support has no menu.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadButton(app: AppModel, thread: ChatThread, beforeOpen: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ThreadRow(
            thread,
            Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { if (!thread.isSupport) menu = true },
            ) { beforeOpen(); app.openThread(thread) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = TnaColor.surface) {
            DropdownMenuItem(
                text = { Text(if (thread.muted) "Включить уведомления" else "Заглушить") },
                leadingIcon = { Icon(if (thread.muted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, null) },
                onClick = { menu = false; app.toggleMute(thread) },
            )
            DropdownMenuItem(
                text = { Text(if (thread.archived) "Вернуть из архива" else "В архив") },
                leadingIcon = { Icon(if (thread.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, null) },
                onClick = { menu = false; app.toggleArchive(thread) },
            )
        }
    }
}

@Composable
fun ThreadRow(thread: ChatThread, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            ThreadAvatar(thread, 52)
            if (thread.isSupport) {
                // «Online» dot: support is always reachable.
                Box(
                    Modifier.align(Alignment.BottomEnd).size(13.dp).clip(CircleShape).background(TnaColor.surface).padding(2.5.dp)
                        .clip(CircleShape).background(Color.hex(0x4CD964))
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(thread.title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (thread.isPinned && !thread.isSupport) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.PushPin, null, tint = TnaColor.subtle, modifier = Modifier.size(11.dp))
                }
                if (thread.muted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.NotificationsOff, null, tint = TnaColor.subtle, modifier = Modifier.size(11.dp))
                }
            }
            Text(thread.lastMessage.ifBlank { thread.subtitle }, color = TnaColor.subtle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (thread.time.isNotBlank()) Text(thread.time, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            // The list shows only "there is something new" - a dot, not a count.
            if (thread.unread > 0) Box(Modifier.size(11.dp).clip(CircleShape).background(TnaColor.accent))
        }
    }
}

@Composable
fun ThreadAvatar(thread: ChatThread, size: Int) {
    if (thread.kind == ChatThreadKind.Direct) {
        val color = authorColor(thread.title)
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(thread.title.trim().take(1).uppercase().ifEmpty { "?" }, color = color, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.ExtraBold)
            if (!thread.avatarUrl.isNullOrBlank()) {
                AsyncImage(model = thread.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        return
    }
    val colors = thread.colors.map { Color.hex(it) }.let { if (it.size >= 2) it else listOf(TnaColor.accentSoft, TnaColor.accent) }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfIcon(thread.icon), null, tint = Color.White, modifier = Modifier.size((size * 0.42f).dp))
        if (!thread.coverUrl.isNullOrBlank()) {
            AsyncImage(model = thread.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/// Stable per-name colour for author labels and initials (iOS `MessageBubble.authorColor`).
fun authorColor(name: String): Color {
    val palette = listOf(0xE8763FL, 0x3A5BB0L, 0x2F9E73L, 0xC2921CL, 0xB0468AL, 0x6C56C4L)
    val sum = name.sumOf { it.code }
    return Color.hex(palette[sum % palette.size])
}

// MARK: - Conversation

@Composable
fun ChatScreen(app: AppModel, thread: ChatThread) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: run { app.chatNotice = "Не удалось прочитать файл"; return@rememberLauncherForActivityResult }
        app.sendImage(bytes)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: run { app.chatNotice = "Не удалось прочитать файл"; return@rememberLauncherForActivityResult }
        val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "Файл"
        val type = resolver.getType(uri) ?: "application/octet-stream"
        val ext = name.substringAfterLast('.', "bin").lowercase()
        app.sendAttachment(bytes, MessageKind.File, ext, type, name)
    }
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start() else app.chatNotice = "Доступ к микрофону запрещён - включается в настройках Android."
    }
    var attachMenu by remember { mutableStateOf(false) }
    var composingPoll by remember { mutableStateOf(false) }
    var sharingLocation by remember { mutableStateOf(false) }
    // The chat map: opened on a location bubble (focus) or from the live banner (no focus).
    var mapOpen by remember { mutableStateOf(false) }
    var mapFocus by remember { mutableStateOf<Message?>(null) }
    val authorName: (Message) -> String = { ChatNames.author(it, app.participant(it.authorId)) }
    var ticketData by remember { mutableStateOf(false) }
    var pinIndex by remember(thread.id) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var menuFor by remember { mutableStateOf<UUID?>(null) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    BackHandler { app.cancelCompose(); app.openChat = null }
    // Poll the open chat (Android has no realtime socket yet); keep unsent messages.
    LaunchedEffect(thread.id) {
        while (true) {
            delay(4000)
            val fresh = runCatching { app.repo.history(thread.id) }.getOrNull() ?: continue
            val pending = app.messages.filter { it.status != MessageStatus.Sent && fresh.none { row -> row.id == it.id } }
            app.messages = fresh + pending
        }
    }
    val messages = if (app.blocked.isEmpty()) app.messages else app.messages.filter { it.authorId == null || it.isMine || it.authorId !in app.blocked }
    val showsAvatars = thread.kind == ChatThreadKind.Trip || thread.kind == ChatThreadKind.Community
    Column(Modifier.fillMaxSize().background(TnaColor.bg).imePadding()) {
        ChatHeader(app, thread)
        if (app.pinned.isNotEmpty()) {
            val pins = app.pinned
            val active = pinIndex.coerceIn(0, pins.lastIndex)
            PinnedBar(
                pins = pins,
                activeIndex = active,
                staff = app.chatStaff,
                onTap = {
                    // Jump to the pin (newest first in the list), then show the next one.
                    val target = pins[active]
                    val rows = messages.albumGrouped().asReversed()
                    val index = rows.indexOfFirst { group -> group.any { it.id == target.id } }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    pinIndex = (active + 1) % pins.size
                },
                onUnpin = { app.setPinned(it, false) },
            )
        }
        if (!app.network.isOnline) {
            Text("Нет сети - сообщения отправятся автоматически", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(TnaColor.accent).padding(vertical = 6.dp))
        }
        app.chatNotice?.let { notice ->
            LaunchedEffect(notice) { delay(3500); app.chatNotice = null }
            Text(notice, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().background(TnaColor.ink.copy(alpha = 0.9f)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyChatState(thread)
            } else {
                val rows = messages.albumGrouped()
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val reversed = rows.asReversed()
                    items(reversed.size, key = { reversed[it].last().id }) { index ->
                        val group = reversed[index]
                        val message = group.last()
                        val newer = reversed.getOrNull(index - 1)?.first()
                        val older = reversed.getOrNull(index + 1)?.last()
                        val first = older == null || !sameGroup(older, group.first())
                        val last = newer == null || !sameGroup(message, newer)
                        val member = app.participant(message.authorId)
                        val shownName = ChatNames.author(message, member)
                        val author = if (!thread.isSupport && !message.isMine && first) shownName else null
                        val roleTag = if (author != null && !message.asCompany) member?.roleTag else null
                        val openAuthor = {
                            message.authorId?.takeIf { !message.isMine && !message.asCompany && !thread.isSupport }?.let { uid ->
                                app.openMiniProfile(MiniProfile(uid, shownName, member?.city.orEmpty(), member?.role ?: "client", message.authorAvatarUrl, member?.isAdmin == true))
                            }
                            Unit
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (showsAvatars && !message.isMine) {
                                Box(Modifier.width(32.dp).pressable(0.9f, onClick = openAuthor)) {
                                    if (last) Avatar(message.authorAvatarUrl, shownName, 28)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Box(Modifier.weight(1f)) {
                                if (group.size >= 2) {
                                    AlbumBubble(group, author, roleTag) { i -> viewer = group.mapNotNull { it.mediaUrl } to i }
                                } else if (message.kind == MessageKind.Poll && message.poll != null) {
                                    PollBubble(app, message) { menuFor = message.id }
                                } else if (message.kind == MessageKind.Location && message.location != null) {
                                    LocationBubble(
                                        message = message,
                                        canStop = message.isMine && LiveLocationHub.isSharing(message.id),
                                        onOpen = { mapFocus = message; mapOpen = true },
                                        onStop = { app.stopLiveLocation(message.id) },
                                        onLongPress = { menuFor = message.id },
                                    )
                                } else {
                                    MessageBubble(
                                        message = message,
                                        authorName = author,
                                        roleTag = roleTag,
                                        onAuthorTap = openAuthor,
                                        quoted = message.replyTo?.let { id -> messages.firstOrNull { it.id == id } },
                                        first = first,
                                        onLongPress = { menuFor = message.id },
                                        onReact = { app.react(message.id, it) },
                                        onImageTap = {
                                            val urls = messages.filter { it.kind == MessageKind.Image }.mapNotNull { it.mediaUrl }
                                            viewer = urls to urls.indexOf(message.mediaUrl).coerceAtLeast(0)
                                        },
                                        onRetry = { app.retry(message) },
                                    )
                                }
                                if (menuFor == message.id) {
                                    MessageMenu(app, message, thread, context) { menuFor = null }
                                }
                            }
                        }
                    }
                }
            }
        }
        val mentions = if (thread.kind == ChatThreadKind.Trip) {
            ChatNames.mentionMatches(ChatNames.mentionQuery(app.chatDraft), app.participants, app.myUserId)
        } else emptyList()
        if (mentions.isNotEmpty() && !recording) {
            MentionBar(mentions) { app.chatDraft = ChatNames.insertMention(app.chatDraft, it.name) }
        }
        LiveLocationBanner(app, app.openChat?.id ?: thread.id, authorName) { mapFocus = null; mapOpen = true }
        Box {
            InputBar(
                app,
                recording = recording,
                onAttach = { attachMenu = true },
                onMic = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) recording = recorder.start() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                onCancelRecording = { recorder.cancel(); recording = false },
                onSendRecording = {
                    recording = false
                    recorder.stop()?.let { (file, ms) ->
                        val bytes = file.readBytes()
                        app.sendAttachment(bytes, MessageKind.Audio, "m4a", "audio/m4a", null, ms)
                        file.delete()
                    }
                },
            )
            DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, containerColor = TnaColor.surface) {
                DropdownMenuItem(text = { Text("Фото") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { attachMenu = false; picker.launch("image/*") })
                DropdownMenuItem(text = { Text("Файл") }, leadingIcon = { Icon(Icons.Filled.AttachFile, null) }, onClick = { attachMenu = false; filePicker.launch("*/*") })
                if (thread.kind == ChatThreadKind.Trip) {
                    DropdownMenuItem(text = { Text("Опрос") }, leadingIcon = { Icon(Icons.Filled.Poll, null) }, onClick = { attachMenu = false; composingPoll = true })
                }
                // Location in any chat: a one-off point or a live location.
                DropdownMenuItem(text = { Text("Геопозиция") }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) }, onClick = { attachMenu = false; sharingLocation = true })
                // Passenger data (own profile + companions) goes to the manager - support chat only.
                if (thread.isSupport && app.account.isRegistered) {
                    DropdownMenuItem(text = { Text("Данные для билета") }, leadingIcon = { Icon(Icons.Filled.Badge, null) }, onClick = {
                        attachMenu = false
                        ticketData = true
                    })
                }
            }
        }
    }
    if (ticketData) {
        TicketDataSheet(app, onDismiss = { ticketData = false }) { chosen ->
            val text = CompanionFormat.message(chosen)
            if (text.isNotEmpty()) {
                val draft = app.chatDraft
                app.chatDraft = text
                app.sendChat()
                app.chatDraft = draft
            }
        }
    }
    viewer?.let { (urls, start) -> ImageViewerDialog(urls, start) { viewer = null } }
    if (app.participantsShown) ParticipantsSheet(app)
    if (composingPoll) PollComposerSheet(onDismiss = { composingPoll = false }) { app.createPoll(it) }
    if (sharingLocation) LocationShareSheet(app) { sharingLocation = false }
    if (mapOpen) ChatMapDialog(app, mapFocus, authorName) { mapOpen = false }
}

private fun sameGroup(a: Message, b: Message): Boolean =
    a.isMine == b.isMine && a.authorId == b.authorId && a.asCompany == b.asCompany &&
        java.time.Duration.between(a.createdAt, b.createdAt).abs().toMinutes() < 5

@Composable
private fun ChatHeader(app: AppModel, thread: ChatThread) {
    Column(Modifier.fillMaxWidth().background(TnaColor.bg.copy(alpha = 0.97f))) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f) { app.cancelCompose(); app.openChat = null },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = TnaColor.ink, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            // Group: member count, opens the participant list; 1:1: opens the other person's card.
            val count = app.participants.size
            val subtitle = if (thread.kind == ChatThreadKind.Trip) {
                if (count > 0) "$count ${pluralRU(count, "участник", "участника", "участников")}" else "участники · нажми"
            } else thread.subtitle
            val opens = thread.kind == ChatThreadKind.Trip || (thread.kind == ChatThreadKind.Direct && thread.otherUserId != null)
            Column(
                Modifier.weight(1f).pressable(0.99f, enabled = opens) {
                    if (thread.kind == ChatThreadKind.Trip) app.participantsShown = true
                    else thread.otherUserId?.let { app.openMiniProfile(it) }
                },
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(thread.title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!thread.isSupport) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f) { app.toggleMute(thread) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (thread.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                        if (thread.muted) "Включить уведомления" else "Заглушить",
                        tint = if (thread.muted) TnaColor.subtle else TnaColor.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            ThreadAvatar(thread, 40)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
    }
}

@Composable
private fun EmptyChatState(thread: ChatThread) {
    val icon = when (thread.kind) {
        ChatThreadKind.Support -> sfIcon("headset")
        ChatThreadKind.Trip -> sfIcon(thread.icon)
        else -> sfIcon("bubble")
    }
    val title = if (thread.kind == ChatThreadKind.Trip) "В чате заезда пока тихо" else "Сообщений пока нет"
    val hint = when (thread.kind) {
        ChatThreadKind.Support -> "Спроси о туре, датах или брони - ответим в рабочее время"
        ChatThreadKind.Trip -> "Напиши первым - здесь вся группа и гид"
        ChatThreadKind.Direct -> "Напиши первым - эту переписку больше никто не видит"
        ChatThreadKind.Community -> "Напиши первым"
    }
    val tint = thread.colors.map { Color.hex(it) }.let { if (it.size >= 2) it else listOf(TnaColor.accent, TnaColor.accent) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(tint.map { it.copy(alpha = 0.16f) })), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TnaColor.subtle, modifier = Modifier.size(32.dp))
        }
        Gap(14)
        Text(title, color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Gap(6)
        Text(hint, color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/// Speech bubble: gradient for mine, white for others; the tail corner is tight.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    authorName: String?,
    roleTag: String? = null,
    onAuthorTap: () -> Unit = {},
    quoted: Message?,
    first: Boolean,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onImageTap: () -> Unit,
    onRetry: () -> Unit,
) {
    val mine = message.isMine
    val big = 18.dp
    val small = 5.dp
    val shape = if (mine) {
        RoundedCornerShape(topStart = big, topEnd = if (first) big else small, bottomEnd = small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (first) big else small, topEnd = big, bottomEnd = big, bottomStart = small)
    }
    val isImage = message.kind == MessageKind.Image
    val ink = if (mine) Color.White else TnaColor.ink
    Row(Modifier.fillMaxWidth().padding(top = if (first) 6.dp else 0.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (mine) Spacer(Modifier.width(56.dp))
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (mine) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.surface, TnaColor.surface)))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onLongPress,
                ) { if (message.status == MessageStatus.Failed) onRetry() else if (isImage) onImageTap() }
                .padding(horizontal = if (isImage) 4.dp else 12.dp, vertical = if (isImage) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (authorName != null) {
                Row(
                    Modifier.padding(horizontal = if (isImage) 8.dp else 0.dp).pressable(0.97f, onClick = onAuthorTap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(authorName, color = authorColor(authorName), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    roleTag?.let { RoleTag(it) }
                }
            }
            quoted?.let { QuotedPreview(it, mine) }
            when (message.kind) {
                MessageKind.Image -> {
                    Box {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.widthIn(max = 244.dp).heightIn(min = 120.dp, max = 320.dp).clip(RoundedCornerShape(16.dp)).background(TnaColor.elevated),
                        )
                        if (message.status == MessageStatus.Queued) {
                            Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp)
                            }
                        } else if (message.status == MessageStatus.Failed) {
                            Column(
                                Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.45f)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                Text("Повторить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { TimeMeta(message, Color.White) }
                    }
                    if (message.text.isNotBlank()) Text(message.text, color = ink, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
                MessageKind.Audio -> {
                    AudioBubble(message.mediaUrl, message.durationMs, mine)
                    Row(Modifier.align(Alignment.End)) { TimeMeta(message, if (mine) Color.White.copy(alpha = 0.7f) else TnaColor.subtle) }
                }
                MessageKind.File -> {
                    val context = LocalContext.current
                    Row(
                        Modifier.pressable(0.97f) {
                            message.mediaUrl?.let { url -> runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, null, tint = ink, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(message.mediaName ?: "Файл", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Открыть", color = ink.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Row(Modifier.align(Alignment.End)) { TimeMeta(message, if (mine) Color.White.copy(alpha = 0.7f) else TnaColor.subtle) }
                }
                else -> {
                    // Text with the time tucked into the last line's corner.
                    Box {
                        Text(message.text + " ".repeat(if (message.edited) 22 else 10), color = ink, fontSize = 16.sp)
                        Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.CenterVertically) {
                            TimeMeta(message, if (mine) Color.White.copy(alpha = 0.7f) else TnaColor.subtle)
                        }
                    }
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
                    message.reactions.forEach { r ->
                        val bg = if (mine) Color.White.copy(alpha = if (r.mine) 0.35f else 0.15f) else if (r.mine) TnaColor.accent.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.06f)
                        Row(
                            Modifier.clip(CircleShape).background(bg).pressable(0.9f) { onReact(r.emoji) }.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.emoji, fontSize = 16.sp)
                            if (r.count > 1) {
                                Spacer(Modifier.width(4.dp))
                                Text("${r.count}", color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (!mine) Spacer(Modifier.width(56.dp))
    }
}

@Composable
private fun TimeMeta(message: Message, color: Color) {
    if (message.edited) {
        Text("изменено ", color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    Text(message.time, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    if (message.isMine) {
        Spacer(Modifier.width(3.dp))
        val icon: ImageVector = when (message.status) {
            MessageStatus.Queued -> Icons.Filled.Schedule
            MessageStatus.Failed -> Icons.Filled.ErrorOutline
            MessageStatus.Sent -> Icons.Filled.Done
        }
        Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
    }
}

@Composable
private fun QuotedPreview(message: Message, mine: Boolean) {
    val ink = if (mine) Color.White else TnaColor.ink
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(if (mine) Color.White.copy(alpha = 0.18f) else TnaColor.accent.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.width(3.dp).height(38.dp).background(if (mine) Color.White else TnaColor.accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(message.author.ifBlank { "Участник" }, color = if (mine) Color.White else TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(if (message.kind == MessageKind.Text) message.text else "вложение", color = ink.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/// Several photos sent together: a tight grid, at most four tiles, "+N" on the last.
@Composable
private fun AlbumBubble(messages: List<Message>, authorName: String?, roleTag: String?, onTap: (Int) -> Unit) {
    val mine = messages.first().isMine
    val shown = messages.take(4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(if (mine) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.surface, TnaColor.surface))).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (authorName != null) {
                Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(authorName, color = authorColor(authorName), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    roleTag?.let { RoleTag(it) }
                }
            }
            shown.chunked(2).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEachIndexed { col, m ->
                        val index = rowIndex * 2 + col
                        Box(Modifier.size(118.dp).clip(RoundedCornerShape(12.dp)).background(TnaColor.elevated).pressable(0.97f) { onTap(index) }) {
                            AsyncImage(model = m.mediaUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                            if (index == 3 && messages.size > 4) {
                                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                    Text("+${messages.size - 4}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(end = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeMeta(messages.last(), if (mine) Color.White.copy(alpha = 0.7f) else TnaColor.subtle)
            }
        }
    }
}

/// Long-press menu: reactions on top, then the actions iOS offers for this message.
@Composable
private fun MessageMenu(app: AppModel, message: Message, thread: ChatThread, context: Context, onDismiss: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, containerColor = TnaColor.surface) {
        if (message.status == MessageStatus.Sent) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Reactions.set.forEach { emoji ->
                    Text(emoji, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).pressable(0.85f) { onDismiss(); app.react(message.id, emoji) }.padding(5.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
        }
        DropdownMenuItem(text = { Text("Ответить") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) }, onClick = { onDismiss(); app.startReply(message) })
        if (message.kind == MessageKind.Text && message.text.isNotBlank()) {
            DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                onDismiss()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TNA", message.text))
            })
        }
        message.location?.takeIf { message.kind == MessageKind.Location }?.let { loc ->
            DropdownMenuItem(text = { Text("Копировать координаты") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = {
                onDismiss()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TNA", LocationText.coordinates(loc)))
            })
        }
        if (message.status == MessageStatus.Failed && message.kind == MessageKind.Text) {
            DropdownMenuItem(text = { Text("Повторить") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = { onDismiss(); app.retry(message) })
        }
        val photoUrl = message.mediaUrl
        if (message.kind == MessageKind.Image && message.status == MessageStatus.Sent && photoUrl != null && android.os.Build.VERSION.SDK_INT >= 29) {
            DropdownMenuItem(text = { Text("Сохранить в галерею") }, leadingIcon = { Icon(Icons.Filled.Download, null) }, onClick = {
                onDismiss()
                app.launch {
                    app.chatNotice = if (savePhotoToGallery(context, photoUrl)) "Сохранено в галерею" else "Не удалось сохранить фото"
                }
            })
        }
        if (app.chatStaff && thread.kind == ChatThreadKind.Trip && message.status == MessageStatus.Sent) {
            val isPinned = message.pinnedAt != null || app.pinned.any { it.id == message.id }
            DropdownMenuItem(
                text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                leadingIcon = { Icon(Icons.Filled.PushPin, null) },
                onClick = { onDismiss(); app.setPinned(message, !isPinned) },
            )
        }
        if (message.isMine && message.kind == MessageKind.Text && message.status == MessageStatus.Sent) {
            DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { onDismiss(); app.startEditing(message) })
        }
        if (message.isMine) {
            DropdownMenuItem(
                text = { Text("Удалить", color = TnaColor.danger) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = TnaColor.danger) },
                onClick = { onDismiss(); app.deleteMessage(message) },
            )
        }
        // Moderation: only between participants; company ("TNA") messages have no personal author.
        if (!message.isMine && !message.asCompany && !thread.isSupport) {
            DropdownMenuItem(text = { Text("Пожаловаться") }, leadingIcon = { Icon(Icons.Filled.Report, null) }, onClick = { onDismiss(); app.reportMessage(message) })
            if (message.authorId != null) {
                DropdownMenuItem(
                    text = { Text("Заблокировать", color = TnaColor.danger) },
                    leadingIcon = { Icon(Icons.Filled.Block, null, tint = TnaColor.danger) },
                    onClick = { onDismiss(); app.blockAuthor(message) },
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    app: AppModel,
    recording: Boolean,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(TnaColor.bg.copy(alpha = 0.97f))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            app.editingMessage?.let {
                ComposeBanner(Icons.Filled.Edit, "Редактирование", null) { app.cancelCompose() }
            }
            app.replyingTo?.let { m ->
                ComposeBanner(Icons.AutoMirrored.Filled.Reply, "Ответ · ${m.author.ifBlank { "Участник" }}", if (m.kind == MessageKind.Text) m.text else "вложение") { app.cancelCompose() }
            }
            if (recording) {
                RecordingBar(onCancel = onCancelRecording, onSend = onSendRecording)
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f, enabled = !app.uploading, onClick = onAttach),
                    contentAlignment = Alignment.Center,
                ) {
                    if (app.uploading) CircularProgressIndicator(Modifier.size(18.dp), color = TnaColor.accent, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Add, "Вложение", tint = TnaColor.accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = app.chatDraft,
                    onValueChange = { app.chatDraft = it },
                    maxLines = 4,
                    textStyle = TextStyle(color = TnaColor.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(TnaColor.accent),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(TnaColor.surface)
                        .border(1.dp, TnaColor.stroke, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        Box {
                            if (app.chatDraft.isEmpty()) Text("Сообщение…", color = TnaColor.subtle, fontSize = 16.sp)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                val canSend = app.chatDraft.isNotBlank()
                if (!canSend && app.editingMessage == null) {
                    // Empty composer → voice note, like iOS.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(TnaColor.elevated).pressable(0.85f, enabled = !app.uploading, onClick = onMic),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Mic, "Голосовое", tint = TnaColor.accent, modifier = Modifier.size(20.dp)) }
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (canSend) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.elevated, TnaColor.elevated)))
                            .pressable(0.85f, enabled = canSend) { app.sendChat() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (app.editingMessage != null) Icons.Filled.Done else Icons.Filled.ArrowUpward,
                            "Отправить",
                            tint = if (canSend) Color.White else TnaColor.subtle,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeBanner(icon: ImageVector, title: String, text: String?, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (text == null) TnaColor.ink else TnaColor.accent, fontSize = if (text == null) 13.sp else 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (text != null) Text(text, color = TnaColor.subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.Cancel, "Отменить", tint = TnaColor.subtle, modifier = Modifier.size(20.dp).pressable(0.9f, onClick = onCancel))
    }
}

