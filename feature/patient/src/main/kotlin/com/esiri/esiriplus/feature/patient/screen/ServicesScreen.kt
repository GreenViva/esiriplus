package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.style.TextDecoration
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import com.esiri.esiriplus.core.domain.model.LocationOffer
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.ServicesViewModel
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val PopularOrange = Color(0xFFEA580C)
private val OfferGreen = Color(0xFF059669)
private val OfferGreenDark = Color(0xFF047857)

// Icon background colors per category
private val iconColors = mapOf(
    "NURSE" to Color(0xFF2A9D8F),
    "CLINICAL_OFFICER" to Color(0xFF3B82F6),
    "PHARMACIST" to Color(0xFFF59E0B),
    "GP" to Color(0xFF2A9D8F),
    "SPECIALIST" to Color(0xFFEF4444),
    "PSYCHOLOGIST" to Color(0xFF8B5CF6),
    "HERBALIST" to Color(0xFF16A34A),
    "DRUG_INTERACTION" to Color(0xFF2A9D8F),
)

private val iconResources = mapOf(
    "NURSE" to R.drawable.ic_nurse,
    "CLINICAL_OFFICER" to R.drawable.ic_clinical_officer,
    "PHARMACIST" to R.drawable.ic_pharmacist,
    "GP" to R.drawable.ic_gp,
    "SPECIALIST" to R.drawable.ic_specialist,
    "PSYCHOLOGIST" to R.drawable.ic_psychologist,
    "HERBALIST" to R.drawable.ic_herbalist,
    "DRUG_INTERACTION" to R.drawable.ic_sober_house,
)

private val comingSoonCategories = emptySet<String>()

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onServiceSelected: (serviceCategory: String, priceAmount: Int, durationMinutes: Int, serviceTier: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()
    var showPaymentDialog by remember { mutableStateOf(false) }

    // Find the selected service for the dialog subtitle
    val selectedService = uiState.services.find { it.id == uiState.selectedServiceId }
    // Effective price AFTER any matching location offer (strikethrough shown separately)
    val selectedEffectivePrice = selectedService?.let {
        uiState.effectivePrice(it.priceAmount, it.category)
    } ?: 0
    val selectedOriginalPrice = selectedService?.let { uiState.tierAdjustedPrice(it.priceAmount) } ?: 0
    val selectedOffer = selectedService?.let { uiState.offerFor(it.category) }

    // Payment verification simulation states
    var showVerifyingDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val isRoyal = uiState.tier == com.esiri.esiriplus.core.domain.model.ConsultationTier.ROYAL

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.background, MintLight))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.services_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.services_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.services_subtitle),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Royal Service banner ─────────────────────────────────────────
            if (isRoyal) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF4C1D95), Color(0xFF7C3AED)),
                            ),
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "♛", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.services_royal_banner_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.services_royal_banner_subtitle),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // ── Location offer banner ────────────────────────────────────────
            if (uiState.hasOffers) {
                LocationOfferBanner(
                    offers = uiState.applicableOffers,
                    district = uiState.serviceDistrict,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandTeal)
                    }
                } else {
                    // Service list
                    val lazyListState = rememberLazyListState()
                    ScrollIndicatorBox(lazyListState = lazyListState) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(uiState.services, key = { it.id }) { service ->
                            val offer = uiState.offerFor(service.category)
                            ServiceCard(
                                service = service,
                                displayPrice = uiState.effectivePrice(service.priceAmount, service.category),
                                originalPrice = uiState.tierAdjustedPrice(service.priceAmount),
                                offer = offer,
                                isSelected = uiState.selectedServiceId == service.id,
                                onSelect = { viewModel.selectService(service.id) },
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    } // ScrollIndicatorBox
                }
            } // PullToRefreshBox

            // Bottom section
            if (!uiState.isLoading) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val isFreeOffer = selectedService != null && selectedEffectivePrice == 0 && selectedOffer != null
                    Button(
                        onClick = {
                            if (isFreeOffer && selectedService != null) {
                                // Skip the payment flow entirely — company covers the free offer
                                onServiceSelected(
                                    selectedService.category,
                                    0,
                                    selectedService.durationMinutes,
                                    uiState.tier.name,
                                )
                            } else {
                                showPaymentDialog = true
                            }
                        },
                        enabled = uiState.selectedServiceId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFreeOffer) OfferGreenDark else BrandTeal,
                            disabledContainerColor = BrandTeal.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = if (isFreeOffer) "Start Consultation — FREE"
                                   else stringResource(R.string.services_pay_to_continue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isFreeOffer) "No payment required — this consultation is on us 🎉"
                               else stringResource(R.string.services_payment_required),
                        fontSize = 13.sp,
                        color = if (isFreeOffer) OfferGreenDark else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // Payment flow: step 1 → step 2 → step 3
    var showProviderDialog by remember { mutableStateOf(false) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<MobileProvider?>(null) }

    // Step 1: Choose Payment Method
    if (showPaymentDialog && selectedService != null) {
        PaymentMethodDialog(
            serviceName = selectedService.displayName,
            priceAmount = selectedEffectivePrice,
            onMobileMoneySelected = {
                showPaymentDialog = false
                showProviderDialog = true
            },
            onDismiss = { showPaymentDialog = false },
        )
    }

    // Step 2: Select Your Provider
    if (showProviderDialog && selectedService != null) {
        ProviderSelectionDialog(
            onProviderSelected = { provider ->
                selectedProvider = provider
                showProviderDialog = false
                showInstructionsDialog = true
            },
            onBack = {
                showProviderDialog = false
                showPaymentDialog = true
            },
            onDismiss = { showProviderDialog = false },
        )
    }

    // Step 3: Payment Instructions (uses tier-adjusted price)
    if (showInstructionsDialog && selectedService != null && selectedProvider != null) {
        PaymentInstructionsDialog(
            provider = selectedProvider!!,
            priceAmount = selectedEffectivePrice,
            patientId = uiState.patientId,
            onChangeProvider = {
                showInstructionsDialog = false
                showProviderDialog = true
            },
            onHavePaid = {
                showInstructionsDialog = false
                showVerifyingDialog = true
            },
            onDismiss = { showInstructionsDialog = false },
        )
    }

    // Step 4: Verifying Payment...
    if (showVerifyingDialog) {
        VerifyingPaymentDialog(
            onVerified = {
                showVerifyingDialog = false
                showSuccessDialog = true
            },
        )
    }

    // Step 5: Payment Successful — pass tier-adjusted price and tier name
    if (showSuccessDialog && selectedService != null) {
        PaymentSuccessDialog(
            onContinue = {
                showSuccessDialog = false
                onServiceSelected(
                    selectedService.category,
                    selectedEffectivePrice,
                    selectedService.durationMinutes,
                    uiState.tier.name,
                )
            },
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceTierEntity,
    displayPrice: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    originalPrice: Int = displayPrice,
    offer: LocationOffer? = null,
) {
    val isPopular = service.category == "GP"
    val isComingSoon = service.category in comingSoonCategories
    val borderColor = when {
        isSelected -> BrandTeal
        offer != null -> OfferGreen
        else -> MaterialTheme.colorScheme.outline
    }
    val contentAlpha = if (isComingSoon) 0.5f else 1f
    val hasDiscount = offer != null && displayPrice != originalPrice

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surface)
            .then(if (!isComingSoon) Modifier.clickable { onSelect() } else Modifier),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Popular / Coming Soon / Offer badges
            if (isPopular || isComingSoon || offer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (offer != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OfferGreen,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = "🎉", fontSize = 11.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = offer.shortLabel(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    if (isPopular) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PopularOrange,
                        ) {
                            Text(
                                text = stringResource(R.string.services_popular),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                    if (isComingSoon) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF6B7280),
                        ) {
                            Text(
                                text = stringResource(R.string.services_coming_soon),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon
                val iconBgColor = iconColors[service.category] ?: BrandTeal
                val iconRes = iconResources[service.category] ?: R.drawable.ic_heart
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconBgColor.copy(alpha = contentAlpha),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Title + description
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = service.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        lineHeight = 16.sp,
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Price + duration
                Column(horizontalAlignment = Alignment.End) {
                    if (hasDiscount) {
                        Text(
                            text = "TSh ${numberFormat.format(originalPrice)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                    Text(
                        text = if (displayPrice == 0) "FREE" else "TSh ${numberFormat.format(displayPrice)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (hasDiscount) OfferGreenDark else BrandTeal.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = stringResource(R.string.services_min_format, service.durationMinutes),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    )
                }

                if (!isComingSoon) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        modifier = Modifier.size(36.dp),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = BrandTeal,
                            unselectedColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }

            // Feature tags
            if (service.features.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    service.features.split(",").forEach { feature ->
                        FeatureTag(feature.trim(), alpha = contentAlpha)
                    }
                }
            }
        }
    }
}

/**
 * Green banner shown at the top of the services list when the patient has one
 * or more location-based offers available. Summarises the best offer and, if
 * multiple offers apply, shows a "+N more" count.
 */
@Composable
private fun LocationOfferBanner(
    offers: List<LocationOffer>,
    district: String?,
) {
    if (offers.isEmpty()) return
    val headline = offers.first()
    val extra = offers.size - 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(listOf(OfferGreenDark, OfferGreen)),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🎉", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                val subtitle = buildString {
                    append(headline.shortLabel())
                    if (!district.isNullOrBlank()) append(" · for ").append(district)
                    if (extra > 0) append(" · +$extra more offer${if (extra == 1) "" else "s"}")
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun FeatureTag(text: String, alpha: Float = 1f) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = BrandTeal.copy(alpha = alpha),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}

// ── Payment Method Dialog ────────────────────────────────────────────

private val MobileMoneyGreen = Color(0xFF16A34A)
private val ComingSoonGrey = Color(0xFF6B7280)

@Composable
private fun PaymentMethodDialog(
    serviceName: String,
    priceAmount: Int,
    onMobileMoneySelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.services_choose_payment_method),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.services_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Subtitle: service name + price
                Text(
                    text = "$serviceName \u2013 TSh ${numberFormat.format(priceAmount)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // Mobile Money option (active)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_mobile_money,
                    iconBgColor = MobileMoneyGreen,
                    title = stringResource(R.string.services_mobile_money),
                    subtitle = stringResource(R.string.services_mobile_money_subtitle),
                    enabled = true,
                    onClick = onMobileMoneySelected,
                )

                Spacer(Modifier.height(12.dp))

                // Card Payment (Coming Soon)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_credit_card,
                    iconBgColor = ComingSoonGrey,
                    title = stringResource(R.string.services_card_payment),
                    subtitle = stringResource(R.string.services_card_payment_subtitle),
                    enabled = false,
                    comingSoon = true,
                    onClick = {},
                )

                Spacer(Modifier.height(12.dp))

                // Bank Transfer (Coming Soon)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_bank,
                    iconBgColor = ComingSoonGrey,
                    title = stringResource(R.string.services_bank_transfer),
                    subtitle = stringResource(R.string.services_bank_transfer_subtitle),
                    enabled = false,
                    comingSoon = true,
                    onClick = {},
                )

                Spacer(Modifier.height(16.dp))

                // Secure payment note
                Text(
                    text = stringResource(R.string.services_secure_payments),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodOption(
    iconRes: Int,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    comingSoon: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    val borderColor = if (enabled) BrandTeal else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (enabled) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .background(MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBgColor.copy(alpha = alpha),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }

            // Coming Soon badge or arrow
            if (comingSoon) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.services_coming_soon),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ComingSoonGrey,
                    )
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_consultation),
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ── Provider Selection Dialog (Step 2 after Mobile Money) ────────────

private data class MobileProvider(
    val name: String,
    val letter: String,
    val color: Color,
    val ussdCode: String,
    val steps: List<String>,
)

private val mobileProviders = listOf(
    MobileProvider(
        name = "M-Pesa",
        letter = "M",
        color = Color(0xFF4CAF50),
        ussdCode = "*150*00#",
        steps = listOf(
            "Dial *150*00#",
            "Choose \"Lipa kwa M-Pesa\"",
            "Choose \"Malipo ya Kampuni\"",
            "Choose \"Esiri Plus\"",
            "Enter your Patient ID as reference number",
            "Enter the exact amount shown above",
            "Enter your M-Pesa PIN",
            "Confirm (Thibitisha)",
        ),
    ),
    MobileProvider(
        name = "Airtel Money",
        letter = "A",
        color = Color(0xFFE53935),
        ussdCode = "*150*60#",
        steps = listOf(
            "Dial *150*60#",
            "Choose \"Make Payments\"",
            "Choose \"Pay Bill\"",
            "Enter business name \"Esiri Plus\"",
            "Enter your Patient ID as reference number",
            "Enter the exact amount shown above",
            "Enter your Airtel Money PIN",
            "Confirm payment",
        ),
    ),
    MobileProvider(
        name = "HaloPesa",
        letter = "H",
        color = Color(0xFF2E7D32),
        ussdCode = "*150*88#",
        steps = listOf(
            "Dial *150*88#",
            "Choose \"Lipa\"",
            "Choose \"Lipa kwa Kampuni\"",
            "Enter business name \"Esiri Plus\"",
            "Enter your Patient ID as reference number",
            "Enter the exact amount shown above",
            "Enter your HaloPesa PIN",
            "Confirm payment",
        ),
    ),
    MobileProvider(
        name = "Yas",
        letter = "Y",
        color = Color(0xFF43A047),
        ussdCode = "*150*01#",
        steps = listOf(
            "Dial *150*01#",
            "Choose \"Payments\"",
            "Choose \"Pay Business\"",
            "Enter business name \"Esiri Plus\"",
            "Enter your Patient ID as reference number",
            "Enter the exact amount shown above",
            "Enter your Yas PIN",
            "Confirm payment",
        ),
    ),
)

@Composable
private fun ProviderSelectionDialog(
    onProviderSelected: (provider: MobileProvider) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Title row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.services_select_provider),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.services_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Back button
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.clickable(onClick = onBack),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.services_back),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFDC2626),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Provider grid (2 columns)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (rowIndex in mobileProviders.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProviderCard(
                                provider = mobileProviders[rowIndex],
                                onClick = { onProviderSelected(mobileProviders[rowIndex]) },
                                modifier = Modifier.weight(1f),
                            )
                            if (rowIndex + 1 < mobileProviders.size) {
                                ProviderCard(
                                    provider = mobileProviders[rowIndex + 1],
                                    onClick = { onProviderSelected(mobileProviders[rowIndex + 1]) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: MobileProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Colored circle with letter
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(provider.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.letter,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = provider.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Payment Instructions Dialog (Step 3) ─────────────────────────────

private val StepNumberColors = listOf(
    Color(0xFF2A9D8F), // teal
    Color(0xFF3B82F6), // blue
    Color(0xFFF59E0B), // amber
    Color(0xFF8B5CF6), // purple
    Color(0xFFEF4444), // red
    Color(0xFF16A34A), // green
    Color(0xFFEC4899), // pink
    Color(0xFF2A9D8F), // teal again
)

@Composable
private fun PaymentInstructionsDialog(
    provider: MobileProvider,
    priceAmount: Int,
    patientId: String,
    onChangeProvider: () -> Unit,
    onHavePaid: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Shorten the patient ID for display (first 8 chars + last 4)
    val referenceNumber = if (patientId.length > 12) {
        "ESP-${patientId.take(8).uppercase()}-${patientId.takeLast(4).uppercase()}"
    } else {
        "ESP-${patientId.uppercase()}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Title row with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = provider.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.services_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Back button
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.clickable(onClick = onChangeProvider),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.services_back),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFDC2626),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Provider icon + USSD code
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(provider.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = provider.letter,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = provider.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = provider.ussdCode,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Reference Number
                Text(
                    text = stringResource(R.string.services_reference_number),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = referenceNumber,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF166534),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Amount to Pay
                Text(
                    text = stringResource(R.string.services_amount_to_pay),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "TSh ${numberFormat.format(priceAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = BrandTeal,
                )

                Spacer(Modifier.height(20.dp))

                // Steps header
                Text(
                    text = stringResource(R.string.services_follow_steps),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(12.dp))

                // Numbered steps
                provider.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Step number circle
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(StepNumberColors[index % StepNumberColors.size]),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Important reminder
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "\u26A0\uFE0F",
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.services_exact_amount_warning, numberFormat.format(priceAmount)),
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            lineHeight = 16.sp,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Change Provider button
                    Button(
                        onClick = onChangeProvider,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = stringResource(R.string.services_change_provider),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                        )
                    }

                    // I Have Paid button
                    Button(
                        onClick = onHavePaid,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.services_i_have_paid),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ── Verifying Payment Dialog ─────────────────────────────────────────

@Composable
private fun VerifyingPaymentDialog(
    onVerified: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(3000L)
        onVerified()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.services_verifying_payment),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(24.dp))

                CircularProgressIndicator(
                    color = BrandTeal,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.services_verifying_payment),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.services_please_wait_confirm),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Payment Successful Dialog ────────────────────────────────────────

private val SuccessGreen = Color(0xFF16A34A)

@Composable
private fun PaymentSuccessDialog(
    onContinue: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Green checkmark circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.services_payment_successful),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.services_payment_confirmed_message),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.services_continue),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
