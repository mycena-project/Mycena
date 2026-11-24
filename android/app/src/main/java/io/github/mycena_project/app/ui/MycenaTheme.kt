// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors =
    lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF2E7D32),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFC8E6C9),
        secondary = androidx.compose.ui.graphics.Color(0xFF558B2F),
    )

private val DarkColors =
    darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF81C784),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF1B5E20),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF2E7D32),
        secondary = androidx.compose.ui.graphics.Color(0xFFAED581),
    )

@Composable
fun MycenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
