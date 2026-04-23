package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.network.api.model.AgentEarningApiModel
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.AgentEarningsService
import com.esiri.esiriplus.core.network.service.EarningsPage
import com.esiri.esiriplus.core.network.service.EarningsSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentEarningsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val summary: EarningsSummary = EarningsSummary(0, 0L, 0L, 0L),
    val rows: List<AgentEarningApiModel> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingPage: Boolean = false,
    val totalCount: Int = 0,
)

@HiltViewModel
class AgentEarningsViewModel @Inject constructor(
    private val service: AgentEarningsService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentEarningsUiState())
    val uiState: StateFlow<AgentEarningsUiState> = _uiState.asStateFlow()

    init {
        loadFirstPage()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadFirstPage()
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoadingPage || !state.hasMore) return
        _uiState.update { it.copy(isLoadingPage = true) }
        viewModelScope.launch {
            when (val result = service.getEarningsPage(page = state.page + 1)) {
                is ApiResult.Success -> _uiState.update {
                    val appended = it.rows + result.data.rows
                    it.copy(
                        rows = appended,
                        page = result.data.page,
                        hasMore = result.data.hasMore,
                        totalCount = result.data.total,
                        isLoadingPage = false,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoadingPage = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoadingPage = false, errorMessage = "Network error")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isLoadingPage = false, errorMessage = "Session expired. Please log in again.")
                }
            }
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            val summaryDeferred = service.getSummary()
            val pageResult = service.getEarningsPage(page = 0)

            when (summaryDeferred) {
                is ApiResult.Success -> _uiState.update { it.copy(summary = summaryDeferred.data) }
                else -> Log.d(TAG, "Summary load failed (non-fatal)")
            }

            when (pageResult) {
                is ApiResult.Success -> _uiState.update {
                    val p: EarningsPage = pageResult.data
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        rows = p.rows,
                        page = p.page,
                        hasMore = p.hasMore,
                        totalCount = p.total,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = pageResult.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = "Network error")
                }
                is ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = "Session expired. Please log in again.")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        private const val TAG = "AgentEarningsVM"
    }
}
