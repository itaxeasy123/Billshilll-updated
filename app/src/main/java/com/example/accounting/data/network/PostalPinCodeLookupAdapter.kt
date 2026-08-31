package com.example.accounting.data.network

import com.example.accounting.domain.profile.PinCodeLookupAdapter
import com.example.accounting.domain.profile.PinCodeLookupResult
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
internal data class PostOfficeDto(
    val Name: String? = null,
    val District: String? = null,
    val State: String? = null,
    val Country: String? = null
)

@JsonClass(generateAdapter = true)
internal data class PinCodeApiResponseDto(
    val Status: String? = null,
    val PostOffice: List<PostOfficeDto>? = null
)

/**
 * Real implementation of [PinCodeLookupAdapter] against India Post's public, free, unauthenticated
 * PIN-code API (`api.postalpincode.in`) - a standalone OkHttp/Retrofit-free client (deliberately
 * NOT [com.example.accounting.core.network.ApiClient], whose interceptors attach this app's own
 * Bearer/API-key/device-id headers meant for the LedgerPrime sync server - those must never be
 * sent to a third-party public API). Only the [District]/[State]/[Country] of the first returned
 * Post Office are used; never fabricates a result on a non-Success status or network failure.
 */
class PostalPinCodeLookupAdapter : PinCodeLookupAdapter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter<List<PinCodeApiResponseDto>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, PinCodeApiResponseDto::class.java)
    )

    override suspend fun lookup(pinCode: String): PinCodeLookupResult {
        if (!pinCode.matches(Regex("^[1-9][0-9]{5}$"))) {
            return PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "Enter a valid 6-digit PIN code.")
        }
        // OkHttp's execute() blocks the calling thread - this app's own dispatch (viewModelScope.
        // launch defaults to Main) must never run it directly, or Android throws
        // NetworkOnMainThreadException. withContext(Dispatchers.IO) is not inline, so the
        // early-return branches below live in the regular suspend function body, never inside this
        // lambda (a `return` inside a non-inline lambda would be a compile error).
        return try {
            val (isSuccessful, code, body) = withContext(Dispatchers.IO) {
                val request = Request.Builder().url("https://api.postalpincode.in/pincode/$pinCode").get().build()
                client.newCall(request).execute().use { response ->
                    Triple(response.isSuccessful, response.code, response.body?.string())
                }
            }
            if (!isSuccessful) return PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "Lookup failed ($code).")
            if (body == null) return PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "Empty response.")

            val parsed = listAdapter.fromJson(body)?.firstOrNull()
            val office = parsed?.PostOffice?.firstOrNull()
            if (parsed?.Status != "Success" || office == null) {
                PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "No address found for this PIN code.")
            } else {
                PinCodeLookupResult(
                    pinCode = pinCode, city = office.District ?: "", state = office.State ?: "",
                    country = office.Country ?: "India", success = true
                )
            }
        } catch (e: IOException) {
            PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "No internet connection - enter the address manually.")
        } catch (e: Exception) {
            PinCodeLookupResult(pinCode = pinCode, success = false, errorMessage = "Lookup failed - enter the address manually.")
        }
    }
}
