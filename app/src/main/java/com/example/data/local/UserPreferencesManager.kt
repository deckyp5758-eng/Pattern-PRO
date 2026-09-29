package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "hub_trader_user_prefs"
        private const val KEY_METAAPI_ACCOUNT_ID = "pref_metaapi_account_id"
        private const val KEY_METAAPI_AUTH_TOKEN = "pref_metaapi_auth_token"
        private const val KEY_METAAPI_REGION = "pref_metaapi_region"
        private const val KEY_GITHUB_REPOSITORY = "pref_github_repository"
        private const val KEY_SELECTED_PROVIDER = "pref_selected_provider"
        private const val KEY_BROKER_SUFFIX = "pref_broker_suffix"
        private const val KEY_TRADE_NOTIFICATIONS_ENABLED = "pref_trade_notifications_enabled"

        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        fun getInstance(context: Context): UserPreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun saveMetaApiCredentials(accountId: String, authToken: String, region: String? = null) {
        prefs.edit().apply {
            putString(KEY_METAAPI_ACCOUNT_ID, accountId.trim())
            putString(KEY_METAAPI_AUTH_TOKEN, authToken.trim())
            if (!region.isNullOrBlank()) {
                putString(KEY_METAAPI_REGION, region.trim())
            }
            apply()
        }
    }

    fun getMetaApiAccountId(): String {
        return prefs.getString(KEY_METAAPI_ACCOUNT_ID, "").orEmpty().trim()
    }

    fun getMetaApiAuthToken(): String {
        return prefs.getString(KEY_METAAPI_AUTH_TOKEN, "").orEmpty().trim()
    }

    fun getMetaApiRegion(): String? {
        val reg = prefs.getString(KEY_METAAPI_REGION, null)?.trim()
        return if (!reg.isNullOrBlank()) reg else null
    }

    fun clearMetaApiCredentials() {
        prefs.edit().apply {
            remove(KEY_METAAPI_ACCOUNT_ID)
            remove(KEY_METAAPI_AUTH_TOKEN)
            remove(KEY_METAAPI_REGION)
            apply()
        }
    }

    fun hasCustomCredentials(): Boolean {
        val acc = getMetaApiAccountId()
        val tok = getMetaApiAuthToken()
        return acc.isNotBlank() && tok.isNotBlank()
    }

    fun saveGitHubRepository(repo: String) {
        if (repo.isNotBlank()) {
            prefs.edit().putString(KEY_GITHUB_REPOSITORY, repo.trim()).apply()
        }
    }

    fun getGitHubRepository(): String {
        return prefs.getString(KEY_GITHUB_REPOSITORY, "").orEmpty().trim()
    }

    fun saveSelectedProvider(providerId: String) {
        if (providerId.isNotBlank()) {
            prefs.edit().putString(KEY_SELECTED_PROVIDER, providerId.trim()).apply()
        }
    }

    fun getSelectedProvider(): String {
        return prefs.getString(KEY_SELECTED_PROVIDER, "").orEmpty().trim()
    }

    fun saveBrokerSuffix(suffix: String) {
        prefs.edit().putString(KEY_BROKER_SUFFIX, suffix.trim()).apply()
    }

    fun getBrokerSuffix(): String {
        return prefs.getString(KEY_BROKER_SUFFIX, ".sml").orEmpty()
    }

    fun saveTradeNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRADE_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isTradeNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_TRADE_NOTIFICATIONS_ENABLED, true)
    }
}
