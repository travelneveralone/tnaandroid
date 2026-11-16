package com.travelneveralone.tna

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Port of TNAiOS Core/DesignSystem/PhoneField.swift: country chip (flag + code,
// opens a searchable picker) and the national number masked as you type. Rules
// live in PhoneInput.kt; this file is only the UI.

private val PhoneDigits = TextStyle(
    color = TnaColor.ink,
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    // Tabular digits: the number doesn't shimmy sideways as it's reformatted.
    fontFeatureSettings = "tnum",
)

@Composable
fun PhoneField(
    country: PhoneCountry,
    national: String,
    onChange: (PhoneCountry, String) -> Unit,
    autoFocus: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val focus = remember { FocusRequester() }
    var picking by remember { mutableStateOf(false) }
    var focusRequests by remember { mutableIntStateOf(if (autoFocus) 1 else 0) }

    var field by remember { mutableStateOf(TextFieldValue(PhoneInput.format(national, country.mask))) }
    var emitted by remember { mutableStateOf(country.iso to national) }
    if (emitted != (country.iso to national)) {
        // Changed from outside (country picker, beta quick logins, seeding).
        val text = PhoneInput.format(national, country.mask)
        field = TextFieldValue(text, TextRange(text.length))
        emitted = country.iso to national
    }

    fun onEdit(new: TextFieldValue) {
        if (new.text == field.text) { field = new; return }   // caret or selection moved only
        val (raw, caretDigits) = PhoneInput.replace(field.text, new.text, new.selection.start)
        val international = raw.trim().startsWith("+")
        val grew = raw.count { it in '0'..'9' } > field.text.count { it in '0'..'9' }
        // Already full: a typed digit would only push the last one out. Ignore it.
        if (!international && grew && national.length >= country.lengths.last) return
        val edit = PhoneInput.apply(raw, country)
        val display = edit.display
        val caret = if (edit.pendingCode != null) display.length
        else PhoneInput.caretOffset((caretDigits - edit.droppedLeading).coerceAtLeast(0), display)
        field = TextFieldValue(display, TextRange(caret))
        emitted = edit.country.iso to edit.national
        if (edit.country != country || edit.national != national) onChange(edit.country, edit.national)
    }

    LaunchedEffect(focusRequests) { if (focusRequests > 0) runCatching { focus.requestFocus() } }

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.fillMaxHeight().pressable(0.97f) { picking = true }.padding(start = 14.dp, end = 10.dp)
                .semantics { contentDescription = "Код страны: ${country.name}, ${country.dialCodeDisplay}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(country.flag, fontSize = 22.sp)
            Text(country.dialCodeDisplay, style = PhoneDigits)
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(TnaColor.stroke))
        Box(Modifier.weight(1f).pressable(1f) { focusRequests++ }.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            if (field.text.isEmpty()) Text(country.placeholder, style = PhoneDigits.copy(color = TnaColor.subtle.copy(alpha = 0.55f)))
            BasicTextField(
                value = field,
                onValueChange = { onEdit(it) },
                singleLine = true,
                textStyle = PhoneDigits,
                cursorBrush = SolidColor(TnaColor.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Номер телефона" },
            )
        }
    }

    if (picking) {
        PhoneCountryPicker(selected = country, onDismiss = { picking = false; focusRequests++ }) { picked ->
            picking = false
            focusRequests++
            if (picked != country) onChange(picked, PhoneInput.clamp(national, picked))
        }
    }
}

/**
 * Searchable list of countries with flags and calling codes: the device's own
 * and popular ones first, then everything alphabetically. Search matches Russian
 * and English names and codes ("+31", "31").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneCountryPicker(selected: PhoneCountry, onDismiss: () -> Unit, onPick: (PhoneCountry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val popular = remember { PhoneCountry.popular(Locale.getDefault().country) }
    val listState = rememberLazyListState()
    // Selected country isn't among the popular ones: scroll it into view.
    LaunchedEffect(Unit) {
        if (selected !in popular) {
            val index = PhoneCountry.sortedByName.indexOf(selected)
            if (index >= 0) listState.scrollToItem((popular.size + 2 + index - 3).coerceAtLeast(0))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TnaColor.bg,
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("КОД СТРАНЫ", style = displayStyle(26))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TnaColor.surface)
                    .border(1.dp, TnaColor.stroke, RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Страна или код", color = TnaColor.subtle.copy(alpha = 0.6f), fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TnaColor.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(TnaColor.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val results = if (query.isBlank()) null else PhoneCountry.search(query)
        LazyColumn(Modifier.fillMaxWidth().height(480.dp).padding(top = 8.dp), state = listState) {
            fun header(title: String) {
                item(key = "h-$title") {
                    Text(title.uppercase(), color = TnaColor.subtle, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
                }
            }
            fun rows(section: String, list: List<PhoneCountry>) {
                items(list, key = { "$section-${it.iso}" }) { c -> CountryRow(c, c == selected) { onPick(c) } }
            }
            when {
                results == null -> {
                    header("Популярные"); rows("top", popular)
                    header("Все страны"); rows("all", PhoneCountry.sortedByName)
                }
                results.isEmpty() -> item(key = "empty") {
                    Text("Ничего не нашлось", color = TnaColor.subtle, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
                }
                else -> rows("hit", results)
            }
        }
    }
}

@Composable
private fun CountryRow(country: PhoneCountry, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(0.99f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(country.flag, fontSize = 24.sp)
        Text(country.name, color = TnaColor.ink, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        Text(country.dialCodeDisplay, style = PhoneDigits.copy(color = TnaColor.subtle, fontSize = 15.sp))
        Icon(Icons.Filled.Check, null, tint = if (selected) TnaColor.accent else TnaColor.accent.copy(alpha = 0f), modifier = Modifier.size(18.dp))
    }
}
