package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.database.dao.ServiceTierDao
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import androidx.navigation.toRoute
import com.esiri.esiriplus.core.domain.model.ConsultationTier
import com.esiri.esiriplus.core.domain.model.LocationOffer
import com.esiri.esiriplus.core.domain.pricing.PricingEngine
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.LocationOfferRepository
import com.esiri.esiriplus.core.domain.repository.PatientSessionRepository
import com.esiri.esiriplus.feature.patient.navigation.ServicesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServicesUiState(
    val services: List<ServiceTierEntity> = emptyList(),
    val selectedServiceId: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val patientId: String = "",
    val tier: ConsultationTier = ConsultationTier.ECONOMY,
    /** Patient's GPS-resolved location (sourced from session, not nav args). */
    val serviceRegion: String? = null,
    val serviceDistrict: String? = null,
    val serviceWard: String? = null,
    val serviceStreet: String? = null,
    /** Offers currently available to this patient for their location + tier. */
    val applicableOffers: List<LocationOffer> = emptyList(),
) {
    /** Price after tier multiplier only — used as the "original" strikethrough price. */
    fun tierAdjustedPrice(basePrice: Int): Int = PricingEngine.calculatePrice(basePrice, tier)

    /**
     * The final price a patient pays. Applies tier multiplier then the best
     * matching offer (if any). Existing call sites that pass no [serviceCategory]
     * continue to get tier-only pricing — that keeps unrelated flows unaffected.
     */
    fun effectivePrice(basePrice: Int, serviceCategory: String? = null): Int {
        val adjusted = tierAdjustedPrice(basePrice)
        if (serviceCategory == null) return adjusted
        val offer = offerFor(serviceCategory) ?: return adjusted
        return offer.applyTo(adjusted)
    }

    /**
     * Returns the best offer that applies to [serviceCategory], or null if none.
     * Prefers offers with more specific scope (ward > district, fewer service types = broader).
     */
    fun offerFor(serviceCategory: String): LocationOffer? {
        val eligible = applicableOffers.filter { it.appliesTo(serviceCategory) }
        if (eligible.isEmpty()) return null
        return eligible.minByOrNull { offer ->
            val wardScore = if (offer.ward != null) 0 else 1
            val typeScore = if (offer.serviceTypes.isNotEmpty()) 0 else 1
            wardScore * 10 + typeScore
        }
    }

    /** True when at least one offer is currently available — drives the banner. */
    val hasOffers: Boolean get() = applicableOffers.isNotEmpty()
}

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val serviceTierDao: ServiceTierDao,
    private val authRepository: AuthRepository,
    private val locationOfferRepository: LocationOfferRepository,
    private val patientSessionRepository: PatientSessionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: ServicesRoute =
        runCatching { savedStateHandle.toRoute<ServicesRoute>() }.getOrNull()
            ?: ServicesRoute()
    private val tierString: String = route.tier.ifBlank { "ECONOMY" }

    private val _uiState = MutableStateFlow(
        ServicesUiState(tier = ConsultationTier.fromString(tierString)),
    )
    val uiState: StateFlow<ServicesUiState> = _uiState.asStateFlow()

    init {
        loadServices()
        loadPatientId()
        loadLocationAndOffers()
    }

    /**
     * Re-fetch offers using the latest session state. Call from the screen on
     * resume so a redeemed offer disappears the moment the patient returns
     * to this screen after the consultation flow.
     */
    fun refreshOffers() {
        viewModelScope.launch {
            val session = patientSessionRepository.getSession().first() ?: return@launch
            val region   = session.region?.takeIf { it.isNotBlank() }
            val district = session.serviceDistrict?.takeIf { it.isNotBlank() }
            val ward     = session.serviceWard?.takeIf { it.isNotBlank() }
            val street   = session.serviceStreet?.takeIf { it.isNotBlank() }
            if (region == null && district == null && ward == null && street == null) return@launch

            when (val result = locationOfferRepository.fetchApplicableOffers(
                region = region,
                district = district,
                ward = ward,
                street = street,
                tier = tierString,
            )) {
                is Result.Success -> _uiState.update { it.copy(applicableOffers = result.data) }
                is Result.Error -> Log.w(TAG, "Refresh offers failed: ${result.message}")
                is Result.Loading -> Unit
            }
        }
    }

    private fun loadServices() {
        // Show hardcoded services immediately so the UI is never empty,
        // then overlay with Room data if available.
        _uiState.update { it.copy(services = FALLBACK_SERVICES, isLoading = false) }
        viewModelScope.launch {
            serviceTierDao.getActiveServiceTiers().collect { tiers ->
                if (tiers.isNotEmpty()) {
                    _uiState.update { it.copy(services = tiers) }
                }
            }
        }
    }

    /**
     * Reads the GPS-resolved location off the patient session (set at app
     * start by LocationResolver) and fetches offers matching ANY level the
     * resolver pinned down. Re-runs whenever the session emits — that lets
     * the offer banner appear automatically once a backfill completes for
     * patients who hadn't been resolved yet.
     */
    private fun loadLocationAndOffers() {
        viewModelScope.launch {
            patientSessionRepository.getSession().collect { session ->
                val region   = session?.region?.takeIf { it.isNotBlank() }
                val district = session?.serviceDistrict?.takeIf { it.isNotBlank() }
                val ward     = session?.serviceWard?.takeIf { it.isNotBlank() }
                val street   = session?.serviceStreet?.takeIf { it.isNotBlank() }

                _uiState.update {
                    it.copy(
                        serviceRegion = region,
                        serviceDistrict = district,
                        serviceWard = ward,
                        serviceStreet = street,
                    )
                }

                if (region == null && district == null && ward == null && street == null) {
                    _uiState.update { it.copy(applicableOffers = emptyList()) }
                    return@collect
                }

                when (val result = locationOfferRepository.fetchApplicableOffers(
                    region = region,
                    district = district,
                    ward = ward,
                    street = street,
                    tier = tierString,
                )) {
                    is Result.Success -> {
                        _uiState.update { it.copy(applicableOffers = result.data) }
                        Log.d(TAG, "Loaded ${result.data.size} offer(s) for $district/$region")
                    }
                    is Result.Error -> {
                        Log.w(TAG, "Failed to load offers: ${result.message}")
                    }
                    is Result.Loading -> Unit
                }
            }
        }
    }

    companion object {
        private const val TAG = "ServicesVM"

        private val FALLBACK_SERVICES = listOf(
            ServiceTierEntity(id = "tier_nurse", category = "NURSE", displayName = "Nurse", description = "Normal consultations for everyday health concerns", priceAmount = 5000, currency = "TZS", isActive = true, sortOrder = 1, durationMinutes = 15, features = "Basic health advice,Symptom assessment,Health education"),
            ServiceTierEntity(id = "tier_clinical_officer", category = "CLINICAL_OFFICER", displayName = "Clinical Officer", description = "Daily medical consultations for common ailments", priceAmount = 7000, currency = "TZS", isActive = true, sortOrder = 2, durationMinutes = 15, features = "Medical diagnosis,Treatment recommendations,Prescription guidance"),
            ServiceTierEntity(id = "tier_pharmacist", category = "PHARMACIST", displayName = "Pharmacist", description = "Quick medication advice and drug interaction checks", priceAmount = 3000, currency = "TZS", isActive = true, sortOrder = 3, durationMinutes = 5, features = "Medication advice,Drug interaction checks,Dosage guidance"),
            ServiceTierEntity(id = "tier_gp", category = "GP", displayName = "General Practitioner", description = "Comprehensive care with specialist referrals when needed", priceAmount = 10000, currency = "TZS", isActive = true, sortOrder = 4, durationMinutes = 15, features = "Full medical assessment,Treatment planning,Specialist referrals"),
            ServiceTierEntity(id = "tier_specialist", category = "SPECIALIST", displayName = "Specialist", description = "Expert consultation in specialized medical fields", priceAmount = 30000, currency = "TZS", isActive = true, sortOrder = 5, durationMinutes = 15, features = "Specialized expertise,Advanced diagnostics,Detailed treatment plans"),
            ServiceTierEntity(id = "tier_psychologist", category = "PSYCHOLOGIST", displayName = "Psychologist", description = "Professional mental health support and counseling", priceAmount = 50000, currency = "TZS", isActive = true, sortOrder = 6, durationMinutes = 20, features = "Mental health support,Professional counseling,Therapy session"),
            ServiceTierEntity(id = "tier_herbalist", category = "HERBALIST", displayName = "Herbalist", description = "Traditional and herbal medicine consultation", priceAmount = 5000, currency = "TZS", isActive = true, sortOrder = 7, durationMinutes = 15, features = "Herbal medicine consultation,Traditional remedy guidance,Natural supplement advice"),
            ServiceTierEntity(id = "tier_drug_interaction", category = "DRUG_INTERACTION", displayName = "Drug Interaction", description = "Check drug interactions and get safety guidance", priceAmount = 5000, currency = "TZS", isActive = true, sortOrder = 8, durationMinutes = 5, features = "Drug interaction checks,Safety alerts,Dosage guidance"),
        )
    }

    private fun loadPatientId() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first()
            if (session != null) {
                _uiState.update { it.copy(patientId = session.user.id) }
            }
        }
    }

    fun selectService(serviceId: String) {
        _uiState.update { it.copy(selectedServiceId = serviceId) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            serviceTierDao.getActiveServiceTiers().first().let { tiers ->
                _uiState.update { it.copy(services = tiers, isRefreshing = false) }
            }
        }
        refreshOffers()
    }
}
