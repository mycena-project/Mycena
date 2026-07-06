// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.bindings.UserTrustSummary
import network.mycelium.bindings.mycelium

class UserTrustViewModel : ViewModel() {
    private val _summary = MutableStateFlow<UserTrustSummary?>(null)
    val summary: StateFlow<UserTrustSummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            while (!MeshService.isNodeReady()) {
                delay(50)
            }
            while (isActive) {
                runCatching { mycelium.userTrustSummary() }
                    .onSuccess { _summary.value = it }
                delay(15_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { mycelium.userTrustSummary() }
                .onSuccess { _summary.value = it }
        }
    }
}
