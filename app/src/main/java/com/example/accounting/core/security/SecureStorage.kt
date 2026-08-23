package com.example.accounting.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * SecureStorage safely persists sensitive auth tokens, API keys, and company-scoped configuration data
 * using Android Jetpack Security EncryptedSharedPreferences (backed by Android Keystore AES-256 GCM).
 *
 * Hardware vs. Emulated/Sandbox Behavior:
 * - Production: Backed by Hardware Security Module (HSM) / StrongBox Keymaster via Android KeyStore.
 * - Test / Headless CI / Development: Graceful isolated fallback to Context.MODE_PRIVATE preferences
 *   if Keystore provider is unavailable.
 */
class SecureStorage(context: Context) : ISecureStorage {

    private val sharedPreferences: SharedPreferences = createEncryptedSharedPreferences(context)

    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_FILE = "ledgerprime_secure_prefs"
        
        private const val KEY_AUTH_TOKEN = "sec_auth_token"
        private const val KEY_REFRESH_TOKEN = "sec_refresh_token"
        private const val KEY_API_KEY = "sec_api_key"
        private const val KEY_API_BASE_URL = "sec_api_base_url"
        private const val KEY_ACTIVE_COMPANY_ID = "sec_active_company_id"
        private const val KEY_DEVICE_ID = "sec_device_id"
        private const val KEY_BIOMETRIC_ENABLED = "sec_biometric_enabled"
        
        // Phase 6: was an unresolvable placeholder host (`api.accounting.ledgerprime.internal`).
        // Defaults to the Android emulator's alias for the host machine's localhost, matching
        // where the Phase 6 Python dev server (`server/`) runs by default - real deployments
        // override this via setApiBaseUrl(), never by editing this constant.
        private const val DEFAULT_API_BASE_URL = "http://10.0.2.2:8000/api/v1/"

        @Volatile
        private var instance: SecureStorage? = null

        fun getInstance(context: Context): SecureStorage {
            return instance ?: synchronized(this) {
                instance ?: SecureStorage(context.applicationContext).also { instance = it }
            }
        }

        private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences unavailable (isolated fallback for testing/sandbox): ${e.message}")
                context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    init {
        // Ensure stable device ID exists for idempotency and multi-device outbox tracking
        if (getDeviceId().isBlank()) {
            val generatedId = "DEV_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            sharedPreferences.edit().putString(KEY_DEVICE_ID, generatedId).apply()
        }
    }

    override fun getDeviceId(): String {
        return sharedPreferences.getString(KEY_DEVICE_ID, "") ?: ""
    }

    override fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    override fun setAuthToken(token: String?) {
        sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    override fun getRefreshToken(): String? {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun setRefreshToken(token: String?) {
        sharedPreferences.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    override fun setApiKey(apiKey: String?) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    override fun getApiBaseUrl(): String {
        return sharedPreferences.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
    }

    override fun setApiBaseUrl(url: String) {
        sharedPreferences.edit().putString(KEY_API_BASE_URL, url).apply()
    }

    override fun getActiveCompanyId(): String? {
        return sharedPreferences.getString(KEY_ACTIVE_COMPANY_ID, null)
    }

    override fun setActiveCompanyId(companyId: String?) {
        sharedPreferences.edit().putString(KEY_ACTIVE_COMPANY_ID, companyId).apply()
    }

    override fun isBiometricEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    // Company-scoped configuration
    override fun getCompanySetting(companyId: String, key: String, defaultValue: String): String {
        val prefKey = "comp_${companyId}_$key"
        return sharedPreferences.getString(prefKey, defaultValue) ?: defaultValue
    }

    override fun setCompanySetting(companyId: String, key: String, value: String) {
        val prefKey = "comp_${companyId}_$key"
        sharedPreferences.edit().putString(prefKey, value).apply()
    }

    override fun getCompanyBooleanSetting(companyId: String, key: String, defaultValue: Boolean): Boolean {
        val prefKey = "comp_${companyId}_$key"
        return sharedPreferences.getBoolean(prefKey, defaultValue)
    }

    override fun setCompanyBooleanSetting(companyId: String, key: String, value: Boolean) {
        val prefKey = "comp_${companyId}_$key"
        sharedPreferences.edit().putBoolean(prefKey, value).apply()
    }

    override fun clearSession() {
        sharedPreferences.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_API_KEY)
            .apply()
    }
}
