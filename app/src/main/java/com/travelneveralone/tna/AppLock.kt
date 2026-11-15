package com.travelneveralone.tna

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/// Port of iOS `AppLock` (Features/Auth/AppLock.swift).
///
/// Sessions are long-lived on purpose (every phone sign-in costs an SMS), so
/// the device itself keeps a stranger out. When the lock is on, the app asks
/// for biometrics on a cold launch and after [GRACE_MS] in the background.
/// Failed biometrics fall back to the device PIN; the lock screen also offers
/// a fresh phone sign-in, so nobody gets stuck.

enum class BiometryKind {
    None, Fingerprint, Face, Generic;

    val isAvailable get() = this != None

    /// «Вход по …» / «Войти по …»; iOS says «Face ID» / «Touch ID».
    val title: String?
        get() = when (this) {
            None -> null
            Fingerprint -> "отпечатку"
            Face -> "лицу"
            Generic -> "биометрии"
        }
}

interface Biometrics {
    val kind: BiometryKind
    /// Biometrics or a device PIN exist. False once the PIN is removed.
    val canAuthenticate: Boolean
    /// Biometrics with the device PIN as fallback. True on success.
    suspend fun authenticate(reason: String): Boolean
}

/// Persisted flags; SharedPreferences in the app, a map in tests.
interface FlagStore {
    fun get(key: String): Boolean?
    fun set(key: String, value: Boolean)
    fun clear()
}

class PrefsFlagStore(context: Context, name: String) : FlagStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun get(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    override fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}

/// androidx.biometric. BiometricPrompt needs a live FragmentActivity, which a
/// ViewModel can't own, so MainActivity attaches itself here.
class AndroidBiometrics(context: Context) : Biometrics {
    private val appContext = context.applicationContext
    private val manager = BiometricManager.from(appContext)
    private var activity: WeakReference<FragmentActivity>? = null

    fun attach(activity: FragmentActivity) {
        this.activity = WeakReference(activity)
    }

    override val kind: BiometryKind
        get() {
            if (manager.canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) return BiometryKind.None
            val pm = appContext.packageManager
            val finger = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
            val face = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
            return when {
                finger && !face -> BiometryKind.Fingerprint
                face && !finger -> BiometryKind.Face
                else -> BiometryKind.Generic
            }
        }

    override val canAuthenticate: Boolean
        get() = manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(reason: String): Boolean {
        val host = activity?.get() ?: return false
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                    // onAuthenticationFailed: one bad try, the prompt stays up.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}

/// Fixture for mock builds and tests.
class MockBiometrics(
    override var kind: BiometryKind = BiometryKind.Fingerprint,
    override var canAuthenticate: Boolean = true,
    var succeeds: Boolean = true,
) : Biometrics {
    var prompts = 0
        private set

    override suspend fun authenticate(reason: String): Boolean {
        prompts += 1
        return succeeds
    }
}

class AppLock(
    private val flags: FlagStore,
    private val biometrics: Biometrics,
    private val isRegistered: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /// Short trips to another app (an SMS, a booking link) don't re-lock.
        const val GRACE_MS = 5 * 60 * 1000L
        private const val ENABLED = "enabled"
        private const val OFFERED = "offered"
        const val REASON = "Войти в TNA"
    }

    var isEnabled by mutableStateOf(flags.get(ENABLED) ?: false)
        private set
    /// Cold launch: locked straight away, before any content shows.
    var isLocked by mutableStateOf(isEnabled && isRegistered())
        private set
    var isAuthenticating by mutableStateOf(false)
        private set
    private var backgroundedAt: Long? = null

    val kind: BiometryKind get() = biometrics.kind

    /// The profile row shows only to signed-in users on capable devices.
    val isOfferable: Boolean get() = isRegistered() && biometrics.kind.isAvailable

    /// Turning it on asks for biometrics once, so the user knows it works.
    suspend fun setEnabled(enabled: Boolean): Boolean {
        if (enabled) {
            if (!isOfferable) return isEnabled
            if (!authenticate()) return isEnabled
        }
        isEnabled = enabled
        flags.set(ENABLED, enabled)
        markOffered()
        return isEnabled
    }

    /// Suggest the lock right after a phone sign-in: once per account.
    val shouldOffer: Boolean get() = isOfferable && !isEnabled && flags.get(OFFERED) == null

    fun markOffered() = flags.set(OFFERED, true)

    fun didEnterBackground() {
        backgroundedAt = now()
    }

    fun didBecomeActive() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (isEnabled && isRegistered() && now() - since >= GRACE_MS) isLocked = true
    }

    /// Prompts and opens the app on success. If the device can no longer
    /// authenticate anyone (PIN removed), the lock turns itself off.
    suspend fun unlock() {
        if (!isLocked || isAuthenticating) return
        if (!isRegistered()) { isLocked = false; return }
        if (!biometrics.canAuthenticate) {
            isLocked = false
            isEnabled = false
            flags.set(ENABLED, false)
            return
        }
        if (authenticate()) isLocked = false
    }

    /// Sign-out: the next person starts without someone else's lock.
    fun reset() {
        flags.clear()
        isEnabled = false
        isLocked = false
    }

    private suspend fun authenticate(): Boolean {
        isAuthenticating = true
        try {
            return biometrics.authenticate(REASON)
        } finally {
            isAuthenticating = false
        }
    }
}
