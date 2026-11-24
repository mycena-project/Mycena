// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.datahandling

import android.content.Context
import org.json.JSONObject
import io.github.mycena_project.app.i18n.LocalePreferences

data class DataHandlingSection(
    val heading: String,
    val bullets: List<String>,
    val paragraphs: List<String>,
)

data class DataHandlingCopy(
    val locale: String,
    val title: String,
    val intro: String,
    val sections: List<DataHandlingSection>,
    val footnote: String,
    val technicalPolicyLinkLabel: String,
    val closeButton: String,
)

object DataHandlingCopyLoader {
    fun load(context: Context): DataHandlingCopy {
        val localeTag = LocalePreferences.resolveAssetLocaleTag(context)
        val json =
            readAssetJson(context, "data-handling/$localeTag.json")
                ?: readAssetJson(context, "data-handling/en.json")
                ?: error("missing data-handling copy in assets/data-handling/")
        return parse(json)
    }

    internal fun parseFromJson(raw: String): DataHandlingCopy = parse(JSONObject(raw))

    private fun readAssetJson(
        context: Context,
        path: String,
    ): JSONObject? =
        runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.mapCatching { JSONObject(it) }.getOrNull()

    private fun parse(json: JSONObject): DataHandlingCopy {
        val sections =
            json.getJSONArray("sections").let { arr ->
                (0 until arr.length()).map { i ->
                    val item = arr.getJSONObject(i)
                    val bullets =
                        item.optJSONArray("bullets")?.let { b ->
                            (0 until b.length()).map { b.getString(it) }
                        } ?: emptyList()
                    val paragraphs =
                        item.optJSONArray("paragraphs")?.let { p ->
                            (0 until p.length()).map { p.getString(it) }
                        } ?: emptyList()
                    DataHandlingSection(
                        heading = item.getString("heading"),
                        bullets = bullets,
                        paragraphs = paragraphs,
                    )
                }
            }
        return DataHandlingCopy(
            locale = json.optString("locale", "en"),
            title = json.getString("title"),
            intro = json.getString("intro"),
            sections = sections,
            footnote = json.getString("footnote"),
            technicalPolicyLinkLabel = json.getString("technical_policy_link_label"),
            closeButton = json.getString("close_button"),
        )
    }
}
