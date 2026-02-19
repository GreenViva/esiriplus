package com.esiri.esiriplus.core.database.di

import android.content.Context
import androidx.room.Room
import com.esiri.esiriplus.core.database.BuildConfig
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.callback.DatabaseCallback
import com.esiri.esiriplus.core.database.dao.AppConfigDao
import com.esiri.esiriplus.core.database.dao.AttachmentDao
import com.esiri.esiriplus.core.database.dao.AuditLogDao
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DiagnosisDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.MedicalRecordDao
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.dao.PrescriptionDao
import com.esiri.esiriplus.core.database.dao.ProviderDao
import com.esiri.esiriplus.core.database.dao.ReviewDao
import com.esiri.esiriplus.core.database.dao.ScheduleDao
import com.esiri.esiriplus.core.database.dao.ServiceTierDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.dao.VitalSignDao
import com.esiri.esiriplus.core.database.encryption.DatabaseEncryption
import com.esiri.esiriplus.core.database.migration.DatabaseMigrations
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
    fun provideDatabase(@ApplicationContext context: Context): EsiriplusDatabase {
        val builder = Room.databaseBuilder(
            context,
            EsiriplusDatabase::class.java,
            DATABASE_NAME,
        )
            .openHelperFactory(DatabaseEncryption.createOpenHelperFactory(context))
            .addCallback(DatabaseCallback(context))
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)

        if (BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    // Original DAOs
    @Provides fun provideUserDao(db: EsiriplusDatabase): UserDao = db.userDao()
    @Provides fun provideSessionDao(db: EsiriplusDatabase): SessionDao = db.sessionDao()
    @Provides fun provideConsultationDao(db: EsiriplusDatabase): ConsultationDao = db.consultationDao()
    @Provides fun providePaymentDao(db: EsiriplusDatabase): PaymentDao = db.paymentDao()

    // New DAOs
    @Provides fun provideServiceTierDao(db: EsiriplusDatabase): ServiceTierDao = db.serviceTierDao()
    @Provides fun provideAppConfigDao(db: EsiriplusDatabase): AppConfigDao = db.appConfigDao()
    @Provides fun provideDoctorProfileDao(db: EsiriplusDatabase): DoctorProfileDao = db.doctorProfileDao()
    @Provides fun providePatientProfileDao(db: EsiriplusDatabase): PatientProfileDao = db.patientProfileDao()
    @Provides fun provideMessageDao(db: EsiriplusDatabase): MessageDao = db.messageDao()
    @Provides fun provideAttachmentDao(db: EsiriplusDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun provideNotificationDao(db: EsiriplusDatabase): NotificationDao = db.notificationDao()
    @Provides fun providePrescriptionDao(db: EsiriplusDatabase): PrescriptionDao = db.prescriptionDao()
    @Provides fun provideDiagnosisDao(db: EsiriplusDatabase): DiagnosisDao = db.diagnosisDao()
    @Provides fun provideVitalSignDao(db: EsiriplusDatabase): VitalSignDao = db.vitalSignDao()
    @Provides fun provideScheduleDao(db: EsiriplusDatabase): ScheduleDao = db.scheduleDao()
    @Provides fun provideReviewDao(db: EsiriplusDatabase): ReviewDao = db.reviewDao()
    @Provides fun provideMedicalRecordDao(db: EsiriplusDatabase): MedicalRecordDao = db.medicalRecordDao()
    @Provides fun provideAuditLogDao(db: EsiriplusDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideProviderDao(db: EsiriplusDatabase): ProviderDao = db.providerDao()

    private const val DATABASE_NAME = "esiriplus.db"
}
