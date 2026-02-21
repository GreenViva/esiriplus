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
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=${context.packageName}"),
                        )
                        context.startActivity(intent)
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
