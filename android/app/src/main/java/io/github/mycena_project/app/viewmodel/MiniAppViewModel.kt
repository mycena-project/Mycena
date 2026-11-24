// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.mycena_project.app.R
import io.github.mycena_project.app.MeshEvent
import io.github.mycena_project.app.meshEvents
import io.github.mycena_project.app.miniapp.MiniAppGrants
import io.github.mycena_project.bindings.AppStoreListing
import io.github.mycena_project.bindings.MiniAppInfo
import io.github.mycena_project.bindings.MiniAppInstallPreview
import io.github.mycena_project.bindings.mycena
import uniffi.mycena.MycenaException

data class PendingMiniAppInstall(
    val bytes: ByteArray,
    val preview: MiniAppInstallPreview,
)

private fun installErrorMessage(context: Context, e: MycenaException): String =
    when (e) {
        is MycenaException.InstallException -> e.`detail`
        else -> e.message ?: context.getString(R.string.miniapp_install_failed)
    }

class MiniAppViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val _installed = MutableStateFlow<List<MiniAppInfo>>(emptyList())
    val installed: StateFlow<List<MiniAppInfo>> = _installed.asStateFlow()

    private val _listings = MutableStateFlow<List<AppStoreListing>>(emptyList())
    val listings: StateFlow<List<AppStoreListing>> = _listings.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _pendingInstall = MutableStateFlow<PendingMiniAppInstall?>(null)
    val pendingInstall: StateFlow<PendingMiniAppInstall?> = _pendingInstall.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            appContext.meshEvents().collect {
                if (it == MeshEvent.AppStore) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps =
                try {
                    mycena.listInstalledApps()
                } catch (_: Exception) {
                    emptyList()
                }
            val rows =
                try {
                    mycena.browseAppStore()
                } catch (_: Exception) {
                    emptyList()
                }
            withContext(Dispatchers.Main) {
                _installed.value = apps
                _listings.value = rows
            }
        }
    }

    fun installFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes =
                    context.contentResolver.openInputStream(uri)?.use { ins -> ins.readBytes() }
                        ?: run {
                            withContext(Dispatchers.Main) {
                                _toast.value =
                                    appContext.getString(R.string.miniapp_read_bundle_failed)
                            }
                            return@launch
                        }
                val preview = mycena.previewMiniappInstall(bytes)
                withContext(Dispatchers.Main) {
                    _pendingInstall.value = PendingMiniAppInstall(bytes, preview)
                }
            } catch (e: MycenaException) {
                withContext(Dispatchers.Main) {
                    _toast.value = installErrorMessage(appContext, e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value =
                        e.message ?: appContext.getString(R.string.miniapp_install_failed)
                }
            }
        }
    }

    fun confirmPendingInstall(listingAppId: String?, allowDowngrade: Boolean = false) {
        val pending = _pendingInstall.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allowSideload = listingAppId == null
                val info =
                    mycena.installApp(
                        pending.bytes,
                        listingAppId,
                        allowSideload,
                        allowDowngrade,
                    )
                MiniAppGrants.recordInstallTrust(
                    appContext,
                    info.id,
                    pending.preview.trustLevel,
                )
                withContext(Dispatchers.Main) {
                    _pendingInstall.value = null
                    _toast.value =
                        appContext.getString(R.string.miniapp_installed_toast, info.name)
                    refresh()
                }
            } catch (e: MycenaException) {
                withContext(Dispatchers.Main) {
                    _toast.value = installErrorMessage(appContext, e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value =
                        e.message ?: appContext.getString(R.string.miniapp_install_failed)
                }
            }
        }
    }

    fun dismissPendingInstall() {
        _pendingInstall.value = null
    }

    fun listingAppIdForPending(): String? {
        val pending = _pendingInstall.value ?: return null
        if (pending.preview.trustLevel == "verified_listing") {
            return pending.preview.manifest.id
        }
        return null
    }

    fun uninstall(appId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mycena.uninstallApp(appId)
                MiniAppGrants.clearApp(appContext, appId)
                withContext(Dispatchers.Main) {
                    _toast.value = appContext.getString(R.string.miniapp_uninstalled_toast)
                    refresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value =
                        e.message ?: appContext.getString(R.string.miniapp_uninstall_failed)
                }
            }
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    fun installOfficialListing(appId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = mycena.installOfficialBundle(appId)
                MiniAppGrants.recordInstallTrust(appContext, info.id, "verified_listing")
                withContext(Dispatchers.Main) {
                    _toast.value =
                        appContext.getString(R.string.miniapp_installed_toast, info.name)
                    refresh()
                }
            } catch (e: MycenaException) {
                withContext(Dispatchers.Main) {
                    _toast.value = installErrorMessage(appContext, e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value =
                        e.message ?: appContext.getString(R.string.miniapp_install_failed)
                }
            }
        }
    }
}
