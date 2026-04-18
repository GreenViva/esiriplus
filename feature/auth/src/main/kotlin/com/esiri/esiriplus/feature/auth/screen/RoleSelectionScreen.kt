package com.esiri.esiriplus.feature.auth.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground

private val BrandTeal = Color(0xFF2A9D8F)
private val PatientBlue = Color(0xFF3B82F6)
private val DoctorGreen = Color(0xFF10B981)

@Composable
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onDoctorSelected: () -> Unit,
    onDoctorRegister: () -> Unit,
    onRecoverPatientId: () -> Unit,
    onHaveMyId: () -> Unit,
    onAgentSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var expandedSection by rememberSaveable { mutableStateOf<String?>(null) }

    GradientBackground(modifier = modifier) {
        ScrollIndicatorBox(scrollState = scrollState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // App logo
                Image(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.role_welcome_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.role_welcome_subtitle),
                    fontSize = 14.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── FOR PATIENT big badge ──
                BigBadgeButton(
                    title = stringResource(R.string.role_for_patients),
                    subtitle = stringResource(R.string.role_patient_badge_subtitle),
                    iconRes = R.drawable.ic_person_add,
                    gradientColors = listOf(PatientBlue, Color(0xFF1D4ED8)),
                    isExpanded = expandedSection == "patient",
                    onClick = {
                        expandedSection = if (expandedSection == "patient") null else "patient"
                    },
                )

                // Patient options (expand below the badge)
                AnimatedVisibility(
                    visible = expandedSection == "patient",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // New to the platform
                        OptionButton(
                            text = stringResource(R.string.role_new_platform),
                            subtitle = stringResource(R.string.role_new_platform_subtitle),
                            onClick = onPatientSelected,
                        )

                        // I have my ID
                        OptionButton(
                            text = stringResource(R.string.role_have_my_id),
                            subtitle = stringResource(R.string.role_access_records),
                            onClick = onHaveMyId,
                        )

                        // Forgot your ID
                        TextButton(
                            onClick = onRecoverPatientId,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = stringResource(R.string.role_forgot_patient_id),
                                fontSize = 13.sp,
                                color = PatientBlue,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── FOR DOCTORS big badge ──
                BigBadgeButton(
                    title = stringResource(R.string.role_for_doctors),
                    subtitle = stringResource(R.string.role_doctor_badge_subtitle),
                    iconRes = R.drawable.ic_stethoscope,
                    gradientColors = listOf(DoctorGreen, Color(0xFF059669)),
                    isExpanded = expandedSection == "doctor",
                    onClick = {
                        expandedSection = if (expandedSection == "doctor") null else "doctor"
                    },
                )

                // Doctor options (expand below the badge)
                AnimatedVisibility(
                    visible = expandedSection == "doctor",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Sign In
                            OutlinedButton(
                                onClick = onDoctorSelected,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, DoctorGreen,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.role_sign_in),
                                    color = DoctorGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }

                            // Sign Up
                            Button(
                                onClick = onDoctorRegister,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DoctorGreen,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.role_sign_up),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }

                        TextButton(
                            onClick = onDoctorSelected,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = stringResource(R.string.role_forgot_password),
                                fontSize = 13.sp,
                                color = DoctorGreen,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── eSIRIPlus AGENTS ──
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                    color = Color(0xFFFFF7ED),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f),
                    ),
                    onClick = onAgentSelected,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFF59E0B), Color(0xFFEF6C00)),
                                    ),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "e+",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "eSIRIPlus Agents",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                            )
                            Text(
                                text = "Earn money by becoming an agent",
                                fontSize = 12.sp,
                                color = Color.Black,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Contact Us
                ContactUsSection()

                // Footer
                Text(
                    text = stringResource(R.string.role_copyright),
                    fontSize = 12.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

// ── Big Badge Button ────────────────────────────────────────────────────────

@Composable
private fun BigBadgeButton(
    title: String,
    subtitle: String,
    iconRes: Int,
    gradientColors: List<Color>,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

// ── Option Button (for patient sub-options) ─────────────────────────────────

@Composable
private fun OptionButton(
    text: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Black,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PatientBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Contact Us ──────────────────────────────────────────────────────────────

@Composable
private fun ContactUsSection() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "For help contact us",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "+255 663 582 994",
                fontSize = 12.sp,
                color = BrandTeal,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+255663582994")))
                },
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "support@esiri.africa",
                fontSize = 12.sp,
                color = BrandTeal,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@esiri.africa")),
                    )
                },
            )
        }
    }
}
