// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.mycena_project.bindings.ChatMessage
import io.github.mycena_project.bindings.GroupInfo
import io.github.mycena_project.bindings.mycena

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    val groups: StateFlow<List<GroupInfo>> =
        flow {
            while (true) {
                emit(mycena.listGroups())
                delay(2_000)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun groupChatHistory(groupId: String): Flow<List<ChatMessage>> =
        flow {
            while (true) {
                emit(mycena.groupChatHistory(groupId, 100u))
                delay(1_000)
            }
        }

    fun createGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.createGroup(name.trim())
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.deleteGroup(groupId)
        }
    }

    suspend fun importInviteAsync(json: String): Boolean =
        withContext(Dispatchers.IO) {
            mycena.importGroupInvite(json) != null
        }

    fun exportInvite(groupId: String): String = mycena.exportGroupInvite(groupId)

    fun sendGroupMessage(groupId: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.sendGroupMessage(groupId, body)
        }
    }

    fun sendLocation(
        context: android.content.Context,
        onLoading: (Boolean) -> Unit = {},
        onMessage: (String) -> Unit,
    ) {
        viewModelScope.launch {
            io.github.mycena_project.app.util.shareCurrentLocation(
                context = context,
                onLoading = onLoading,
                onDeliver = onMessage,
                onError = { msg ->
                    launch { _errors.emit(msg) }
                },
            )
        }
    }
}
