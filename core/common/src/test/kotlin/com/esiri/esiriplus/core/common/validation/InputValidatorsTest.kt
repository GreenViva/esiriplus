package com.esiri.esiriplus.core.common.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsTest {

    // ── Email ────────────────────────────────────────────────────────────

    @Test
    fun `validateEmail accepts valid emails`() {
        assertTrue(InputValidators.validateEmail("user@example.com").isValid)
        assertTrue(InputValidators.validateEmail("user+tag@domain.co.ke").isValid)
        assertTrue(InputValidators.validateEmail("first.last@sub.domain.com").isValid)
    }

    @Test
    fun `validateEmail rejects blank email`() {
        val result = InputValidators.validateEmail("")
        assertFalse(result.isValid)
        assertEquals("Email is required", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects invalid format`() {
        assertFalse(InputValidators.validateEmail("not-an-email").isValid)
        assertFalse(InputValidators.validateEmail("missing@tld").isValid)
        assertFalse(InputValidators.validateEmail("@no-local.com").isValid)
    }

    // ── Password ─────────────────────────────────────────────────────────

    @Test
    fun `validatePassword accepts strong password`() {
        assertTrue(InputValidators.validatePassword("Passw0rd").isValid)
        assertTrue(InputValidators.validatePassword("MySecret123!").isValid)
    }

    @Test
    fun `validatePassword rejects short password`() {
        val result = InputValidators.validatePassword("Pw0")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("8 characters"))
    }

    @Test
    fun `validatePassword rejects missing uppercase`() {
        val result = InputValidators.validatePassword("password1")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("uppercase"))
    }

    @Test
    fun `validatePassword rejects missing digit`() {
        val result = InputValidators.validatePassword("Password")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("digit"))
    }

    // ── Phone ────────────────────────────────────────────────────────────

    @Test
    fun `validatePhone accepts valid phone numbers`() {
        assertTrue(InputValidators.validatePhone("2557001234567").isValid)
        assertTrue(InputValidators.validatePhone("+255700123456").isValid)
        assertTrue(InputValidators.validatePhone("0712345678").isValid)
    }

    @Test
    fun `validatePhone rejects empty phone`() {
        assertFalse(InputValidators.validatePhone("").isValid)
    }

    @Test
    fun `validatePhone rejects too short or too long`() {
        assertFalse(InputValidators.validatePhone("12345").isValid)
        assertFalse(InputValidators.validatePhone("1234567890123456").isValid)
    }

    @Test
    fun `validateTanzanianPhone accepts valid TZ numbers`() {
        assertTrue(InputValidators.validateTanzanianPhone("255712345678").isValid)
        assertTrue(InputValidators.validateTanzanianPhone("255612345678").isValid)
    }

    @Test
    fun `validateTanzanianPhone rejects non-TZ numbers`() {
        assertFalse(InputValidators.validateTanzanianPhone("254712345678").isValid)
        assertFalse(InputValidators.validateTanzanianPhone("255812345678").isValid)
    }

    // ── Full Name ────────────────────────────────────────────────────────

    @Test
    fun `validateFullName accepts valid names`() {
        assertTrue(InputValidators.validateFullName("Jo").isValid)
        assertTrue(InputValidators.validateFullName("John Doe").isValid)
    }

    @Test
    fun `validateFullName rejects too short`() {
        assertFalse(InputValidators.validateFullName("J").isValid)
        assertFalse(InputValidators.validateFullName("").isValid)
    }

    @Test
    fun `validateFullName rejects too long`() {
        assertFalse(InputValidators.validateFullName("A".repeat(101)).isValid)
    }

    // ── Service Type ─────────────────────────────────────────────────────

    @Test
    fun `validateServiceType accepts valid types`() {
        assertTrue(InputValidators.validateServiceType("gp").isValid)
        assertTrue(InputValidators.validateServiceType("nurse").isValid)
        assertTrue(InputValidators.validateServiceType("Specialist").isValid)
    }

    @Test
    fun `validateServiceType rejects invalid types`() {
        assertFalse(InputValidators.validateServiceType("dentist").isValid)
        assertFalse(InputValidators.validateServiceType("").isValid)
    }

    // ── Consultation Type ────────────────────────────────────────────────

    @Test
    fun `validateConsultationType accepts valid types`() {
        assertTrue(InputValidators.validateConsultationType("chat").isValid)
        assertTrue(InputValidators.validateConsultationType("video").isValid)
        assertTrue(InputValidators.validateConsultationType("both").isValid)
    }

    @Test
    fun `validateConsultationType rejects invalid types`() {
        assertFalse(InputValidators.validateConsultationType("phone").isValid)
        assertFalse(InputValidators.validateConsultationType("").isValid)
    }

    // ── Chief Complaint ──────────────────────────────────────────────────

    @Test
    fun `validateChiefComplaint accepts valid complaint`() {
        assertTrue(InputValidators.validateChiefComplaint("I have been experiencing headaches").isValid)
    }

    @Test
    fun `validateChiefComplaint rejects too short`() {
        val result = InputValidators.validateChiefComplaint("short")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("10 characters"))
    }

    @Test
    fun `validateChiefComplaint rejects too long`() {
        assertFalse(InputValidators.validateChiefComplaint("A".repeat(1001)).isValid)
    }

    // ── Payment ──────────────────────────────────────────────────────────

    @Test
    fun `validatePaymentAmount accepts positive amount`() {
        assertTrue(InputValidators.validatePaymentAmount(5000).isValid)
    }

    @Test
    fun `validatePaymentAmount rejects zero or negative`() {
        assertFalse(InputValidators.validatePaymentAmount(0).isValid)
        assertFalse(InputValidators.validatePaymentAmount(-100).isValid)
    }

    @Test
    fun `validateIdempotencyKey accepts valid key`() {
        assertTrue(InputValidators.validateIdempotencyKey("12345678").isValid)
    }

    @Test
    fun `validateIdempotencyKey rejects too short`() {
        assertFalse(InputValidators.validateIdempotencyKey("short").isValid)
    }

    // ── Call Recharge ────────────────────────────────────────────────────

    @Test
    fun `validateRechargeMinutes accepts valid packages`() {
        assertTrue(InputValidators.validateRechargeMinutes(10).isValid)
        assertTrue(InputValidators.validateRechargeMinutes(30).isValid)
        assertTrue(InputValidators.validateRechargeMinutes(60).isValid)
        assertTrue(InputValidators.validateRechargeMinutes(120).isValid)
    }

    @Test
    fun `validateRechargeMinutes rejects invalid packages`() {
        assertFalse(InputValidators.validateRechargeMinutes(15).isValid)
        assertFalse(InputValidators.validateRechargeMinutes(0).isValid)
    }

    // ── Doctor Profile ───────────────────────────────────────────────────

    @Test
    fun `validateBio accepts valid bio`() {
        assertTrue(InputValidators.validateBio("I am a qualified doctor with experience.").isValid)
    }

    @Test
    fun `validateBio rejects too short or too long`() {
        assertFalse(InputValidators.validateBio("Short").isValid)
        assertFalse(InputValidators.validateBio("A".repeat(1001)).isValid)
    }

    @Test
    fun `validateYearsExperience accepts valid range`() {
        assertTrue(InputValidators.validateYearsExperience(0).isValid)
        assertTrue(InputValidators.validateYearsExperience(35).isValid)
        assertTrue(InputValidators.validateYearsExperience(70).isValid)
    }

    @Test
    fun `validateYearsExperience rejects out of range`() {
        assertFalse(InputValidators.validateYearsExperience(-1).isValid)
        assertFalse(InputValidators.validateYearsExperience(71).isValid)
    }

    @Test
    fun `validateOtpCode accepts valid OTP`() {
        assertTrue(InputValidators.validateOtpCode("123456").isValid)
    }

    @Test
    fun `validateOtpCode rejects invalid OTP`() {
        assertFalse(InputValidators.validateOtpCode("12345").isValid)
        assertFalse(InputValidators.validateOtpCode("1234567").isValid)
        assertFalse(InputValidators.validateOtpCode("abcdef").isValid)
    }

    // ── Utility ──────────────────────────────────────────────────────────

    @Test
    fun `validateAll returns first invalid result`() {
        val result = validateAll(
            ValidationResult.Valid,
            ValidationResult.Invalid("first error"),
            ValidationResult.Invalid("second error"),
        )
        assertFalse(result.isValid)
        assertEquals("first error", result.errorMessage)
    }

    @Test
    fun `validateAll returns valid when all pass`() {
        val result = validateAll(
            ValidationResult.Valid,
            ValidationResult.Valid,
        )
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateRequiredId rejects blank`() {
        assertFalse(InputValidators.validateRequiredId("", "Doctor ID").isValid)
        assertTrue(InputValidators.validateRequiredId("doc-123", "Doctor ID").isValid)
    }
}
