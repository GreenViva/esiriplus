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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.ServicesViewModel
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val CardBorder = Color(0xFFE5E7EB)
private val SubtitleGrey = Color(0xFF374151)
private val PopularOrange = Color(0xFFEA580C)

// Icon background colors per category
private val iconColors = mapOf(
    "NURSE" to Color(0xFF2A9D8F),
    "CLINICAL_OFFICER" to Color(0xFF3B82F6),
    "PHARMACIST" to Color(0xFFF59E0B),
    "GP" to Color(0xFF2A9D8F),
    "SPECIALIST" to Color(0xFFEF4444),
    "PSYCHOLOGIST" to Color(0xFF8B5CF6),
    "DRUG_INTERACTION" to Color(0xFF2A9D8F),
)

private val iconResources = mapOf(
    "NURSE" to R.drawable.ic_nurse,
    "CLINICAL_OFFICER" to R.drawable.ic_clinical_officer,
    "PHARMACIST" to R.drawable.ic_pharmacist,
    "GP" to R.drawable.ic_gp,
    "SPECIALIST" to R.drawable.ic_specialist,
    "PSYCHOLOGIST" to R.drawable.ic_psychologist,
    "DRUG_INTERACTION" to R.drawable.ic_sober_house,
)

private val comingSoonCategories = emptySet<String>()

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@Composable
fun ServicesScreen(
    onServiceSelected: (serviceCategory: String, priceAmount: Int, durationMinutes: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }

    // Find the selected service for the dialog subtitle
    val selectedService = uiState.services.find { it.id == uiState.selectedServiceId }

    // Payment verification simulation states
    var showVerifyingDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
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
                            contentDescription = "Back",
                            tint = Color.Black,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Our Services",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                        Text(
                            text = "Choose the type of healthcare provider you need \u2022 Tanzania",
                            fontSize = 13.sp,
                            color = SubtitleGrey,
                        )
                    }
                }
            }

            HorizontalDivider(color = CardBorder, thickness = 1.dp)

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            } else {
                // Service list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.services, key = { it.id }) { service ->
                        ServiceCard(
                            service = service,
                            isSelected = uiState.selectedServiceId == service.id,
                            onSelect = { viewModel.selectService(service.id) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Bottom section
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = { showPaymentDialog = true },
                        enabled = uiState.selectedServiceId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            disabledContainerColor = BrandTeal.copy(alpha = 0.5f),
                        ),
                    ) {
                        Text(
                            text = "Pay to Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Payment required to view available healthcare providers",
                        fontSize = 13.sp,
                        color = Color.Black,
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
            priceAmount = selectedService.priceAmount,
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

    // Step 3: Payment Instructions
    if (showInstructionsDialog && selectedService != null && selectedProvider != null) {
        PaymentInstructionsDialog(
            provider = selectedProvider!!,
            priceAmount = selectedService.priceAmount,
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

    // Step 5: Payment Successful
    if (showSuccessDialog && selectedService != null) {
        PaymentSuccessDialog(
            onContinue = {
                showSuccessDialog = false
                onServiceSelected(
                    selectedService.category,
                    selectedService.priceAmount,
                    selectedService.durationMinutes,
                )
            },
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceTierEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val isPopular = service.category == "GP"
    val isComingSoon = service.category in comingSoonCategories
    val borderColor = if (isSelected) BrandTeal else CardBorder
    val contentAlpha = if (isComingSoon) 0.5f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .background(Color.White)
            .then(if (!isComingSoon) Modifier.clickable { onSelect() } else Modifier),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Popular / Coming Soon badges
            if (isPopular || isComingSoon) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isPopular) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PopularOrange,
                        ) {
                            Text(
                                text = "\u2728 Popular",
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
                                text = "Coming Soon",
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
                        color = Color.Black.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = service.description,
                        fontSize = 12.sp,
                        color = SubtitleGrey.copy(alpha = contentAlpha),
                        lineHeight = 16.sp,
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Price + duration
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TSh ${numberFormat.format(service.priceAmount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BrandTeal.copy(alpha = contentAlpha),
                    )
                    Text(
                        text = "${service.durationMinutes} min",
                        fontSize = 11.sp,
                        color = SubtitleGrey.copy(alpha = contentAlpha),
                    )
                }

                if (!isComingSoon) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        modifier = Modifier.size(36.dp),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = BrandTeal,
                            unselectedColor = CardBorder,
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

@Composable
private fun FeatureTag(text: String, alpha: Float = 1f) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF9FAFB).copy(alpha = alpha),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
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
                color = Color.Black.copy(alpha = alpha),
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
            color = Color.White,
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
                        text = "Choose Payment Method",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Subtitle: service name + price
                Text(
                    text = "$serviceName \u2013 TSh ${numberFormat.format(priceAmount)}",
                    fontSize = 14.sp,
                    color = SubtitleGrey,
                )

                Spacer(Modifier.height(20.dp))

                // Mobile Money option (active)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_mobile_money,
                    iconBgColor = MobileMoneyGreen,
                    title = "Mobile Money",
                    subtitle = "M-Pesa, Airtel Money, HaloPesa, Yas",
                    enabled = true,
                    onClick = onMobileMoneySelected,
                )

                Spacer(Modifier.height(12.dp))

                // Card Payment (Coming Soon)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_credit_card,
                    iconBgColor = ComingSoonGrey,
                    title = "Card Payment",
                    subtitle = "Visa, Mastercard",
                    enabled = false,
                    comingSoon = true,
                    onClick = {},
                )

                Spacer(Modifier.height(12.dp))

                // Bank Transfer (Coming Soon)
                PaymentMethodOption(
                    iconRes = R.drawable.ic_bank,
                    iconBgColor = ComingSoonGrey,
                    title = "Bank Transfer",
                    subtitle = "Direct bank transfer",
                    enabled = false,
                    comingSoon = true,
                    onClick = {},
                )

                Spacer(Modifier.height(16.dp))

                // Secure payment note
                Text(
                    text = "\uD83D\uDD12 All payments are secure and encrypted",
                    fontSize = 12.sp,
                    color = SubtitleGrey,
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
    val borderColor = if (enabled) BrandTeal else CardBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (enabled) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .background(Color.White)
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
                    color = Color.Black.copy(alpha = alpha),
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SubtitleGrey.copy(alpha = alpha),
                )
            }

            // Coming Soon badge or arrow
            if (comingSoon) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3F4F6),
                ) {
                    Text(
                        text = "Coming Soon",
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
            color = Color.White,
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
                        text = "Select Your Provider",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
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
                            text = "Back",
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
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
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
                color = Color.Black,
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
            color = Color.White,
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
                        color = Color.Black,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
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
                            text = "Back",
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
                            color = Color.Black,
                        )
                        Text(
                            text = provider.ussdCode,
                            fontSize = 13.sp,
                            color = SubtitleGrey,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Reference Number
                Text(
                    text = "Reference Number",
                    fontSize = 12.sp,
                    color = SubtitleGrey,
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
                    text = "Amount to Pay",
                    fontSize = 12.sp,
                    color = SubtitleGrey,
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
                    text = "Follow these steps on your phone:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
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
                            color = Color.Black,
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
                            text = "To avoid unnecessary delays, please pay the exact amount of TSh ${numberFormat.format(priceAmount)} for your chosen service.",
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
                            containerColor = Color.White,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    ) {
                        Text(
                            text = "Change\nProvider",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
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
                            text = "I Have Paid",
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
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Verifying Payment...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(24.dp))

                CircularProgressIndicator(
                    color = BrandTeal,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 4.dp,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Verifying Payment...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Please wait while we confirm your payment",
                    fontSize = 13.sp,
                    color = SubtitleGrey,
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
            color = Color.White,
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
                    text = "Payment Successful!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Your payment has been confirmed. You can now browse available healthcare providers.",
                    fontSize = 13.sp,
                    color = SubtitleGrey,
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
                        text = "Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
