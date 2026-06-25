package pt.droninho32.app.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pt.droninho32.app.BuildConfig
import pt.droninho32.app.di.ServiceLocator
import pt.droninho32.app.ui.screens.AccountScreen
import pt.droninho32.app.ui.screens.CameraScreen
import pt.droninho32.app.ui.screens.ControlScreen
import pt.droninho32.app.ui.screens.DroneListScreen
import pt.droninho32.app.ui.screens.FlightDetailScreen
import pt.droninho32.app.ui.screens.HistoryScreen
import pt.droninho32.app.ui.screens.MapScreen
import pt.droninho32.app.viewmodel.AuthViewModel
import pt.droninho32.app.viewmodel.ControlViewModel
import pt.droninho32.app.viewmodel.DroneViewModel
import pt.droninho32.app.viewmodel.FlightsViewModel

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.CONTROL, "Controlo", Icons.Default.FlightTakeoff),
    BottomItem(Routes.HISTORY, "Histórico", Icons.Default.History),
    BottomItem(Routes.ACCOUNT, "Conta", Icons.Default.Person),
)

/**
 * Grafo de navegação. A app **abre no Controlo** (não há porta de login): controlar o
 * drone só precisa do WiFi do drone, sem Internet. O login é OPCIONAL, na aba "Conta",
 * e serve apenas para sincronizar/ver voos no backend quando há Internet.
 *
 * O ControlViewModel é criado na MainActivity (owner partilhado) e passado aqui, para
 * que Controlo, Mapa e Câmara vejam a MESMA telemetria.
 */
@Composable
fun AppNavHost(
    locator: ServiceLocator,
    controlVm: ControlViewModel,
    activityViewModelOwner: androidx.lifecycle.ViewModelStoreOwner,
    onToggleScreenRecording: () -> Unit,
) {
    val nav = rememberNavController()

    val authVm: AuthViewModel = viewModel(
        viewModelStoreOwner = activityViewModelOwner,
        factory = AuthViewModel.Factory(locator.authRepository),
    )

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    nav.navigate(item.route) {
                                        popUpTo(Routes.CONTROL) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CONTROL,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            mainGraph(nav, locator, controlVm, authVm, onToggleScreenRecording)
        }
    }
}

private fun NavGraphBuilder.mainGraph(
    nav: NavHostController,
    locator: ServiceLocator,
    controlVm: ControlViewModel,
    authVm: AuthViewModel,
    onToggleScreenRecording: () -> Unit,
) {
    composable(Routes.CONTROL) {
        ControlScreen(
            vm = controlVm,
            onOpenMap = { nav.navigate(Routes.MAP) },
            onOpenCamera = { nav.navigate(Routes.CAMERA) },
            onToggleScreenRecording = onToggleScreenRecording,
        )
    }

    composable(Routes.CAMERA) {
        val s by controlVm.state.collectAsStateWithLifecycle()
        val url = s.cameraUrl.ifBlank { BuildConfig.CAMERA_URL }
        CameraScreen(streamUrl = url, onBack = { nav.popBackStack() })
    }

    composable(Routes.MAP) {
        MapScreen(vm = controlVm, onBack = { nav.popBackStack() })
    }

    composable(Routes.ACCOUNT) {
        AccountScreen(
            vm = authVm,
            controlVm = controlVm,
            onOpenDrones = { nav.navigate(Routes.DRONES) },
        )
    }

    composable(Routes.DRONES) {
        val droneVm: DroneViewModel = viewModel(
            factory = DroneViewModel.Factory(locator.backendRepository),
        )
        DroneListScreen(
            vm = droneVm,
            onOpenControl = { drone ->
                controlVm.selectDrone(drone.id)
                nav.navigate(Routes.CONTROL) { launchSingleTop = true }
            },
            onLogout = {
                authVm.logout()
                nav.popBackStack()
            },
        )
    }

    composable(Routes.HISTORY) {
        val authState by authVm.state.collectAsStateWithLifecycle()
        val flightsVm: FlightsViewModel = viewModel(
            factory = FlightsViewModel.Factory(locator.backendRepository),
        )
        HistoryScreen(
            vm = flightsVm,
            loggedIn = authState.loggedIn,
            onOpenFlight = { id -> nav.navigate(Routes.flightDetail(id)) },
            onOpenAccount = { nav.navigate(Routes.ACCOUNT) { launchSingleTop = true } },
        )
    }

    composable(
        route = Routes.FLIGHT_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_FLIGHT_ID) { type = NavType.IntType }),
    ) { entry ->
        val id = entry.arguments?.getInt(Routes.ARG_FLIGHT_ID) ?: 0
        val flightsVm: FlightsViewModel = viewModel(
            factory = FlightsViewModel.Factory(locator.backendRepository),
        )
        FlightDetailScreen(
            flightId = id,
            vm = flightsVm,
            onBack = { nav.popBackStack() },
        )
    }
}
