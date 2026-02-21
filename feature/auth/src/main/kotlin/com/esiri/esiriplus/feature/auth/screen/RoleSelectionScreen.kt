package com.esiri.esiriplus.feature.auth.screen

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val SubtitleGray = Color.Black
private val CardBorder = Color(0xFFE5E7EB)
private val IconBg = Color(0xFFF0FDFA)

@Composable
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onDoctorSelected: () -> Unit,
    onRecoverPatientId: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Stethoscope icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(BrandTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to eSIRI Plus",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "How would you like to proceed?",
                fontSize = 14.sp,
                color = SubtitleGray,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // FOR PATIENTS section
            Text(
                text = "FOR PATIENTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SubtitleGray,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Are you new? card
            RoleCard(
                iconRes = R.drawable.ic_person_add,
                title = "New to the platform?",
                subtitle = "Click here to get started",
                onClick = onPatientSelected,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // I have my ID card
            RoleCard(
                iconRes = R.drawable.ic_key,
                title = "I have my ID",
                subtitle = "Access my records",
                onClick = onRecoverPatientId,
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onRecoverPatientId) {
                Text(
                    text = "Forgot your Patient ID?",
                    fontSize = 13.sp,
                    color = BrandTeal,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Divider with "or"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = CardBorder,
                )
                Text(
                    text = "or",
                    fontSize = 14.sp,
                    color = SubtitleGray,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = CardBorder,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // DOCTOR PORTAL section
            Text(
                text = "DOCTOR PORTAL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SubtitleGray,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDoctorSelected,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                ) {
                    Text(
                        text = "Sign In",
                        color = DarkText,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Button(
                    onClick = onDoctorSelected,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Sign Up",
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDoctorSelected) {
                Text(
                    text = "Forgot your password?",
                    fontSize = 13.sp,
                    color = BrandTeal,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Text(
                text = "\u00A9 2026 eSIRI Plus. All rights reserved.",
                fontSize = 12.sp,
                color = SubtitleGray,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun RoleCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, CardBorder, cardShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(IconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = BrandTeal,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = SubtitleGray,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = SubtitleGray,
            modifier = Modifier.size(20.dp),
        )
    }
}
