package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@Composable
fun ProfileScreen(onBottomTabClick: (String) -> Unit = {}) {
    Scaffold(
        bottomBar = { FunctionalBottomNavBar(currentRoute = "profile", onTabClick = onBottomTabClick) },
        containerColor = Paper
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header / User Info
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Jade),
                    contentAlignment = Alignment.Center
                ) {
                    Text("L", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = "林小舟", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(text = "已加入沿途 128 天", fontSize = 12.sp, color = Muted)
                }
            }

            // Stats
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                ProfileStat("行程", "12")
                ProfileStat("收藏", "45")
                ProfileStat("足迹", "8")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Menu Items
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
            ) {
                ProfileMenuItem(Icons.Default.Favorite, "我的收藏")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = LineSoft)
                ProfileMenuItem(Icons.Default.Star, "评价过的景点")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = LineSoft)
                ProfileMenuItem(Icons.Default.Settings, "偏好设置")
            }
        }
    }
}

@Composable
fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(text = label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = JadeDeep, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Line, modifier = Modifier.size(20.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun ProfilePreview() {
    MyApplicationTheme {
        ProfileScreen()
    }
}