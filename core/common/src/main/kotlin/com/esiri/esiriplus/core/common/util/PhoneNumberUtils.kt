package com.esiri.esiriplus.core.common.util

object PhoneNumberUtils {
    private const val E164_LENGTH = 13
    private const val COUNTRY_CODE_END = 4
    private const val PREFIX_END = 7

    fun formatForMpesa(phone: String): String {
        val cleaned = phone.replace(Regex("[^0-9+]"), "")
        return when {
            cleaned.startsWith("+254") -> cleaned.removePrefix("+")
            cleaned.startsWith("254") -> cleaned
            cleaned.startsWith("0") -> "254${cleaned.substring(1)}"
            else -> "254$cleaned"
        }
    }

    fun formatForDisplay(phone: String): String {
        val e164 = when {
            phone.startsWith("+254") -> phone
            phone.startsWith("254") -> "+$phone"
            phone.startsWith("0") -> "+254${phone.substring(1)}"
            else -> "+254$phone"
        }
        return if (e164.length == E164_LENGTH) {
            "${e164.substring(0, COUNTRY_CODE_END)} " +
                "${e164.substring(COUNTRY_CODE_END, PREFIX_END)} " +
                e164.substring(PREFIX_END)
        } else {
            e164
        }
    }
}
