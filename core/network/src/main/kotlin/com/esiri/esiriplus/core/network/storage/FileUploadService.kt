package com.esiri.esiriplus.core.network.storage

import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploadService @Inject constructor(
    supabaseClientProvider: SupabaseClientProvider,
) {
    private val storage: Storage =
        supabaseClientProvider.client.pluginManager.getPlugin(Storage)

    suspend fun uploadFile(
        bucketName: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): ApiResult<String> = safeApiCall {
        storage.from(bucketName).upload(path, bytes) {
            upsert = true
        }
        path
    }

    fun getPublicUrl(bucketName: String, path: String): String {
        return storage.from(bucketName).publicUrl(path)
    }
}
