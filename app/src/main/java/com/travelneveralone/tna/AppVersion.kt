package com.travelneveralone.tna

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

// Force update and the «new version» banner (TNA-114, TNACloud 0185) - iOS AppVersion.swift.
//
// The backend owns the decision: `app_version_check` answers ok / soft / force
// for this platform and build, and never force for someone on a running trip.
// Every PostgREST request also carries the build in headers; a build the backend
// no longer supports gets HTTP 426, and the gate turns that into the update
// screen at once. Offline never blocks: no answer means «carry on as before».

/** This binary, as the backend sees it. */
object AppBuild {
    const val PLATFORM = "android"
    val version: String get() = BuildConfig.VERSION_NAME
    val build: String get() = BuildConfig.TNA_BUILD
    /** Sent with every PostgREST request; `app_request_guard` (0185) reads them. */
    val headers: Map<String, String>
        get() = mapOf("x-app-platform" to PLATFORM, "x-app-version" to version, "x-app-build" to build)
}

/** A request came back 426 (Net.kt). AppModel points it at its gate. */
object UpgradeSignal {
    @Volatile var listener: (() -> Unit)? = null
    fun fire() { listener?.invoke() }
}

enum class AppVersionStatus { Ok, Soft, Force }

data class AppVersionVerdict(
    val status: AppVersionStatus,
    /** Soft only because the person is travelling: force waits for the trip's end. */
    val onTrip: Boolean = false,
    val storeUrl: String = "",
    val message: String = "",
    val recommendedBuild: String? = null,
) {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The answer of `app_version_check`; null when it is not one. An unknown
         *  status from a newer backend never locks anyone out. */
        fun parse(text: String): AppVersionVerdict? = runCatching {
            val o = JSON.parseToJsonElement(text).jsonObject
            fun s(key: String) = o[key]?.jsonPrimitive?.contentOrNull
            AppVersionVerdict(
                status = when (s("status")) {
                    "force" -> AppVersionStatus.Force
                    "soft" -> AppVersionStatus.Soft
                    else -> AppVersionStatus.Ok
                },
                onTrip = o["on_trip"]?.jsonPrimitive?.booleanOrNull ?: false,
                storeUrl = s("store_url").orEmpty(),
                message = s("message").orEmpty(),
                recommendedBuild = s("recommended_build"),
            )
        }.getOrNull()
    }
}

interface AppVersionChecking {
    /** null - no answer (offline, timeout, a backend without 0185). */
    suspend fun check(): AppVersionVerdict?
}

/** Where «not now» on the banner is remembered. */
interface StringStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

class PrefsStringStore(context: Context, name: String) : StringStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

/** Calls `app_version_check` on its own short-fused client: it runs on every
 *  launch and must not wait the minute the main client allows. */
class LiveAppVersionCheck(
    private val baseUrl: String,
    private val anonKey: String,
    private val token: suspend () -> String?,
    private val store: StringStore,
) : AppVersionChecking {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    /** One row per install in `app_clients`; Android has no Keychain to outlive a reinstall. */
    private fun deviceId(): String =
        store.get(DEVICE_KEY) ?: UUID.randomUUID().toString().also { store.set(DEVICE_KEY, it) }

    override suspend fun check(): AppVersionVerdict? = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("p_platform", AppBuild.PLATFORM)
                put("p_version", AppBuild.version)
                put("p_build", AppBuild.build)
                put("p_device", deviceId())
            }.toString()
            val jwt = token()
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/rest/v1/rpc/app_version_check")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer ${jwt ?: anonKey}")
                .header("Accept", "application/json")
                .apply { AppBuild.headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else AppVersionVerdict.parse(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private companion object { const val DEVICE_KEY = "appVersion.deviceId" }
}

/** What the shell shows: nothing, the banner or the blocking update screen. */
class AppVersionGate(
    private val checker: AppVersionChecking,
    private val store: StringStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var verdict by mutableStateOf<AppVersionVerdict?>(null)
        private set
    /** Compose state, so «not now» hides the banner on this frame. */
    private var dismissed by mutableStateOf(store.get(DISMISSED_KEY))
    private var lastCheck: Long? = null

    val isBlocked: Boolean get() = verdict?.status == AppVersionStatus.Force

    val showsBanner: Boolean
        get() {
            val v = verdict ?: return false
            return v.status == AppVersionStatus.Soft && dismissed != dismissKey(v)
        }

    /** Launch and every return to the foreground. */
    suspend fun refresh(force: Boolean = false) {
        val last = lastCheck
        if (!force && last != null && now() - last < MIN_INTERVAL_MS) return
        lastCheck = now()
        // No answer keeps what we had: offline neither blocks nor unblocks.
        checker.check()?.let { verdict = it }
    }

    /** A request was refused with 426. Ask why; if the check itself can't be
     *  reached, block anyway - the server has just said this build is done. */
    suspend fun upgradeRequired() {
        val fresh = checker.check()
        if (fresh != null) {
            verdict = fresh
            lastCheck = now()
        } else if (verdict?.status != AppVersionStatus.Force) {
            verdict = AppVersionVerdict(AppVersionStatus.Force, storeUrl = verdict?.storeUrl.orEmpty(),
                message = verdict?.message.orEmpty())
        }
    }

    /** «Not now»: hidden until the backend recommends a newer build. */
    fun dismissBanner() {
        val v = verdict ?: return
        dismissKey(v).let { store.set(DISMISSED_KEY, it); dismissed = it }
    }

    val storeUrl: String? get() = verdict?.storeUrl?.takeIf { it.startsWith("https://") }

    private fun dismissKey(v: AppVersionVerdict) = (if (v.onTrip) "trip:" else "") + v.recommendedBuild.orEmpty()

    companion object {
        const val MIN_INTERVAL_MS = 60_000L
        const val DISMISSED_KEY = "appVersion.softDismissed"
    }
}
