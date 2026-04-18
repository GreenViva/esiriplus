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

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE doctor_profiles ADD COLUMN rejectionReason TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate messages table WITHOUT foreign key to consultations.
            // Messages are cached from the server; the consultation may not exist
            // locally, so the FK constraint prevents all message inserts.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_new` (
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
                    PRIMARY KEY(`messageId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `messages_new` (
                    `messageId`, `consultationId`, `senderType`, `senderId`,
                    `messageText`, `messageType`, `attachmentUrl`, `isRead`,
                    `synced`, `createdAt`
                )
                SELECT
                    `messageId`, `consultationId`, `senderType`, `senderId`,
                    `messageText`, `messageType`, `attachmentUrl`, `isRead`,
                    `synced`, `createdAt`
                FROM `messages`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `messages`")
            db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_consultationId_createdAt` ON `messages` (`consultationId`, `createdAt`)")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate consultations table WITHOUT foreign key to patient_sessions.
            // Consultations are cached from the server; the patient session may not
            // exist locally (especially on the doctor side), so the FK prevents inserts.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `consultations_new` (
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
                    PRIMARY KEY(`consultationId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `consultations_new` (
                    `consultationId`, `patientSessionId`, `doctorId`, `status`,
                    `serviceType`, `consultationFee`, `sessionStartTime`, `sessionEndTime`,
                    `sessionDurationMinutes`, `requestExpiresAt`, `createdAt`, `updatedAt`
                )
                SELECT
                    `consultationId`, `patientSessionId`, `doctorId`, `status`,
                    `serviceType`, `consultationFee`, `sessionStartTime`, `sessionEndTime`,
                    `sessionDurationMinutes`, `requestExpiresAt`, `createdAt`, `updatedAt`
                FROM `consultations`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `consultations`")
            db.execSQL("ALTER TABLE `consultations_new` RENAME TO `consultations`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_patientSessionId` ON `consultations` (`patientSessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_doctorId` ON `consultations` (`doctorId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_status_createdAt` ON `consultations` (`status`, `createdAt`)")
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `failedAt` INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `isBanned` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `bannedAt` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `banReason` TEXT DEFAULT NULL")
        }
    }

    @Suppress("LongMethod")
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create appointments table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `appointments` (
                    `appointmentId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `patientSessionId` TEXT NOT NULL,
                    `scheduledAt` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL DEFAULT 15,
                    `status` TEXT NOT NULL,
                    `serviceType` TEXT NOT NULL,
                    `consultationType` TEXT NOT NULL DEFAULT 'chat',
                    `chiefComplaint` TEXT NOT NULL DEFAULT '',
                    `consultationFee` INTEGER NOT NULL DEFAULT 0,
                    `consultationId` TEXT DEFAULT NULL,
                    `rescheduledFrom` TEXT DEFAULT NULL,
                    `reminderSentAt` INTEGER DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`appointmentId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_doctorId_status` ON `appointments` (`doctorId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_patientSessionId_status` ON `appointments` (`patientSessionId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_scheduledAt` ON `appointments` (`scheduledAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_appointments_status_scheduledAt` ON `appointments` (`status`, `scheduledAt`)")

            // 2. Create doctor_availability_slots table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `doctor_availability_slots` (
                    `slotId` TEXT NOT NULL,
                    `doctorId` TEXT NOT NULL,
                    `dayOfWeek` INTEGER NOT NULL,
                    `startTime` TEXT NOT NULL,
                    `endTime` TEXT NOT NULL,
                    `bufferMinutes` INTEGER NOT NULL DEFAULT 5,
                    `isActive` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`slotId`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_availability_slots_doctorId_isActive` ON `doctor_availability_slots` (`doctorId`, `isActive`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_doctor_availability_slots_dayOfWeek_isActive` ON `doctor_availability_slots` (`dayOfWeek`, `isActive`)")

            // 3. Add inSession and maxAppointmentsPerDay to doctor_profiles
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `inSession` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `maxAppointmentsPerDay` INTEGER NOT NULL DEFAULT 10")
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `scheduledEndAt` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `extensionCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `gracePeriodEndAt` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `originalDurationMinutes` INTEGER NOT NULL DEFAULT 15")
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `payments` ADD COLUMN `consultationId` TEXT DEFAULT ''")
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `doctorName` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `consultationDate` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `diagnosedProblem` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `severity` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `presentingSymptoms` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `diagnosisAssessment` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `treatmentPlan` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `followUpInstructions` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `followUpRecommended` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `furtherNotes` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `verificationCode` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `meetingId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `initiatedBy` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `callType` TEXT NOT NULL DEFAULT 'VIDEO'")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'initiated'")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `timeLimitSeconds` INTEGER NOT NULL DEFAULT 180")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `timeUsedSeconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `isTimeExpired` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `video_calls` ADD COLUMN `totalRecharges` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ── Tiered consultation system ────────────────────────────────────────────
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `serviceTier` TEXT NOT NULL DEFAULT 'ECONOMY'")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `serviceRegion` TEXT NOT NULL DEFAULT 'TANZANIA'")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `followUpExpiry` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `isPremium` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // ── Follow-up chat history ────────────────────────────────────────────────
    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `parentConsultationId` TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `prescribedMedications` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `prescriptionsJson` TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `doctor_earnings` ADD COLUMN `earningType` TEXT NOT NULL DEFAULT 'consultation'")
        }
    }

    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `doctor_profiles` ADD COLUMN `canServeAsGp` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recreate patient_profiles WITHOUT foreign key to users table.
            // Patient userId is a session_id, not a users.id — the FK was wrong
            // and caused crashes when inserting patient profiles.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `patient_profiles_new` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `dateOfBirth` INTEGER DEFAULT NULL,
                    `bloodGroup` TEXT DEFAULT NULL,
                    `allergies` TEXT DEFAULT NULL,
                    `emergencyContactName` TEXT DEFAULT NULL,
                    `emergencyContactPhone` TEXT DEFAULT NULL,
                    `sex` TEXT DEFAULT NULL,
                    `ageGroup` TEXT DEFAULT NULL,
                    `chronicConditions` TEXT DEFAULT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `patient_profiles_new` (
                    `id`, `userId`, `dateOfBirth`, `bloodGroup`, `allergies`,
                    `emergencyContactName`, `emergencyContactPhone`,
                    `sex`, `ageGroup`, `chronicConditions`
                )
                SELECT
                    `id`, `userId`, `dateOfBirth`, `bloodGroup`, `allergies`,
                    `emergencyContactName`, `emergencyContactPhone`,
                    `sex`, `ageGroup`, `chronicConditions`
                FROM `patient_profiles`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `patient_profiles`")
            db.execSQL("ALTER TABLE `patient_profiles_new` RENAME TO `patient_profiles`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_patient_profiles_userId` ON `patient_profiles` (`userId`)")
        }
    }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `patientAge` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `patient_reports` ADD COLUMN `patientGender` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `followUpCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `followUpMax` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `isReopened` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `consultations` ADD COLUMN `lastReopenedAt` INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `patient_sessions` ADD COLUMN `serviceDistrict` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `patient_sessions` ADD COLUMN `serviceWard` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `patient_sessions` ADD COLUMN `serviceStreet` TEXT DEFAULT NULL")
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
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
    )
}
