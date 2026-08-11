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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.loomytrip.mobile.data.network.ScheduleDto
import com.loomytrip.mobile.data.network.TokenStore
import com.loomytrip.mobile.data.repository.AiPlanningRepository
import com.loomytrip.mobile.data.repository.AuthRepository
import com.loomytrip.mobile.data.repository.MockTripRepository
import com.loomytrip.mobile.data.repository.TripSyncRepository
import com.loomytrip.mobile.ui.screen.AttractionScreen
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val tripRepository = remember { MockTripRepository() }
    val extractedPlaces = remember { mutableStateListOf<ExtractedPlace>() }
    val tripActivities = remember { mutableStateListOf<TripActivity>() }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var selectedMapDay by remember { mutableIntStateOf(1) }
    var selectedEditDay by remember { mutableIntStateOf(1) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var currentTripId by remember { mutableStateOf<Long?>(null) }
    var currentTripName by remember { mutableStateOf<String?>(null) }
    var baselineSchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var planningSessionId by remember { mutableStateOf<Long?>(null) }
    var planningLoading by remember { mutableStateOf(false) }
    var planningError by remember { mutableStateOf<String?>(null) }

    suspend fun loadLatestTrip() {
        val trip = TripSyncRepository.fetchLatestTrip()
        currentTripId = trip?.id
        currentTripName = trip?.tripName
        baselineSchedules = trip?.schedules ?: emptyList()
        tripActivities.clear()
        if (trip != null) tripActivities.addAll(TripSyncRepository.toActivities(trip))
    }

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
                    isLoading = authLoading,
                    serverError = authError,
                    onLogin = { email, password ->
                        scope.launch {
                            authLoading = true
                            authError = null
                            try {
                                val auth = AuthRepository.login(email, password)
                                signedInEmail = auth.email
                                loadLatestTrip()
                                navController.navigate(Destination.Home.route) {
                                    popUpTo(Destination.Login.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                authError = e.message ?: "Sign in failed"
                            } finally {
                                authLoading = false
                            }
                        }
                    },
                    onCreateAccount = { authError = null; navController.navigate(Destination.Register.route) }
                )
            }
            composable(Destination.Register.route) {
                RegisterScreen(
                    isLoading = authLoading,
                    serverError = authError,
                    onRegister = { name, email, password ->
                        scope.launch {
                            authLoading = true
                            authError = null
                            try {
                                val auth = AuthRepository.register(name, email, password)
                                signedInEmail = auth.email
                                loadLatestTrip()
                                navController.navigate(Destination.Home.route) {
                                    popUpTo(Destination.Login.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                authError = e.message ?: "Registration failed"
                            } finally {
                                authLoading = false
                            }
                        }
                    },
                    onBackToLogin = { authError = null; navController.popBackStack() }
                )
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    onStartPlanning = { navController.navigate(Destination.Import.route) },
                    onViewTrip = { navigateToRoot(Destination.Route) },
                    tripName = currentTripName,
                    tripDayCount = (tripActivities.maxOfOrNull { it.day } ?: 1).coerceAtLeast(1),
                    tripStopCount = tripActivities.size
                )
            }
            composable(Destination.Import.route) {
                ImportGuideScreen(
                    isLoading = planningLoading,
                    errorMessage = planningError,
                    onExtract = { sourceText ->
                        scope.launch {
                            planningLoading = true
                            planningError = null
                            try {
                                val result = AiPlanningRepository.startSession(sourceText)
                                planningSessionId = result.sessionId
                                extractedPlaces.clear()
                                extractedPlaces.addAll(result.places)
                                navController.navigate(Destination.Review.route)
                            } catch (e: Exception) {
                                planningError = e.message ?: "AI extraction failed"
                            } finally {
                                planningLoading = false
                            }
                        }
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
                    onConfirm = {
                        val sessionId = planningSessionId
                        scope.launch {
                            if (sessionId != null) {
                                try {
                                    extractedPlaces.filterNot { it.isIncluded }.forEach { place ->
                                        place.id.toLongOrNull()?.let { AiPlanningRepository.deletePlace(it) }
                                    }
                                    AiPlanningRepository.confirm(sessionId)
                                    loadLatestTrip()
                                } catch (_: Exception) {
                                    // Best-effort: still let the user see whatever is on the backend.
                                }
                            }
                            navigateToRoot(Destination.Route)
                        }
                    },
                    onImportAgain = { navController.popBackStack() }
                )
            }
            composable(Destination.Attraction.route) {
                AttractionScreen(onAddToTrip = { navController.navigate(Destination.Route.route) })
            }
            composable(Destination.Route.route) {
                RouteScreen(
                    activities = tripActivities,
                    tripName = currentTripName ?: "Your trip",
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
                    onSave = {
                        val tripId = currentTripId
                        if (tripId == null) {
                            navController.popBackStack()
                        } else {
                            scope.launch {
                                try {
                                    TripSyncRepository.syncEdits(tripId, tripActivities.toList(), baselineSchedules)
                                    loadLatestTrip()
                                } catch (_: Exception) {
                                    // Best-effort: keep local state even if the sync call failed.
                                }
                                navController.popBackStack()
                            }
                        }
                    }
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    email = signedInEmail.orEmpty(),
                    onSignOut = {
                        signedInEmail = null
                        extractedPlaces.clear()
                        tripActivities.clear()
                        currentTripId = null
                        currentTripName = null
                        baselineSchedules = emptyList()
                        TokenStore.token = null
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
            "Signed in with your LoomyTrip account.",
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
