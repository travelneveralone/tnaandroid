package com.travelneveralone.tna

import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Ports of TNAiOS Profile/EditProfileSheet, CitizenshipPicker, CompanionsView, AvatarCropSheet.

// MARK: - Edit profile

@Composable
fun EditProfileSheet(app: AppModel) {
    val initial = app.profile
    var last by remember { mutableStateOf(initial.lastName) }
    var first by remember { mutableStateOf(initial.firstName) }
    var patronymic by remember { mutableStateOf(initial.patronymic) }
    var city by remember { mutableStateOf(initial.city) }
    var email by remember { mutableStateOf(initial.email) }
    var birthday by remember { mutableStateOf(initial.birthday.ifBlank { null }) }
    var display by remember { mutableStateOf(app.account.name) }
    var doc by remember {
        mutableStateOf(initial.doc.withSuggestedKind(initial.birthday.ifBlank { null }))
    }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isPartner = app.role == UserRole.Partner
    val needsTicketData = app.role.canBookAndEarn
    val variants = NameDisplay.variants(last, first, patronymic)
    val emailValid = EmailFormat.isValid(email)
    val canSave = (display.trim().length >= 2 || first.trim().length >= 2) && emailValid
    SheetColumn(spacing = 16) {
        Text("ПРОФИЛЬ", style = displayStyle(28))
        AvatarRow(app, display)
        BookingField("Фамилия", last, capitalization = KeyboardCapitalization.Words) { last = it }
        BookingField("Имя", first, capitalization = KeyboardCapitalization.Words) { first = it }
        BookingField("Отчество", patronymic, capitalization = KeyboardCapitalization.Words) { patronymic = it }
        if (!isPartner) DisplayNamePicker(display, variants) { display = it }
        PhoneRow(app)
        FieldSection(if (needsTicketData) "Для быстрого оформления билета" else "О себе")
        OptionalDateField("Дата рождения", birthday) {
            birthday = it
            doc = doc.withSuggestedKind(it)
        }
        BookingField("Город проживания", city, capitalization = KeyboardCapitalization.Words) { city = it }
        if (needsTicketData) {
            EmailField(email, emailValid) { email = it }
            DocumentFields(doc) { doc = it }
        }
        SubmitButton("Сохранить", busy = saving, enabled = canSave) {
            saving = true
            scope.launch {
                val name = display.trim().ifEmpty { variants.firstOrNull() ?: first.trim() }
                val draft = app.profile.copy(
                    lastName = last.trim(),
                    firstName = first.trim(),
                    patronymic = patronymic.trim(),
                    city = city.trim(),
                    birthday = birthday.orEmpty(),
                    email = EmailFormat.normalized(email),
                    emailVerified = app.profile.emailVerified && EmailFormat.normalized(email) == app.profile.email,
                    doc = doc,
                )
                val error = app.saveProfileNow(draft, name)
                saving = false
                // Stay open on failure: the person must see the name did not go through.
                if (error == null) app.sheet = null else saveError = error
            }
        }
    }
    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = { saveError = null },
            confirmButton = { TextButton({ saveError = null }) { Text("OK") } },
            title = { Text("Ошибка") },
            text = { Text(message) },
            containerColor = TnaColor.bg,
        )
    }
}

@Composable
private fun AvatarRow(app: AppModel, name: String) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { app.pickAvatar(it) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.pressable(0.95f, enabled = !app.avatarBusy) { picker.launch("image/*") }) {
            Avatar(app.avatarUrl, name, 84, mine = true)
            if (app.avatarBusy) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(TnaColor.bg).padding(3.dp).clip(CircleShape).background(TnaColor.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (app.avatarUrl == null) "Добавь фото" else "Фото профиля", color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Тебя увидят в чатах заездов и в рейтинге", color = TnaColor.subtle, fontSize = 13.sp)
            if (app.avatarUrl != null) {
                Text("Удалить фото", color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressable(enabled = !app.avatarBusy) { app.removeAvatar() }.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun DisplayNamePicker(current: String, variants: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LabeledBox("Как тебя видят другие") {
        if (variants.isEmpty()) {
            Text(current.ifBlank { "Заполни фамилию и имя выше" }, color = if (current.isBlank()) TnaColor.subtle else TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Box {
                Row(Modifier.fillMaxWidth().pressable(0.99f) { open = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(current.ifBlank { "Выбрать" }, color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.UnfoldMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = TnaColor.surface) {
                    variants.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v, fontWeight = if (v == current) FontWeight.Bold else FontWeight.Medium) },
                            trailingIcon = if (v == current) ({ Icon(Icons.Filled.Check, null, tint = TnaColor.accent) }) else null,
                            onClick = { onPick(v); open = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneRow(app: AppModel) {
    if (app.account.phone.isBlank()) return
    LabeledBox("Телефон") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PhoneNumber.pretty(app.account.phone), color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Сменить", color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressable { app.sheet = Sheet.Login })
        }
    }
}

@Composable
private fun FieldSection(title: String) {
    Text(title.uppercase(), color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

/// Same frame as BookingField, but with arbitrary content (iOS LabeledBox).
@Composable
fun LabeledBox(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

// MARK: - Email

/// Saved as typed - only a format check, no confirmation code (0196, iOS emailField).
@Composable
private fun EmailField(value: String, valid: Boolean, onChange: (String) -> Unit) {
    LabeledBox("Email") {
        PlainInput(value, "you@example.com", KeyboardType.Email, onChange = onChange)
        if (!valid) {
            Text(
                "Похоже, в адресе ошибка - нужен вид name@example.com",
                color = Color.hex(0xE0534E), fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PlainInput(value: String, placeholder: String, keyboard: KeyboardType, enabled: Boolean = true, onChange: (String) -> Unit) {
    Box {
        if (value.isEmpty()) Text(placeholder, color = TnaColor.subtle.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = if (enabled) TnaColor.ink else TnaColor.subtle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(TnaColor.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// MARK: - Dates

private val ruDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

/// Optional ISO date (yyyy-MM-dd) with a calendar dialog; past by default, future for expiry dates.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalDateField(title: String, value: String?, future: Boolean = false, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val date = value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    LabeledBox(title, Modifier.pressable(0.99f) { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date?.let { ruDate.format(it) } ?: "Не указана",
                color = if (date == null) TnaColor.subtle else TnaColor.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (date != null) {
                Icon(Icons.Filled.Close, "Очистить", tint = TnaColor.subtle, modifier = Modifier.size(18.dp).pressable(0.9f) { onChange(null) })
                Spacer(Modifier.width(10.dp))
            }
            Icon(Icons.Filled.CalendarMonth, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
        }
    }
    if (!open) return
    val today = LocalDate.now()
    val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val baseYears = if (future) today.year..(today.year + 30) else (today.year - 100)..today.year
    val years = minOf(baseYears.first, date?.year ?: baseYears.first)..maxOf(baseYears.last, date?.year ?: baseYears.last)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = if (future) utcTimeMillis >= todayMs else utcTimeMillis <= todayMs
        },
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton({
                state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text("Готово", color = TnaColor.accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton({ open = false }) { Text("Отмена", color = TnaColor.subtle) } },
    ) { DatePicker(state, showModeToggle = true) }
}

// MARK: - Documents

@Composable
fun DocumentFields(doc: IdentityDoc, onChange: (IdentityDoc) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val kind = doc.kind
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CitizenshipField(doc.citizenship.ifBlank { Citizenship.defaultValue }) { onChange(doc.copy(citizenship = it)) }
        LabeledBox("Документ") {
            Box {
                Row(Modifier.fillMaxWidth().pressable(0.99f) { menu = true }, verticalAlignment = Alignment.CenterVertically) {
                    Text(kind?.title ?: "Выбрать", color = if (kind == null) TnaColor.subtle else TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.UnfoldMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = TnaColor.surface) {
                    DropdownMenuItem(text = { Text("Не указан") }, onClick = { onChange(doc.copy(type = "")); menu = false })
                    DocKind.entries.forEach { k ->
                        DropdownMenuItem(text = { Text(k.title) }, onClick = { onChange(doc.copy(type = k.raw)); menu = false })
                    }
                }
            }
        }
        if (kind != null) {
            BookingField("Номер документа", doc.number, capitalization = KeyboardCapitalization.Characters) { onChange(doc.copy(number = it)) }
            OptionalDateField("Дата выдачи", doc.issued) { onChange(doc.copy(issued = it)) }
            if (kind.hasInternalFields) {
                BookingField("Кем выдан", doc.issuedBy, singleLine = false) { onChange(doc.copy(issuedBy = it)) }
                BookingField("Прописка", doc.registration, singleLine = false) { onChange(doc.copy(registration = it)) }
            }
            if (kind.hasExpiry) OptionalDateField("Срок действия", doc.expiry, future = true) { onChange(doc.copy(expiry = it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenshipField(value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LabeledBox("Гражданство", Modifier.pressable(0.99f) { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.UnfoldMore, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
        }
    }
    if (!open) return
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val priority = Citizenship.priority.filter { q.isEmpty() || it.lowercase().contains(q) }
    val others = Citizenship.others.filter { q.isEmpty() || it.lowercase().contains(q) }
    ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TnaColor.bg,
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ГРАЖДАНСТВО", style = displayStyle(26))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TnaColor.surface).border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { PlainInput(query, "Поиск страны", KeyboardType.Text) { query = it } }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().height(480.dp).padding(top = 8.dp)) {
            fun section(title: String, rows: List<String>) {
                if (rows.isEmpty()) return
                item { Text(title.uppercase(), color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)) }
                items(rows) { name ->
                    Row(
                        Modifier.fillMaxWidth().pressable(0.99f) { onPick(name); open = false }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = TnaColor.ink, fontSize = 16.sp, fontWeight = if (name == value) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (name == value) Icon(Icons.Filled.Check, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
            section("Популярные", priority)
            section("Все страны", others)
        }
    }
}

// MARK: - Companions

@Composable
fun CompanionsSheet(app: AppModel) {
    var editing by remember { mutableStateOf<TravelCompanion?>(null) }
    var isNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.loadCompanions() }
    val current = editing
    if (current != null) {
        CompanionEditor(app, current, isNew) { editing = null }
        return
    }
    SheetColumn(spacing = 16) {
        Text("ПОПУТЧИКИ", style = displayStyle(28))
        Text("Сохрани данные тех, для кого бронируешь билеты - семьи, детей. Не придётся вводить их каждый раз.", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (app.companions.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.GroupAdd, null, tint = TnaColor.subtle, modifier = Modifier.size(40.dp))
                Text("Пока никого нет", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        app.companions.forEach { person ->
            CompanionRow(person) { isNew = false; editing = person }
        }
        Row(
            Modifier.fillMaxWidth().height(54.dp).pressable(0.98f) { isNew = true; editing = TravelCompanion(lastName = "", firstName = "") }
                .clip(RoundedCornerShape(16.dp)).background(TnaColor.accentBrush),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Добавить попутчика", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompanionRow(person: TravelCompanion, onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val subtitle = listOfNotNull(
        person.birthday?.let { runCatching { ruDate.format(LocalDate.parse(it.take(10))) }.getOrNull() },
        person.phone.takeIf { it.isNotBlank() }?.let { PhoneNumber.pretty(it) },
    ).joinToString(" · ").ifBlank { null }
    Row(
        Modifier.fillMaxWidth().pressable(0.99f, onClick = onTap).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(TnaColor.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(person.fullName.take(1).uppercase(), style = displayStyle(20, TnaColor.accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(person.fullName.ifBlank { "Без имени" }, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false))
                if (person.isDraft) DraftBadge()
            }
            val hint = person.missingHint
            if (hint != null) Text(hint, color = TnaColor.subtle, fontSize = 13.sp)
            else subtitle?.let { Text(it, color = TnaColor.subtle, fontSize = 13.sp, maxLines = 1) }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CompanionEditor(app: AppModel, start: TravelCompanion, isNew: Boolean, onClose: () -> Unit) {
    var person by remember(start.id) {
        mutableStateOf(start.copy(doc = start.doc.withSuggestedKind(start.birthday)))
    }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    SheetColumn(spacing = 14) {
        Text(if (isNew) "НОВЫЙ ПОПУТЧИК" else "ПОПУТЧИК", style = displayStyle(28))
        BookingField("Фамилия", person.lastName, capitalization = KeyboardCapitalization.Words) { person = person.copy(lastName = it) }
        BookingField("Имя", person.firstName, capitalization = KeyboardCapitalization.Words) { person = person.copy(firstName = it) }
        BookingField("Отчество", person.patronymic, capitalization = KeyboardCapitalization.Words) { person = person.copy(patronymic = it) }
        OptionalDateField("Дата рождения", person.birthday) {
            person = person.copy(birthday = it, doc = person.doc.withSuggestedKind(it))
        }
        BookingField("Телефон", person.phone, keyboard = KeyboardType.Phone) { person = person.copy(phone = it) }
        DocumentFields(person.doc) { person = person.copy(doc = it) }
        person.missingHint?.let { DraftNote(it) }
        SubmitButton("Сохранить", busy = busy, enabled = person.lastName.isNotBlank() || person.firstName.isNotBlank()) {
            busy = true
            scope.launch {
                val trimmed = person.copy(
                    lastName = person.lastName.trim(),
                    firstName = person.firstName.trim(),
                    patronymic = person.patronymic.trim(),
                    phone = person.phone.trim(),
                )
                val ok = app.saveCompanion(trimmed)
                busy = false
                if (ok) onClose() else saveFailed = true
            }
        }
        if (!isNew) {
            Text(
                "Удалить попутчика",
                color = Color.hex(0xE0534E),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().pressable { deleteConfirm = true }.padding(vertical = 12.dp),
            )
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Удалить попутчика?") },
            confirmButton = { TextButton({ deleteConfirm = false; app.removeCompanion(person.id); onClose() }) { Text("Удалить", color = Color.hex(0xE0534E)) } },
            dismissButton = { TextButton({ deleteConfirm = false }) { Text("Отмена") } },
            containerColor = TnaColor.bg,
        )
    }
    if (saveFailed) {
        AlertDialog(
            onDismissRequest = { saveFailed = false },
            title = { Text("Не удалось сохранить") },
            text = { Text("Нет соединения с сервером.") },
            confirmButton = { TextButton({ saveFailed = false }) { Text("OK") } },
            containerColor = TnaColor.bg,
        )
    }
}

// MARK: - Ticket data picker (iOS CompanionPickerSheet)

private val selfId: java.util.UUID = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000FF")

/// «Данные для билета» in the support chat: pick yourself and/or companions to send.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDataSheet(app: AppModel, onDismiss: () -> Unit, onSend: (List<TravelCompanion>) -> Unit) {
    var selected by remember { mutableStateOf<Set<java.util.UUID>>(emptySet()) }
    /// Companion open in the editor: a new one, or a draft being finished (iOS EditorRoute).
    var editing by remember { mutableStateOf<TravelCompanion?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf<Set<java.util.UUID>>(emptySet()) }
    var known by remember { mutableStateOf<Set<java.util.UUID>?>(null) }
    LaunchedEffect(Unit) { app.loadCompanions() }
    // A companion added or finished from here comes back selected - unless still a draft.
    LaunchedEffect(app.companions) {
        val ids = app.companions.map { it.id }.toSet()
        known?.let { before ->
            val fresh = app.companions.filter { (it.id !in before || it.id in touched) && !it.isDraft }.map { it.id }
            selected = selected + fresh
        }
        selected = selected - app.companions.filter { it.isDraft }.map { it.id }.toSet()
        known = ids
    }
    val profile = app.profile
    val me = if (!profile.doc.isEmpty || profile.birthday.isNotBlank()) {
        val name = listOf(profile.lastName, profile.firstName, profile.patronymic).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { app.account.name.trim() }
        TravelCompanion(
            id = selfId,
            lastName = name.ifBlank { "Мои данные" }, firstName = "",
            birthday = profile.birthday.ifBlank { null }, phone = app.account.phone, doc = profile.doc,
        )
    } else null
    val entries = listOfNotNull(me) + app.companions
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TnaColor.bg,
    ) {
        val open = editing
        if (open != null) {
            CompanionEditor(app, open, isNew = editingIsNew) { editing = null }
            return@ModalBottomSheet
        }
        SheetColumn(spacing = 10) {
            Text("ДАННЫЕ ДЛЯ БИЛЕТА", style = displayStyle(28))
            if (entries.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.GroupAdd, null, tint = TnaColor.subtle, modifier = Modifier.size(36.dp))
                    Text("Нет сохранённых данных", color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Добавь попутчика здесь или заполни свой профиль - данные подставятся в следующий раз.", color = TnaColor.subtle, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            entries.forEach { c ->
                val isSelf = c.id == selfId
                val draft = c.isDraft
                val isOn = c.id in selected && !draft
                val shape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().pressable(0.99f) {
                        when {
                            // A draft can't be sent: tap opens the editor to finish it
                            // (own profile is finished in the profile screen).
                            draft && isSelf -> Unit
                            draft -> { touched = touched + c.id; editingIsNew = false; editing = c }
                            else -> selected = if (isOn) selected - c.id else selected + c.id
                        }
                    }
                        .clip(shape).background(TnaColor.surface)
                        .border(1.dp, if (isOn) TnaColor.accent.copy(alpha = 0.5f) else TnaColor.stroke, shape).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when {
                            draft -> Icons.Filled.Edit
                            isOn -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.RadioButtonUnchecked
                        }, null,
                        tint = if (isOn) TnaColor.accent else TnaColor.subtle, modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c.fullName.ifBlank { "Без имени" }, color = TnaColor.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (isSelf) Text("ты", color = TnaColor.accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            if (draft) DraftBadge()
                        }
                        val hint = c.missingHint
                        if (hint != null) Text(if (isSelf) "Дозаполни профиль. $hint" else hint, color = TnaColor.subtle, fontSize = 13.sp)
                        else Text(CompanionFormat.line(c), color = TnaColor.subtle, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().pressable(0.99f) { editingIsNew = true; editing = TravelCompanion(lastName = "", firstName = "") }.dashedOutline(TnaColor.accent.copy(alpha = 0.5f), 16).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Добавить попутчика", color = TnaColor.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            val chosen = entries.filter { it.id in selected && !it.isDraft }
            PrimaryButton(if (chosen.isEmpty()) "Выбери, кого отправить" else "Отправить (${chosen.size})", enabled = chosen.isNotEmpty()) {
                onSend(chosen)
                onDismiss()
            }
        }
    }
}

/// «черновик» chip: a companion saved without the minimal ticket data (iOS DraftBadge).
@Composable
fun DraftBadge() {
    Text(
        "черновик",
        color = TnaColor.subtle, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(CircleShape).background(TnaColor.subtle.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/// Saving a draft is fine - this only explains why it can't be picked or sent yet.
@Composable
private fun DraftNote(hint: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Это черновик", color = TnaColor.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            "Сохранить можно и так. Выбрать попутчика для брони или отправить его данные в чат получится, когда всё будет заполнено. $hint.",
            color = TnaColor.subtle, fontSize = 13.sp,
        )
    }
}

// MARK: - Avatar crop

/// Pan/zoom inside a circle, rotate and mirror; «Готово» hands a square JPEG to the upload.
@Composable
fun AvatarCropScreen(app: AppModel, source: Bitmap) {
    var working by remember(source) { mutableStateOf(source) }
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offsetX by remember(source) { mutableFloatStateOf(0f) }
    var offsetY by remember(source) { mutableFloatStateOf(0f) }
    var diameter by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    BackHandler { app.cropSource = null }
    val image = remember(working) { working.asImageBitmap() }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Text("Подвинь и приблизь", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(1f)) {
            val d = constraints.maxWidth.toFloat()
            LaunchedEffect(d) { diameter = d }
            val w = working.width.toFloat()
            val h = working.height.toFloat()
            // Gestures live on the whole square: a finger often starts outside the mask.
            Box(Modifier.fillMaxSize().pointerInput(working, d) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, AvatarCrop.MAX_SCALE)
                    val (x, y) = AvatarCrop.clamp(offsetX + pan.x, offsetY + pan.y, d, w, h, scale)
                    offsetX = x
                    offsetY = y
                }
            }) {
                Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                    val s = AvatarCrop.fillScale(d, w, h, scale)
                    val dw = w * s
                    val dh = h * s
                    drawImage(
                        image,
                        dstOffset = IntOffset(((d - dw) / 2 + offsetX).roundToInt(), ((d - dh) / 2 + offsetY).roundToInt()),
                        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    )
                }
                Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            CropTool(Icons.Filled.RotateLeft, "Повернуть") {
                // Reset the frame: after the sides swap the old offset points somewhere else.
                working = working.transformed(Matrix().apply { postRotate(-90f) })
                scale = 1f; offsetX = 0f; offsetY = 0f
            }
            CropTool(Icons.Filled.Flip, "Отразить") {
                working = working.transformed(Matrix().apply { preScale(-1f, 1f) })
                offsetX = -offsetX
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(54.dp).pressable(0.97f) { app.cropSource = null }.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("Отмена", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.weight(1f).height(54.dp).pressable(0.97f) {
                    val bitmap = working
                    val frame = floatArrayOf(diameter, scale, offsetX, offsetY)
                    app.cropSource = null
                    scope.launch {
                        val jpeg = withContext(Dispatchers.Default) { cropJpeg(bitmap, frame[0], frame[1], frame[2], frame[3]) }
                        app.uploadAvatar(jpeg)
                    }
                }.clip(RoundedCornerShape(16.dp)).background(TnaColor.accentBrush),
                contentAlignment = Alignment.Center,
            ) { Text("Готово", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CropTool(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Column(Modifier.pressable(0.92f, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Bitmap.transformed(matrix: Matrix): Bitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)

/// Screen frame → source pixels, square cut, ≤1024 px JPEG.
private fun cropJpeg(bitmap: Bitmap, d: Float, scale: Float, x: Float, y: Float): ByteArray {
    val square = if (d <= 0f) {
        val side = minOf(bitmap.width, bitmap.height)
        Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
    } else {
        val (left, top, side) = AvatarCrop.cropRect(d, bitmap.width.toFloat(), bitmap.height.toFloat(), scale, x, y)
        val s = side.roundToInt().coerceIn(1, minOf(bitmap.width, bitmap.height))
        val l = left.roundToInt().coerceIn(0, bitmap.width - s)
        val t = top.roundToInt().coerceIn(0, bitmap.height - s)
        Bitmap.createBitmap(bitmap, l, t, s, s)
    }
    val out = if (square.width > 1024) Bitmap.createScaledBitmap(square, 1024, 1024, true) else square
    return ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    }
}

/// Decodes a picked photo upright (EXIF applied) and no larger than [maxSide].
fun decodeUpright(bytes: ByteArray, maxSide: Int = 2048): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
    val orientation = ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        else -> return@runCatching raw
    }
    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}.getOrNull()
