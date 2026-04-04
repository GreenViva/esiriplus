package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.patient.R

private val BrandTeal = Color(0xFF2A9D8F)
private val RoyalDeep = Color(0xFF4C1D95)      // deep purple
private val RoyalMid = Color(0xFF7C3AED)        // violet
private val RoyalGold = Color(0xFFF59E0B)       // gold accent
private val EconomyBg = Color(0xFFF0FDFA)
private val RoyalBg = Color(0xFFF5F3FF)

@Composable
fun TierSelectionScreen(
    onSelectRoyal: () -> Unit,
    onSelectEconomy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA)),
    ) {
        ScrollIndicatorBox(scrollState = scrollState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.tier_back),
                        tint = Color.Black,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = stringResource(R.string.tier_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Text(
                        text = stringResource(R.string.tier_subtitle),
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(20.dp))

            // ── Royal card ───────────────────────────────────────────────────
            RoyalTierCard(
                onClick = onSelectRoyal,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ── Economy card ─────────────────────────────────────────────────
            EconomyTierCard(
                onClick = onSelectEconomy,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(32.dp))
        }
        } // ScrollIndicatorBox
    }
}

// ── Royal tier card ───────────────────────────────────────────────────────────

@Composable
private fun RoyalTierCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(RoyalDeep, RoyalMid)),
                    RoundedCornerShape(20.dp),
                ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = RoyalGold,
                        modifier = Modifier.padding(0.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.tier_royal_badge),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.tier_royal_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tier_royal_tagline),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )

                Spacer(Modifier.height(20.dp))

                val benefits = listOf(
                    R.string.tier_royal_benefit_1,
                    R.string.tier_royal_benefit_2,
                    R.string.tier_royal_benefit_3,
                    R.string.tier_royal_benefit_4,
                    R.string.tier_royal_benefit_5,
                )
                benefits.forEach { res ->
                    BenefitRow(text = stringResource(res), tint = RoyalGold)
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(12.dp))

                // CTA row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tier_select),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoyalGold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Economy tier card ─────────────────────────────────────────────────────────

@Composable
private fun EconomyTierCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandTeal.copy(alpha = 0.4f)),
        colors = CardDefaults.outlinedCardColors(containerColor = EconomyBg),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Badge
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BrandTeal,
            ) {
                Text(
                    text = stringResource(R.string.tier_economy_badge),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.tier_economy_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tier_economy_tagline),
                fontSize = 14.sp,
                color = Color.Gray,
            )

            Spacer(Modifier.height(20.dp))

            val benefits = listOf(
                R.string.tier_economy_benefit_1,
                R.string.tier_economy_benefit_2,
                R.string.tier_economy_benefit_3,
            )
            benefits.forEach { res ->
                BenefitRow(text = stringResource(res), tint = BrandTeal)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tier_select),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandTeal,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun BenefitRow(text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.15f),
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (tint == Color(0xFFF59E0B)) Color.White.copy(alpha = 0.9f) else Color.Black,
        )
    }
}
