package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.VpnServer
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VpnConnectionState
import com.example.viewmodel.VpnViewModel

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
            var showServerListDialog by remember { mutableStateOf(false) }
            var currentTab by remember { mutableStateOf(AppTab.HOME) }

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

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    bottomBar = {
                        TabNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(innerPadding)
                    ) {
                        if (currentTab == AppTab.HOME) {
                            // Main Dashboard Client Surface
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AppHeader(
                                    isDarkTheme = isDarkTheme,
                                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Large Glowing VPN Connection Hub
                                PowerButton(
                                    connectionState = connectionState,
                                    onClick = { handleVpnToggle() }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Status textual display
                                val activeServerName = selectedServer?.name ?: "سروری انتخاب نشده است"
                                MainStatusDisplay(
                                    connectionState = connectionState,
                                    serverName = activeServerName
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Speed indicator panel
                                RealtimeSpeedDashboard(networkSpeed = networkSpeed)

                                Spacer(modifier = Modifier.height(14.dp))

                                // Encryption metadata + Latency Checker
                                SmartConfigPingWidget(
                                    connectionState = connectionState,
                                    pingMs = pingMs,
                                    isPinging = isPinging,
                                    onTestPingClick = { viewModel.triggerPing() }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Server selections and administration entry
                                ServerSelectorCard(
                                    selectedServer = selectedServer,
                                    servers = servers,
                                    serverPings = serverPings,
                                    isPingingAll = isPingingAll,
                                    onTestAllPings = { viewModel.testAllServerPings() },
                                    onSmartConnect = { viewModel.connectToBestServer() },
                                    onServerSelected = { viewModel.selectServer(it) },
                                    isSyncing = isSyncingSubscription,
                                    syncError = subscriptionSyncError,
                                    onSyncClick = { viewModel.triggerSubscriptionSync(manually = true) },
                                    onOpenServerList = { showServerListDialog = true }
                                )

                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        } else {
                            AboutUsScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            )
                        }

                        // Detailed, comprehensive server selector dialog
                        ServerListDialog(
                            isOpen = showServerListDialog,
                            servers = servers,
                            selectedServer = selectedServer,
                            serverPings = serverPings,
                            isPingingAll = isPingingAll,
                            onServerSelected = { server ->
                                viewModel.selectServer(server)
                                if (connectionState == com.example.viewmodel.VpnConnectionState.DISCONNECTED) {
                                    handleVpnToggle()
                                }
                                showServerListDialog = false
                            },
                            onTestSinglePing = { server ->
                                viewModel.testSingleServerPing(server)
                            },
                            onTestAllPings = {
                                viewModel.testAllServerPings()
                            },
                            onClose = {
                                showServerListDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}
