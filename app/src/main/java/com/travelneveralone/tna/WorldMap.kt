package com.travelneveralone.tna

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class GeoCountry(val code: String, val rings: List<FloatArray>)

internal object WorldAtlas {
    @Volatile private var cached: List<GeoCountry>? = null

    fun load(context: Context): List<GeoCountry> {
        cached?.let { return it }
        val text = context.assets.open("world.geo.json").bufferedReader().use { it.readText() }
        val features = JSONObject(text).getJSONArray("features")
        val countries = ArrayList<GeoCountry>(features.length())
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val code = feature.optString("id")
            if (code.isBlank()) continue
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            val rings = mutableListOf<FloatArray>()
            if (geometry.getString("type") == "Polygon") {
                rings += ring(coordinates.getJSONArray(0))
            } else {
                for (part in 0 until coordinates.length()) {
                    rings += ring(coordinates.getJSONArray(part).getJSONArray(0))
                }
            }
            countries += GeoCountry(code, rings)
        }
        cached = countries
        return countries
    }

    private fun ring(points: org.json.JSONArray): FloatArray {
        val out = FloatArray(points.length() * 2)
        for (i in 0 until points.length()) {
            val pair = points.getJSONArray(i)
            out[i * 2] = pair.getDouble(0).toFloat()
            out[i * 2 + 1] = pair.getDouble(1).toFloat()
        }
        return out
    }
}

/// The country under a point of a w×h equirectangular map, if any.
internal fun WorldAtlas.countryAt(countries: List<GeoCountry>, x: Float, y: Float, width: Float, height: Float): String? =
    findCountry(countries, x, y, width, height)

private fun findCountry(countries: List<GeoCountry>, x: Float, y: Float, width: Float, height: Float): String? {
    if (width <= 0f || height <= 0f) return null
    val lon = x / width * 360f - 180f
    val lat = 90f - y / height * 180f
    return countries.firstOrNull { country -> country.rings.any { ring -> inside(lon, lat, ring) } }?.code
}

private fun inside(lon: Float, lat: Float, ring: FloatArray): Boolean {
    var hits = false
    var j = ring.size - 2
    var i = 0
    while (i < ring.size) {
        val xi = ring[i]
        val yi = ring[i + 1]
        val xj = ring[j]
        val yj = ring[j + 1]
        val crosses = (yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi
        if (crosses) hits = !hits
        j = i
        i += 2
    }
    return hits
}
