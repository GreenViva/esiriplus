package com.esiri.esiriplus.feature.admin.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.repository.UserRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnusedPrivateProperty")
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : UserRepository {

    override fun getCurrentUser(): Flow<User?> = flowOf(null) // TODO: Implement

    override suspend fun getUser(userId: String): Result<User> =
        Result.Error(NotImplementedError("Not yet implemented"))

    override suspend fun updateUser(user: User): Result<User> =
        Result.Error(NotImplementedError("Not yet implemented"))
}
