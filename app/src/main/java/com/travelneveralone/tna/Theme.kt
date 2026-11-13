package com.travelneveralone.tna

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil.compose.AsyncImage

fun Color.Companion.hex(value: Long): Color = Color(0xFF000000 or value)

object TnaColor {
    val bg = Color.hex(Palette.bg)
    val surface = Color.White
    val elevated = Color.hex(Palette.elevated)
    val ink = Color.hex(Palette.ink)
    val accent = Color.hex(Palette.accent)
    val accentSoft = Color.hex(Palette.accentSoft)
    val subtle = Color.hex(Palette.subtle)
    val stroke = ink.copy(alpha = 0.08f)
    val accentBrush = Brush.linearGradient(
        listOf(Color.hex(0xF09B7C), Color.hex(0xED7D4D), Color.hex(0xE85C68))
    )
}

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

fun displayStyle(size: Int, color: Color = TnaColor.ink) = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    color = color,
    letterSpacing = (-0.5).sp,
)

@Composable
fun BrandMark(size: Int = 44, prod: Boolean) {
    Image(
        painter = painterResource(if (prod) R.drawable.brand_logo else R.drawable.brand_logo_beta),
        contentDescription = null,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.22f).dp)),
    )
}

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.subtle, TnaColor.subtle)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun SectionTitle(title: String, hashtag: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title.uppercase(), style = displayStyle(24))
        Spacer(Modifier.weight(1f))
        if (hashtag != null) Text(hashtag, color = TnaColor.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun Chip(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (filled) Color.White else TnaColor.ink,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) TnaColor.ink else TnaColor.ink.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun TourGradient(colors: List<Long>, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val brush = Brush.linearGradient(colors.map { Color.hex(it) }.ifEmpty { listOf(TnaColor.ink, TnaColor.accent) })
    Box(modifier.background(brush)) { content() }
}

@Composable
fun Cover(url: String?, colors: List<Long>, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        TourGradient(colors, modifier)
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, color = TnaColor.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = TnaColor.subtle, fontSize = 13.sp)
    }
}

fun symbolEmoji(symbol: String): String = when {
    symbol.contains("mountain") -> "🏔"
    symbol.contains("snow") -> "❄️"
    symbol.contains("leaf") -> "🌿"
    symbol.contains("tree") -> "🐉"
    symbol.contains("spark") -> "✨"
    symbol.contains("camera") -> "🌸"
    symbol.contains("paw") -> "🦁"
    symbol.contains("water") -> "🌊"
    symbol.contains("headset") -> "🎧"
    symbol.contains("person") -> "👤"
    else -> "🌍"
}

@Composable
fun PosterCard(
    title: String,
    subtitle: String,
    price: String,
    colors: List<Long>,
    image: String?,
    symbol: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .height(210.dp),
    ) {
        Cover(image, colors, Modifier.matchParentSize())
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
                )
        )
        Text(
            symbolEmoji(symbol),
            fontSize = 64.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            color = Color.White.copy(alpha = 0.85f),
        )
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.hex(0xE6432D))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(0.4f), blurRadius = 8f)))
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            Text(price, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TnaColor.surface)
            .padding(14.dp)
    ) { content() }
}

@Composable
fun Stars(rating: Int) {
    Text("★".repeat(rating.coerceIn(0, 5)) + "☆".repeat((5 - rating).coerceAtLeast(0)), color = TnaColor.accent, fontSize = 14.sp)
}

@Composable
fun Inline(spaced: Arrangement.Horizontal = Arrangement.spacedBy(8.dp), content: @Composable () -> Unit) {
    Row(horizontalArrangement = spaced, verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun Gap(h: Int = 12) { Spacer(Modifier.height(h.dp)) }

@Composable
fun HGap(w: Int = 8) { Spacer(Modifier.width(w.dp)) }
