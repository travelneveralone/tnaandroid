package com.travelneveralone.tna

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/// «Номера для входа» (iOS `Profile/LoginPhonesViewModel` + `LiveLoginPhonesService`).
/// A person may have several phones and not remember which one the account was
/// made with. Every number listed here signs into the same account (TNACloud
/// 0153, edge function login-phones). Attaching a number takes a code sent to
/// that number; the last number can't be removed.

/// A number the account can sign in with. [phone] is E.164, "+79161234567".
data class LoginPhone(val phone: String, val primary: Boolean)

sealed interface LoginPhoneOutcome {
    /// Done. [phones] is the account's list after the action, when the server sent it.
    data class Ok(val phones: List<LoginPhone>?) : LoginPhoneOutcome
    /// Refused or failed: the text to show as is.
    data class Refused(val message: String) : LoginPhoneOutcome
}

interface LoginPhonesBackend {
    /// The account's numbers, primary first. null when the server didn't answer.
    suspend fun list(): List<LoginPhone>?
    suspend fun sendCode(phone: String): LoginPhoneOutcome
    suspend fun confirm(code: String): LoginPhoneOutcome
    suspend fun remove(phone: String): LoginPhoneOutcome
}

/// Reading the function's answers. Pure, covered by LoginPhonesTest.
object LoginPhonesWire {
    const val OFFLINE = "Нет связи. Попробуй ещё раз"
    const val GENERIC = "Не получилось. Попробуй чуть позже"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun phones(obj: JsonObject?): List<LoginPhone>? {
        val raw = obj?.get("phones") ?: return null
        return raw.arrayOrEmpty().mapNotNull { el ->
            val row = el.jsonObjectOrNull() ?: return@mapNotNull null
            val phone = row.str("phone") ?: return@mapNotNull null
            LoginPhone(phone, row.bool("primary") == true)
        }
    }

    /// Body of any answer, 2xx or not (refusals carry `message`).
    fun outcome(body: String?): LoginPhoneOutcome {
        if (body == null) return LoginPhoneOutcome.Refused(OFFLINE)
        val obj = runCatching { json.parseToJsonElement(body).jsonObjectOrNull() }.getOrNull()
        if (obj?.bool("ok") == true) return LoginPhoneOutcome.Ok(phones(obj))
        val message = obj?.str("message")?.takeIf { it.isNotBlank() }
        return LoginPhoneOutcome.Refused(message ?: GENERIC)
    }

    fun list(body: String?): List<LoginPhone>? {
        if (body == null) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObjectOrNull() }.getOrNull()
        return phones(obj)
    }
}

/// In-memory numbers for mock mode and tests. Any code works except "000000".
class MockLoginPhones(
    var phones: MutableList<LoginPhone> = mutableListOf(LoginPhone("+79990000001", true)),
) : LoginPhonesBackend {
    private var pending: String? = null

    override suspend fun list(): List<LoginPhone> = phones.toList()

    override suspend fun sendCode(phone: String): LoginPhoneOutcome {
        if (phones.any { it.phone == phone }) return LoginPhoneOutcome.Refused("Этот номер уже привязан к твоему аккаунту")
        pending = phone
        return LoginPhoneOutcome.Ok(null)
    }

    override suspend fun confirm(code: String): LoginPhoneOutcome {
        val phone = pending ?: return LoginPhoneOutcome.Refused("Сначала запроси код")
        if (code == "000000") return LoginPhoneOutcome.Refused("Неверный код")
        pending = null
        phones.add(LoginPhone(phone, false))
        return LoginPhoneOutcome.Ok(phones.toList())
    }

    override suspend fun remove(phone: String): LoginPhoneOutcome {
        if (phones.size <= 1) return LoginPhoneOutcome.Refused("Это единственный номер для входа - его нельзя удалить")
        val wasPrimary = phones.firstOrNull { it.phone == phone }?.primary == true
        phones.removeAll { it.phone == phone }
        if (wasPrimary && phones.isNotEmpty()) phones[0] = phones[0].copy(primary = true)
        return LoginPhoneOutcome.Ok(phones.toList())
    }
}

/// Screen state and rules (iOS `LoginPhonesViewModel`, same steps and texts).
class LoginPhonesFlow(
    private val backend: LoginPhonesBackend,
    /// The server moved the primary number: the profile header shows the new one.
    private val onPrimaryChanged: (String) -> Unit = {},
) {
    sealed interface Step {
        data object Numbers : Step
        data object Phone : Step
        /// The code went to this number (E.164).
        data class Code(val phone: String) : Step
    }

    companion object {
        /// Same cap as the server (login-phones-rules.mjs MAX_PHONES).
        const val MAX_PHONES = 5
        const val CODE_LENGTH = 6
    }

    var phones by mutableStateOf<List<LoginPhone>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var step by mutableStateOf<Step>(Step.Numbers)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var code by mutableStateOf("")

    val canRemove: Boolean get() = phones.size > 1
    val canAddMore: Boolean get() = phones.size < MAX_PHONES
    val codeIsComplete: Boolean get() = code.count(Char::isDigit) == CODE_LENGTH

    suspend fun load() {
        val list = backend.list()
        if (list != null) {
            phones = list
            loadFailed = false
        } else {
            loadFailed = phones.isEmpty()
        }
        loaded = true
    }

    fun startAdding() {
        error = null
        notice = null
        code = ""
        step = Step.Phone
    }

    fun cancelAdding() {
        error = null
        code = ""
        step = Step.Numbers
    }

    fun editNumber() {
        error = null
        code = ""
        step = Step.Phone
    }

    suspend fun sendCode(phone: String) {
        if (busy) return
        error = null
        if (phones.any { PhoneNumber.digits(it.phone) == PhoneNumber.digits(phone) }) {
            error = "Этот номер уже привязан к твоему аккаунту"
            return
        }
        busy = true
        try {
            when (val result = backend.sendCode(phone)) {
                is LoginPhoneOutcome.Ok -> { code = ""; step = Step.Code(phone) }
                is LoginPhoneOutcome.Refused -> error = result.message
            }
        } finally {
            busy = false
        }
    }

    suspend fun resendCode() {
        val current = step as? Step.Code ?: return
        sendCode(current.phone)
    }

    suspend fun confirm() {
        val current = step as? Step.Code ?: return
        if (busy) return
        val entered = code.filter(Char::isDigit)
        if (entered.length != CODE_LENGTH) {
            error = "В коде 6 цифр"
            return
        }
        busy = true
        error = null
        try {
            when (val result = backend.confirm(entered)) {
                is LoginPhoneOutcome.Ok -> {
                    val list = result.phones ?: backend.list()
                    if (list != null) phones = list
                    notice = "Номер ${PhoneNumber.pretty(current.phone)} привязан. Теперь можно входить и по нему"
                    code = ""
                    step = Step.Numbers
                }
                is LoginPhoneOutcome.Refused -> error = result.message
            }
        } finally {
            busy = false
        }
    }

    suspend fun remove(item: LoginPhone) {
        if (busy || !canRemove) return
        busy = true
        error = null
        notice = null
        val primaryBefore = phones.firstOrNull { it.primary }?.phone
        try {
            when (val result = backend.remove(item.phone)) {
                is LoginPhoneOutcome.Ok -> {
                    phones = result.phones ?: phones.filterNot { it.phone == item.phone }
                    val primaryNow = phones.firstOrNull { it.primary }?.phone
                    if (primaryNow != null && primaryNow != primaryBefore) onPrimaryChanged(primaryNow)
                }
                is LoginPhoneOutcome.Refused -> error = result.message
            }
        } finally {
            busy = false
        }
    }
}
