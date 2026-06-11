package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoUtils
import com.example.db.AppDatabase
import com.example.model.VpnServer
import com.example.model.VlessConfig
import com.example.model.SubscriptionItem
import com.example.service.ConnectionSimulator
import com.example.service.NetworkSpeed
import com.example.service.MyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val vpnDao = database.vpnServerDao()
    private val subscriptionDao = database.subscriptionDao()

    // Server State
    val servers: StateFlow<List<VpnServer>> = vpnDao.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Subscription List state
    val subscriptions: StateFlow<List<SubscriptionItem>> = subscriptionDao.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    // Connection States
    val connectionState: StateFlow<VpnConnectionState> = MyVpnService.vpnState
        .map { serviceState ->
            when (serviceState) {
                MyVpnService.ConnectionState.CONNECTED -> VpnConnectionState.CONNECTED
                MyVpnService.ConnectionState.CONNECTING -> VpnConnectionState.CONNECTING
                MyVpnService.ConnectionState.DISCONNECTED -> VpnConnectionState.DISCONNECTED
                MyVpnService.ConnectionState.DISCONNECTING -> VpnConnectionState.DISCONNECTING
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnConnectionState.DISCONNECTED)

    private val _pingMs = MutableStateFlow<Int?>(null)
    val pingMs: StateFlow<Int?> = _pingMs.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _serverPings = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val serverPings: StateFlow<Map<String, Int?>> = _serverPings.asStateFlow()

    private val _isPingingAll = MutableStateFlow(false)
    val isPingingAll: StateFlow<Boolean> = _isPingingAll.asStateFlow()

    // Subscription details
    private val _subscriptionUrl = MutableStateFlow("")
    val subscriptionUrl: StateFlow<String> = _subscriptionUrl.asStateFlow()

    private val _isSyncingSubscription = MutableStateFlow(false)
    val isSyncingSubscription: StateFlow<Boolean> = _isSyncingSubscription.asStateFlow()

    private val _subscriptionSyncError = MutableStateFlow<String?>(null)
    val subscriptionSyncError: StateFlow<String?> = _subscriptionSyncError.asStateFlow()

    private val PREF_SUB_URL = "subscription_url"
    private val sharedPrefs = application.getSharedPreferences("vpn_prefs", android.content.Context.MODE_PRIVATE)

    // Base64 of: https://raw.githubusercontent.com/amirpocom63-del/Amir-web-code/refs/heads/main/Sub.txt
    private val ENCRYPTED_HARDCODED_SUB_URL = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2FtaXJwb2NvbTYzLWRlbC9BbWlyLXdlYi1jb2RlL3JlZnMvaGVhZHMvbWFpbi9TdWIudHh0"

    private fun getHardcodedSubscriptionUrl(): String {
        return try {
            val decodedBytes = android.util.Base64.decode(ENCRYPTED_HARDCODED_SUB_URL, android.util.Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            "https://raw.githubusercontent.com/amirpocom63-del/Amir-web-code/refs/heads/main/Sub.txt"
        }
    }

    // Real-time speed
    private val _networkSpeed = MutableStateFlow(NetworkSpeed(0f, 0f))
    val networkSpeed: StateFlow<NetworkSpeed> = _networkSpeed.asStateFlow()

    private val _publicIp = MutableStateFlow<String>("در حال دریافت...")
    val publicIp: StateFlow<String> = _publicIp.asStateFlow()

    private val _ipLocation = MutableStateFlow<String>("")
    val ipLocation: StateFlow<String> = _ipLocation.asStateFlow()

    private var speedJob: Job? = null
    private var pingJob: Job? = null

    init {
        // Load stored subscription URL (Fallback or hardcoded)
        val loadedUrl = sharedPrefs.getString(PREF_SUB_URL, "") ?: ""
        _subscriptionUrl.value = if (loadedUrl.isNotEmpty() && !loadedUrl.contains("mrvpn-sub")) loadedUrl else getHardcodedSubscriptionUrl()
        
        // Seed default servers if empty, then auto-fetch subscription upon startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = vpnDao.getAllServersSync().size
                if (count == 0) {
                    seedFallbackServers()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            withContext(Dispatchers.Main) {
                triggerSubscriptionSync(manually = false)
            }
        }

        // Hydrate initial defaults with robust persistent server selection recovery
        viewModelScope.launch {
            servers.collectLatest { list ->
                if (list.isNotEmpty()) {
                    val savedId = sharedPrefs.getString("selected_server_id", "") ?: ""
                    val savedName = sharedPrefs.getString("selected_server_name", "") ?: ""
                    val currentSelected = _selectedServer.value
                    
                    if (currentSelected == null) {
                        // Restore by ID or Name, otherwise fall back to default or first
                        val matched = list.find { it.id == savedId }
                            ?: list.find { it.name == savedName }
                            ?: list.find { it.isDefault }
                            ?: list.first()
                        _selectedServer.value = matched
                        sharedPrefs.edit()
                            .putString("selected_server_id", matched.id)
                            .putString("selected_server_name", matched.name)
                            .apply()
                    } else {
                        // Make sure currently selected server still exists, otherwise recover gracefully
                        val stillExists = list.any { it.id == currentSelected.id }
                        if (!stillExists) {
                            val matchedByName = list.find { it.name == currentSelected.name }
                            val newSelect = matchedByName ?: list.find { it.isDefault } ?: list.first()
                            _selectedServer.value = newSelect
                            sharedPrefs.edit()
                                .putString("selected_server_id", newSelect.id)
                                .putString("selected_server_name", newSelect.name)
                                .apply()
                        }
                    }
                }
            }
        }

        // Keep connection speed updated dynamically based on real-time state
        viewModelScope.launch {
            connectionState.collectLatest { state ->
                speedJob?.cancel()
                if (state == VpnConnectionState.CONNECTED) {
                    speedJob = viewModelScope.launch {
                        ConnectionSimulator.streamLiveSpeed(true).collect { speed ->
                            _networkSpeed.value = speed
                        }
                    }
                    // Trigger initial ping upon successful connection
                    triggerPing()
                    fetchPublicIp()
                } else {
                    _networkSpeed.value = NetworkSpeed(0f, 0f)
                    _pingMs.value = null
                    if (state == VpnConnectionState.DISCONNECTED) {
                        fetchPublicIp()
                    }
                }
            }
        }
        
        // Initial IP fetch
        fetchPublicIp()
    }

    fun selectServer(server: VpnServer) {
        viewModelScope.launch {
            _selectedServer.value = server
            // Persist the selection so it never resets or switches on exit/launch
            sharedPrefs.edit()
                .putString("selected_server_id", server.id)
                .putString("selected_server_name", server.name)
                .apply()
            
            if (connectionState.value != VpnConnectionState.DISCONNECTED) {
                // Reconnect to new server if currently connected
                disconnectVpn()
                connectVpn()
            } else {
                _pingMs.value = null
            }
        }
    }

    fun toggleConnection() {
        if (connectionState.value == VpnConnectionState.DISCONNECTED) {
            connectVpn()
        } else {
            disconnectVpn()
        }
    }

    fun startVpnService() {
        val server = _selectedServer.value
        val configUrl = if (server != null) {
            try {
                CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        MyVpnService.startService(getApplication(), configUrl)
    }

    fun stopVpnService() {
        MyVpnService.stopService(getApplication())
    }

    private fun connectVpn() {
        startVpnService()
    }

    fun disconnectVpn() {
        stopVpnService()
    }

    fun triggerPing() {
        val server = _selectedServer.value ?: return
        if (connectionState.value != VpnConnectionState.CONNECTED) return
        
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            _isPinging.value = true
            _pingMs.value = null
            
            // Decrypt secured URL parameters at point of connection handshake
            val cipherText = server.encryptedConfig
            val key = server.encryptionKey
            var host = "188.114.97.6"
            var port = 443

            if (cipherText.isNotEmpty() && key.isNotEmpty()) {
                try {
                    val rawUrl = CryptoUtils.decrypt(cipherText, key)
                    val parsed = VlessConfig.parse(rawUrl)
                    if (parsed != null) {
                        host = parsed.address
                        port = parsed.port
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val result = ConnectionSimulator.measurePing(host, port)
            _pingMs.value = result
            _isPinging.value = false
        }
    }

    fun testAllServerPings() {
        viewModelScope.launch {
            _isPingingAll.value = true
            val currentServers = servers.value
            val pings = _serverPings.value.toMutableMap()
            
            currentServers.forEach { server ->
                pings[server.id] = null
            }
            _serverPings.value = pings
            
            currentServers.forEach { server ->
                var host = "188.114.97.6"
                var port = 443
                if (server.encryptedConfig.isNotEmpty() && server.encryptionKey.isNotEmpty()) {
                    try {
                        val rawUrl = CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey)
                        val parsed = VlessConfig.parse(rawUrl)
                        if (parsed != null) {
                            host = parsed.address
                            port = parsed.port
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val result = ConnectionSimulator.measurePing(host, port)
                pings[server.id] = result
                _serverPings.value = pings.toMap()
            }
            _isPingingAll.value = false
        }
    }

    fun testSingleServerPing(server: VpnServer) {
        viewModelScope.launch {
            val pings = _serverPings.value.toMutableMap()
            pings[server.id] = null // UI loading state indicators
            _serverPings.value = pings

            var host = "188.114.97.6"
            var port = 443
            if (server.encryptedConfig.isNotEmpty() && server.encryptionKey.isNotEmpty()) {
                try {
                    val rawUrl = CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey)
                    val parsed = VlessConfig.parse(rawUrl)
                    if (parsed != null) {
                        host = parsed.address
                        port = parsed.port
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val result = ConnectionSimulator.measurePing(host, port)
            val updatedMap = _serverPings.value.toMutableMap()
            updatedMap[server.id] = result
            _serverPings.value = updatedMap
        }
    }

    fun connectToBestServer() {
        viewModelScope.launch {
            _isPingingAll.value = true
            val currentServers = servers.value
            if (currentServers.isEmpty()) {
                _isPingingAll.value = false
                return@launch
            }
            
            val pings = mutableMapOf<String, Int>()
            currentServers.forEach { server ->
                var host = "188.114.97.6"
                var port = 443
                if (server.encryptedConfig.isNotEmpty() && server.encryptionKey.isNotEmpty()) {
                    try {
                        val rawUrl = CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey)
                        val parsed = VlessConfig.parse(rawUrl)
                        if (parsed != null) {
                            host = parsed.address
                            port = parsed.port
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val result = ConnectionSimulator.measurePing(host, port)
                pings[server.id] = result
                
                val updatedMap = _serverPings.value.toMutableMap()
                updatedMap[server.id] = result
                _serverPings.value = updatedMap
            }
            
            _isPingingAll.value = false
            
            val bestServerId = pings.minByOrNull { it.value }?.key
            val bestServer = currentServers.find { it.id == bestServerId }
            if (bestServer != null) {
                selectServer(bestServer)
                connectVpn()
            }
        }
    }

    // Server Management operations
    fun addServer(name: String, rawConfigUrl: String, remarks: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = VlessConfig.parse(rawConfigUrl)
            val finalName = if (parsed != null) parsed.remarks else name
            
            val aesKey = CryptoUtils.generateKey()
            val encryptedUrl = CryptoUtils.encrypt(rawConfigUrl, aesKey)

            val newServer = VpnServer(
                name = finalName.ifEmpty { "Custom Server" },
                configUrl = "SECURED_ENCRYPTED_FIELD",
                remarks = remarks.ifEmpty { parsed?.address ?: "Manual VLESS config" },
                isDefault = false,
                encryptedConfig = encryptedUrl,
                encryptionKey = aesKey,
                isUserConfig = false
            )
            vpnDao.insertServer(newServer)
        }
    }

    fun addUserServer(name: String, rawConfigUrl: String, remarks: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = VlessConfig.parse(rawConfigUrl)
            val finalName = if (parsed != null) {
                if (parsed.remarks.trim().isNotEmpty()) parsed.remarks else name
            } else name
            
            val aesKey = CryptoUtils.generateKey()
            val encryptedUrl = CryptoUtils.encrypt(rawConfigUrl, aesKey)

            val newServer = VpnServer(
                name = finalName.ifEmpty { "کانفیگ شخصی" },
                configUrl = "SECURED_ENCRYPTED_FIELD",
                remarks = remarks.ifEmpty { parsed?.address ?: "Manual VLESS config" },
                isDefault = false,
                encryptedConfig = encryptedUrl,
                encryptionKey = aesKey,
                isUserConfig = true
            )
            vpnDao.insertServer(newServer)
        }
    }

    fun updateServer(server: VpnServer, name: String, rawConfigUrl: String, remarks: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = VlessConfig.parse(rawConfigUrl)
            val finalName = name.ifEmpty { if (parsed != null) parsed.remarks else server.name }

            val aesKey = CryptoUtils.generateKey()
            val encryptedUrl = CryptoUtils.encrypt(rawConfigUrl, aesKey)

            val updatedServer = server.copy(
                name = finalName,
                remarks = remarks.ifEmpty { parsed?.address ?: "Manual VLESS config" },
                encryptedConfig = encryptedUrl,
                encryptionKey = aesKey
            )
            vpnDao.updateServer(updatedServer)

            if (_selectedServer.value?.id == server.id) {
                _selectedServer.value = updatedServer
            }
        }
    }

    fun getDecryptedConfig(server: VpnServer): String {
        return CryptoUtils.decrypt(server.encryptedConfig, server.encryptionKey)
    }

    fun deleteServer(server: VpnServer) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_selectedServer.value?.id == server.id) {
                _selectedServer.value = null
            }
            vpnDao.deleteServer(server)
        }
    }

    fun addSubscription(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.insertSubscription(SubscriptionItem(name = name, url = url))
        }
    }

    fun updateSubscription(item: SubscriptionItem, name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.updateSubscription(item.copy(name = name, url = url))
        }
    }

    fun deleteSubscription(item: SubscriptionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            subscriptionDao.deleteSubscription(item)
        }
    }

    fun syncSubscription(subscription: SubscriptionItem) {
        val url = subscription.url.trim()
        if (url.isEmpty()) {
            _subscriptionSyncError.value = "آدرس ساب تعریف نشده است."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingSubscription.value = true
            _subscriptionSyncError.value = null
            try {
                val rawContent = fetchUrl(url)
                val lines = decodeSubscriptionContent(rawContent)
                
                if (lines.isEmpty()) {
                    throw Exception("هیچ کانفیگ معتبری در لینک ساب یافت نشد.")
                }

                val newServers = mutableListOf<VpnServer>()
                lines.forEach { rawLine ->
                    val parsed = VlessConfig.parse(rawLine)
                    if (parsed != null) {
                        val aesKey = CryptoUtils.generateKey()
                        val encryptedUrl = CryptoUtils.encrypt(rawLine, aesKey)
                        newServers.add(
                            VpnServer(
                                name = parsed.remarks.ifEmpty { "${parsed.protocol.uppercase()} Server" },
                                configUrl = "SECURED_ENCRYPTED_FIELD",
                                remarks = parsed.address,
                                isDefault = false,
                                encryptedConfig = encryptedUrl,
                                encryptionKey = aesKey
                            )
                        )
                    }
                }

                if (newServers.isNotEmpty()) {
                    val currentSelectedName = _selectedServer.value?.name
                    
                    // Transactionally rebuild local server list based on sub
                    vpnDao.clearAllServers()
                    newServers.forEach {
                        vpnDao.insertServer(it)
                    }

                    // Restore server selection safely
                    val updatedList = vpnDao.getAllServersSync()
                    if (updatedList.isNotEmpty()) {
                        val matching = updatedList.find { it.name == currentSelectedName }
                        val finalSelection = matching ?: updatedList.first()
                        _selectedServer.value = finalSelection
                        sharedPrefs.edit()
                            .putString("selected_server_id", finalSelection.id)
                            .putString("selected_server_name", finalSelection.name)
                            .apply()
                    }
                } else {
                    throw Exception("فرمت کانفیگ‌ها در ساب پشتیبانی نمی‌شود.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _subscriptionSyncError.value = "خطا در بروزرسانی: ${e.localizedMessage ?: e.message}"
            } finally {
                _isSyncingSubscription.value = false
            }
        }
    }

    fun saveSubscriptionUrl(url: String) {
        sharedPrefs.edit().putString(PREF_SUB_URL, url).apply()
        _subscriptionUrl.value = url
    }

    fun triggerSubscriptionSync(manually: Boolean = true) {
        val url = if (_subscriptionUrl.value.trim().isNotEmpty()) _subscriptionUrl.value.trim() else getHardcodedSubscriptionUrl()
        if (url.isEmpty()) {
            if (manually) {
                _subscriptionSyncError.value = "آدرس ساب تعریف نشده است."
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isSyncingSubscription.value = true
            _subscriptionSyncError.value = null
            try {
                // Fetch subscription text from network
                val rawContent = fetchUrl(url)
                val lines = decodeSubscriptionContent(rawContent)
                
                if (lines.isEmpty()) {
                    throw Exception("هیچ کانفیگ معتبری در لینک ساب یافت نشد.")
                }

                // Process vless, vmess, trojan, hysteria, shadowsocks configs and build encrypted items
                val newServers = mutableListOf<VpnServer>()
                lines.forEach { rawLine ->
                    val parsed = VlessConfig.parse(rawLine)
                    if (parsed != null) {
                        val aesKey = CryptoUtils.generateKey()
                        val encryptedUrl = CryptoUtils.encrypt(rawLine, aesKey)
                        newServers.add(
                            VpnServer(
                                name = parsed.remarks.ifEmpty { "${parsed.protocol.uppercase()} Server" },
                                configUrl = "SECURED_ENCRYPTED_FIELD",
                                remarks = parsed.address,
                                isDefault = false,
                                encryptedConfig = encryptedUrl,
                                encryptionKey = aesKey
                            )
                        )
                    }
                }

                if (newServers.isNotEmpty()) {
                    val currentSelectedName = _selectedServer.value?.name
                    
                    // Transactionally rebuild local server list based on sub
                    vpnDao.clearAllServers()
                    newServers.forEach {
                        vpnDao.insertServer(it)
                    }

                    // Restore server selection safely
                    val updatedList = vpnDao.getAllServersSync()
                    if (updatedList.isNotEmpty()) {
                        val matching = updatedList.find { it.name == currentSelectedName }
                        val finalSelection = matching ?: updatedList.first()
                        _selectedServer.value = finalSelection
                        sharedPrefs.edit()
                            .putString("selected_server_id", finalSelection.id)
                            .putString("selected_server_name", finalSelection.name)
                            .apply()
                    }
                } else {
                    throw Exception("فرمت کانفیگ‌ها در ساب پشتیبانی نمی‌شود.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _subscriptionSyncError.value = "خطا در بروزرسانی: ${e.localizedMessage ?: e.message}"
            } finally {
                _isSyncingSubscription.value = false
            }
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val url = java.net.URL(urlStr)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.connect()
        if (connection.responseCode == 200) {
            return connection.inputStream.use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
        } else {
            throw Exception("کد پاسخ سرور: ${connection.responseCode}")
        }
    }

    private fun decodeSubscriptionContent(rawText: String): List<String> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        
        var decodedText = trimmed
        try {
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            decodedText = String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Not Base64 / raw URL list
        }
        
        return decodedText.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("vless://") ||
                line.startsWith("vmess://") ||
                line.startsWith("trojan://") ||
                line.startsWith("hysteria2://") ||
                line.startsWith("hysteria://") ||
                line.startsWith("ss://")
            }
            .toList()
    }

    private suspend fun seedFallbackServers() {
        val fallbacks = listOf(
            "vless://cfdcafeb-1122-3344-5566-778899aabbcc@de.mrvpn.xyz:443?path=%2Fmrvpn&security=tls&alpn=h2%2Chttp%2F1.1&host=de.mrvpn.xyz&type=ws&sni=de.mrvpn.xyz#%D8%A2%D9%84%D9%85%D8%A7%D9%86%20-%20VLESS%20%D9%BE%D8%B1%D8%B3%D8%B1%D8%B9%D8%AA",
            "trojan://cfdcafeb-1122-3344-5566-778899aabbcc@fi.mrvpn.xyz:443?security=tls&sni=fi.mrvpn.xyz#%D9%81%D9%86%D9%84%D8%A7%D9%86%D8%AF%20-%20Trojan%20%D8%A7%D9%85%D9%86",
            "vmess://eyJhZGQiOiJ0ci5tcnZwbi54eXoiLCJhaWQiOiIwIiwiaG9zdCI6InRyLm1ydnBuLnh5eiIsImlkIjoiY2ZkY2FmZWItMTEyMi0zMzQ0LTU1NjYtNzc4ODk5YWFiYmNjIiwibmV0Ijoid3MiLCJwYXRoIjoiL21ydnBuIiwicG9ydCI6IjQ0MyIsInBzIjoi2KrYs9qp24zZhyAtIFZNZXNzIiwidGxzIjoidGxzIiwidHlwZSI6Im5vbmUifQ=="
        )
        
        fallbacks.forEach { rawLine ->
            val parsed = VlessConfig.parse(rawLine)
            if (parsed != null) {
                val aesKey = CryptoUtils.generateKey()
                val encryptedUrl = CryptoUtils.encrypt(rawLine, aesKey)
                vpnDao.insertServer(
                    VpnServer(
                        name = parsed.remarks,
                        configUrl = "SECURED_ENCRYPTED_FIELD",
                        remarks = parsed.address,
                        isDefault = true,
                        encryptedConfig = encryptedUrl,
                        encryptionKey = aesKey
                    )
                )
            }
        }
    }

    fun fetchPublicIp() {
        viewModelScope.launch {
            val isConnected = (connectionState.value == VpnConnectionState.CONNECTED)
            _publicIp.value = "در حال دریافت آی‌پی..."
            _ipLocation.value = ""
            
            withContext(Dispatchers.IO) {
                if (isConnected) {
                    val currentServer = _selectedServer.value
                    val serverName = currentServer?.name ?: ""
                    val host = currentServer?.remarks ?: ""
                    
                    var mockIp = "104.244.75.11"
                    var loc = "سرور امن خارج از کشور 🌐"
                    
                    if (serverName.contains("آلمان") || host.contains("de.")) {
                        mockIp = "185.190.140.68"
                        loc = "آلمان (فرانکفورت) 🇩🇪 - شبکه امن"
                    } else if (serverName.contains("فنلاند") || host.contains("fi.")) {
                        mockIp = "95.175.99.102"
                        loc = "فنلاند (هلسینکی) 🇫🇮 - شبکه امن"
                    } else if (serverName.contains("ترکیه") || host.contains("tr.")) {
                        mockIp = "176.240.111.90"
                        loc = "ترکیه (استانبول) 🇹🇷 - شبکه امن"
                    }
                    
                    // Try to resolve the actual server domain IP if possible
                    if (host.isNotEmpty()) {
                        try {
                            val addr = java.net.InetAddress.getByName(host).hostAddress
                            if (!addr.isNullOrEmpty() && addr != "127.0.0.1") {
                                mockIp = addr
                            }
                        } catch (e: Exception) {
                            // ignore and use regional mock IP
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        _publicIp.value = mockIp
                        _ipLocation.value = loc
                    }
                } else {
                    val endpoints = listOf(
                        "https://api.ipify.org",
                        "https://icanhazip.com",
                        "https://ifconfig.me/ip",
                        "https://ipinfo.io/ip"
                    )
                    
                    var resolvedIp = ""
                    for (urlStr in endpoints) {
                        try {
                            val url = java.net.URL(urlStr)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            conn.requestMethod = "GET"
                            
                            val code = conn.responseCode
                            if (code == 200) {
                                val ip = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                                if (ip.isNotEmpty()) {
                                    resolvedIp = ip
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            // ignore and try next
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (resolvedIp.isNotEmpty()) {
                            _publicIp.value = resolvedIp
                            _ipLocation.value = "آی‌پی اصلی (ایران) 🇮🇷"
                        } else {
                            _publicIp.value = "نامشخص (عدم اتصال به اینترنت)"
                            _ipLocation.value = ""
                        }
                    }
                }
            }
        }
    }
}

enum class VpnConnectionState {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING
}
