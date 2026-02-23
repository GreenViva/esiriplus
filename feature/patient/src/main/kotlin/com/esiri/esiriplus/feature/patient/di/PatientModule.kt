package com.esiri.esiriplus.feature.patient.di

import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.core.domain.repository.NotificationRepository
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import com.esiri.esiriplus.core.domain.repository.TypingIndicatorRepository
import com.esiri.esiriplus.core.domain.repository.VideoCallRepository
import com.esiri.esiriplus.feature.patient.data.ConsultationRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.NotificationRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.PatientReportRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.PaymentRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.TypingIndicatorRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.VideoCallRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PatientModule {
    @Binds
    @Singleton
    abstract fun bindConsultationRepository(impl: ConsultationRepositoryImpl): ConsultationRepository

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindPatientReportRepository(impl: PatientReportRepositoryImpl): PatientReportRepository

    @Binds
    @Singleton
    abstract fun bindVideoCallRepository(impl: VideoCallRepositoryImpl): VideoCallRepository

    @Binds
    @Singleton
    abstract fun bindTypingIndicatorRepository(impl: TypingIndicatorRepositoryImpl): TypingIndicatorRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
