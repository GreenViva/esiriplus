package com.esiri.esiriplus.core.network.di

import com.esiri.esiriplus.core.domain.repository.AppointmentRepository
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.domain.repository.FcmTokenRepository
import com.esiri.esiriplus.core.domain.repository.MessageRepository
import com.esiri.esiriplus.core.domain.repository.NotificationRepository

import com.esiri.esiriplus.core.network.BuildConfig
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.fcm.FcmTokenRepositoryImpl
import com.esiri.esiriplus.core.network.service.AppointmentRepositoryImpl
import com.esiri.esiriplus.core.network.service.ConsultationRequestRepositoryImpl
import com.esiri.esiriplus.core.network.service.MessageRepositoryImpl
import com.esiri.esiriplus.core.network.service.NotificationRepositoryImpl

import android.content.Context
import com.esiri.esiriplus.core.network.interceptor.AuthInterceptor
import com.esiri.esiriplus.core.network.interceptor.CircuitBreakerInterceptor
import com.esiri.esiriplus.core.network.interceptor.ClientRateLimiterInterceptor
import com.esiri.esiriplus.core.network.interceptor.LoggingInterceptorFactory
import com.esiri.esiriplus.core.network.interceptor.ProactiveTokenRefreshInterceptor
import com.esiri.esiriplus.core.network.interceptor.RetryInterceptor
import com.esiri.esiriplus.core.network.interceptor.TokenRefreshAuthenticator
import com.esiri.esiriplus.core.network.interceptor.TokenRefresher
import com.esiri.esiriplus.core.network.interceptor.TokenRefresherImpl
import com.esiri.esiriplus.core.network.metrics.MetricsInterceptor
import com.esiri.esiriplus.core.network.security.CertificatePinning
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedClient

/** Application-scoped CoroutineScope for fire-and-forget cleanup tasks. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideTokenRefresher(impl: TokenRefresherImpl): TokenRefresher = impl

    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        proactiveTokenRefreshInterceptor: ProactiveTokenRefreshInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator,
        metricsInterceptor: MetricsInterceptor,
        clientRateLimiter: ClientRateLimiterInterceptor,
        circuitBreaker: CircuitBreakerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectionPool(
            ConnectionPool(
                MAX_IDLE_CONNECTIONS,
                KEEP_ALIVE_DURATION_MINUTES,
                TimeUnit.MINUTES,
            ),
        )
        .cache(Cache(File(context.cacheDir, "http_cache"), CACHE_SIZE_BYTES))
        .certificatePinner(CertificatePinning.createCertificatePinner())
        .addInterceptor(circuitBreaker)
        .addInterceptor(clientRateLimiter)
        .addInterceptor(metricsInterceptor)
        .addInterceptor(proactiveTokenRefreshInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(RetryInterceptor())
        .addInterceptor(LoggingInterceptorFactory.create())
        .authenticator(tokenRefreshAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @AuthenticatedClient okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SUPABASE_URL + "/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideSupabaseApi(retrofit: Retrofit): SupabaseApi =
        retrofit.create(SupabaseApi::class.java)

    @Provides
    @Singleton
    fun provideSupabaseClientProvider(
        @AuthenticatedClient okHttpClient: OkHttpClient,
    ): SupabaseClientProvider = SupabaseClientProvider(okHttpClient)

    @Provides
    @Singleton
    fun provideSupabaseClient(provider: SupabaseClientProvider): SupabaseClient =
        provider.client

    @Provides
    @Singleton
    fun provideFcmTokenRepository(impl: FcmTokenRepositoryImpl): FcmTokenRepository = impl

    @Provides
    @Singleton
    fun provideNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository = impl

    @Provides
    @Singleton
    fun provideConsultationRequestRepository(impl: ConsultationRequestRepositoryImpl): ConsultationRequestRepository = impl

    @Provides
    @Singleton
    fun provideMessageRepository(impl: MessageRepositoryImpl): MessageRepository = impl

    @Provides
    @Singleton
    fun provideAppointmentRepository(impl: AppointmentRepositoryImpl): AppointmentRepository = impl

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L

    // Connection pool: keep more idle connections alive for faster subsequent requests
    private const val MAX_IDLE_CONNECTIONS = 15
    private const val KEEP_ALIVE_DURATION_MINUTES = 10L

    // 10 MB HTTP response cache for read-heavy endpoints
    private const val CACHE_SIZE_BYTES = 10L * 1024 * 1024
}
