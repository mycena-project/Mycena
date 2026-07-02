// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import network.mycelium.app.R
import network.mycelium.app.viewmodel.ChatViewModel

private data class TopLevelTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val topLevelTabs = listOf(
    TopLevelTab("chat", R.string.nav_chat, Icons.AutoMirrored.Filled.Chat),
    TopLevelTab("peers", R.string.nav_peers, Icons.Default.People),
    TopLevelTab("bulletin", R.string.nav_bulletin, Icons.Default.Forum),
    TopLevelTab("sos", R.string.nav_sos, Icons.Default.Warning),
    TopLevelTab("more", R.string.nav_more, Icons.Default.Apps),
)

private fun isDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route.startsWith("chat/") ||
        route == "groups" ||
        route.startsWith("groups/") ||
        route == "settings" ||
        route == "wallet" ||
        route == "miniapp_store" ||
        route == "share_app" ||
        route == "mail"
}

private fun tabForRoute(route: String?): String? {
    if (route == null) return null
    return when {
        route == "chat" || route.startsWith("chat/") ||
            route == "groups" || route.startsWith("groups/") -> "chat"
        route == "peers" -> "peers"
        route == "bulletin" -> "bulletin"
        route == "sos" -> "sos"
        route == "more" || route == "settings" || route == "miniapp_store" ||
            route == "wallet" || route == "share_app" || route == "mail" ||
            route.startsWith("miniapp/run/") -> "more"
        else -> null
    }
}

@Composable
fun MainShell() {
    val nav = rememberNavController()
    val chatVm: ChatViewModel = viewModel()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedTab = tabForRoute(currentRoute)

    val showBottomBar = currentRoute?.startsWith("miniapp/run/") != true && !isDetailRoute(currentRoute)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MyceliumBottomBar(
                    nav = nav,
                    selectedTab = selectedTab,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showBottomBar) {
                ConnectionStatusBanner()
                NotificationPermissionBanner()
            }
            NavHost(
                navController = nav,
                startDestination = "chat",
                modifier = Modifier.weight(1f, fill = true),
            ) {
            composable("chat") {
                ChatScreen(nav, chatVm)
            }
            composable("chat/{peerId}") { backStack ->
                ChatConversationScreen(
                    peerId = backStack.arguments?.getString("peerId") ?: "",
                    navController = nav,
                    vm = chatVm,
                )
            }
            composable("groups") { GroupsListScreen(nav) }
            composable("groups/{groupId}") { backStack ->
                GroupConversationScreen(
                    groupId = backStack.arguments?.getString("groupId") ?: "",
                    navController = nav,
                )
            }
            composable("peers") { PeersScreen(nav) }
            composable("mail") { MailScreen(nav) }
            composable("bulletin") { BulletinScreen(nav) }
            composable("sos") { SosScreen(nav) }
            composable("more") { MoreScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("miniapp_store") { MiniAppStoreScreen(nav) }
            composable("miniapp/run/{appId}") { backStack ->
                val appId = backStack.arguments?.getString("appId") ?: ""
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.LaunchedEffect(appId) {
                    ctx.startActivity(
                        network.mycelium.app.miniapp.MiniAppProcessActivity.intent(ctx, appId),
                    )
                    nav.popBackStack()
                }
            }
            composable("wallet") { WalletScreen(nav) }
            composable("share_app") { ShareAppScreen(nav) }
            }
        }
    }
}

@Composable
private fun MyceliumBottomBar(
    nav: NavHostController,
    selectedTab: String?,
) {
    NavigationBar {
        topLevelTabs.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
