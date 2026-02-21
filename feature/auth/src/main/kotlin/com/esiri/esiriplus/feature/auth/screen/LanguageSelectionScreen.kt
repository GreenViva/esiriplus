package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun LanguageSelectionScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)

    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Teal circle with globe icon
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

            Spacer(modifier = Modifier.height(48.dp))

            // Swahili button — filled teal
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = pillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Chagua lugha >",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // English button — outlined teal
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = pillShape,
                border = BorderStroke(1.dp, BrandTeal),
            ) {
                Text(
                    text = "Choose your Language >",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandTeal,
                )
            }
        }
    }
}
