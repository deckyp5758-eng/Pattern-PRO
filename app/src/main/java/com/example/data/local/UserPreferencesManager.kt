package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class UserPreferencesManager(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val secureCredentialPrefs: SharedPreferences = appContext.getSharedPreferences(
        SECURE_CREDENTIAL_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // Hapus sisa kredensial plaintext dari versi lama jika masih ada
        purgeLegacyPlaintextCredentials()
    }

    companion object {
        private const val PREFS_NAME = "hub_trader_user_prefs"
        private const val SECURE_CREDENTIAL_PREFS_NAME = "hub_secure_credentials"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "hub_pattern_pro_cred_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val LEGACY_KEY_METAAPI_ACCOUNT_ID = "pref_metaapi_account_id"
        private const val LEGACY_KEY_METAAPI_AUTH_TOKEN = "pref_metaapi_auth_token"

        private const val KEY_ENC_METAAPI_ACCOUNT_ID = "enc_metaapi_account_id_v2"
        private const val KEY_ENC_METAAPI_AUTH_TOKEN = "enc_metaapi_auth_token_v2"
        private const val KEY_METAAPI_REGION = "pref_metaapi_region"

        private const val KEY_GITHUB_REPOSITORY = "pref_github_repository"
        private const val KEY_SELECTED_PROVIDER = "pref_selected_provider"
        private const val KEY_BROKER_SUFFIX = "pref_broker_suffix"
        private const val KEY_TRADE_NOTIFICATIONS_ENABLED = "pref_trade_notifications_enabled"
        private const val KEY_OTA_ALLOW_DOWNGRADE = "pref_ota_allow_downgrade"

        @Volatile
        private var INSTANCE: UserPreferencesManager? = null

        @Volatile
        private var jvmTestAesKey: SecretKey? = null

        fun getInstance(context: Context): UserPreferencesManager {
            val appCtx = context.applicationContext
            val existing = INSTANCE
            val instance = if (existing != null && existing.appContext === appCtx) {
                existing
            } else {
                synchronized(this) {
                    val locked = INSTANCE
                    if (locked != null && locked.appContext === appCtx) {
                        locked
                    } else {
                        UserPreferencesManager(appCtx).also { INSTANCE = it }
                    }
                }
            }
            instance.purgeLegacyPlaintextCredentials()
            return instance
        }

        fun maskSecretForDisplay(secret: String): String {
            val clean = secret.trim()
            if (clean.isEmpty()) return ""
            return if (clean.length <= 4) {
                "••••"
            } else {
                "••••${clean.takeLast(4)}"
            }
        }
    }

    private fun isRobolectricEnvironment(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        if (isRobolectricEnvironment()) {
            // Robolectric JVM tidak menyediakan provider AndroidKeyStore secara native;
            // tetap gunakan kunci AES-256 acak berbasis KeyGenerator (tidak hardcoded).
            return jvmTestAesKey ?: synchronized(UserPreferencesManager::class.java) {
                jvmTestAesKey ?: KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey().also { jvmTestAesKey = it }
            }
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptValue(plaintext: String): String {
        val clean = plaintext.trim()
        if (clean.isEmpty()) return ""
        val key = getOrCreateEncryptionKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(clean.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$ivB64:$dataB64"
    }

    private fun decryptValue(payload: String?): String {
        if (payload.isNullOrBlank()) return ""
        val parts = payload.split(":")
        if (parts.size != 2) return ""
        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8).trim()
        }.getOrDefault("")
    }

    private fun purgeLegacyPlaintextCredentials() {
        if (prefs.contains(LEGACY_KEY_METAAPI_ACCOUNT_ID) || prefs.contains(LEGACY_KEY_METAAPI_AUTH_TOKEN)) {
            prefs.edit()
                .remove(LEGACY_KEY_METAAPI_ACCOUNT_ID)
                .remove(LEGACY_KEY_METAAPI_AUTH_TOKEN)
                .remove(KEY_METAAPI_REGION)
                .commit()
        }
    }

    fun saveMetaApiCredentials(accountId: String, authToken: String, region: String? = null) {
        val cleanAcc = accountId.trim()
        val cleanTok = authToken.trim()
        if (cleanAcc.isBlank() || cleanTok.isBlank()) return

        purgeLegacyPlaintextCredentials()
        val encAcc = encryptValue(cleanAcc)
        val encTok = encryptValue(cleanTok)

        secureCredentialPrefs.edit().apply {
            putString(KEY_ENC_METAAPI_ACCOUNT_ID, encAcc)
            putString(KEY_ENC_METAAPI_AUTH_TOKEN, encTok)
            if (!region.isNullOrBlank()) {
                putString(KEY_METAAPI_REGION, region.trim())
            }
            commit()
        }
    }

    fun getMetaApiAccountId(): String {
        purgeLegacyPlaintextCredentials()
        return decryptValue(secureCredentialPrefs.getString(KEY_ENC_METAAPI_ACCOUNT_ID, null))
    }

    fun getMetaApiAuthToken(): String {
        purgeLegacyPlaintextCredentials()
        return decryptValue(secureCredentialPrefs.getString(KEY_ENC_METAAPI_AUTH_TOKEN, null))
    }

    fun getMaskedAccountIdHint(): String = maskSecretForDisplay(getMetaApiAccountId())

    fun getMaskedTokenHint(): String = maskSecretForDisplay(getMetaApiAuthToken())

    /**
     * Untuk audit keamanan/pengujian: memastikan nilai tersimpan adalah ciphertext AES-GCM terenkripsi.
     */
    fun getEncryptedTokenPayloadForAudit(): String {
        return secureCredentialPrefs.getString(KEY_ENC_METAAPI_AUTH_TOKEN, "").orEmpty()
    }

    fun getMetaApiRegion(): String? {
        val reg = secureCredentialPrefs.getString(KEY_METAAPI_REGION, null)?.trim()
        return if (!reg.isNullOrBlank()) reg else null
    }

    fun clearMetaApiCredentials() {
        purgeLegacyPlaintextCredentials()
        secureCredentialPrefs.edit()
            .remove(KEY_ENC_METAAPI_ACCOUNT_ID)
            .remove(KEY_ENC_METAAPI_AUTH_TOKEN)
            .remove(KEY_METAAPI_REGION)
            .commit()
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

    fun saveAllowOtaDowngrade(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OTA_ALLOW_DOWNGRADE, enabled).apply()
    }

    fun isAllowOtaDowngrade(): Boolean {
        return prefs.getBoolean(KEY_OTA_ALLOW_DOWNGRADE, false)
    }
}
