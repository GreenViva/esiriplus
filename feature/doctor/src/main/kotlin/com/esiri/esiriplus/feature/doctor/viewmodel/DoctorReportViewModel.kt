package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.feature.doctor.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

data class Prescription(
    val medication: String,
    val form: String = "",          // "Tablets", "Syrup", or "Injection"
    val quantity: Int = 1,          // tablets count or ml amount (unused for Injection)
    val timesPerDay: Int = 1,
    val days: Int = 1,
    val route: String = "",         // "IM", "IV", or "SC" (only for Injection)
) {
    fun displayText(): String = when (form) {
        "Tablets" -> "Take $quantity tablet${if (quantity > 1) "s" else ""} × $timesPerDay time${if (timesPerDay > 1) "s" else ""} per day × $days day${if (days > 1) "s" else ""}"
        "Syrup" -> "Take ${quantity}ml × $timesPerDay time${if (timesPerDay > 1) "s" else ""} per day × $days day${if (days > 1) "s" else ""}"
        "Injection" -> "$route, $timesPerDay time${if (timesPerDay > 1) "s" else ""} per day × $days day${if (days > 1) "s" else ""}"
        else -> medication
    }

    companion object {
        /** Returns true if the medication name indicates an injectable. */
        fun isInjectable(medicationName: String): Boolean =
            medicationName.contains(" inj", ignoreCase = true)
    }
}

data class DoctorReportUiState(
    val consultationId: String = "",
    val serviceType: String = "",

    // Form fields matching wireframes
    val patientAge: String = "",
    val patientGender: String = "",
    val diagnosedProblem: String = "",
    val category: String = "",
    val otherCategory: String = "",
    val severity: String = "Mild",
    val treatmentPlan: String = "",
    val furtherNotes: String = "",
    val followUpRecommended: Boolean = false,
    val prescriptions: List<Prescription> = emptyList(),
    val medicationSearchQuery: String = "",
    val pendingMedication: String? = null, // medication awaiting dosage config

    // UI state
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class DoctorReportViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val consultationDao: ConsultationDao,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(DoctorReportUiState(consultationId = consultationId))
    val uiState: StateFlow<DoctorReportUiState> = _uiState.asStateFlow()

    init {
        loadConsultationInfo()
    }

    private fun loadConsultationInfo() {
        viewModelScope.launch {
            val consultation = consultationDao.getById(consultationId)
            if (consultation != null) {
                _uiState.update {
                    it.copy(
                        serviceType = consultation.serviceType,
                        isLoading = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updatePatientAge(value: String) {
        _uiState.update { it.copy(patientAge = value, errorMessage = null) }
    }

    fun updatePatientGender(value: String) {
        _uiState.update { it.copy(patientGender = value, errorMessage = null) }
    }

    fun updateDiagnosedProblem(value: String) {
        _uiState.update { it.copy(diagnosedProblem = value, errorMessage = null) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value, errorMessage = null) }
    }

    fun updateOtherCategory(value: String) {
        _uiState.update { it.copy(otherCategory = value, errorMessage = null) }
    }

    fun updateSeverity(value: String) {
        _uiState.update { it.copy(severity = value, errorMessage = null) }
    }

    fun updateTreatmentPlan(value: String) {
        _uiState.update { it.copy(treatmentPlan = value, errorMessage = null) }
    }

    fun updateFurtherNotes(value: String) {
        _uiState.update { it.copy(furtherNotes = value, errorMessage = null) }
    }

    fun toggleFollowUp() {
        _uiState.update { it.copy(followUpRecommended = !it.followUpRecommended, errorMessage = null) }
    }

    fun updateMedicationSearch(query: String) {
        _uiState.update { it.copy(medicationSearchQuery = query) }
    }

    fun selectMedication(medication: String) {
        _uiState.update {
            it.copy(
                pendingMedication = medication,
                medicationSearchQuery = "",
                errorMessage = null,
            )
        }
    }

    fun confirmPrescription(form: String, quantity: Int, timesPerDay: Int, days: Int, route: String = "") {
        val pending = _uiState.value.pendingMedication ?: return
        _uiState.update {
            it.copy(
                prescriptions = it.prescriptions + Prescription(
                    medication = pending,
                    form = form,
                    quantity = quantity,
                    timesPerDay = timesPerDay,
                    days = days,
                    route = route,
                ),
                pendingMedication = null,
            )
        }
    }

    fun cancelPendingMedication() {
        _uiState.update { it.copy(pendingMedication = null) }
    }

    fun removePrescription(medication: String) {
        _uiState.update {
            it.copy(
                prescriptions = it.prescriptions.filter { p -> p.medication != medication },
                errorMessage = null,
            )
        }
    }

    fun submitReport() {
        val state = _uiState.value
        if (state.diagnosedProblem.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_diagnosed_problem_required)) }
            return
        }
        if (state.category.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_category_required)) }
            return
        }
        if (state.category == "Other" && state.otherCategory.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_specify_category)) }
            return
        }
        if (state.treatmentPlan.isBlank()) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.vm_treatment_plan_required)) }
            return
        }
        if (state.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        val effectiveCategory = if (state.category == "Other") state.otherCategory else state.category

        viewModelScope.launch {
            val body = buildJsonObject {
                put("consultation_id", JsonPrimitive(consultationId))
                if (state.patientAge.isNotBlank()) put("patient_age", JsonPrimitive(state.patientAge.trim()))
                if (state.patientGender.isNotBlank()) put("patient_gender", JsonPrimitive(state.patientGender.trim()))
                put("diagnosed_problem", JsonPrimitive(state.diagnosedProblem.trim()))
                put("category", JsonPrimitive(effectiveCategory.trim()))
                put("severity", JsonPrimitive(state.severity))
                put("treatment_plan", JsonPrimitive(state.treatmentPlan.trim()))
                put("further_notes", JsonPrimitive(state.furtherNotes.trim()))
                put("follow_up_recommended", JsonPrimitive(state.followUpRecommended))
                put("prescriptions", JsonArray(state.prescriptions.map { p ->
                    buildJsonObject {
                        put("medication", JsonPrimitive(p.medication))
                        put("form", JsonPrimitive(p.form))
                        put("dosage", JsonPrimitive(p.displayText()))
                        if (p.form == "Injection") {
                            put("route", JsonPrimitive(p.route))
                        }
                    }
                }))
            }

            when (val result = edgeFunctionClient.invoke("generate-consultation-report", body)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, submitSuccess = true)
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Report submission failed: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    Log.w(TAG, "Report submission network error: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = application.getString(R.string.vm_session_expired),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DoctorReportVM"
        val CATEGORIES = listOf(
            "General Medicine",
            "Neurological Conditions",
            "Cardiovascular",
            "Respiratory",
            "Gastrointestinal",
            "Musculoskeletal",
            "Dermatological",
            "Mental Health",
            "Infectious Disease",
            "Other",
        )
        val SEVERITIES = listOf("Mild", "Moderate", "Severe")
        val MEDICATIONS = listOf(
            // ANTIBIOTICS & ANTI-INFECTIVE
            "Amoxycillin trihydrate 500mg BP caps",
            "Ceftriaxone 1gm vial USP 30",
            "Amoxicillin + Ac. Clavulanate 500/125mg tab",
            "Ciprofloxacine 500mg tab USP",
            "Sulphamethoxazole 400mg + Trimethoprim 80mg tabs BP",
            "Gentamycine 80mg/2ml inj BP09",
            "Amoxicilline 250mg caps BP",
            "Neomycine + Bacitracin (0.5% + 500IU/gm) ointment",
            "Streptomycine 1gm inj BP",
            "Gentamycine 20mg/2ml inj BP09",
            "Ceftazidime 1gm inj",
            "Cefotaxime 1gm + WFI inj USP",
            "Amoxicilline + Ac. Clavulanate 1gm/200mg inj",
            "Kanamycine 1gm inj BP",
            "Erythromycine 500mg tabs BP",
            "Clanoxy 1.2gm inj (Amox + Pot. Clav)",
            "Ceftriaxone 500mg inj",
            "Amoxicillin and clavulanate potassium tabs USP",
            "Cefixime tabs USP 200mg",
            "Keftaz 1000 (Ceftazidime for inj USP 1gm)",
            "Clanoxy 625mg (Amox + Pot Clav) tabs USP",
            "Chloramphenicol inj BP",
            "Gentamicine inj 80mg BP",
            "Amoxicillin 500mg and clavulanate potassium 62.5mg tabs USP",
            "Chloramphenicol inj 1gm BP",
            "Imipenem + Cilastatin 500mg",
            "Teicoplanin 400mg",
            "Vancomycin 500mg/1gm",
            "Meropenem 500mg",
            "Cefepime 1gm",
            // SEDATIVES AND HYPNOTICS
            "Diazepam 5mg/ml, 2ml inj BP",
            // ANTI FUNGAL
            "Miconazole 2% cream",
            "Nystatine 100000 IU ointment BP",
            "Amphotericine B for inj USP (50mg/vial)",
            // ANTI ANTHELMATICS
            "Metronidazole 250mg tabs BP",
            "Helmanil tabs (Albendazole)",
            // ANTI VIRAL
            "Acyclovir 3% 5gm eye ointment BP",
            "Acyclovir 5% 10gm cream",
            // NSAIDs
            "Paracetamol 500mg tabs BP 09",
            "Diclofenac 75mg/3ml inj BP",
            "Ibuprofen 200mg tabs BP",
            "Ibuprofen 400mg tab",
            // ANTI MALARIALS
            "Quinine base 600mg/2ml inj",
            "Quinine inj 100mg/ml, 2ml inj",
            "Quinine 100mg/ml inj amp 2.4ml",
            "Quinine 300mg (2ml amp) BP",
            "Artemether 80mg",
            // ANTI CHOLINERGICS
            "Atropine sulphate 1mg inj",
            // ANTI SPASMODICS
            "Hyoscine butyl bromide injection BP",
            "N-butyl hyoscine bromide inj 20mg",
            // STEROIDAL ANTI-INFLAMMATORY
            "Hydrocortisone 100mg inj BP",
            "Dexamethasone 4mg inj BP",
            "Betamethasone 0.1%, 5gm cream BP",
            "Hydrocortisone sodium succinate 100mg",
            // ELECTROLYTE REPLENISHERS
            "Calcium gluconate inj BP",
            "Sodium chloride inj",
            "Potassium chloride inj",
            // VITAMINS
            "Cyanocobalamine (Hydroxocobalamine) 1mg inj",
            "Ascorbic acid inj 500mg BP",
            // GYNAECOLOGY
            "Ergometrine inj BP",
            "Oxytocin inj 10IU/ml, 1ml BP",
            "Oxytocin inj 5IU/ml, 1ml amp BP",
            "Methylergometrine 0.2mg/ml, 1ml inj USP",
            // DIURETICS
            "Frusemide 10mg/ml, 2ml inj BP",
            // ANAESTHETICS
            "Propofol 1gm inj BP",
            "Propofol inj (1% w/v) BP",
            "Ketamine 50mg/ml 10ml inj BP",
            "Haloperidol 5mg/ml, 1ml inj",
            "Bupivacaine 0.25% (950mg/20ml) inj BP",
            "Thiopental 0.5gm inj BP",
            "Lignocaine injection BP",
            "Lidocaine injection",
            "Bupivacaine 0.5% inj BP",
            "Vecuronium bromide 4mg & 10mg",
            "Pancuronium bromide BP 4mg",
            "Ropivacaine hydrochloride 40/150/200",
            "Atracurium besylate USP 10mg/ml",
            "Suxamethonium chloride 100mg/2ml inj",
            "Neostigmine inj 0.5mg/ml BP",
            "Lignocaine hydrochloride & dextrose inj USP",
            "Lidocaine 2% + adrenaline inj",
            "Bupivacaine rachi anaesthesia",
            // COAGULANTS AND ANTI-COAGULANTS
            "Vitamin K1 (Phytonadione) 10mg/ml inj BP",
            "Heparinate sodium inj 5000IU/ml",
            "Ethamsylate 250mg/2ml inj",
            // ANTI EPILEPTIC
            "Phenobarbital inj 100mg/ml, 2ml amp",
            "Phenobarbital 200mg/ml",
        )
    }
}
