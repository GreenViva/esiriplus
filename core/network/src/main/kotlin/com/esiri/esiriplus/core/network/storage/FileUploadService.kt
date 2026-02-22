package com.esiri.esiriplus.core.network.storage

import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploadService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {
    private val storage: Storage =
        supabaseClientProvider.client.pluginManager.getPlugin(Storage)

    suspend fun uploadFile(
        bucketName: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): ApiResult<String> = safeApiCall {
        val parsedType = ContentType.parse(contentType)
        storage.from(bucketName).upload(path, bytes) {
            upsert = true
            this.contentType = parsedType
        }
        path
    }

    fun getPublicUrl(bucketName: String, path: String): String {
        return storage.from(bucketName).publicUrl(path)
    }

    /**
     * Update doctor profile file URLs in the server database via Postgrest.
     */
    suspend fun updateDoctorProfileUrls(
        doctorId: String,
        profilePhotoUrl: String?,
        licenseDocumentUrl: String?,
        certificatesUrl: String?,
    ) {
        supabaseClientProvider.client.from("doctor_profiles").update(
            buildJsonObject {
                profilePhotoUrl?.let { put("profile_photo_url", it) }
                licenseDocumentUrl?.let { put("license_document_url", it) }
                certificatesUrl?.let { put("certificates_url", it) }
            },
        ) {
            filter { eq("doctor_id", doctorId) }
        }
    }

    /**
     * Update doctor editable profile fields (languages, services, availability, photo)
     * in the server database via Postgrest.
     */
    suspend fun updateDoctorEditableFields(
        doctorId: String,
        languages: List<String>,
        services: List<String>,
        isAvailable: Boolean,
        profilePhotoUrl: String?,
    ) {
        supabaseClientProvider.client.from("doctor_profiles").update(
            buildJsonObject {
                put("languages", JsonArray(languages.map { JsonPrimitive(it) }))
                put("services", JsonArray(services.map { JsonPrimitive(it) }))
                put("is_available", isAvailable)
                profilePhotoUrl?.let { put("profile_photo_url", it) }
                put("updated_at", java.time.Instant.now().toString())
            },
        ) {
            filter { eq("doctor_id", doctorId) }
        }
    }
}
