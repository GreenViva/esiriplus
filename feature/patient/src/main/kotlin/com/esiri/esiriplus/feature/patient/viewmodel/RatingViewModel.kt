package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.model.DoctorRating
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class RatingUiState(
    val stars: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null,
    val commentError: String? = null,
    val consultationId: String = "",
    val doctorId: String = "",
    val patientSessionId: String = "",
)

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val ratingRepository: DoctorRatingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RatingUiState())
    val uiState: StateFlow<RatingUiState> = _uiState.asStateFlow()

    fun initialize(consultationId: String, doctorId: String, patientSessionId: String) {
        _uiState.update {
            it.copy(
                consultationId = consultationId,
                doctorId = doctorId,
                patientSessionId = patientSessionId,
            )
        }
    }

    fun setStars(n: Int) {
        _uiState.update { it.copy(stars = n, commentError = null, error = null) }
    }

    fun setComment(text: String) {
        _uiState.update { it.copy(comment = text, commentError = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting || state.submitSuccess) return

        // Validate
        if (state.stars < 1) {
            _uiState.update { it.copy(error = "Please select a rating") }
            return
        }
        if (state.stars <= 3 && state.comment.isBlank()) {
            _uiState.update { it.copy(commentError = "Please tell us what could be improved") }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, error = null, commentError = null) }

        viewModelScope.launch {
            try {
                val rating = DoctorRating(
                    ratingId = UUID.randomUUID().toString(),
                    doctorId = state.doctorId,
                    consultationId = state.consultationId,
                    patientSessionId = state.patientSessionId,
                    rating = state.stars,
                    comment = state.comment.trim().ifBlank { null },
                    createdAt = System.currentTimeMillis(),
                    synced = false,
                )

                // Try local save (may fail if foreign key references are
                // missing on this device — e.g. DoctorProfile not cached)
                var savedLocally = false
                try {
                    ratingRepository.submitRating(rating)
                    savedLocally = true
                } catch (e: Exception) {
                    Log.w(TAG, "Local rating save failed (FK constraint?), proceeding with server sync", e)
                }

                // Always sync to server — this is the source of truth
                val synced = ratingRepository.submitRatingToServer(rating)
                if (synced && savedLocally) {
                    ratingRepository.markSynced(rating.ratingId)
                }

                if (synced || savedLocally) {
                    _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                    Log.d(TAG, "Rating submitted: stars=${state.stars}, synced=$synced, savedLocally=$savedLocally")
                } else {
                    _uiState.update {
                        it.copy(isSubmitting = false, error = "Failed to submit rating. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit rating", e)
                _uiState.update {
                    it.copy(isSubmitting = false, error = "Failed to submit rating. Please try again.")
                }
            }
        }
    }

    companion object {
        private const val TAG = "RatingViewModel"
    }
}
