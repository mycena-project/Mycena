package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.bootstrap.WifiDirectSharer

class ShareAppViewModel(application: Application) : AndroidViewModel(application) {

    private val sharer = WifiDirectSharer(application)

    private val _shareUrl = MutableStateFlow<String?>(null)
    val shareUrl: StateFlow<String?> = _shareUrl

    private val _isServing = MutableStateFlow(false)
    val isServing: StateFlow<Boolean> = _isServing

    private val _transferCount = MutableStateFlow(0)
    val transferCount: StateFlow<Int> = _transferCount

    init {
        sharer.setOnTransferListener {
            _transferCount.value = sharer.getTransferCount()
        }
    }

    fun startSharing() {
        viewModelScope.launch {
            val url = sharer.startApkServer()
            _shareUrl.value = url
            _isServing.value = true
            _transferCount.value = 0
        }
    }

    fun stopSharing() {
        sharer.stopServer()
        _isServing.value = false
        _shareUrl.value = null
    }

    override fun onCleared() {
        sharer.stopServer()
        super.onCleared()
    }
}
