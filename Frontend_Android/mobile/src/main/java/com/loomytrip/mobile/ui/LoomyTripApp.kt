package com.loomytrip.mobile.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.loomytrip.mobile.data.model.TripPlan
import com.loomytrip.mobile.data.remote.ExtractionResponse
import com.loomytrip.mobile.data.remote.RecommendationResponse
import com.loomytrip.mobile.data.repository.OfflineAiPlanningRepository
import com.loomytrip.mobile.data.repository.RemoteAiPlanningRepository
import com.loomytrip.mobile.data.repository.MockTripRepository
import com.loomytrip.mobile.data.repository.BackendRepository
import com.loomytrip.mobile.data.repository.toExtractedPlaces
import com.loomytrip.mobile.data.repository.toExtractionResponse
import com.loomytrip.mobile.notification.TRIP_NOTIFICATION_PERMISSION
import com.loomytrip.mobile.notification.canShowTripNotification
import com.loomytrip.mobile.notification.showTripReadyNotification
import com.loomytrip.mobile.ui.screen.AttractionScreen
import com.loomytrip.mobile.ui.screen.AiRecommendationScreen
import com.loomytrip.mobile.ui.screen.EditTripScreen
import com.loomytrip.mobile.ui.screen.HomeScreen
import com.loomytrip.mobile.ui.screen.ImportGuideScreen
import com.loomytrip.mobile.ui.screen.LoginScreen
import com.loomytrip.mobile.ui.screen.MapScreen
import com.loomytrip.mobile.ui.screen.RegisterScreen
import com.loomytrip.mobile.ui.screen.ReviewExtractedScreen
import com.loomytrip.mobile.ui.screen.RouteScreen
import kotlinx.coroutines.launch

private enum class Destination(val route: String, val label: String) {
    Login("login", "Sign in"),
    Register("register", "Create account"),
    Home("home", "Home"),
    Import("import", "Import guide"),
    Review("review", "Review places"),
    AiResult("ai-result", "AI plan"),
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val remoteAiRepository = remember { RemoteAiPlanningRepository() }
    val offlineAiRepository = remember { OfflineAiPlanningRepository() }
    val backendRepository = remember { BackendRepository(context) }
    val tripRepository = remember { MockTripRepository() }
    val extractedPlaces = remember { mutableStateListOf<ExtractedPlace>() }
    val savedTripPlans = remember {
        mutableStateListOf<TripPlan>().apply { addAll(tripRepository.savedTrips()) }
    }
    var signedInEmail by remember { mutableStateOf(backendRepository.savedEmail()) }
    var isBackendSession by remember { mutableStateOf(backendRepository.hasSession()) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var activeTripId by remember { mutableStateOf(savedTripPlans.first().id) }
    var selectedMapDay by remember { mutableIntStateOf(1) }
    var selectedEditDay by remember { mutableIntStateOf(1) }
    var travelPreference by rememberSaveable { mutableStateOf("Culture") }
    var tripRemindersEnabled by rememberSaveable { mutableStateOf(true) }
    var importNoticeVisible by remember { mutableStateOf(false) }
    var extractionResult by remember { mutableStateOf<ExtractionResponse?>(null) }
    var recommendationResult by remember { mutableStateOf<RecommendationResponse?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var isRecommending by remember { mutableStateOf(false) }
    var planningMessage by remember { mutableStateOf<String?>(null) }
    var isLiveAiResult by remember { mutableStateOf(false) }
    var resultSourceLabel by remember { mutableStateOf("Offline fallback") }
    var backendPlanningSessionId by remember { mutableStateOf<Long?>(null) }
    var confirmedBackendTrip by remember { mutableStateOf<TripPlan?>(null) }
    var pendingNotificationPlaces by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingNotificationPlaces > 0) {
            showTripReadyNotification(context, pendingNotificationPlaces)
        }
        pendingNotificationPlaces = 0
    }
    val activeTrip = savedTripPlans.first { it.id == activeTripId }

    fun notifyTripReady(placeCount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canShowTripNotification(context)) {
            pendingNotificationPlaces = placeCount
            notificationPermissionLauncher.launch(TRIP_NOTIFICATION_PERMISSION)
        } else {
            showTripReadyNotification(context, placeCount)
        }
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

    fun updateActiveTrip(transform: (TripPlan) -> TripPlan) {
        val index = savedTripPlans.indexOfFirst { it.id == activeTripId }
        if (index >= 0) savedTripPlans[index] = transform(savedTripPlans[index])
    }

    fun mergeBackendTrips(remoteTrips: List<TripPlan>) {
        val localTrips = savedTripPlans.filterNot { it.id.startsWith("backend-") }
        savedTripPlans.clear()
        savedTripPlans.addAll(remoteTrips + localTrips)
        if (remoteTrips.isNotEmpty()) {
            activeTripId = remoteTrips.first().id
        }
    }

    fun useTrip(trip: TripPlan) {
        val existingIndex = savedTripPlans.indexOfFirst { it.id == trip.id }
        if (existingIndex >= 0) savedTripPlans[existingIndex] = trip else savedTripPlans.add(0, trip)
        activeTripId = trip.id
        selectedMapDay = 1
        importNoticeVisible = tripRemindersEnabled
        if (tripRemindersEnabled) notifyTripReady(trip.activities.size)
        navController.navigate(Destination.Route.route) {
            popUpTo(Destination.Home.route)
        }
    }

    LaunchedEffect(isBackendSession) {
        if (isBackendSession) {
            runCatching { backendRepository.savedTrips() }
                .onSuccess(::mergeBackendTrips)
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
                BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == item.destination.route
                            } == true,
                            onClick = { navigateToRoot(item.destination) },
                            icon = { Icon(item.icon, contentDescription = item.destination.label) },
                            label = { Text(item.destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
                            )
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
                    isLoading = isAuthenticating,
                    serverError = authMessage,
                    onLogin = { email, password ->
                        if (!isAuthenticating) {
                            isAuthenticating = true
                            authMessage = null
                            coroutineScope.launch {
                                runCatching { backendRepository.login(email, password) }
                                    .onSuccess { session ->
                                        signedInEmail = session.email
                                        isBackendSession = true
                                        runCatching { backendRepository.savedTrips() }
                                            .onSuccess(::mergeBackendTrips)
                                        navController.navigate(Destination.Home.route) {
                                            popUpTo(Destination.Login.route) { inclusive = true }
                                        }
                                    }
                                    .onFailure { error ->
                                        authMessage = error.message ?: "Unable to sign in to the Backend."
                                    }
                                isAuthenticating = false
                            }
                        }
                    },
                    onDemoLogin = {
                        backendRepository.signOut()
                        signedInEmail = "demo@loomytrip.local"
                        isBackendSession = false
                        authMessage = null
                        navController.navigate(Destination.Home.route) {
                            popUpTo(Destination.Login.route) { inclusive = true }
                        }
                    },
                    onCreateAccount = { navController.navigate(Destination.Register.route) }
                )
            }
            composable(Destination.Register.route) {
                RegisterScreen(
                    isLoading = isAuthenticating,
                    serverError = authMessage,
                    onRegister = { name, email, password ->
                        if (!isAuthenticating) {
                            isAuthenticating = true
                            authMessage = null
                            coroutineScope.launch {
                                runCatching { backendRepository.register(name, email, password) }
                                    .onSuccess { session ->
                                        signedInEmail = session.email
                                        isBackendSession = true
                                        mergeBackendTrips(emptyList())
                                        navController.navigate(Destination.Home.route) {
                                            popUpTo(Destination.Login.route) { inclusive = true }
                                        }
                                    }
                                    .onFailure { error ->
                                        authMessage = error.message ?: "Unable to create the Backend account."
                                    }
                                isAuthenticating = false
                            }
                        }
                    },
                    onBackToLogin = {
                        authMessage = null
                        navController.popBackStack()
                    }
                )
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    currentTripTitle = activeTrip.title,
                    currentTripDate = activeTrip.dateLabel,
                    currentTripDays = activeTrip.totalDays,
                    currentTripStops = activeTrip.activities.size,
                    travelPreference = travelPreference,
                    onStartPlanning = { navController.navigate(Destination.Import.route) },
                    onOpenTrip = { navController.navigate(Destination.Route.route) }
                )
            }
            composable(Destination.Import.route) {
                ImportGuideScreen(
                    isLoading = isExtracting,
                    errorMessage = planningMessage,
                    onExtract = { sourceText ->
                        if (!isExtracting) {
                            isExtracting = true
                            planningMessage = null
                            coroutineScope.launch {
                                recommendationResult = null
                                confirmedBackendTrip = null
                                backendPlanningSessionId = null
                                extractedPlaces.clear()

                                val backendDetail = if (isBackendSession) {
                                    runCatching { backendRepository.createPlanningSession(sourceText) }.getOrNull()
                                } else {
                                    null
                                }

                                if (backendDetail != null && backendDetail.draftPlaces.isNotEmpty()) {
                                    backendPlanningSessionId = backendDetail.id
                                    extractionResult = backendDetail.toExtractionResponse()
                                    extractedPlaces.addAll(backendDetail.toExtractedPlaces())
                                    isLiveAiResult = true
                                    resultSourceLabel = "LoomyTrip Backend"
                                } else {
                                    val liveAttempt = runCatching { remoteAiRepository.extract(sourceText) }
                                    val extraction = liveAttempt.getOrElse {
                                        planningMessage = if (isBackendSession) {
                                            "Backend is online, but AI extraction is not ready. Using the offline demo response."
                                        } else {
                                            "Live AI is unavailable. Using the offline demo response."
                                        }
                                        offlineAiRepository.extract(sourceText)
                                    }
                                    isLiveAiResult = liveAttempt.isSuccess
                                    resultSourceLabel = if (liveAttempt.isSuccess) "Direct AI service" else "Offline fallback"
                                    extractionResult = extraction
                                    extractedPlaces.addAll(
                                        extraction.places.mapIndexed { index, place ->
                                            ExtractedPlace(
                                                id = "ai-$index",
                                                name = place.name,
                                                category = place.type.replaceFirstChar { it.uppercase() },
                                                activities = place.activities,
                                                latitude = place.coords?.lat,
                                                longitude = place.coords?.lng
                                            )
                                        }
                                    )
                                }
                                isExtracting = false
                                navController.navigate(Destination.Review.route)
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
                    onRemove = { id ->
                        extractedPlaces.removeAll { it.id == id }
                        val backendPlaceId = id.removePrefix("backend-").toLongOrNull()
                        if (backendPlanningSessionId != null && backendPlaceId != null) {
                            coroutineScope.launch {
                                runCatching { backendRepository.deleteDraftPlace(backendPlaceId) }
                                    .onFailure {
                                        backendPlanningSessionId = null
                                        planningMessage = "The place was removed locally, but Backend sync failed. Offline planning will be used."
                                    }
                            }
                        }
                    },
                    isConfirming = isRecommending,
                    errorMessage = planningMessage,
                    onConfirm = {
                        val extraction = extractionResult
                        if (extraction != null && !isRecommending) {
                            isRecommending = true
                            planningMessage = null
                            coroutineScope.launch {
                                val includedNames = extractedPlaces
                                    .filter { it.isIncluded }
                                    .map { it.name }
                                    .toSet()
                                val canConfirmWithBackend = backendPlanningSessionId != null &&
                                    extractedPlaces.all { it.isIncluded }
                                val backendAttempt = if (canConfirmWithBackend) {
                                    runCatching {
                                        backendRepository.confirmPlanningSession(backendPlanningSessionId!!)
                                    }
                                } else {
                                    Result.failure(IllegalStateException("Backend draft was changed locally"))
                                }

                                val recommendation = if (backendAttempt.isSuccess) {
                                    val backendPlan = backendAttempt.getOrThrow()
                                    confirmedBackendTrip = backendPlan.trip
                                    isLiveAiResult = true
                                    resultSourceLabel = "LoomyTrip Backend"
                                    backendPlan.recommendation
                                } else {
                                    val directAttempt = if (isLiveAiResult) {
                                        runCatching {
                                            remoteAiRepository.recommend(
                                                extraction,
                                                includedNames,
                                                travelPreference
                                            )
                                        }
                                    } else {
                                        Result.failure(IllegalStateException("Offline extraction"))
                                    }
                                    val fallback = directAttempt.getOrElse {
                                        planningMessage = "Backend AI optimisation is unavailable. Showing the offline result."
                                        offlineAiRepository.recommend(extraction, includedNames, travelPreference)
                                    }
                                    isLiveAiResult = directAttempt.isSuccess
                                    resultSourceLabel = if (directAttempt.isSuccess) "Direct AI service" else "Offline fallback"
                                    fallback
                                }
                                recommendationResult = recommendation
                                isRecommending = false
                                navController.navigate(Destination.AiResult.route)
                            }
                        }
                    },
                    onImportAgain = {
                        planningMessage = null
                        navController.navigate(Destination.Import.route)
                    }
                )
            }
            composable(Destination.AiResult.route) {
                recommendationResult?.let { result ->
                    AiRecommendationScreen(
                        result = result,
                        isLiveResult = isLiveAiResult,
                        sourceLabel = resultSourceLabel,
                        onUseItinerary = { selectedSuggestions ->
                            val backendTrip = confirmedBackendTrip
                            val backendTripId = backendTrip?.id?.removePrefix("backend-")?.toLongOrNull()
                            if (backendTrip != null && backendTripId != null && selectedSuggestions.isNotEmpty()) {
                                coroutineScope.launch {
                                    val trip = runCatching {
                                        backendRepository.addSuggestedPlaces(backendTripId, selectedSuggestions)
                                    }.getOrElse {
                                        planningMessage = "Suggested places could not be saved to Backend. The confirmed route is still available."
                                        backendTrip
                                    }
                                    useTrip(trip)
                                }
                            } else {
                                useTrip(
                                    backendTrip ?: buildAiTrip(
                                        extraction = extractionResult,
                                        recommendation = result,
                                        selectedSuggestions = selectedSuggestions
                                    )
                                )
                            }
                        }
                    )
                }
            }
            composable(Destination.Attraction.route) {
                AttractionScreen(onAddToTrip = { navController.navigate(Destination.Route.route) })
            }
            composable(Destination.Route.route) {
                RouteScreen(
                    tripTitle = activeTrip.title,
                    tripDateLabel = activeTrip.dateLabel,
                    dayLabels = activeTrip.dayLabels,
                    activities = activeTrip.activities,
                    totalDays = activeTrip.totalDays,
                    importedPlacesCount = if (importNoticeVisible) extractedPlaces.size else 0,
                    onReviewImported = { navController.navigate(Destination.Review.route) },
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
                    trips = savedTripPlans,
                    activeTripId = activeTripId,
                    initialDay = selectedMapDay,
                    onOpenTrip = { trip, day ->
                        activeTripId = trip.id
                        selectedMapDay = day
                        navigateToRoot(Destination.Route)
                    }
                )
            }
            composable(Destination.Edit.route) {
                EditTripScreen(
                    activities = activeTrip.activities,
                    initialDay = selectedEditDay,
                    totalDays = activeTrip.totalDays,
                    onReorder = { id, day, index ->
                        updateActiveTrip { trip ->
                            trip.copy(
                                activities = tripRepository.reorderActivity(
                                    trip.activities,
                                    id,
                                    day,
                                    index
                                )
                            )
                        }
                    },
                    onDelete = { id ->
                        updateActiveTrip { trip ->
                            trip.copy(activities = tripRepository.deleteActivity(trip.activities, id))
                        }
                    },
                    onRestore = { activity, index ->
                        updateActiveTrip { trip ->
                            trip.copy(
                                activities = tripRepository.restoreActivity(
                                    trip.activities,
                                    activity,
                                    index
                                )
                            )
                        }
                    },
                    onAdd = { day, title, time ->
                        updateActiveTrip { trip ->
                            trip.copy(
                                activities = tripRepository.addActivity(
                                    trip.activities,
                                    day,
                                    title,
                                    time
                                )
                            )
                        }
                    },
                    onUpdateActivity = { id, startTime, durationMinutes ->
                        updateActiveTrip { trip ->
                            trip.copy(
                                activities = tripRepository.updateActivity(
                                    trip.activities,
                                    id,
                                    startTime,
                                    durationMinutes
                                )
                            )
                        }
                    },
                    onAddDay = {
                        updateActiveTrip { trip -> trip.copy(totalDays = trip.totalDays + 1) }
                    },
                    onSave = { navController.popBackStack() }
                )
            }
            composable(Destination.Profile.route) {
                ProfileScreen(
                    email = signedInEmail.orEmpty(),
                    isBackendSession = isBackendSession,
                    travelPreference = travelPreference,
                    onTravelPreferenceChange = { travelPreference = it },
                    remindersEnabled = tripRemindersEnabled,
                    onRemindersEnabledChange = { enabled ->
                        tripRemindersEnabled = enabled
                        if (!enabled) importNoticeVisible = false
                    },
                    onSignOut = {
                        backendRepository.signOut()
                        signedInEmail = null
                        isBackendSession = false
                        backendPlanningSessionId = null
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

private fun buildAiTrip(
    extraction: ExtractionResponse?,
    recommendation: RecommendationResponse,
    selectedSuggestions: Set<String>
): TripPlan {
    val orderedActivities = recommendation.orderedStops.sortedBy { it.order }.mapIndexed { index, stop ->
        TripActivity(
            id = "ai-stop-$index",
            title = stop.name,
            category = stop.type.replaceFirstChar { it.uppercase() },
            day = 1,
            startTime = when (stop.timeOfDay?.lowercase()) {
                "morning" -> "09:00"
                "afternoon" -> "14:00"
                "evening" -> "18:00"
                else -> "%02d:00".format(9 + index * 2)
            },
            durationMinutes = 90,
            address = stop.reason.ifBlank { "AI planned stop" },
            latitude = stop.lat ?: 1.2900 + index * 0.004,
            longitude = stop.lng ?: 103.8500 + index * 0.004
        )
    }
    val suggestedActivities = recommendation.suggestedAdditions
        .filter { it.name in selectedSuggestions }
        .mapIndexed { index, suggestion ->
            TripActivity(
                id = "ai-suggestion-$index",
                title = suggestion.name,
                category = suggestion.type.replaceFirstChar { it.uppercase() },
                day = 1,
                startTime = "%02d:00".format(9 + (orderedActivities.size + index) * 2),
                durationMinutes = 90,
                address = suggestion.reason.removePrefix("[MOCK] "),
                latitude = suggestion.lat ?: 1.3000 + index * 0.003,
                longitude = suggestion.lng ?: 103.8600 + index * 0.003
            )
        }
    val dates = extraction?.dates.orEmpty()
    return TripPlan(
        id = "ai-singapore",
        title = extraction?.destination?.ifBlank { "Singapore" } ?: "Singapore",
        dateLabel = dates.firstOrNull() ?: "AI planned day",
        totalDays = 1,
        activities = orderedActivities + suggestedActivities,
        dayLabels = listOf(dates.firstOrNull() ?: "Flexible date")
    )
}

@Composable
private fun ProfileScreen(
    email: String,
    isBackendSession: Boolean,
    travelPreference: String,
    onTravelPreferenceChange: (String) -> Unit,
    remindersEnabled: Boolean,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        Text("Traveler account", fontSize = 27.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(email.ifBlank { "Offline demo user" })
        Text(
            if (isBackendSession) {
                "Connected to LoomyTrip Backend. Your JWT session is stored on this device."
            } else {
                "Offline demo account. No Backend data will be changed."
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        Text("Travel preference", style = MaterialTheme.typography.titleMedium)
        listOf("Culture", "Food", "Nature", "Shopping").chunked(2).forEach { rowOptions ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = travelPreference == option,
                        onClick = { onTravelPreferenceChange(option) },
                        label = { Text(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text("In-app trip reminders", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        "Show status updates after an import finishes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = onRemindersEnabledChange
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
            ) {
                Text("Offline demo ready", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(
                    "Core trip screens use on-device sample data and remain available without a connection.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}
