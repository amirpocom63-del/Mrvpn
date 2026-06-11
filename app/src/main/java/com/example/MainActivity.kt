package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.VpnServer
import com.example.ui.components.*
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VpnConnectionState
import com.example.viewmodel.VpnViewModel
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    MANAGE_SERVERS,
    MANAGE_SUBSCRIBES,
    ABOUT_US
}

class MainActivity : ComponentActivity() {
    
    private val viewModel: VpnViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startVpnService()
        }
    }

    private fun handleVpnToggle() {
        if (viewModel.connectionState.value == VpnConnectionState.DISCONNECTED) {
            val vpnIntent = android.net.VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                viewModel.startVpnService()
            }
        } else {
            viewModel.stopVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fully edge-to-edge full bleed support
        enableEdgeToEdge()
        
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val servers by viewModel.servers.collectAsStateWithLifecycle()
                val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
                val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                val pingMs by viewModel.pingMs.collectAsStateWithLifecycle()
                val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
                val networkSpeed by viewModel.networkSpeed.collectAsStateWithLifecycle()
                val serverPings by viewModel.serverPings.collectAsStateWithLifecycle()
                val isPingingAll by viewModel.isPingingAll.collectAsStateWithLifecycle()
                val isSyncingSubscription by viewModel.isSyncingSubscription.collectAsStateWithLifecycle()
                val subscriptionSyncError by viewModel.subscriptionSyncError.collectAsStateWithLifecycle()
                val publicIp by viewModel.publicIp.collectAsStateWithLifecycle()
                val ipLocation by viewModel.ipLocation.collectAsStateWithLifecycle()

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF0F1115),
                                drawerContentColor = Color.White,
                                modifier = Modifier.width(300.dp)
                            ) {
                                // Cover Drawer Header
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF1E2235), Color(0xFF0F1115))
                                            )
                                        )
                                        .padding(horizontal = 24.dp, vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(CyberCyan.copy(alpha = 0.08f), CircleShape)
                                            .border(1.dp, CyberCyan.copy(alpha = 0.3f), CircleShape)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.mrvpn_logo),
                                            contentDescription = "MrVpn Logo",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "مستر وی‌پی‌ان",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = "mrvpn",
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                Spacer(modifier = Modifier.height(12.dp))

                                // Drawer Navigation Items
                                val menuItems = listOf(
                                    Triple(AppScreen.HOME, "صفحه اصلی و اتصال", Icons.Default.Home),
                                    Triple(AppScreen.MANAGE_SERVERS, "مدیریت سرورها", Icons.Default.List),
                                    Triple(AppScreen.MANAGE_SUBSCRIBES, "تنظیمات ساب‌اسکریپشن", Icons.Default.Refresh),
                                    Triple(AppScreen.ABOUT_US, "درباره ما", Icons.Default.Info)
                                )

                                menuItems.forEach { (screen, label, icon) ->
                                    val isSelected = currentScreen == screen
                                    NavigationDrawerItem(
                                        icon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = if (isSelected) Color(0xFF0F1115) else CyberCyan
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        selected = isSelected,
                                        onClick = {
                                            currentScreen = screen
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = CyberCyan,
                                            selectedTextColor = Color(0xFF0F1115),
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = "نسخه ۲.۲",
                                    color = Color.White.copy(alpha = 0.25f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 24.dp)
                                )
                            }
                        }
                    ) {
                        VpnAppBackground(isDark = isDarkTheme) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (currentScreen) {
                                        AppScreen.HOME -> {
                                            MrVpnHomeScreen(
                                                servers = servers,
                                                selectedServer = selectedServer,
                                                connectionState = connectionState,
                                                pingMs = pingMs,
                                                isPinging = isPinging,
                                                networkSpeed = networkSpeed,
                                                serverPings = serverPings,
                                                isPingingAll = isPingingAll,
                                                isSyncingSubscription = isSyncingSubscription,
                                                subscriptionSyncError = subscriptionSyncError,
                                                publicIp = publicIp,
                                                ipLocation = ipLocation,
                                                onRefreshIp = {
                                                    viewModel.fetchPublicIp()
                                                },
                                                onServerSelected = { server ->
                                                    viewModel.selectServer(server)
                                                },
                                                onTestSinglePing = { server ->
                                                    viewModel.testSingleServerPing(server)
                                                },
                                                onTestAllPings = {
                                                    viewModel.testAllServerPings()
                                                },
                                                onTriggerSync = {
                                                    viewModel.triggerSubscriptionSync(manually = true)
                                                },
                                                onVpnToggle = {
                                                    handleVpnToggle()
                                                },
                                                onTriggerPing = {
                                                    viewModel.triggerPing()
                                                },
                                                onAboutUsClick = {
                                                    scope.launch { drawerState.open() }
                                                },
                                                onAddUserConfig = { name, url ->
                                                    viewModel.addUserServer(name, url, "")
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        AppScreen.MANAGE_SERVERS -> {
                                            ServerManagerScreen(
                                                servers = servers,
                                                viewModel = viewModel,
                                                onMenuClick = {
                                                    scope.launch { drawerState.open() }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        AppScreen.MANAGE_SUBSCRIBES -> {
                                            SubscriptionManagerScreen(
                                                viewModel = viewModel,
                                                isSyncing = isSyncingSubscription,
                                                syncError = subscriptionSyncError,
                                                onMenuClick = {
                                                    scope.launch { drawerState.open() }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        AppScreen.ABOUT_US -> {
                                            AboutUsScreen(
                                                onMenuClick = {
                                                    scope.launch { drawerState.open() }
                                                },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .statusBarsPadding()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
