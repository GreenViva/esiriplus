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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.common.locale.LanguagePreferences
import com.esiri.esiriplus.core.common.locale.SupportedLanguage
import com.esiri.esiriplus.core.common.locale.supportedLanguages
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import kotlinx.coroutines.launch

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun LanguagePickerScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCode by remember {
        mutableStateOf(LanguagePreferences.getLanguageCode(context))
    }
    val recommended = remember { supportedLanguages.filter { it.isRecommended } }
    val other = remember { supportedLanguages.filter { !it.isRecommended } }

    GradientBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header: globe icon + title + subtitle
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(BrandTeal, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_globe),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Select Language / Chagua Lugha",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Choose your preferred language for the app",
                            fontSize = 14.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // RECOMMENDED section
                    item {
                        Text(
                            text = "RECOMMENDED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTeal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(recommended, key = { it.code }) { language ->
                        LanguageCard(
                            language = language,
                            isSelected = selectedCode == language.code,
                            onClick = { selectedCode = language.code },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // OTHER LANGUAGES section
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "OTHER LANGUAGES / LUGHA NYINGINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(other, key = { it.code }) { language ->
                        LanguageCard(
                            language = language,
                            isSelected = false,
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "${language.displayName} - Coming Soon / Inakuja Hivi Karibuni"
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Sticky bottom buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            LanguagePreferences.setLanguageCode(context, selectedCode)
                            onContinue()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(
                            text = "Continue / Endelea >",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back / Rudi",
                            fontSize = 14.sp,
                            color = BrandTeal,
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp),
            )
        }
    }
}

@Composable
private fun LanguageCard(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) BrandTeal else Color.LightGray
    val backgroundColor = if (isSelected) BrandTeal.copy(alpha = 0.08f) else Color.Transparent
    val cardShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = cardShape,
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.nativeName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) BrandTeal else Color.Black,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
