package com.pos.cosmos.services

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ItemTranslation(
    val id: Int = 0,
    val companyCode: Int = 0,
    val itemId: Int = 0,
    val itemName: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "",
    val translatedName: String? = null,
    val translatedDescription: String? = null,
    val loanword: String? = null,
    val active: String = "Y"
)

object TranslationService {
    private const val TAG = "TranslationService"
    private var cachedTranslations: Map<Int, ItemTranslation> = emptyMap()
    private var cachedLanguage: String = "English"
    private var isLoading: Boolean = false

    suspend fun loadTranslations(
        baseUrl: String,
        token: String,
        sourceLanguage: String = "English",
        targetLanguage: String
    ): Map<Int, ItemTranslation> {
        // English doesn't need translations - return empty map
        if (targetLanguage == "English" || targetLanguage == "English US") {
            cachedTranslations = emptyMap()
            cachedLanguage = targetLanguage
            return cachedTranslations
        }

        // Return cached if already loaded for this language
        if (cachedLanguage == targetLanguage && cachedTranslations.isNotEmpty()) {
            return cachedTranslations
        }

        // Avoid concurrent loading
        if (isLoading) {
            return cachedTranslations
        }

        isLoading = true

        return withContext(Dispatchers.IO) {
            try {
                val urlString = "$baseUrl/item-language-mapping/by-languages?sourceLanguage=$sourceLanguage&targetLanguage=$targetLanguage"
                Log.d(TAG, "Loading translations from: $urlString")

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                if (connection.responseCode == 200) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val gson = Gson()
                    val type = object : TypeToken<List<ItemTranslation>>() {}.type
                    val translations: List<ItemTranslation> = gson.fromJson(content, type)

                    cachedTranslations = translations.associateBy { it.itemId }
                    cachedLanguage = targetLanguage
                    Log.d(TAG, "Loaded ${cachedTranslations.size} translations for $targetLanguage")
                    cachedTranslations
                } else {
                    Log.e(TAG, "Failed to load translations: ${connection.responseCode}")
                    emptyMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading translations", e)
                emptyMap()
            } finally {
                isLoading = false
            }
        }
    }

    fun getTranslatedName(itemId: Int, originalName: String): String {
        val translation = cachedTranslations[itemId]
        return if (translation?.translatedName?.isNotBlank() == true) {
            translation.translatedName
        } else if (translation?.loanword?.isNotBlank() == true) {
            translation.loanword
        } else {
            originalName
        }
    }

    fun getTranslatedDescription(itemId: Int, originalDescription: String): String {
        val translation = cachedTranslations[itemId]
        return if (translation?.translatedDescription?.isNotBlank() == true) {
            translation.translatedDescription
        } else {
            originalDescription
        }
    }

    fun getCurrentLanguage(): String = cachedLanguage

    fun clearCache() {
        cachedTranslations = emptyMap()
        cachedLanguage = "English"
    }
}
