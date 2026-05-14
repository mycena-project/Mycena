package network.mycelium.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import network.mycelium.app.R
import network.mycelium.app.viewmodel.MiniAppViewModel
import network.mycelium.bindings.AppStoreListing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppStoreScreen(navController: NavController, vm: MiniAppViewModel = viewModel()) {
    val context = LocalContext.current
    val installed by vm.installed.collectAsState()
    val listings by vm.listings.collectAsState()
    val toast by vm.toast.collectAsState()

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                vm.installFromUri(context, uri)
            }
        }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2800)
            vm.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.miniapp_store_title)) })
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            item {
                if (!toast.isNullOrBlank()) {
                    Text(
                        text = toast!!,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp, top = 8.dp),
                    )
                }
                Button(
                    onClick = { pickLauncher.launch("application/zip") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.miniapp_install_file))
                }
                Text(
                    stringResource(R.string.miniapp_installed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }
            if (installed.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.miniapp_no_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            } else {
                items(installed, key = { it.id }) { app ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(app.name, style = MaterialTheme.typography.titleSmall)
                            Text(app.id, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "v${app.version} · ${app.entry}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { navController.navigate("miniapp/run/${app.id}") },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.miniapp_open))
                            }
                            Button(
                                onClick = { vm.uninstall(app.id) },
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(stringResource(R.string.miniapp_uninstall))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.miniapp_catalog),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            if (listings.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.miniapp_no_listings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(listings, key = { it.manifest.id + it.bundleHash }) { row: AppStoreListing ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.manifest.name, style = MaterialTheme.typography.titleSmall)
                            Text(row.manifest.id, style = MaterialTheme.typography.bodySmall)
                            Text(
                                row.bundleHash.take(16) + "…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
