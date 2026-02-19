package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY createdAt DESC")
    fun getForUser(userId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(log: AuditLogEntity)
}
