package com.loomytrip.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.loomytrip.mobile.data.model.ExtractedPlace
import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.repository.MockPlanningRepository
import com.loomytrip.mobile.data.repository.MockTripRepository
import com.loomytrip.mobile.ui.screen.AttractionScreen
import com.loomytrip.mobile.ui.screen.EditTripScreen
import com.loomytrip.mobile.ui.screen.HomeScreen
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.LoginScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.RegisterScreen
import com.loomytrip.mobile.ui.screen.ReviewExtractedScreen
import com.loomytrip.mobile.ui.screen.RouteScreen

private enum class Destination(val route: String, val label: String) {
    Login("login", "Sign in"),
    Register("register", "Create account"),
    Home("home", "Home"),
    Import("import", "Import guide"),
    Review("review", "Review places"),
    Attraction("attraction", "Attraction"),
    Route("route", "Trips"),
    Map("map", "Map"),
    Edit("edit", "Edit trip"),
    Profile("profile", "Profile")
}

private data class BottomItem(
    val destination: Destination,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoomyTripApp() {
    val navController = rememberNavController()
    val planningRepository = remember { MockPlanningRepository() }
    val tripRepository = remember { MockTripRepository() }
    val extractedPlaces = remember { mutableStateListOf<ExtractedPlace>() }
    val tripActivities = remember {
        mutableStateListOf<TripActivity>().apply { addAll(tripRepository.initialItinerary()) }
    }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var selectedMapDay by remember { mutableIntStateOf(1) }
    var selectedEditDay by remember { mutableIntStateOf(1) }

    val backStackEntry = navController.currentBackStackEntryAsState().value
    val route = backStackEntry?.destination?.route ?: Destination.Login.route
    val current = Destination.entries.firstOrNull { it.route == route } ?: Destination.Login
    val authDestinations = setOf(Destination.Login, Destination.Register)
    val bottomDestinations = setOf(
        Destination.Home,
        Destination.Route,
        Destination.Map,
        Destination.Profile
    )
    val showAppChrome = current !in authDestinations
    val showBottomBar = current in bottomDestinations
    val bottomItems = listOf(
        BottomItem(Destination.Home, Icons.Default.Home),
        BottomItem(Destination.Route, Icons.Default.Route),
        BottomItem(Destination.Map, Icons.Default.Map),
        BottomItem(Destination.Profile, Icons.Default.Person)
    )

    fun navigateToRoot(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun replaceTripActivities(updated: List<TripActivity>) {
        tripActivities.clear()
        tripActivities.addAll(updated)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showAppChrome) {
                TopAppBar(
                    title = { Text(if (current == Destination.Home) "Loomytrip" else current.label) },
                    navigationIcon = {
                        if (current !in bottomDestinations) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomAppBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == item.destination.route
                            } == true,
                            onClick = { navigateToRoot(item.destination) },
                            icon = { Icon(item.icon, contentDescription = item.destination.label) },
                            label = { Text(item.destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Login.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Destination.Login.route) {
                LoginScreen(
                    onLogin = { email ->
                        signedInEmail = email
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Destination.Login.route) { inclusive = true }
                        }
                    },
                    onCreateAccount = { navController.navigate(Destination.Register.route) }
                )
            }
            composable(Destination.Register.route) {
                RegisterScreen(
                    onRegister = { email ->
                        signedInEmail = email
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Destination.Login.route) { inclusive = true }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() }
                )
            }
            composable(Destination.Home.route) {
                HomeScreen(onStartPlanning = { navController.navigate(Destination.Import.route) })
            }
            composable(Destination.Import.route) {
                ImportGuideScreen(
                    onExtract = { sourceText ->
                        extractedPlaces.clear()
                        extractedPlaces.addAll(planningRepository.extractPlaces(sourceText))
                        navController.navigate(Destination.Review.route)
                    }
                )
            }
            composable(Destination.Review.route) {
                ReviewExtractedScreen(
                    places = extractedPlaces,
                    onIncludedChange = { id, included ->
                        val index = extractedPlaces.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            extractedPlaces[index] = extractedPlaces[index].copy(isIncluded = included)
                        }
                    },
                    onConfirm = { navController.navigate(Destination.Route.route) },
                    onImportAgain = { navController.popBackStack() }
                )
            }
            composable(Destination.Attraction.route) {
                AttractionScreen(onAddToTrip = { navController.navigate(Destination.Route.route) })
            }
            composable(Destination.Route.route) {
                RouteScreen(
                    activities = tripActivities,
                    onViewMap = { day ->
                        selectedMapDay = day
                        navController.navigate(Destination.Map.route)
                    },
                    onEdit = { day ->
                        selectedEditDay = day
                        navController.navigate(Destination.Edit.route)
                    }
                )
            }
            composable(Destination.Map.route) {
                MapScreen(
                    activities = tripActivities,
                    initialDay = selectedMapDay,
                    onEdit = { day ->
                        selectedEditDay = day
                        navController.navigate(Destination.Edit.route)
                    }
                )
            }
            composable(Destination.Edit.route) {
                EditTripScreen(
                    activities = tripActivities,
                    initialDay = selectedEditDay,
                    onMove = { id, direction ->
                        replaceTripActivities(
                            tripRepository.moveActivity(tripActivities.toList(), id, direction)
                        )
                    },
                    onDelete = { id ->
                        replaceTripActivities(
                            tripRepository.deleteActivity(tripActivities.toList(), id)
                        )
                    },
                    onAdd = { day, title, time ->
                        replaceTripActivities(
                            tripRepository.addActivity(tripActivities.toList(), day, title, time)
                        )
                    },
                    onSave = { navController.popBackStack() }
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    email = signedInEmail.orEmpty(),
                    onSignOut = {
                        signedInEmail = null
                        extractedPlaces.clear()
                        navController.navigate(Destination.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(email: String, onSignOut: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        Text("Traveler account", fontSize = 27.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(email.ifBlank { "traveler@loomytrip.com" })
        Text(
            "Authentication is represented with local state for the Sprint 1 prototype.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        androidx.compose.material3.OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}
