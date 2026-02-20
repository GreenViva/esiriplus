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

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
