package com.esiri.esiriplus.core.database.di

import android.content.Context
import androidx.room.Room
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EsiriplusDatabase =
        Room.databaseBuilder(
            context,
            EsiriplusDatabase::class.java,
            "esiriplus.db",
        ).build()

    @Provides
    fun provideUserDao(db: EsiriplusDatabase): UserDao = db.userDao()

    @Provides
    fun provideSessionDao(db: EsiriplusDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideConsultationDao(db: EsiriplusDatabase): ConsultationDao = db.consultationDao()

    @Provides
    fun providePaymentDao(db: EsiriplusDatabase): PaymentDao = db.paymentDao()
}
