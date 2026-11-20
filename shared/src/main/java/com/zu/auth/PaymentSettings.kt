package com.zu.auth

import org.json.JSONObject

data class PaymentSettings(
    val upiId: String,
    val qrUrl: String,
    val showUpiId: Boolean = true
) {
    companion object {
        fun fromFirestore(fields: JSONObject) = PaymentSettings(
            upiId = fields.getJSONObject("upiId").getString("stringValue"),
            qrUrl = fields.optJSONObject("qrUrl")?.optString("stringValue").orEmpty(),
            showUpiId = fields.optJSONObject("showUpiId")?.optBoolean("booleanValue", true) ?: true
        )
    }
}
