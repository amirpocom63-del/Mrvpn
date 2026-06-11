package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VpnServer
import com.example.model.VlessConfig
import com.example.ui.theme.CyberCyan
import com.example.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagerScreen(
    servers: List<VpnServer>,
    viewModel: VpnViewModel,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    // Add / Edit Server Dialog states
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedServerToEdit by remember { mutableStateOf<VpnServer?>(null) }
    
    val filteredServers = remember(servers, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            servers
        } else {
            servers.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.remarks.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Custom Header matching MrVpn Theme
                TopAppBar(
                    title = {
                        Text(
                            text = "مدیریت سرورها",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White
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

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "جستجوی سرور...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجو",
                            tint = CyberCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "پاک کردن",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E2235),
                        unfocusedContainerColor = Color(0xFF1E2235)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (filteredServers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "بدون سرور",
                                tint = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "سروری با این مشخصات پیدا نشد" else "سروری ثبت نشده است. روی دکمه + کلیک کنید.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredServers, key = { it.id }) { server ->
                            ServerManageCard(
                                server = server,
                                onEditClick = {
                                    selectedServerToEdit = server
                                    showAddEditDialog = true
                                },
                                onDeleteClick = {
                                    viewModel.deleteServer(server)
                                    Toast.makeText(context, "سرور حذف شد", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Floating Add Server Button
            FloatingActionButton(
                onClick = {
                    selectedServerToEdit = null
                    showAddEditDialog = true
                },
                containerColor = CyberCyan,
                contentColor = Color(0xFF0F1115),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_config_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "افزودن سرور",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (showAddEditDialog) {
            AddEditServerDialog(
                server = selectedServerToEdit,
                onDismiss = { showAddEditDialog = false },
                onSave = { name, configUrl, remarks ->
                    if (selectedServerToEdit == null) {
                        viewModel.addServer(name, configUrl, remarks)
                        Toast.makeText(context, "سرور با موفقیت افزوده شد", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateServer(selectedServerToEdit!!, name, configUrl, remarks)
                        Toast.makeText(context, "سرور آپدیت شد", Toast.LENGTH_SHORT).show()
                    }
                    showAddEditDialog = false
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun ServerManageCard(
    server: VpnServer,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val config = remember(server) { VlessConfig.parse(VlessConfig.parse(server.configUrl)?.let { server.configUrl } ?: "Manual") }
    val protocol = remember(server) { 
        val parsed = VlessConfig.parse(server.configUrl)
        parsed?.protocol ?: "VLESS"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x0CFFFFFF)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left icon and server labels
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Protocol Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(CyberCyan.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = protocol,
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = server.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = server.remarks.ifEmpty { "سرور ویرایش دستی" },
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Edit Button
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "ویرایش",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditServerDialog(
    server: VpnServer?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    viewModel: VpnViewModel
) {
    var rawUrl by remember {
        mutableStateOf(
            if (server != null) {
                viewModel.getDecryptedConfig(server)
            } else ""
        )
    }
    var serverName by remember { mutableStateOf(server?.name ?: "") }
    var description by remember { mutableStateOf(server?.remarks ?: "") }
    
    // Auto populate fields if config URL changes
    LaunchedEffect(rawUrl) {
        if (serverName.isEmpty() || server == null) {
            val parsedResult = VlessConfig.parse(rawUrl)
            if (parsedResult != null) {
                if (serverName.isEmpty()) {
                    serverName = parsedResult.remarks.ifEmpty { parsedResult.address }
                }
                if (description.isEmpty()) {
                    description = parsedResult.address
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = if (server == null) "افزودن کانفیگ دستي" else "ویرایش کانفیگ",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Config URL Textbox
                    OutlinedTextField(
                        value = rawUrl,
                        onValueChange = { rawUrl = it },
                        label = { Text("آدرس پیکربندی (vless / trojan)") },
                        placeholder = { Text("vless://...") },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = CyberCyan,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Name
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("نام سرور") },
                        placeholder = { Text("مثلا: سرور پرسرعت آلمان") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = CyberCyan,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Additional Remarks
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیحات / آدرس هاست") },
                        placeholder = { Text("آدرس سرور یا ریمارک") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = CyberCyan,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawUrl.trim().isEmpty()) {
                            return@Button
                        }
                        
                        val finalName = serverName.trim().ifEmpty { 
                            VlessConfig.parse(rawUrl)?.remarks?.ifEmpty { "Manual Config" } ?: "Manual Config"
                        }
                        
                        onSave(
                            finalName,
                            rawUrl.trim(),
                            description.trim()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF0F1115)
                    )
                ) {
                    Text("ذخیره", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("انصراف", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF161A28),
            shape = RoundedCornerShape(24.dp)
        )
    }
}
