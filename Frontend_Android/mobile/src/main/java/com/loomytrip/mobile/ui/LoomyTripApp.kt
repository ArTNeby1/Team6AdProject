package com.loomytrip.mobile.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
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
import com.loomytrip.mobile.data.network.CrowdHintDto
import com.loomytrip.mobile.data.network.MapConfigDto
import com.loomytrip.mobile.data.network.NearbyRecommendationDto
import com.loomytrip.mobile.data.network.PlanningSessionSummaryDto
import com.loomytrip.mobile.data.network.TripRouteDto
import com.loomytrip.mobile.data.network.UserProfileDto
import com.loomytrip.mobile.data.repository.AiPlanningRepository
import com.loomytrip.mobile.data.repository.AuthRepository
import com.loomytrip.mobile.data.repository.MapRepository
import com.loomytrip.mobile.data.repository.LocalDestination
import com.loomytrip.mobile.data.repository.LocalExploreRepository
import com.loomytrip.mobile.data.repository.MockTripRepository
import com.loomytrip.mobile.data.repository.ProfileRepository
import com.loomytrip.mobile.data.repository.TripSyncRepository
import com.loomytrip.mobile.ui.screen.CityExploreScreen
import com.loomytrip.mobile.ui.screen.DestinationDetailScreen
import com.loomytrip.mobile.ui.screen.EditTripScreen
import com.loomytrip.mobile.ui.screen.ExploreCity
import com.loomytrip.mobile.ui.screen.HomeScreen
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.LoginScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.MapTripOption
import com.loomytrip.mobile.ui.screen.RegisterScreen
import com.loomytrip.mobile.ui.screen.ReviewExtractedScreen
import com.loomytrip.mobile.ui.screen.RouteScreen
import com.loomytrip.mobile.ui.screen.TripsListScreen
import com.loomytrip.mobile.ui.screen.DEFAULT_IMPORT_GUIDE
import java.io.IOException
import kotlinx.coroutines.launch
import retrofit2.HttpException

private enum class Destination(val route: String, val label: String) {
    Login("login", "Sign in"),
    Register("register", "Create account"),
    Home("home", "Home"),
    Import("import", "Smart AI Import"),
    Review("review", "Review places"),
    Explore("explore", "Explore"),
    Place("place", "Place Details"),
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
    val context = LocalContext.current
    val tripRepository = remember { MockTripRepository() }
    val extractedPlaces = remember { mutableStateListOf<ExtractedPlace>() }
    val trips = remember { mutableStateListOf<TripDto>() }
    val tripActivities = remember { mutableStateListOf<TripActivity>() }
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var selectedMapDay by remember { mutableIntStateOf(1) }
    var selectedEditDay by remember { mutableIntStateOf(1) }
    var editorDayCount by remember { mutableIntStateOf(1) }
    var authLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var activeTripId by remember { mutableStateOf<Long?>(null) }
    var tripsLoading by remember { mutableStateOf(false) }
    var tripsError by remember { mutableStateOf<String?>(null) }
    var deletingTripId by remember { mutableStateOf<Long?>(null) }
    var deleteTripError by remember { mutableStateOf<String?>(null) }
    var baselineSchedules by remember { mutableStateOf<List<ScheduleDto>>(emptyList()) }
    var planningSessionId by remember { mutableStateOf<Long?>(null) }
    var planningLoading by remember { mutableStateOf(false) }
    var planningError by remember { mutableStateOf<String?>(null) }
    var editSaving by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    var tripNameUpdating by remember { mutableStateOf(false) }
    var tripNameError by remember { mutableStateOf<String?>(null) }
    var startDateUpdating by remember { mutableStateOf(false) }
    var startDateError by remember { mutableStateOf<String?>(null) }
    var mapConfig by remember { mutableStateOf(MapConfigDto()) }
    var mapRoute by remember { mutableStateOf<TripRouteDto?>(null) }
    val nearbyPlaces = remember { mutableStateListOf<NearbyRecommendationDto>() }
    var crowdHint by remember { mutableStateOf<CrowdHintDto?>(null) }
    var showCrowd by remember { mutableStateOf(false) }
    var mapRouteLoading by remember { mutableStateOf(false) }
    var mapSuggestionLoading by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<UserProfileDto?>(null) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var selectedExploreCity by remember { mutableStateOf<ExploreCity?>(null) }
    var selectedLocalDestination by remember { mutableStateOf<LocalDestination?>(null) }
    val planningHistory = remember { mutableStateListOf<PlanningSessionSummaryDto>() }
    var planningHistoryLoading by remember { mutableStateOf(false) }
    var planningHistoryError by remember { mutableStateOf<String?>(null) }
    var preferencesSaving by remember { mutableStateOf(false) }
    var preferencesError by remember { mutableStateOf<String?>(null) }
    var itineraryGenerating by remember { mutableStateOf(false) }
    var itineraryGenerateError by remember { mutableStateOf<String?>(null) }
    var itineraryGenerateSummary by remember { mutableStateOf<String?>(null) }
    var itinerarySharing by remember { mutableStateOf(false) }
    var itineraryShareError by remember { mutableStateOf<String?>(null) }
    var importGuideSeed by remember { mutableStateOf(DEFAULT_IMPORT_GUIDE) }

    fun selectTrip(tripId: Long?) {
        val trip = trips.firstOrNull { it.id == tripId } ?: trips.firstOrNull()
        tripNameError = null
        startDateError = null
        preferencesError = null
        itineraryGenerateError = null
        itineraryGenerateSummary = null
        itineraryShareError = null
        activeTripId = trip?.id
        editorDayCount = trip?.durationDays ?: 1
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

    suspend fun loadMapRoute(tripId: Long?, day: Int) {
        if (tripId == null) {
            mapRoute = null
            mapError = "Select an itinerary to view its route."
            return
        }
        mapRouteLoading = true
        mapError = null
        mapRoute = null
        try {
            mapRoute = MapRepository.getRoute(tripId, day)
        } catch (error: Exception) {
            mapError = error.userMessage("Could not load route distance or the Google Maps link.")
        } finally {
            mapRouteLoading = false
        }
    }

    suspend fun loadMapConfig() {
        runCatching { MapRepository.getConfig() }
            .onSuccess { mapConfig = it }
        // The backend defaults are also kept in MapConfigDto so the map remains usable
        // if this small config request is temporarily unavailable.
    }

    suspend fun loadNearby(latitude: Double, longitude: Double) {
        mapSuggestionLoading = true
        mapError = null
        try {
            val recommendations = MapRepository.getNearby(latitude, longitude)
            nearbyPlaces.clear()
            nearbyPlaces.addAll(recommendations)
        } catch (error: Exception) {
            mapError = error.userMessage("Could not load nearby recommendations.")
        } finally {
            mapSuggestionLoading = false
        }
    }

    suspend fun loadCrowd(date: String?) {
        mapSuggestionLoading = true
        mapError = null
        try {
            crowdHint = MapRepository.getCrowdHint(date)
        } catch (error: Exception) {
            showCrowd = false
            mapError = error.userMessage("Could not load the crowd estimate.")
        } finally {
            mapSuggestionLoading = false
        }
    }

    suspend fun loadProfile() {
        profileLoading = true
        profileError = null
        try {
            profile = ProfileRepository.getMyProfile()
        } catch (error: Exception) {
            profileError = error.userMessage("Could not load your profile.")
        } finally {
            profileLoading = false
        }
    }

    suspend fun loadPlanningHistory() {
        planningHistoryLoading = true
        planningHistoryError = null
        try {
            planningHistory.clear()
            planningHistory.addAll(AiPlanningRepository.sessions())
        } catch (error: Exception) {
            planningHistoryError = error.userMessage("Could not load previous AI imports.")
        } finally {
            planningHistoryLoading = false
        }
    }

    suspend fun deleteTrip(tripId: Long): Boolean {
        deletingTripId = tripId
        deleteTripError = null
        return try {
            TripSyncRepository.deleteTrip(tripId)
            loadTrips(preferredTripId = if (activeTripId == tripId) null else activeTripId)
            true
        } catch (error: Exception) {
            deleteTripError = error.userMessage("Could not delete this itinerary.")
            false
        } finally {
            deletingTripId = null
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
        if (signedInEmail != null && current == Destination.Profile) {
            if (trips.isEmpty()) loadTrips()
            loadProfile()
        }
        if (signedInEmail != null && current == Destination.Import) {
            loadPlanningHistory()
        }
    }

    val activeTrip = trips.firstOrNull { it.id == activeTripId } ?: trips.firstOrNull()

    LaunchedEffect(route, activeTripId, selectedMapDay, signedInEmail) {
        if (signedInEmail != null && current == Destination.Map) {
            nearbyPlaces.clear()
            crowdHint = null
            showCrowd = false
            loadMapConfig()
            loadMapRoute(activeTripId, selectedMapDay)
        }
    }

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
                    onStartPlanning = {
                        importGuideSeed = DEFAULT_IMPORT_GUIDE
                        planningError = null
                        navController.navigate(Destination.Import.route)
                    },
                    onExploreCity = { city ->
                        selectedExploreCity = city
                        navController.navigate(Destination.Explore.route)
                    },
                    onViewTrip = {
                        navigateToRoot(if (activeTrip == null) Destination.Trips else Destination.Itinerary)
                    },
                    onViewAllTrips = { navigateToRoot(Destination.Trips) },
                    tripName = activeTrip?.tripName,
                    tripDayCount = activeTrip?.durationDays ?: 1,
                    tripStopCount = tripActivities.size
                )
            }
            composable(Destination.Explore.route) {
                val city = selectedExploreCity
                if (city != null) {
                    val localDestinations = LocalExploreRepository.destinationsForCity(city.city)
                    CityExploreScreen(
                        city = city,
                        destinations = localDestinations,
                        onDestinationSelected = { destination ->
                            selectedLocalDestination = destination
                            navController.navigate(Destination.Place.route)
                        },
                        onPlanCity = {
                            val placeNames = localDestinations.take(4).map { it.name }
                            importGuideSeed = if (placeNames.isEmpty()) {
                                "Plan a one-day trip in ${city.city}."
                            } else {
                                "Plan a one-day trip in ${city.city}. Visit ${placeNames.joinToString(", ")}."
                            }
                            planningError = null
                            navController.navigate(Destination.Import.route)
                        }
                    )
                }
            }
            composable(Destination.Place.route) {
                selectedLocalDestination?.let { destination ->
                    DestinationDetailScreen(
                        destination = destination,
                        onPlanDestination = {
                            importGuideSeed = "Plan a one-day trip in ${destination.city} including ${destination.name}."
                            planningError = null
                            navController.navigate(Destination.Import.route)
                        }
                    )
                }
            }
            composable(Destination.Import.route) {
                ImportGuideScreen(
                    initialGuide = importGuideSeed,
                    isLoading = planningLoading,
                    errorMessage = planningError,
                    history = planningHistory,
                    isHistoryLoading = planningHistoryLoading,
                    historyErrorMessage = planningHistoryError,
                    onRefreshHistory = { scope.launch { loadPlanningHistory() } },
                    onHistorySelected = { session ->
                        scope.launch {
                            planningHistoryLoading = true
                            planningHistoryError = null
                            try {
                                val confirmedTripId = session.confirmedTripId
                                if (confirmedTripId != null) {
                                    loadTrips(confirmedTripId)
                                    navController.navigate(Destination.Itinerary.route)
                                } else {
                                    val resumed = AiPlanningRepository.resumeSession(session.id)
                                    if (resumed.places.isEmpty()) {
                                        error("This draft has no extracted places yet.")
                                    }
                                    planningSessionId = resumed.sessionId
                                    extractedPlaces.clear()
                                    extractedPlaces.addAll(resumed.places)
                                    navController.navigate(Destination.Review.route)
                                }
                            } catch (error: Exception) {
                                planningHistoryError = error.userMessage("Could not open this AI import.")
                            } finally {
                                planningHistoryLoading = false
                            }
                        }
                    },
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
                                    val confirmedTrip = try {
                                        TripSyncRepository.fetchTrip(newTripId)
                                    } catch (firstLoadError: Exception) {
                                        loadTrips(newTripId)
                                        trips.firstOrNull { it.id == newTripId } ?: throw firstLoadError
                                    }
                                    val sortedTrips = TripSyncRepository.sortForDisplay(
                                        trips.filterNot { it.id == confirmedTrip.id } + confirmedTrip
                                    )
                                    trips.clear()
                                    trips.addAll(sortedTrips)
                                    selectTrip(confirmedTrip.id)
                                    selectedEditDay = 1
                                    selectedMapDay = 1
                                    planningSessionId = null
                                    navController.navigate(Destination.Itinerary.route) {
                                        popUpTo(Destination.Home.route)
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
            composable(Destination.Trips.route) {
                TripsListScreen(
                    trips = trips,
                    isLoading = tripsLoading,
                    errorMessage = tripsError,
                    deleteErrorMessage = deleteTripError,
                    deletingTripId = deletingTripId,
                    onRefresh = { scope.launch { loadTrips() } },
                    onStartPlanning = {
                        importGuideSeed = DEFAULT_IMPORT_GUIDE
                        navController.navigate(Destination.Import.route)
                    },
                    onTripSelected = { tripId ->
                        selectTrip(tripId)
                        navController.navigate(Destination.Itinerary.route)
                    },
                    onDeleteTrip = { tripId ->
                        scope.launch { deleteTrip(tripId) }
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
                    isUpdatingTripName = tripNameUpdating,
                    tripNameError = tripNameError,
                    isUpdatingStartDate = startDateUpdating,
                    startDateError = startDateError,
                    isDeletingTrip = deletingTripId == activeTrip?.id,
                    deleteErrorMessage = deleteTripError,
                    travelStyle = activeTrip?.travelStyle,
                    preferTransport = activeTrip?.preferTransport,
                    isSavingPreferences = preferencesSaving,
                    preferencesErrorMessage = preferencesError,
                    isGenerating = itineraryGenerating,
                    generateErrorMessage = itineraryGenerateError,
                    generateSummary = itineraryGenerateSummary,
                    isSharing = itinerarySharing,
                    shareErrorMessage = itineraryShareError,
                    onTripNameChange = { newName ->
                        val tripId = activeTrip?.id
                        if (tripId == null) {
                            tripNameError = "Select an itinerary before changing its name."
                        } else {
                            scope.launch {
                                tripNameUpdating = true
                                tripNameError = null
                                try {
                                    val updated = TripSyncRepository.updateTripName(tripId, newName)
                                    val sortedTrips = TripSyncRepository.sortForDisplay(
                                        trips.filterNot { it.id == updated.id } + updated
                                    )
                                    trips.clear()
                                    trips.addAll(sortedTrips)
                                    selectTrip(updated.id)
                                } catch (error: Exception) {
                                    tripNameError = error.userMessage("Could not rename this itinerary.")
                                } finally {
                                    tripNameUpdating = false
                                }
                            }
                        }
                    },
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
                        editorDayCount = activeTrip?.durationDays ?: 1
                        editError = null
                        navController.navigate(Destination.Edit.route)
                    },
                    onSavePreferences = { style, transport ->
                        val tripId = activeTrip?.id
                        if (tripId == null) {
                            preferencesError = "Select an itinerary before changing its preferences."
                        } else {
                            scope.launch {
                                preferencesSaving = true
                                preferencesError = null
                                try {
                                    val updated = TripSyncRepository.updatePreferences(tripId, style, transport)
                                    val index = trips.indexOfFirst { it.id == updated.id }
                                    if (index >= 0) trips[index] = updated
                                    selectTrip(updated.id)
                                } catch (error: Exception) {
                                    preferencesError = error.userMessage("Could not save this trip's preferences.")
                                } finally {
                                    preferencesSaving = false
                                }
                            }
                        }
                    },
                    onSmartReorder = {
                        val tripId = activeTrip?.id
                        if (tripId == null) {
                            itineraryGenerateError = "Select an itinerary before using smart reorder."
                        } else {
                            scope.launch {
                                itineraryGenerating = true
                                itineraryGenerateError = null
                                itineraryGenerateSummary = null
                                try {
                                    val (result, updated) = TripSyncRepository.generateItinerary(tripId)
                                    val index = trips.indexOfFirst { it.id == updated.id }
                                    if (index >= 0) trips[index] = updated
                                    selectTrip(updated.id)
                                    val stopCount = result.days.sumOf { it.stops.size }
                                    val weather = result.days.firstNotNullOfOrNull { it.weatherSummary?.takeIf(String::isNotBlank) }
                                    itineraryGenerateSummary = buildString {
                                        append("AI reorganized $stopCount stops across ${result.days.size} day(s).")
                                        if (weather != null) append(" $weather")
                                    }
                                } catch (error: Exception) {
                                    itineraryGenerateError = error.userMessage("AI could not reorganize this itinerary.")
                                } finally {
                                    itineraryGenerating = false
                                }
                            }
                        }
                    },
                    onShare = {
                        val tripId = activeTrip?.id
                        if (tripId == null) {
                            itineraryShareError = "Select an itinerary before sharing."
                        } else {
                            scope.launch {
                                itinerarySharing = true
                                itineraryShareError = null
                                try {
                                    val shareUrl = TripSyncRepository.createShareLink(tripId)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, activeTrip?.tripName ?: "LoomyTrip itinerary")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "${activeTrip?.tripName ?: "LoomyTrip itinerary"}\n$shareUrl"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share itinerary"))
                                } catch (error: Exception) {
                                    itineraryShareError = error.userMessage("Could not create the share link.")
                                } finally {
                                    itinerarySharing = false
                                }
                            }
                        }
                    },
                    onDeleteTrip = {
                        val tripId = activeTrip?.id
                        if (tripId != null) {
                            scope.launch {
                                if (deleteTrip(tripId)) {
                                    navigateToRoot(Destination.Trips)
                                }
                            }
                        }
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
                    mapConfig = mapConfig,
                    routeSummary = mapRoute,
                    nearbyPlaces = nearbyPlaces,
                    crowdHint = crowdHint,
                    showCrowd = showCrowd,
                    isMapDataLoading = mapRouteLoading || mapSuggestionLoading,
                    mapDataError = mapError,
                    onTripSelected = { tripId ->
                        selectTrip(tripId)
                        selectedMapDay = 1
                    },
                    onDaySelected = { day ->
                        if (selectedMapDay != day) {
                            selectedMapDay = day
                            nearbyPlaces.clear()
                        }
                    },
                    onMapClick = { latitude, longitude ->
                        scope.launch { loadNearby(latitude, longitude) }
                    },
                    onToggleCrowd = {
                        showCrowd = !showCrowd
                        if (showCrowd && crowdHint == null) {
                            scope.launch { loadCrowd(activeTrip?.startDate) }
                        }
                    },
                    onRetryRoute = {
                        scope.launch { loadMapRoute(activeTrip?.id, selectedMapDay) }
                    },
                    onEdit = { day ->
                        selectedEditDay = day
                        editorDayCount = activeTrip?.durationDays ?: 1
                        editError = null
                        navController.navigate(Destination.Edit.route)
                    }
                )
            }
            composable(Destination.Edit.route) {
                EditTripScreen(
                    activities = tripActivities,
                    initialDay = selectedEditDay,
                    totalDays = editorDayCount,
                    onReorder = { id, day, position ->
                        replaceTripActivities(
                            tripRepository.reorderActivity(tripActivities.toList(), id, day, position)
                        )
                    },
                    onDelete = { id ->
                        replaceTripActivities(
                            tripRepository.deleteActivity(tripActivities.toList(), id)
                        )
                    },
                    onRestore = { activity, position ->
                        replaceTripActivities(
                            tripRepository.restoreActivity(
                                tripActivities.toList(),
                                activity,
                                position
                            )
                        )
                    },
                    onAdd = { day, title, time ->
                        replaceTripActivities(
                            tripRepository.addActivity(tripActivities.toList(), day, title, time)
                        )
                    },
                    onUpdateActivity = { id, startTime ->
                        replaceTripActivities(
                            tripRepository.updateActivity(tripActivities.toList(), id, startTime)
                        )
                    },
                    onAddDay = {
                        editorDayCount += 1
                        selectedEditDay = editorDayCount
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
                                        baselineSchedules,
                                        editorDayCount
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
                    onSaveAndSmartReorder = {
                        val tripId = activeTripId
                        if (tripId == null) {
                            editError = "Select a Backend itinerary before using smart reorder."
                        } else {
                            scope.launch {
                                editSaving = true
                                itineraryGenerating = true
                                editError = null
                                try {
                                    val saved = TripSyncRepository.syncEdits(
                                        tripId,
                                        tripActivities.toList(),
                                        baselineSchedules,
                                        editorDayCount
                                    )
                                    baselineSchedules = saved.schedules
                                    val (result, updated) = TripSyncRepository.generateItinerary(tripId)
                                    val index = trips.indexOfFirst { it.id == updated.id }
                                    if (index >= 0) trips[index] = updated else trips.add(0, updated)
                                    selectTrip(updated.id)
                                    val stopCount = result.days.sumOf { it.stops.size }
                                    itineraryGenerateSummary = "AI reorganized $stopCount stops."
                                    navController.popBackStack()
                                } catch (error: Exception) {
                                    editError = error.userMessage("Could not save and reorganize this itinerary.")
                                } finally {
                                    editSaving = false
                                    itineraryGenerating = false
                                }
                            }
                        }
                    },
                    isSaving = editSaving,
                    isGenerating = itineraryGenerating,
                    errorMessage = editError
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    email = signedInEmail.orEmpty(),
                    profile = profile,
                    tripCount = trips.size,
                    isLoading = profileLoading,
                    errorMessage = profileError,
                    onRetry = { scope.launch { loadProfile() } },
                    onSignOut = {
                        signedInEmail = null
                        extractedPlaces.clear()
                        trips.clear()
                        tripActivities.clear()
                        activeTripId = null
                        baselineSchedules = emptyList()
                        startDateError = null
                        startDateUpdating = false
                        tripNameError = null
                        tripNameUpdating = false
                        mapConfig = MapConfigDto()
                        mapRoute = null
                        nearbyPlaces.clear()
                        crowdHint = null
                        showCrowd = false
                        profile = null
                        selectedExploreCity = null
                        selectedLocalDestination = null
                        planningHistory.clear()
                        planningHistoryError = null
                        preferencesError = null
                        itineraryGenerateError = null
                        itineraryGenerateSummary = null
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
private fun ProfileScreen(
    email: String,
    profile: UserProfileDto?,
    tripCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Traveler account", fontSize = 27.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (isLoading && profile == null) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        profile?.username?.takeIf { it.isNotBlank() } ?: email.substringBefore('@'),
                        fontSize = 22.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(profile?.email ?: email.ifBlank { "traveler@loomytrip.com" })
                    Text(
                        listOfNotNull(
                            profile?.gender?.takeIf { it.isNotBlank() },
                            profile?.age?.let { "$it years old" }
                        ).ifEmpty { listOf("Profile details not set") }.joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        fontSize = 13.sp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileValueCard("Trips", tripCount.toString(), Modifier.weight(1f))
                Card(
                    modifier = Modifier.weight(2f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text("Preferences belong to each trip", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "Open an itinerary to choose its style and transport.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
        }
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}

@Composable
private fun ProfileValueCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

private fun Throwable.userMessage(fallback: String): String = when (this) {
    is IOException -> "Cannot reach LoomyTrip Backend. Check the network and try again."
    is HttpException -> when (code()) {
        400 -> "The request was not accepted. Check the entered information."
        401, 403 -> "The session is not authorized."
        404 -> "The requested itinerary could not be found."
        in 500..599 -> "LoomyTrip Backend is temporarily unavailable. Please retry."
        else -> fallback
    }
    else -> message?.takeIf { it.isNotBlank() } ?: fallback
}
