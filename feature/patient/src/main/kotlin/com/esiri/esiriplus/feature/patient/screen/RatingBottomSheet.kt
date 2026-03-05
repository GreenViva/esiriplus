package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.RatingViewModel
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)
private val StarAmber = Color(0xFFF59E0B)
private val SuccessGreen = Color(0xFF10B981)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingBottomSheet(
    consultationId: String,
    doctorId: String,
    patientSessionId: String,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit,
    viewModel: RatingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(consultationId) {
        viewModel.initialize(consultationId, doctorId, patientSessionId)
    }

    // Auto-dismiss after success
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            delay(1500)
            onSubmitSuccess()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.submitSuccess) {
                // Success state
                Spacer(Modifier.height(24.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.rating_success_content_desc),
                    tint = SuccessGreen,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.rating_thank_you),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(40.dp))
            } else {
                // Header
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = StarAmber,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.rating_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rating_subtitle),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                )
                Spacer(Modifier.height(24.dp))

                // Star picker
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { viewModel.setStars(i) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (i <= uiState.stars) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = stringResource(R.string.rating_star_format, i),
                                tint = if (i <= uiState.stars) StarAmber else Color(0xFFD1D5DB),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }

                if (uiState.stars > 0) {
                    Text(
                        text = when (uiState.stars) {
                            1 -> stringResource(R.string.rating_poor)
                            2 -> stringResource(R.string.rating_fair)
                            3 -> stringResource(R.string.rating_good)
                            4 -> stringResource(R.string.rating_very_good)
                            5 -> stringResource(R.string.rating_excellent)
                            else -> ""
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = StarAmber,
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Comment field
                OutlinedTextField(
                    value = uiState.comment,
                    onValueChange = viewModel::setComment,
                    label = {
                        Text(
                            if (uiState.stars in 1..3) stringResource(R.string.rating_low_comment_label)
                            else stringResource(R.string.rating_optional_comment_label),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    isError = uiState.commentError != null,
                    supportingText = uiState.commentError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        focusedLabelColor = BrandTeal,
                        cursorColor = BrandTeal,
                    ),
                )

                // Error message
                if (uiState.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Submit button
                Button(
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !uiState.isSubmitting && uiState.stars > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.isSubmitting) stringResource(R.string.rating_submitting) else stringResource(R.string.rating_submit),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Remind me later
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.rating_remind_later),
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
