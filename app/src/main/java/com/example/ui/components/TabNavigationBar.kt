package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan

@Composable
fun TabNavigationBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        containerColor = Color(0xFF0F1426), // match deep space theme background
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "خانه"
                )
            },
            label = {
                Text(
                    text = "خانه",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0F1426),
                selectedTextColor = CyberCyan,
                indicatorColor = CyberCyan,
                unselectedIconColor = Color.White.copy(alpha = 0.4f),
                unselectedTextColor = Color.White.copy(alpha = 0.4f)
            )
        )

        NavigationBarItem(
            selected = currentTab == AppTab.ABOUT_US,
            onClick = { onTabSelected(AppTab.ABOUT_US) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "درباره ما"
                )
            },
            label = {
                Text(
                    text = "درباره ما",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0F1426),
                selectedTextColor = CyberCyan,
                indicatorColor = CyberCyan,
                unselectedIconColor = Color.White.copy(alpha = 0.4f),
                unselectedTextColor = Color.White.copy(alpha = 0.4f)
            )
        )
    }
}

enum class AppTab {
    HOME,
    ABOUT_US
}
