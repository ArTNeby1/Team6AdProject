package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
fun AttractionScreen(onBack: () -> Unit = {}, onAdd: () -> Unit = {}) {
    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, LineSoft, RoundedCornerShape(0.dp)),
                color = Surface
            ) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Jade),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(
                        text = "＋ 加入行程",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = Surface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                // Hero Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Jade, JadeDeep)
                            )
                        )
                        .padding(14.dp, 16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = "契迪龙寺",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 23.sp
                        )
                        Text(
                            text = "Wat Chedi Luang",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            item {
                // Meta Chips
                Row(
                    modifier = Modifier
                        .padding(12.dp, 16.dp, 12.dp, 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetaChip(text = "⭐ 4.7", color = Color(0xFFFCEFD6), textColor = Color(0xFF9A6410))
                    MetaChip(text = "寺庙")
                    MetaChip(text = "免费")
                    MetaChip(text = "建议 1.5h")
                }
            }

            item {
                // Description
                Text(
                    text = "清迈古城中心的古老寺庙，14 世纪兰纳王朝遗址，标志性的巨型佛塔与傍晚的诵经氛围值得一看。开放 08:00–17:00。",
                    fontSize = 11.5.sp,
                    color = Ink.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            item {
                Text(
                    text = "用户评论 · 128",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp)
                )
            }

            // Comments
            item { CommentItem("林小舟", "林", Amber, "★★★★★", "傍晚去人少，光线特别好，拍照绝了。") }
            item { CommentItem("Aiko", "A", Jade, "★★★★☆", "进殿需脱鞋，记得穿长裤～") }

            item {
                // Write Comment Button
                Box(
                    modifier = Modifier
                        .padding(16.dp, 2.dp, 16.dp, 10.dp)
                        .fillMaxWidth()
                        .border(1.dp, Line, RoundedCornerShape(11.dp))
                        .padding(9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋ 写评论",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = JadeDeep
                    )
                }
            }
        }
    }
}

@Composable
fun MetaChip(text: String, color: Color = Mint, textColor: Color = Ink) {
    Surface(
        color = color,
        shape = RoundedCornerShape(99.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun CommentItem(name: String, initial: String, bgColor: Color, stars: String, text: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(9.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stars,
                    fontSize = 9.sp,
                    color = Amber,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = text,
                fontSize = 11.sp,
                color = Ink.copy(alpha = 0.7f),
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AttractionPreview() {
    MyApplicationTheme {
        AttractionScreen()
    }
}