package com.esiri.esiriplus.feature.doctor.di

import com.esiri.esiriplus.core.domain.repository.DoctorProfileRepository
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.feature.doctor.data.DoctorProfileRepositoryImpl
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
}
