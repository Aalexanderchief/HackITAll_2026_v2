package com.agentpilot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agentpilot.android.ui.screens.agentdetail.AgentDetailViewModel
import com.agentpilot.android.ui.theme.AgentPilotTheme
import com.agentpilot.shared.ui.Routes
import com.agentpilot.shared.ui.screens.agentdetail.AgentDetailScreen
import com.agentpilot.shared.ui.screens.agentlist.AgentListScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentPilotTheme {
                AgentPilotApp()
            }
        }
    }
}

@Composable
fun AgentPilotApp() {
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
            val agentId = backStackEntry.arguments?.getString("agentId") ?: return@composable
            val vm: AgentDetailViewModel = viewModel(key = agentId) { AgentDetailViewModel(agentId) }
            val agentStatus by vm.agentStatus.collectAsState()
            val activityFeed by vm.activityFeed.collectAsState()
            AgentDetailScreen(
                agentId = agentId,
                agentStatus = agentStatus,
                activityFeed = activityFeed,
                onBackClick = { navController.navigateUp() }
            )
        }
    }
}
