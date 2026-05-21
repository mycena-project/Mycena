package network.mycelium.app.viewmodel

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
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.app.miniapp.MiniAppGrants
import network.mycelium.bindings.AppStoreListing
import network.mycelium.bindings.MiniAppInfo
import network.mycelium.bindings.MiniAppInstallPreview
import network.mycelium.bindings.mycelium
import uniffi.mycelium.MyceliumException

data class PendingMiniAppInstall(
    val bytes: ByteArray,
    val preview: MiniAppInstallPreview,
)

private fun installErrorMessage(e: MyceliumException): String =
    when (e) {
        is MyceliumException.InstallException -> e.`detail`
        else -> e.message ?: "Install failed"
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
                    mycelium.listInstalledApps()
                } catch (_: Exception) {
                    emptyList()
                }
            val rows =
                try {
                    mycelium.browseAppStore()
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
                                _toast.value = "Could not read bundle"
                            }
                            return@launch
                        }
                val preview = mycelium.previewMiniappInstall(bytes)
                withContext(Dispatchers.Main) {
                    _pendingInstall.value = PendingMiniAppInstall(bytes, preview)
                }
            } catch (e: MyceliumException) {
                withContext(Dispatchers.Main) {
                    _toast.value = installErrorMessage(e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value = e.message ?: "Install failed"
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
                    mycelium.installApp(
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
                    _toast.value = "Installed ${info.name}"
                    refresh()
                }
            } catch (e: MyceliumException) {
                withContext(Dispatchers.Main) {
                    _toast.value = installErrorMessage(e)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value = e.message ?: "Install failed"
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
                mycelium.uninstallApp(appId)
                MiniAppGrants.clearApp(appContext, appId)
                withContext(Dispatchers.Main) {
                    _toast.value = "Uninstalled"
                    refresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toast.value = e.message ?: "Uninstall failed"
                }
            }
        }
    }

    fun clearToast() {
        _toast.value = null
    }
}
