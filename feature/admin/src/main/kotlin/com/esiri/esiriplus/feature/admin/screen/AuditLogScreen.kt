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
import com.esiri.esiriplus.feature.admin.viewmodel.ActionCategory
import com.esiri.esiriplus.feature.admin.viewmodel.AuditLogEntry
import com.esiri.esiriplus.feature.admin.viewmodel.AuditLogViewModel

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuditLogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Audit Log",
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
                text = "Your HR activity audit trail",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                placeholder = { Text("Search actions...", color = Color.Gray) },
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
                ActionFilterDropdown(
                    selectedCategory = uiState.actionFilter,
                    onCategorySelected = { viewModel.updateActionFilter(it) },
                )
            }

            Spacer(Modifier.height(4.dp))

            // Results count
            val count = uiState.filteredLogs.size
            Text(
                "$count entr${if (count != 1) "ies" else "y"}",
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
                        OutlinedButton(onClick = { viewModel.loadLogs() }) {
                            Text("Retry", color = BrandTeal)
                        }
                    }
                }
            } else if (uiState.filteredLogs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No audit entries found.", color = Color.Gray, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(uiState.filteredLogs, key = { it.id }) { entry ->
                        AuditLogCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionFilterDropdown(
    selectedCategory: ActionCategory,
    onCategorySelected: (ActionCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedCategory != ActionCategory.ALL,
            onClick = { expanded = true },
            label = { Text(selectedCategory.label, fontSize = 13.sp) },
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
            ActionCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label, color = Color.Black) },
                    onClick = { onCategorySelected(category); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun AuditLogCard(entry: AuditLogEntry) {
    val badgeLabel = AuditLogViewModel.getTypeBadgeLabel(entry.action)
    val badgeColor = getTypeBadgeColor(entry.action)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: timestamp + type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatAuditDate(entry.createdAt),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = badgeLabel,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            badgeColor.copy(alpha = 0.1f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // User email
            Text(
                text = entry.adminEmail,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            // Action description
            Text(
                text = AuditLogViewModel.formatActionDescription(entry),
                color = Color.Black,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Details (target_type)
            val details = entry.targetType
            if (!details.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = details.replace("_", " "),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun getTypeBadgeColor(action: String): Color {
    return when {
        action.contains("unsuspend") || action == "unban_doctor" -> Color(0xFF16A34A) // green
        action.contains("unflag") -> Color(0xFF6B7280) // gray
        action.contains("approve") || action == "doctor_verified" || action == "doctor_activated" -> Color(0xFF16A34A) // green
        action.contains("reject") || action == "doctor_rejected" -> Color(0xFFDC2626) // red
        action == "doctor_deactivated" -> Color(0xFFEA580C) // orange
        action.contains("suspend") -> Color(0xFFEA580C) // orange
        action.contains("ban") -> Color(0xFFDC2626) // red
        action.contains("warn") -> Color(0xFFD97706) // amber
        action.contains("create") || action.contains("setup") || action == "role_assigned" -> Color(0xFF2563EB) // blue
        action.contains("flag") -> Color(0xFF7C3AED) // purple
        action.contains("revoke") || action.contains("deauthorize") -> Color(0xFF6B7280) // gray
        action == "doctor_registered" || action == "patient_session_created" -> Color(0xFF4F46E5) // indigo
        action.contains("consultation") -> Color(0xFF2A9D8F) // teal
        action.contains("payment") -> Color(0xFF059669) // emerald
        action.contains("rating") -> Color(0xFFD97706) // amber/yellow
        else -> Color(0xFF6B7280) // gray
    }
}

private fun formatAuditDate(isoDate: String): String {
    return try {
        val instant = java.time.Instant.parse(isoDate)
        val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        local.format(formatter)
    } catch (_: Exception) {
        isoDate.take(16) // fallback: date + time portion
    }
}
