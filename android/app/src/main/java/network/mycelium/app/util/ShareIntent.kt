// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.util

import android.content.Context
import android.content.Intent

fun sharePlainText(
    context: Context,
    text: String,
    chooserTitle: String? = null,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
