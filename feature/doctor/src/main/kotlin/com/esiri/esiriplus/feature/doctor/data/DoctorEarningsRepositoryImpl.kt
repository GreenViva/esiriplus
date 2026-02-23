package com.esiri.esiriplus.feature.doctor.data

import com.esiri.esiriplus.core.database.dao.DoctorEarningsDao
import com.esiri.esiriplus.core.database.entity.DoctorEarningsEntity
import com.esiri.esiriplus.core.domain.model.DoctorEarnings
import com.esiri.esiriplus.core.domain.model.EarningStatus
import com.esiri.esiriplus.core.domain.repository.DoctorEarningsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorEarningsRepositoryImpl @Inject constructor(
    private val doctorEarningsDao: DoctorEarningsDao,
) : DoctorEarningsRepository {

    override fun getEarningsForDoctor(doctorId: String): Flow<List<DoctorEarnings>> =
        doctorEarningsDao.getEarningsForDoctor(doctorId)
            .map { entities -> entities.map { it.toDomain() } }

    override fun getEarningsByStatus(doctorId: String, status: String): Flow<List<DoctorEarnings>> =
        doctorEarningsDao.getEarningsByStatus(doctorId, status)
            .map { entities -> entities.map { it.toDomain() } }

    override fun getTotalEarnings(doctorId: String, startDate: Long, endDate: Long): Flow<Int> =
        doctorEarningsDao.getTotalEarnings(doctorId, startDate, endDate)

    override suspend fun clearAll() {
        doctorEarningsDao.clearAll()
    }
}

private fun DoctorEarningsEntity.toDomain() = DoctorEarnings(
    earningId = earningId,
    doctorId = doctorId,
    consultationId = consultationId,
    amount = amount,
    status = EarningStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: EarningStatus.PENDING,
    paidAt = paidAt,
    createdAt = createdAt,
)
