package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE `key` = :key")
    suspend fun getConfig(key: String): AppConfigEntity?

    @Query("SELECT value FROM app_config WHERE `key` = :key")
    suspend fun getConfigValue(key: String): String?

    @Query("SELECT * FROM app_config")
    fun getAllConfig(): Flow<List<AppConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<AppConfigEntity>)

    @Query("SELECT COUNT(*) FROM app_config")
    suspend fun count(): Int
}
