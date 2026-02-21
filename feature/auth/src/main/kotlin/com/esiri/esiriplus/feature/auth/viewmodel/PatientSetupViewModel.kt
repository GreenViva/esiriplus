package com.esiri.esiriplus.feature.auth.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.database.entity.PatientProfileEntity
import com.esiri.esiriplus.core.domain.usecase.CreatePatientSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class PatientSetupUiState(
    val patientId: String = "",
    val recoveryQuestionsCompleted: Boolean = false,
    val sex: String = "",
    val ageGroup: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val chronicConditions: String = "",
    val isCreatingSession: Boolean = true,
    val isSaving: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val sessionError: String? = null,
    val saveError: String? = null,
    val pdfError: String? = null,
    val isComplete: Boolean = false,
) {
    val canDownloadPdf: Boolean
        get() = patientId.isNotBlank() &&
            sex.isNotBlank() &&
            ageGroup.isNotBlank()
}

@HiltViewModel
class PatientSetupViewModel @Inject constructor(
    private val createPatientSession: CreatePatientSessionUseCase,
    private val patientProfileDao: PatientProfileDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientSetupUiState())
    val uiState: StateFlow<PatientSetupUiState> = _uiState.asStateFlow()

    init {
        createSession()
    }

    private fun createSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingSession = true, sessionError = null) }
            when (val result = createPatientSession()) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isCreatingSession = false,
                            patientId = result.data.user.id,
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        isCreatingSession = false,
                        sessionError = result.message ?: "Failed to create session",
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun retryCreateSession() {
        createSession()
    }

    fun onRecoveryQuestionsCompleted() {
        _uiState.update { it.copy(recoveryQuestionsCompleted = true) }
    }

    fun onSexChanged(sex: String) {
        _uiState.update { it.copy(sex = sex) }
    }

    fun onAgeGroupChanged(ageGroup: String) {
        _uiState.update { it.copy(ageGroup = ageGroup) }
    }

    fun onBloodTypeChanged(bloodType: String) {
        _uiState.update { it.copy(bloodType = bloodType) }
    }

    fun onAllergiesChanged(allergies: String) {
        _uiState.update { it.copy(allergies = allergies) }
    }

    fun onChronicConditionsChanged(chronicConditions: String) {
        _uiState.update { it.copy(chronicConditions = chronicConditions) }
    }

    fun onContinue() {
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            saveHealthProfileIfNeeded(state)
            _uiState.update { it.copy(isSaving = false, isComplete = true) }
        }
    }

    fun downloadIdCard(context: Context) {
        val state = _uiState.value
        if (!state.canDownloadPdf || state.isGeneratingPdf) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPdf = true, pdfError = null) }
            try {
                val pdfDocument = withContext(Dispatchers.Default) {
                    createIdCardPdf(state)
                }
                withContext(Dispatchers.IO) {
                    savePdfToDownloads(context, pdfDocument, state.patientId)
                }
                pdfDocument.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "ID Card saved to Downloads",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(pdfError = e.message ?: "Failed to generate PDF")
                }
            } finally {
                _uiState.update { it.copy(isGeneratingPdf = false) }
            }
        }
    }

    private fun createIdCardPdf(state: PatientSetupUiState): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val pageWidth = 595f

        val tealColor = 0xFF2A9D8F.toInt()
        val darkColor = 0xFF1A1A2E.toInt()
        val grayColor = 0xFF6B7280.toInt()
        val white = 0xFFFFFFFF.toInt()
        val lightBg = 0xFFF9FAFB.toInt()

        val paint = Paint().apply { isAntiAlias = true }
        val pageHeight = 842f

        // --- Watermark pattern ---
        val watermarkPaint = Paint().apply {
            isAntiAlias = true
            color = 0x0D2A9D8F.toInt() // teal at ~5% opacity
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.save()
        canvas.rotate(-30f, pageWidth / 2f, pageHeight / 2f)
        val wmText = "eSIRI"
        val wmSpacingX = 140f
        val wmSpacingY = 100f
        var wmY = -200f
        while (wmY < pageHeight + 400f) {
            var wmX = -200f
            while (wmX < pageWidth + 400f) {
                canvas.drawText(wmText, wmX, wmY, watermarkPaint)
                wmX += wmSpacingX
            }
            wmY += wmSpacingY
        }
        canvas.restore()

        // --- Header bar ---
        paint.color = tealColor
        canvas.drawRect(0f, 0f, pageWidth, 80f, paint)

        paint.color = white
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        canvas.drawText("eSIRI Plus", 40f, 40f, paint)

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Patient ID Card", 40f, 62f, paint)

        // --- Patient ID card ---
        val cardLeft = 60f
        val cardRight = pageWidth - 60f
        val cardTop = 120f
        val cardBottom = 240f

        paint.color = tealColor
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 16f, 16f, paint)

        paint.color = white
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("YOUR PATIENT ID", cardLeft + 30f, cardTop + 40f, paint)

        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(state.patientId, cardLeft + 30f, cardTop + 80f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawText("Save this ID for future access", cardLeft + 30f, cardTop + 105f, paint)

        // --- Health Profile section ---
        var y = 290f

        paint.color = darkColor
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Health Profile", 60f, y, paint)
        y += 20f

        // Divider
        paint.color = 0xFFE5E7EB.toInt()
        paint.strokeWidth = 1f
        canvas.drawLine(60f, y, pageWidth - 60f, y, paint)
        y += 30f

        // Profile rows
        val rows = listOf(
            "Sex" to state.sex,
            "Age Group" to state.ageGroup,
            "Blood Type" to state.bloodType.ifBlank { "Unknown" },
            "Allergies" to state.allergies.ifBlank { "None" },
            "Chronic Conditions" to state.chronicConditions.ifBlank { "None" },
        )

        val labelPaint = Paint(paint).apply {
            color = grayColor
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val valuePaint = Paint(paint).apply {
            color = darkColor
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        for ((label, value) in rows) {
            canvas.drawText("$label:", 80f, y, labelPaint)
            canvas.drawText(value, 280f, y, valuePaint)
            y += 32f
        }

        // --- Footer divider ---
        y += 20f
        paint.color = 0xFFE5E7EB.toInt()
        canvas.drawLine(60f, y, pageWidth - 60f, y, paint)
        y += 30f

        // Generated date
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        paint.color = grayColor
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated: $dateStr", 60f, y, paint)
        y += 20f
        canvas.drawText("This is an official eSIRI Plus", 60f, y, paint)
        y += 18f
        canvas.drawText("patient identification document.", 60f, y, paint)

        document.finishPage(page)
        return document
    }

    private fun savePdfToDownloads(
        context: Context,
        document: PdfDocument,
        patientId: String,
    ) {
        val fileName = "eSIRI_ID_Card_${patientId.replace("-", "_")}.pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create download entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            document.writeTo(outputStream)
        } ?: throw IllegalStateException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private suspend fun saveHealthProfileIfNeeded(state: PatientSetupUiState) {
        val hasHealthData = state.sex.isNotBlank() ||
            state.ageGroup.isNotBlank() ||
            state.bloodType.isNotBlank() ||
            state.allergies.isNotBlank() ||
            state.chronicConditions.isNotBlank()

        if (hasHealthData) {
            val profile = PatientProfileEntity(
                id = state.patientId,
                userId = state.patientId,
                bloodGroup = state.bloodType.ifBlank { null },
                allergies = state.allergies.ifBlank { null },
                sex = state.sex.ifBlank { null },
                ageGroup = state.ageGroup.ifBlank { null },
                chronicConditions = state.chronicConditions.ifBlank { null },
            )
            patientProfileDao.insert(profile)
        }
    }
}
