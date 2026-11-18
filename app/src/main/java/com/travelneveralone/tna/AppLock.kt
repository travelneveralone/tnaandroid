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
///
/// The same switch covers sign-out: with the lock on, signing out keeps the
/// session aside and the login sheet brings it back with biometrics alone
/// ([AppLock.confirmQuickSignIn] + `AppModel.quickSignIn`), so coming back
/// never costs an SMS.

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

/// How a biometric check without the PIN fallback ended (iOS `BiometricCheck`).
enum class BiometricCheck {
    Passed,
    /// The person closed the prompt: nothing to say about it.
    Cancelled,
    /// Not recognised.
    Failed,
    /// Too many failed tries: biometrics are off until the device PIN is
    /// entered on the lock screen. Retrying in the app cannot help.
    LockedOut,
    /// No enrolled biometrics or no sensor available right now.
    Unavailable,
}

/// BiometricPrompt's error codes, reduced to what the login sheet says.
fun biometricCheckFor(errorCode: Int): BiometricCheck = when (errorCode) {
    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED -> BiometricCheck.Cancelled
    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricCheck.LockedOut
    BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricCheck.Unavailable
    else -> BiometricCheck.Failed
}

interface Biometrics {
    val kind: BiometryKind
    /// Biometrics or a device PIN exist. False once the PIN is removed.
    val canAuthenticate: Boolean
    /// Biometrics with the device PIN as fallback. True on success.
    suspend fun authenticate(reason: String): Boolean
    /// Biometrics only, no PIN fallback. Signing back in after a sign-out
    /// uses this: knowing the phone's PIN is not enough there, the fallback
    /// is an SMS code.
    suspend fun authenticateBiometricsOnly(reason: String): BiometricCheck
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

    override suspend fun authenticate(reason: String): Boolean =
        prompt(reason, allowDeviceCredential = true) == BiometricCheck.Passed

    override suspend fun authenticateBiometricsOnly(reason: String): BiometricCheck =
        prompt(reason, allowDeviceCredential = false)

    private suspend fun prompt(reason: String, allowDeviceCredential: Boolean): BiometricCheck {
        val host = activity?.get() ?: return BiometricCheck.Unavailable
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(BiometricCheck.Passed)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(biometricCheckFor(errorCode))
                    }
                    // onAuthenticationFailed: one bad try, the prompt stays up.
                },
            )
            val builder = BiometricPrompt.PromptInfo.Builder().setTitle(reason)
            if (allowDeviceCredential) {
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            } else {
                // Without the device credential the prompt must carry its own cancel button.
                builder.setAllowedAuthenticators(BIOMETRIC_WEAK).setNegativeButtonText("Отмена")
            }
            val info = builder.build()
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
    /// What a biometrics-only check returns when [succeeds] is false.
    var failure: BiometricCheck = BiometricCheck.Failed,
) : Biometrics {
    var prompts = 0
        private set

    override suspend fun authenticate(reason: String): Boolean {
        prompts += 1
        return succeeds
    }

    override suspend fun authenticateBiometricsOnly(reason: String): BiometricCheck {
        prompts += 1
        return if (succeeds) BiometricCheck.Passed else failure
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

    /// The login sheet offers a biometric sign-in only where the device has biometrics.
    val canConfirmQuickSignIn: Boolean get() = biometrics.kind.isAvailable

    /// Biometric check in front of `AppModel.quickSignIn`. A second tap while
    /// the prompt is up counts as a cancel: the first check will answer.
    suspend fun confirmQuickSignIn(): BiometricCheck {
        if (!canConfirmQuickSignIn) return BiometricCheck.Unavailable
        if (isAuthenticating) return BiometricCheck.Cancelled
        isAuthenticating = true
        try {
            return biometrics.authenticateBiometricsOnly(REASON)
        } finally {
            isAuthenticating = false
        }
    }

    /// After a biometric sign-in the lock is on again without a second prompt:
    /// it was on when this person signed out (that is why the session was
    /// kept) and they have just passed biometrics. The app stays open.
    fun restoreAfterQuickSignIn() {
        isEnabled = true
        isLocked = false
        flags.set(ENABLED, true)
        markOffered()
    }

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
