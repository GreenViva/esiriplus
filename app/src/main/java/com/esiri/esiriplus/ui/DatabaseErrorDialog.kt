package com.esiri.esiriplus.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.esiri.esiriplus.R
import com.esiri.esiriplus.core.database.init.DatabaseInitError

@Composable
fun DatabaseErrorDialog(error: DatabaseInitError) {
    val context = LocalContext.current

    when (error) {
        is DatabaseInitError.VersionDowngrade -> {
            AlertDialog(
                onDismissRequest = { /* non-dismissible */ },
                title = { Text(stringResource(R.string.db_error_update_required_title)) },
                text = { Text(stringResource(R.string.db_error_update_required_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        // Try Play Store first, fall back to package installer settings
                        val marketIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${context.packageName}"),
                        )
                        val resolvedActivity = marketIntent.resolveActivity(context.packageManager)
                        if (resolvedActivity != null) {
                            context.startActivity(marketIntent)
                        } else {
                            // No Play Store — open app details so user can uninstall/reinstall
                            val settingsIntent = Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(settingsIntent)
                        }
                    }) {
                        Text(stringResource(R.string.db_error_update_button))
                    }
                },
            )
        }
        else -> {
            AlertDialog(
                onDismissRequest = { /* non-dismissible */ },
                title = { Text(stringResource(R.string.db_error_failed_title)) },
                text = { Text(stringResource(R.string.db_error_failed_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        (context as? Activity)?.finishAffinity()
                    }) {
                        Text(stringResource(R.string.db_error_ok_button))
                    }
                },
            )
        }
    }
}
