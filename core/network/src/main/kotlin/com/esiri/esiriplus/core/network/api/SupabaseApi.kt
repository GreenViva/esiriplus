package com.esiri.esiriplus.core.network.api

import com.esiri.esiriplus.core.network.api.model.ConsultationApiModel
import com.esiri.esiriplus.core.network.api.model.PaymentApiModel
import com.esiri.esiriplus.core.network.api.model.UpdateConsultationStatusBody
import com.esiri.esiriplus.core.network.api.model.UpdateUserBody
import com.esiri.esiriplus.core.network.api.model.UserApiModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Query

interface SupabaseApi {

    // Consultations
    @GET("rest/v1/consultations")
    suspend fun getConsultationsForPatient(
        @Query("patient_id") patientIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
    ): Response<List<ConsultationApiModel>>

    @GET("rest/v1/consultations")
    suspend fun getConsultationsForDoctor(
        @Query("doctor_id") doctorIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
    ): Response<List<ConsultationApiModel>>

    @GET("rest/v1/consultations")
    suspend fun getConsultation(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*",
        @Header("Accept") accept: String = "application/vnd.pgrst.object+json",
    ): Response<ConsultationApiModel>

    @PATCH("rest/v1/consultations")
    suspend fun updateConsultationStatus(
        @Query("id") idFilter: String,
        @Body body: UpdateConsultationStatusBody,
        @Header("Prefer") prefer: String = "return=representation",
        @Header("Accept") accept: String = "application/vnd.pgrst.object+json",
    ): Response<ConsultationApiModel>

    // Payments
    @GET("rest/v1/payments")
    suspend fun getPaymentsForConsultation(
        @Query("consultation_id") consultationIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
    ): Response<List<PaymentApiModel>>

    @GET("rest/v1/payments")
    suspend fun getPayment(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*",
        @Header("Accept") accept: String = "application/vnd.pgrst.object+json",
    ): Response<PaymentApiModel>

    // Users
    @GET("rest/v1/users")
    suspend fun getUser(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*",
        @Header("Accept") accept: String = "application/vnd.pgrst.object+json",
    ): Response<UserApiModel>

    @PATCH("rest/v1/users")
    suspend fun updateUser(
        @Query("id") idFilter: String,
        @Body body: UpdateUserBody,
        @Header("Prefer") prefer: String = "return=representation",
        @Header("Accept") accept: String = "application/vnd.pgrst.object+json",
    ): Response<UserApiModel>
}
