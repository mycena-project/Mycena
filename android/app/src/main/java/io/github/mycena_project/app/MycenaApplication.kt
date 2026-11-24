// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app

import android.app.Application
import io.github.mycena_project.app.i18n.LocalePreferences
import io.github.mycena_project.app.stability.AppLifecycleTracker
import io.github.mycena_project.app.stability.StabilityDiagnostics

class MycenaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StabilityDiagnostics.installUncaughtHandler()
        AppLifecycleTracker.install(this)
        LocalePreferences.applyStoredLanguage(this)
    }
}
