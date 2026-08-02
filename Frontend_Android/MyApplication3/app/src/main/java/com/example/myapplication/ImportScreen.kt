package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit = {}, onRunAI: () -> Unit = {}) {
    var noteText by remember { mutableStateOf("清迈超全攻略｜3天2夜这样玩🌴\nDay1 契迪龙寺 → 塔佩门 → 尼曼路 → 周日夜市，寺庙控必去…") }
    var extractions by remember { mutableStateOf<List<ExtractionItem>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Mint)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = JadeDeep,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "导入游记",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
        },
        containerColor = Surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Paste Area - Now an editable TextField
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Mint)
                    .border(1.dp, Jade, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column {
                    TextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = JadeDeep
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 11.5.sp,
                            color = Ink,
                            lineHeight = 18.sp
                        ),
                        placeholder = {
                            Text("粘贴您的游记内容...", fontSize = 11.5.sp, color = Muted)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Run AI Button
            Button(
                onClick = {
                    isExtracting = true
                    // Mock AI logic: extract locations after a short delay
                    extractions = listOf(
                        ExtractionItem("契迪龙寺", true),
                        ExtractionItem("塔佩门", true),
                        ExtractionItem("尼曼路", true),
                        ExtractionItem("周日夜市", false, "需确认时间")
                    )
                    isExtracting = false
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Jade),
                shape = RoundedCornerShape(13.dp),
                enabled = !isExtracting && noteText.isNotEmpty()
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "✨ AI 提取景点",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (extractions.isNotEmpty()) {
                // AI Agent status line
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Amber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🤖", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Agent 已识别 ${extractions.size} 个地点，正在定位校验…",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(extractions) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, LineSoft, RoundedCornerShape(12.dp))
                                .background(Surface)
                                .padding(9.dp, 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Mint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.isOk) Icons.Default.LocationOn else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (item.isOk) JadeDeep else Coral,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = item.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.isOk) {
                                Surface(
                                    color = Mint,
                                    shape = RoundedCornerShape(99.dp)
                                ) {
                                    Text(
                                        text = "✓ 已定位",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = JadeDeep,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFFCEFD6),
                                    shape = RoundedCornerShape(99.dp)
                                ) {
                                    Text(
                                        text = item.status ?: "",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9A6410),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRunAI, // Navigate to itinerary details
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = JadeDeep),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("确认并生成行程", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

data class ExtractionItem(val name: String, val isOk: Boolean, val status: String? = null)

@Preview(showBackground = true)
@Composable
fun ImportPreview() {
    MyApplicationTheme {
        ImportScreen()
    }
}