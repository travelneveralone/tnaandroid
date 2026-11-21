package com.travelneveralone.tna

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/// Full-screen photo viewer (iOS `ImageViewer`): swipe between photos, ✕ to close.
@Composable
fun ImageViewerDialog(urls: List<String>, start: Int, onSave: ((String) -> Unit)? = null, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, urls.lastIndex)) { urls.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = urls[page],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (urls.size > 1) {
                Text(
                    "${pager.currentPage + 1} из ${urls.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp),
                )
            }
            CloseButton(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), onDismiss)
            // Save to the gallery, as the iOS viewer's ↓ button (chat photos, TNA-90).
            if (onSave != null) {
                Box(
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(32.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)).pressable(0.9f) { onSave(urls[pager.currentPage]) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Download, "Сохранить в галерею", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
