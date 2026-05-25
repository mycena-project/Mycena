package network.mycelium.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import network.mycelium.app.R
import network.mycelium.app.viewmodel.StatusViewModel
import network.mycelium.bindings.ConnectivityMode

private const val CONNECTED_BANNER_MS = 4_000L

@Composable
fun ConnectionStatusBanner(vm: StatusViewModel = viewModel()) {
    val peerCount by vm.peerCount.collectAsState()
    val connectivityMode by vm.connectivityMode.collectAsState()
    var showConnectedBanner by remember { mutableStateOf(false) }

    LaunchedEffect(peerCount) {
        if (peerCount > 0) {
            showConnectedBanner = true
            delay(CONNECTED_BANNER_MS)
            showConnectedBanner = false
        } else {
            showConnectedBanner = false
        }
    }

    AnimatedVisibility(
        visible = peerCount == 0,
        enter = slideInVertically(),
        exit = slideOutVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        when (connectivityMode) {
                            ConnectivityMode.INTERNET ->
                                stringResource(R.string.status_searching_internet)
                            ConnectivityMode.MESH_ONLY ->
                                stringResource(R.string.status_searching_local)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showConnectedBanner && peerCount > 0,
        enter = slideInVertically(),
        exit = slideOutVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(
                    text = stringResource(R.string.status_connected, peerCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
