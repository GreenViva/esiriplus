package com.esiri.esiriplus.feature.admin.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// ── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class AllRatingsResponse(
    val ratings: List<AdminRatingRow> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("flagged_count") val flaggedCount: Int = 0,
)

@Serializable
data class AdminRatingRow(
    @SerialName("rating_id") val ratingId: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("doctor_name") val doctorName: String = "Unknown Doctor",
    val rating: Int,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_flagged") val isFlagged: Boolean = false,
)

// ── UI State ─────────────────────────────────────────────────────────────────

data class RatingsFeedbackUiState(
    val ratings: List<AdminRatingRow> = emptyList(),
    val totalCount: Int = 0,
    val flaggedCount: Int = 0,
    val searchQuery: String = "",
    val ratingFilter: Int? = null, // null = All Ratings
    val flaggedOnly: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RatingsFeedbackViewModel @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(RatingsFeedbackUiState())
    val uiState: StateFlow<RatingsFeedbackUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRatings()
    }

    fun loadRatings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val state = _uiState.value
            val body = buildJsonObject {
                if (state.searchQuery.isNotBlank()) {
                    put("search", state.searchQuery.trim())
                }
                if (state.ratingFilter != null) {
                    put("rating_filter", state.ratingFilter)
                }
                if (state.flaggedOnly) {
                    put("flagged_only", true)
                }
                put("limit", 100)
                put("offset", 0)
            }

            when (val result = edgeFunctionClient.invoke("get-all-ratings", body)) {
                is ApiResult.Success -> {
                    try {
                        val response = json.decodeFromString<AllRatingsResponse>(result.data)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                ratings = response.ratings,
                                totalCount = response.totalCount,
                                flaggedCount = response.flaggedCount,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse ratings response", e)
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to parse response")
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false, error = "Network error: ${result.message}") }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isLoading = false, error = "Unauthorized") }
                }
            }
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        // Debounce search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadRatings()
        }
    }

    fun updateRatingFilter(rating: Int?) {
        _uiState.update { it.copy(ratingFilter = rating) }
        loadRatings()
    }

    fun toggleFlaggedOnly() {
        _uiState.update { it.copy(flaggedOnly = !it.flaggedOnly) }
        loadRatings()
    }

    companion object {
        private const val TAG = "RatingsFeedbackVM"
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
