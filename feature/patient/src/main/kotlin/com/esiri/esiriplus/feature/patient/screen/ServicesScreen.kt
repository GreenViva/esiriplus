package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.ServiceTierEntity
import com.esiri.esiriplus.core.domain.model.ConsultationTier
import com.esiri.esiriplus.core.ui.PulsingScrollArrow
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.Teal
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.ServicesUiState
import com.esiri.esiriplus.feature.patient.viewmodel.ServicesViewModel

private val Royal          = Color(0xFF6E4FE0)
private val RoyalDeep      = Color(0xFF4F2FBF)
private val IconGreen      = Color(0xFF1E8E76)
private val IconGreenBg    = Color(0xFFDDF3EB)
private val IconBlue       = Color(0xFF2B6FCF)
private val IconBlueBg     = Color(0xFFDCEAFB)
private val IconOrange     = Color(0xFFC26A1F)
private val IconOrangeBg   = Color(0xFFFFE9D6)
private val IconRed        = Color(0xFFB5483A)
private val IconRedBg      = Color(0xFFFBE0DD)
private val IconPurple     = Color(0xFF5E3CC4)
private val IconPurpleBg   = Color(0xFFEAE0FB)
private val IconLeaf       = Color(0xFF4A7E2B)
private val IconLeafBg     = Color(0xFFDEF2D5)
private val PopularBg      = Color(0xFFFFF1E0)
private val PopularFg      = Color(0xFFB86A1A)

private data class ServiceVisuals(
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val groupKey: ServiceGroup,
    val isPopular: Boolean,
)

private enum class ServiceGroup(val labelRes: Int, val sortOrder: Int) {
    PRIMARY_CARE(R.string.services_group_primary_care, 0),
    PHARMACY(R.string.services_group_pharmacy, 1),
    MENTAL_TRADITIONAL(R.string.services_group_mental_traditional, 2),
    OTHER(R.string.services_group_primary_care, 9),
}

private fun visualsFor(category: String): ServiceVisuals = when (category.uppercase()) {
    "GP" -> ServiceVisuals(Icons.Outlined.Person, IconGreenBg, IconGreen, ServiceGroup.PRIMARY_CARE, isPopular = true)
    "NURSE" -> ServiceVisuals(Icons.Outlined.Favorite, IconGreenBg, IconGreen, ServiceGroup.PRIMARY_CARE, false)
    "CLINICAL_OFFICER" -> ServiceVisuals(Icons.Outlined.Science, IconBlueBg, IconBlue, ServiceGroup.PRIMARY_CARE, false)
    "SPECIALIST" -> ServiceVisuals(Icons.Outlined.MedicalServices, IconRedBg, IconRed, ServiceGroup.PRIMARY_CARE, false)
    "PHARMACIST" -> ServiceVisuals(Icons.Outlined.LocalPharmacy, IconOrangeBg, IconOrange, ServiceGroup.PHARMACY, false)
    "DRUG_INTERACTION" -> ServiceVisuals(Icons.Outlined.Medication, IconGreenBg, IconGreen, ServiceGroup.PHARMACY, false)
    "PSYCHOLOGIST" -> ServiceVisuals(Icons.Outlined.SelfImprovement, IconPurpleBg, IconPurple, ServiceGroup.MENTAL_TRADITIONAL, false)
    "HERBALIST" -> ServiceVisuals(Icons.Outlined.Spa, IconLeafBg, IconLeaf, ServiceGroup.MENTAL_TRADITIONAL, false)
    else -> ServiceVisuals(Icons.Outlined.MedicalServices, TealSoft, TealDeep, ServiceGroup.OTHER, false)
}

private fun formatTzs(amount: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(amount.toLong())

@Composable
fun ServicesScreen(
    onServiceSelected: (serviceCategory: String, priceAmount: Int, durationMinutes: Int, serviceTier: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Auto-select the first service so the pay bar always has something to show.
    LaunchedEffect(state.services) {
        if (state.selectedServiceId == null && state.services.isNotEmpty()) {
            viewModel.selectService(state.services.first().id)
        }
    }

    val selected = state.services.firstOrNull { it.id == state.selectedServiceId }
        ?: state.services.firstOrNull()

    var showPaymentFlow by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = { ServicesTopBar(onBack = onBack) },
        bottomBar = {
            if (selected != null) {
                val finalPrice = state.effectivePrice(selected)
                PayBar(
                    service = selected,
                    finalPriceAmount = finalPrice,
                    onClick = {
                        if (finalPrice == 0) {
                            // Free offer — skip the payment dialog chain entirely.
                            onServiceSelected(
                                selected.category,
                                0,
                                selected.durationMinutes,
                                state.tier.name,
                            )
                        } else {
                            showPaymentFlow = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        val canScrollDown by remember {
            derivedStateOf { scrollState.canScrollForward }
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.services_headline_prefix))
                    withStyle(
                        SpanStyle(
                            color = TealDeep,
                            fontStyle = FontStyle.Italic,
                            fontFamily = InstrumentSerif,
                        ),
                    ) { append(stringResource(R.string.services_headline_accent)) }
                },
                fontFamily = InstrumentSerif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 30.sp,
                color = Ink,
            )

            Spacer(Modifier.height(4.dp))

            val tierLabel = if (state.tier == ConsultationTier.ROYAL) {
                stringResource(R.string.services_tier_label_royal)
            } else {
                stringResource(R.string.services_tier_label_economy)
            }
            Text(
                text = stringResource(R.string.services_location_active, tierLabel),
                fontFamily = Geist,
                fontSize = 12.sp,
                color = Muted,
            )

            Spacer(Modifier.height(12.dp))

            if (state.tier == ConsultationTier.ROYAL) {
                RoyalActivePill()
                Spacer(Modifier.height(14.dp))
            }

            val grouped = state.services
                .sortedBy { it.sortOrder }
                .groupBy { visualsFor(it.category).groupKey }
                .toSortedMap(compareBy { it.sortOrder })

            grouped.forEach { (group, services) ->
                ServiceGroupSection(
                    groupLabel = stringResource(group.labelRes),
                    services = services,
                    selectedId = selected?.id,
                    state = state,
                    onSelect = { id -> viewModel.selectService(id) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = canScrollDown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                PulsingScrollArrow(tint = TealDeep)
            }
        }
    }

    if (showPaymentFlow && selected != null) {
        val finalPrice = state.effectivePrice(selected)
        PaymentMethodFlow(
            serviceName = selected.displayName,
            priceAmount = finalPrice,
            patientId = state.patientId,
            onPaid = {
                showPaymentFlow = false
                onServiceSelected(
                    selected.category,
                    finalPrice,
                    selected.durationMinutes,
                    state.tier.name,
                )
            },
            onDismiss = { showPaymentFlow = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServicesTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Hairline, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.tier_back),
                        tint = Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = "eSIRI Plus",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
            }
        },
    )
}

@Composable
private fun RoyalActivePill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Royal, RoyalDeep)))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Star,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.services_royal_pill),
            fontFamily = Geist,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
private fun ServiceGroupSection(
    groupLabel: String,
    services: List<ServiceTierEntity>,
    selectedId: String?,
    state: ServicesUiState,
    onSelect: (String) -> Unit,
) {
    Text(
        text = groupLabel.uppercase(),
        fontFamily = Geist,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Muted,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )

    services.forEach { service ->
        ServiceCard(
            service = service,
            visuals = visualsFor(service.category),
            isSelected = service.id == selectedId,
            finalPriceAmount = state.effectivePrice(service),
            onSelect = { onSelect(service.id) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ServiceCard(
    service: ServiceTierEntity,
    visuals: ServiceVisuals,
    isSelected: Boolean,
    finalPriceAmount: Int,
    onSelect: () -> Unit,
) {
    val cardBg = if (isSelected) TealSoft else Color.White
    val cardBorder = if (isSelected) Teal else Hairline
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(borderWidth, cardBorder, RoundedCornerShape(14.dp))
            .pressableClick(onClick = onSelect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(visuals.iconBg),
        ) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.iconTint,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = service.displayName,
                    fontFamily = Geist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                if (visuals.isPopular) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.services_popular_badge),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PopularBg)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        fontFamily = Geist,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = PopularFg,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = service.description,
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted,
                lineHeight = 15.sp,
                maxLines = 2,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatTzs(finalPriceAmount),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TealDeep,
                letterSpacing = 0.3.sp,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(R.string.services_minutes_format, service.durationMinutes),
                fontFamily = Geist,
                fontSize = 10.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun PayBar(
    service: ServiceTierEntity,
    finalPriceAmount: Int,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.services_selected_template,
                        service.displayName.uppercase(),
                    ),
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    color = Muted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.services_currency_amount,
                        formatTzs(finalPriceAmount),
                    ),
                    fontFamily = Geist,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = 0.3.sp,
                )
            }

            Spacer(Modifier.width(12.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(TealDeep, Teal)))
                    .pressableClick(onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.services_pay_continue),
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
