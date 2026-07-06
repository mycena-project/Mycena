// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.app.Application
import network.mycelium.app.i18n.LocalePreferences

class MyceliumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalePreferences.applyStoredLanguage(this)
    }
}
