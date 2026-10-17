package com.example.youtubemusicdownloder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class UserAccountManager(context: Context) {

    private val prefs = context.getSharedPreferences("user_account_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(hasValidCookie())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _accountEmail = MutableStateFlow(prefs.getString("account_email", "") ?: "")
    val accountEmail: StateFlow<String> = _accountEmail.asStateFlow()

    fun getCookie(): String {
        return prefs.getString("yt_cookie", "") ?: ""
    }

    fun saveCookie(cookie: String, email: String = "") {
        val cleanCookie = cookie.trim()
        prefs.edit()
            .putString("yt_cookie", cleanCookie)
            .putString("account_email", email.trim())
            .apply()
        _isLoggedIn.value = hasValidCookie()
        _accountEmail.value = email.trim()
    }

    fun clearAccount() {
        prefs.edit()
            .remove("yt_cookie")
            .remove("account_email")
            .apply()
        _isLoggedIn.value = false
        _accountEmail.value = ""
    }

    fun hasValidCookie(): Boolean {
        val cookie = prefs.getString("yt_cookie", "") ?: ""
        return cookie.contains("SAPISID") || cookie.contains("__Secure-3PAPISID") || cookie.contains("LOGIN_INFO")
    }

    companion object {
        fun extractCookieValue(cookie: String, key: String): String? {
            val parts = cookie.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("$key=")) {
                    return trimmed.substringAfter("$key=")
                }
            }
            return null
        }

        fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
            val timestamp = System.currentTimeMillis() / 1000
            val raw = "$timestamp $sapisid $origin"
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "${timestamp}_$hex"
        }
    }
}
