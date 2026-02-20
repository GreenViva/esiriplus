package com.esiri.esiriplus.feature.auth.di

import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.PatientSessionRepository
import com.esiri.esiriplus.core.domain.repository.SecurityQuestionRepository
import com.esiri.esiriplus.core.network.SessionInvalidator
import com.esiri.esiriplus.feature.auth.data.AuthRepositoryImpl
import com.esiri.esiriplus.feature.auth.data.PatientSessionRepositoryImpl
import com.esiri.esiriplus.feature.auth.data.SecurityQuestionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionInvalidator(impl: AuthRepositoryImpl): SessionInvalidator

    @Binds
    @Singleton
    abstract fun bindPatientSessionRepository(
        impl: PatientSessionRepositoryImpl,
    ): PatientSessionRepository

    @Binds
    @Singleton
    abstract fun bindSecurityQuestionRepository(
        impl: SecurityQuestionRepositoryImpl,
    ): SecurityQuestionRepository
}
