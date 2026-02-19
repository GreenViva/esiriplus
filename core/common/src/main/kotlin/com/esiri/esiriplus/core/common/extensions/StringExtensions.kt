package com.esiri.esiriplus.core.common.extensions

fun String.isValidEmail(): Boolean =
    matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))

fun String.isValidKenyanPhone(): Boolean =
    matches(Regex("^(?:\\+254|0)[17]\\d{8}$"))

fun String.toE164Kenyan(): String = when {
    startsWith("+254") -> this
    startsWith("0") -> "+254${substring(1)}"
    else -> "+254$this"
}
