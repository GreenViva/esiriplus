package com.esiri.esiriplus.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.esiri.esiriplus.R
import com.esiri.esiriplus.ui.preferences.FontScale
import com.esiri.esiriplus.ui.preferences.ThemeMode
import com.esiri.esiriplus.ui.preferences.UserPreferencesManager

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun AccessibilityFab(
    preferencesManager: UserPreferencesManager,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(250),
        label = "fab_rotation",
    )

    // Drag offset — starts at bottom-end, user can drag anywhere
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fabSizePx = with(density) { 52.dp.toPx() }
        val paddingPx = with(density) { 16.dp.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        // Clamp so the FAB never leaves the screen
        val clampedX = offsetX.coerceIn(-(maxWidthPx - fabSizePx - paddingPx), 0f)
        val clampedY = offsetY.coerceIn(-(maxHeightPx - fabSizePx - paddingPx), 0f)

        // Scrim when panel is open
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { expanded = false },
                    ),
            )
        }

        // Settings panel — anchored near the FAB
        AnimatedVisibility(
            visible = expanded,
            enter = scaleIn(tween(250), initialScale = 0.8f) + fadeIn(tween(200)),
            exit = scaleOut(tween(200), targetScale = 0.8f) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = with(density) { clampedX.roundToInt().toDp() },
                    y = with(density) { clampedY.roundToInt().toDp() },
                )
                .padding(end = 16.dp, bottom = 80.dp),
        ) {
            AccessibilityPanel(
                preferencesManager = preferencesManager,
                onDismiss = { expanded = false },
            )
        }

        // FAB + drag-arrow colors driven by MaterialTheme so they adapt to
        // Light/Dark. Theme primary is Teal40 (≈ BrandTeal) in light mode
        // and Teal80 (lighter teal) in dark mode, which reads cleanly
        // against the dark surface roles.
        val fabContainer = MaterialTheme.colorScheme.primary
        val fabContent = MaterialTheme.colorScheme.onPrimary
        val arrowColor = fabContainer.copy(alpha = 0.5f)

        // Draggable FAB with directional arrows (↑↓←→)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(
                    x = with(density) { clampedX.roundToInt().toDp() },
                    y = with(density) { clampedY.roundToInt().toDp() },
                )
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-(maxWidthPx - fabSizePx - paddingPx), 0f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(-(maxHeightPx - fabSizePx - paddingPx), 0f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Arrow indicators around the FAB
            // Top arrow
            Text(
                text = "\u25B2",
                fontSize = 8.sp,
                color = arrowColor,
                modifier = Modifier.offset(y = (-30).dp),
            )
            // Bottom arrow
            Text(
                text = "\u25BC",
                fontSize = 8.sp,
                color = arrowColor,
                modifier = Modifier.offset(y = 30.dp),
            )
            // Left arrow
            Text(
                text = "\u25C0",
                fontSize = 8.sp,
                color = arrowColor,
                modifier = Modifier.offset(x = (-30).dp),
            )
            // Right arrow
            Text(
                text = "\u25B6",
                fontSize = 8.sp,
                color = arrowColor,
                modifier = Modifier.offset(x = 30.dp),
            )

            // Main FAB button
            FloatingActionButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                containerColor = fabContainer,
                contentColor = fabContent,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp,
                ),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Settings,
                    contentDescription = "Accessibility settings",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun AccessibilityPanel(
    preferencesManager: UserPreferencesManager,
    onDismiss: () -> Unit,
) {
    val themeMode by preferencesManager.themeMode.collectAsState()
    val fontScale by preferencesManager.fontScale.collectAsState()
    val highContrast by preferencesManager.highContrast.collectAsState()
    val reduceMotion by preferencesManager.reduceMotion.collectAsState()
    val callRingtoneUri by preferencesManager.callRingtoneUri.collectAsState()
    val requestRingtoneUri by preferencesManager.requestRingtoneUri.collectAsState()
    val context = LocalContext.current

    val callRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        preferencesManager.setCallRingtoneUri(uri)
    }

    val requestRingtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        preferencesManager.setRequestRingtoneUri(uri)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Text(
                text = "Display & Accessibility",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Customize your experience",
                fontSize = 13.sp,
                color = onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Theme mode
            SectionLabel("THEME")
            Spacer(Modifier.height(8.dp))
            ThemeSelector(
                selected = themeMode,
                onSelect = { preferencesManager.setThemeMode(it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = outlineColor)
            Spacer(Modifier.height(16.dp))

            // Font size
            SectionLabel("TEXT SIZE")
            Spacer(Modifier.height(8.dp))
            FontScaleSelector(
                selected = fontScale,
                onSelect = { preferencesManager.setFontScale(it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = outlineColor)
            Spacer(Modifier.height(12.dp))

            // High contrast toggle
            ToggleRow(
                label = "High contrast",
                subtitle = "Bolder text & borders",
                checked = highContrast,
                onCheckedChange = { preferencesManager.setHighContrast(it) },
            )

            Spacer(Modifier.height(8.dp))

            // Reduce motion toggle
            ToggleRow(
                label = "Reduce motion",
                subtitle = "Minimize animations",
                checked = reduceMotion,
                onCheckedChange = { preferencesManager.setReduceMotion(it) },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = outlineColor)
            Spacer(Modifier.height(12.dp))

            // Language
            SectionLabel("LANGUAGE")
            Spacer(Modifier.height(8.dp))
            LanguageRow(onDismiss = onDismiss)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = outlineColor)
            Spacer(Modifier.height(12.dp))

            // Sounds
            SectionLabel("SOUNDS")
            Spacer(Modifier.height(8.dp))

            // Incoming Call Ringtone
            RingtoneRow(
                label = "Incoming Call",
                currentUri = callRingtoneUri,
                onPick = {
                    callRingtoneLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Incoming Call Ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (callRingtoneUri != null) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, callRingtoneUri)
                            }
                        },
                    )
                },
                onReset = { preferencesManager.setCallRingtoneUri(null) },
            )

            Spacer(Modifier.height(8.dp))

            // Consultation Request Ringtone
            RingtoneRow(
                label = "Consultation Request",
                currentUri = requestRingtoneUri,
                onPick = {
                    requestRingtoneLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Request Ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (requestRingtoneUri != null) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, requestRingtoneUri)
                            }
                        },
                    )
                },
                onReset = { preferencesManager.setRequestRingtoneUri(null) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeOption("Auto", ThemeMode.SYSTEM, selected, onSelect, Modifier.weight(1f))
        ThemeOption("Light", ThemeMode.LIGHT, selected, onSelect, Modifier.weight(1f))
        ThemeOption("Dark", ThemeMode.DARK, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = mode == selected
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) BrandTeal else surfaceColor,
        shadowElevation = if (isSelected) 0.dp else 1.dp,
        border = if (!isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        } else {
            null
        },
        onClick = { onSelect(mode) },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FontScaleSelector(
    selected: FontScale,
    onSelect: (FontScale) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        FontScaleOption("A", 13.sp, FontScale.SMALL, selected, onSelect, Modifier.weight(1f))
        FontScaleOption("A", 16.sp, FontScale.NORMAL, selected, onSelect, Modifier.weight(1f))
        FontScaleOption("A", 20.sp, FontScale.LARGE, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun FontScaleOption(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    scale: FontScale,
    selected: FontScale,
    onSelect: (FontScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = scale == selected
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) BrandTeal.copy(alpha = 0.1f) else surfaceColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) BrandTeal else borderColor,
        ),
        onClick = { onSelect(scale) },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun RingtoneRow(
    label: String,
    currentUri: Uri?,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val ringtoneName = remember(currentUri) {
        if (currentUri == null) null
        else try {
            RingtoneManager.getRingtone(context, currentUri)?.getTitle(context)
        } catch (_: Exception) { null }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onPick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = ringtoneName ?: "System Default",
                    fontSize = 11.sp,
                    color = if (currentUri != null) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (currentUri != null) {
                TextButton(onClick = onReset) {
                    Text("Reset", fontSize = 11.sp, color = Color(0xFFDC2626))
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentCode = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) java.util.Locale.getDefault().language else locales[0]?.language ?: "en"
    }

    val languages = listOf(
        "en" to "English",
        "sw" to "Kiswahili",
        "fr" to "Francais",
        "es" to "Espanol",
        "ar" to "العربية",
        "hi" to "हिन्दी",
    )

    val currentName = languages.firstOrNull { it.first == currentCode }?.second ?: "English"

    Column {
        languages.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { (code, name) ->
                    val isSelected = code == currentCode
                    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    val borderColor = MaterialTheme.colorScheme.outline
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) BrandTeal else surfaceColor,
                        border = if (!isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                        } else {
                            null
                        },
                        onClick = {
                            if (code != currentCode) {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(code),
                                )
                                // Some OEM Android builds (Samsung One UI in
                                // particular) don't reliably auto-recreate the
                                // activity after setApplicationLocales — strings
                                // stay in the previous language until the next
                                // cold start. Force a recreate so the new locale
                                // applies immediately.
                                (context as? android.app.Activity)?.recreate()
                            }
                            onDismiss()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Pad last row if fewer than 3
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
