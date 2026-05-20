package network.mycelium.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import network.mycelium.app.MeshService

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _bleBootstrapEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BLE_BOOTSTRAP, false),
    )
    val bleBootstrapEnabled: StateFlow<Boolean> = _bleBootstrapEnabled

    fun setBleBootstrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_BOOTSTRAP, enabled).apply()
        _bleBootstrapEnabled.value = enabled
        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, MeshService::class.java).setAction(MeshService.ACTION_UPDATE_BLE),
        )
    }

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_BLE_BOOTSTRAP = "ble_bootstrap_enabled"
    }
}
