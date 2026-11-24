// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.asImageBitmap
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.mycena_project.app.BuildConfig
import io.github.mycena_project.app.R
import io.github.mycena_project.app.PrivacyShapingPrefs
import io.github.mycena_project.app.performFreshIdentity
import io.github.mycena_project.app.performPanicWipe
import io.github.mycena_project.app.bootstrap.BleBootstrapPermissions
import io.github.mycena_project.app.i18n.LocalePreferences
import io.github.mycena_project.app.settings.ActivityLevel
import io.github.mycena_project.app.settings.ActivityProfiles
import io.github.mycena_project.app.viewmodel.SettingsViewModel
import io.github.mycena_project.bindings.PrivacyShapingProfile
import io.github.mycena_project.bindings.mycena

private const val PREFS_NAME = LocalePreferences.PREFS_NAME
private const val KEY_BOOTSTRAP_PEERS = "bootstrap_peers"
private const val KEY_RENDEZVOUS = "rendezvous_enabled"
private const val KEY_INTERNET_ENABLED = "internet_enabled"
private const val KEY_MAX_CONNECTIVITY = SettingsViewModel.KEY_MAX_CONNECTIVITY
private const val KEY_CROWD_MODE = SettingsViewModel.KEY_CROWD_MODE

@Composable
private fun MaxConnectivitySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_max_connectivity_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.settings_max_connectivity_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsVm: SettingsViewModel = viewModel()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var pendingBleAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val blePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            pendingBleAction?.invoke()
        }
        pendingBleAction = null
    }
    val enableBleAfterPermissions: (onGranted: () -> Unit) -> Unit = { onGranted ->
        if (BleBootstrapPermissions.hasAll(context)) {
            onGranted()
        } else {
            pendingBleAction = onGranted
            blePermissionLauncher.launch(BleBootstrapPermissions.required)
        }
    }

    var displayName by remember {
        mutableStateOf(
            prefs.getString("display_name", null)?.takeIf { it.isNotBlank() }
                ?: mycena.displayName(),
        )
    }
    var bootstrapPeers by remember {
        mutableStateOf(prefs.getString(KEY_BOOTSTRAP_PEERS, "") ?: "")
    }
    var saved by remember { mutableStateOf(false) }
    var rendezvousEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_RENDEZVOUS, true))
    }
    var internetEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_INTERNET_ENABLED, true))
    }
    var maxConnectivity by remember {
        mutableStateOf(prefs.getBoolean(KEY_MAX_CONNECTIVITY, false))
    }
    var crowdModeEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_CROWD_MODE, false))
    }
    var localGeohashPrefix by remember {
        mutableStateOf(settingsVm.localGeohashPrefix())
    }
    var showTechnicalIds by remember { mutableStateOf(false) }
    var bleBootstrapEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(SettingsViewModel.KEY_BLE_BOOTSTRAP, false)
                || prefs.getBoolean(SettingsViewModel.KEY_MAX_CONNECTIVITY, false),
        )
    }

    Scaffold(
        topBar = {
            MycenaBackTopBar(
                title = stringResource(R.string.settings_title),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ActivityLevelSelector(
                initial = ActivityProfiles.readLevel(prefs),
                onSelected = { level ->
                    ActivityProfiles.applyLevel(context, level)
                },
            )

            Spacer(Modifier.height(20.dp))
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
            LanguageSelector(modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
            TextButton(onClick = { showTechnicalIds = !showTechnicalIds }) {
                Text(
                    if (showTechnicalIds) {
                        stringResource(R.string.settings_hide_technical_ids)
                    } else {
                        stringResource(R.string.settings_show_technical_ids)
                    },
                )
            }
            if (showTechnicalIds) {
                IdentityIdsSection(modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_network_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
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
            MaxConnectivitySwitch(
                checked = maxConnectivity,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        maxConnectivity = false
                        prefs.edit().putBoolean(KEY_MAX_CONNECTIVITY, false).apply()
                        return@MaxConnectivitySwitch
                    }
                    enableBleAfterPermissions {
                        maxConnectivity = true
                        internetEnabled = true
                        rendezvousEnabled = true
                        bleBootstrapEnabled = true
                        settingsVm.applyMaxConnectivity(true)
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_internet_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_internet_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = internetEnabled,
                    onCheckedChange = { enabled ->
                        internetEnabled = enabled
                        prefs.edit().putBoolean(KEY_INTERNET_ENABLED, enabled).apply()
                        mycena.setInternetEnabled(enabled)
                        if (!enabled) {
                            rendezvousEnabled = false
                            prefs.edit().putBoolean(KEY_RENDEZVOUS, false).apply()
                            mycena.setRendezvousEnabled(false)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_rendezvous_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_rendezvous_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = rendezvousEnabled,
                    enabled = internetEnabled,
                    onCheckedChange = { enabled ->
                        rendezvousEnabled = enabled
                        prefs.edit().putBoolean(KEY_RENDEZVOUS, enabled).apply()
                        mycena.setRendezvousEnabled(enabled)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_ble_advertise),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_ble_advertise_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = bleBootstrapEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            bleBootstrapEnabled = false
                            settingsVm.setBleBootstrap(false)
                            if (maxConnectivity) {
                                maxConnectivity = false
                                prefs.edit()
                                    .putBoolean(SettingsViewModel.KEY_MAX_CONNECTIVITY, false)
                                    .apply()
                            }
                            return@Switch
                        }
                        enableBleAfterPermissions {
                            bleBootstrapEnabled = true
                            settingsVm.setBleBootstrap(true)
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_crowd_mode_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_crowd_mode_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = crowdModeEnabled,
                    onCheckedChange = { enabled ->
                        crowdModeEnabled = enabled
                        prefs.edit().putBoolean(KEY_CROWD_MODE, enabled).apply()
                        mycena.setCrowdMode(enabled)
                    },
                )
            }

            OutlinedTextField(
                value = localGeohashPrefix,
                onValueChange = { localGeohashPrefix = it.lowercase().filter { c -> c.isLetterOrDigit() } },
                label = { Text(stringResource(R.string.settings_geohash_prefix_title)) },
                placeholder = { Text(stringResource(R.string.settings_geohash_prefix_hint)) },
                supportingText = {
                    Text(stringResource(R.string.settings_geohash_prefix_sub))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))
            PrivacySettingsSection()
            Spacer(Modifier.height(12.dp))
            LegalSettingsSection()
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(12.dp))
            BulletinSettingsSection()
            Spacer(Modifier.height(12.dp))
            EncryptedChannelsSection()

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val trimmedName = displayName.trim()
                    mycena.setDisplayName(trimmedName)
                    prefs.edit().putString("display_name", trimmedName).apply()
                    val peers = bootstrapPeers
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    prefs.edit()
                        .putString(KEY_BOOTSTRAP_PEERS, peers.joinToString("\n"))
                        .apply()
                    peers.forEach { mycena.addBootstrapPeer(it) }
                    settingsVm.setLocalGeohashPrefix(localGeohashPrefix)
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
            FreshIdentitySection(
                modifier = Modifier.padding(top = 24.dp),
                onFreshIdentity = { performFreshIdentity(context) },
            )

            DistributionSettings(modifier = Modifier.padding(top = 24.dp))

            TextButton(
                onClick = { navController.navigate("share_app") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_share_app))
            }

            PanicWipeSection(
                modifier = Modifier.padding(top = 24.dp),
                onPanicWipe = { performPanicWipe(context) },
            )

            Text(
                text =
                    stringResource(
                        R.string.settings_version_format,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
fun LegalSettingsSection() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_legal_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, io.github.mycena_project.app.DataHandlingActivity::class.java),
                    )
                },
            ) {
                Text(stringResource(R.string.settings_data_handling))
            }
            TextButton(
                onClick = {
                    context.startActivity(
                        android.content.Intent(context, io.github.mycena_project.app.LegalDocumentActivity::class.java),
                    )
                },
            ) {
                Text(stringResource(R.string.settings_privacy_policy))
            }
        }
    }
}

@Composable
fun PrivacySettingsSection(vm: SettingsViewModel = viewModel()) {
    val relayKeyAge by vm.relayKeyAgeSecs.collectAsState()
    val hoursUntilRotation = ((86400 - relayKeyAge) / 3600).coerceAtLeast(0)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var shapingEnabled by remember {
        mutableStateOf(prefs.getBoolean(PrivacyShapingPrefs.KEY_ENABLED, false))
    }
    var coverEnabled by remember {
        mutableStateOf(prefs.getBoolean(PrivacyShapingPrefs.KEY_COVER, false))
    }
    var profile by remember {
        mutableStateOf(
            PrivacyShapingPrefs.profileFromName(
                prefs.getString(PrivacyShapingPrefs.KEY_PROFILE, "Balanced"),
            ),
        )
    }
    val overhead = remember(shapingEnabled, coverEnabled, profile) {
        val config = mycena.defaultPrivacyShapingConfig(profile).copy(
            enabled = shapingEnabled,
            coverTrafficEnabled = coverEnabled,
        )
        mycena.privacyShapingOverheadEstimate(config)
    }

    fun persistAndApply() {
        prefs.edit()
            .putBoolean(PrivacyShapingPrefs.KEY_ENABLED, shapingEnabled)
            .putBoolean(PrivacyShapingPrefs.KEY_COVER, coverEnabled)
            .putString(PrivacyShapingPrefs.KEY_PROFILE, PrivacyShapingPrefs.profileName(profile))
            .apply()
        PrivacyShapingPrefs.applyFromPrefs(prefs)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_privacy_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.settings_relay_key_rotation,
                    hoursUntilRotation,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_relay_key_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_privacy_shaping_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.settings_privacy_shaping_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = shapingEnabled,
                    onCheckedChange = {
                        shapingEnabled = it
                        persistAndApply()
                    },
                )
            }
            if (shapingEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_privacy_shaping_profile_title),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PrivacyShapingProfile.FAST,
                        PrivacyShapingProfile.BALANCED,
                        PrivacyShapingProfile.PARANOID,
                    ).forEach { option ->
                        TextButton(
                            onClick = {
                                profile = option
                                persistAndApply()
                            },
                        ) {
                            Text(
                                PrivacyShapingPrefs.profileName(option),
                                color = if (profile == option) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_privacy_shaping_cover_title),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_privacy_shaping_cover_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = coverEnabled,
                        onCheckedChange = {
                            coverEnabled = it
                            persistAndApply()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.settings_privacy_shaping_overhead,
                        overhead.batteryImpactSummary,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun BulletinSettingsSection(vm: SettingsViewModel = viewModel()) {
    val subscriptions by vm.bulletinSubscriptions.collectAsState()
    var newScope by remember { mutableStateOf("") }
    val bulletinEnabled = subscriptions.isNotEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_bulletin_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (bulletinEnabled) {
                            stringResource(
                                R.string.settings_bulletin_subscribed,
                                subscriptions.size,
                            )
                        } else {
                            stringResource(R.string.settings_bulletin_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            if (!bulletinEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_bulletin_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            subscriptions.forEach { scope ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        scope,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    IconButton(
                        onClick = { vm.unsubscribeBulletin(scope) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_unsubscribe),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newScope,
                    onValueChange = { newScope = it.lowercase().replace(" ", "/") },
                    label = { Text(stringResource(R.string.settings_bulletin_add_scope)) },
                    placeholder = { Text(stringResource(R.string.bulletin_scope_example)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (newScope.isNotBlank()) {
                            vm.subscribeBulletin(newScope)
                            newScope = ""
                        }
                    },
                    enabled = newScope.isNotBlank(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_subscribe))
                }
            }
        }
    }
}

@Composable
fun EncryptedChannelsSection(vm: SettingsViewModel = viewModel()) {
    val scopeKeys by vm.scopeKeys.collectAsState()
    var exportInvite by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newScope by remember { mutableStateOf("") }
    var newDisplayName by remember { mutableStateOf("") }
    val invalidInviteMessage = stringResource(R.string.error_invalid_invite)
    val scanChannelPrompt = stringResource(R.string.settings_scan_channel_prompt)
    val scopePlaceholder = stringResource(R.string.settings_scope_key_placeholder)

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents?.trim()
        if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
        try {
            vm.addScopeKeyFromInvite(raw)
        } catch (e: Exception) {
            scanError = e.message ?: invalidInviteMessage
        }
    }

    if (exportInvite != null) {
        val qr = remember(exportInvite) { generateQrCode(exportInvite!!, 400) }
        AlertDialog(
            onDismissRequest = { exportInvite = null },
            title = { Text(stringResource(R.string.settings_scope_key_export_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        qr.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(240.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        exportInvite!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exportInvite = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    scanError?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text(stringResource(R.string.settings_encrypted_channels)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { scanError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.settings_create_scope_key)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text(stringResource(R.string.settings_display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newScope,
                        onValueChange = { newScope = it.lowercase().replace(" ", "/") },
                        label = { Text(stringResource(R.string.settings_scope_label)) },
                        placeholder = { Text(scopePlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newScope.isNotBlank() && newDisplayName.isNotBlank()) {
                            vm.createScopeKey(newScope.trim(), newDisplayName.trim())
                            newScope = ""
                            newDisplayName = ""
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_encrypted_channels),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_encrypted_channels_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))

            scopeKeys.forEach { key ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(key.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            key.scope,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            try {
                                exportInvite = vm.exportScopeKey(key.scope)
                            } catch (e: Exception) {
                                scanError = e.message
                            }
                        }) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = stringResource(R.string.action_export),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { vm.deleteScopeKey(key.scope) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_create_scope_key))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setBeepEnabled(false)
                            .setPrompt(scanChannelPrompt),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_scan_scope_key))
            }
        }
    }
}

@Composable
fun DistributionSettings(
    modifier: Modifier = Modifier,
) {
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
        }
    }
}

@Composable
private fun ActivityLevelSelector(
    initial: ActivityLevel,
    onSelected: (ActivityLevel) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    val options =
        listOf(
            ActivityLevel.AlwaysActive to R.string.settings_activity_always,
            ActivityLevel.Balanced to R.string.settings_activity_balanced,
            ActivityLevel.BatterySaver to R.string.settings_activity_battery,
        )
    val consequenceRes =
        when (selected) {
            ActivityLevel.AlwaysActive -> R.string.settings_activity_consequence_always
            ActivityLevel.Balanced -> R.string.settings_activity_consequence_balanced
            ActivityLevel.BatterySaver -> R.string.settings_activity_consequence_battery
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_activity),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            options.forEach { (level, labelRes) ->
                val isSelected = selected == level
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    selected = level
                                    onSelected(level)
                                },
                            ),
                    border =
                        if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        },
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                        ),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selected = level
                                onSelected(level)
                            },
                        )
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(consequenceRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun IdentityIdsSection(modifier: Modifier = Modifier) {
    val accountId =
        remember {
            runCatching { mycena.localAccountId() }.getOrDefault("")
        }
    val transportId =
        remember {
            runCatching { mycena.localPeerId() }.getOrDefault("")
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_account_id_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = accountId.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.settings_account_id_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_transport_id_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = transportId.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.settings_transport_id_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun FreshIdentitySection(
    modifier: Modifier = Modifier,
    onFreshIdentity: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_fresh_identity_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_fresh_identity_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_fresh_identity_button))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.fresh_identity_confirm_title)) },
            text = { Text(stringResource(R.string.fresh_identity_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onFreshIdentity()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.fresh_identity_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.fresh_identity_confirm_no))
                }
            },
        )
    }
}

@Composable
fun PanicWipeSection(
    modifier: Modifier = Modifier,
    onPanicWipe: () -> Boolean,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showFailedDialog by remember { mutableStateOf(false) }

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
                        if (!onPanicWipe()) {
                            showFailedDialog = true
                        }
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

    if (showFailedDialog) {
        AlertDialog(
            onDismissRequest = { showFailedDialog = false },
            title = { Text(stringResource(R.string.settings_panic_wipe_failed_title)) },
            text = { Text(stringResource(R.string.settings_panic_wipe_failed_body)) },
            confirmButton = {
                TextButton(onClick = { showFailedDialog = false }) {
                    Text(stringResource(R.string.settings_panic_wipe_failed_ok))
                }
            },
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(LocalePreferences.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(
            prefs.getString(LocalePreferences.KEY_LANGUAGE, LocalePreferences.LANGUAGE_SYSTEM)
                ?: LocalePreferences.LANGUAGE_SYSTEM,
        )
    }
    val languages = remember(context) {
        listOf(
            LocalePreferences.LANGUAGE_SYSTEM to context.getString(R.string.language_system),
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
            supportingText = { Text(stringResource(R.string.settings_language_sub)) },
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
                        prefs.edit().putString(LocalePreferences.KEY_LANGUAGE, code).apply()
                        LocalePreferences.applyLanguageCode(code)
                    },
                )
            }
        }
    }
}
