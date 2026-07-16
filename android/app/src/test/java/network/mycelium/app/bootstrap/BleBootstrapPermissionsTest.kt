// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleBootstrapPermissionsTest {
    @Test
    fun requiredPermissionsMatchSdkLevel() {
        val required = BleBootstrapPermissions.required
        assertTrue(required.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertEquals(
                setOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ),
                required.toSet(),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals(
                setOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                required.toSet(),
            )
        } else {
            assertEquals(
                setOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                required.toSet(),
            )
        }
    }
}
