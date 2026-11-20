package com.travelneveralone.tna

import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment

/**
 * The point of a tour photo that must stay in frame (TNA-57), iOS `ImageFocus`.
 * Fractions of the photo's width and height from its top-left corner, 0..1.
 * [Center] is the old centred crop.
 *
 * Every frame still fills itself (ContentScale.Crop), but places the photo so the
 * focus point lands on the same relative spot of the frame - CSS
 * `object-position: x% y%`, which the admin preview and the site use. With
 * `BiasAlignment` that is exactly bias = 2 * fraction - 1.
 */
data class ImageFocus(val x: Double, val y: Double) {
    /** For `AsyncImage(alignment = …)` together with `ContentScale.Crop`. */
    val alignment: Alignment get() = BiasAlignment((2 * x - 1).toFloat(), (2 * y - 1).toFloat())

    /**
     * Top-left corner of a photo of [contentW] x [contentH] (already scaled to fill)
     * inside a [frameW] x [frameH] frame. Same math as iOS `ImageFocus.origin`.
     */
    fun origin(contentW: Double, contentH: Double, frameW: Double, frameH: Double): Pair<Double, Double> =
        (frameW - contentW) * x to (frameH - contentH) * y

    companion object {
        val Center = ImageFocus(0.5, 0.5)

        /** Missing, non-finite or out-of-range values fall back to the centre or are clamped. */
        fun of(x: Double?, y: Double?): ImageFocus = ImageFocus(unit(x), unit(y))

        private fun unit(value: Double?): Double =
            if (value == null || !value.isFinite()) 0.5 else value.coerceIn(0.0, 1.0)
    }
}
