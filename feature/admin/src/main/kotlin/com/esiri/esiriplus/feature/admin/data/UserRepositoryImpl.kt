package com.esiri.esiriplus.feature.admin.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.repository.UserRepository
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.UpdateUserBody
import com.esiri.esiriplus.core.network.api.model.toDomain
import com.esiri.esiriplus.core.network.model.safeApiCall
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.model.toApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val supabaseApi: SupabaseApi,
) : UserRepository {

    override fun getCurrentUser(): Flow<User?> = flow {
        // Current user context comes from the auth token;
        // a dedicated endpoint or local cache would be used here.
        emit(null)
    }

    override suspend fun getUser(userId: String): Result<User> {
        val apiResult = safeApiCall {
            val response = supabaseApi.getUser(idFilter = "eq.$userId")
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }

    override suspend fun updateUser(user: User): Result<User> {
        val apiResult = safeApiCall {
            val response = supabaseApi.updateUser(
                idFilter = "eq.${user.id}",
                body = UpdateUserBody(
                    fullName = user.fullName,
                    phone = user.phone,
                    email = user.email,
                ),
            )
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }
}
