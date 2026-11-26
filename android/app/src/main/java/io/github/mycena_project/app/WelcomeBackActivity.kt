// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mycena_project.app.performFreshIdentity

class WelcomeBackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedName =
            settingsPrefs.getString("display_name", null)?.takeIf { it.isNotBlank() }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WelcomeBackScreen(
                        displayName = savedName,
                        onContinue = {
                            getSharedPreferences("app", MODE_PRIVATE)
                                .edit()
                                .putBoolean("onboarding_complete", true)
                                .apply()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onStartFresh = {
                            performFreshIdentity(this)
                            getSharedPreferences("app", MODE_PRIVATE)
                                .edit()
                                .putBoolean("onboarding_complete", false)
                                .apply()
                            startActivity(Intent(this, OnboardingActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBackScreen(
    displayName: String?,
    onContinue: () -> Unit,
    onStartFresh: () -> Unit,
) {
    var showFreshConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.mycena_mark),
            contentDescription = stringResource(R.string.app_name),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.welcome_back_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (displayName != null) {
                stringResource(R.string.welcome_back_body_named, displayName)
            } else {
                stringResource(R.string.welcome_back_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_back_continue))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { showFreshConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.welcome_back_fresh))
        }
    }

    if (showFreshConfirm) {
        AlertDialog(
            onDismissRequest = { showFreshConfirm = false },
            title = { Text(stringResource(R.string.fresh_identity_confirm_title)) },
            text = { Text(stringResource(R.string.fresh_identity_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showFreshConfirm = false
                        onStartFresh()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.fresh_identity_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreshConfirm = false }) {
                    Text(stringResource(R.string.fresh_identity_confirm_no))
                }
            },
        )
    }
}
