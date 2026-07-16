// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

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

/** Opens the system share sheet with the installed APK (Nearby Share / Quick Share, Bluetooth, etc.). */
fun shareApkFile(
    context: Context,
    chooserTitle: String? = null,
) {
    val apk = File(context.applicationInfo.sourceDir)
    val authority = "${context.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, apk)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
