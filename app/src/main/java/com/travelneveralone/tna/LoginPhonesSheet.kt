package com.travelneveralone.tna

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/// «Номера для входа» (iOS `Profile/LoginPhonesView`): the account's numbers,
/// adding one by an SMS code and removing one. Rules live in [LoginPhonesFlow].
@Composable
fun LoginPhonesSheet(app: AppModel) {
    val scope = rememberCoroutineScope()
    val flow = remember {
        LoginPhonesFlow(app.loginPhones) { phone -> app.account.phone = PhoneNumber.pretty(phone) }
    }
    var country by remember { mutableStateOf(app.loginCountry) }
    var national by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<LoginPhone?>(null) }
    LaunchedEffect(Unit) { flow.load() }

    SheetColumn(spacing = 16) {
        Text("НОМЕРА ДЛЯ ВХОДА", style = displayStyle(28))
        Text(
            "Входи по любому из этих номеров - аккаунт, поездки и бонусы будут одни и те же.",
            color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        )

        when (val step = flow.step) {
            LoginPhonesFlow.Step.Numbers -> when {
                !flow.loaded -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TnaColor.subtle, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                flow.loadFailed -> Text(
                    "Не удалось загрузить номера. Проверь связь и открой экран ещё раз.",
                    color = TnaColor.subtle, fontSize = 14.sp,
                )
                else -> {
                    val shape = RoundedCornerShape(18.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape).padding(horizontal = 14.dp)) {
                        flow.phones.forEachIndexed { index, item ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(TnaColor.stroke))
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Smartphone, null, tint = TnaColor.accent, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(PhoneNumber.pretty(item.phone), color = TnaColor.ink, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (flow.canRemove) {
                                    Box(Modifier.size(36.dp).pressable(enabled = !flow.busy) { removing = item }, contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Delete, "Удалить номер ${PhoneNumber.pretty(item.phone)}",
                                            tint = TnaColor.subtle, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    flow.notice?.let { Text(it, color = TnaColor.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    if (flow.canAddMore) {
                        PrimaryButton("Добавить номер", icon = Icons.Filled.Add) {
                            national = ""
                            flow.startAdding()
                        }
                    }
                }
            }
            LoginPhonesFlow.Step.Phone -> {
                Text("Пришлём код на новый номер - так убедимся, что он твой.", color = TnaColor.subtle, fontSize = 14.sp)
                PhoneField(country, national, autoFocus = true, onChange = { c, n -> country = c; national = n })
                PrimaryButton(
                    if (flow.busy) "Отправляем…" else "Получить код",
                    enabled = !flow.busy && PhoneInput.isComplete(national, country),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                ) { scope.launch { flow.sendCode(PhoneInput.e164(national, country)) } }
                LinkText("Отмена", enabled = !flow.busy) { flow.cancelAdding() }
            }
            is LoginPhonesFlow.Step.Code -> {
                Text("Отправили код на ${PhoneNumber.pretty(step.phone)}", color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                CodeInput(flow.code) { flow.code = it.filter(Char::isDigit).take(LoginPhonesFlow.CODE_LENGTH) }
                PrimaryButton(
                    if (flow.busy) "Проверяем…" else "Привязать номер",
                    enabled = !flow.busy && flow.codeIsComplete,
                    icon = Icons.Filled.Check,
                ) { scope.launch { flow.confirm() } }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LinkText("Отправить ещё раз", enabled = !flow.busy) { scope.launch { flow.resendCode() } }
                    LinkText("Изменить номер", enabled = !flow.busy) { flow.editNumber() }
                }
            }
        }

        flow.error?.let { Text(it, color = TnaColor.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }

    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Удалить номер?", fontWeight = FontWeight.Bold) },
            text = { Text("Войти по ${PhoneNumber.pretty(item.phone)} больше не получится. Поездки и бонусы останутся в аккаунте.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { flow.remove(item) }
                }) { Text("Удалить", color = TnaColor.danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена", color = TnaColor.accent) } },
            containerColor = TnaColor.bg,
        )
    }
}

@Composable
private fun LinkText(title: String, enabled: Boolean, onClick: () -> Unit) {
    Text(title, color = TnaColor.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressable(enabled = enabled) { onClick() })
}

@Composable
private fun CodeInput(value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = TnaColor.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(TnaColor.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth().clip(shape).background(TnaColor.surface).border(1.dp, TnaColor.stroke, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text("Код из SMS", color = TnaColor.subtle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                inner()
            }
        },
    )
}
