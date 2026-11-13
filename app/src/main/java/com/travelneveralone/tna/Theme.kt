package com.travelneveralone.tna

import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Restaurant
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Mirrors TNAiOS Core/DesignSystem (Theme.swift + Components.swift). Keep the two in step.

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
    val danger = Color.hex(0xE0534E)
    val fire = Color.hex(0xE6432D)
    val accentBrush = Brush.linearGradient(
        listOf(Color.hex(0xF09B7C), Color.hex(0xED7D4D), Color.hex(0xE85C68))
    )
    val hands = Palette.hands.map { Color.hex(it) }
}

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

/// iOS `Font.display`: black, condensed poster headline.
fun displayStyle(size: Int, color: Color = TnaColor.ink) = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    lineHeight = (size * 1.05f).sp,
    color = color,
    letterSpacing = (-0.3).sp,
)

/// Display headline painted with a brush (the orange brand gradient by default).
fun displayBrushStyle(size: Int, brush: Brush = TnaColor.accentBrush) = TextStyle(
    brush = brush,
    fontFamily = Condensed,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    lineHeight = (size * 1.05f).sp,
    letterSpacing = (-0.3).sp,
)

/// iOS `Font.scaled(size:weight:)`.
fun bodyStyle(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = TnaColor.ink) =
    TextStyle(fontSize = size.sp, fontWeight = weight, color = color)

// MARK: - Interaction

/// iOS `PressableStyle`: shrinks a little under the finger, springs back. No ripple.
fun Modifier.pressable(scale: Float = 0.96f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val target = if (pressed) scale else 1f
    val current by animateFloatAsState(target, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    this
        .graphicsLayer {
            scaleX = current
            scaleY = current
            alpha = if (pressed) 0.9f else 1f
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/// Lets a child of a padded list run edge to edge (carousels inside the 20 dp page inset).
fun Modifier.bleed(horizontal: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/// iOS `shimmering(cornerRadius:)` for loading skeletons.
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val x = phase * size.width
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, TnaColor.ink.copy(alpha = 0.07f), Color.Transparent),
                startX = x,
                endX = x + band,
            ),
        )
    }
}

/// Grey rounded placeholder block with a moving highlight.
@Composable
fun SkeletonBlock(modifier: Modifier, corner: Int = 24) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(TnaColor.surface).shimmer())
}

// MARK: - Icons

/// SF Symbol names come from the backend (tours.symbol, achievements, stamps). Map them
/// to the closest Material icon so Android never shows a raw emoji where iOS has a glyph.
fun sfIcon(symbol: String): ImageVector {
    val s = symbol.lowercase()
    return when {
        s.startsWith("mountain") -> Icons.Filled.Landscape
        s.startsWith("fork.knife") -> Icons.Filled.Restaurant
        s.startsWith("car.fill") || s == "car" -> Icons.Filled.DirectionsCar
        s.startsWith("mappin") -> Icons.Filled.Place
        s.startsWith("snowflake") || s.startsWith("wind.snow") -> Icons.Filled.AcUnit
        s.startsWith("leaf") -> Icons.Filled.Eco
        s.startsWith("tree") -> Icons.Filled.Park
        s.startsWith("sparkles") -> Icons.Filled.AutoAwesome
        s.startsWith("camera.macro") -> Icons.Filled.LocalFlorist
        s.startsWith("pawprint") -> Icons.Filled.Pets
        s.startsWith("water") -> Icons.Filled.Waves
        s.startsWith("headset") -> Icons.Filled.Headset
        s.startsWith("person.2") || s.startsWith("person.3") -> Icons.Filled.Group
        s.startsWith("person") -> Icons.Filled.Person
        s.startsWith("airplane") -> Icons.Filled.Flight
        s.startsWith("building.columns") -> Icons.Filled.AccountBalance
        s.startsWith("calendar") -> Icons.Filled.CalendarMonth
        s.startsWith("circle.hexagongrid") -> Icons.Filled.Terrain
        s.startsWith("crown") -> Icons.Filled.WorkspacePremium
        s.startsWith("medal") -> Icons.Filled.MilitaryTech
        s.startsWith("trophy") -> Icons.Filled.EmojiEvents
        s.startsWith("flame") -> Icons.Filled.LocalFireDepartment
        s.startsWith("figure.hiking") -> Icons.Filled.Hiking
        s.startsWith("figure") -> Icons.AutoMirrored.Filled.DirectionsWalk
        s.startsWith("gauge") -> Icons.Filled.Speed
        s.startsWith("flag") -> Icons.Filled.Flag
        s.startsWith("globe") -> Icons.Filled.Public
        s.startsWith("point.topleft") || s == "path" -> Icons.Filled.Timeline
        s.startsWith("sun.horizon") -> Icons.Filled.WbTwilight
        s.startsWith("sun") -> Icons.Filled.WbSunny
        s.startsWith("moon") -> Icons.Filled.NightsStay
        s.startsWith("map") -> Icons.Filled.Map
        s.startsWith("star") -> Icons.Filled.Star
        s.startsWith("heart") -> Icons.Filled.Favorite
        s.startsWith("bolt") -> Icons.Filled.Bolt
        s.startsWith("tent") -> Icons.Filled.Terrain
        s.startsWith("binoculars") || s.startsWith("compass") -> Icons.Filled.Explore
        s.startsWith("book") -> Icons.AutoMirrored.Filled.MenuBook
        s.startsWith("bubble") -> Icons.AutoMirrored.Filled.Chat
        else -> Icons.Filled.Public
    }
}

/// Kept for call sites that still render a symbol as text; prefer `sfIcon`.
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

// MARK: - Brand

@Composable
fun BrandMark(size: Int = 44, prod: Boolean, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (prod) R.drawable.brand_logo else R.drawable.brand_logo_beta),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.225f).dp)),
    )
}

// MARK: - Buttons

@Composable
fun PrimaryButton(title: String, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (enabled) Modifier.shadow(12.dp, shape, ambientColor = TnaColor.accent, spotColor = TnaColor.accent) else Modifier)
            .clip(shape)
            .background(if (enabled) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.subtle.copy(0.5f), TnaColor.subtle.copy(0.5f))))
            .pressable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

/// White round icon button (catalog header: calendar, bell).
@Composable
fun CircleIconButton(icon: ImageVector, tint: Color = TnaColor.ink, size: Int = 44, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(TnaColor.surface).pressable(0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.42f).dp)) }
}

/// iOS `closeButton`: small white circle with an ✕ in the top-right corner of a sheet.
@Composable
fun CloseButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(TnaColor.surface)
            .border(1.dp, TnaColor.stroke, CircleShape)
            .pressable(0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, "Закрыть", tint = TnaColor.subtle, modifier = Modifier.size(16.dp)) }
}

// MARK: - Chips and headers

/// iOS `HashtagChip`: navy pill, filled when selected.
@Composable
fun Chip(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (filled) Color.White else TnaColor.ink,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) TnaColor.ink else TnaColor.ink.copy(alpha = 0.06f))
            .then(if (filled) Modifier else Modifier.border(1.dp, TnaColor.stroke, CircleShape))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/// iOS `SectionHeader`: uppercase display title + accent hashtag on the right.
@Composable
fun SectionTitle(title: String, hashtag: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title.uppercase(), style = displayStyle(24), modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (hashtag != null) Text(hashtag, color = TnaColor.accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/// iOS `FireBadge`: red capsule «Осталось N места».
@Composable
fun FireBadge(text: String) {
    Row(
        Modifier.clip(CircleShape).background(TnaColor.fire).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/// iOS `StampBorder`: solid ring + dashed inner ring, like a visa stamp.
fun Modifier.stampBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    val outer = size.minDimension / 2f
    drawCircle(color, radius = outer - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
    drawCircle(
        color,
        radius = outer - 4.dp.toPx(),
        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

// MARK: - Covers

/// iOS `TourGradient`: brand gradient + top-right highlight + bottom scrim.
@Composable
fun TourGradient(colors: List<Long>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val palette = colors.map { Color.hex(it) }.let { if (it.size >= 2) it else listOf(TnaColor.ink, TnaColor.accent) }
    Box(modifier.background(Brush.linearGradient(palette))) {
        // Top-right highlight for volume.
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.maxDimension * 0.9f,
                    )
                )
            }
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.66f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
            )
        )
        content()
    }
}

/// iOS `TourCover`: gradient base, photo on top (gradient shows while it loads), scrim under text.
@Composable
fun TourCover(imageUrl: String?, colors: List<Long>, modifier: Modifier = Modifier, scrim: Boolean = true) {
    TourGradient(colors, modifier) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            if (scrim) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))
                    )
                )
            }
        }
    }
}

/// Oversized faint glyph used as a watermark on covers without a photo.
@Composable
fun BoxScope.SymbolWatermark(symbol: String, size: Int, x: Int, y: Int, angle: Float, alpha: Float = 0.12f) {
    Icon(
        sfIcon(symbol),
        null,
        tint = Color.White.copy(alpha = alpha),
        modifier = Modifier.align(Alignment.BottomStart).offset(x.dp, y.dp).size(size.dp).rotate(angle),
    )
}

@Composable
fun Cover(url: String?, colors: List<Long>, modifier: Modifier = Modifier) {
    TourCover(url, colors, modifier, scrim = false)
}

// MARK: - Avatar

/// iOS `Avatar`: initial on a circle, the photo on top once it loads.
@Composable
fun Avatar(url: String?, name: String, size: Int = 40, mine: Boolean = false) {
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (mine) TnaColor.accentBrush else Brush.linearGradient(listOf(TnaColor.elevated, TnaColor.elevated))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = if (mine) Color.White else TnaColor.subtle, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.42f).sp)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

// MARK: - Rows and cards

/// iOS settings row: title (+ subtitle) and a chevron, on a white rounded card.
@Composable
fun SettingsRow(title: String, subtitle: String? = null, icon: ImageVector? = null, iconTint: Color = TnaColor.accent, trailing: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(iconTint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TnaColor.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = TnaColor.subtle, fontSize = 13.sp)
        }
        if (trailing != null) {
            Text(trailing, color = TnaColor.subtle, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TnaColor.subtle.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

/// White rounded group of rows with hairline separators (iOS inset list).
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(TnaColor.surface)
    ) { content() }
}

@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(TnaColor.stroke))
}

@Composable
fun Card(modifier: Modifier = Modifier, corner: Int = 20, padding: Int = 16, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner.dp))
            .background(TnaColor.surface)
            .padding(padding.dp)
    ) { content() }
}

@Composable
fun Stars(rating: Int, size: Int = 14) {
    Row {
        repeat(5) { index ->
            Icon(
                Icons.Filled.Star,
                null,
                tint = if (index < rating) Color.hex(0xF5B300) else TnaColor.subtle.copy(alpha = 0.25f),
                modifier = Modifier.size(size.dp),
            )
        }
    }
}

/// Small capsule label (statuses, seasons).
@Composable
fun Pill(text: String, color: Color, filled: Boolean = false, onDark: Boolean = false) {
    Text(
        text,
        color = if (filled) Color.White else color,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = if (onDark) 0.2f else 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/// Unread counter badge (tab bar, chat rows).
@Composable
fun CountBadge(count: Int, ring: Color? = null) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(CircleShape)
            .then(if (ring != null) Modifier.border(BorderStroke(1.5.dp, ring), CircleShape) else Modifier)
            .background(TnaColor.danger)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/// Progress bar on a soft track.
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, track: Color = TnaColor.elevated, height: Int = 8) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height.dp).clip(CircleShape).background(TnaColor.accentBrush))
    }
}

/// Icon + headline + body + optional CTA, centered. Empty/locked/error states.
@Composable
fun EmptyState(icon: ImageVector, title: String?, text: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = TnaColor.subtle, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        if (!title.isNullOrBlank()) {
            Text(title, color = TnaColor.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
        Text(text, color = TnaColor.subtle, fontWeight = FontWeight.Medium, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Text(action, color = TnaColor.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.pressable(onClick = onAction).padding(8.dp))
        }
    }
}

/// Accent-gradient capsule CTA («Войти / регистрация»).
@Composable
fun CapsuleButton(title: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(TnaColor.accentBrush).pressable(0.95f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun Gap(h: Int = 12) { Spacer(Modifier.height(h.dp)) }

@Composable
fun HGap(w: Int = 8) { Spacer(Modifier.width(w.dp)) }

@Composable
fun Inline(spaced: Arrangement.Horizontal = Arrangement.spacedBy(8.dp), content: @Composable () -> Unit) {
    Row(horizontalArrangement = spaced, verticalAlignment = Alignment.CenterVertically) { content() }
}

/// Generic poster card still used by a few sheets (trip, schedule). Catalog has its own cards.
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
            .height(210.dp)
            .clip(RoundedCornerShape(22.dp))
            .pressable(0.97f, onClick = onClick),
    ) {
        TourCover(image, colors, Modifier.matchParentSize())
        if (image == null) SymbolWatermark(symbol, 90, 40, -55, -10f, 0.13f)
        if (badge != null) {
            Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { FireBadge(badge) }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(title.uppercase(), style = displayStyle(24, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (price.isNotBlank()) Text(price, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/// Unused shape helper kept public for sheets that need a custom outline.
val SheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
