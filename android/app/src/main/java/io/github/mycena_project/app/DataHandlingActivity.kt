// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mycena_project.app.datahandling.DataHandlingCopy
import io.github.mycena_project.app.datahandling.DataHandlingCopyLoader
import io.github.mycena_project.app.datahandling.DataHandlingSection
import io.github.mycena_project.app.ui.MycenaTheme

class DataHandlingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MycenaTheme {
                DataHandlingScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHandlingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val copy = remember { DataHandlingCopyLoader.load(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(copy.title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = copy.closeButton,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                copy.intro,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(24.dp))
            copy.sections.forEach { section ->
                DataHandlingSectionBlock(section)
                Spacer(Modifier.height(20.dp))
            }
            Text(
                copy.footnote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(context, LegalDocumentActivity::class.java),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(copy.technicalPolicyLinkLabel)
            }
        }
    }
}

@Composable
private fun DataHandlingSectionBlock(section: DataHandlingSection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            section.heading,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        section.bullets.forEach { bullet ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    "•",
                    modifier = Modifier.padding(end = 10.dp, top = 2.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    bullet,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
        section.paragraphs.forEach { paragraph ->
            Text(
                paragraph,
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

fun openDataHandling(context: android.content.Context) {
    context.startActivity(Intent(context, DataHandlingActivity::class.java))
}
