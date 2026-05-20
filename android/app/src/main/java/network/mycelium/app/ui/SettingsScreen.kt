package network.mycelium.app.ui

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.StorageKeyHelper
import network.mycelium.app.bootstrap.BleBootstrapPermissions
import network.mycelium.app.viewmodel.SettingsViewModel
import network.mycelium.bindings.EnergyState
import network.mycelium.bindings.mycelium
import java.io.File

private const val PREFS_NAME = "settings"
private const val KEY_BOOTSTRAP_PEERS = "bootstrap_peers"
private const val KEY_ENERGY_STATE = "energy_state"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var displayName by remember {
        mutableStateOf(
            prefs.getString("display_name", null)?.takeIf { it.isNotBlank() }
                ?: mycelium.displayName(),
        )
    }
    var bootstrapPeers by remember {
        mutableStateOf(prefs.getString(KEY_BOOTSTRAP_PEERS, "") ?: "")
    }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_identity_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.settings_display_name)) },
                placeholder = { Text(stringResource(R.string.settings_display_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_network_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            EnergyModeSelector(
                initial = prefs.getString(KEY_ENERGY_STATE, "Active") ?: "Active",
                onSelected = { mode ->
                    prefs.edit().putString(KEY_ENERGY_STATE, mode).apply()
                },
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bootstrapPeers,
                onValueChange = { bootstrapPeers = it },
                label = { Text(stringResource(R.string.settings_bootstrap_peers)) },
                placeholder = { Text(stringResource(R.string.settings_bootstrap_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Text(
                stringResource(R.string.settings_bootstrap_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val trimmedName = displayName.trim()
                    mycelium.setDisplayName(trimmedName)
                    prefs.edit().putString("display_name", trimmedName).apply()
                    val peers = bootstrapPeers
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    prefs.edit()
                        .putString(KEY_BOOTSTRAP_PEERS, peers.joinToString("\n"))
                        .apply()
                    peers.forEach { mycelium.addBootstrapPeer(it) }
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (saved) {
                        stringResource(R.string.settings_saved)
                    } else {
                        stringResource(R.string.settings_save)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate("miniapp_store") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_mini_apps))
            }
            Button(
                onClick = { navController.navigate("wallet") },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_open_wallet))
            }

            DistributionSettings(modifier = Modifier.padding(top = 24.dp))

            TextButton(
                onClick = { navController.navigate("share_app") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_share_app))
            }

            LanguageSelector(modifier = Modifier.padding(top = 24.dp).fillMaxWidth())

            PanicWipeSection(
                modifier = Modifier.padding(top = 24.dp),
                onPanicWipe = { performPanicWipe(context) },
            )
        }
    }
}

@Composable
fun DistributionSettings(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val bleEnabled by vm.bleBootstrapEnabled.collectAsState()
    var pendingEnable by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (pendingEnable && results.values.all { it }) {
            vm.setBleBootstrap(true)
        }
        pendingEnable = false
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_spread_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_spread_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_ble_advertise),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_ble_advertise_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Switch(
                    checked = bleEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            vm.setBleBootstrap(false)
                            return@Switch
                        }
                        if (BleBootstrapPermissions.hasAll(context)) {
                            vm.setBleBootstrap(true)
                        } else {
                            pendingEnable = true
                            permissionLauncher.launch(BleBootstrapPermissions.required)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnergyModeSelector(
    initial: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initial) }
    val options = listOf(
        "Active" to stringResource(R.string.settings_energy_active),
        "Intermittent" to stringResource(R.string.settings_energy_intermittent),
        "Passive" to stringResource(R.string.settings_energy_passive),
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options.find { it.first == selected }?.second ?: options.first().second,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_energy)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        selected = code
                        expanded = false
                        val state = when (code) {
                            "Intermittent" -> EnergyState.INTERMITTENT
                            "Passive" -> EnergyState.PASSIVE
                            else -> EnergyState.ACTIVE
                        }
                        mycelium.setEnergyState(state)
                        onSelected(code)
                    },
                )
            }
        }
    }
}

@Composable
fun PanicWipeSection(
    modifier: Modifier = Modifier,
    onPanicWipe: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "⚠ ${stringResource(R.string.settings_danger_zone)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_panic_description),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_panic_button))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_panic_confirm_title)) },
            text = { Text(stringResource(R.string.settings_panic_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onPanicWipe()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.settings_panic_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.settings_panic_confirm_no))
                }
            },
        )
    }
}

fun performPanicWipe(context: Context) {
    mycelium.stopNode()
    val dbPath = context.filesDir.absolutePath + "/mycelium"
    File(dbPath).deleteRecursively()
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    StorageKeyHelper.clear(context)
    val activity = context as? AppCompatActivity
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    activity?.finishAffinity()
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(prefs.getString("language", "system") ?: "system")
    }
    val languages = remember(context) {
        listOf(
            "system" to context.getString(R.string.language_system),
            "en" to "English",
            "de" to "Deutsch",
            "ru" to "Русский",
            "fa" to "فارسی",
            "zh-CN" to "中文（简体）",
            "ar" to "العربية",
        )
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = languages.find { it.first == selected }?.second ?: languages.first().second,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            languages.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        selected = code
                        expanded = false
                        prefs.edit().putString("language", code).apply()
                        val locales = when (code) {
                            "system" -> LocaleListCompat.getEmptyLocaleList()
                            "zh-CN" -> LocaleListCompat.forLanguageTags("zh-CN")
                            else -> LocaleListCompat.forLanguageTags(code)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                    },
                )
            }
        }
    }
}
