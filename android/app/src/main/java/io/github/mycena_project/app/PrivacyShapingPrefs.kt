// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app

import android.content.SharedPreferences
import io.github.mycena_project.bindings.PrivacyShapingConfig
import io.github.mycena_project.bindings.PrivacyShapingProfile
import io.github.mycena_project.bindings.mycena

object PrivacyShapingPrefs {
    const val KEY_ENABLED = "privacy_shaping_enabled"
    const val KEY_COVER = "privacy_shaping_cover"
    const val KEY_PROFILE = "privacy_shaping_profile"

    fun profileFromName(name: String?): PrivacyShapingProfile =
        when (name) {
            "Fast" -> PrivacyShapingProfile.FAST
            "Paranoid" -> PrivacyShapingProfile.PARANOID
            else -> PrivacyShapingProfile.BALANCED
        }

    fun profileName(profile: PrivacyShapingProfile): String =
        when (profile) {
            PrivacyShapingProfile.FAST -> "Fast"
            PrivacyShapingProfile.BALANCED -> "Balanced"
            PrivacyShapingProfile.PARANOID -> "Paranoid"
        }

    fun configFromPrefs(prefs: SharedPreferences): PrivacyShapingConfig {
        val profile = profileFromName(prefs.getString(KEY_PROFILE, "Balanced"))
        val base = mycena.defaultPrivacyShapingConfig(profile)
        return base.copy(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            coverTrafficEnabled = prefs.getBoolean(KEY_COVER, false),
        )
    }

    fun applyFromPrefs(prefs: SharedPreferences) {
        mycena.setPrivacyShaping(configFromPrefs(prefs))
    }
}
