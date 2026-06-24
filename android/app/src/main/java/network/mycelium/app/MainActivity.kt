// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import network.mycelium.app.ui.MainShell

class MainActivity : AppCompatActivity() {

    private var meshServiceStarted = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Mesh node already running; permission only affects chat/mail notifications.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        // Start the mesh node before UI — ViewModels call Rust FFI immediately.
        startMeshServiceIfNeeded()
        requestNotificationPermissionIfNeeded()
        setContent {
            var nodeReady by remember { mutableStateOf(MeshService.isNodeReady()) }
            LaunchedEffect(Unit) {
                while (!nodeReady) {
                    if (MeshService.isNodeReady()) {
                        nodeReady = true
                    } else {
                        delay(50)
                    }
                }
            }
            if (nodeReady) {
                MainShell()
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
        startForegroundService(Intent(this, MeshService::class.java))
        meshServiceStarted = true
    }
}
