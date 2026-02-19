package com.esiri.esiriplus.core.network.interceptor

interface TokenRefresher {
    fun refreshToken(currentRefreshToken: String): Boolean
}
