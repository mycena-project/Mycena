// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import network.mycelium.app.bootstrap.ShareApkArtifact

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

/**
 * Opens the system share sheet with a cache copy of the Technical Beta APK
 * (Nearby Share / Quick Share, Bluetooth, etc.).
 *
 * @throws ShareApkArtifact.SplitApkNotSupportedException if this install is split (Play).
 * @throws ShareApkArtifact.DebugApkNotShareableException if this is a debug/development APK.
 * @throws ShareApkArtifact.NotTechnicalBetaException if this is not a Technical Beta release build.
 * @throws ShareApkArtifact.IncompleteApkException if the installed package is missing or empty.
 */
fun shareApkFile(
    context: Context,
    chooserTitle: String? = null,
) {
    val apk = ShareApkArtifact.materializeInstalledApk(context)
    val authority = "${context.packageName}.fileprovider"
    val uri =
        try {
            FileProvider.getUriForFile(context, authority, apk)
        } catch (e: IllegalArgumentException) {
            network.mycelium.app.stability.StabilityDiagnostics.record("shareApkFile.fileProvider", e)
            throw e
        }
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
