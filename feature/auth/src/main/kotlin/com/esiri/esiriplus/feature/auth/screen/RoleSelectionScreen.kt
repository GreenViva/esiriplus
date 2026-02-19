package com.esiri.esiriplus.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onDoctorSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome to eSIRI+",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "How would you like to continue?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onPatientSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("I'm a Patient")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onDoctorSelected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("I'm a Doctor")
        }
    }
}
