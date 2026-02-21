package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.ServiceAccessPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceAccessPaymentDao {

    @Query("SELECT * FROM service_access_payments WHERE paymentId = :paymentId")
    suspend fun getById(paymentId: String): ServiceAccessPaymentEntity?

    @Query("SELECT * FROM service_access_payments ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ServiceAccessPaymentEntity>>

    @Query("SELECT * FROM service_access_payments WHERE serviceType = :serviceType ORDER BY createdAt DESC")
    fun getByServiceType(serviceType: String): Flow<List<ServiceAccessPaymentEntity>>

    @Query("SELECT * FROM service_access_payments WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<ServiceAccessPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: ServiceAccessPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<ServiceAccessPaymentEntity>)

    @Delete
    suspend fun delete(payment: ServiceAccessPaymentEntity)

    @Query("DELETE FROM service_access_payments")
    suspend fun clearAll()
}
