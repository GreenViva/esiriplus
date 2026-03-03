package com.esiri.esiriplus.di

import com.esiri.esiriplus.call.CallServiceControllerImpl
import com.esiri.esiriplus.core.domain.service.CallServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CallServiceModule {

    @Binds
    @Singleton
    abstract fun bindCallServiceController(
        impl: CallServiceControllerImpl,
    ): CallServiceController
}
