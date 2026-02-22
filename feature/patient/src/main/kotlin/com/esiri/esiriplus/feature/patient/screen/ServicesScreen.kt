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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val comingSoonCategories = setOf("DRUG_INTERACTION")

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@Composable
fun ServicesScreen(
    onServiceSelected: (serviceId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        onClick = {
                            uiState.selectedServiceId?.let { onServiceSelected(it) }
                        },
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
                            text = "Select a Service",
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
