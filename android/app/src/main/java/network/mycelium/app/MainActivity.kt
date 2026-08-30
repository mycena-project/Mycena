// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import network.mycelium.app.bootstrap.OfflineBootstrap
import network.mycelium.app.stability.StabilityDiagnostics
import network.mycelium.app.ui.MainShell
import network.mycelium.app.ui.MyceliumTheme

class MainActivity : AppCompatActivity() {

    private var meshServiceStarted = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Mesh node already running; permission only affects chat/mail notifications.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        OfflineBootstrap.applySideloadAutoOnboarding(this)
        if (!prefs.getBoolean("onboarding_complete", false)) {
            val next =
                if (IdentityHelper.hasExistingIdentity(this)) {
                    WelcomeBackActivity::class.java
                } else {
                    OnboardingActivity::class.java
                }
            startActivity(Intent(this, next))
            finish()
            return
        }
        startMeshServiceIfNeeded()
        requestNotificationPermissionIfNeeded()
        setContent {
            MyceliumTheme {
                var nodeReady by remember { mutableStateOf(MeshService.isNodeReady()) }
                var nodeFailed by remember { mutableStateOf(MeshService.isNodeFailed()) }
                var timedOut by remember { mutableStateOf(false) }
                var waitGeneration by remember { mutableStateOf(0) }
                LaunchedEffect(waitGeneration) {
                    nodeReady = MeshService.isNodeReady()
                    nodeFailed = MeshService.isNodeFailed()
                    timedOut = false
                    val deadline = System.currentTimeMillis() + NODE_READY_TIMEOUT_MS
                    while (!nodeReady && !nodeFailed && System.currentTimeMillis() < deadline) {
                        delay(50)
                        nodeReady = MeshService.isNodeReady()
                        nodeFailed = MeshService.isNodeFailed()
                    }
                    if (!nodeReady && !nodeFailed) {
                        timedOut = true
                        StabilityDiagnostics.info("MainActivity.nodeWait", "timeout")
                    }
                }
                when {
                    nodeReady -> MainShell()
                    nodeFailed || timedOut ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.mesh_start_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(
                                onClick = {
                                    meshServiceStarted = false
                                    startMeshServiceIfNeeded()
                                    waitGeneration++
                                },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.mesh_start_retry))
                            }
                        }
                    else ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMeshServiceIfNeeded() {
        if (meshServiceStarted) {
            return
        }
        try {
            startForegroundService(Intent(this, MeshService::class.java))
            meshServiceStarted = true
        } catch (e: IllegalStateException) {
            StabilityDiagnostics.record("MainActivity.startForegroundService", e)
        }
    }

    companion object {
        private const val NODE_READY_TIMEOUT_MS = 15_000L
    }
}
