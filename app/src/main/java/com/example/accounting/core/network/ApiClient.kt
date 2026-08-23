package com.example.accounting.core.network

import android.content.Context
import com.example.accounting.core.security.SecureStorage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ApiClient configures Retrofit, Moshi, and OkHttp with:
 * - Idempotency-Key injection
 * - Secure Bearer Token Authentication
 * - Device & Client Identification headers
 * - Exponential backoff / Timeout configurations
 */
class ApiClient(
    private val context: Context,
    private val secureStorage: SecureStorage = SecureStorage.getInstance(context)
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        // Attach bearer token if authenticated
        secureStorage.getAuthToken()?.let { token ->
            requestBuilder.header("Authorization", "Bearer $token")
        }

        // Attach API Key if configured
        secureStorage.getApiKey()?.let { apiKey ->
            requestBuilder.header("X-API-Key", apiKey)
        }

        // Device identifier for multi-device outbox & auditing
        requestBuilder.header("X-Device-Id", secureStorage.getDeviceId())
        requestBuilder.header("X-Client-Platform", "Android-Native")
        requestBuilder.header("Accept", "application/json")

        chain.proceed(requestBuilder.build())
    }

    private val idempotencyInterceptor = Interceptor { chain ->
        val original = chain.request()
        val method = original.method

        // If it's a mutating request (POST, PUT, DELETE, PATCH) and doesn't already have an Idempotency-Key
        if ((method == "POST" || method == "DELETE" || method == "PUT" || method == "PATCH") &&
            original.header("Idempotency-Key") == null
        ) {
            val autoKey = UUID.randomUUID().toString()
            val newRequest = original.newBuilder()
                .header("Idempotency-Key", autoKey)
                .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(original)
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(idempotencyInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: AccountingApiService by lazy {
        val baseUrl = secureStorage.getApiBaseUrl()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AccountingApiService::class.java)
    }

    companion object {
        @Volatile
        private var instance: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
