package com.example.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CyberCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUsScreen(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Hamburger Header
            TopAppBar(
                title = {
                    Text(
                        text = "درباره ما",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "منو",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Identity Brand Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyberCyan.copy(alpha = 0.3f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .border(2.dp, CyberCyan, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mrvpn_logo),
                        contentDescription = "مستر وی‌پی‌ان لوگو",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Main description card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 500.dp)
                        .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1426)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.End // Right aligned for Persian text
                    ) {
                        Text(
                            text = "با سلام خدمت همه کاربران عزیز",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "این برنامه توسط امیر ساخته شده و هرگونه کپی برداری پیگرد قانونی دارد.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "کانال تلگرامی ما :",
                            color = CyberCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Telegram Clickable Button Link
                        Button(
                            onClick = {
                                try {
                                    uriHandler.openUri("https://t.me/MrVpn294")
                                } catch (e: Exception) {
                                    // Safe fallback
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF229ED9), // Telegram official Hex blue
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@MrVpn294",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "عضویت در تلگرام",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))

                // Small branding footer
                Text(
                    text = "توسعه یافته با عشق توسط امیر",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
