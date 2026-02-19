package com.esiri.esiriplus.core.network.api

import com.esiri.esiriplus.core.network.mock.MockResponses
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class SupabaseApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: SupabaseApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        api = retrofit.create(SupabaseApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // Consultation tests

    @Test
    fun `getConsultationsForPatient deserializes list correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.CONSULTATION_LIST_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        val response = api.getConsultationsForPatient("eq.user-001")

        assertTrue(response.isSuccessful)
        val consultations = response.body()!!
        assertEquals(2, consultations.size)
        assertEquals("consult-001", consultations[0].id)
        assertEquals("user-001", consultations[0].patientId)
        assertEquals("GENERAL_CONSULTATION", consultations[0].serviceType)
        assertEquals("consult-002", consultations[1].id)
        assertEquals("FOLLOW_UP", consultations[1].serviceType)
    }

    @Test
    fun `getConsultationsForPatient sends correct query params`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"),
        )

        api.getConsultationsForPatient("eq.user-001")

        val request = mockWebServer.takeRequest()
        assertTrue(request.path!!.contains("patient_id=eq.user-001"))
        assertTrue(request.path!!.contains("select=*"))
        assertTrue(request.path!!.contains("order=created_at.desc"))
    }

    @Test
    fun `getConsultation deserializes single object`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.CONSULTATION_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        val response = api.getConsultation("eq.consult-001")

        assertTrue(response.isSuccessful)
        val consultation = response.body()!!
        assertEquals("consult-001", consultation.id)
        assertEquals("doctor-001", consultation.doctorId)
        assertNull(consultation.notes)
    }

    @Test
    fun `updateConsultationStatus sends correct headers`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.CONSULTATION_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        api.updateConsultationStatus(
            idFilter = "eq.consult-001",
            body = com.esiri.esiriplus.core.network.api.model.UpdateConsultationStatusBody("COMPLETED"),
        )

        val request = mockWebServer.takeRequest()
        assertEquals("return=representation", request.getHeader("Prefer"))
        assertEquals("application/vnd.pgrst.object+json", request.getHeader("Accept"))
        assertEquals("PATCH", request.method)
    }

    // Payment tests

    @Test
    fun `getPaymentsForConsultation deserializes correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.PAYMENT_LIST_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        val response = api.getPaymentsForConsultation("eq.consult-001")

        assertTrue(response.isSuccessful)
        val payments = response.body()!!
        assertEquals(1, payments.size)
        assertEquals("pay-001", payments[0].id)
        assertEquals(1500, payments[0].amount)
        assertEquals("KES", payments[0].currency)
        assertEquals("COMPLETED", payments[0].status)
        assertEquals("QKJ3B7X9YM", payments[0].mpesaReceiptNumber)
    }

    @Test
    fun `getPayment deserializes single object`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.PAYMENT_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        val response = api.getPayment("eq.pay-001")

        assertTrue(response.isSuccessful)
        val payment = response.body()!!
        assertEquals("pay-001", payment.id)
        assertEquals("consult-001", payment.consultationId)
    }

    // User tests

    @Test
    fun `getUser deserializes correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.USER_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        val response = api.getUser("eq.user-001")

        assertTrue(response.isSuccessful)
        val user = response.body()!!
        assertEquals("user-001", user.id)
        assertEquals("John Doe", user.fullName)
        assertEquals("+254700000000", user.phone)
        assertEquals("john@example.com", user.email)
        assertEquals("PATIENT", user.role)
        assertTrue(user.isVerified)
    }

    @Test
    fun `updateUser sends PATCH with correct body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponses.USER_RESPONSE)
                .addHeader("Content-Type", "application/json"),
        )

        api.updateUser(
            idFilter = "eq.user-001",
            body = com.esiri.esiriplus.core.network.api.model.UpdateUserBody(
                fullName = "Jane Doe",
                phone = "+254711111111",
            ),
        )

        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("Jane Doe"))
        assertTrue(body.contains("+254711111111"))
    }

    // Error handling tests

    @Test
    fun `handles 404 response correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(MockResponses.ERROR_RESPONSE_404),
        )

        val response = api.getUser("eq.nonexistent")

        assertEquals(404, response.code())
        assertNotNull(response.errorBody())
    }

    @Test
    fun `handles 500 response correctly`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody(MockResponses.ERROR_RESPONSE_500),
        )

        val response = api.getConsultation("eq.consult-001")

        assertEquals(500, response.code())
    }
}
