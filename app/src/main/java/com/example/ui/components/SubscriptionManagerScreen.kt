package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SubscriptionItem
import com.example.ui.theme.CyberCyan
import com.example.viewmodel.VpnViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionManagerScreen(
    viewModel: VpnViewModel,
    isSyncing: Boolean,
    syncError: String?,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var subToEdit by remember { mutableStateOf<SubscriptionItem?>(null) }
    var subToDelete by remember { mutableStateOf<SubscriptionItem?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "تنظیمات ساب‌اسکریپشن",
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF0F1115),
                    shape = CircleShape,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "افزودن ساب‌اسکریپشن",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sync Status Widget
                    AnimatedVisibility(
                        visible = isSyncing || syncError != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        isSyncing -> CyberCyan.copy(alpha = 0.2f)
                                        else -> Color(0xFFFF5252).copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x1A0F1115)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        color = CyberCyan,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "شکست",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSyncing) "در حال همگام‌سازی و بارگذاری سرورها..." else "بروز خطا در اتصال ساب‌اسکریپشن",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (!isSyncing && syncError != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = syncError,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (subscriptions.isEmpty()) {
                        // Empty State Visual Panel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(CyberCyan.copy(alpha = 0.05f), CircleShape)
                                        .border(1.dp, CyberCyan.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "لیست خالی",
                                        tint = CyberCyan.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "لیست ساب‌اسکریپشن‌ها خالی است",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "برای دریافت خودکار کانفیگ‌ها، دکمه + پایین صفحه را بزنید و یک ساب‌اسکریپشن اضافه کنید.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Polished Subscibers list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(subscriptions) { sub ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF131722)
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(CyberCyan.copy(alpha = 0.08f), CircleShape)
                                                        .border(1.dp, CyberCyan.copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Link,
                                                        contentDescription = "لینک",
                                                        tint = CyberCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = sub.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = sub.url,
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            color = Color.White.copy(alpha = 0.04f),
                                            thickness = 1.dp
                                        )

                                        // Action buttons row inside item
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Sync button
                                            IconButton(
                                                onClick = {
                                                    viewModel.syncSubscription(sub)
                                                    Toast.makeText(context, "در حال بروزرسانی سرورها از ساب‌اسکریپشن ${sub.name}...", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "بروزرسانی",
                                                    tint = CyberCyan,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Edit button
                                            IconButton(
                                                onClick = { subToEdit = sub },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "ویرایش",
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Delete button
                                            IconButton(
                                                onClick = { subToDelete = sub },
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(20.dp)
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

        // Add Dialog
        if (showAddDialog) {
            var inputName by remember { mutableStateOf("") }
            var inputUrl by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Text(
                        text = "افزودن ساب‌اسکریپشن جدید",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("عنوان (نام ساب)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            label = { Text("لینک (URL)") },
                            placeholder = { Text("https://example.com/sub/...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputName.trim().isEmpty() || inputUrl.trim().isEmpty()) {
                                Toast.makeText(context, "لطفاً تمام فیلدها را پر کنید.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addSubscription(inputName.trim(), inputUrl.trim())
                                showAddDialog = false
                                Toast.makeText(context, "ساب‌اسکریپشن با موفقیت افزوده شد.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF0F1115)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("افزودن", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("انصراف", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF131722),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Edit Dialog
        subToEdit?.let { sub ->
            var inputName by remember { mutableStateOf(sub.name) }
            var inputUrl by remember { mutableStateOf(sub.url) }

            AlertDialog(
                onDismissRequest = { subToEdit = null },
                title = {
                    Text(
                        text = "ویرایش ساب‌اسکریپشن",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("عنوان (نام ساب)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            label = { Text("لینک (URL)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputName.trim().isEmpty() || inputUrl.trim().isEmpty()) {
                                Toast.makeText(context, "لطفاً تمام فیلدها را پر کنید.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.updateSubscription(sub, inputName.trim(), inputUrl.trim())
                                subToEdit = null
                                Toast.makeText(context, "تغییرات با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF0F1115)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ذخیره", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { subToEdit = null }) {
                        Text("انصراف", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF131722),
                shape = RoundedCornerShape(24.dp)
            )
        }

        // Delete Dialog
        subToDelete?.let { sub ->
            AlertDialog(
                onDismissRequest = { subToDelete = null },
                title = {
                    Text(
                        text = "تایید حذف ساب‌اسکریپشن",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                },
                text = {
                    Text(
                        text = "آیا مطمئن هستید که می‌خواهید ساب‌اسکریپشن «${sub.name}» را حذف کنید؟ این عمل غیر قابل بازگشت است.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSubscription(sub)
                            subToDelete = null
                            Toast.makeText(context, "ساب‌اسکریپشن حذف شد.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حذف", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { subToDelete = null }) {
                        Text("انصراف", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF131722),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
