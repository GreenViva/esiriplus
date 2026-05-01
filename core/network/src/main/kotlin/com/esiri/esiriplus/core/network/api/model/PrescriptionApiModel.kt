package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PrescriptionApiModel(
    @Json(name = "prescription_id") val prescriptionId: String,
    @Json(name = "medication_name") val medicationName: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
)
