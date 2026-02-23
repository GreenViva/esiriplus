package com.esiri.esiriplus.feature.doctor.di

import com.esiri.esiriplus.core.domain.repository.DoctorEarningsRepository
import com.esiri.esiriplus.core.domain.repository.DoctorProfileRepository
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.feature.doctor.data.DoctorEarningsRepositoryImpl
import com.esiri.esiriplus.feature.doctor.data.DoctorProfileRepositoryImpl
import com.esiri.esiriplus.feature.doctor.data.DoctorRatingRepositoryImpl
import com.esiri.esiriplus.feature.doctor.data.VideoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DoctorModule {
    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindDoctorProfileRepository(impl: DoctorProfileRepositoryImpl): DoctorProfileRepository

    @Binds
    @Singleton
    abstract fun bindDoctorRatingRepository(impl: DoctorRatingRepositoryImpl): DoctorRatingRepository

    @Binds
    @Singleton
    abstract fun bindDoctorEarningsRepository(impl: DoctorEarningsRepositoryImpl): DoctorEarningsRepository
}
