package com.esiri.esiriplus.feature.auth.screen

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorLoginViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val CreamBackground = Color(0xFFF5F0EB)
private val SubtitleGray = Color.Black
private val FieldBorder = Color(0xFFE0E0E0)

@Composable
fun DoctorLoginScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    onRegister: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DoctorLoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Stethoscope icon in teal circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(BrandTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Doctor Portal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle
        Text(
            text = "Sign in to manage your consultations",
            fontSize = 14.sp,
            color = SubtitleGray,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // White card container
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                // Tab row: Sign In | Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF2F2F2), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // Sign In tab (active)
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        onClick = { /* already on sign in */ },
                    ) {
                        Text(
                            text = "Sign In",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                        )
                    }

                    // Register tab (inactive)
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        onClick = onRegister,
                    ) {
                        Text(
                            text = "Register",
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SubtitleGray,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Email label
                Text(
                    text = "Email",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Email field
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("doctor@example.com", color = SubtitleGray, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_email),
                            contentDescription = null,
                            tint = SubtitleGray,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = FieldBorder,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password label
                Text(
                    text = "Password",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Password field
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", color = SubtitleGray, fontSize = 14.sp)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            tint = SubtitleGray,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility
                                    else R.drawable.ic_visibility_off,
                                ),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = SubtitleGray,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = FieldBorder,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                // Error message
                uiState.error?.let { error ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign In button
                Button(
                    onClick = { viewModel.login(onAuthenticated) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = uiState.isFormValid && !uiState.isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        disabledContainerColor = BrandTeal.copy(alpha = 0.5f),
                    ),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Sign In",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
