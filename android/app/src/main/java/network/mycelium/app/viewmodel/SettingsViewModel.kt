// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.bindings.mycelium

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _bleBootstrapEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BLE_BOOTSTRAP, false),
    )
    val bleBootstrapEnabled: StateFlow<Boolean> = _bleBootstrapEnabled

    private val _relayKeyAgeSecs = MutableStateFlow(0L)
    val relayKeyAgeSecs: StateFlow<Long> = _relayKeyAgeSecs

    private val _bulletinSubscriptions = MutableStateFlow(loadBulletinSubscriptions())
    val bulletinSubscriptions: StateFlow<List<String>> = _bulletinSubscriptions

    private val _scopeKeys = MutableStateFlow(mycelium.listScopeKeys())
    val scopeKeys: StateFlow<List<network.mycelium.bindings.ScopeKeyInfo>> = _scopeKeys

    init {
        viewModelScope.launch {
            while (true) {
                _relayKeyAgeSecs.value = mycelium.relayKeypairAgeSecs().toLong()
                _bulletinSubscriptions.value = mycelium.getBulletinSubscriptions()
                _scopeKeys.value = mycelium.listScopeKeys()
                delay(5_000)
            }
        }
    }

    fun refreshScopeKeys() {
        _scopeKeys.value = mycelium.listScopeKeys()
    }

    fun addScopeKeyFromInvite(inviteJson: String) {
        mycelium.addScopeKey(inviteJson)
        refreshScopeKeys()
        val updated = mycelium.getBulletinSubscriptions()
        _bulletinSubscriptions.value = updated
        persistBulletinSubscriptions(updated)
    }

    fun createScopeKey(scope: String, displayName: String) {
        mycelium.createScopeKey(scope, displayName)
        refreshScopeKeys()
        val updated = mycelium.getBulletinSubscriptions()
        _bulletinSubscriptions.value = updated
        persistBulletinSubscriptions(updated)
    }

    fun deleteScopeKey(scope: String) {
        mycelium.deleteScopeKey(scope)
        refreshScopeKeys()
        val updated = mycelium.getBulletinSubscriptions()
        _bulletinSubscriptions.value = updated
        persistBulletinSubscriptions(updated)
    }

    fun exportScopeKey(scope: String): String = mycelium.exportScopeKey(scope)

    /** Turn on internet, relay discovery, and BLE discovery together (F0 max-connectivity). */
    fun applyMaxConnectivity(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAX_CONNECTIVITY, enabled).apply()
        if (!enabled) return
        prefs.edit()
            .putBoolean("internet_enabled", true)
            .putBoolean("rendezvous_enabled", true)
            .apply()
        mycelium.setInternetEnabled(true)
        mycelium.setRendezvousEnabled(true)
        setBleBootstrap(true)
    }

    fun isMaxConnectivityEnabled(): Boolean =
        prefs.getBoolean(KEY_MAX_CONNECTIVITY, false)

    fun setBleBootstrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_BOOTSTRAP, enabled).apply()
        _bleBootstrapEnabled.value = enabled
        val ctx = getApplication<Application>()
        try {
            ctx.startForegroundService(
                Intent(ctx, MeshService::class.java).setAction(MeshService.ACTION_UPDATE_BLE),
            )
        } catch (e: IllegalStateException) {
            network.mycelium.app.stability.StabilityDiagnostics.record(
                "SettingsViewModel.startForegroundService",
                e,
            )
        }
    }

    fun subscribeBulletin(scope: String) {
        val normalized = scope.trim().lowercase().replace(" ", "/")
        if (normalized.isBlank()) return
        mycelium.subscribeBulletinScope(normalized)
        val updated = mycelium.getBulletinSubscriptions()
        _bulletinSubscriptions.value = updated
        persistBulletinSubscriptions(updated)
    }

    fun unsubscribeBulletin(scope: String) {
        mycelium.unsubscribeBulletinScope(scope)
        val updated = mycelium.getBulletinSubscriptions()
        _bulletinSubscriptions.value = updated
        persistBulletinSubscriptions(updated)
    }

    private fun loadBulletinSubscriptions(): List<String> =
        prefs.getStringSet(KEY_BULLETIN_SUBSCRIPTIONS, emptySet())?.toList().orEmpty()

    private fun persistBulletinSubscriptions(scopes: List<String>) {
        prefs.edit().putStringSet(KEY_BULLETIN_SUBSCRIPTIONS, scopes.toSet()).apply()
    }

    fun localGeohashPrefix(): String =
        prefs.getString(SosViewModel.KEY_LOCAL_GEOHASH_PREFIX, "").orEmpty()

    fun setLocalGeohashPrefix(prefix: String) {
        val normalized = prefix.trim().takeIf { it.length >= 4 }
        prefs.edit()
            .putString(SosViewModel.KEY_LOCAL_GEOHASH_PREFIX, normalized)
            .apply()
        mycelium.setLocalGeohashPrefix(normalized)
        SosViewModel.refreshCrisisAutoFromPrefs(getApplication())
    }

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_BLE_BOOTSTRAP = "ble_bootstrap_enabled"
        const val KEY_BULLETIN_SUBSCRIPTIONS = "bulletin_subscriptions"
        const val KEY_CROWD_MODE = "crowd_mode_enabled"
        const val KEY_MAX_CONNECTIVITY = "max_connectivity_enabled"
    }
}
