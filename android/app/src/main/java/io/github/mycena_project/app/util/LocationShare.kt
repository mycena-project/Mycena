// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.mycena_project.app.R

suspend fun shareCurrentLocation(
    context: Context,
    onLoading: (Boolean) -> Unit,
    onDeliver: (String) -> Unit,
    onError: (String) -> Unit,
) {
    withContext(Dispatchers.Main) { onLoading(true) }
    try {
        val locationMsg = LocationHelper.getCurrentLocationMessage(context)
        if (locationMsg != null) {
            withContext(Dispatchers.Main) { onDeliver(locationMsg) }
        } else {
            withContext(Dispatchers.Main) {
                onError(context.getString(R.string.error_location_unavailable))
            }
        }
    } finally {
        withContext(Dispatchers.Main) { onLoading(false) }
    }
}
