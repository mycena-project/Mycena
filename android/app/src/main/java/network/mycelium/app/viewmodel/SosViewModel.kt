// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.content.Context
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.MeshEvents
import network.mycelium.bindings.ConnectivityMode
import network.mycelium.bindings.ResourceMapEntry
import network.mycelium.bindings.SosPost
import network.mycelium.bindings.VolunteerTaskEntry
import network.mycelium.bindings.mycelium

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _listenEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LISTEN_SOS, false),
    )
    val listenEnabled: StateFlow<Boolean> = _listenEnabled.asStateFlow()

    private val _crisisManualMode = MutableStateFlow(
        prefs.getInt(KEY_CRISIS_MANUAL_MODE, CRISIS_FOLLOW_AUTO),
    )
    val crisisManualMode: StateFlow<Int> = _crisisManualMode.asStateFlow()

    private val _crisisActive = MutableStateFlow(false)
    val crisisActive: StateFlow<Boolean> = _crisisActive.asStateFlow()

    private val _posts = MutableStateFlow<List<SosPost>>(emptyList())
    val posts: StateFlow<List<SosPost>> = _posts.asStateFlow()

    private val _resourceEntries = MutableStateFlow<List<ResourceMapEntry>>(emptyList())
    val resourceEntries: StateFlow<List<ResourceMapEntry>> = _resourceEntries.asStateFlow()

    private val _volunteerTasks = MutableStateFlow<List<VolunteerTaskEntry>>(emptyList())
    val volunteerTasks: StateFlow<List<VolunteerTaskEntry>> = _volunteerTasks.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MeshEvents.SOS, MeshEvents.CONNECTIVITY -> refreshAll()
            }
        }
    }

    init {
        syncListenToNode()
        syncCrisisManualToNode()
        refreshAll()
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter().apply {
                addAction(MeshEvents.SOS)
                addAction(MeshEvents.CONNECTIVITY)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        super.onCleared()
    }

    fun refreshAll() {
        refreshPosts()
        refreshResourceEntries()
        refreshVolunteerTasks()
        refreshCrisisActive()
    }

    fun refreshPosts() {
        _posts.value = runCatching { mycelium.listSosPosts() }.getOrDefault(emptyList())
    }

    fun refreshResourceEntries() {
        _resourceEntries.value =
            runCatching { mycelium.listResourceMapEntries() }.getOrDefault(emptyList())
    }

    fun refreshVolunteerTasks() {
        _volunteerTasks.value =
            runCatching { mycelium.listVolunteerTasks() }.getOrDefault(emptyList())
    }

    fun refreshCrisisActive() {
        _crisisActive.value = runCatching { mycelium.isCrisisModeActive() }.getOrDefault(false)
    }

    fun setListenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LISTEN_SOS, enabled).apply()
        _listenEnabled.value = enabled
        mycelium.setListenSos(enabled)
        triggerCrisisAutoRefresh()
    }

    fun setCrisisManualMode(mode: Int) {
        val clamped = mode.coerceIn(CRISIS_FOLLOW_AUTO, CRISIS_FORCE_OFF)
        prefs.edit().putInt(KEY_CRISIS_MANUAL_MODE, clamped).apply()
        _crisisManualMode.value = clamped
        mycelium.setCrisisManualMode(clamped.toUByte())
        refreshCrisisActive()
    }

    fun postSos(geohash: String, message: String) {
        viewModelScope.launch {
            val gh = geohash.trim()
            mycelium.postSos(gh, message.trim(), 3600u)
            rememberGeohashPrefix(gh)
            refreshAll()
            triggerCrisisAutoRefresh()
        }
    }

    fun postResourceMap(geohash: String, category: String, description: String) {
        viewModelScope.launch {
            val gh = geohash.trim()
            mycelium.postResourceMap(gh, category, description.trim(), 21_600u)
            rememberGeohashPrefix(gh)
            refreshAll()
        }
    }

    fun postVolunteerTask(
        geohash: String,
        category: String,
        description: String,
        slotsNeeded: Int,
    ) {
        viewModelScope.launch {
            val gh = geohash.trim()
            mycelium.postVolunteerTask(
                gh,
                category,
                description.trim(),
                slotsNeeded.coerceIn(1, 50).toUInt(),
                43_200u,
            )
            rememberGeohashPrefix(gh)
            refreshAll()
        }
    }

    fun claimVolunteerTask(taskId: String) {
        viewModelScope.launch {
            runCatching { mycelium.claimVolunteerTask(taskId.trim()) }
            refreshVolunteerTasks()
        }
    }

    private fun rememberGeohashPrefix(geohash: String) {
        val prefix = geohash.take(4).takeIf { it.length >= 4 } ?: return
        prefs.edit().putString(KEY_LOCAL_GEOHASH_PREFIX, prefix).apply()
        mycelium.setLocalGeohashPrefix(prefix)
    }

    private fun syncListenToNode() {
        if (_listenEnabled.value) {
            mycelium.setListenSos(true)
        }
    }

    private fun syncCrisisManualToNode() {
        mycelium.setCrisisManualMode(_crisisManualMode.value.toUByte())
        refreshCrisisActive()
    }

    private fun triggerCrisisAutoRefresh() {
        val internet =
            runCatching { mycelium.currentConnectivityMode() == ConnectivityMode.INTERNET }
                .getOrDefault(false)
        val prefix = prefs.getString(KEY_LOCAL_GEOHASH_PREFIX, null)?.takeIf { it.isNotBlank() }
        mycelium.refreshCrisisAuto(internet, prefix)
        refreshCrisisActive()
    }

    companion object {
        const val PREFS_NAME = SettingsViewModel.PREFS_NAME
        const val KEY_LISTEN_SOS = "listen_sos_enabled"
        const val KEY_CRISIS_MANUAL_MODE = "crisis_manual_mode"
        const val KEY_LOCAL_GEOHASH_PREFIX = "local_geohash_prefix"

        const val CRISIS_FOLLOW_AUTO = 0
        const val CRISIS_FORCE_ON = 1
        const val CRISIS_FORCE_OFF = 2

        fun refreshCrisisAutoFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val internet =
                runCatching { mycelium.currentConnectivityMode() == ConnectivityMode.INTERNET }
                    .getOrDefault(false)
            val prefix = prefs.getString(KEY_LOCAL_GEOHASH_PREFIX, null)?.takeIf { it.isNotBlank() }
            mycelium.refreshCrisisAuto(internet, prefix)
        }
    }
}
