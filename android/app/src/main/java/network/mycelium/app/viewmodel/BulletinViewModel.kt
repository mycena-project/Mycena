// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.BulletinPost
import network.mycelium.bindings.mycelium

class BulletinViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _subscriptions = MutableStateFlow(loadSubscriptions())
    val subscriptions: StateFlow<List<String>> = _subscriptions.asStateFlow()

    private val _activeScope = MutableStateFlow(_subscriptions.value.firstOrNull() ?: DEFAULT_SCOPE)
    val activeScope: StateFlow<String> = _activeScope.asStateFlow()

    private val _posts = MutableStateFlow<List<BulletinPost>>(emptyList())
    val posts: StateFlow<List<BulletinPost>> = _posts.asStateFlow()

    init {
        ensureSubscribed(_activeScope.value)
        reloadPosts()
        viewModelScope.launch {
            appContext.meshEvents().collect {
                if (it == MeshEvent.Bulletin) {
                    reloadPosts()
                }
            }
        }
    }

    fun setActiveScope(scope: String) {
        val normalized = normalizeScope(scope)
        if (normalized.isBlank()) return
        _activeScope.value = normalized
        ensureSubscribed(normalized)
        reloadPosts()
    }

    fun addScope(scope: String) {
        val normalized = normalizeScope(scope)
        if (normalized.isBlank()) return
        ensureSubscribed(normalized)
        _activeScope.value = normalized
        reloadPosts()
    }

    fun removeScope(scope: String) {
        mycelium.unsubscribeBulletinScope(scope)
        val updated = mycelium.getBulletinSubscriptions()
        persistSubscriptions(updated)
        _subscriptions.value = updated
        if (_activeScope.value == scope) {
            _activeScope.value = updated.firstOrNull() ?: DEFAULT_SCOPE
        }
        reloadPosts()
    }

    fun post(title: String, body: String) {
        val scope = _activeScope.value
        if (scope.isBlank() || title.isBlank() || body.isBlank()) return
        ensureSubscribed(scope)
        mycelium.postBulletin(scope, title.trim(), body.trim(), 86_400u)
        reloadPosts()
    }

    fun reloadPosts() {
        val scope = _activeScope.value
        _subscriptions.value = mycelium.getBulletinSubscriptions()
        _posts.value =
            runCatching { mycelium.bulletinsForScope(scope) }
                .getOrElse { emptyList() }
                .sortedByDescending { it.timestampMs }
    }

    private fun ensureSubscribed(scope: String) {
        mycelium.subscribeBulletinScope(scope)
        val updated = mycelium.getBulletinSubscriptions()
        persistSubscriptions(updated)
        _subscriptions.value = updated
    }

    private fun loadSubscriptions(): List<String> =
        prefs.getStringSet(KEY_BULLETIN_SUBSCRIPTIONS, emptySet())?.toList().orEmpty()
            .ifEmpty { mycelium.getBulletinSubscriptions() }

    private fun persistSubscriptions(scopes: List<String>) {
        prefs.edit().putStringSet(KEY_BULLETIN_SUBSCRIPTIONS, scopes.toSet()).apply()
    }

    private fun normalizeScope(raw: String): String =
        raw.trim().lowercase().replace(" ", "/")

    companion object {
        const val PREFS_NAME = "settings"
        const val KEY_BULLETIN_SUBSCRIPTIONS = "bulletin_subscriptions"
        const val DEFAULT_SCOPE = "mycelium/general"
    }
}
