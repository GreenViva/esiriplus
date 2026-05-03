package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.ListMissedRpcBody
import com.esiri.esiriplus.core.network.api.model.MissedConsultationApiModel
import com.esiri.esiriplus.core.network.model.toApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * Read-only view of paid-but-unresolved consultations for the current patient.
 * Backed by the `list_missed_for_patient` PostgREST RPC.
 *
 * Reconnect happens elsewhere — when the patient picks a doctor on
 * FindDoctorScreen the request-create call carries the source kind/id and
 * the server marks it consumed. This screen just lists; tapping a row
 * navigates out.
 */
data class MissedConsultationItem(
    val sourceKind: String,
    val sourceId: String,
    val serviceType: String,
    val serviceTier: String,
    val consultationFee: Int,
    val doctorId: String?,
    val doctorName: String?,
    val createdAtIso: String,
)

data class MissedConsultationsUiState(
    val isLoading: Boolean = true,
    val items: List<MissedConsultationItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MissedConsultationsViewModel @Inject constructor(
    private val supabaseApi: SupabaseApi,
    private val tokenManager: TokenManager,
    private val doctorProfileDao: DoctorProfileDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissedConsultationsUiState())
    val uiState: StateFlow<MissedConsultationsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val sessionId = sessionIdFromToken()
            if (sessionId == null) {
                _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            try {
                val resp = supabaseApi.listMissedConsultations(ListMissedRpcBody(sessionId))
                when (val result = resp.toApiResult()) {
                    is com.esiri.esiriplus.core.network.model.ApiResult.Success -> {
                        val rows = result.data
                        val items = rows.map { it.toItem() }
                        _uiState.update { it.copy(isLoading = false, items = items, error = null) }
                    }
                    is com.esiri.esiriplus.core.network.model.ApiResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = result.message ?: "Failed to load") }
                    }
                    is com.esiri.esiriplus.core.network.model.ApiResult.NetworkError -> {
                        _uiState.update { it.copy(isLoading = false, error = "Network error") }
                    }
                    is com.esiri.esiriplus.core.network.model.ApiResult.Unauthorized -> {
                        _uiState.update { it.copy(isLoading = false, error = "Session expired") }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load missed consultations", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun MissedConsultationApiModel.toItem(): MissedConsultationItem {
        val doctorName = doctorId?.let {
            try { doctorProfileDao.getById(it)?.fullName } catch (_: Exception) { null }
        }
        return MissedConsultationItem(
            sourceKind = sourceKind,
            sourceId = sourceId,
            serviceType = serviceType,
            serviceTier = serviceTier,
            consultationFee = consultationFee,
            doctorId = doctorId,
            doctorName = doctorName,
            createdAtIso = createdAt,
        )
    }

    private fun sessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING),
            )
            JSONObject(payload).optString("session_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }

    companion object {
        private const val TAG = "MissedConsultVM"
    }
}
