package com.esiri.esiriplus.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN sex TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN ageGroup TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE patient_profiles ADD COLUMN chronicConditions TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `patient_sessions` (
                    `sessionId` TEXT NOT NULL,
                    `sessionTokenHash` TEXT NOT NULL,
                    `ageGroup` TEXT DEFAULT NULL,
                    `sex` TEXT DEFAULT NULL,
                    `region` TEXT DEFAULT NULL,
                    `bloodType` TEXT DEFAULT NULL,
                    `allergies` TEXT NOT NULL DEFAULT '[]',
                    `chronicConditions` TEXT NOT NULL DEFAULT '[]',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastSynced` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`sessionId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_patient_sessions_sessionId` ON `patient_sessions` (`sessionId`)",
            )
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop tables that reference consultations (FK dependencies)
            db.execSQL("DROP TABLE IF EXISTS `messages`")
            db.execSQL("DROP TABLE IF EXISTS `diagnoses`")
            db.execSQL("DROP TABLE IF EXISTS `attachments`")
            db.execSQL("DROP TABLE IF EXISTS `prescriptions`")
            db.execSQL("DROP TABLE IF EXISTS `reviews`")
            db.execSQL("DROP TABLE IF EXISTS `vital_signs`")
            db.execSQL("DROP TABLE IF EXISTS `consultations`")

            // Recreate consultations with new schema
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `consultations` (
                    `consultationId` TEXT NOT NULL,
                    `patientSessionId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `serviceType` TEXT NOT NULL,
                    `consultationFee` INTEGER NOT NULL,
                    `sessionStartTime` INTEGER DEFAULT NULL,
                    `sessionEndTime` INTEGER DEFAULT NULL,
                    `sessionDurationMinutes` INTEGER NOT NULL DEFAULT 15,
                    `requestExpiresAt` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`consultationId`),
                    FOREIGN KEY(`patientSessionId`) REFERENCES `patient_sessions`(`sessionId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_patientSessionId` ON `consultations` (`patientSessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_doctorId` ON `consultations` (`doctorId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_status_createdAt` ON `consultations` (`status`, `createdAt`)")

            // Recreate messages with new schema
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages` (
                    `messageId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `senderType` TEXT NOT NULL,
                    `senderId` TEXT NOT NULL,
                    `messageText` TEXT NOT NULL,
                    `messageType` TEXT NOT NULL,
                    `attachmentUrl` TEXT DEFAULT NULL,
                    `isRead` INTEGER NOT NULL DEFAULT 0,
                    `synced` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`messageId`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_consultationId_createdAt` ON `messages` (`consultationId`, `createdAt`)")

            // Recreate dependent tables with updated FK (consultationId instead of id)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `diagnoses` (
                    `id` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `icdCode` TEXT DEFAULT NULL,
                    `description` TEXT NOT NULL,
                    `severity` TEXT NOT NULL,
                    `notes` TEXT DEFAULT NULL,
                    `createdAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnoses_consultationId` ON `diagnoses` (`consultationId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments` (
                    `id` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `uploaderId` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `fileType` TEXT NOT NULL,
                    `fileSize` INTEGER NOT NULL,
                    `url` TEXT NOT NULL,
                    `createdAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_consultationId` ON `attachments` (`consultationId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prescriptions` (
                    `id` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `patientId` TEXT NOT NULL,
                    `medication` TEXT NOT NULL,
                    `dosage` TEXT NOT NULL,
                    `frequency` TEXT NOT NULL,
                    `duration` TEXT NOT NULL,
                    `notes` TEXT DEFAULT NULL,
                    `createdAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescriptions_consultationId` ON `prescriptions` (`consultationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescriptions_doctorId` ON `prescriptions` (`doctorId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reviews` (
                    `id` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `patientId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `rating` INTEGER NOT NULL,
                    `comment` TEXT DEFAULT NULL,
                    `createdAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_consultationId` ON `reviews` (`consultationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_doctorId` ON `reviews` (`doctorId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reviews_patientId` ON `reviews` (`patientId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vital_signs` (
                    `id` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `patientId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `recordedAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vital_signs_consultationId` ON `vital_signs` (`consultationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vital_signs_patientId` ON `vital_signs` (`patientId`)")
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop old doctor_profiles (schema changed completely)
            db.execSQL("DROP TABLE IF EXISTS `doctor_profiles`")

            // Create new doctor_profiles with richer schema
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_profiles` (
                    `doctorId` TEXT NOT NULL,
                    `fullName` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `specialty` TEXT NOT NULL,
                    `languages` TEXT NOT NULL,
                    `bio` TEXT NOT NULL,
                    `licenseNumber` TEXT NOT NULL,
                    `yearsExperience` INTEGER NOT NULL,
                    `profilePhotoUrl` TEXT DEFAULT NULL,
                    `averageRating` REAL NOT NULL DEFAULT 0.0,
                    `totalRatings` INTEGER NOT NULL DEFAULT 0,
                    `isVerified` INTEGER NOT NULL DEFAULT 0,
                    `isAvailable` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`doctorId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_profiles_isVerified_isAvailable_specialty` ON `doctor_profiles` (`isVerified`, `isAvailable`, `specialty`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_profiles_averageRating` ON `doctor_profiles` (`averageRating`)")

            // Create doctor_availability
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_availability` (
                    `availabilityId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `isAvailable` INTEGER NOT NULL,
                    `availabilitySchedule` TEXT NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    PRIMARY KEY(`availabilityId`),
                    FOREIGN KEY(`doctorId`) REFERENCES `doctor_profiles`(`doctorId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_availability_doctorId` ON `doctor_availability` (`doctorId`)")

            // Create doctor_credentials
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_credentials` (
                    `credentialId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `documentUrl` TEXT NOT NULL,
                    `documentType` TEXT NOT NULL,
                    `verifiedAt` INTEGER DEFAULT NULL,
                    PRIMARY KEY(`credentialId`),
                    FOREIGN KEY(`doctorId`) REFERENCES `doctor_profiles`(`doctorId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_credentials_doctorId` ON `doctor_credentials` (`doctorId`)")
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop old payments table (schema changed completely)
            db.execSQL("DROP TABLE IF EXISTS `payments`")

            // Create new payments with FK to patient_sessions
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `payments` (
                    `paymentId` TEXT NOT NULL,
                    `patientSessionId` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `transactionId` TEXT DEFAULT NULL,
                    `phoneNumber` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `failureReason` TEXT DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `synced` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`paymentId`),
                    FOREIGN KEY(`patientSessionId`) REFERENCES `patient_sessions`(`sessionId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_patientSessionId_createdAt` ON `payments` (`patientSessionId`, `createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_status` ON `payments` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_transactionId` ON `payments` (`transactionId`)")

            // Create service_access_payments
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `service_access_payments` (
                    `paymentId` TEXT NOT NULL,
                    `serviceType` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`paymentId`)
                )
                """.trimIndent(),
            )

            // Create call_recharge_payments with FK to consultations
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `call_recharge_payments` (
                    `paymentId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL DEFAULT 2500,
                    `additionalMinutes` INTEGER NOT NULL DEFAULT 3,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`paymentId`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_recharge_payments_consultationId` ON `call_recharge_payments` (`consultationId`)")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create doctor_ratings with FKs and UNIQUE constraint on consultationId
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_ratings` (
                    `ratingId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `patientSessionId` TEXT NOT NULL,
                    `rating` INTEGER NOT NULL,
                    `comment` TEXT DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `synced` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`ratingId`),
                    FOREIGN KEY(`doctorId`) REFERENCES `doctor_profiles`(`doctorId`) ON DELETE CASCADE,
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE,
                    FOREIGN KEY(`patientSessionId`) REFERENCES `patient_sessions`(`sessionId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_ratings_doctorId` ON `doctor_ratings` (`doctorId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_doctor_ratings_consultationId` ON `doctor_ratings` (`consultationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_ratings_patientSessionId` ON `doctor_ratings` (`patientSessionId`)")

            // Create doctor_earnings with FKs
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_earnings` (
                    `earningId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `amount` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `paidAt` INTEGER DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`earningId`),
                    FOREIGN KEY(`doctorId`) REFERENCES `doctor_profiles`(`doctorId`) ON DELETE CASCADE,
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_earnings_doctorId_status` ON `doctor_earnings` (`doctorId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_earnings_consultationId` ON `doctor_earnings` (`consultationId`)")
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop old notifications table (schema changed completely)
            db.execSQL("DROP TABLE IF EXISTS `notifications`")

            // Create new notifications with readAt instead of isRead
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notifications` (
                    `notificationId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `data` TEXT NOT NULL,
                    `readAt` INTEGER DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`notificationId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_userId` ON `notifications` (`userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_type` ON `notifications` (`type`)")

            // Create video_calls with FK to consultations
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `video_calls` (
                    `callId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER DEFAULT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    `callQuality` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`callId`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_calls_consultationId` ON `video_calls` (`consultationId`)")

            // Create patient_reports with FKs to consultations and patient_sessions
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `patient_reports` (
                    `reportId` TEXT NOT NULL,
                    `consultationId` TEXT NOT NULL,
                    `patientSessionId` TEXT NOT NULL,
                    `reportUrl` TEXT NOT NULL,
                    `localFilePath` TEXT DEFAULT NULL,
                    `generatedAt` INTEGER NOT NULL,
                    `downloadedAt` INTEGER DEFAULT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `isDownloaded` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`reportId`),
                    FOREIGN KEY(`consultationId`) REFERENCES `consultations`(`consultationId`) ON DELETE CASCADE,
                    FOREIGN KEY(`patientSessionId`) REFERENCES `patient_sessions`(`sessionId`) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_reports_consultationId` ON `patient_reports` (`consultationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_reports_patientSessionId` ON `patient_reports` (`patientSessionId`)")

            // Create typing_indicators with composite PK
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `typing_indicators` (
                    `consultationId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `isTyping` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`consultationId`, `userId`)
                )
                """.trimIndent(),
            )
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns to service_tiers
            db.execSQL("ALTER TABLE service_tiers ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 15")
            db.execSQL("ALTER TABLE service_tiers ADD COLUMN features TEXT NOT NULL DEFAULT ''")

            // Update prices and new fields to match current service catalog
            db.execSQL("UPDATE service_tiers SET priceAmount = 5000, durationMinutes = 15, description = 'Normal consultations for everyday health concerns', features = 'Basic health advice,Symptom assessment,Health education' WHERE id = 'tier_nurse'")
            db.execSQL("UPDATE service_tiers SET priceAmount = 7000, durationMinutes = 15, description = 'Daily medical consultations for common ailments', features = 'Medical diagnosis,Treatment recommendations,Prescription guidance' WHERE id = 'tier_clinical_officer'")
            db.execSQL("UPDATE service_tiers SET priceAmount = 3000, durationMinutes = 5, description = 'Quick medication advice and drug interaction checks', features = 'Medication advice,Drug interaction checks,Dosage guidance' WHERE id = 'tier_pharmacist'")
            db.execSQL("UPDATE service_tiers SET priceAmount = 10000, durationMinutes = 15, description = 'Comprehensive care with specialist referrals when needed', features = 'Full medical assessment,Treatment planning,Specialist referrals' WHERE id = 'tier_gp'")
            db.execSQL("UPDATE service_tiers SET priceAmount = 30000, durationMinutes = 15, description = 'Expert consultation in specialized medical fields', features = 'Specialized expertise,Advanced diagnostics,Detailed treatment plans' WHERE id = 'tier_specialist'")
            db.execSQL("UPDATE service_tiers SET priceAmount = 50000, durationMinutes = 20, description = 'Professional mental health support and counseling', features = 'Mental health support,Professional counseling,Therapy session' WHERE id = 'tier_psychologist'")

            // Insert new Drug Interaction tier
            db.execSQL(
                """
                INSERT OR IGNORE INTO service_tiers (id, category, displayName, description, priceAmount, currency, isActive, sortOrder, durationMinutes, features)
                VALUES ('tier_drug_interaction', 'DRUG_INTERACTION', 'Drug Interaction', 'Check drug interactions and get safety guidance', 5000, 'TZS', 1, 7, 5, 'Drug interaction checks,Safety alerts,Dosage guidance')
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN passwordHash TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN services TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN countryCode TEXT NOT NULL DEFAULT '+255'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_profiles_email ON doctor_profiles (email)")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN country TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN licenseDocumentUrl TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN certificatesUrl TEXT DEFAULT NULL")

            // Create a new table without passwordHash, copy data, then swap
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_profiles_new` (
                    `doctorId` TEXT NOT NULL,
                    `fullName` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `specialty` TEXT NOT NULL,
                    `languages` TEXT NOT NULL,
                    `bio` TEXT NOT NULL,
                    `licenseNumber` TEXT NOT NULL,
                    `yearsExperience` INTEGER NOT NULL,
                    `profilePhotoUrl` TEXT DEFAULT NULL,
                    `averageRating` REAL NOT NULL DEFAULT 0.0,
                    `totalRatings` INTEGER NOT NULL DEFAULT 0,
                    `isVerified` INTEGER NOT NULL DEFAULT 0,
                    `isAvailable` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `services` TEXT NOT NULL DEFAULT '[]',
                    `countryCode` TEXT NOT NULL DEFAULT '+255',
                    `country` TEXT NOT NULL DEFAULT '',
                    `licenseDocumentUrl` TEXT DEFAULT NULL,
                    `certificatesUrl` TEXT DEFAULT NULL,
                    PRIMARY KEY(`doctorId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `doctor_profiles_new` (
                    `doctorId`, `fullName`, `email`, `phone`, `specialty`, `languages`,
                    `bio`, `licenseNumber`, `yearsExperience`, `profilePhotoUrl`,
                    `averageRating`, `totalRatings`, `isVerified`, `isAvailable`,
                    `createdAt`, `updatedAt`, `services`, `countryCode`, `country`,
                    `licenseDocumentUrl`, `certificatesUrl`
                )
                SELECT
                    `doctorId`, `fullName`, `email`, `phone`, `specialty`, `languages`,
                    `bio`, `licenseNumber`, `yearsExperience`, `profilePhotoUrl`,
                    `averageRating`, `totalRatings`, `isVerified`, `isAvailable`,
                    `createdAt`, `updatedAt`, `services`, `countryCode`, `country`,
                    `licenseDocumentUrl`, `certificatesUrl`
                FROM `doctor_profiles`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `doctor_profiles`")
            db.execSQL("ALTER TABLE `doctor_profiles_new` RENAME TO `doctor_profiles`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_profiles_isVerified_isAvailable_specialty` ON `doctor_profiles` (`isVerified`, `isAvailable`, `specialty`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_profiles_averageRating` ON `doctor_profiles` (`averageRating`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_profiles_email` ON `doctor_profiles` (`email`)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN specialistField TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate doctor_availability without foreign key to doctor_profiles
            db.execSQL("DROP TABLE IF EXISTS doctor_availability")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `doctor_availability` (" +
                    "`availabilityId` TEXT NOT NULL, " +
                    "`doctorId` TEXT NOT NULL, " +
                    "`isAvailable` INTEGER NOT NULL, " +
                    "`availabilitySchedule` TEXT NOT NULL, " +
                    "`lastUpdated` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`availabilityId`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_availability_doctorId` ON `doctor_availability` (`doctorId`)")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
    )
}
