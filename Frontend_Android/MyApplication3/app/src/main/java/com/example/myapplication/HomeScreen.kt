package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun HomeScreen(
    onImportClick: () -> Unit = {},
    onTripClick: () -> Unit = {},
    onDestinationClick: () -> Unit = {},
    onBottomTabClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val allPlaces = listOf("新加坡滨海湾", "圣淘沙岛", "牛车水 (Chinatown)", "小印度", "乌节路", "新加坡环球影城", "清迈古城", "契迪龙寺", "素贴山", "宁曼路")
    val searchResults = allPlaces.filter { it.contains(searchQuery, ignoreCase = true) && searchQuery.isNotEmpty() }

    Scaffold(
        bottomBar = { FunctionalBottomNavBar(currentRoute = "home", onTabClick = onBottomTabClick) },
        containerColor = Paper
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item { HomeHeader() }
            item { 
                SearchBar(
                    query = searchQuery, 
                    onQueryChange = { searchQuery = it }
                ) 
            }
            
            if (searchQuery.isNotEmpty()) {
                items(searchResults) { result ->
                    SearchResultItem(text = result, onClick = {
                        searchQuery = result
                    })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = Line)
                }
                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            text = "未找到相关地点",
                            modifier = Modifier.padding(20.dp),
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                item { AIPlanningCTA(onImportClick) }
                item { SectionTitle(title = "我的行程", action = "全部") }
                item { TripCard(onTripClick) }
                item { SectionTitle(title = "热门目的地") }
                item { PopularDestinations(onDestinationClick) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun HomeHeader() {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "你好，准备去哪儿？",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            text = "新加坡 · 晴 31°",
            fontSize = 12.sp,
            color = Muted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        placeholder = {
            Text(
                text = "搜索目的地 / 景点",
                fontSize = 12.5.sp,
                color = Muted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Muted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Mint,
            unfocusedContainerColor = Mint,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = JadeDeep
        ),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Ink)
    )
}

@Composable
fun SearchResultItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, fontSize = 14.sp, color = Ink)
    }
}

@Composable
fun AIPlanningCTA(onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Jade, JadeDeep)
                )
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "AI 规划",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "粘贴游记，AI 帮你排好行程",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "＋ 导入游记",
                    color = JadeDeep,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Glow effect placeholder
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(150.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    )
                )
        )
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        if (action != null) {
            Text(
                text = action,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = JadeDeep
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCard(onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(colors = listOf(Jade, JadeDeep))),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = "清迈",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(6.dp)
                )
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "清迈 3 日",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    text = "Day 1 / 4 站 · 规划中",
                    fontSize = 11.sp,
                    color = Muted
                )
                // Progress Bar
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .height(5.dp)
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(Mint)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.6f)
                            .background(Amber)
                    )
                }
                Text(
                    text = "继续规划 →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = JadeDeep,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopularDestinations(onClick: () -> Unit = {}) {
    val destinations = listOf(
        DestinationItem("清迈", "寺庙 · 夜市", listOf(Amber, Color(0xFFE07D2C))),
        DestinationItem("曼谷", "都市 · 河景", listOf(Jade, JadeDeep)),
        DestinationItem("巴厘", "海岛", listOf(Coral, Color(0xFFC94B3A)))
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        items(destinations) { item ->
            Card(
                onClick = onClick,
                modifier = Modifier.width(108.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .height(74.dp)
                            .fillMaxWidth()
                            .background(Brush.linearGradient(item.colors)),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(7.dp)
                        )
                    }
                    Text(
                        text = item.caption,
                        fontSize = 10.sp,
                        color = Muted,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

data class DestinationItem(val name: String, val caption: String, val colors: List<Color>)

@Composable
fun FunctionalBottomNavBar(currentRoute: String, onTabClick: (String) -> Unit) {
    NavigationBar(
        containerColor = Surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .background(Surface)
            .navigationBarsPadding()
            .padding(top = 4.dp)
            .height(56.dp)
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { onTabClick("home") },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(22.dp)) },
            label = { Text("首页", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = JadeDeep,
                selectedTextColor = JadeDeep,
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "route",
            onClick = { onTabClick("route") },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = "Itinerary", modifier = Modifier.size(22.dp)) },
            label = { Text("行程", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = JadeDeep,
                selectedTextColor = JadeDeep,
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "map",
            onClick = { onTabClick("map") },
            icon = { Icon(Icons.Default.Search, contentDescription = "Map", modifier = Modifier.size(22.dp)) }, // Placeholder for Map icon
            label = { Text("地图", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = JadeDeep,
                selectedTextColor = JadeDeep,
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { onTabClick("profile") },
            icon = { Icon(Icons.Default.Person, contentDescription = "Me", modifier = Modifier.size(22.dp)) },
            label = { Text("我的", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = JadeDeep,
                selectedTextColor = JadeDeep,
                unselectedIconColor = Muted,
                unselectedTextColor = Muted,
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun BottomNavBar() {
    FunctionalBottomNavBar(currentRoute = "home", onTabClick = {})
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    MyApplicationTheme {
        HomeScreen()
    }
}