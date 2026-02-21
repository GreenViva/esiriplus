package com.esiri.esiriplus.core.database.callback

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.esiri.esiriplus.core.database.encryption.DatabaseEncryption
import com.esiri.esiriplus.core.database.init.DatabaseEncryptionException

class DatabaseCallback(
    private val context: Context,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        Log.d(TAG, "Database created, prepopulating data...")
        prepopulateServiceTiers(db)
        prepopulateAppConfig(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        verifyEncryption()
    }

    private fun prepopulateServiceTiers(db: SupportSQLiteDatabase) {
        SERVICE_TIERS.forEach { tier ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO service_tiers (id, category, displayName, description, priceAmount, currency, isActive, sortOrder)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    tier.id,
                    tier.category,
                    tier.displayName,
                    tier.description,
                    tier.priceAmount,
                    tier.currency,
                    tier.sortOrder,
                ),
            )
        }
        Log.d(TAG, "Prepopulated ${SERVICE_TIERS.size} service tiers")
    }

    private fun prepopulateAppConfig(db: SupportSQLiteDatabase) {
        APP_CONFIG_DEFAULTS.forEach { (key, value, description) ->
            db.execSQL(
                "INSERT OR IGNORE INTO app_config (`key`, value, description) VALUES (?, ?, ?)",
                arrayOf(key, value, description),
            )
        }
        Log.d(TAG, "Prepopulated ${APP_CONFIG_DEFAULTS.size} app config entries")
    }

    private fun verifyEncryption() {
        val isEncrypted = DatabaseEncryption.verifyEncryption(context)
        if (isEncrypted) {
            Log.d(TAG, "Database encryption verified successfully")
        } else {
            throw DatabaseEncryptionException("Database encryption verification failed")
        }
    }

    companion object {
        private const val TAG = "DatabaseCallback"

        private data class ServiceTierSeed(
            val id: String,
            val category: String,
            val displayName: String,
            val description: String,
            val priceAmount: Int,
            val currency: String,
            val sortOrder: Int,
        )

        private data class AppConfigSeed(
            val key: String,
            val value: String,
            val description: String,
        )

        private val SERVICE_TIERS = listOf(
            ServiceTierSeed(
                id = "tier_nurse",
                category = "NURSE",
                displayName = "Nurse",
                description = "Consultation with a registered nurse",
                priceAmount = 3000,
                currency = "TZS",
                sortOrder = 1,
            ),
            ServiceTierSeed(
                id = "tier_clinical_officer",
                category = "CLINICAL_OFFICER",
                displayName = "Clinical Officer",
                description = "Consultation with a clinical officer",
                priceAmount = 5000,
                currency = "TZS",
                sortOrder = 2,
            ),
            ServiceTierSeed(
                id = "tier_pharmacist",
                category = "PHARMACIST",
                displayName = "Pharmacist",
                description = "Consultation with a pharmacist",
                priceAmount = 5000,
                currency = "TZS",
                sortOrder = 3,
            ),
            ServiceTierSeed(
                id = "tier_gp",
                category = "GP",
                displayName = "General Practitioner",
                description = "Consultation with a general practitioner",
                priceAmount = 10000,
                currency = "TZS",
                sortOrder = 4,
            ),
            ServiceTierSeed(
                id = "tier_specialist",
                category = "SPECIALIST",
                displayName = "Specialist",
                description = "Consultation with a medical specialist",
                priceAmount = 15000,
                currency = "TZS",
                sortOrder = 5,
            ),
            ServiceTierSeed(
                id = "tier_psychologist",
                category = "PSYCHOLOGIST",
                displayName = "Psychologist",
                description = "Consultation with a psychologist",
                priceAmount = 12000,
                currency = "TZS",
                sortOrder = 6,
            ),
        )

        private val APP_CONFIG_DEFAULTS = listOf(
            AppConfigSeed(
                key = "session_timeout_minutes",
                value = "30",
                description = "Session timeout duration in minutes",
            ),
            AppConfigSeed(
                key = "max_file_size_mb",
                value = "10",
                description = "Maximum file upload size in megabytes",
            ),
            AppConfigSeed(
                key = "consultation_timeout_hours",
                value = "24",
                description = "Maximum consultation duration in hours",
            ),
            AppConfigSeed(
                key = "max_attachments_per_consultation",
                value = "5",
                description = "Maximum number of attachments per consultation",
            ),
            AppConfigSeed(
                key = "payment_poll_interval_ms",
                value = "3000",
                description = "Payment status polling interval in milliseconds",
            ),
        )
    }
}
