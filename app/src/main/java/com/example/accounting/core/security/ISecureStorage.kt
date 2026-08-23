package com.example.accounting.core.security

/**
 * Interface abstraction for secure token, credential, and company-scoped configuration storage.
 * Keeps security implementation details decoupled from business and domain logic.
 */
interface ISecureStorage {
    fun getDeviceId(): String
    fun getAuthToken(): String?
    fun setAuthToken(token: String?)
    fun getRefreshToken(): String?
    fun setRefreshToken(token: String?)
    fun getApiKey(): String?
    fun setApiKey(apiKey: String?)
    fun getApiBaseUrl(): String
    fun setApiBaseUrl(url: String)
    fun getActiveCompanyId(): String?
    fun setActiveCompanyId(companyId: String?)
    fun isBiometricEnabled(): Boolean
    fun setBiometricEnabled(enabled: Boolean)
    fun getCompanySetting(companyId: String, key: String, defaultValue: String): String
    fun setCompanySetting(companyId: String, key: String, value: String)
    fun getCompanyBooleanSetting(companyId: String, key: String, defaultValue: Boolean): Boolean
    fun setCompanyBooleanSetting(companyId: String, key: String, value: Boolean)
    fun clearSession()
}
