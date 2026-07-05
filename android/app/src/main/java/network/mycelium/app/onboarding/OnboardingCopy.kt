// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.onboarding

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import kotlin.random.Random

data class PositioningVariant(
    val id: String,
    val headline: String,
)

data class FaqItem(
    val question: String,
    val answer: String,
)

data class OnboardingCopy(
    val locale: String,
    val positioningDefaultVariant: String,
    val positioningVariants: Map<String, PositioningVariant>,
    val whyTitle: String,
    val whyBullets: List<String>,
    val faqTitle: String,
    val faqItems: List<FaqItem>,
    val completionTitle: String,
    val completionBody: String,
    val completionFreshIdentityNote: String,
    val completionNameHint: String,
    val completionNamePlaceholder: String,
    val completionNamePrivacyNote: String,
    val completionStartButton: String,
    val actionNext: String,
    val actionBack: String,
)

object OnboardingCopyLoader {
    private const val PREFS_APP = "app"
    private const val KEY_POSITIONING_VARIANT = "onboarding_positioning_variant"

    fun load(context: Context): OnboardingCopy {
        val localeTag = resolveLocaleTag()
        val json =
            readAssetJson(context, "onboarding/$localeTag.json")
                ?: readAssetJson(context, "onboarding/en.json")
                ?: error("missing onboarding copy in assets/onboarding/")
        return parse(json)
    }

    /** Visible for unit tests — production UI loads from assets via [load]. */
    internal fun parseFromJson(raw: String): OnboardingCopy = parse(JSONObject(raw))

    fun resolvePositioningVariant(
        context: Context,
        copy: OnboardingCopy,
    ): PositioningVariant {
        val prefs = context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_POSITIONING_VARIANT, null)
        if (stored != null) {
            copy.positioningVariants[stored]?.let { return it }
        }

        val keys = copy.positioningVariants.keys.toList()
        val chosenId =
            when {
                keys.isEmpty() -> copy.positioningDefaultVariant
                keys.size == 1 -> keys.first()
                else -> keys.random(Random.Default)
            }
        val variant =
            copy.positioningVariants[chosenId]
                ?: copy.positioningVariants[copy.positioningDefaultVariant]
                ?: copy.positioningVariants.values.first()

        prefs.edit().putString(KEY_POSITIONING_VARIANT, variant.id).apply()
        return variant
    }

    private fun resolveLocaleTag(): String {
        val locale = Locale.getDefault()
        return when (locale.language) {
            "de" -> "de"
            "zh" -> if (locale.country.equals("CN", ignoreCase = true)) "zh-CN" else "zh"
            else -> locale.language.takeIf { it.length == 2 } ?: "en"
        }
    }

    private fun readAssetJson(
        context: Context,
        path: String,
    ): JSONObject? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.mapCatching { JSONObject(it) }.getOrNull()

    private fun parse(json: JSONObject): OnboardingCopy {
        val positioning = json.getJSONObject("positioning")
        val variantsJson = positioning.getJSONObject("variants")
        val variants =
            variantsJson.keys().asSequence().associateWith { key ->
                val item = variantsJson.getJSONObject(key)
                PositioningVariant(
                    id = key,
                    headline = item.getString("headline"),
                )
            }

        val why = json.getJSONObject("why")
        val bullets =
            why.getJSONArray("bullets").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }

        val faq = json.getJSONObject("faq")
        val faqItems =
            faq.getJSONArray("items").let { arr ->
                (0 until arr.length()).map { i ->
                    val item = arr.getJSONObject(i)
                    FaqItem(
                        question = item.getString("question"),
                        answer = item.getString("answer"),
                    )
                }
            }

        val completion = json.getJSONObject("completion")
        val actions = json.getJSONObject("actions")

        return OnboardingCopy(
            locale = json.optString("locale", "en"),
            positioningDefaultVariant = positioning.getString("default_variant"),
            positioningVariants = variants,
            whyTitle = why.getString("title"),
            whyBullets = bullets,
            faqTitle = faq.getString("title"),
            faqItems = faqItems,
            completionTitle = completion.getString("title"),
            completionBody = completion.getString("body"),
            completionFreshIdentityNote = completion.optString("fresh_identity_note", ""),
            completionNameHint = completion.getString("name_hint"),
            completionNamePlaceholder = completion.getString("name_placeholder"),
            completionNamePrivacyNote = completion.getString("name_privacy_note"),
            completionStartButton = completion.getString("start_button"),
            actionNext = actions.getString("next"),
            actionBack = actions.getString("back"),
        )
    }
}
