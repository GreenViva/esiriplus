package com.esiri.esiriplus.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.esiri.esiriplus.core.database.EsiriplusDatabase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.PatientReportEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PatientReportDaoTest {

    private lateinit var database: EsiriplusDatabase
    private lateinit var dao: PatientReportDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EsiriplusDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.patientReportDao()

        // Insert parent entities for FK chain
        runTest {
            database.patientSessionDao().insert(
                PatientSessionEntity(
                    sessionId = "session-1",
                    sessionTokenHash = "hash1",
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-1",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
                    status = "COMPLETED",
                    serviceType = "GP",
                    consultationFee = 5000,
                    requestExpiresAt = 2000L,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
            database.consultationDao().insert(
                ConsultationEntity(
                    consultationId = "consult-2",
                    patientSessionId = "session-1",
                    doctorId = "doc-1",
                    status = "COMPLETED",
                    serviceType = "GP",
                    consultationFee = 5000,
                    requestExpiresAt = 2000L,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createReport(
        reportId: String = "report-1",
        consultationId: String = "consult-1",
        patientSessionId: String = "session-1",
        reportUrl: String = "https://example.com/report.pdf",
        localFilePath: String? = null,
        generatedAt: Long = 1000L,
        downloadedAt: Long? = null,
        fileSizeBytes: Long = 1024L,
        isDownloaded: Boolean = false,
    ) = PatientReportEntity(
        reportId = reportId,
        consultationId = consultationId,
        patientSessionId = patientSessionId,
        reportUrl = reportUrl,
        localFilePath = localFilePath,
        generatedAt = generatedAt,
        downloadedAt = downloadedAt,
        fileSizeBytes = fileSizeBytes,
        isDownloaded = isDownloaded,
    )

    @Test
    fun `insert and retrieve by id`() = runTest {
        dao.insert(createReport())

        val result = dao.getById("report-1")
        assertNotNull(result)
        assertEquals("report-1", result!!.reportId)
        assertEquals("consult-1", result.consultationId)
        assertEquals("session-1", result.patientSessionId)
        assertEquals(1024L, result.fileSizeBytes)
        assertFalse(result.isDownloaded)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(dao.getById("non-existent"))
    }

    @Test
    fun `getByConsultationId returns reports ordered by generatedAt DESC`() = runTest {
        dao.insert(createReport("report-1", generatedAt = 1000L))
        dao.insert(createReport("report-2", generatedAt = 3000L))

        dao.getByConsultationId("consult-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("report-2", result[0].reportId)
            assertEquals("report-1", result[1].reportId)
            cancel()
        }
    }

    @Test
    fun `getByPatientSessionId returns reports ordered by generatedAt DESC`() = runTest {
        dao.insert(createReport("report-1", consultationId = "consult-1", generatedAt = 1000L))
        dao.insert(createReport("report-2", consultationId = "consult-2", generatedAt = 3000L))

        dao.getByPatientSessionId("session-1").test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("report-2", result[0].reportId)
            assertEquals("report-1", result[1].reportId)
            cancel()
        }
    }

    @Test
    fun `getDownloadedReports returns only downloaded`() = runTest {
        dao.insert(createReport("report-1", isDownloaded = false))
        dao.insert(createReport("report-2", isDownloaded = true, downloadedAt = 5000L))

        dao.getDownloadedReports().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("report-2", result[0].reportId)
            cancel()
        }
    }

    @Test
    fun `markAsDownloaded updates download fields`() = runTest {
        dao.insert(createReport("report-1"))
        assertFalse(dao.getById("report-1")!!.isDownloaded)

        dao.markAsDownloaded("report-1", "/local/report.pdf", 9999L)

        val updated = dao.getById("report-1")!!
        assertTrue(updated.isDownloaded)
        assertEquals("/local/report.pdf", updated.localFilePath)
        assertEquals(9999L, updated.downloadedAt)
    }

    @Test
    fun `insertAll inserts multiple`() = runTest {
        dao.insertAll(
            listOf(
                createReport("report-1"),
                createReport("report-2"),
            ),
        )

        assertNotNull(dao.getById("report-1"))
        assertNotNull(dao.getById("report-2"))
    }

    @Test
    fun `delete removes specific report`() = runTest {
        val report = createReport()
        dao.insert(report)
        dao.delete(report)

        assertNull(dao.getById("report-1"))
    }

    @Test
    fun `clearAll removes all reports`() = runTest {
        dao.insert(createReport("report-1"))
        dao.insert(createReport("report-2"))
        dao.clearAll()

        assertNull(dao.getById("report-1"))
        assertNull(dao.getById("report-2"))
    }

    @Test
    fun `cascade delete removes reports when consultation cleared`() = runTest {
        dao.insert(createReport("report-1", consultationId = "consult-1"))
        assertNotNull(dao.getById("report-1"))

        // Deleting the parent session cascades to consultations, then to patient_reports
        database.patientSessionDao().clearAll()

        assertNull(dao.getById("report-1"))
    }
}
