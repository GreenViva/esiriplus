package com.esiri.esiriplus.core.common.validation

/**
 * Result of a validation check. Either [Valid] or [Invalid] with a message.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val message: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
    val errorMessage: String? get() = (this as? Invalid)?.message
}

/**
 * Combines multiple validation results. Returns the first invalid result, or [Valid].
 */
fun validateAll(vararg results: ValidationResult): ValidationResult =
    results.firstOrNull { it is ValidationResult.Invalid } ?: ValidationResult.Valid
