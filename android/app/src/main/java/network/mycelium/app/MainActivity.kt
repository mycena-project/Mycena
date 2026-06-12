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
import androidx.core.content.ContextCompat
import network.mycelium.app.ui.MainShell

class MainActivity : AppCompatActivity() {

    private var meshServiceStarted = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startMeshServiceIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_complete", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        ensureNotificationPermissionThenStartService()
        setContent {
            MainShell()
        }
    }

    private fun ensureNotificationPermissionThenStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> startMeshServiceIfNeeded()
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMeshServiceIfNeeded()
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
