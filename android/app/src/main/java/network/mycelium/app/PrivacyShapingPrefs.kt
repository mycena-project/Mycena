// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.SharedPreferences
import network.mycelium.bindings.PrivacyShapingConfig
import network.mycelium.bindings.PrivacyShapingProfile
import network.mycelium.bindings.mycelium

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
        val base = mycelium.defaultPrivacyShapingConfig(profile)
        return base.copy(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            coverTrafficEnabled = prefs.getBoolean(KEY_COVER, false),
        )
    }

    fun applyFromPrefs(prefs: SharedPreferences) {
        mycelium.setPrivacyShaping(configFromPrefs(prefs))
    }
}
