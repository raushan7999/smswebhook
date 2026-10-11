package com.bestcallerapp.smswebhook.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bestcallerapp.smswebhook.presentation.home.HomeScreen
import com.bestcallerapp.smswebhook.presentation.logs.LogsScreen
import com.bestcallerapp.smswebhook.presentation.message_detail.MessageDetailScreen
import com.bestcallerapp.smswebhook.presentation.messages.MessagesScreen
import com.bestcallerapp.smswebhook.presentation.settings.SettingsScreen
import com.bestcallerapp.smswebhook.presentation.webhook.WebhooksScreen
import com.bestcallerapp.smswebhook.presentation.webhook_detail.WebhookDetailScreen
import com.bestcallerapp.smswebhook.presentation.webhook_editor.WebhookEditorScreen

@Composable
fun SmsWebhookApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { top -> destination?.hasRoute(top.route::class) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { top ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(top) },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenMessage = { navController.navigate(MessageDetailRoute(it)) },
                    onOpenMessages = { navController.navigateTopLevel(TopLevelDestination.MESSAGES) },
                    onOpenWebhooks = { navController.navigateTopLevel(TopLevelDestination.WEBHOOKS) },
                    onAddWebhook = { navController.navigate(WebhookEditorRoute()) },
                )
            }
            composable<MessagesRoute> {
                MessagesScreen(onOpenMessage = { navController.navigate(MessageDetailRoute(it)) })
            }
            composable<MessageDetailRoute> {
                MessageDetailScreen(onBack = navController::popBackStack)
            }
            composable<WebhooksRoute> {
                WebhooksScreen(
                    onOpenWebhook = { navController.navigate(WebhookDetailRoute(it)) },
                    onAddWebhook = { navController.navigate(WebhookEditorRoute()) },
                    onEditWebhook = { navController.navigate(WebhookEditorRoute(it)) },
                )
            }
            composable<WebhookDetailRoute> {
                WebhookDetailScreen(
                    onBack = navController::popBackStack,
                    onEdit = { navController.navigate(WebhookEditorRoute(it)) },
                    onOpenMessage = { navController.navigate(MessageDetailRoute(it)) },
                )
            }
            composable<WebhookEditorRoute> {
                WebhookEditorScreen(onClose = navController::popBackStack)
            }
            composable<LogsRoute> {
                LogsScreen(onOpenMessage = { navController.navigate(MessageDetailRoute(it)) })
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
