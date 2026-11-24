// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Single source for UI locale: Android [strings.xml] via AppCompat, plus JSON copy under assets/.
 *
 * Preference key [KEY_LANGUAGE] in [PREFS_NAME]:
 * - `system` — follow device locale
 * - `en`, `de`, `ar`, `ru`, `fa`, `zh-CN` — explicit override
 */
object LocalePreferences {
    const val PREFS_NAME = "settings"
    const val KEY_LANGUAGE = "language"
    const val LANGUAGE_SYSTEM = "system"

    fun applyStoredLanguage(context: Context) {
        val code =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM)
                ?: LANGUAGE_SYSTEM
        applyLanguageCode(code)
    }

    fun applyLanguageCode(code: String) {
        val locales =
            when (code) {
                LANGUAGE_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                "zh-CN" -> LocaleListCompat.forLanguageTags("zh-CN")
                else -> LocaleListCompat.forLanguageTags(code)
            }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Tag for `assets/onboarding/{tag}.json` and `assets/data-handling/{tag}.json`. */
    fun resolveAssetLocaleTag(context: Context): String {
        val pref =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM)
                ?: LANGUAGE_SYSTEM
        if (pref != LANGUAGE_SYSTEM) {
            return assetTagForLanguageCode(pref)
        }
        return assetTagForLocale(context.resources.configuration.locales[0])
    }

    private fun assetTagForLanguageCode(code: String): String =
        when (code) {
            "zh-CN" -> "zh"
            else -> code
        }

    private fun assetTagForLocale(locale: Locale): String =
        when (locale.language) {
            "de" -> "de"
            "ar" -> "ar"
            "ru" -> "ru"
            "fa" -> "fa"
            "zh" -> "zh"
            else -> "en"
        }
}
