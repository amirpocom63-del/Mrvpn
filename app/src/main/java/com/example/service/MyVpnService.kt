package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.VlessConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.InetAddress
import java.util.ArrayList
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLSocket

class MyVpnService : VpnService(), Runnable {
    
    private var vpnThread: Thread? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    private var proxyServerSocket: ServerSocket? = null
    private var proxyThread: Thread? = null
    private val proxyPort = 20808
    private var activeConfigUrl: String? = null
    private var tunnelReaderThread: Thread? = null
    private val ipToDomainMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val CHANNEL_ID = "MyVpnServiceChannel"
    private val NOTIFICATION_ID = 4591

    private fun createNotification(): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 
                0, 
                notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        val disconnectIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 
            1, 
            disconnectIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MrVpn متصل است")
            .setContentText("ترافیک شما به صورت امن و رمزگذاری‌شده عبور می‌کند.")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "قطع اتصال",
                disconnectPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service Connection Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the active VPN connection state"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }
        
        activeConfigUrl = intent?.getStringExtra("EXTRA_CONFIG_URL")
        
        // Start Foreground immediately to prevent OS termination or ForegroundServiceStartNotAllowedException
        try {
            createNotificationChannel()
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to start as Foreground Service", e)
        }

        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    @Synchronized
    private fun startVpn() {
        if (vpnThread != null) return
        _vpnState.value = ConnectionState.CONNECTING
        vpnThread = Thread(this, "MyVpnThread").apply { start() }
    }

    @Synchronized
    private fun stopVpn() {
        _vpnState.value = ConnectionState.DISCONNECTING
        stopLocalProxy()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error closing interface", e)
        }
        vpnInterface = null
        tunnelReaderThread?.interrupt()
        tunnelReaderThread = null
        vpnThread?.interrupt()
        vpnThread = null
        _vpnState.value = ConnectionState.DISCONNECTED
        stopForeground(true)
    }

    override fun run() {
        try {
            startLocalProxy()

            val builder = Builder()
                .setSession("MrVpn Connection")
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .setMtu(1500)
            
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.e("MyVpnService", "Failed to add disallowed application", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", proxyPort))
            }
            
            val activeInterface = builder.establish()
            vpnInterface = activeInterface
            
            if (activeInterface != null) {
                _vpnState.value = ConnectionState.CONNECTED
                Log.i("MyVpnService", "Real VPN tunnel interface established successfully with Port $proxyPort")
                
                val inputStream = java.io.FileInputStream(activeInterface.fileDescriptor)
                val outputStream = java.io.FileOutputStream(activeInterface.fileDescriptor)
                
                tunnelReaderThread = Thread {
                    val buffer = ByteArray(16384)
                    try {
                        while (!Thread.currentThread().isInterrupted) {
                            val readBytes = inputStream.read(buffer)
                            if (readBytes <= 0) {
                                Thread.sleep(50)
                                continue
                            }
                            
                            // Intercept Packets of IPv4
                            try {
                                val ipVersion = (buffer[0].toInt() shr 4) and 0x0F
                                if (ipVersion == 4 && readBytes >= 20) {
                                    val ipProto = buffer[9].toInt() and 0xFF
                                    if (ipProto == 17) {
                                        handleDnsPacket(buffer, readBytes, outputStream)
                                    } else if (ipProto == 6) {
                                        handleTcpPacket(buffer, readBytes, outputStream)
                                    }
                                }
                            } catch (packetEx: Exception) {
                                // Ignore packet processing faults to maintain connection robustness
                            }
                        }
                    } catch (e: Exception) {
                        Log.i("MyVpnService", "TUN reader stopped: ${e.message}")
                    }
                }.apply { start() }
                
                try {
                    tunnelReaderThread?.join()
                } catch (e: InterruptedException) {
                    Log.i("MyVpnService", "VPN join interrupted")
                }
            } else {
                Log.e("MyVpnService", "Failed to establish VPN interface (null)")
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error establishing VPN interface", e)
        } finally {
            stopVpn()
        }
    }

    private fun handleDnsPacket(packet: ByteArray, len: Int, out: java.io.FileOutputStream) {
        if (len < 28) return
        val ipVersion = (packet[0].toInt() shr 4) and 0x0F
        if (ipVersion != 4) return // IPv4 only for DNS queries
        
        val ipProto = packet[9].toInt() and 0xFF
        if (ipProto != 17) return // UDP only
        
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (len < ipHeaderLen + 8) return
        
        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        
        if (destPort != 53) return // DNS query is on port 53
        
        val dnsOffset = ipHeaderLen + 8
        if (len < dnsOffset + 12) return
        
        val txId0 = packet[dnsOffset]
        val txId1 = packet[dnsOffset + 1]
        val flags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 3].toInt() and 0xFF)
        val qr = (flags shr 15) and 0x01
        if (qr != 0) return // It's already a response, ignore
        
        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 5].toInt() and 0xFF)
        if (qdCount <= 0) return
        
        try {
            val (domainName, _) = parseDnsName(packet, dnsOffset + 12)
            if (domainName.isEmpty()) return
            
            // Capture DNS payload safely synchronously (prevents concurrent overwrite in recycled buffer)
            val dnsPayloadOffset = ipHeaderLen + 8
            val dnsPayloadLen = len - dnsPayloadOffset
            if (dnsPayloadLen <= 0) return
            val dnsPayload = packet.copyOfRange(dnsPayloadOffset, len)
            
            val srcIp = ByteArray(4)
            val destIp = ByteArray(4)
            System.arraycopy(packet, 12, srcIp, 0, 4)
            System.arraycopy(packet, 16, destIp, 0, 4)
            
            Thread {
                var dnsSocket: java.net.DatagramSocket? = null
                try {
                    dnsSocket = java.net.DatagramSocket()
                    protect(dnsSocket)
                    dnsSocket.soTimeout = 2000
                    
                    val dnsServers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222")
                    var dnsRespBytes: ByteArray? = null
                    
                    // 1. Try DNS over secure VLESS Tunnel first (100% encrypted, avoids any local censorship/detection)
                    if (activeConfigUrl != null) {
                        for (serverIp in dnsServers) {
                            try {
                                val resp = resolveDnsOverTunnel(dnsPayload, serverIp)
                                if (resp != null && resp.size > 12) {
                                    dnsRespBytes = resp
                                    Log.d("MyVpnService", "Resolved DNS securely via VLESS Tunnel over $serverIp for $domainName")
                                    break
                                }
                            } catch (ex: Exception) {
                                // Try next
                            }
                        }
                    }
                    
                    // 2. Fallback to DNS over HTTPS (DoH) over direct interface (extremely robust, uses port 443 which is always open)
                    if (dnsRespBytes == null) {
                        for (serverIp in dnsServers) {
                            try {
                                val resp = resolveDnsOverHttps(dnsPayload, serverIp)
                                if (resp != null && resp.size > 12) {
                                    dnsRespBytes = resp
                                    Log.d("MyVpnService", "Resolved DNS via DoH over $serverIp for $domainName")
                                    break
                                }
                            } catch (ex: Exception) {
                                // Try next
                            }
                        }
                    }
                    
                    // 3. Fallback to standard DNS over TCP
                    if (dnsRespBytes == null) {
                        for (serverIp in dnsServers) {
                            try {
                                val resp = resolveDnsOverTcp(dnsPayload, serverIp)
                                if (resp != null && resp.size > 12) {
                                    dnsRespBytes = resp
                                    Log.d("MyVpnService", "Resolved DNS via TCP over $serverIp for $domainName")
                                    break
                                }
                            } catch (ex: Exception) {
                                // Try next
                            }
                        }
                    }
                    
                    // 4. Fallback to standard UDP DNS
                    if (dnsRespBytes == null) {
                        for (serverIp in dnsServers) {
                            try {
                                val serverAddress = java.net.InetAddress.getByName(serverIp)
                                val sendPacket = java.net.DatagramPacket(dnsPayload, dnsPayload.size, serverAddress, 53)
                                dnsSocket.send(sendPacket)
                                
                                val recvBuf = ByteArray(2048)
                                val recvPacket = java.net.DatagramPacket(recvBuf, recvBuf.size)
                                dnsSocket.receive(recvPacket)
                                
                                val resp = recvPacket.data.copyOfRange(0, recvPacket.length)
                                if (resp.size > 12) {
                                    dnsRespBytes = resp
                                    Log.d("MyVpnService", "Resolved DNS via UDP over $serverIp for $domainName")
                                    break
                                }
                            } catch (ex: Exception) {
                                // Try next
                            }
                        }
                    }
                    
                    if (dnsRespBytes == null) {
                        Log.w("MyVpnService", "All TCP/UDP DNS servers failed for $domainName, falling back to local resolver...")
                        val resolvedIps = try {
                            java.net.InetAddress.getAllByName(domainName).toList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        if (resolvedIps.isNotEmpty()) {
                            for (ip in resolvedIps) {
                                val ipStr = ip.hostAddress
                                ipToDomainMap[ipStr] = domainName
                                Log.d("MyVpnService", "Parsed map via local resolver fallback: $ipStr -> $domainName")
                            }
                            dnsRespBytes = constructLocalDnsResponse(txId0, txId1, domainName, resolvedIps)
                        } else {
                            Log.e("MyVpnService", "All DNS servers and local resolver failed for $domainName")
                            return@Thread
                        }
                    } else {
                        // Parse response to associate IP -> domain safely
                        parseDnsResponseAndPopulateMap(dnsRespBytes, domainName)
                    }
                    
                    // Wrap response in IPv4/UDP packet
                    val udpLen = 8 + dnsRespBytes.size
                    val ipLen = 20 + udpLen
                    val respPacket = ByteArray(ipLen)
                    
                    // IPv4 Header
                    respPacket[0] = 0x45.toByte()     // Version 4, Header Len 5 (20 bytes)
                    respPacket[1] = 0x00.toByte()     // TOS
                    respPacket[2] = ((ipLen shr 8) and 0xFF).toByte()
                    respPacket[3] = (ipLen and 0xFF).toByte()
                    respPacket[4] = 0x00.toByte()     // Identification
                    respPacket[5] = 0x00.toByte()
                    respPacket[6] = 0x40.toByte()     // Flags: Don't Fragment
                    respPacket[7] = 0x00.toByte()
                    respPacket[8] = 0x40.toByte()     // TTL: 64
                    respPacket[9] = 17.toByte()       // Protocol: UDP (17)
                    respPacket[10] = 0x00.toByte()    // Header Checksum
                    respPacket[11] = 0x00.toByte()
                    
                    // Swap IP addresses (Src IP is now the query's dest IP; Dest IP is the query's src IP)
                    System.arraycopy(destIp, 0, respPacket, 12, 4)
                    System.arraycopy(srcIp, 0, respPacket, 16, 4)
                    
                    // UDP Header
                    respPacket[20] = 0x00.toByte()
                    respPacket[21] = 0x35.toByte()    // Source Port: 53
                    respPacket[22] = ((srcPort shr 8) and 0xFF).toByte()
                    respPacket[23] = (srcPort and 0xFF).toByte()
                    respPacket[24] = ((udpLen shr 8) and 0xFF).toByte()
                    respPacket[25] = (udpLen and 0xFF).toByte()
                    respPacket[26] = 0x00.toByte()    // Checksum disabled
                    respPacket[27] = 0x00.toByte()
                    
                    // Copy dns response payload
                    System.arraycopy(dnsRespBytes, 0, respPacket, 28, dnsRespBytes.size)
                    
                    // Recalculate IPv4 Header Checksum
                    val checksum = calculateChecksum(respPacket, 20)
                    respPacket[10] = ((checksum shr 8) and 0xFF).toByte()
                    respPacket[11] = (checksum and 0xFF).toByte()
                    
                    synchronized(out) {
                        out.write(respPacket)
                        out.flush()
                    }
                } catch (e: Exception) {
                    Log.e("MyVpnService", "Error forwarding DNS to upstream for $domainName", e)
                } finally {
                    try { dnsSocket?.close() } catch (ex: Exception) {}
                }
            }.start()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error parsing DNS packet", e)
        }
    }

    private fun parseDnsResponseAndPopulateMap(responseBytes: ByteArray, domainName: String) {
        if (responseBytes.size < 12) return
        val qdCount = ((responseBytes[4].toInt() and 0xFF) shl 8) or (responseBytes[5].toInt() and 0xFF)
        val anCount = ((responseBytes[6].toInt() and 0xFF) shl 8) or (responseBytes[7].toInt() and 0xFF)
        
        var pos = 12
        // Skip Questions section
        for (i in 0 until qdCount) {
            pos = skipDnsName(responseBytes, pos)
            pos += 4 // Type (2 bytes) + Class (2 bytes)
        }
        
        // Parse Answers section
        for (i in 0 until anCount) {
            if (pos >= responseBytes.size) break
            pos = skipDnsName(responseBytes, pos)
            if (pos + 10 > responseBytes.size) break
            
            val type = ((responseBytes[pos].toInt() and 0xFF) shl 8) or (responseBytes[pos + 1].toInt() and 0xFF)
            val rdLength = ((responseBytes[pos + 8].toInt() and 0xFF) shl 8) or (responseBytes[pos + 9].toInt() and 0xFF)
            pos += 10
            
            if (pos + rdLength > responseBytes.size) break
            
            if (type == 1 && rdLength == 4) { // IPv4 A record
                val ipBytes = responseBytes.copyOfRange(pos, pos + 4)
                val ipAddress = java.net.InetAddress.getByAddress(ipBytes).hostAddress
                ipToDomainMap[ipAddress] = domainName
                Log.d("MyVpnService", "Parsed map: $ipAddress -> $domainName")
            } else if (type == 28 && rdLength == 16) { // IPv6 AAAA record
                val ipBytes = responseBytes.copyOfRange(pos, pos + 16)
                val ipAddress = java.net.InetAddress.getByAddress(ipBytes).hostAddress
                ipToDomainMap[ipAddress] = domainName
                Log.d("MyVpnService", "Parsed map: $ipAddress -> $domainName")
            }
            pos += rdLength
        }
    }

    private fun resolveDnsOverTcp(dnsPayload: ByteArray, serverIp: String): ByteArray? {
        var socket: java.net.Socket? = null
        try {
            socket = java.net.Socket()
            protect(socket)
            socket.connect(java.net.InetSocketAddress(serverIp, 53), 2000)
            socket.soTimeout = 2500
            
            val output = socket.getOutputStream()
            val input = socket.getInputStream()
            
            val len = dnsPayload.size
            output.write((len shr 8) and 0xFF)
            output.write(len and 0xFF)
            output.write(dnsPayload)
            output.flush()
            
            val len0 = input.read()
            val len1 = input.read()
            if (len0 < 0 || len1 < 0) return null
            val respLen = (len0 shl 8) or len1
            if (respLen <= 0 || respLen > 4096) return null
            
            val respBuf = ByteArray(respLen)
            var bytesRead = 0
            while (bytesRead < respLen) {
                val r = input.read(respBuf, bytesRead, respLen - bytesRead)
                if (r < 0) return null
                bytesRead += r
            }
            return respBuf
        } catch (e: Exception) {
            return null
        } finally {
            try { socket?.close() } catch (ex: Exception) {}
        }
    }

    private fun resolveDnsOverTunnel(dnsPayload: ByteArray, serverIp: String): ByteArray? {
        var tunnel: RemoteTunnel? = null
        try {
            tunnel = connectToRemote(serverIp, 53) ?: return null
            
            val len = dnsPayload.size
            val queryWithLen = ByteArray(2 + len)
            queryWithLen[0] = ((len shr 8) and 0xFF).toByte()
            queryWithLen[1] = (len and 0xFF).toByte()
            System.arraycopy(dnsPayload, 0, queryWithLen, 2, len)
            
            tunnel.send(queryWithLen, queryWithLen.size)
            
            val firstChunk = tunnel.receive() ?: return null
            if (firstChunk.size < 2) return null
            
            val respLen = ((firstChunk[0].toInt() and 0xFF) shl 8) or (firstChunk[1].toInt() and 0xFF)
            if (respLen <= 0 || respLen > 4096) return null
            
            val bos = java.io.ByteArrayOutputStream()
            bos.write(firstChunk, 2, firstChunk.size - 2)
            
            var timeoutCount = 0
            while (bos.size() < respLen && timeoutCount < 10) {
                val chunk = tunnel.receive()
                if (chunk != null && chunk.isNotEmpty()) {
                    bos.write(chunk)
                } else {
                    Thread.sleep(50)
                    timeoutCount++
                }
            }
            
            val bytes = bos.toByteArray()
            return if (bytes.size >= respLen) {
                bytes.copyOfRange(0, respLen)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error in resolveDnsOverTunnel to $serverIp", e)
            return null
        } finally {
            try { tunnel?.close() } catch (ex: Exception) {}
        }
    }

    private fun resolveDnsOverHttps(dnsPayload: ByteArray, dnsServerIp: String): ByteArray? {
        var conn: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL("https://$dnsServerIp/dns-query")
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.doInput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            
            val out = conn.outputStream
            out.write(dnsPayload)
            out.flush()
            out.close()
            
            val code = conn.responseCode
            if (code == 200) {
                val input = conn.inputStream
                val outBytes = java.io.ByteArrayOutputStream()
                val buf = ByteArray(2048)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    outBytes.write(buf, 0, r)
                }
                return outBytes.toByteArray()
            } else {
                Log.w("MyVpnService", "DoH failed with code $code on server $dnsServerIp")
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "DoH exception on server $dnsServerIp: ${e.message}")
        } finally {
            conn?.disconnect()
        }
        return null
    }

    private fun constructLocalDnsResponse(txId0: Byte, txId1: Byte, domainName: String, ips: List<java.net.InetAddress>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // Transaction ID
        out.write(txId0.toInt())
        out.write(txId1.toInt())
        // Flags: 0x8180 (Standard response, no error)
        out.write(0x81)
        out.write(0x80)
        // Questions count: 1
        out.write(0x00)
        out.write(0x01)
        // Answers count: ips.size
        out.write(0x00)
        out.write(ips.size and 0xFF)
        // Authority RRs: 0
        out.write(0x00)
        out.write(0x00)
        // Additional RRs: 0
        out.write(0x00)
        out.write(0x00)
        
        // Question section
        val labels = domainName.split(".")
        for (label in labels) {
            val labelBytes = label.toByteArray(Charsets.US_ASCII)
            out.write(labelBytes.size)
            out.write(labelBytes)
        }
        out.write(0) // Null byte terminates label sequence
        
        // Type A (1), Class IN (1)
        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x01)
        
        // Answers section
        for (ip in ips) {
            val addr = ip.address
            if (addr.size == 4) { // IPv4 A record
                out.write(0xC0)
                out.write(0x0C)
                // Type A (1), Class IN (1)
                out.write(0x00)
                out.write(0x01)
                out.write(0x00)
                out.write(0x01)
                // TTL (120 sec)
                out.write(0x00)
                out.write(0x00)
                out.write(0x00)
                out.write(0x78)
                // Data length: 4
                out.write(0x00)
                out.write(0x04)
                // IP bytes
                out.write(addr)
            }
        }
        return out.toByteArray()
    }

    private fun skipDnsName(bytes: ByteArray, startPos: Int): Int {
        var pos = startPos
        while (pos < bytes.size) {
            val len = bytes[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if ((len and 0xC0) == 0xC0) { // Compression pointer
                pos += 2
                break
            }
            pos += 1 + len
        }
        return pos
    }

    private fun parseDnsName(packet: ByteArray, startOffset: Int): Pair<String, Int> {
        var pos = startOffset
        val sb = java.lang.StringBuilder()
        while (pos < packet.size) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) {
                pos++
                break
            }
            if (sb.isNotEmpty()) {
                sb.append('.')
            }
            if (pos + 1 + len <= packet.size) {
                sb.append(String(packet, pos + 1, len, Charsets.US_ASCII))
            }
            pos += 1 + len
        }
        return Pair(sb.toString(), pos)
    }

    private fun calculateChecksum(buf: ByteArray, length: Int): Int {
        var sum = 0
        var i = 0
        while (i < length) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private val activeTcpSessions = java.util.concurrent.ConcurrentHashMap<String, TransparentTcpSession>()

    private fun handleTcpPacket(packet: ByteArray, len: Int, out: java.io.FileOutputStream) {
        if (len < 40) return
        val ipVersion = (packet[0].toInt() shr 4) and 0x0F
        if (ipVersion != 4) return
        
        val ipProto = packet[9].toInt() and 0xFF
        if (ipProto != 6) return
        
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (len < ipHeaderLen + 20) return
        
        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
        
        val srcIp = ByteArray(4)
        val destIp = ByteArray(4)
        System.arraycopy(packet, 12, srcIp, 0, 4)
        System.arraycopy(packet, 16, destIp, 0, 4)
        
        // Skip loopback connection attempts
        if (srcPort == proxyPort || destPort == proxyPort) return
        
        val rawIp = java.net.InetAddress.getByAddress(destIp).hostAddress
        val destHost = ipToDomainMap[rawIp] ?: rawIp
        val sessionKey = "$srcPort->$destHost:$destPort"
        
        val seqBytes = ByteArray(4)
        System.arraycopy(packet, ipHeaderLen + 4, seqBytes, 0, 4)
        val seq = ((seqBytes[0].toInt() and 0xFF).toLong() shl 24) or
                  ((seqBytes[1].toInt() and 0xFF).toLong() shl 16) or
                  ((seqBytes[2].toInt() and 0xFF).toLong() shl 8) or
                  (seqBytes[3].toInt() and 0xFF).toLong()
                  
        val flags = packet[ipHeaderLen + 13].toInt() and 0xFF
        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0
        
        val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() shr 4) and 0x0F) * 4
        val payloadOffset = ipHeaderLen + tcpHeaderLen
        val payloadLen = len - payloadOffset
        
        if (destPort == 853) {
            sendTcpPacket(
                srcIp = destIp,
                dstIp = srcIp,
                srcPort = destPort,
                dstPort = srcPort,
                seq = 0,
                ack = seq + (if (isSyn) 1 else 0) + payloadLen,
                flags = 0x04, // RST
                payload = null,
                outputStream = out
            )
            return
        }
        
        if (isRst) {
            val session = activeTcpSessions.remove(sessionKey)
            session?.close()
            return
        }
        
        var session = activeTcpSessions[sessionKey]
        if (isSyn) {
            if (session != null) {
                session.close()
            }
            val newSession = TransparentTcpSession(
                srcPort = srcPort,
                destIpStr = destHost,
                destPort = destPort,
                clientIp = srcIp,
                destIp = destIp,
                outputStream = out,
                vpnService = this
            )
            newSession.clientSeq = seq
            newSession.state = TransparentTcpSession.SessionState.SYN_RECEIVED
            activeTcpSessions[sessionKey] = newSession
            
            sendTcpPacket(
                srcIp = destIp,
                dstIp = srcIp,
                srcPort = destPort,
                dstPort = srcPort,
                seq = newSession.serverSeq,
                ack = newSession.clientSeq + 1,
                flags = 0x12, // SYN | ACK
                payload = null,
                outputStream = out
            )
            newSession.serverSeq++
            return
        }
        
        if (session == null) {
            sendTcpPacket(
                srcIp = destIp,
                dstIp = srcIp,
                srcPort = destPort,
                dstPort = srcPort,
                seq = 0,
                ack = seq + 1,
                flags = 0x04, // RST
                payload = null,
                outputStream = out
            )
            return
        }
        
        if (isFin) {
            sendTcpPacket(
                srcIp = destIp,
                dstIp = srcIp,
                srcPort = destPort,
                dstPort = srcPort,
                seq = session.serverSeq,
                ack = seq + 1,
                flags = 0x10, // ACK
                payload = null,
                outputStream = out
            )
            activeTcpSessions.remove(sessionKey)
            session.close()
            return
        }
        
        if (isAck) {
            if (session.state == TransparentTcpSession.SessionState.SYN_RECEIVED) {
                session.state = TransparentTcpSession.SessionState.ESTABLISHED
                session.clientSeq = seq
                
                Thread {
                    try {
                        val tunnel = connectToRemote(session.destIpStr, session.destPort)
                        if (tunnel == null) {
                            sendTcpPacket(
                                srcIp = session.destIp,
                                dstIp = session.clientIp,
                                srcPort = session.destPort,
                                dstPort = session.srcPort,
                                seq = session.serverSeq,
                                ack = session.clientSeq,
                                flags = 0x04, // RST
                                payload = null,
                                outputStream = out
                            )
                            activeTcpSessions.remove(sessionKey)
                            session.close()
                            return@Thread
                        }
                        session.remoteTunnel = tunnel
                        
                        session.outboundJob = Thread {
                            try {
                                while (!Thread.currentThread().isInterrupted && session.state == TransparentTcpSession.SessionState.ESTABLISHED) {
                                    val data = tunnel.receive() ?: break
                                    if (data.isNotEmpty()) {
                                        sendTcpPacket(
                                            srcIp = session.destIp,
                                            dstIp = session.clientIp,
                                            srcPort = session.destPort,
                                            dstPort = session.srcPort,
                                            seq = session.serverSeq,
                                            ack = session.clientSeq,
                                            flags = 0x18, // PSH | ACK
                                            payload = data,
                                            outputStream = out
                                        )
                                        session.serverSeq += data.size
                                    }
                                }
                            } catch (e: Exception) {
                            } finally {
                                if (activeTcpSessions[sessionKey] == session) {
                                    sendTcpPacket(
                                        srcIp = session.destIp,
                                        dstIp = session.clientIp,
                                        srcPort = session.destPort,
                                        dstPort = session.srcPort,
                                        seq = session.serverSeq,
                                        ack = session.clientSeq,
                                        flags = 0x11, // FIN | ACK
                                        payload = null,
                                        outputStream = out
                                    )
                                    activeTcpSessions.remove(sessionKey)
                                }
                                session.close()
                            }
                        }.apply { start() }
                        
                    } catch (e: Exception) {
                        activeTcpSessions.remove(sessionKey)
                        session.close()
                    }
                }.start()
            }
            
            if (payloadLen > 0 && session.state == TransparentTcpSession.SessionState.ESTABLISHED) {
                val payloadBytes = ByteArray(payloadLen)
                System.arraycopy(packet, payloadOffset, payloadBytes, 0, payloadLen)
                
                try {
                    val tunnel = session.remoteTunnel
                    if (tunnel != null) {
                        tunnel.send(payloadBytes, payloadLen)
                        
                        session.clientSeq = seq + payloadLen
                        sendTcpPacket(
                            srcIp = session.destIp,
                            dstIp = session.clientIp,
                            srcPort = session.destPort,
                            dstPort = session.srcPort,
                            seq = session.serverSeq,
                            ack = session.clientSeq,
                            flags = 0x10, // ACK
                            payload = null,
                            outputStream = out
                        )
                    }
                } catch (e: Exception) {
                    Log.e("MyVpnService", "Error sending TCP payload to tunnel", e)
                }
            }
        }
    }

    private fun sendTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray?,
        outputStream: java.io.FileOutputStream
    ) {
        val payloadLen = payload?.size ?: 0
        val tcpHeaderLen = 20
        val ipHeaderLen = 20
        val totalLen = ipHeaderLen + tcpHeaderLen + payloadLen
        
        val packet = ByteArray(totalLen)
        
        packet[0] = 0x45.toByte()
        packet[1] = 0x00.toByte()
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        
        packet[4] = 0x00.toByte()
        packet[5] = 0x00.toByte()
        
        packet[6] = 0x40.toByte()
        packet[7] = 0x00.toByte()
        
        packet[8] = 0x40.toByte()
        packet[9] = 6.toByte()
        
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()
        
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)
        
        val ipChecksum = calculateChecksum(packet, ipHeaderLen)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()
        
        val t = ipHeaderLen
        packet[t + 0] = ((srcPort shr 8) and 0xFF).toByte()
        packet[t + 1] = (srcPort and 0xFF).toByte()
        packet[t + 2] = ((dstPort shr 8) and 0xFF).toByte()
        packet[t + 3] = (dstPort and 0xFF).toByte()
        
        packet[t + 4] = ((seq shr 24) and 0xFF).toByte()
        packet[t + 5] = ((seq shr 16) and 0xFF).toByte()
        packet[t + 6] = ((seq shr 8) and 0xFF).toByte()
        packet[t + 7] = (seq and 0xFF).toByte()
        
        packet[t + 8] = ((ack shr 24) and 0xFF).toByte()
        packet[t + 9] = ((ack shr 16) and 0xFF).toByte()
        packet[t + 10] = ((ack shr 8) and 0xFF).toByte()
        packet[t + 11] = (ack and 0xFF).toByte()
        
        packet[t + 12] = 0x50.toByte()
        packet[t + 13] = flags.toByte()
        
        packet[t + 14] = 0xFF.toByte()
        packet[t + 15] = 0xFF.toByte()
        
        packet[t + 16] = 0x00.toByte()
        packet[t + 17] = 0x00.toByte()
        
        packet[t + 18] = 0x00.toByte()
        packet[t + 19] = 0x00.toByte()
        
        if (payload != null && payloadLen > 0) {
            System.arraycopy(payload, 0, packet, ipHeaderLen + tcpHeaderLen, payloadLen)
        }
        
        val tcpChecksum = computeTcpChecksum(packet, ipHeaderLen, tcpHeaderLen + payloadLen, srcIp, dstIp)
        packet[t + 16] = ((tcpChecksum shr 8) and 0xFF).toByte()
        packet[t + 17] = (tcpChecksum and 0xFF).toByte()
        
        synchronized(outputStream) {
            try {
                outputStream.write(packet)
                outputStream.flush()
            } catch (e: Exception) {
            }
        }
    }

    private fun computeTcpChecksum(
        packet: ByteArray,
        ipHeaderLen: Int,
        tcpLen: Int,
        srcIp: ByteArray,
        dstIp: ByteArray
    ): Int {
        val pseudoHeader = ByteArray(12 + tcpLen)
        System.arraycopy(srcIp, 0, pseudoHeader, 0, 4)
        System.arraycopy(dstIp, 0, pseudoHeader, 4, 4)
        pseudoHeader[8] = 0.toByte()
        pseudoHeader[9] = 6.toByte()
        pseudoHeader[10] = ((tcpLen shr 8) and 0xFF).toByte()
        pseudoHeader[11] = (tcpLen and 0xFF).toByte()
        
        System.arraycopy(packet, ipHeaderLen, pseudoHeader, 12, tcpLen)
        
        pseudoHeader[28] = 0.toByte()
        pseudoHeader[29] = 0.toByte()
        
        var sum = 0
        var i = 0
        val len = pseudoHeader.size
        while (i < len - 1) {
            val word = ((pseudoHeader[i].toInt() and 0xFF) shl 8) or (pseudoHeader[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < len) {
            val word = (pseudoHeader[i].toInt() and 0xFF) shl 8
            sum += word
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    // Local Dual Proxy Server
    private fun startLocalProxy() {
        if (proxyServerSocket != null) return
        try {
            proxyServerSocket = ServerSocket(proxyPort, 128, java.net.InetAddress.getByName("127.0.0.1")).apply {
                reuseAddress = true
            }
            proxyThread = Thread(Runnable {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val clientSocket = proxyServerSocket?.accept() ?: break
                        Thread {
                            try {
                                clientSocket.soTimeout = 30000
                                val input = clientSocket.getInputStream()
                                val firstByte = input.read()
                                if (firstByte == -1) {
                                    try { clientSocket.close() } catch (e: Exception) {}
                                    return@Thread
                                }
                                
                                if (firstByte == 0x05) {
                                    handleSocks5(clientSocket, firstByte)
                                } else if (firstByte in 0x20..0x7E) {
                                    val remainingLine = readLine(input) ?: ""
                                    val firstLine = firstByte.toChar().toString() + remainingLine
                                    handleHttpRequest(clientSocket, firstLine)
                                } else {
                                    try { clientSocket.close() } catch (e: Exception) {}
                                }
                            } catch (e: Exception) {
                                Log.e("MyVpnService", "Error in client proxy session", e)
                            }
                        }.start()
                    }
                } catch (e: Exception) {
                    Log.i("MyVpnService", "Proxy loop finished: ${e.message}")
                }
            }, "MyVpnLocalProxy").apply { start() }
        } catch (e: Exception) {
            Log.e("MyVpnService", "Failed to start local proxy", e)
        }
    }

    private fun stopLocalProxy() {
        try {
            proxyServerSocket?.close()
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error closing server socket", e)
        }
        proxyServerSocket = null
        proxyThread?.interrupt()
        proxyThread = null
    }

    private fun readLine(input: java.io.InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (out.size() == 0) return null else break
            }
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                out.write(b)
            }
        }
        return out.toString("UTF-8")
    }

    private fun handleSocks5(clientSocket: Socket, firstByte: Int) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            val nMethods = input.read()
            if (nMethods == -1) return
            val methods = ByteArray(nMethods)
            input.read(methods)
            
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()
            
            val ver = input.read()
            val cmd = input.read()
            val rsv = input.read()
            val atyp = input.read()
            
            if (ver != 0x05 || cmd != 0x01) {
                return
            }
            
            var destHost = ""
            if (atyp == 0x01) {
                val ipv4 = ByteArray(4)
                input.read(ipv4)
                destHost = java.net.InetAddress.getByAddress(ipv4).hostAddress
            } else if (atyp == 0x03) {
                val len = input.read()
                if (len == -1) return
                val domainBytes = ByteArray(len)
                var readBytes = 0
                while (readBytes < len) {
                    val n = input.read(domainBytes, readBytes, len - readBytes)
                    if (n == -1) return
                    readBytes += n
                }
                destHost = String(domainBytes, Charsets.UTF_8)
            } else if (atyp == 0x04) {
                val ipv6 = ByteArray(16)
                input.read(ipv6)
                destHost = java.net.InetAddress.getByAddress(ipv6).hostAddress
            } else {
                return
            }
            
            val p0 = input.read()
            val p1 = input.read()
            if (p0 == -1 || p1 == -1) return
            val destPort = (p0 shl 8) or p1
            
            val remoteTunnel = connectToRemote(destHost, destPort)
            if (remoteTunnel == null) {
                output.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                output.flush()
                return
            }
            
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0))
            output.flush()
            
            bridgeConnections(clientSocket, remoteTunnel)
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error handshaking SOCKS5", e)
        }
    }

    private fun handleHttpRequest(clientSocket: Socket, firstLine: String) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()
            
            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                try { clientSocket.close() } catch (e: Exception) {}
                return
            }
            
            val method = parts[0].uppercase()
            val urlOrHost = parts[1]
            
            var destHost = ""
            var destPort = 80
            
            if (method == "CONNECT") {
                val hostPortParts = urlOrHost.split(":")
                destHost = hostPortParts[0]
                destPort = if (hostPortParts.size > 1) hostPortParts[1].toIntOrNull() ?: 443 else 443
                
                while (true) {
                    val l = readLine(input)
                    if (l.isNullOrEmpty()) break
                }
                
                val remoteTunnel = connectToRemote(destHost, destPort)
                if (remoteTunnel == null) {
                    output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                    try { clientSocket.close() } catch (e: Exception) {}
                    return
                }
                
                output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                
                bridgeConnections(clientSocket, remoteTunnel)
            } else {
                val url = urlOrHost
                if (url.startsWith("http://", ignoreCase = true)) {
                    val temp = url.substring(7)
                    val slashIdx = temp.indexOf('/')
                    val hostPort = if (slashIdx == -1) temp else temp.substring(0, slashIdx)
                    val hostPortParts = hostPort.split(":")
                    destHost = hostPortParts[0]
                    destPort = if (hostPortParts.size > 1) hostPortParts[1].toIntOrNull() ?: 80 else 80
                }
                
                val headers = ArrayList<String>()
                while (true) {
                    val l = readLine(input)
                    if (l.isNullOrEmpty()) break
                    headers.add(l)
                    if (destHost.isEmpty() && l.lowercase().startsWith("host:")) {
                        val hostVal = l.substring(5).trim()
                        val hostPortParts = hostVal.split(":")
                        destHost = hostPortParts[0]
                        destPort = if (hostPortParts.size > 1) hostPortParts[1].toIntOrNull() ?: 80 else 80
                    }
                }
                
                if (destHost.isEmpty()) {
                    try { clientSocket.close() } catch (e: Exception) {}
                    return
                }
                
                val remoteTunnel = connectToRemote(destHost, destPort)
                if (remoteTunnel == null) {
                    output.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                    try { clientSocket.close() } catch (e: Exception) {}
                    return
                }
                
                val requestBuilder = StringBuilder()
                requestBuilder.append(firstLine).append("\r\n")
                for (h in headers) {
                    requestBuilder.append(h).append("\r\n")
                }
                requestBuilder.append("\r\n")
                
                val requestBytes = requestBuilder.toString().toByteArray(Charsets.UTF_8)
                remoteTunnel.send(requestBytes, requestBytes.size)
                
                bridgeConnections(clientSocket, remoteTunnel)
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error handshaking HTTP Connect", e)
        }
    }

    private fun getTrustAllSocketFactory(): SSLSocketFactory {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            javax.net.ssl.SSLSocketFactory.getDefault() as SSLSocketFactory
        }
    }

    private fun connectToRemote(destHost: String, destPort: Int): RemoteTunnel? {
        val configUrl = activeConfigUrl
        val config = if (!configUrl.isNullOrEmpty()) VlessConfig.parse(configUrl) else null
        
        if (config == null) {
            return try {
                val socket = Socket()
                protect(socket)
                socket.connect(InetSocketAddress(destHost, destPort), 10000)
                DirectTcpTunnel(socket)
            } catch (e: Exception) {
                Log.e("MyVpnService", "Direct connection failed to $destHost:$destPort", e)
                null
            }
        }
        
        val targetAddr = try {
            val isIpv4 = config.address.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            val isIpv6 = config.address.contains(":") && !config.address.contains("[a-zA-Z]".toRegex())
            if (isIpv4 || isIpv6) {
                config.address
            } else {
                val resolved = resolveDomainViaTcpDns(config.address)
                if (resolved != null) {
                    resolved
                } else {
                    Log.w("MyVpnService", "Could not resolve server domain via TCP DNS, falling back to system resolver")
                    config.address
                }
            }
        } catch (e: Exception) {
            config.address
        }
        
        return try {
            val socket = Socket()
            protect(socket)
            socket.connect(InetSocketAddress(targetAddr, config.port), 12000)
            
            val activeSocket = if (config.security == "tls" || config.security == "xtls" || config.port == 443) {
                val sslSocketFactory = getTrustAllSocketFactory()
                val sslSocket = sslSocketFactory.createSocket(socket, config.sni ?: config.address, config.port, true) as javax.net.ssl.SSLSocket
                sslSocket.startHandshake()
                sslSocket
            } else {
                socket
            }
            
            if (config.type == "ws") {
                val wPath = config.path ?: "/"
                val wHost = config.host ?: config.sni ?: config.address
                val request = "GET $wPath HTTP/1.1\r\n" +
                              "Host: $wHost\r\n" +
                              "Upgrade: websocket\r\n" +
                              "Connection: Upgrade\r\n" +
                              "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                              "Sec-WebSocket-Version: 13\r\n\r\n"
                
                val output = activeSocket.getOutputStream()
                output.write(request.toByteArray(Charsets.UTF_8))
                output.flush()
                
                val headerBytes = ByteArrayOutputStream()
                var state = 0
                val input = activeSocket.getInputStream()
                while (true) {
                    val b = input.read()
                    if (b == -1) break
                    headerBytes.write(b)
                    if (state == 0 && b == '\r'.code) state = 1
                    else if (state == 1 && b == '\n'.code) state = 2
                    else if (state == 2 && b == '\r'.code) state = 3
                    else if (state == 3 && b == '\n'.code) {
                        break
                    } else {
                        state = 0
                    }
                }
                
                val headerStr = headerBytes.toString("UTF-8")
                if (!headerStr.contains("101")) {
                    Log.e("MyVpnService", "WS Handshake failed: $headerStr")
                    try { activeSocket.close() } catch (e: Exception) {}
                    return null
                }
                
                VlessWsTlsTunnel(activeSocket, config, destHost, destPort)
            } else {
                VlessTcpTlsTunnel(activeSocket, config, destHost, destPort)
            }
        } catch (e: Exception) {
            Log.e("MyVpnService", "VLESS connection failed to $destHost:$destPort", e)
            null
        }
    }

    private fun bridgeConnections(clientSocket: Socket, remoteTunnel: RemoteTunnel) {
        val clientInput = clientSocket.getInputStream()
        val clientOutput = clientSocket.getOutputStream()
        
        val t1 = Thread {
            try {
                val buf = ByteArray(8192)
                while (!Thread.currentThread().isInterrupted) {
                    val n = clientInput.read(buf)
                    if (n == -1) break
                    remoteTunnel.send(buf, n)
                }
            } catch (e: Exception) {
            } finally {
                remoteTunnel.close()
                try { clientSocket.close() } catch (e: Exception) {}
            }
        }
        
        val t2 = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val data = remoteTunnel.receive() ?: break
                    clientOutput.write(data)
                    clientOutput.flush()
                }
            } catch (e: Exception) {
            } finally {
                remoteTunnel.close()
                try { clientSocket.close() } catch (e: Exception) {}
            }
        }
        
        t1.start()
        t2.start()
    }

    interface RemoteTunnel {
        fun send(data: ByteArray, len: Int)
        fun receive(): ByteArray?
        fun close()
    }

    class DirectTcpTunnel(val socket: Socket) : RemoteTunnel {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        
        override fun send(data: ByteArray, len: Int) {
            output.write(data, 0, len)
            output.flush()
        }
        
        override fun receive(): ByteArray? {
            val buf = ByteArray(8192)
            val n = input.read(buf)
            if (n == -1) return null
            return buf.copyOfRange(0, n)
        }
        
        override fun close() {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    class VlessWsTlsTunnel(
        val socket: Socket,
        val config: VlessConfig,
        val destHost: String,
        val destPort: Int
    ) : RemoteTunnel {
        private val input = socket.getInputStream()
        private val output = socket.getOutputStream()
        private var isFirstResponse = true
        
        init {
            val uuidBytes = parseUuidBytes(config.uuid)
            val vlessHeader = ByteArrayOutputStream()
            vlessHeader.write(0x00) // Version
            vlessHeader.write(uuidBytes) // UUID
            vlessHeader.write(0x00) // Addon length
            vlessHeader.write(0x01) // Command CONNECT
            vlessHeader.write((destPort shr 8) and 0xFF)
            vlessHeader.write(destPort and 0xFF)
            val isIpv4 = destHost.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            val isIpv6 = destHost.contains(":") && !destHost.contains("[a-zA-Z]".toRegex())
            
            if (isIpv4) {
                vlessHeader.write(0x01) // Address type IPv4
                val ipBytes = destHost.split(".").map { it.toInt().toByte() }.toByteArray()
                vlessHeader.write(ipBytes)
            } else if (isIpv6) {
                vlessHeader.write(0x03) // Address type IPv6
                try {
                    val addrBytes = java.net.InetAddress.getByName(destHost).address
                    if (addrBytes.size == 16) {
                        vlessHeader.write(addrBytes)
                    } else {
                        vlessHeader.write(0x02)
                        val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                        vlessHeader.write(domainBytes.size)
                        vlessHeader.write(domainBytes)
                    }
                } catch (e: Exception) {
                    vlessHeader.write(0x02)
                    val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                    vlessHeader.write(domainBytes.size)
                    vlessHeader.write(domainBytes)
                }
            } else {
                vlessHeader.write(0x02) // Address type Domain
                val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                vlessHeader.write(domainBytes.size)
                vlessHeader.write(domainBytes)
            }
            
            writeWsFrame(output, vlessHeader.toByteArray(), 0, vlessHeader.size())
        }
        
        override fun send(data: ByteArray, len: Int) {
            writeWsFrame(output, data, 0, len)
        }
        
        override fun receive(): ByteArray? {
            while (true) {
                var payload = readWsFrame(input) ?: return null
                if (isFirstResponse) {
                    isFirstResponse = false
                    val addonLen = if (payload.size >= 2) payload[1].toInt() and 0xFF else 0
                    val headerSize = 2 + addonLen
                    if (payload.size > headerSize) {
                        return payload.copyOfRange(headerSize, payload.size)
                    } else {
                        continue
                    }
                }
                return payload
            }
        }
        
        override fun close() {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    class VlessTcpTlsTunnel(
        val socket: Socket,
        val config: VlessConfig,
        val destHost: String,
        val destPort: Int
    ) : RemoteTunnel {
        private val input = socket.getInputStream()
        private val output = socket.getOutputStream()
        private var isFirstResponse = true
        
        init {
            val uuidBytes = parseUuidBytes(config.uuid)
            val vlessHeader = ByteArrayOutputStream()
            vlessHeader.write(0x00) // Version
            vlessHeader.write(uuidBytes) // UUID
            vlessHeader.write(0x00) // Addon length
            vlessHeader.write(0x01) // Command CONNECT
            vlessHeader.write((destPort shr 8) and 0xFF)
            vlessHeader.write(destPort and 0xFF)
            val isIpv4 = destHost.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$"""))
            val isIpv6 = destHost.contains(":") && !destHost.contains("[a-zA-Z]".toRegex())
            
            if (isIpv4) {
                vlessHeader.write(0x01) // Address type IPv4
                val ipBytes = destHost.split(".").map { it.toInt().toByte() }.toByteArray()
                vlessHeader.write(ipBytes)
            } else if (isIpv6) {
                vlessHeader.write(0x03) // Address type IPv6
                try {
                    val addrBytes = java.net.InetAddress.getByName(destHost).address
                    if (addrBytes.size == 16) {
                        vlessHeader.write(addrBytes)
                    } else {
                        vlessHeader.write(0x02)
                        val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                        vlessHeader.write(domainBytes.size)
                        vlessHeader.write(domainBytes)
                    }
                } catch (e: Exception) {
                    vlessHeader.write(0x02)
                    val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                    vlessHeader.write(domainBytes.size)
                    vlessHeader.write(domainBytes)
                }
            } else {
                vlessHeader.write(0x02) // Address type Domain
                val domainBytes = destHost.toByteArray(Charsets.UTF_8)
                vlessHeader.write(domainBytes.size)
                vlessHeader.write(domainBytes)
            }
            
            val headerBytes = vlessHeader.toByteArray()
            output.write(headerBytes)
            output.flush()
        }
        
        override fun send(data: ByteArray, len: Int) {
            output.write(data, 0, len)
            output.flush()
        }
        
        override fun receive(): ByteArray? {
            val buf = ByteArray(8192)
            if (isFirstResponse) {
                val b0 = input.read()
                val b1 = input.read()
                if (b0 == -1 || b1 == -1) return null
                val addonLen = b1 and 0xFF
                if (addonLen > 0) {
                    val addons = ByteArray(addonLen)
                    var readAddons = 0
                    while (readAddons < addonLen) {
                        val n = input.read(addons, readAddons, addonLen - readAddons)
                        if (n == -1) return null
                        readAddons += n
                    }
                }
                isFirstResponse = false
            }
            val n = input.read(buf)
            if (n == -1) return null
            return buf.copyOfRange(0, n)
        }
        
        override fun close() {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun resolveDomainViaTcpDns(domain: String): String? {
        val dnsServers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "208.67.222.222")
        val dnsPayload = buildDnsQueryBytes(domain) ?: return null
        
        // 1. Try DNS over HTTPS first (absolutely censorship resistant, runs on port 443)
        for (serverIp in dnsServers) {
            try {
                val resp = resolveDnsOverHttps(dnsPayload, serverIp)
                if (resp != null && resp.size > 12) {
                    val resolvedIp = extractDnsResponseFirstIp(resp)
                    if (!resolvedIp.isNullOrEmpty()) {
                        Log.d("MyVpnService", "Resolved server domain $domain via DNS-over-HTTPS as $resolvedIp")
                        return resolvedIp
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        
        // 2. Fallback to TCP DNS
        for (serverIp in dnsServers) {
            try {
                val resp = resolveDnsOverTcp(dnsPayload, serverIp)
                if (resp != null && resp.size > 12) {
                    val resolvedIp = extractDnsResponseFirstIp(resp)
                    if (!resolvedIp.isNullOrEmpty()) {
                        Log.d("MyVpnService", "Resolved server domain $domain via DNS-over-TCP as $resolvedIp")
                        return resolvedIp
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return null
    }

    private fun buildDnsQueryBytes(domain: String): ByteArray? {
        try {
            val out = java.io.ByteArrayOutputStream()
            // Tx ID
            out.write(0x12)
            out.write(0x34)
            // Flags: standard query, recursion desired
            out.write(0x01)
            out.write(0x00)
            // Questions count: 1
            out.write(0x00)
            out.write(0x01)
            // Answer, Authority, Additional counts: 0
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            out.write(0x00)
            
            // Domain sections
            val labels = domain.split(".")
            for (label in labels) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                if (bytes.size > 63) return null
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0x00) // Null terminator
            
            // Type A (1), Class IN (1)
            out.write(0x00)
            out.write(0x01)
            out.write(0x00)
            out.write(0x01)
            
            return out.toByteArray()
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractDnsResponseFirstIp(responseBytes: ByteArray): String? {
        try {
            if (responseBytes.size < 12) return null
            val qdCount = ((responseBytes[4].toInt() and 0xFF) shl 8) or (responseBytes[5].toInt() and 0xFF)
            val anCount = ((responseBytes[6].toInt() and 0xFF) shl 8) or (responseBytes[7].toInt() and 0xFF)
            
            var pos = 12
            // Skip Questions section
            for (i in 0 until qdCount) {
                pos = skipDnsName(responseBytes, pos)
                pos += 4 // Type (2 bytes) + Class (2 bytes)
            }
            
            // Parse Answers section
            for (i in 0 until anCount) {
                if (pos >= responseBytes.size) break
                pos = skipDnsName(responseBytes, pos)
                if (pos + 10 > responseBytes.size) break
                
                val type = ((responseBytes[pos].toInt() and 0xFF) shl 8) or (responseBytes[pos + 1].toInt() and 0xFF)
                val rdLength = ((responseBytes[pos + 8].toInt() and 0xFF) shl 8) or (responseBytes[pos + 9].toInt() and 0xFF)
                pos += 10
                
                if (pos + rdLength > responseBytes.size) break
                
                if (type == 1 && rdLength == 4) { // IPv4 A record
                    val ip0 = responseBytes[pos].toInt() and 0xFF
                    val ip1 = responseBytes[pos + 1].toInt() and 0xFF
                    val ip2 = responseBytes[pos + 2].toInt() and 0xFF
                    val ip3 = responseBytes[pos + 3].toInt() and 0xFF
                    return "$ip0.$ip1.$ip2.$ip3"
                }
                pos += rdLength
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    companion object {
        const val ACTION_DISCONNECT = "com.example.service.DISCONNECT"
        
        private val _vpnState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val vpnState: StateFlow<ConnectionState> = _vpnState

        fun startService(context: Context, configUrl: String) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                putExtra("EXTRA_CONFIG_URL", configUrl)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }

        private fun writeWsFrame(output: OutputStream, payload: ByteArray, offset: Int, len: Int) {
            output.write(0x82)
            val maskKey = byteArrayOf(0x11, 0x22, 0x33, 0x44)
            if (len <= 125) {
                output.write(len or 0x80)
            } else if (len <= 65535) {
                output.write(126 or 0x80)
                output.write((len shr 8) and 0xFF)
                output.write(len and 0xFF)
            } else {
                output.write(127 or 0x80)
                // Write 8-byte length (0, 0, 0, 0, then 4 bytes of Int length)
                output.write(0)
                output.write(0)
                output.write(0)
                output.write(0)
                output.write((len shr 24) and 0xFF)
                output.write((len shr 16) and 0xFF)
                output.write((len shr 8) and 0xFF)
                output.write(len and 0xFF)
            }
            output.write(maskKey)
            val masked = ByteArray(len)
            for (i in 0 until len) {
                masked[i] = (payload[offset + i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
            output.write(masked)
            output.flush()
        }

        private fun readWsFrame(input: InputStream): ByteArray? {
            val b0 = input.read()
            if (b0 == -1) return null
            
            val b1 = input.read()
            if (b1 == -1) return null
            val masked = (b1 and 0x80) != 0
            var payloadLen = b1 and 0x7F
            
            if (payloadLen == 126) {
                val lenHi = input.read()
                val lenLo = input.read()
                if (lenHi == -1 || lenLo == -1) return null
                payloadLen = (lenHi shl 8) or lenLo
            } else if (payloadLen == 127) {
                var lenVal = 0L
                for (i in 0 until 8) {
                    val b = input.read()
                    if (b == -1) return null
                    if (i >= 4) {
                        lenVal = (lenVal shl 8) or b.toLong()
                    }
                }
                payloadLen = lenVal.toInt()
            }
            
            val maskKey = if (masked) {
                val keys = ByteArray(4)
                var readBytes = 0
                while (readBytes < 4) {
                    val n = input.read(keys, readBytes, 4 - readBytes)
                    if (n == -1) return null
                    readBytes += n
                }
                keys
            } else null
            
            val payload = ByteArray(payloadLen)
            var readBytes = 0
            while (readBytes < payloadLen) {
                val n = input.read(payload, readBytes, payloadLen - readBytes)
                if (n == -1) return null
                readBytes += n
            }
            
            if (maskKey != null) {
                for (i in 0 until payloadLen) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }
            return payload
        }

        private fun parseUuidBytes(uuidStr: String): ByteArray {
            val clean = uuidStr.replace("-", "")
            if (clean.length != 32) return ByteArray(16)
            val data = ByteArray(16)
            for (i in 0 until 16) {
                val high = Character.digit(clean[i * 2], 16)
                val low = Character.digit(clean[i * 2 + 1], 16)
                data[i] = ((high shl 4) or low).toByte()
            }
            return data
        }
    }
}

class TransparentTcpSession(
    val srcPort: Int,
    val destIpStr: String,
    val destPort: Int,
    val clientIp: ByteArray,
    val destIp: ByteArray,
    val outputStream: java.io.FileOutputStream,
    val vpnService: MyVpnService
) {
    var clientSeq: Long = 0L
    var serverSeq: Long = kotlin.random.Random.nextLong(1000, 1000000)
    var state: SessionState = SessionState.CLOSED
    var remoteTunnel: MyVpnService.RemoteTunnel? = null
    var outboundJob: Thread? = null
    
    enum class SessionState {
        CLOSED,
        SYN_RECEIVED,
        ESTABLISHED,
        FIN_WAIT
    }
    
    fun close() {
        state = SessionState.CLOSED
        outboundJob?.interrupt()
        try { remoteTunnel?.close() } catch (e: Exception) {}
        remoteTunnel = null
    }
}

