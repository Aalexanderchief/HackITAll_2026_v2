package com.agentpilot.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agentpilot.shared.ui.Routes
import com.agentpilot.shared.ui.screens.agentdetail.AgentDetailScreen
import com.agentpilot.shared.ui.screens.agentlist.AgentListScreen
import com.agentpilot.shared.ui.theme.AgentPilotTheme

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "AgentPilot") {
        AgentPilotTheme {
            AgentPilotDesktopApp()
        }
    }
}

@Composable
private fun AgentPilotDesktopApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.AGENT_LIST) {
        composable(Routes.AGENT_LIST) {
            AgentListScreen(onAgentClick = { agentId ->
                navController.navigate(Routes.agentDetail(agentId))
            })
        }
        composable(
            route = Routes.AGENT_DETAIL,
            arguments = listOf(navArgument("agentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val agentId = backStackEntry.savedStateHandle.get<String>("agentId") ?: return@composable
            AgentDetailScreen(agentId = agentId, onBackClick = { navController.navigateUp() })
        }
    }
}
