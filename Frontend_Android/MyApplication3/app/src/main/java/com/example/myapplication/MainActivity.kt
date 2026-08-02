package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    // Shared state for all trips
    val trips = remember {
        mutableStateMapOf<String, TripData>(
            "chiang_mai_3" to TripData(
                id = "chiang_mai_3", 
                title = "清迈 3 日", 
                activities = mutableStateListOf(
                    TripActivity("1", "契迪龙寺", 1, "09:00", "1.5h"),
                    TripActivity("2", "塔佩门", 1, "11:00", "1h"),
                    TripActivity("3", "尼曼路 · 午餐", 1, "13:00", "1.5h"),
                    TripActivity("4", "周日夜市", 1, "17:00", "2h"),
                    TripActivity("5", "素贴寺 (双龙寺)", 2, "08:30", "2h"),
                    TripActivity("6", "蒲屏皇宫", 2, "11:30", "1.5h"),
                    TripActivity("7", "清迈大学", 2, "14:00", "1h"),
                    TripActivity("8", "大象自然公园", 3, "09:30", "4h"),
                    TripActivity("9", "清迈古城 SPA", 3, "15:00", "2h"),
                    TripActivity("10", "瓦洛落市场", 3, "18:00", "1.5h")
                ),
                status = TripStatus.ACTIVE,
                date = "2026.07.15 - 2026.07.18",
                description = "3 天 10 站"
            ),
            "新加坡 4 日" to TripData(
                id = "新加坡 4 日", 
                title = "新加坡 4 日", 
                activities = mutableStateListOf(
                    TripActivity("s1", "滨海湾花园", 1, "10:00", "3h"),
                    TripActivity("s2", "鱼尾狮公园", 1, "14:00", "1h"),
                    TripActivity("s3", "金沙酒店观景台", 1, "18:00", "2h"),
                    TripActivity("s4", "圣淘沙岛", 2, "09:00", "6h")
                ),
                status = TripStatus.FINISHED,
                date = "2026.05.01",
                description = "4 天 12 站"
            ),
            "曼谷探店" to TripData(
                id = "曼谷探店", 
                title = "曼谷探店", 
                activities = mutableStateListOf(
                    TripActivity("b1", "大皇宫", 1, "09:00", "2.5h"),
                    TripActivity("b2", "郑王庙", 1, "13:00", "1.5h"),
                    TripActivity("b3", "考山路", 1, "19:00", "3h")
                ),
                status = TripStatus.FINISHED,
                date = "2026.03.12",
                description = "2 天 8 站"
            ),
            "巴厘岛海滩" to TripData(
                id = "巴厘岛海滩", 
                title = "巴厘岛海滩", 
                activities = mutableStateListOf(
                    TripActivity("p1", "乌鲁瓦图情人崖", 1, "15:00", "3h"),
                    TripActivity("p2", "金巴兰海滩海鲜", 1, "18:30", "2h")
                ),
                status = TripStatus.FINISHED,
                date = "2025.12.20",
                description = "5 天 15 站"
            )
        )
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onImportClick = { navController.navigate("import") },
                onTripClick = { navController.navigate("route_details/chiang_mai_3/true") },
                onDestinationClick = { navController.navigate("attraction") },
                onBottomTabClick = { route -> 
                    if (route != "home") navController.navigate(route) 
                }
            )
        }
        composable("import") {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onRunAI = {
                    // Logic to create a "NOT STARTED" trip
                    val newId = "imported_${java.util.UUID.randomUUID()}"
                    trips[newId] = TripData(
                        id = newId,
                        title = "新导入的行程",
                        activities = mutableStateListOf(
                            TripActivity("1", "契迪龙寺", 1, "09:00", "1.5h"),
                            TripActivity("2", "塔佩门", 1, "11:00", "1h"),
                            TripActivity("3", "尼曼路", 1, "13:00", "1.5h"),
                            TripActivity("4", "周日夜市", 1, "17:00", "2h")
                        ),
                        status = TripStatus.NOT_STARTED,
                        date = "2026.10.10",
                        description = "1 天 4 站"
                    )
                    navController.navigate("route") 
                }
            )
        }
        composable("attraction") {
            AttractionScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("route_details/chiang_mai_3/true") }
            )
        }
        composable("route") {
            ItineraryListScreen(
                trips = trips.values.toList(),
                onTripClick = { tripId -> 
                    val trip = trips[tripId]
                    val isEditable = trip?.status != TripStatus.FINISHED
                    navController.navigate("route_details/$tripId/$isEditable") 
                },
                onBottomTabClick = { route ->
                    if (route != "route") navController.navigate(route)
                }
            )
        }
        composable(
            "route_details/{tripId}/{isEditable}",
            arguments = listOf(
                androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("isEditable") { type = androidx.navigation.NavType.BoolType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "chiang_mai_3"
            val isEditable = backStackEntry.arguments?.getBoolean("isEditable") ?: true
            val trip = trips[tripId] ?: trips["chiang_mai_3"]!!
            
            RouteScreen(
                activities = trip.activities,
                tripTitle = trip.title,
                isEditable = isEditable,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("edit/$tripId") },
                onMapView = { day -> navController.navigate("map/$tripId/$day") },
                onBottomTabClick = { route ->
                    if (route == "route") {
                        navController.popBackStack("route", inclusive = false)
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
        composable(
            "map/{tripId}/{initialDay}",
            arguments = listOf(
                androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("initialDay") { type = androidx.navigation.NavType.IntType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "chiang_mai_3"
            val initialDay = backStackEntry.arguments?.getInt("initialDay") ?: 1
            val trip = trips[tripId] ?: trips["chiang_mai_3"]!!
            
            MapScreen(
                activities = trip.activities,
                tripTitle = trip.title,
                initialDay = initialDay,
                onBack = { navController.popBackStack() },
                onBottomTabClick = { route ->
                    if (route != "map") navController.navigate(route)
                }
            )
        }
        composable("map") {
            val trip = trips["chiang_mai_3"]!!
            MapScreen(
                activities = trip.activities,
                tripTitle = trip.title,
                initialDay = 1,
                onBack = { navController.popBackStack() },
                onBottomTabClick = { route ->
                    if (route != "map") navController.navigate(route)
                }
            )
        }
        composable(
            "edit/{tripId}",
            arguments = listOf(androidx.navigation.navArgument("tripId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "chiang_mai_3"
            val trip = trips[tripId] ?: trips["chiang_mai_3"]!!
            
            EditScreen(
                activities = trip.activities,
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }
        composable("profile") {
            ProfileScreen(
                onBottomTabClick = { route ->
                    if (route != "profile") navController.navigate(route)
                }
            )
        }
    }
}