package com.esiri.esiriplus.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.esiri.esiriplus.core.database.converter.DateConverters
import com.esiri.esiriplus.core.database.converter.JsonConverter
import com.esiri.esiriplus.core.database.converter.ListStringConverter
import com.esiri.esiriplus.core.database.dao.AppConfigDao
import com.esiri.esiriplus.core.database.dao.AttachmentDao
import com.esiri.esiriplus.core.database.dao.AuditLogDao
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DiagnosisDao
import com.esiri.esiriplus.core.database.dao.DoctorAvailabilityDao
import com.esiri.esiriplus.core.database.dao.DoctorCredentialsDao
import com.esiri.esiriplus.core.database.dao.DoctorEarningsDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.DoctorRatingDao
import com.esiri.esiriplus.core.database.dao.MedicalRecordDao
import com.esiri.esiriplus.core.database.dao.MessageDao
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.dao.CallRechargePaymentDao
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.dao.PrescriptionDao
import com.esiri.esiriplus.core.database.dao.ProviderDao
import com.esiri.esiriplus.core.database.dao.ReviewDao
import com.esiri.esiriplus.core.database.dao.ScheduleDao
import com.esiri.esiriplus.core.database.dao.ServiceAccessPaymentDao
import com.esiri.esiriplus.core.database.dao.ServiceTierDao
import com.esiri.esiriplus.core.database.dao.SessionDao
import com.esiri.esiriplus.core.database.dao.UserDao
import com.esiri.esiriplus.core.database.dao.PatientReportDao
import com.esiri.esiriplus.core.database.dao.TypingIndicatorDao
import com.esiri.esiriplus.core.database.dao.VideoCallDao
import com.esiri.esiriplus.core.database.dao.VitalSignDao
import com.esiri.esiriplus.core.database.entity.AppConfigEntity
import com.esiri.esiriplus.core.database.entity.AttachmentEntity
import com.esiri.esiriplus.core.database.entity.AuditLogEntity
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DiagnosisEntity
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilityEntity
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import com.esiri.esiriplus.core.database.entity.DoctorEarningsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.entity.DoctorRatingEntity
import com.esiri.esiriplus.core.database.entity.MedicalRecordEntity
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.database.entity.PatientProfileEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import com.esiri.esiriplus.core.database.entity.CallRechargePaymentEntity
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.database.entity.PrescriptionEntity
import com.esiri.esiriplus.core.database.entity.ProviderEntity
import com.esiri.esiriplus.core.database.entity.ReviewEntity
import com.esiri.esiriplus.core.database.entity.ScheduleEntity
import com.esiri.esiriplus.core.database.entity.ServiceAccessPaymentEntity
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import com.esiri.esiriplus.core.database.entity.SessionEntity
import com.esiri.esiriplus.core.database.entity.UserEntity
import com.esiri.esiriplus.core.database.entity.PatientReportEntity
import com.esiri.esiriplus.core.database.entity.TypingIndicatorEntity
import com.esiri.esiriplus.core.database.entity.VideoCallEntity
import com.esiri.esiriplus.core.database.entity.VitalSignEntity

@Database(
    entities = [
        UserEntity::class,
        SessionEntity::class,
        ConsultationEntity::class,
        PaymentEntity::class,
        ServiceTierEntity::class,
        AppConfigEntity::class,
        DoctorProfileEntity::class,
        DoctorAvailabilityEntity::class,
        DoctorCredentialsEntity::class,
        PatientProfileEntity::class,
        PatientSessionEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        NotificationEntity::class,
        PrescriptionEntity::class,
        DiagnosisEntity::class,
        VitalSignEntity::class,
        ScheduleEntity::class,
        ReviewEntity::class,
        MedicalRecordEntity::class,
        AuditLogEntity::class,
        ProviderEntity::class,
        ServiceAccessPaymentEntity::class,
        CallRechargePaymentEntity::class,
        DoctorRatingEntity::class,
        DoctorEarningsEntity::class,
        VideoCallEntity::class,
        PatientReportEntity::class,
        TypingIndicatorEntity::class,
    ],
    version = 19,
    exportSchema = true,
)
@TypeConverters(
    DateConverters::class,
    ListStringConverter::class,
    JsonConverter::class,
)
abstract class EsiriplusDatabase : RoomDatabase() {

    /**
     * Re-inserts reference data (service tiers, app config) that gets wiped by [clearAllTables].
     * Call this immediately after [clearAllTables] to restore prepopulated rows.
     */
    fun reseedReferenceData() {
        val db = openHelper.writableDatabase
        com.esiri.esiriplus.core.database.callback.DatabaseCallback.reseed(db)
    }

    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun paymentDao(): PaymentDao
    abstract fun serviceTierDao(): ServiceTierDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun doctorProfileDao(): DoctorProfileDao
    abstract fun doctorAvailabilityDao(): DoctorAvailabilityDao
    abstract fun doctorCredentialsDao(): DoctorCredentialsDao
    abstract fun patientProfileDao(): PatientProfileDao
    abstract fun patientSessionDao(): PatientSessionDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun diagnosisDao(): DiagnosisDao
    abstract fun vitalSignDao(): VitalSignDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun reviewDao(): ReviewDao
    abstract fun medicalRecordDao(): MedicalRecordDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun providerDao(): ProviderDao
    abstract fun serviceAccessPaymentDao(): ServiceAccessPaymentDao
    abstract fun callRechargePaymentDao(): CallRechargePaymentDao
    abstract fun doctorRatingDao(): DoctorRatingDao
    abstract fun doctorEarningsDao(): DoctorEarningsDao
    abstract fun videoCallDao(): VideoCallDao
    abstract fun patientReportDao(): PatientReportDao
    abstract fun typingIndicatorDao(): TypingIndicatorDao
}
