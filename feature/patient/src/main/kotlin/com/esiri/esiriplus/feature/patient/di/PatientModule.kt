package com.esiri.esiriplus.feature.patient.di

import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import com.esiri.esiriplus.feature.patient.data.ConsultationRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.PatientReportRepositoryImpl
import com.esiri.esiriplus.feature.patient.data.PaymentRepositoryImpl
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
}
