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
import androidx.compose.runtime.LaunchedEffect
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
import com.loomytrip.mobile.data.network.TripDto
import com.loomytrip.mobile.data.repository.AiPlanningRepository
import com.loomytrip.mobile.data.repository.AuthRepository
import com.loomytrip.mobile.data.repository.MockTripRepository
import com.loomytrip.mobile.data.repository.TripSyncRepository
import com.loomytrip.mobile.ui.screen.AttractionScreen
import com.loomytrip.mobile.ui.screen.EditTripScreen
import com.loomytrip.mobile.ui.screen.HomeScreen
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.LoginScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.MapTripOption
import com.loomytrip.mobile.ui.screen.RegisterScreen
import com.loomytrip.mobile.ui.screen.ReviewExtractedScreen
import com.loomytrip.mobile.ui.screen.RouteScreen
import com.loomytrip.mobile.ui.screen.TripsListScreen
import java.io.IOException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private enum class Destination(val route: String, val label: String) {
    Login("login", "Sign in"),
    Register("register", "Create account"),
    Home("home", "Home"),
    Import("import", "Smart AI Import"),
    Review("review", "Review places"),
    Attraction("attraction", "Attraction"),
    Trips("trips", "My Itineraries"),
    Itinerary("itinerary", "Itinerary Details"),
    Map("map", "Map"),
    Edit("edit", "Edit Itinerary"),
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
    val trips = remember { mutableStateListOf<TripDto>() }
    val tripActivities = remember { mutableStateListOf<TripActivity>() }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var selectedMapDay by remember { mutableIntStateOf(1) }
    var selectedEditDay by remember { mutableIntStateOf(1) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var activeTripId by remember { mutableStateOf<Long?>(null) }
    var tripsLoading by remember { mutableStateOf(false) }
    var tripsError by remember { mutableStateOf<String?>(null) }
    var baselineSchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var planningSessionId by remember { mutableStateOf<Long?>(null) }
    var planningLoading by remember { mutableStateOf(false) }
    var planningError by remember { mutableStateOf<String?>(null) }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var startDateUpdating by remember { mutableStateOf(false) }
    var startDateError by remember { mutableStateOf<String?>(null) }

    fun selectTrip(tripId: Long?) {
        val trip = trips.firstOrNull { it.id == tripId } ?: trips.firstOrNull()
        startDateError = null
        activeTripId = trip?.id
        baselineSchedules = trip?.schedules ?: emptyList()
        tripActivities.clear()
        if (trip != null) tripActivities.addAll(TripSyncRepository.toActivities(trip))
    }

    suspend fun loadTrips(preferredTripId: Long? = activeTripId) {
        tripsLoading = true
        tripsError = null
        try {
            val remoteTrips = TripSyncRepository.fetchTrips()
            trips.clear()
            trips.addAll(remoteTrips)
            selectTrip(preferredTripId)
        } catch (error: Exception) {
            tripsError = error.userMessage("Could not load trips from Backend.")
        } finally {
            tripsLoading = false
        }
    }

    val backStackEntry = navController.currentBackStackEntryAsState().value
    val route = backStackEntry?.destination?.route ?: Destination.Login.route
    val current = Destination.entries.firstOrNull { it.route == route } ?: Destination.Login
    val authDestinations = setOf(Destination.Login, Destination.Register)
    val bottomDestinations = setOf(
        Destination.Home,
        Destination.Trips,
        Destination.Map,
        Destination.Profile
    )
    val showAppChrome = current !in authDestinations
    val showBottomBar = current in bottomDestinations
    val bottomItems = listOf(
        BottomItem(Destination.Home, Icons.Default.Home),
        BottomItem(Destination.Trips, Icons.Default.Route),
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

    LaunchedEffect(route, signedInEmail) {
        if (signedInEmail != null && current in setOf(Destination.Home, Destination.Trips)) {
            loadTrips()
        }
    }

    val activeTrip = trips.firstOrNull { it.id == activeTripId } ?: trips.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showAppChrome) {
                TopAppBar(
                    title = { Text(if (current == Destination.Home) "LoomyTrip" else current.label) },
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
                                navController.navigate(Destination.Home.route) {
                                    popUpTo(Destination.Login.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                authError = e.userMessage("Sign in failed. Check the account and try again.")
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
                                navController.navigate(Destination.Home.route) {
                                    popUpTo(Destination.Login.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                authError = e.userMessage("Registration failed. Please try again.")
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
                    onViewTrip = {
                        navigateToRoot(if (activeTrip == null) Destination.Trips else Destination.Itinerary)
                    },
                    onViewAllTrips = { navigateToRoot(Destination.Trips) },
                    tripName = activeTrip?.tripName,
                    tripDayCount = activeTrip?.durationDays ?: 1,
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
                                if (result.places.isEmpty()) {
                                    error("AI finished but did not return any places. Try adding a destination and dates.")
                                }
                                planningSessionId = result.sessionId
                                extractedPlaces.clear()
                                extractedPlaces.addAll(result.places)
                                navController.navigate(Destination.Review.route)
                            } catch (e: Exception) {
                                planningError = e.userMessage("AI analysis failed. Please try again.")
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
                            if (sessionId == null) {
                                planningError = "Start the AI import again before confirming."
                            } else {
                                planningLoading = true
                                planningError = null
                                try {
                                    extractedPlaces.filterNot { it.isIncluded }.forEach { place ->
                                        place.id.toLongOrNull()?.let { AiPlanningRepository.deletePlace(it) }
                                    }
                                    val newTripId = AiPlanningRepository.confirm(sessionId)
                                    loadTrips(newTripId)
                                    if (trips.any { it.id == newTripId }) {
                                        navController.navigate(Destination.Itinerary.route) {
                                            popUpTo(Destination.Home.route)
                                        }
                                    } else {
                                        planningError = tripsError ?: "The itinerary was created but could not be loaded."
                                    }
                                } catch (error: Exception) {
                                    planningError = error.userMessage("Could not create the itinerary.")
                                } finally {
                                    planningLoading = false
                                }
                            }
                        }
                    },
                    onImportAgain = { navController.popBackStack() },
                    isLoading = planningLoading,
                    errorMessage = planningError
                )
            }
            composable(Destination.Attraction.route) {
                AttractionScreen(onAddToTrip = { navController.navigate(Destination.Itinerary.route) })
            }
            composable(Destination.Trips.route) {
                TripsListScreen(
                    trips = trips,
                    isLoading = tripsLoading,
                    errorMessage = tripsError,
                    onRefresh = { scope.launch { loadTrips() } },
                    onStartPlanning = { navController.navigate(Destination.Import.route) },
                    onTripSelected = { tripId ->
                        selectTrip(tripId)
                        navController.navigate(Destination.Itinerary.route)
                    }
                )
            }
            composable(Destination.Itinerary.route) {
                RouteScreen(
                    activities = tripActivities,
                    tripName = activeTrip?.tripName ?: "Your itinerary",
                    startDate = activeTrip?.startDate,
                    tripStatus = activeTrip?.status,
                    totalDays = activeTrip?.durationDays ?: 1,
                    isUpdatingStartDate = startDateUpdating,
                    startDateError = startDateError,
                    onStartDateChange = { newDate ->
                        val tripId = activeTrip?.id
                        if (tripId == null) {
                            startDateError = "Select an itinerary before changing its start date."
                        } else {
                            scope.launch {
                                startDateUpdating = true
                                startDateError = null
                                try {
                                    val updated = TripSyncRepository.updateStartDate(tripId, newDate)
                                    val sortedTrips = TripSyncRepository.sortForDisplay(
                                        trips.filterNot { it.id == updated.id } + updated
                                    )
                                    trips.clear()
                                    trips.addAll(sortedTrips)
                                    selectTrip(updated.id)
                                } catch (error: Exception) {
                                    startDateError = error.userMessage("Could not update the start date.")
                                } finally {
                                    startDateUpdating = false
                                }
                            }
                        }
                    },
                    onViewMap = { day ->
                        selectedMapDay = day
                        navController.navigate(Destination.Map.route)
                    },
                    onEdit = { day ->
                        selectedEditDay = day
                        editError = null
                        navController.navigate(Destination.Edit.route)
                    }
                )
            }
            composable(Destination.Map.route) {
                MapScreen(
                    activities = tripActivities,
                    initialDay = selectedMapDay,
                    totalDays = activeTrip?.durationDays ?: 1,
                    tripOptions = trips.map { MapTripOption(it.id, it.tripName) },
                    selectedTripId = activeTrip?.id,
                    onTripSelected = { tripId ->
                        selectTrip(tripId)
                        selectedMapDay = 1
                    },
                    onEdit = { day ->
                        selectedEditDay = day
                        editError = null
                        navController.navigate(Destination.Edit.route)
                    }
                )
            }
            composable(Destination.Edit.route) {
                EditTripScreen(
                    activities = tripActivities,
                    initialDay = selectedEditDay,
                    totalDays = activeTrip?.durationDays ?: 1,
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
                        val tripId = activeTripId
                        if (tripId == null) {
                            editError = "Select a Backend itinerary before saving."
                        } else {
                            scope.launch {
                                editSaving = true
                                editError = null
                                try {
                                    val updated = TripSyncRepository.syncEdits(
                                        tripId,
                                        tripActivities.toList(),
                                        baselineSchedules
                                    )
                                    val index = trips.indexOfFirst { it.id == updated.id }
                                    if (index >= 0) trips[index] = updated else trips.add(0, updated)
                                    selectTrip(updated.id)
                                    navController.popBackStack()
                                } catch (error: Exception) {
                                    editError = error.userMessage("Could not save this itinerary.")
                                } finally {
                                    editSaving = false
                                }
                            }
                        }
                    },
                    isSaving = editSaving,
                    errorMessage = editError
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    email = signedInEmail.orEmpty(),
                    onSignOut = {
                        signedInEmail = null
                        extractedPlaces.clear()
                        trips.clear()
                        tripActivities.clear()
                        activeTripId = null
                        baselineSchedules = emptyList()
                        startDateError = null
                        startDateUpdating = false
                        TokenStore.clear()
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

private fun Throwable.userMessage(fallback: String): String = when (this) {
    is IOException -> "Cannot reach LoomyTrip Backend. Check the network and try again."
    is HttpException -> when (code()) {
        400 -> "The request was not accepted. Check the entered information."
        401, 403 -> "The session is not authorized. Sign in again with the same account used on Web."
        404 -> "The requested itinerary could not be found."
        in 500..599 -> "LoomyTrip Backend is temporarily unavailable. Please retry."
        else -> fallback
    }
    else -> message?.takeIf { it.isNotBlank() } ?: fallback
}
