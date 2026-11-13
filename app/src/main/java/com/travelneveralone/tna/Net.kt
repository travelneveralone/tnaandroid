package com.travelneveralone.tna

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var isOnline: Boolean by mutableStateOf(true)
        private set
    var isConstrained: Boolean by mutableStateOf(false)
        private set

    init {
        refresh()
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        })
    }

    fun refresh() {
        if (AppConfig.forceOffline) {
            isOnline = false
            isConstrained = true
            return
        }
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        isConstrained = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }
}

object AppConfig {
    const val useMock = false
    const val forceOffline = false
}

private val JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

class DiskCache(dir: File) {
    private val root = File(dir, "postgrest").apply { mkdirs() }

    fun read(key: String, maxAgeSec: Int?): String? {
        val file = file(key)
        if (!file.exists()) return null
        if (maxAgeSec != null) {
            val age = (System.currentTimeMillis() - file.lastModified()) / 1000
            if (age > maxAgeSec) return null
        }
        return runCatching { file.readText() }.getOrNull()
    }

    fun readStale(key: String): String? = file(key).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun write(key: String, body: String) {
        runCatching { file(key).writeText(body) }
    }

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, hash)
    }
}

class Postgrest(
    private val baseUrl: String,
    private val anonKey: String,
    private val token: suspend () -> String?,
    private val cache: DiskCache,
    private val online: () -> Boolean,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json".toMediaType()

    val isOffline: Boolean get() = !online()

    suspend fun get(table: String, query: List<Pair<String, String>>, ttlSec: Int?): String =
        call("GET", "rest/v1/$table", query, null, ttlSec)

    suspend fun post(table: String, body: String, prefer: String = "return=minimal"): String =
        call("POST", "rest/v1/$table", emptyList(), body, null, prefer)

    suspend fun patch(table: String, query: List<Pair<String, String>>, body: String) {
        call("PATCH", "rest/v1/$table", query, body, null, "return=minimal")
    }

    suspend fun delete(table: String, query: List<Pair<String, String>>) {
        call("DELETE", "rest/v1/$table", query, null, null, "return=minimal")
    }

    suspend fun rpc(name: String, body: String = "{}", ttlSec: Int? = null): String =
        call("POST", "rest/v1/rpc/$name", emptyList(), body, ttlSec)

    suspend fun function(name: String, body: String): String =
        call("POST", "functions/v1/$name", emptyList(), body, null)

    suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String): String =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/storage/v1/object/$bucket/$path"
            val jwt = token()
            val request = Request.Builder()
                .url(url)
                .post(bytes.toRequestBody(contentType.toMediaType()))
                .header("apikey", anonKey)
                .header("Authorization", "Bearer ${jwt ?: anonKey}")
                .header("x-upsert", "true")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw TnaException("Сервер ответил ${response.code}. Файл не ушёл")
            }
            baseUrl.trimEnd('/') + "/storage/v1/object/public/$bucket/$path"
        }

    suspend fun signedUrls(bucket: String, paths: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyMap()
        val body = buildJsonObject {
            put("expiresIn", 60 * 60 * 24 * 30)
            put("paths", buildJsonArray { paths.forEach { add(JsonPrimitive(it)) } })
        }.toString()
        val url = baseUrl.trimEnd('/') + "/storage/v1/object/sign/$bucket"
        val jwt = token()
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMedia))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${jwt ?: anonKey}")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyMap()
            val rows = JSON.parseToJsonElement(response.body?.string().orEmpty()).arrayOrEmpty()
            val out = mutableMapOf<String, String>()
            rows.forEach { el ->
                val row = el.jsonObjectOrNull() ?: return@forEach
                val signed = row.str("signedURL") ?: return@forEach
                val path = row.str("path") ?: return@forEach
                out[path] = baseUrl.trimEnd('/') + "/storage/v1" + signed
            }
            out
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        query: List<Pair<String, String>>,
        body: String?,
        ttlSec: Int?,
        prefer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append('/')
            append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.joinToString("&") { (k, v) ->
                    "${enc(k)}=${enc(v)}"
                })
            }
        }
        val cacheKey = "$method $url ${body.orEmpty()}"
        if (method == "GET" || path.contains("/rpc/")) {
            if (!online()) {
                cache.readStale(cacheKey)?.let { return@withContext it }
                if (!online()) throw TnaException("Нет соединения с интернетом")
            }
            if (ttlSec != null) cache.read(cacheKey, ttlSec)?.let { return@withContext it }
        }
        val jwt = token()
        val request = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${jwt ?: anonKey}")
            .header("Accept", "application/json")
            .apply {
                if (prefer != null) header("Prefer", prefer)
                if (body != null) method(method, body.toRequestBody(jsonMedia))
                else method(method, null)
            }
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (method == "GET" || path.contains("/rpc/")) {
                        cache.readStale(cacheKey)?.let { return@withContext it }
                    }
                    throw TnaException("Сервер ответил ${response.code}. Запрос не прошёл — можно повторить")
                }
                if (method == "GET" || path.contains("/rpc/")) cache.write(cacheKey, text)
                text
            }
        } catch (error: TnaException) {
            throw error
        } catch (error: Exception) {
            if (method == "GET" || path.contains("/rpc/")) {
                cache.readStale(cacheKey)?.let { return@withContext it }
            }
            throw TnaException(error.message ?: "Нет соединения с интернетом")
        }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}

class TnaException(message: String) : Exception(message)

class AuthSession(
    context: Context,
    private val baseUrl: String,
    private val anonKey: String,
) {
    private val prefs = context.getSharedPreferences("tna.auth", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val jsonMedia = "application/json".toMediaType()

    var accessToken: String? = prefs.getString("access", null)
        private set
    var refreshToken: String? = prefs.getString("refresh", null)
        private set
    var userId: String? = prefs.getString("user", null)
        private set
    private var expiresAtEpoch: Long = prefs.getLong("exp", 0L)

    suspend fun token(): String? = mutex.withLock {
        val now = System.currentTimeMillis() / 1000
        if (accessToken != null && expiresAtEpoch > now + 60) return accessToken
        if (refreshToken != null) {
            if (refresh()) return accessToken
            // 5xx / offline: keep a still-valid access token. A rejected refresh
            // already cleared the session, so the next step is a fresh anonymous user.
            if (accessToken != null && expiresAtEpoch > now) return accessToken
            if (accessToken == null) {
                signUpAnonymous()
                return accessToken
            }
            return null
        }
        signUpAnonymous()
        accessToken
    }

    /// `sub` does not change when the access token expires, so the id stays
    /// available offline without a refresh.
    suspend fun currentUserId(): UUID? {
        val raw = userId ?: accessToken?.let(::jwtSub)
        if (raw != null) return runCatching { UUID.fromString(raw) }.getOrNull()
        return token()?.let { userId?.let(UUID::fromString) }
    }

    suspend fun requestCode(phone: String) = withContext(Dispatchers.IO) {
        token()
        val digits = PhoneNumber.digits(phone)
        val body = """{"phone":"$digits"}"""
        val code = post("/auth/v1/otp", body, bearer = null)
        if (code !in 200..299) throw TnaException("Сервер ответил $code. Запрос не прошёл — можно повторить")
    }

    suspend fun verify(phone: String, code: String): ReturningCustomer? = withContext(Dispatchers.IO) {
        val digits = PhoneNumber.digits(phone)
        val body = """{"type":"sms","phone":"$digits","token":"$code"}"""
        val (status, text) = exchange("/auth/v1/verify", body, bearer = null)
        if (status !in 200..299) throw TnaException("Сервер ответил $status. Запрос не прошёл — можно повторить")
        store(text)
        null
    }

    suspend fun currentPhone(): String? = userField("phone")
    suspend fun currentNameHint(): String? = userField("phone")

    suspend fun sessionIsAnonymous(): Boolean? = withContext(Dispatchers.IO) {
        val jwt = token() ?: return@withContext null
        val (status, text) = exchange("/auth/v1/user", null, bearer = jwt, method = "GET")
        if (status !in 200..299) return@withContext null
        val obj = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext null
        obj.bool("is_anonymous") ?: (obj.str("phone").isNullOrEmpty() && obj.str("email").isNullOrEmpty())
    }

    fun signOut() {
        accessToken = null
        refreshToken = null
        userId = null
        prefs.edit().clear().apply()
    }

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        val jwt = token() ?: throw TnaException("Нет соединения с интернетом")
        val (status, _) = exchange("/functions/v1/delete-account", "{}", bearer = jwt, method = "POST")
        if (status !in 200..299) throw TnaException("Сервер ответил $status. Запрос не прошёл — можно повторить")
        signOut()
    }

    private suspend fun signUpAnonymous() {
        val (status, text) = exchange("/auth/v1/signup", "{}", bearer = null)
        if (status !in 200..299) return
        store(text)
    }

    private suspend fun refresh(): Boolean {
        val refresh = refreshToken ?: return false
        val (status, text) = exchange(
            "/auth/v1/token?grant_type=refresh_token",
            """{"refresh_token":"$refresh"}""",
            bearer = null,
        )
        if (status !in 200..299) {
            if (status in 400..499 && status != 408 && status != 429) {
                accessToken = null
                refreshToken = null
            }
            return false
        }
        store(text)
        return true
    }

    private fun store(text: String) {
        val obj = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        accessToken = obj.str("access_token") ?: accessToken
        refreshToken = obj.str("refresh_token") ?: refreshToken
        val tokenNow = accessToken
        userId = obj["user"]?.jsonObjectOrNull()?.str("id")
            ?: obj.str("id")
            ?: tokenNow?.let(::jwtSub)
            ?: userId
        val seconds = obj.int("expires_in")
        expiresAtEpoch = when {
            seconds != null -> System.currentTimeMillis() / 1000 + seconds
            tokenNow != null -> jwtExp(tokenNow) ?: (System.currentTimeMillis() / 1000 + 3600)
            else -> expiresAtEpoch
        }
        prefs.edit()
            .putString("access", accessToken)
            .putString("refresh", refreshToken)
            .putString("user", userId)
            .putLong("exp", expiresAtEpoch)
            .apply()
    }

    private fun jwtPayload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        val body = parts[1].replace('-', '+').replace('_', '/')
        val padded = body + "=".repeat((4 - body.length % 4) % 4)
        val json = runCatching { String(java.util.Base64.getDecoder().decode(padded)) }.getOrNull() ?: return null
        return runCatching { JSON.parseToJsonElement(json).jsonObject }.getOrNull()
    }

    private fun jwtSub(token: String): String? = jwtPayload(token)?.str("sub")

    private fun jwtExp(token: String): Long? = jwtPayload(token)?.int("exp")?.toLong()

    private suspend fun userField(name: String): String? = withContext(Dispatchers.IO) {
        val jwt = token() ?: return@withContext null
        val (status, text) = exchange("/auth/v1/user", null, bearer = jwt, method = "GET")
        if (status !in 200..299) return@withContext null
        runCatching { JSON.parseToJsonElement(text).jsonObject.str(name) }.getOrNull()
    }

    private fun post(path: String, body: String, bearer: String?): Int =
        exchange(path, body, bearer).first

    private fun exchange(path: String, body: String?, bearer: String?, method: String = "POST"): Pair<Int, String> {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .method(method, body?.toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }
}

class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("tna.account", Context.MODE_PRIVATE)
    var name: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) { prefs.edit().putString("name", value).apply() }
    var phone: String
        get() = prefs.getString("phone", "").orEmpty()
        set(value) { prefs.edit().putString("phone", value).apply() }
    var isRegistered: Boolean
        get() = prefs.getBoolean("registered", false)
        set(value) { prefs.edit().putBoolean("registered", value).apply() }
    var avatarPath: String?
        get() = prefs.getString("avatar", null)
        set(value) { prefs.edit().putString("avatar", value).apply() }
}

fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
fun JsonObject.dbl(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
fun JsonElement.jsonPrimitiveOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
fun JsonElement.arrayOrEmpty(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

fun parseInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { Instant.parse(raw.replace(" ", "T").let { if (it.endsWith("Z") || it.contains("+")) it else it + "Z" }) }.getOrNull()
}

/** «3 нояб. 2026 г.» — the feed date, like iOS SupabaseDate.displayDate. */
fun displayDay(raw: String?): String {
    val instant = parseInstant(raw) ?: return ""
    return DateTimeFormatter.ofPattern("d MMM yyyy 'г.'", java.util.Locale("ru"))
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}

fun displayTime(raw: String?): String {
    val instant = parseInstant(raw) ?: return ""
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(instant)
}

object Mappers {
    fun tour(obj: JsonObject): Tour {
        val images = obj["tour_images"]?.arrayOrEmpty()?.mapNotNull { el ->
            el.jsonObjectOrNull()?.let { it.str("url") to (it.int("sort") ?: 0) }
        }?.sortedBy { it.second }?.mapNotNull { it.first }.orEmpty()
        val days = obj["tour_days"]?.arrayOrEmpty()?.mapNotNull { el ->
            val day = el.jsonObjectOrNull() ?: return@mapNotNull null
            DayPlan(day = day.int("day_number") ?: 0, title = day.str("title").orEmpty(), details = day.str("details").orEmpty())
        }?.sortedBy { it.day }.orEmpty()
        val departures = obj["departures"]?.arrayOrEmpty()?.mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            Departure(
                label = row.str("label").orEmpty(),
                spotsLeft = row.int("spots_left") ?: 0,
                startsOn = Dates.parseDay(row.str("starts_on")),
                backendId = row.str("id")?.let(UUID::fromString),
            )
        }?.filter { it.isUpcoming }?.sortedBy { it.startsOn ?: LocalDate.MAX }.orEmpty()
        val id = UUID.fromString(obj.str("id"))
        return Tour(
            name = obj.str("name").orEmpty(),
            tagline = obj.str("tagline").orEmpty(),
            hashtag = obj.str("hashtag").orEmpty(),
            region = TourRegion.fromRaw(obj.str("region").orEmpty()),
            days = obj.int("days") ?: 0,
            km = obj.int("km") ?: 0,
            priceFrom = obj.int("price_from") ?: 0,
            difficulty = obj.int("difficulty") ?: 1,
            maxAltitude = obj.int("max_altitude") ?: 0,
            symbol = obj.str("symbol").orEmpty(),
            colors = listOf(hex(obj.str("color_start")), hex(obj.str("color_end"))),
            departures = departures,
            program = days,
            includes = obj["includes"]?.arrayOrEmpty()?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            backendId = id,
            imageUrl = obj.str("image_url") ?: images.firstOrNull(),
            images = images,
            videoUrl = obj.str("video_url")?.takeIf { it.isNotEmpty() },
        )
    }

    fun hex(raw: String?): Long {
        val cleaned = raw?.trim()?.removePrefix("#").orEmpty()
        return cleaned.toLongOrNull(16) ?: Palette.accent
    }
}
