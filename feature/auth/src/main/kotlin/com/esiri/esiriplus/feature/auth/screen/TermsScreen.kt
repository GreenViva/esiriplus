package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import kotlinx.coroutines.launch

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val SubtitleGray = Color.Black
private val SectionBg = Color(0xFFF8FFFE)
private val CardBorder = Color(0xFFE5E7EB)

@Composable
fun TermsScreen(
    onAgree: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = LegalTab.entries
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var agreed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DarkText,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terms & Privacy Policy",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                    )
                    Text(
                        text = "Please read carefully before continuing",
                        fontSize = 13.sp,
                        color = SubtitleGray,
                    )
                }
            }

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = BrandTeal,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = BrandTeal,
                        )
                    }
                },
                divider = { HorizontalDivider(color = CardBorder) },
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            scope.launch { scrollState.scrollTo(0) }
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) BrandTeal else SubtitleGray,
                            )
                        },
                    )
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Document header
                val currentTab = tabs[selectedTabIndex]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = BrandTeal,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandTeal.copy(alpha = 0.1f))
                            .padding(8.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentTab.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                        )
                        Text(
                            text = "eSIRI Plus Legal Document",
                            fontSize = 13.sp,
                            color = SubtitleGray,
                        )
                    }
                }

                HorizontalDivider(color = CardBorder)

                // Sections
                val sections = TermsContent.getSections(currentTab)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SectionBg)
                        .padding(16.dp),
                ) {
                    sections.forEach { section ->
                        if (section.heading != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = section.heading,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandTeal,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = section.body,
                            fontSize = 14.sp,
                            color = DarkText,
                            lineHeight = 22.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sticky bottom: checkbox + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.95f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF9FAFB))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = agreed,
                        onCheckedChange = { agreed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandTeal,
                            uncheckedColor = SubtitleGray,
                        ),
                    )
                    Text(
                        text = "I have read, understood, and agree to the Terms of Service, Privacy Policy, Medical Disclaimer, and Informed Consent",
                        fontSize = 12.sp,
                        color = DarkText,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onAgree,
                    enabled = agreed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                        disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f),
                    ),
                ) {
                    Text(
                        text = "I Agree & Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
