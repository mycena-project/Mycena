// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.viewmodel.SosViewModel
import java.text.DateFormat
import java.util.Date

private val RESOURCE_CATEGORIES = listOf("water", "food", "shelter", "medicine", "power")

@Composable
private fun TrustStatusLine(label: String, score: Float, status: String) {
    val color = when {
        status.contains("confirmed") -> MaterialTheme.colorScheme.primary
        status.contains("conflicting") -> MaterialTheme.colorScheme.error
        status.contains("partial") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    }
    Text(
        text = "$label · trust ${"%.0f".format(score * 100)}%",
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(navController: NavController, vm: SosViewModel = viewModel()) {
    val listenEnabled by vm.listenEnabled.collectAsState()
    val crisisManualMode by vm.crisisManualMode.collectAsState()
    val crisisActive by vm.crisisActive.collectAsState()
    val posts by vm.posts.collectAsState()
    val resourceEntries by vm.resourceEntries.collectAsState()
    val volunteerTasks by vm.volunteerTasks.collectAsState()
    var geohash by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var resourceDescription by remember { mutableStateOf("") }
    var volunteerDescription by remember { mutableStateOf("") }
    var volunteerSlots by remember { mutableStateOf("2") }
    var resourceCategory by remember { mutableStateOf(RESOURCE_CATEGORIES.first()) }
    var crisisMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val crisisModeLabels = listOf(
        stringResource(R.string.crisis_mode_auto),
        stringResource(R.string.crisis_mode_force_on),
        stringResource(R.string.crisis_mode_force_off),
    )

    val categoryLabels = mapOf(
        "water" to stringResource(R.string.resource_category_water),
        "food" to stringResource(R.string.resource_category_food),
        "shelter" to stringResource(R.string.resource_category_shelter),
        "medicine" to stringResource(R.string.resource_category_medicine),
        "power" to stringResource(R.string.resource_category_power),
    )

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.sos_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.sos_disclaimer),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Text(stringResource(R.string.crisis_mode_section), style = MaterialTheme.typography.titleSmall)
                if (crisisActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.crisis_mode_active_banner),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = crisisMenuExpanded,
                    onExpandedChange = { crisisMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = crisisModeLabels[crisisManualMode.coerceIn(0, 2)],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.crisis_mode_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = crisisMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = crisisMenuExpanded,
                        onDismissRequest = { crisisMenuExpanded = false },
                    ) {
                        crisisModeLabels.forEachIndexed { index, label ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.setCrisisManualMode(index)
                                    crisisMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.crisis_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Text(stringResource(R.string.sos_listen_section), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sos_listen_label))
                    Switch(checked = listenEnabled, onCheckedChange = { vm.setListenEnabled(it) })
                }
                Text(
                    stringResource(R.string.sos_listen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Text(stringResource(R.string.sos_post_section), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = geohash,
                    onValueChange = { geohash = it.take(12) },
                    label = { Text(stringResource(R.string.sos_geohash_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.sos_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(
                    onClick = {
                        if (geohash.isNotBlank() && message.isNotBlank()) {
                            vm.postSos(geohash, message)
                            message = ""
                        }
                    },
                    enabled = geohash.isNotBlank() && message.isNotBlank(),
                ) {
                    Text(stringResource(R.string.sos_post_button))
                }
            }
            item {
                Text(
                    stringResource(R.string.sos_nearby_section, posts.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!listenEnabled) {
                    Text(
                        stringResource(R.string.sos_listen_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                    )
                } else if (posts.isEmpty()) {
                    Text(
                        stringResource(R.string.sos_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(posts) { post ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(post.geohash, style = MaterialTheme.typography.labelMedium)
                    Text(post.message, style = MaterialTheme.typography.bodyMedium)
                    TrustStatusLine(
                        label = post.trust.displayLabel,
                        score = post.trust.trustScore,
                        status = post.trust.status,
                    )
                }
            }
            item {
                Text(stringResource(R.string.resource_map_section), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.resource_map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = categoryLabels[resourceCategory] ?: resourceCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.resource_map_category_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        RESOURCE_CATEGORIES.forEach { cat ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(categoryLabels[cat] ?: cat) },
                                onClick = {
                                    resourceCategory = cat
                                    categoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = resourceDescription,
                    onValueChange = { resourceDescription = it.take(200) },
                    label = { Text(stringResource(R.string.resource_map_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(
                    onClick = {
                        if (geohash.isNotBlank() && resourceDescription.isNotBlank()) {
                            vm.postResourceMap(geohash, resourceCategory, resourceDescription)
                            resourceDescription = ""
                        }
                    },
                    enabled = geohash.isNotBlank() && resourceDescription.isNotBlank(),
                ) {
                    Text(stringResource(R.string.resource_map_post_button))
                }
            }
            item {
                Text(
                    stringResource(R.string.resource_map_nearby_section, resourceEntries.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!listenEnabled) {
                    Text(
                        stringResource(R.string.sos_listen_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (resourceEntries.isEmpty()) {
                    Text(
                        stringResource(R.string.resource_map_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(resourceEntries) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "${entry.category} @ ${entry.geohash}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                    TrustStatusLine(
                        label = entry.trust.displayLabel,
                        score = entry.trust.trustScore,
                        status = entry.trust.status,
                    )
                    Text(
                        stringResource(
                            R.string.resource_map_expires,
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(entry.expiresAtMs.toLong())),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            item {
                Text(stringResource(R.string.volunteer_section), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.volunteer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                OutlinedTextField(
                    value = volunteerSlots,
                    onValueChange = { volunteerSlots = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.volunteer_slots_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = volunteerDescription,
                    onValueChange = { volunteerDescription = it.take(200) },
                    label = { Text(stringResource(R.string.volunteer_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(
                    onClick = {
                        val slots = volunteerSlots.toIntOrNull() ?: 0
                        if (geohash.isNotBlank() && volunteerDescription.isNotBlank() && slots in 1..50) {
                            vm.postVolunteerTask(geohash, resourceCategory, volunteerDescription, slots)
                            volunteerDescription = ""
                        }
                    },
                    enabled = geohash.isNotBlank() &&
                        volunteerDescription.isNotBlank() &&
                        (volunteerSlots.toIntOrNull() ?: 0) in 1..50,
                ) {
                    Text(stringResource(R.string.volunteer_post_button))
                }
            }
            item {
                Text(
                    stringResource(R.string.volunteer_nearby_section, volunteerTasks.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!listenEnabled) {
                    Text(
                        stringResource(R.string.sos_listen_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (volunteerTasks.isEmpty()) {
                    Text(
                        stringResource(R.string.volunteer_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(volunteerTasks, key = { it.taskId }) { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "${task.category} @ ${task.geohash}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(task.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            R.string.volunteer_claims,
                            task.claimsCount.toInt(),
                            task.slotsNeeded.toInt(),
                            task.slotsOpen.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    if (task.slotsOpen > 0u) {
                        OutlinedButton(onClick = { vm.claimVolunteerTask(task.taskId) }) {
                            Text(stringResource(R.string.volunteer_claim_button))
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sos_meshaid_section), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.sos_meshaid_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { navController.navigate("miniapp/run/network.mycelium.meshaid") }) {
                    Text(stringResource(R.string.sos_meshaid_button))
                }
            }
        }
    }
}
