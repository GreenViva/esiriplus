package com.esiri.esiriplus.core.common.util

import java.util.UUID

object IdempotencyKeyGenerator {
    fun generate(): String = UUID.randomUUID().toString()
    fun generate(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}
