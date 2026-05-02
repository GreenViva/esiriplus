package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InkSoft
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.Teal
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.feature.patient.R

private val Royal         = Color(0xFF6E4FE0)
private val RoyalDeep     = Color(0xFF4F2FBF)
private val RoyalCheckBg  = Color(0xFFEFE6FF)
private val EconomyCardBg = Color(0xFFF4FAF7)

private enum class Tier { ECONOMY, ROYAL }

private data class TierOption(
    val tier: Tier,
    val nameRes: Int,
    val priceFromRes: Int,
    val descriptionRes: Int,
    val featureResIds: List<Int>,
    val badgeRes: Int,
    val ctaRes: Int,
)

@Composable
fun TierSelectionScreen(
    onSelectRoyal: () -> Unit,
    onSelectEconomy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiers = remember {
        listOf(
            TierOption(
                tier = Tier.ECONOMY,
                nameRes = R.string.tier_economy_title,
                priceFromRes = R.string.tier_economy_price_from,
                descriptionRes = R.string.tier_economy_short_desc,
                featureResIds = listOf(
                    R.string.tier_economy_feature_1,
                    R.string.tier_economy_feature_2,
                ),
                badgeRes = R.string.tier_economy_badge_chosen,
                ctaRes = R.string.tier_economy_cta,
            ),
            TierOption(
                tier = Tier.ROYAL,
                nameRes = R.string.tier_royal_title,
                priceFromRes = R.string.tier_royal_price_from,
                descriptionRes = R.string.tier_royal_short_desc,
                featureResIds = listOf(
                    R.string.tier_royal_feature_1,
                    R.string.tier_royal_feature_2,
                    R.string.tier_royal_feature_3,
                ),
                badgeRes = R.string.tier_royal_badge_premium,
                ctaRes = R.string.tier_royal_cta,
            ),
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = { ServiceTierTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.tier_headline_prefix))
                    withStyle(
                        SpanStyle(
                            color = TealDeep,
                            fontStyle = FontStyle.Italic,
                            fontFamily = InstrumentSerif,
                        ),
                    ) { append(stringResource(R.string.tier_headline_accent)) }
                },
                fontFamily = InstrumentSerif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 30.sp,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tier_subtitle_short),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = Muted,
            )

            Spacer(Modifier.height(18.dp))

            tiers.forEach { tier ->
                TierCard(
                    tier = tier,
                    onClick = {
                        when (tier.tier) {
                            Tier.ECONOMY -> onSelectEconomy()
                            Tier.ROYAL -> onSelectRoyal()
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceTierTopBar(onBack: () -> Unit) {
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
        title = {},
        actions = {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = "eSIRI Plus",
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape),
            )
        },
    )
}

@Composable
private fun TierCard(tier: TierOption, onClick: () -> Unit) {
    val isRoyal = tier.tier == Tier.ROYAL
    val cardBorder = if (isRoyal) Hairline else Teal
    val cardBg = if (isRoyal) Color.White else EconomyCardBg
    val checkBg = if (isRoyal) RoyalCheckBg else TealSoft
    val checkTint = if (isRoyal) Royal else TealDeep
    val priceColor = if (isRoyal) Royal else TealDeep
    val ctaBrush: Brush = if (isRoyal) {
        Brush.linearGradient(listOf(Royal, RoyalDeep))
    } else {
        Brush.linearGradient(listOf(TealDeep, TealDeep))
    }
    val badgeBrush: Brush = ctaBrush

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardBg)
                .border(1.5.dp, cardBorder, RoundedCornerShape(18.dp))
                .pressableClick(onClick = onClick)
                .padding(top = 26.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(tier.nameRes),
                    fontFamily = InstrumentSerif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = Ink,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.tier_price_from_label),
                        fontFamily = Geist,
                        fontSize = 9.sp,
                        color = Muted,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = stringResource(tier.priceFromRes),
                        fontFamily = Geist,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = priceColor,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(tier.descriptionRes),
                fontFamily = Geist,
                fontSize = 12.sp,
                color = Muted,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(14.dp))

            tier.featureResIds.forEach { featureRes ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(checkBg),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = checkTint,
                            modifier = Modifier.size(9.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(featureRes),
                        fontFamily = Geist,
                        fontSize = 12.sp,
                        color = InkSoft,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ctaBrush)
                    .pressableClick(onClick = onClick)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(tier.ctaRes),
                        fontFamily = Geist,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Floating badge above the top-left corner.
        Box(
            modifier = Modifier
                .padding(start = 20.dp)
                .offset(y = (-10).dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBrush)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(tier.badgeRes),
                fontFamily = Geist,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.6.sp,
            )
        }
    }
}
