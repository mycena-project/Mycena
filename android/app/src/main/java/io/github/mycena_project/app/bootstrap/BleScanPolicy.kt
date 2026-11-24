// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings

/**
 * Portable BLE scan settings. [ScanSettings.CALLBACK_TYPE_FIRST_MATCH] is optional hardware
 * (`ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED`).
 */
object BleScanPolicy {
    const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4

    fun portableCallbackType(): Int = ScanSettings.CALLBACK_TYPE_ALL_MATCHES

    fun firstMatchCallbackType(): Int = ScanSettings.CALLBACK_TYPE_FIRST_MATCH

    fun shouldFallbackToAllMatches(errorCode: Int): Boolean =
        errorCode == SCAN_FAILED_FEATURE_UNSUPPORTED ||
            errorCode == ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
}
