package com.esiri.esiriplus.feature.admin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.admin.viewmodel.AdminRatingRow
import com.esiri.esiriplus.feature.admin.viewmodel.RatingsFeedbackViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val StarAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingsFeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RatingsFeedbackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ratings & Patient Feedback",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Subtitle
            Text(
                text = "Review all patient ratings, identify patterns, and flag concerns",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                placeholder = { Text("Search by doctor name or comment...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Default.Close, "Clear", tint = Color.Gray)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    cursorColor = BrandTeal,
                ),
            )

            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Rating filter dropdown
                RatingFilterDropdown(
                    selectedRating = uiState.ratingFilter,
                    onRatingSelected = { viewModel.updateRatingFilter(it) },
                )

                Spacer(Modifier.weight(1f))

                // Flagged toggle
                FlaggedToggleButton(
                    flaggedCount = uiState.flaggedCount,
                    isActive = uiState.flaggedOnly,
                    onClick = { viewModel.toggleFlaggedOnly() },
                )
            }

            Spacer(Modifier.height(4.dp))

            // Results count
            Text(
                "${uiState.totalCount} reviews found",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Content
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            } else if (uiState.error != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.error!!, color = Color(0xFFDC2626), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.loadRatings() }) {
                            Text("Retry", color = BrandTeal)
                        }
                    }
                }
            } else if (uiState.ratings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ratings found", color = Color.Gray, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(uiState.ratings, key = { it.ratingId }) { rating ->
                        RatingCard(rating)
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingFilterDropdown(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (selectedRating != null) "$selectedRating Star${if (selectedRating > 1) "s" else ""}" else "All Ratings"

    Box {
        FilterChip(
            selected = selectedRating != null,
            onClick = { expanded = true },
            label = { Text(label, fontSize = 13.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BrandTeal,
                selectedLabelColor = Color.White,
                containerColor = Color.White,
                labelColor = Color.Black,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All Ratings", color = Color.Black) },
                onClick = { onRatingSelected(null); expanded = false },
            )
            (1..5).forEach { stars ->
                DropdownMenuItem(
                    text = { Text("$stars Star${if (stars > 1) "s" else ""}", color = Color.Black) },
                    onClick = { onRatingSelected(stars); expanded = false },
                    leadingIcon = {
                        Icon(Icons.Default.Star, null, tint = StarAmber, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun FlaggedToggleButton(
    flaggedCount: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isActive) Color.White else Color(0xFFD97706),
                )
                Spacer(Modifier.width(4.dp))
                Text("Flagged ($flaggedCount)", fontSize = 13.sp)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFD97706),
            selectedLabelColor = Color.White,
            containerColor = Color.White,
            labelColor = Color.Black,
        ),
    )
}

@Composable
private fun RatingCard(rating: AdminRatingRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: doctor name + flag badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rating.doctorName,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (rating.isFlagged) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Flagged",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color(0xFFDC2626), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Star row
            Row {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (index < rating.rating) StarAmber else Color.LightGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${rating.rating}/5",
                    color = Color.Black,
                    fontSize = 13.sp,
                )
            }

            // Comment
            if (!rating.comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = rating.comment,
                    color = Color.Black,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Date
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatRatingDate(rating.createdAt),
                color = Color.Gray,
                fontSize = 12.sp,
            )
        }
    }
}

private fun formatRatingDate(isoDate: String): String {
    return try {
        val instant = java.time.Instant.parse(isoDate)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        local.format(formatter)
    } catch (_: Exception) {
        isoDate.take(10) // fallback: just the date portion
    }
}
