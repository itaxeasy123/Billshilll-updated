package com.example.accounting.domain.taxation.gstreturn

/**
 * The result of one online-filing attempt (Rule 33, Section 6/16) - always the truth of what
 * actually happened, never a fabricated success.
 */
data class GstOnlineFilingResult(
    val success: Boolean,
    val acknowledgementNumber: String? = null,
    val responseJson: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null
)

/**
 * The integration boundary a real GST Network client would implement (Rule 33, Section 6) - kept
 * deliberately narrow (one request JSON in, one result out) so a future real implementation can plug
 * in without [com.example.accounting.application.gstreturn.GstReturnManagementService] changing at
 * all.
 */
interface GstOnlineFilingGateway {
    suspend fun submitReturn(requestJson: String): GstOnlineFilingResult
}

/**
 * The honest default (Rule 33, Section 6) - this repository was inspected (docs/25/26 API
 * architecture, docs/32 server architecture, docs/49 GSTR JSON export's own "filing is out of
 * scope" note) and contains no real GST Network endpoint, credential storage, or client anywhere.
 * This implementation NEVER simulates a successful filing; it always reports the integration as
 * unconfigured so the caller can show the correct "not available" state and preserve the prepared
 * return for Offline mode instead.
 */
class UnconfiguredGstOnlineFilingGateway : GstOnlineFilingGateway {
    override suspend fun submitReturn(requestJson: String): GstOnlineFilingResult = GstOnlineFilingResult(
        success = false,
        errorCode = "GST_INTEGRATION_NOT_CONFIGURED",
        errorMessage = "Online GST filing is not configured in this application. Prepare the return " +
            "and use Offline mode to generate a JSON file for manual upload to the GST portal."
    )
}
