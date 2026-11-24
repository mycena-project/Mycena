// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.mycena_project.app.MeshService
import io.github.mycena_project.bindings.UserTrustSummary
import io.github.mycena_project.bindings.mycena

class UserTrustViewModel : ViewModel() {
    private val _summary = MutableStateFlow<UserTrustSummary?>(null)
    val summary: StateFlow<UserTrustSummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch {
            while (!MeshService.isNodeReady()) {
                delay(50)
            }
            while (isActive) {
                runCatching { mycena.userTrustSummary() }
                    .onSuccess { _summary.value = it }
                delay(15_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { mycena.userTrustSummary() }
                .onSuccess { _summary.value = it }
        }
    }
}
