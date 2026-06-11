package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.R
import com.example.model.VpnServer
import com.example.model.VlessConfig
import com.example.service.NetworkSpeed
import com.example.ui.theme.CyberCyan
import com.example.viewmodel.VpnConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MrVpnHomeScreen(
    servers: List<VpnServer>,
    selectedServer: VpnServer?,
    connectionState: VpnConnectionState,
    pingMs: Int?,
    isPinging: Boolean,
    networkSpeed: NetworkSpeed,
    serverPings: Map<String, Int?>,
    isPingingAll: Boolean,
    isSyncingSubscription: Boolean,
    subscriptionSyncError: String?,
    publicIp: String,
    ipLocation: String,
    onServerSelected: (VpnServer) -> Unit,
    onTestSinglePing: (VpnServer) -> Unit,
    onTestAllPings: () -> Unit,
    onTriggerSync: () -> Unit,
    onVpnToggle: () -> Unit,
    onTriggerPing: () -> Unit,
    onAboutUsClick: () -> Unit,
    onRefreshIp: () -> Unit,
    onAddUserConfig: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showAddConfigDialog by remember { mutableStateOf(false) }

    // Animation for pulse effect of floating action button when connected/connecting
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val fabPulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_pulse_scale"
    )

    // Glow animation for connecting amber state
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Beautiful Premium Sleek Header
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title on Left with Logo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.mrvpn_logo),
                                contentDescription = "MrVpn Logo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, CyberCyan.copy(alpha = 0.5f), CircleShape)
                            )
                            Text(
                                text = "MrVpn",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                        
                        // Badge indicating servers loaded
                        Box(
                            modifier = Modifier
                                .background(CyberCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${servers.size} CONFIGS",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onAboutUsClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "درباره ما",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Update Subscription Trigger
                    IconButton(
                        onClick = onTriggerSync,
                        modifier = Modifier.testTag("v2_header_sync_button")
                    ) {
                        if (isSyncingSubscription) {
                            CircularProgressIndicator(
                                color = CyberCyan,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "بروزرسانی سرورها",
                                tint = if (subscriptionSyncError != null) Color(0xFFFF3366) else CyberCyan
                            )
                        }
                    }

                    // Test All Pings Play Button
                    IconButton(
                        onClick = onTestAllPings,
                        modifier = Modifier.testTag("v2_header_ping_all_button")
                    ) {
                        if (isPingingAll) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "تست پینگ همه",
                                tint = Color.White
                            )
                        }
                    }

                    // Add custom client-side config imports
                    IconButton(
                        onClick = {
                            showAddConfigDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "وارد کردن کانفیگ",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F1426), // Match design drawer base background
                    titleContentColor = Color.White
                )
            )

            // Subscription Sync Error Header Notice, if any exists
            if (subscriptionSyncError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF3366).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFF3366).copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "خطای همگام‌سازی! لطفاً اتصال اینترنت خود را چک کنید.",
                            color = Color(0xFFFF5588),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "تلاش مجدد",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable { onTriggerSync() }
                        )
                    }
                }
            }

            // Real-time Public IP & Privacy Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF13182C)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (connectionState == VpnConnectionState.CONNECTED) CyberCyan.copy(alpha = 0.4f)
                    else Color.White.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (connectionState == VpnConnectionState.CONNECTED) CyberCyan.copy(alpha = 0.15f)
                                    else Color.White.copy(alpha = 0.05f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (connectionState == VpnConnectionState.CONNECTED) Icons.Default.Security
                                             else Icons.Default.Public,
                                contentDescription = "وضعیت آی‌پی",
                                tint = if (connectionState == VpnConnectionState.CONNECTED) CyberCyan else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Column {
                            Text(
                                text = if (connectionState == VpnConnectionState.CONNECTED) "وضعیت اتصال امن" else "وضعیت حریم خصوصی",
                                color = if (connectionState == VpnConnectionState.CONNECTED) CyberCyan else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (connectionState == VpnConnectionState.CONNECTED) "ترافیک رمزگذاری شده 🔒" else "محافظت نشده (اتصال قطع) ⚠️",
                                color = if (connectionState == VpnConnectionState.CONNECTED) CyberCyan else Color(0xFFFF5555),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("public_ip_text")
                            )
                            if (ipLocation.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ipLocation,
                                    color = if (connectionState == VpnConnectionState.CONNECTED) CyberCyan.copy(alpha = 0.9f) else Color(0xFFFFCC00),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // Button to manually re-fetch IP
                    IconButton(
                        onClick = { onRefreshIp() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.04f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بروزرسانی آی‌پی",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. Main Live Servers List View
            val subscriptionServers = servers.filter { !it.isUserConfig }
            val userServers = servers.filter { it.isUserConfig }

            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(CyberCyan.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "بدون سرور",
                                tint = CyberCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "هنوز هیچ سروری اضافه نشده است\nبرای افزودن کانفیگ دکمه + بالا را لمس کنید",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp, start = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (userServers.isNotEmpty()) {
                        item {
                            Text(
                                text = "کانفیگ‌های شما 📌",
                                color = CyberCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                        itemsIndexed(userServers) { idx, server ->
                            ServerCardItem(
                                server = server,
                                index = idx + 1,
                                isSelected = selectedServer?.id == server.id,
                                serverPings = serverPings,
                                onServerSelected = onServerSelected,
                                onTestSinglePing = onTestSinglePing
                            )
                        }
                    }

                    if (subscriptionServers.isNotEmpty()) {
                        item {
                            if (userServers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                            Text(
                                text = "سرورهای ساب‌اسکریپشن 🌐",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                        itemsIndexed(subscriptionServers) { idx, server ->
                            ServerCardItem(
                                server = server,
                                index = idx + 1,
                                isSelected = selectedServer?.id == server.id,
                                serverPings = serverPings,
                                onServerSelected = onServerSelected,
                                onTestSinglePing = onTestSinglePing
                            )
                        }
                    }
                }
            }

            // 3. Persistent Standard Bottom State strip
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F1426)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Connection State line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when (connectionState) {
                                            VpnConnectionState.CONNECTED -> CyberCyan
                                            VpnConnectionState.CONNECTING -> Color(0xFFFFCC00)
                                            else -> Color.White.copy(alpha = 0.3f)
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = when (connectionState) {
                                    VpnConnectionState.CONNECTED -> "متصل شد "
                                    VpnConnectionState.CONNECTING -> "در حال برقراری اتصال..."
                                    VpnConnectionState.DISCONNECTING -> "در حال قطع اتصال..."
                                    VpnConnectionState.DISCONNECTED -> "قطع شده"
                                },
                                color = when (connectionState) {
                                    VpnConnectionState.CONNECTED -> CyberCyan
                                    VpnConnectionState.CONNECTING -> Color(0xFFFFCC00)
                                    else -> Color.White
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Configuration name attached
                        Text(
                            text = selectedServer?.name ?: "سرور انتخاب نشده است",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Speed Metrics Block (Upload/Download stats)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "دانلود",
                                    tint = CyberCyan.copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = formatSpeedValue(networkSpeed.downSpeedMbs),
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "آپلود",
                                    tint = Color(0xFFFF3366).copy(alpha = 0.8f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = formatSpeedValue(networkSpeed.upSpeedMbs),
                                    color = Color(0xFFFF5588),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Click to test actual full proxy connection ping
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { onTriggerPing() }
                    ) {
                        Text(
                            text = "تست اتصال",
                            color = CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPinging) "⏳" else if (pingMs != null) "${pingMs}ms" else "لمس کنید",
                            color = if (pingMs != null && pingMs >= 0) CyberCyan else Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 4. floating action button (FAB) for Connection Toggle
        // Absolute authentic V logo elevated bottom-right toggle
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 98.dp, end = 24.dp) // placed overlay over list index safely
        ) {
            val hasActiveGlow = connectionState == VpnConnectionState.CONNECTED || connectionState == VpnConnectionState.CONNECTING
            val scaleModifier = if (connectionState == VpnConnectionState.CONNECTING) {
                Modifier.scale(fabPulseScale)
            } else Modifier

            FloatingActionButton(
                onClick = onVpnToggle,
                shape = CircleShape,
                containerColor = when (connectionState) {
                    VpnConnectionState.CONNECTED -> CyberCyan
                    VpnConnectionState.CONNECTING -> Color(0xFFFFB300)
                    else -> Color(0xFF1E293B) // Slate dark slate
                },
                contentColor = when (connectionState) {
                    VpnConnectionState.CONNECTED -> Color(0xFF0F1426)
                    else -> Color.White
                },
                modifier = scaleModifier
                    .size(60.dp)
                    .testTag("v2_float_power_button")
                    .then(
                        if (hasActiveGlow) {
                            Modifier.shadow(
                                12.dp,
                                CircleShape,
                                spotColor = if (connectionState == VpnConnectionState.CONNECTED) CyberCyan else Color(0xFFFFB300)
                            )
                        } else Modifier
                    )
            ) {
                if (connectionState == VpnConnectionState.CONNECTING) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (connectionState == VpnConnectionState.CONNECTED) Icons.Default.Check else Icons.Default.PowerSettingsNew,
                        contentDescription = "کنترل اتصال به شبکه",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Add Configuration Dialog
        AddConfigDialog(
            isOpen = showAddConfigDialog,
            onAddConfig = onAddUserConfig,
            onClose = { showAddConfigDialog = false }
        )
    }
}

@Composable
fun AddConfigDialog(
    isOpen: Boolean,
    onAddConfig: (name: String, url: String) -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var configName by remember { mutableStateOf("") }
    var configUrl by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 420.dp)
                    .background(Color(0xFF0F1426), shape = RoundedCornerShape(24.dp))
                    .border(1.5.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .clickable(enabled = true, onClick = { /* consume click */ })
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "افزودن کانفیگ جدید ➕",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "لینک پروتکل (VLESS, VMess, Trojan, ShadowSocks) خود را وارد کنید",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = configName,
                    onValueChange = { configName = it },
                    label = { Text("نام دلخواه کانفیگ (اختیاری)", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = configUrl,
                    onValueChange = { configUrl = it },
                    label = { Text("لینک کانفیگ (vless://...)", color = Color.White.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = CyberCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = {
                            val clipboardText = clipboardManager.getText()?.text ?: ""
                            if (clipboardText.trim().isNotEmpty()) {
                                configUrl = clipboardText.trim()
                                Toast.makeText(context, "با موفقیت از حافظه جایگذاری شد ✔", Toast.LENGTH_SHORT).show()
                                
                                val hashIdx = clipboardText.indexOf('#')
                                if (hashIdx != -1 && configName.isEmpty()) {
                                    try {
                                        val label = clipboardText.substring(hashIdx + 1).trim()
                                        configName = java.net.URLDecoder.decode(label, "UTF-8")
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            } else {
                                Toast.makeText(context, "حافظه موقت خالی است!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "جایگذاری",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "جایگذاری از کلیپ‌برد",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.06f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("انصراف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val urlTrimmed = configUrl.trim()
                            if (urlTrimmed.isEmpty()) {
                                Toast.makeText(context, "لطفا لینک کانفیگ را وارد کنید!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!urlTrimmed.startsWith("vless://") &&
                                !urlTrimmed.startsWith("vmess://") &&
                                !urlTrimmed.startsWith("trojan://") &&
                                !urlTrimmed.startsWith("ss://")) {
                                Toast.makeText(context, "لینک نامعتبر است! باید با پروتکل معتبر مانند vless شروع شود.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            onAddConfig(configName, urlTrimmed)
                            onClose()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF0F1426)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("ثبت کانفیگ", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerCardItem(
    server: VpnServer,
    index: Int,
    isSelected: Boolean,
    serverPings: Map<String, Int?>,
    onServerSelected: (VpnServer) -> Unit,
    onTestSinglePing: (VpnServer) -> Unit
) {
    val protocol = VlessConfig.parse(server.configUrl)?.protocol ?: "VLESS"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 6.dp else 1.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onServerSelected(server) }
            .testTag("v2_config_card_${server.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF131B36) else Color(0xFF0F1426)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.2.dp,
            color = if (isSelected) CyberCyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.04f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(
                        if (isSelected) CyberCyan else Color.Transparent
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp)
            ) {
                Text(
                    text = "$index",
                    color = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = getProtocolColors(protocol).second,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = protocol,
                        color = getProtocolColors(protocol).first,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = server.name,
                    color = if (isSelected) CyberCyan else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = server.remarks,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pingVal = serverPings[server.id]
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(85.dp)
                    .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                    .clickable { onTestSinglePing(server) }
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (pingVal == null) {
                        Text(
                            text = "کلیک",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Test Ping",
                            tint = CyberCyan.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (pingVal < 0) {
                        Text(
                            text = "خطا 🔴",
                            color = Color(0xFFFF3366),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text(
                            text = "${pingVal}ms",
                            color = if (pingVal < 150) CyberCyan else Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (pingVal < 150) CyberCyan else Color(0xFFFFCC00),
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

// Support helper to extract beautiful protocol indicator styling
private fun getProtocolColors(protocol: String): Pair<Color, Color> {
    return when (protocol.uppercase()) {
        "VLESS" -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.15f))
        "VMESS" -> Pair(Color(0xFF818CF8), Color(0xFF818CF8).copy(alpha = 0.15f))
        "TROJAN" -> Pair(Color(0xFFF472B6), Color(0xFFF472B6).copy(alpha = 0.15f))
        "SS", "SHADOWSOCKS" -> Pair(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.15f))
        else -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.15f))
    }
}

private fun formatSpeedValue(speedMbs: Float): String {
    return if (speedMbs >= 1.0f) {
        String.format("%.1f MB/s", speedMbs)
    } else {
        String.format("%.0f KB/s", speedMbs * 1024f)
    }
}
