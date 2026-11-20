package com.medibot.g1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medibot.g1.ui.components.SimulatorControlBottomSheet
import com.medibot.g1.ui.screens.DashboardScreen
import com.medibot.g1.ui.screens.HistoryScreen
import com.medibot.g1.ui.screens.MissionsScreen
import com.medibot.g1.ui.screens.RobotSelectionScreen
import com.medibot.g1.ui.theme.*
import com.medibot.g1.ui.viewmodel.SupervisionViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Robot : Screen("robot", "Robot", Icons.Default.SmartToy)
    data object Dashboard : Screen("dashboard", "Supervisión", Icons.Default.Dashboard)
    data object Missions : Screen("missions", "Misiones", Icons.AutoMirrored.Filled.ListAlt)
    data object History : Screen("history", "Historial", Icons.Default.History)

    companion object {
        val bottomBarItems = listOf(Robot, Dashboard, Missions, History)
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: SupervisionViewModel by viewModels { SupervisionViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediBotTheme {
                MediBotApp(viewModel)
            }
        }
    }
}

@Composable
private fun MediBotApp(viewModel: SupervisionViewModel) {
    // Consumo consciente del ciclo de vida: deja de recolectar con la app en segundo plano.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val missionsHistory by viewModel.missionsHistory.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    var showSimulatorBottomSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSimulatorBottomSheet = true },
                containerColor = ElectricViolet,
                contentColor = DarkBackground,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Abrir panel de ingeniería (simulador)"
                )
            }
        },
        bottomBar = { MediBotBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Robot.route) {
                RobotSelectionScreen(
                    telemetry = uiState.telemetry,
                    linkStatus = uiState.linkStatus,
                    onNavigateToDashboard = { navController.navigateToTab(Screen.Dashboard) }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    uiState = uiState,
                    onSendCommand = viewModel::sendCommand,
                    onAcknowledgeAlert = viewModel::acknowledgeAlert,
                    onDismissCommandFeedback = viewModel::dismissCommandFeedback
                )
            }
            composable(Screen.Missions.route) {
                MissionsScreen(
                    catalog = viewModel.missionCatalog,
                    preconditions = uiState.startPreconditions,
                    canStart = uiState.availability.start,
                    onStartMission = { mission ->
                        viewModel.startMission(mission)
                        navController.navigateToTab(Screen.Dashboard)
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    missionsHistory = missionsHistory,
                    eventLogs = eventLogs,
                    onInsertSeedData = viewModel::insertSampleData,
                    onClearHistory = viewModel::clearHistory
                )
            }
        }

        if (showSimulatorBottomSheet) {
            SimulatorControlBottomSheet(
                linkUp = uiState.simulatorLinkUp,
                forceRejectNext = uiState.forceRejectNext,
                onInjectScenario = viewModel::injectScenario,
                onToggleForceReject = viewModel::setForceRejectNext,
                onRestoreConnection = viewModel::restoreConnection,
                onResetSimulation = viewModel::resetSimulation,
                onDismissRequest = { showSimulatorBottomSheet = false }
            )
        }
    }
}

@Composable
private fun MediBotBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = DarkSurface, contentColor = NeonCyan) {
        Screen.bottomBarItems.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { navController.navigateToTab(screen) },
                icon = { Icon(imageVector = screen.icon, contentDescription = null) },
                label = {
                    Text(
                        text = screen.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkBackground,
                    selectedTextColor = NeonCyan,
                    indicatorColor = NeonCyan,
                    unselectedIconColor = DarkOnSurface,
                    unselectedTextColor = DarkOnSurface
                )
            )
        }
    }
}

/** Navegación entre pestañas sin apilar destinos y conservando el estado de cada una. */
private fun NavHostController.navigateToTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
