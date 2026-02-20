package com.esiri.esiriplus.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorCredentialsDao {

    @Query("SELECT * FROM doctor_credentials WHERE doctorId = :doctorId")
    fun getByDoctorId(doctorId: String): Flow<List<DoctorCredentialsEntity>>

    @Query("SELECT * FROM doctor_credentials WHERE credentialId = :credentialId")
    suspend fun getById(credentialId: String): DoctorCredentialsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: DoctorCredentialsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(credentials: List<DoctorCredentialsEntity>)

    @Query("DELETE FROM doctor_credentials WHERE credentialId = :credentialId")
    suspend fun delete(credentialId: String)

    @Query("DELETE FROM doctor_credentials WHERE doctorId = :doctorId")
    suspend fun deleteByDoctorId(doctorId: String)
}
