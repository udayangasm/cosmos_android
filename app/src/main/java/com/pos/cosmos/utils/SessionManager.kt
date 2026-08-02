package com.pos.cosmos.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * SessionManager handles secure storage of user credentials using EncryptedSharedPreferences.
 * This allows "Remember Me" functionality for auto-login.
 *
 * The encrypted store is built defensively: the AndroidKeyStore master key can
 * become unreadable after an app or OS update (and budget devices have the
 * flakiest KeyStore implementations), which makes
 * `EncryptedSharedPreferences.create` throw. Since this class is touched during
 * the first composition of LoginScreen, an uncaught throw there kills the app on
 * every launch with no way out short of reinstalling. Instead we wipe the bad
 * state and rebuild, and if even that fails we drop to plain prefs so the app
 * still runs — the worst case is the user logging in again.
 */
class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences = buildPreferences(context)

    /** True when credentials are being kept in plain prefs because this device's KeyStore is unusable. */
    var isUsingFallbackStorage: Boolean = false
        private set

    private fun buildPreferences(context: Context): SharedPreferences {
        createEncrypted(context)?.let { return it }

        Log.w(TAG, "Encrypted prefs unreadable — clearing master key and prefs, rebuilding")
        clearCorruptState(context)
        createEncrypted(context)?.let { return it }

        Log.e(TAG, "KeyStore unusable on this device — falling back to plain session storage")
        isUsingFallbackStorage = true
        return context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun createEncrypted(context: Context): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // GeneralSecurityException, AEADBadTagException, InvalidProtocolBufferException,
        // KeyStoreException — all mean the same thing here: the store is unreadable.
        Log.e(TAG, "EncryptedSharedPreferences.create failed", t)
        null
    }

    /** Drop the undecryptable prefs file and the master key so the next create() starts clean. */
    private fun clearCorruptState(context: Context) {
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            .onFailure { Log.w(TAG, "could not delete $PREFS_NAME", it) }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }.onFailure { Log.w(TAG, "could not delete master key", it) }
    }

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
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "cosmos_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "cosmos_session_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
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
