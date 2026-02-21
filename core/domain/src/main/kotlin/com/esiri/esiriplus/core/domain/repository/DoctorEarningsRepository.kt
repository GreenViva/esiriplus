package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.DoctorEarnings
import kotlinx.coroutines.flow.Flow

interface DoctorEarningsRepository {
    fun getEarningsForDoctor(doctorId: String): Flow<List<DoctorEarnings>>
    fun getEarningsByStatus(doctorId: String, status: String): Flow<List<DoctorEarnings>>
    fun getTotalEarnings(doctorId: String, startDate: Long, endDate: Long): Flow<Int>
    suspend fun clearAll()
}
