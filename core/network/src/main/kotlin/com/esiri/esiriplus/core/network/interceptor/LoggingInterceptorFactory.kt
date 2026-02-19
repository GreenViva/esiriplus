package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor

object LoggingInterceptorFactory {

    fun create(): HttpLoggingInterceptor {
        val level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        return HttpLoggingInterceptor().apply {
            this.level = level
            redactHeader("Authorization")
            redactHeader("apikey")
        }
    }
}
