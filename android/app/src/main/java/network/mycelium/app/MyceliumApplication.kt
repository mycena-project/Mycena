// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.app.Application
import network.mycelium.app.i18n.LocalePreferences
import network.mycelium.app.stability.AppLifecycleTracker
import network.mycelium.app.stability.StabilityDiagnostics

class MyceliumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StabilityDiagnostics.installUncaughtHandler()
        AppLifecycleTracker.install(this)
        LocalePreferences.applyStoredLanguage(this)
    }
}
