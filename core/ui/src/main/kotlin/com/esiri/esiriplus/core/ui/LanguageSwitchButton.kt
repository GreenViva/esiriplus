package com.esiri.esiriplus.core.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.esiri.esiriplus.core.common.locale.supportedLanguages
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)

/**
 * Compact language-switch button. Tapping opens a dropdown of recommended
 * languages (English, Kiswahili). Selecting a different language applies
 * it immediately via [AppCompatDelegate.setApplicationLocales].
 *
 * @param showLabel if true, displays the current language name next to the icon
 * @param iconTint tint for the globe icon
 */
@Composable
fun LanguageSwitchButton(
    showLabel: Boolean = false,
    iconTint: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    val currentCode = remember {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language ?: "en"
    }
    val recommendedLanguages = remember {
        supportedLanguages.filter { it.isRecommended }
    }
    val currentLanguage = remember(currentCode) {
        recommendedLanguages.firstOrNull { it.code == currentCode }
    }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (showLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_language),
                    contentDescription = stringResource(R.string.cd_change_language),
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = currentLanguage?.displayName ?: "English",
                    fontSize = 14.sp,
                    color = Color.Black,
                )
            }
        } else {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_language),
                    contentDescription = stringResource(R.string.cd_change_language),
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            recommendedLanguages.forEach { language ->
                val isSelected = language.code == currentCode
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = language.nativeName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.Black,
                            )
                            if (language.displayName != language.nativeName) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "(${language.displayName})",
                                    fontSize = 12.sp,
                                    color = Color.Black.copy(alpha = 0.6f),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (language.code != currentCode) {
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(language.code),
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = BrandTeal,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
