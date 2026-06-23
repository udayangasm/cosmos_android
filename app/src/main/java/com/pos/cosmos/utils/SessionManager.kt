package com.pos.cosmos.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SessionManager handles secure storage of user credentials using EncryptedSharedPreferences.
 * This allows "Remember Me" functionality for auto-login.
 */
class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Save user credentials securely
     */
    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER_ME, true)
            apply()
        }
    }

    /**
     * Get saved username
     */
    fun getSavedUsername(): String? {
        return if (isRememberMeEnabled()) {
            sharedPreferences.getString(KEY_USERNAME, null)
        } else null
    }

    /**
     * Get saved password
     */
    fun getSavedPassword(): String? {
        return if (isRememberMeEnabled()) {
            sharedPreferences.getString(KEY_PASSWORD, null)
        } else null
    }

    /**
     * Check if remember me is enabled and credentials exist
     */
    fun isRememberMeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, false)
    }

    /**
     * Check if saved credentials exist for auto-login
     */
    fun hasSavedCredentials(): Boolean {
        return isRememberMeEnabled() &&
                !getSavedUsername().isNullOrEmpty() &&
                !getSavedPassword().isNullOrEmpty()
    }

    /**
     * Clear all saved credentials (used during logout)
     */
    fun clearCredentials() {
        sharedPreferences.edit().apply {
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            remove(KEY_TOKEN)
            putBoolean(KEY_REMEMBER_ME, false)
            apply()
        }
    }

    /** Persist the auth token so background workers (sync) can reach the API. */
    fun saveToken(token: String?) {
        sharedPreferences.edit().apply {
            if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun getToken(): String? = sharedPreferences.getString(KEY_TOKEN, null)

    companion object {
        private const val PREFS_NAME = "cosmos_secure_prefs"
        private const val KEY_USERNAME = "saved_username"
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_TOKEN = "auth_token"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
