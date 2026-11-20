package com.travelneveralone.tna

import kotlin.math.ceil

/// Port of iOS `CodeCooldown` (Core/Services/CodeCooldown.swift).
///
/// When the next login code may go out, and whether the code already sent can
/// still be typed in. Every code is a paid SMS, so the app never asks for one
/// the server would refuse anyway or that the person does not need: going back
/// to check the number and returning to the same one reuses the code in flight.
///
/// Mirrors the server (TNACloud): [RESEND_SECONDS] = `sms_settings.cooldown_seconds`
/// (0145), [CODE_LIFETIME_SECONDS] = Auth SMS OTP expiry (`GOTRUE_SMS_OTP_EXP`).
class CodeCooldown {
    companion object {
        /// Pause between two codes to one number.
        const val RESEND_SECONDS = 60L
        /// How long a sent code is accepted by the server.
        const val CODE_LIFETIME_SECONDS = 300L

        /// "0:42", "1:05"; an hour and more reads as whole hours: "3 ч".
        fun countdown(seconds: Long): String {
            val s = seconds.coerceAtLeast(0)
            if (s >= 3600) return "${ceil(s / 3600.0).toLong()} ч"
            return "%d:%02d".format(s / 60, s % 60)
        }
    }

    /// Earliest moment (epoch ms) a new code may be requested, per number (digits).
    private val nextAllowed = mutableMapOf<String, Long>()
    /// The last code that went out. Only this one can still be typed in.
    private var lastPhone: String? = null
    private var lastAt = 0L

    /// A code went out to [phone].
    fun recordSent(phone: String, nowMs: Long) {
        val key = PhoneNumber.digits(phone)
        nextAllowed[key] = nowMs + RESEND_SECONDS * 1000
        lastPhone = key
        lastAt = nowMs
    }

    /// The server refused and said when to come back (pause, daily limit).
    fun recordRefusal(phone: String, retryAfterSec: Long, nowMs: Long) {
        if (retryAfterSec <= 0) return
        nextAllowed[PhoneNumber.digits(phone)] = nowMs + retryAfterSec * 1000
    }

    /// Whole seconds until a new code may be requested; 0 = right now.
    fun secondsUntilNewCode(phone: String, nowMs: Long): Long {
        val at = nextAllowed[PhoneNumber.digits(phone)] ?: return 0
        return ceil((at - nowMs) / 1000.0).toLong().coerceAtLeast(0)
    }

    /// The last code went to this number and the server still accepts it.
    fun hasLiveCode(phone: String, nowMs: Long): Boolean {
        if (lastPhone == null || lastPhone != PhoneNumber.digits(phone)) return false
        return nowMs - lastAt < CODE_LIFETIME_SECONDS * 1000
    }

    /// Seconds until «Получить код» can do anything for this number; 0 = now.
    /// A live code makes it 0 even inside the resend pause: the button then
    /// reuses that code (no SMS), so it must stay pressable.
    fun secondsUntilCodeStep(phone: String, nowMs: Long): Long =
        if (hasLiveCode(phone, nowMs)) 0 else secondsUntilNewCode(phone, nowMs)

    /// The code was used, or it belonged to a session that is gone.
    fun forgetLiveCode() {
        lastPhone = null
    }
}
