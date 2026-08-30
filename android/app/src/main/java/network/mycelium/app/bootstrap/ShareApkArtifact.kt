// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
import java.security.MessageDigest
import network.mycelium.app.BuildConfig

/**
 * Phone-to-phone / Quick Share APK: only a **complete Technical Beta** install.
 *
 * Refuses debug builds, Play split installs, and incomplete packages.
 * Does not silently fall back to whatever APK happens to be installed.
 */
object ShareApkArtifact {

    const val CACHE_SUBDIR = "apk-share"
    const val CACHE_FILENAME = "mycelium.apk"

    enum class RejectReason {
        DEBUG_BUILD,
        NOT_TECHNICAL_BETA,
        SPLIT_INSTALL,
        INCOMPLETE,
    }

    class SplitApkNotSupportedException :
        IllegalStateException("split_apk_unsupported")

    class DebugApkNotShareableException :
        IllegalStateException("debug_apk_not_shareable")

    class NotTechnicalBetaException :
        IllegalStateException("not_technical_beta")

    class IncompleteApkException :
        IllegalStateException("incomplete_apk")

    class ApkCopyFailedException(cause: Throwable) :
        IllegalStateException("apk_copy_failed", cause)

    fun isSplitInstall(splitSourceDirs: Array<String>?): Boolean =
        !splitSourceDirs.isNullOrEmpty()

    fun isDebuggable(appInfo: ApplicationInfo): Boolean =
        appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Pure gate used by unit tests. Production calls [requireShareableTechnicalBeta].
     */
    fun rejectReason(
        debuggable: Boolean,
        technicalBeta: Boolean,
        splitSourceDirs: Array<String>?,
        sourceExistsAndNonEmpty: Boolean,
    ): RejectReason? {
        if (debuggable) return RejectReason.DEBUG_BUILD
        if (!technicalBeta) return RejectReason.NOT_TECHNICAL_BETA
        if (isSplitInstall(splitSourceDirs)) return RejectReason.SPLIT_INSTALL
        if (!sourceExistsAndNonEmpty) return RejectReason.INCOMPLETE
        return null
    }

    fun cacheFile(context: Context): File =
        File(File(context.cacheDir, CACHE_SUBDIR), CACHE_FILENAME)

    /**
     * Copies the installed APK into cache for FileProvider and HTTP share.
     * @throws DebugApkNotShareableException if this is a development/debug install
     * @throws NotTechnicalBetaException if this is not a Technical Beta release build
     * @throws SplitApkNotSupportedException if this install is a split package
     * @throws IncompleteApkException if sourceDir is missing or empty
     */
    fun materializeInstalledApk(context: Context): File {
        val info = context.applicationInfo
        val src = File(info.sourceDir)
        val complete = src.isFile && src.length() > 0L
        when (
            rejectReason(
                debuggable = isDebuggable(info),
                technicalBeta = BuildConfig.TECHNICAL_BETA,
                splitSourceDirs = info.splitSourceDirs,
                sourceExistsAndNonEmpty = complete,
            )
        ) {
            RejectReason.DEBUG_BUILD -> throw DebugApkNotShareableException()
            RejectReason.NOT_TECHNICAL_BETA -> throw NotTechnicalBetaException()
            RejectReason.SPLIT_INSTALL -> throw SplitApkNotSupportedException()
            RejectReason.INCOMPLETE -> throw IncompleteApkException()
            null -> Unit
        }
        val dest = cacheFile(context)
        dest.parentFile?.mkdirs()
        try {
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            throw ApkCopyFailedException(e)
        }
        if (!dest.isFile || dest.length() == 0L) {
            throw IncompleteApkException()
        }
        return dest
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
