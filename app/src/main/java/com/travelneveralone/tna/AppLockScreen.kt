package com.travelneveralone.tna

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

// Port of iOS AppLockView + the lock parts of RootView and ProfileView.

val BiometryKind.icon: ImageVector
    get() = if (this == BiometryKind.Face) Icons.Filled.Face else Icons.Filled.Fingerprint

/// Lock screen and the post-login offer. Called last in Root.
@Composable
fun AppLockLayer(app: AppModel) {
    val lock = app.appLock
    val scope = rememberCoroutineScope()
    // A dialog window, so it also covers the bottom sheets (themselves dialogs).
    if (lock.isLocked && !app.showSplash) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            AppLockScreen(app) {
                lock.reset()
                app.signOut()
                app.requireAccount {}
            }
        }
    }
    if (app.lockOfferShown) {
        val what = lock.kind.title ?: "биометрии"
        AlertDialog(
            onDismissRequest = { app.lockOfferShown = false; lock.markOffered() },
            title = { Text("Входить по $what?", fontWeight = FontWeight.Bold) },
            text = { Text("Приложение будет открываться только у тебя. Выключить можно в профиле.", color = TnaColor.subtle) },
            confirmButton = {
                TextButton(onClick = {
                    app.lockOfferShown = false
                    scope.launch { lock.setEnabled(true) }
                }) { Text("Включить", color = TnaColor.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { app.lockOfferShown = false; lock.markOffered() }) {
                    Text("Не сейчас", color = TnaColor.ink)
                }
            },
            containerColor = TnaColor.bg,
        )
    }
}

@Composable
private fun AppLockScreen(app: AppModel, onUsePhone: () -> Unit) {
    val lock = app.appLock
    val scope = rememberCoroutineScope()
    var confirmPhone by remember { mutableStateOf(false) }
    val cream = Color.hex(Palette.bg)
    // Prompt right away: the cover is only useful with the prompt on top.
    LaunchedEffect(Unit) { lock.unlock() }
    Box(
        Modifier.fillMaxSize().background(TnaColor.ink).background(
            Brush.radialGradient(listOf(TnaColor.accent.copy(alpha = 0.25f), Color.Transparent), radius = 900f)
        ),
    ) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.weight(1f))
            BrandMark(84, app.isProd)
            Text("С возвращением", style = displayStyle(34, cream))
            Text("Подтверди, что это ты", color = cream.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            val title = lock.kind.title?.let { "Войти по $it" } ?: "Войти"
            PrimaryButton(title, enabled = !lock.isAuthenticating, icon = lock.kind.icon) {
                scope.launch { lock.unlock() }
            }
            TextButton(onClick = { confirmPhone = true }, modifier = Modifier.padding(bottom = 12.dp)) {
                Text("Войти по номеру телефона", color = cream.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (confirmPhone) {
        AlertDialog(
            onDismissRequest = { confirmPhone = false },
            title = { Text("Войти по номеру телефона?", fontWeight = FontWeight.Bold) },
            text = { Text("Пришлём код на твой номер. Брони и история никуда не денутся.", color = TnaColor.subtle) },
            confirmButton = {
                TextButton(onClick = { confirmPhone = false; onUsePhone() }) {
                    Text("Получить код по SMS", color = TnaColor.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmPhone = false }) { Text("Отмена", color = TnaColor.ink) } },
            containerColor = TnaColor.bg,
        )
    }
}

/// Profile settings row with a switch (iOS `SettingsToggleRow`). The switch
/// shows [on]; [change] decides whether a tap sticks.
@Composable
fun ProfileToggleRow(icon: ImageVector, title: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TnaColor.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = TnaColor.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = change,
            colors = SwitchDefaults.colors(checkedTrackColor = TnaColor.accent, checkedThumbColor = Color.White, uncheckedTrackColor = TnaColor.elevated, uncheckedBorderColor = TnaColor.stroke),
        )
    }
}
