// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
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
import network.mycelium.bindings.SosPost
import network.mycelium.bindings.mycelium

class SosViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _listenEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LISTEN_SOS, false),
    )
    val listenEnabled: StateFlow<Boolean> = _listenEnabled.asStateFlow()

    private val _posts = MutableStateFlow<List<SosPost>>(emptyList())
    val posts: StateFlow<List<SosPost>> = _posts.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MeshEvents.SOS) {
                refreshPosts()
            }
        }
    }

    init {
        syncListenToNode()
        refreshPosts()
        ContextCompat.registerReceiver(
            application,
            receiver,
            IntentFilter(MeshEvents.SOS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
        super.onCleared()
    }

    fun refreshPosts() {
        _posts.value = runCatching { mycelium.listSosPosts() }.getOrDefault(emptyList())
    }

    fun setListenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LISTEN_SOS, enabled).apply()
        _listenEnabled.value = enabled
        mycelium.setListenSos(enabled)
    }

    fun postSos(geohash: String, message: String) {
        viewModelScope.launch {
            mycelium.postSos(geohash.trim(), message.trim(), 3600u)
            refreshPosts()
        }
    }

    private fun syncListenToNode() {
        if (_listenEnabled.value) {
            mycelium.setListenSos(true)
        }
    }

    companion object {
        const val PREFS_NAME = SettingsViewModel.PREFS_NAME
        const val KEY_LISTEN_SOS = "listen_sos_enabled"
    }
}
