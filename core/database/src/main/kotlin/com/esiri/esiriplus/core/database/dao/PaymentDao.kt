package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT * FROM payments WHERE paymentId = :paymentId")
    suspend fun getById(paymentId: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE patientSessionId = :sessionId ORDER BY createdAt DESC")
    fun getByPatientSessionId(sessionId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    fun getTransactionHistory(limit: Int, offset: Int): Flow<List<PaymentEntity>>

    @Query("UPDATE payments SET status = :status, transactionId = :transactionId, updatedAt = :updatedAt WHERE paymentId = :paymentId")
    suspend fun updateStatus(paymentId: String, status: String, transactionId: String?, updatedAt: Long)

    @Query("SELECT * FROM payments WHERE synced = 0")
    suspend fun getUnsyncedPayments(): List<PaymentEntity>

    @Query("UPDATE payments SET synced = 1 WHERE paymentId = :paymentId")
    suspend fun markSynced(paymentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Delete
    suspend fun delete(payment: PaymentEntity)

    @Query("DELETE FROM payments")
    suspend fun clearAll()
}
