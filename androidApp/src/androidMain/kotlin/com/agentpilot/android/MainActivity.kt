package com.agentpilot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agentpilot.android.ui.Routes
import com.agentpilot.android.ui.screens.agentdetail.AgentDetailScreen
import com.agentpilot.android.ui.screens.agentlist.AgentListScreen
import com.agentpilot.android.ui.theme.AgentPilotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }
            AgentPilotTheme(darkTheme = isDarkTheme) {
                AgentPilotApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun AgentPilotApp(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.AGENT_LIST
    ) {
        // Agent List Screen
        composable(Routes.AGENT_LIST) {
            AgentListScreen(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onAgentClick = { agentId ->
                    navController.navigate(Routes.agentDetail(agentId))
                }
            )
        }

        // Agent Detail Screen
        composable(
            route = Routes.AGENT_DETAIL,
            arguments = listOf(
                navArgument("agentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId") ?: return@composable
            AgentDetailScreen(
                agentId = agentId,
                onBackClick = {
                    navController.navigateUp()
                }
            )
        }
    }
}
