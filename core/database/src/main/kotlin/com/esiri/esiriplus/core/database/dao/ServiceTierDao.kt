package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceTierDao {
    @Query("SELECT * FROM service_tiers WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getActiveServiceTiers(): Flow<List<ServiceTierEntity>>

    @Query("SELECT * FROM service_tiers WHERE id = :id")
    suspend fun getServiceTierById(id: String): ServiceTierEntity?

    @Query("SELECT * FROM service_tiers WHERE category = :category AND isActive = 1")
    suspend fun getServiceTierByCategory(category: String): ServiceTierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceTiers(tiers: List<ServiceTierEntity>)

    @Query("SELECT COUNT(*) FROM service_tiers")
    suspend fun count(): Int
}
