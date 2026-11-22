package com.travelneveralone.tna

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// iOS Features/Update/AppUpdateViews.swift: the blocking screen and the banner.

private fun openStore(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Covers the whole app and cannot be closed: this build no longer works with
 *  the backend. Never shown during a trip - the backend answers «soft» then. */
@Composable
fun ForceUpdateScreen(message: String, storeUrl: String?, prod: Boolean) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(TnaColor.bg)
            // Swallow taps: nothing underneath is usable any more.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 28.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(size = 72, prod = prod)
        Text("Пора обновить приложение", style = displayStyle(24), textAlign = TextAlign.Center)
        Text(
            message.ifEmpty { "Эта версия TNA больше не работает. Обнови приложение - это займёт минуту, все твои поездки, чаты и бонусы на месте." },
            style = bodyStyle(16, color = TnaColor.subtle), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        if (storeUrl != null) {
            PrimaryButton("Обновить", icon = Icons.Filled.ArrowCircleDown) { openStore(context, storeUrl) }
        } else {
            Text("Найди TNA в Google Play и нажми «Обновить».", style = bodyStyle(15, FontWeight.SemiBold), textAlign = TextAlign.Center)
        }
    }
}

/** «A new version is out» - or, during a trip, «update when you're back». */
@Composable
fun UpdateBanner(onTrip: Boolean, storeUrl: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.statusBarsPadding().padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = TnaColor.ink, spotColor = TnaColor.ink)
            .clip(shape).background(TnaColor.surface)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ArrowCircleDown, null, tint = TnaColor.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (onTrip) "Вышла новая версия. Обнови приложение, когда вернёшься из поездки." else "Вышла новая версия TNA",
            style = bodyStyle(13, FontWeight.SemiBold), modifier = Modifier.weight(1f),
        )
        if (storeUrl != null && !onTrip) {
            Text(
                "Обновить", color = TnaColor.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp).pressable { openStore(context, storeUrl) },
            )
        }
        Icon(
            Icons.Filled.Close, "Закрыть", tint = TnaColor.subtle,
            modifier = Modifier.padding(start = 4.dp).size(28.dp).clip(RoundedCornerShape(14.dp))
                .pressable(0.9f, onClick = onDismiss).padding(7.dp),
        )
    }
}
