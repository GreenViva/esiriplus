package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.PaymentDao
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.model.PaymentMethod
import com.esiri.esiriplus.core.domain.model.PaymentStatus
import com.esiri.esiriplus.core.domain.repository.PaymentRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.PaymentService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val paymentDao: PaymentDao,
    private val paymentService: PaymentService,
) : PaymentRepository {

    override fun getPaymentsBySession(sessionId: String): Flow<List<Payment>> =
        paymentDao.getByPatientSessionId(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getPaymentsByStatus(status: String): Flow<List<Payment>> =
        paymentDao.getByStatus(status).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getTransactionHistory(limit: Int, offset: Int): Flow<List<Payment>> =
        paymentDao.getTransactionHistory(limit, offset).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getPaymentById(paymentId: String): Payment? =
        paymentDao.getById(paymentId)?.toDomain()

    override suspend fun createPayment(payment: Payment): Result<Payment> {
        return try {
            // Save locally first
            paymentDao.insert(payment.toEntity())

            // Call edge function to initiate STK push
            val result = paymentService.initiateServicePayment(
                phoneNumber = payment.phoneNumber,
                amount = payment.amount,
                serviceType = "general",
                idempotencyKey = payment.paymentId,
            )

            when (result) {
                is ApiResult.Success -> {
                    // Update local record with server payment ID
                    val serverPaymentId = result.data.paymentId
                    val now = System.currentTimeMillis()
                    paymentDao.updateStatus(payment.paymentId, "PENDING", null, now)
                    Result.Success(payment.copy(updatedAt = now))
                }
                is ApiResult.Error -> {
                    paymentDao.updateStatus(payment.paymentId, "FAILED", null, System.currentTimeMillis())
                    Result.Error(Exception(result.message), result.message)
                }
                is ApiResult.NetworkError -> {
                    paymentDao.updateStatus(payment.paymentId, "FAILED", null, System.currentTimeMillis())
                    Result.Error(result.exception, result.message)
                }
                is ApiResult.Unauthorized -> {
                    paymentDao.updateStatus(payment.paymentId, "FAILED", null, System.currentTimeMillis())
                    Result.Error(Exception("Unauthorized"), "Session expired")
                }
            }
        } catch (e: Exception) {
            Result.Error(e, e.message)
        }
    }

    override suspend fun updatePaymentStatus(paymentId: String, status: String, transactionId: String?) {
        paymentDao.updateStatus(paymentId, status, transactionId, System.currentTimeMillis())
    }

    override suspend fun getUnsyncedPayments(): List<Payment> =
        paymentDao.getUnsyncedPayments().map { it.toDomain() }

    override suspend fun markPaymentSynced(paymentId: String) {
        paymentDao.markSynced(paymentId)
    }

    override suspend fun clearAll() {
        paymentDao.clearAll()
    }
}

private fun PaymentEntity.toDomain(): Payment = Payment(
    paymentId = paymentId,
    patientSessionId = patientSessionId,
    amount = amount,
    paymentMethod = try { PaymentMethod.valueOf(paymentMethod) } catch (_: Exception) { PaymentMethod.MPESA },
    transactionId = transactionId,
    phoneNumber = phoneNumber,
    status = try { PaymentStatus.valueOf(status) } catch (_: Exception) { PaymentStatus.PENDING },
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    synced = synced,
)

private fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    paymentId = paymentId,
    patientSessionId = patientSessionId,
    amount = amount,
    paymentMethod = paymentMethod.name,
    transactionId = transactionId,
    phoneNumber = phoneNumber,
    status = status.name,
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    synced = synced,
)
