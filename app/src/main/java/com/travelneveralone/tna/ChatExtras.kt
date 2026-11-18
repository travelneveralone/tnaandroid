package com.travelneveralone.tna

import java.util.UUID
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Port of TNAiOS Chats/Views: PollBubble, PollComposerView, AudioBubble/AudioRecorder,
// and the pinned-message bar from ChatView.

// MARK: - Poll

@Composable
fun PollBubble(
    app: AppModel,
    message: Message,
    onLongPress: () -> Unit,
) {
    val poll = message.poll ?: return
    val state = app.pollStates[message.id]
    val deadlinePassed = poll.closesAt?.let { parseInstant(it) }?.isBefore(Instant.now()) == true
    val closed = poll.closed || state?.closed == true || deadlinePassed
    val myVotes = state?.my.orEmpty()
    val hasVoted = myVotes.isNotEmpty()
    var revoting by remember(message.id) { mutableStateOf(false) }
    var pending by remember(message.id) { mutableStateOf(setOf<String>()) }
    var addingOption by remember(message.id) { mutableStateOf(false) }
    var newOption by remember(message.id) { mutableStateOf("") }
    val voting = !closed && (!hasVoted || revoting)
    val total = state?.total ?: 0
    val options = remember(message.id, poll.options) { if (poll.shuffle) poll.options.shuffled() else poll.options }

    // Live tally while the poll is open (iOS: every 6 s).
    LaunchedEffect(message.id, closed) {
        app.refreshPoll(message.id)
        while (!closed) {
            delay(6000)
            app.refreshPoll(message.id)
        }
    }

    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
                .pressableLong(onLongPress).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(poll.question, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                poll.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp) }
                val bits = buildList {
                    add(if (poll.anonymous) "Анонимный опрос" else "Публичный опрос")
                    if (poll.quiz) add("викторина") else if (poll.multiple) add("несколько ответов")
                    if (closed) add("завершён")
                }
                Text(bits.joinToString(" · "), color = TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    if (voting) {
                        Row(
                            Modifier.fillMaxWidth().pressable(0.98f) {
                                if (poll.multiple) {
                                    pending = if (pending.contains(option.id)) pending - option.id else pending + option.id
                                } else {
                                    app.vote(message.id, listOf(option.id))
                                    revoting = false
                                }
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = when {
                                !poll.multiple -> Icons.Filled.RadioButtonUnchecked
                                pending.contains(option.id) -> Icons.Filled.CheckBox
                                else -> Icons.Filled.CheckBoxOutlineBlank
                            }
                            Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(option.text, color = TnaColor.ink, fontSize = 15.sp)
                        }
                    } else {
                        PollResultRow(poll, option, state, myVotes.contains(option.id))
                    }
                }
            }
            if (voting && poll.multiple) {
                Box(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(TnaColor.accentBrush)
                        .alpha(if (pending.isEmpty()) 0.5f else 1f)
                        .pressable(enabled = pending.isNotEmpty()) { app.vote(message.id, pending.toList()); revoting = false },
                    contentAlignment = Alignment.Center,
                ) { Text("Голосовать", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (total == 0) "Пока никто не проголосовал" else "Проголосовали: $total", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val canRevote = hasVoted && poll.revote && !closed && !poll.quiz
                if (canRevote && !revoting) {
                    Text("Переголосовать", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { revoting = true; pending = myVotes.toSet() })
                }
            }
            if (poll.addOptions && !closed) {
                if (addingOption) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newOption,
                            onValueChange = { newOption = it },
                            singleLine = true,
                            textStyle = TextStyle(color = TnaColor.ink, fontSize = 14.sp),
                            cursorBrush = SolidColor(TnaColor.accent),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(TnaColor.elevated).padding(horizontal = 10.dp, vertical = 8.dp),
                            decorationBox = { inner -> Box { if (newOption.isEmpty()) Text("Новый вариант", color = TnaColor.subtle, fontSize = 14.sp); inner() } },
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.AddCircle, "Добавить", tint = TnaColor.accent.copy(alpha = if (newOption.isBlank()) 0.4f else 1f),
                            modifier = Modifier.size(26.dp).pressable(enabled = newOption.isNotBlank()) {
                                app.addPollOption(message.id, newOption); newOption = ""; addingOption = false
                            })
                    }
                } else {
                    Text("+ Добавить вариант", color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { addingOption = true })
                }
            }
            if (message.isMine && !closed) {
                Text("Завершить опрос", color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { app.closePoll(message.id) })
            }
        }
    }
}

@Composable
private fun PollResultRow(poll: PollData, option: PollOption, state: PollState?, chosen: Boolean) {
    val fraction = state?.fraction(option.id) ?: 0f
    val correct = poll.quiz && poll.correct.contains(option.id)
    val bar = when {
        !poll.quiz -> TnaColor.accent
        correct -> Color.hex(0x27AE60)
        chosen -> Color.hex(0xEB5757)
        else -> TnaColor.accent.copy(alpha = 0.6f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chosen) {
                Icon(if (poll.quiz && !correct) Icons.Filled.Cancel else Icons.Filled.CheckCircle, null, tint = bar, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(option.text, color = TnaColor.ink, fontSize = 15.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).toInt()}%", color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(TnaColor.elevated)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(bar))
        }
        if (!poll.anonymous) {
            val voters = state?.voters(option.id).orEmpty()
            if (voters.isNotEmpty()) {
                Text(voters.take(6).joinToString(", ") + if (voters.size > 6) " и ещё…" else "", color = TnaColor.subtle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/// Long-press without a click action (poll card).
fun Modifier.pressableLong(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }

/// Tap + long-press without ripple (rows with a staff context menu).
fun Modifier.pressableLong(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    pointerInput(onClick, onLongClick) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() }) }

// MARK: - Poll composer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollComposerSheet(onDismiss: () -> Unit, onSend: (PollData) -> Unit) {
    var draft by remember { mutableStateOf(PollDraft()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TnaColor.bg) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Отмена", color = TnaColor.subtle, fontSize = 16.sp, modifier = Modifier.pressable(onClick = onDismiss))
                Text("Новый опрос", color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(
                    "Отправить",
                    color = TnaColor.accent.copy(alpha = if (draft.isValid) 1f else 0.4f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressable(enabled = draft.isValid) { onSend(draft.build()); onDismiss() },
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ComposerSection("ВОПРОС") {
                    ComposerField(draft.question, "Текст вопроса") { draft = draft.copy(question = it) }
                    ComposerDivider(14)
                    ComposerField(draft.descr, "Описание (необязательно)") { draft = draft.copy(descr = it) }
                }
                ComposerSection("ВАРИАНТЫ ОТВЕТА") {
                    draft.options.forEachIndexed { index, opt ->
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (draft.quiz) {
                                val on = draft.correct.contains(opt.id)
                                Icon(if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, "Правильный", tint = if (on) Color.hex(0x27AE60) else TnaColor.subtle,
                                    modifier = Modifier.size(22.dp).pressable { draft = draft.copy(correct = if (on) draft.correct - opt.id else draft.correct + opt.id) })
                                Spacer(Modifier.width(10.dp))
                            }
                            Box(Modifier.weight(1f)) {
                                ComposerField(opt.text, "Ответ", horizontal = 0) { text ->
                                    draft = draft.copy(options = draft.options.map { if (it.id == opt.id) it.copy(text = text) else it })
                                }
                            }
                            if (draft.options.size > 2) {
                                Icon(Icons.Filled.RemoveCircle, "Убрать", tint = TnaColor.danger, modifier = Modifier.size(22.dp).pressable {
                                    draft = draft.copy(options = draft.options.filter { it.id != opt.id }, correct = draft.correct - opt.id)
                                })
                            }
                        }
                        if (index < draft.options.lastIndex || draft.canAddOption) ComposerDivider(14)
                    }
                    if (draft.canAddOption) {
                        Row(Modifier.fillMaxWidth().pressable(0.99f) { draft = draft.copy(options = draft.options + PollDraftOption()) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AddCircle, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Добавить ответ", color = TnaColor.accent, fontSize = 16.sp)
                        }
                    }
                }
                if (draft.quiz) Text("Правильных вариантов может быть несколько.", color = TnaColor.subtle, fontSize = 12.sp)
                ComposerSection("НАСТРОЙКИ") {
                    SettingToggle(Icons.Filled.Visibility, 0x2F80ED, "Имена участников", "Рядом с ответами отображаются имена голосовавших", draft.showNames) { draft = draft.copy(showNames = it) }
                    if (!draft.quiz) {
                        ComposerDivider(60)
                        SettingToggle(Icons.Filled.Checklist, 0xF2994A, "Несколько ответов", "Участники могут выбрать более одного варианта", draft.multiple) { draft = draft.copy(multiple = it) }
                        ComposerDivider(60)
                        SettingToggle(Icons.Filled.AddCircle, 0x2D9CDB, "Добавление вариантов", "Участники могут предлагать новые варианты", draft.addOptions) { draft = draft.copy(addOptions = it) }
                        ComposerDivider(60)
                        SettingToggle(Icons.Filled.Repeat, 0x6C56C4, "Изменение ответа", "Участники могут изменить выбранный ответ", draft.revote) { draft = draft.copy(revote = it) }
                    }
                    ComposerDivider(60)
                    SettingToggle(Icons.Filled.Shuffle, 0x9B51E0, "Случайный порядок", "Ответы отображаются в случайном порядке", draft.shuffle) { draft = draft.copy(shuffle = it) }
                    ComposerDivider(60)
                    SettingToggle(Icons.Filled.CheckBox, 0x27AE60, "Правильный ответ", "Правильных вариантов может быть несколько", draft.quiz) { draft = draft.copy(quiz = it) }
                    ComposerDivider(60)
                    SettingToggle(Icons.Filled.Timer, 0xEB5757, "Ограничение срока", "Опрос автоматически завершится в заданное время", draft.hasDeadline) { draft = draft.copy(hasDeadline = it) }
                    if (draft.hasDeadline) {
                        ComposerDivider(60)
                        DeadlineRow(draft.closesAt) { draft = draft.copy(closesAt = it) }
                    }
                }
            }
        }
    }
}

/// Deadline as quick steps (+1 h / +1 day) - no platform date-picker dialog needed.
@Composable
private fun DeadlineRow(value: Instant, onChange: (Instant) -> Unit) {
    val local = LocalDateTime.ofInstant(value, ZoneId.systemDefault())
    val text = java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale("ru")).format(local)
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Завершить", color = TnaColor.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        listOf("−1 д" to -86400L, "−1 ч" to -3600L).forEach { (label, delta) ->
            Text(label, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable {
                val next = value.plusSeconds(delta)
                if (next.isAfter(Instant.now().plusSeconds(600))) onChange(next)
            }.padding(horizontal = 6.dp))
        }
        Text(text, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
        listOf("+1 ч" to 3600L, "+1 д" to 86400L).forEach { (label, delta) ->
            Text(label, color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { onChange(value.plusSeconds(delta)) }.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun ComposerSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TnaColor.subtle, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TnaColor.surface)) { content() }
    }
}

@Composable
private fun ComposerDivider(start: Int) {
    Box(Modifier.padding(start = start.dp).fillMaxWidth().height(1.dp).background(TnaColor.stroke))
}

@Composable
private fun ComposerField(value: String, placeholder: String, horizontal: Int = 14, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = TnaColor.ink, fontSize = 16.sp),
        cursorBrush = SolidColor(TnaColor.accent),
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal.dp, vertical = 14.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = TnaColor.subtle, fontSize = 16.sp); inner() } },
    )
}

@Composable
private fun SettingToggle(icon: ImageVector, tint: Long, title: String, subtitle: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.hex(tint)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TnaColor.subtle, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = change, colors = SwitchDefaults.colors(checkedTrackColor = Color.hex(0x27AE60), checkedThumbColor = Color.White))
    }
}

// MARK: - Pinned bar

/// Bar under the header: the pinned message, «N из M», tap jumps / cycles to the next pin.
@Composable
fun PinnedBar(pins: List<Message>, activeIndex: Int, staff: Boolean, onTap: () -> Unit, onUnpin: (Message) -> Unit) {
    val pin = pins.getOrNull(activeIndex) ?: return
    Row(
        Modifier.fillMaxWidth().background(TnaColor.surface).pressable(0.99f, onClick = onTap).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One segment per pin, the active one solid (iOS pinIndicator).
        Column(Modifier.width(3.dp).height(34.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pins.take(4).forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).fillMaxWidth().clip(CircleShape).background(TnaColor.accent.copy(alpha = if (i == activeIndex % 4) 1f else 0.3f)))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (pins.size > 1) "Закреплённое ${activeIndex + 1} из ${pins.size}" else "Закреплённое сообщение", color = TnaColor.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(pinnedPreview(pin), color = TnaColor.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (staff) {
            Icon(Icons.Filled.PushPin, "Открепить", tint = TnaColor.subtle, modifier = Modifier.size(18.dp).pressable(0.9f) { onUnpin(pin) })
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
}

fun pinnedPreview(m: Message): String = when (m.kind) {
    MessageKind.Image -> "📷 Фото"
    MessageKind.File -> "📎 ${m.mediaName ?: "Файл"}"
    MessageKind.Audio -> "🎤 Голосовое"
    MessageKind.Poll -> "📊 ${m.poll?.question ?: m.text}"
    MessageKind.Text -> m.text
}

// MARK: - Voice

/// Plays a voice note: play/pause, progress line, duration (iOS AudioBubble).
@Composable
fun AudioBubble(url: String?, durationMs: Int?, mine: Boolean) {
    var playing by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    val player = remember(url) { arrayOfNulls<MediaPlayer>(1) }
    val total = (durationMs ?: 0) / 1000
    DisposableEffect(url) {
        onDispose {
            player[0]?.release()
            player[0] = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player[0] ?: break
            val duration = if (p.duration > 0) p.duration else (durationMs ?: 1)
            progress = (p.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            delay(100)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (mine) Color.White else TnaColor.accent).pressable(0.9f) {
                if (url == null) return@pressable
                val existing = player[0]
                if (playing) {
                    existing?.pause()
                    playing = false
                } else if (existing != null) {
                    existing.start()
                    playing = true
                } else {
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(url)
                            setOnPreparedListener { it.start(); playing = true }
                            setOnCompletionListener { playing = false; progress = 0f; it.seekTo(0) }
                            prepareAsync()
                        }
                    }.onSuccess { player[0] = it }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Слушать",
                tint = if (mine) TnaColor.accent else Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(110.dp).height(4.dp).clip(CircleShape).background((if (mine) Color.White else TnaColor.subtle).copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.04f)).height(4.dp).clip(CircleShape).background(if (mine) Color.White else TnaColor.accent))
        }
        Spacer(Modifier.width(10.dp))
        Text("%d:%02d".format(total / 60, total % 60), color = if (mine) Color.White.copy(alpha = 0.85f) else TnaColor.subtle, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/// AAC in an MPEG-4 container (.m4a) - the same format iOS records and plays.
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean = runCatching {
        val out = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(44100)
        r.setAudioEncodingBitRate(64000)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    /// Stops and returns the take with its length, or null when it was too short / failed.
    fun stop(): Pair<File, Int>? {
        val r = recorder ?: return null
        recorder = null
        val ms = (System.currentTimeMillis() - startedAt).toInt()
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file ?: return null
        if (!ok || ms < 700) {
            f.delete()
            return null
        }
        return f to ms
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
    }
}

/// Recording strip in place of the composer: bin, red dot, timer, send.
@Composable
fun RecordingBar(onCancel: () -> Unit, onSend: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val pulse = rememberInfiniteTransition(label = "rec")
    val dot by pulse.animateFloat(1f, 0.3f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f, onClick = onCancel), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Delete, "Отменить", tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(10.dp).alpha(dot).clip(CircleShape).background(Color.Red))
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(seconds / 60, seconds % 60), color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(CircleShape).background(TnaColor.accentBrush).pressable(0.85f, onClick = onSend), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowUpward, "Отправить", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}


// MARK: - Participants (iOS ChatParticipantsView)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsSheet(app: AppModel) {
    val people = app.participants
    var menuFor by remember { mutableStateOf<UUID?>(null) }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { app.participantsShown = false },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TnaColor.bg,
    ) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text("УЧАСТНИКИ", style = displayStyle(28))
            if (people.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text("${people.size}", color = TnaColor.subtle, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (people.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = TnaColor.accent)
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(people.size, key = { people[it].id }) { index ->
                val p = people[index]
                val isMe = p.id == app.myUserId
                val shown = p.shownName
                val shape = RoundedCornerShape(16.dp)
                Box {
                    Row(
                        Modifier.fillMaxWidth()
                            .pressableLong(
                                onClick = {
                                    app.participantsShown = false
                                    app.openMiniProfile(MiniProfile(p.id, shown, p.city, p.role, p.avatarUrl, p.isAdmin))
                                },
                                onLongClick = {
                                    if (!isMe && (app.chatStaff || ChatModeration.canBlock(p, app.myUserId, app.chatCanBlock))) menuFor = p.id
                                },
                            )
                            .clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.avatarUrl, shown, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (isMe) "$shown (ты)" else shown, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                p.roleTag?.let { RoleTag(it) }
                                if (p.muted) Icon(Icons.Filled.VolumeOff, "Не может писать", tint = TnaColor.subtle, modifier = Modifier.size(14.dp))
                            }
                            if (p.city.isNotBlank()) Text(p.city, color = TnaColor.subtle, fontSize = 13.sp, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(menuFor == p.id, onDismissRequest = { menuFor = null }, containerColor = TnaColor.surface) {
                        if (app.chatStaff) {
                            DropdownMenuItem(
                                text = { Text(if (p.muted) "Разрешить писать" else "Запретить писать") },
                                leadingIcon = { Icon(if (p.muted) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, null) },
                                onClick = { menuFor = null; app.setMemberMuted(p, !p.muted) },
                            )
                        }
                        if (ChatModeration.canBlock(p, app.myUserId, app.chatCanBlock)) {
                            DropdownMenuItem(
                                text = { Text("Удалить из чата", color = TnaColor.danger) },
                                leadingIcon = { Icon(Icons.Filled.PersonRemove, null, tint = TnaColor.danger) },
                                onClick = { menuFor = null; app.blockTarget = p },
                            )
                        }
                    }
                }
            }
            // Removed from the chat (0157) - team only; "Вернуть" brings them back.
            val removed = app.removedParticipants
            if (removed.isNotEmpty()) {
                item(key = "removed-header") {
                    Text(
                        "УДАЛЕНЫ ИЗ ЧАТА", color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
                items(removed.size, key = { "removed-" + removed[it].id }) { index ->
                    val p = removed[index]
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface.copy(alpha = 0.6f))
                            .border(1.dp, TnaColor.stroke, shape).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.avatarUrl, p.name, 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(p.name, color = TnaColor.subtle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                if (p.blockedBy.isBlank()) "Удалён из чата" else "Удалил(а): ${p.blockedBy}",
                                color = TnaColor.subtle, fontSize = 13.sp, maxLines = 1,
                            )
                        }
                        if (ChatModeration.canUnblock(p, app.chatCanBlock)) {
                            Text(
                                "Вернуть", color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f))
                                    .pressable(0.95f) { app.setMemberBlocked(p, false) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    app.blockTarget?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { app.blockTarget = null },
            title = { Text("Удалить ${p.name} из чата?") },
            text = { Text("Человек перестанет видеть этот чат и писать в него. Из заезда он не пропадёт. Вернуть можно здесь же, внизу списка.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { app.blockTarget = null; app.setMemberBlocked(p, true) }) {
                    Text("Удалить из чата", color = TnaColor.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { app.blockTarget = null }) { Text("Отмена") }
            },
            containerColor = TnaColor.surface,
        )
    }
}

/// Small role badge next to a name ("гид", "менеджер"), Telegram-admin style.
@Composable
fun RoleTag(tag: String) {
    Text(
        tag,
        color = TnaColor.accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/// Chips with members matching the "@" token being typed.
@Composable
fun MentionBar(matches: List<ChatParticipant>, onPick: (ChatParticipant) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        matches.forEach { p ->
            Row(
                Modifier.pressable(0.96f) { onPick(p) }.clip(CircleShape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Avatar(p.avatarUrl, p.name, 24)
                Text(p.name, color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                p.roleTag?.let { Text(it, color = TnaColor.accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
}

/// Saves a chat photo to the gallery (Pictures/TNA) through MediaStore. Needs no
/// permission on Android 10+; older systems are not supported and return false.
suspend fun savePhotoToGallery(context: Context, url: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val name = "tna-${System.currentTimeMillis()}.jpg"
        if (android.os.Build.VERSION.SDK_INT < 29) return@runCatching false
        val bytes = java.net.URL(url).openStream().use { it.readBytes() }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/TNA")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
        true
    }.getOrDefault(false)
}
