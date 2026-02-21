package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.CallRechargePaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRechargePaymentDao {

    @Query("SELECT * FROM call_recharge_payments WHERE paymentId = :paymentId")
    suspend fun getById(paymentId: String): CallRechargePaymentEntity?

    @Query("SELECT * FROM call_recharge_payments WHERE consultationId = :consultationId ORDER BY createdAt DESC")
    fun getByConsultationId(consultationId: String): Flow<List<CallRechargePaymentEntity>>

    @Query("SELECT * FROM call_recharge_payments WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<CallRechargePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: CallRechargePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<CallRechargePaymentEntity>)

    @Delete
    suspend fun delete(payment: CallRechargePaymentEntity)

    @Query("DELETE FROM call_recharge_payments")
    suspend fun clearAll()
}
