package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.VpnServer
import com.example.ui.theme.CyberCyan
import com.example.crypto.CryptoUtils

@Composable
fun ServerListDialog(
    isOpen: Boolean,
    servers: List<VpnServer>,
    selectedServer: VpnServer?,
    serverPings: Map<String, Int?>,
    isPingingAll: Boolean,
    onServerSelected: (VpnServer) -> Unit,
    onTestSinglePing: (VpnServer) -> Unit,
    onTestAllPings: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 500.dp) // Responsive size limit for tablets and larger screens
                    .fillMaxHeight(0.85f)
                    .background(Color(0xFF0F1426), shape = RoundedCornerShape(28.dp))
                    .border(1.5.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                    .clickable(enabled = true, onClick = { /* consume click events to prevent parent close triggers */ })
                    .padding(20.dp)
                    .testTag("server_list_dialog_box")
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "لیست سرورهای هوشمند",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${servers.size} سرور فعال و تایید شده",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Right
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions panel: Test all pings
                Button(
                    onClick = onTestAllPings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF070B19)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("dialog_test_all_pings_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPingingAll) {
                            CircularProgressIndicator(
                                color = Color(0xFF070B19),
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text("در حال محاسبه پینگ همگانی...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تست پینگ همه",
                                modifier = Modifier.size(18.dp)
                            )
                            Text("تست پینگ همه سرورها", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Servers list - refactored to LazyColumn for ultimate performance & responsiveness
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (servers.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "هیچ سروری یافت نشد. منتظر بروزرسانی بمانید.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(servers) { server ->
                            val isSelected = selectedServer?.id == server.id
                            val protocol = getProtocolTag(server)
                            val (protocolColor, protocolBg) = getProtocolColors(protocol)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onServerSelected(server)
                                    }
                                    .testTag("dialog_server_card_${server.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF16203D) else Color(0xFF11172D)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) CyberCyan.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Test ping area (interactive, easy to tap)
                                    val pingVal = serverPings[server.id]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .width(95.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.02f))
                                            .clickable { onTestSinglePing(server) } // whole area clickable for quick ping test
                                            .padding(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onTestSinglePing(server) },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color.White.copy(alpha = 0.04f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "تست پینگ",
                                                tint = CyberCyan.copy(alpha = 0.9f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (pingVal == null) {
                                            Text(
                                                text = "کلیک",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        } else if (pingVal < 0) {
                                            Text(
                                                text = "خطا",
                                                color = Color(0xFFFF3366),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = "${pingVal}ms",
                                                color = if (pingVal < 150) CyberCyan else Color(0xFFFFCC00),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Right: Server description and details
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        ) {
                                            Text(
                                                text = server.name,
                                                color = if (isSelected) CyberCyan else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Right
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = server.remarks,
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Right
                                            )
                                        }

                                        // Protocol Indicator Badge
                                        Box(
                                            modifier = Modifier
                                                .background(protocolBg, shape = RoundedCornerShape(8.dp))
                                                .border(0.5.dp, protocolColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = protocol,
                                                color = protocolColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = if (isSelected) "انتخاب شده" else "غیر فعال",
                                            tint = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.size(18.dp)
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

private fun getProtocolTag(server: VpnServer): String {
    return try {
        val raw = CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey).trim()
        if (raw.startsWith("vless://")) "VLESS"
        else if (raw.startsWith("trojan://")) "TROJAN"
        else if (raw.startsWith("vmess://")) "VMESS"
        else if (raw.startsWith("hysteria2://") || raw.startsWith("hysteria://")) "HYSTERIA"
        else if (raw.startsWith("ss://")) "SS"
        else "VLESS"
    } catch (e: Exception) {
        "VLESS"
    }
}

private fun getProtocolColors(protocol: String): Pair<Color, Color> {
    return when (protocol.uppercase()) {
        "VLESS" -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.12f))
        "TROJAN" -> Pair(Color(0xFFFFCC00), Color(0xFFFFCC00).copy(alpha = 0.12f))
        "VMESS" -> Pair(Color(0xFF00FF66), Color(0xFF00FF66).copy(alpha = 0.12f))
        "HYSTERIA" -> Pair(Color(0xFFFF6633), Color(0xFFFF6633).copy(alpha = 0.12f))
        "SS" -> Pair(Color(0xFFCC66FF), Color(0xFFCC66FF).copy(alpha = 0.12f))
        else -> Pair(CyberCyan, CyberCyan.copy(alpha = 0.12f))
    }
}
