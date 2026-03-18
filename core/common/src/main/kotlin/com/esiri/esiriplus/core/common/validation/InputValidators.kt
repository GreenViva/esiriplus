package com.esiri.esiriplus.core.common.validation

/**
 * Centralized input validators matching server-side edge function rules.
 * Each validator returns [ValidationResult] for consistent error handling.
 */
object InputValidators {

    // ── Service & Consultation Enums ─────────────────────────────────────

    val VALID_SERVICE_TYPES = setOf(
        "nurse", "clinical_officer", "pharmacist", "gp", "specialist", "psychologist",
    )

    val VALID_CONSULTATION_TYPES = setOf("chat", "video", "both")

    // ── Email ────────────────────────────────────────────────────────────

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return ValidationResult.Invalid("Email is required")
        if (!EMAIL_REGEX.matches(trimmed)) return ValidationResult.Invalid("Invalid email format")
        return ValidationResult.Valid
    }

    // ── Password ─────────────────────────────────────────────────────────

    fun validatePassword(password: String): ValidationResult {
        if (password.length < 8) return ValidationResult.Invalid("Password must be at least 8 characters")
        if (!password.any { it.isUpperCase() }) return ValidationResult.Invalid("Password must contain an uppercase letter")
        if (!password.any { it.isDigit() }) return ValidationResult.Invalid("Password must contain a digit")
        return ValidationResult.Valid
    }

    // ── Phone ────────────────────────────────────────────────────────────

    private val PHONE_DIGITS_REGEX = Regex("^\\d{7,15}$")
    private val TZ_PHONE_REGEX = Regex("^255[67]\\d{8}$")

    fun validatePhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        if (digits.isEmpty()) return ValidationResult.Invalid("Phone number is required")
        if (!PHONE_DIGITS_REGEX.matches(digits)) {
            return ValidationResult.Invalid("Phone number must be 7–15 digits")
        }
        return ValidationResult.Valid
    }

    fun validateTanzanianPhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        if (digits.isEmpty()) return ValidationResult.Invalid("Phone number is required")
        if (!TZ_PHONE_REGEX.matches(digits)) {
            return ValidationResult.Invalid("Phone must be a valid Tanzanian number (2556/2557)")
        }
        return ValidationResult.Valid
    }

    // ── Full Name ────────────────────────────────────────────────────────

    fun validateFullName(name: String): ValidationResult {
        val trimmed = name.trim()
        if (trimmed.length < 2) return ValidationResult.Invalid("Name must be at least 2 characters")
        if (trimmed.length > 100) return ValidationResult.Invalid("Name must not exceed 100 characters")
        return ValidationResult.Valid
    }

    // ── Service Type ─────────────────────────────────────────────────────

    fun validateServiceType(serviceType: String): ValidationResult {
        if (serviceType.isBlank()) return ValidationResult.Invalid("Service type is required")
        if (serviceType.lowercase() !in VALID_SERVICE_TYPES) {
            return ValidationResult.Invalid("Invalid service type: $serviceType")
        }
        return ValidationResult.Valid
    }

    // ── Consultation Type ────────────────────────────────────────────────

    fun validateConsultationType(type: String): ValidationResult {
        if (type.isBlank()) return ValidationResult.Invalid("Consultation type is required")
        if (type.lowercase() !in VALID_CONSULTATION_TYPES) {
            return ValidationResult.Invalid("Consultation type must be one of: chat, video, both")
        }
        return ValidationResult.Valid
    }

    // ── Chief Complaint ──────────────────────────────────────────────────

    fun validateChiefComplaint(complaint: String): ValidationResult {
        val trimmed = complaint.trim()
        if (trimmed.length < 10) {
            return ValidationResult.Invalid("Chief complaint must be at least 10 characters")
        }
        if (trimmed.length > 1000) {
            return ValidationResult.Invalid("Chief complaint must not exceed 1000 characters")
        }
        return ValidationResult.Valid
    }

    // ── Payment ──────────────────────────────────────────────────────────

    fun validatePaymentAmount(amount: Int): ValidationResult {
        if (amount <= 0) return ValidationResult.Invalid("Payment amount must be greater than zero")
        return ValidationResult.Valid
    }

    fun validateIdempotencyKey(key: String): ValidationResult {
        if (key.length < 8) return ValidationResult.Invalid("Idempotency key must be at least 8 characters")
        return ValidationResult.Valid
    }

    // ── Call Recharge ────────────────────────────────────────────────────

    private val VALID_RECHARGE_MINUTES = setOf(10, 30, 60, 120)

    fun validateRechargeMinutes(minutes: Int): ValidationResult {
        if (minutes !in VALID_RECHARGE_MINUTES) {
            return ValidationResult.Invalid("Minutes must be one of: ${VALID_RECHARGE_MINUTES.joinToString()}")
        }
        return ValidationResult.Valid
    }

    // ── Doctor Profile ───────────────────────────────────────────────────

    fun validateBio(bio: String): ValidationResult {
        val trimmed = bio.trim()
        if (trimmed.length < 10) return ValidationResult.Invalid("Bio must be at least 10 characters")
        if (trimmed.length > 1000) return ValidationResult.Invalid("Bio must not exceed 1000 characters")
        return ValidationResult.Valid
    }

    fun validateYearsExperience(years: Int): ValidationResult {
        if (years < 0 || years > 70) return ValidationResult.Invalid("Years of experience must be 0–70")
        return ValidationResult.Valid
    }

    fun validateLicenseNumber(license: String): ValidationResult {
        if (license.isBlank()) return ValidationResult.Invalid("License number is required")
        return ValidationResult.Valid
    }

    // ── OTP ──────────────────────────────────────────────────────────────

    private val OTP_REGEX = Regex("^\\d{6}$")

    fun validateOtpCode(code: String): ValidationResult {
        if (!OTP_REGEX.matches(code)) return ValidationResult.Invalid("OTP must be exactly 6 digits")
        return ValidationResult.Valid
    }

    // ── Booking ──────────────────────────────────────────────────────────

    fun validateRequiredId(id: String, fieldName: String = "ID"): ValidationResult {
        if (id.isBlank()) return ValidationResult.Invalid("$fieldName is required")
        return ValidationResult.Valid
    }
}
