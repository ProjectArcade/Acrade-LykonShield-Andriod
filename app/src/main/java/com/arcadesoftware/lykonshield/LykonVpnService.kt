package com.arcadesoftware.lykonshield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.arcadesoftware.lykonshield.Logger as Log
import androidx.core.app.NotificationCompat
import com.arcadesoftware.lykon.AdblockEngine
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Production-grade VPN service optimized for maximum battery life and network speed.
 * Implements the "Selective Routing" strategy (similar to Blokada):
 *
 * 1. Routes ONLY the local DNS IP (10.0.0.1/32) and known DoH/DoT provider IPs.
 * 2. All other device traffic (web, video, gaming) bypasses the VPN completely at the OS level.
 * 3. Intercepts DNS queries on port 53 and filters them via AdblockEngine.
 * 4. Intercepts and blocks DNS-over-TLS (port 853) and DNS-over-HTTPS (port 443) to DNS provider IPs using TCP RST.
 */
class LykonVpnService : VpnService() {

    companion object {
        private const val TAG = "LykonVpnService"
        const val ACTION_START = "com.arcadesoftware.lykonshield.START"
        const val ACTION_STOP = "com.arcadesoftware.lykonshield.STOP"

        const val VPN_ADDRESS = "10.0.0.2"
        const val DNS_SERVER = "10.0.0.1"
        private const val VPN_MTU = 1500

        private const val PROTO_TCP = 6
        private const val PROTO_UDP = 17

        private const val PORT_DNS = 53
        private const val PORT_DOT = 853
        private const val PORT_HTTPS = 443

        // Known DoH/DoT provider IPs to intercept and block
        private val DOH_PROVIDER_IPS = setOf(
            "8.8.8.8", "8.8.4.4",                     // Google DNS
            "1.1.1.1", "1.0.0.1",                     // Cloudflare
            "9.9.9.9", "149.112.112.112",              // Quad9
            "208.67.222.222", "208.67.220.220",        // OpenDNS
            "94.140.14.14", "94.140.15.15",            // AdGuard DNS
            "185.228.168.168", "185.228.169.168",      // CleanBrowsing
            "76.76.2.0", "76.76.10.0",                 // ControlD
            "104.16.248.249", "104.16.249.249",        // Cloudflare DoH CDN
            "76.223.122.150", "13.107.42.14"           // NextDNS, Microsoft
        )
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile private var isRunning = false
    private var dnsExecutor: ExecutorService? = null
    private val outputLock = Any()

    override fun onCreate() {
        super.onCreate()
        dnsExecutor = Executors.newFixedThreadPool(4)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopVpn()
        dnsExecutor?.shutdownNow()
        super.onDestroy()
    }

    @Synchronized
    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "Starting Lykon Shield Selective VPN...")

        AdblockEngine.init(applicationContext)
        ShieldStatsManager.init(applicationContext)
        FilterListUpdater.checkAndUpdate(applicationContext)

        try {
            startForegroundNotification()
            logSystemDnsServers()

            val builder = Builder()
                .setSession("Lykon Shield VPN")
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_SERVER)
                .setMtu(VPN_MTU)
                .setBlocking(true)

            // Route our local virtual DNS server through the TUN interface
            builder.addRoute(DNS_SERVER, 32)

            // Intercept known DoT/DoH provider IPs so we can block ports 853/443
            for (ip in DOH_PROVIDER_IPS) {
                try {
                    builder.addRoute(ip, 32)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add route for IP: $ip", e)
                }
            }

            // Exclude apps selected by the user (bypass VPN entirely)
            val prefs = getSharedPreferences("lykon_shield_prefs", MODE_PRIVATE)
            val excludedApps = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()
            for (app in excludedApps) {
                try {
                    builder.addDisallowedApplication(app)
                    Log.d(TAG, "Bypassed app from VPN: $app")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to exclude app: $app", e)
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                isRunning = false
                return
            }

            vpnThread = Thread({ runVpnLoop() }, "LykonVPN-Loop")
            vpnThread?.start()

            Log.d(TAG, "Lykon Shield VPN established — selective routing active")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            isRunning = false
        }
    }

    @Synchronized
    private fun stopVpn() {
        if (!isRunning) return
        isRunning = false
        Log.d(TAG, "Stopping Lykon Shield VPN...")

        vpnInterface?.close()
        vpnInterface = null
        vpnThread?.interrupt()
        vpnThread = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun runVpnLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val packetBuffer = ByteArray(16384)

        try {
            while (isRunning) {
                val readBytes = inputStream.read(packetBuffer)
                if (readBytes <= 0) {
                    Thread.sleep(2)
                    continue
                }

                val versionAndIhl = packetBuffer[0].toInt() and 0xFF
                val version = versionAndIhl shr 4
                if (version != 4 || readBytes < 20) continue // IPv4 only

                val ihl = versionAndIhl and 0x0F
                val ipHeaderLen = ihl * 4
                if (readBytes < ipHeaderLen + 4) continue

                val protocol = packetBuffer[9].toInt() and 0xFF
                val destIpStr = formatIp(packetBuffer, 16)

                when (protocol) {
                    PROTO_UDP -> {
                        Log.v(TAG, "Incoming UDP to $destIpStr")
                        handleUdpPacket(packetBuffer, readBytes, ipHeaderLen, destIpStr, outputStream)
                    }
                    PROTO_TCP -> {
                        Log.v(TAG, "Incoming TCP to $destIpStr")
                        handleTcpPacket(packetBuffer, readBytes, ipHeaderLen, destIpStr, outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Error in VPN Loop", e)
        } finally {
            inputStream.close()
            outputStream.close()
        }
    }

    private fun handleUdpPacket(
        packet: ByteArray, len: Int, ipHeaderLen: Int,
        destIpStr: String, outputStream: FileOutputStream
    ) {
        if (len < ipHeaderLen + 8) return
        val buf = ByteBuffer.wrap(packet, 0, len)
        buf.position(ipHeaderLen)
        val srcPort = buf.short.toInt() and 0xFFFF
        val destPort = buf.short.toInt() and 0xFFFF

        if (destPort == PORT_DNS) {
            Log.d(TAG, "Intercepted UDP DNS query to $destIpStr:$destPort")
            val requestCopy = packet.copyOf(len)
            dnsExecutor?.execute {
                handleDnsQuery(requestCopy, len, ipHeaderLen, 8, srcPort, destIpStr, outputStream)
            }
        } else {
            Log.v(TAG, "Ignoring UDP packet to $destIpStr:$destPort")
        }
    }

    private fun handleTcpPacket(
        packet: ByteArray, len: Int, ipHeaderLen: Int,
        destIpStr: String, outputStream: FileOutputStream
    ) {
        if (len < ipHeaderLen + 20) return
        val buf = ByteBuffer.wrap(packet, 0, len)
        buf.position(ipHeaderLen)
        val srcPort = buf.short.toInt() and 0xFFFF
        val destPort = buf.short.toInt() and 0xFFFF

        // Intercept and reject any DNS-over-TLS (853) or DNS-over-HTTPS (443 to known DoH IPs)
        // Also intercept standard TCP DNS (53) and send RST to force fallback to UDP 53
        if (destPort == PORT_DOT || (destPort == PORT_HTTPS && destIpStr in DOH_PROVIDER_IPS) || destPort == PORT_DNS) {
            val rst = buildTcpRst(packet, len, ipHeaderLen)
            if (rst != null) {
                writeToTun(outputStream, rst)
                Log.d(TAG, "Blocked Secure DNS connection (TCP RST) to $destIpStr:$destPort")
            }
        }
    }

    private fun handleDnsQuery(
        requestPacket: ByteArray, requestLen: Int,
        ipHeaderLen: Int, udpHeaderLen: Int,
        srcPort: Int, destIpStr: String,
        outputStream: FileOutputStream
    ) {
        if (!AdblockEngine.isReady()) {
            AdblockEngine.awaitReady(1000)
        }

        val buffer = ByteBuffer.wrap(requestPacket, 0, requestLen)
        val domain = DnsPacketParser.parseQueryDomain(buffer, ipHeaderLen, udpHeaderLen)
        if (domain == null) {
            Log.w(TAG, "Failed to parse DNS query domain")
            return
        }

        val packageName = getAppPackageForConnection(srcPort, destIpStr, PROTO_UDP) ?: "system"

        val shouldBlock = AdblockEngine.shouldBlockDomain(domain)
        Log.d(TAG, "DNS Query from $packageName: $domain -> shouldBlock = $shouldBlock")

        if (shouldBlock) {
            val queryType = DnsPacketParser.getQueryType(buffer, ipHeaderLen, udpHeaderLen)
            val response = when (queryType) {
                DnsPacketParser.DNS_TYPE_A ->
                    DnsPacketParser.buildBlockedAResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen)
                DnsPacketParser.DNS_TYPE_AAAA ->
                    DnsPacketParser.buildBlockedAAAAResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen)
                else ->
                    DnsPacketParser.buildNxDomainResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen)
            } ?: DnsPacketParser.buildNxDomainResponse(requestPacket, requestLen, ipHeaderLen, udpHeaderLen)

            writeToTun(outputStream, response)
            ShieldStatsManager.recordBlockWithApp(applicationContext, domain, packageName)
        } else {
            forwardDnsQuery(requestPacket, ipHeaderLen, udpHeaderLen, outputStream)
            ShieldStatsManager.recordTraffic(applicationContext, domain, packageName, isBlocked = false)
        }
    }

    private fun forwardDnsQuery(
        requestPacket: ByteArray, ipHeaderLen: Int,
        udpHeaderLen: Int, outputStream: FileOutputStream
    ) {
        val dnsStart = ipHeaderLen + udpHeaderLen
        val dnsLen = requestPacket.size - dnsStart
        val dnsServers = getUpstreamDnsServers()

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null)
            protect(socket)
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = 1500

            val sendPacket = DatagramPacket(requestPacket, dnsStart, dnsLen)
            val receiveBuf = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            var received = false

            for (server in dnsServers) {
                try {
                    sendPacket.address = server
                    sendPacket.port = PORT_DNS
                    socket.send(sendPacket)
                    socket.receive(receivePacket)
                    received = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "DNS query to $server failed: ${e.message}")
                }
            }

            if (!received) return

            val responseDnsLen = receivePacket.length
            val responseTotalLen = 20 + 8 + responseDnsLen
            val response = ByteArray(responseTotalLen)
            val responseBuf = ByteBuffer.wrap(response)

            // IP Header
            response[0] = 0x45.toByte()
            response[1] = 0x00.toByte()
            responseBuf.putShort(2, responseTotalLen.toShort())
            responseBuf.putShort(4, 0)
            responseBuf.putShort(6, 0x4000.toShort())
            response[8] = 64.toByte()
            response[9] = PROTO_UDP.toByte()
            responseBuf.putShort(10, 0)

            // Swap IPs
            System.arraycopy(requestPacket, 16, response, 12, 4)
            System.arraycopy(requestPacket, 12, response, 16, 4)

            val ipChecksum = calculateChecksum(response, 0, 20)
            responseBuf.putShort(10, ipChecksum)

            // UDP Header
            val origSrcPort = ByteBuffer.wrap(requestPacket).getShort(ipHeaderLen)
            responseBuf.putShort(20, PORT_DNS.toShort())
            responseBuf.putShort(22, origSrcPort)
            responseBuf.putShort(24, (8 + responseDnsLen).toShort())
            responseBuf.putShort(26, 0) // Disable UDP checksum

            // DNS payload
            System.arraycopy(receivePacket.data, receivePacket.offset, response, 28, responseDnsLen)

            writeToTun(outputStream, response)
        } catch (e: Exception) {
            Log.e(TAG, "DNS forwarding error", e)
        } finally {
            socket?.close()
        }
    }

    private fun buildTcpRst(packet: ByteArray, len: Int, ipHeaderLen: Int): ByteArray? {
        if (len < ipHeaderLen + 20) return null
        val buf = ByteBuffer.wrap(packet, 0, len)

        buf.position(ipHeaderLen)
        val srcPort = buf.short
        val dstPort = buf.short
        val seqNum = buf.int.toLong() and 0xFFFFFFFFL
        val ackNum = buf.int.toLong() and 0xFFFFFFFFL
        val dataOffsetByte = buf.get().toInt() and 0xFF
        val tcpHeaderLen = (dataOffsetByte shr 4) * 4
        val flags = buf.get().toInt() and 0x3F

        val isSyn = (flags and 0x02) != 0
        val payloadLen = len - ipHeaderLen - tcpHeaderLen
        val responseAck = seqNum + (if (isSyn) 1 else payloadLen.toLong()).coerceAtLeast(1)

        val rstLen = 40
        val rst = ByteArray(rstLen)
        val rstBuf = ByteBuffer.wrap(rst)

        // IP header
        rst[0] = 0x45.toByte()
        rst[1] = 0x00.toByte()
        rstBuf.putShort(2, rstLen.toShort())
        rstBuf.putShort(4, 0)
        rstBuf.putShort(6, 0x4000.toShort())
        rst[8] = 64.toByte()
        rst[9] = PROTO_TCP.toByte()
        rstBuf.putShort(10, 0)

        // Swap IPs
        System.arraycopy(packet, 16, rst, 12, 4)
        System.arraycopy(packet, 12, rst, 16, 4)

        val ipChk = calculateChecksum(rst, 0, 20)
        rstBuf.putShort(10, ipChk)

        // TCP header
        rstBuf.putShort(20, dstPort)    // Src port = original dst
        rstBuf.putShort(22, srcPort)    // Dst port = original src
        rstBuf.putInt(24, 0)            // Seq = 0
        rstBuf.putInt(28, responseAck.toInt()) // Ack = incoming seq + 1
        rst[32] = 0x50.toByte()         // Data offset = 5
        rst[33] = 0x14.toByte()         // Flags = RST + ACK
        rstBuf.putShort(34, 0)
        rstBuf.putShort(36, 0)
        rstBuf.putShort(38, 0)

        val tcpChk = calculateTcpChecksum(rst, 12, 16, 20, 20)
        rstBuf.putShort(36, tcpChk)

        return rst
    }

    private fun writeToTun(outputStream: FileOutputStream, data: ByteArray) {
        synchronized(outputLock) {
            try {
                outputStream.write(data)
                outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to TUN", e)
            }
        }
    }

    private fun formatIp(packet: ByteArray, offset: Int): String {
        return "${packet[offset].toInt() and 0xFF}." +
                "${packet[offset + 1].toInt() and 0xFF}." +
                "${packet[offset + 2].toInt() and 0xFF}." +
                "${packet[offset + 3].toInt() and 0xFF}"
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        val end = offset + length - 1
        while (i < end) {
            val high = buf[i].toInt() and 0xFF
            val low = buf[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i == end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()).toShort()
    }

    private fun calculateTcpChecksum(
        buf: ByteArray, srcIpOffset: Int, dstIpOffset: Int,
        tcpOffset: Int, tcpLen: Int
    ): Short {
        var sum = 0L
        for (i in 0..1) {
            val high = buf[srcIpOffset + i * 2].toInt() and 0xFF
            val low = buf[srcIpOffset + i * 2 + 1].toInt() and 0xFF
            sum += (high shl 8) or low
        }
        for (i in 0..1) {
            val high = buf[dstIpOffset + i * 2].toInt() and 0xFF
            val low = buf[dstIpOffset + i * 2 + 1].toInt() and 0xFF
            sum += (high shl 8) or low
        }
        sum += PROTO_TCP.toLong()
        sum += tcpLen.toLong()

        var i = tcpOffset
        val end = tcpOffset + tcpLen - 1
        while (i < end) {
            val high = buf[i].toInt() and 0xFF
            val low = buf[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i == end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()).toShort()
    }

    private fun logSystemDnsServers() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val linkProps = cm.getLinkProperties(activeNetwork)
            val dnsServers = linkProps?.dnsServers
            if (dnsServers != null) {
                Log.d(TAG, "System DNS servers: ${dnsServers.joinToString { it.hostAddress ?: "?" }}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query system DNS servers", e)
        }
    }

    private fun getUpstreamDnsServers(): List<InetAddress> {
        val servers = mutableListOf<InetAddress>()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    val linkProperties = cm.getLinkProperties(network)
                    val dns = linkProperties?.dnsServers?.filterIsInstance<java.net.Inet4Address>()
                    if (dns != null) {
                        servers.addAll(dns)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get system DNS servers from physical networks", e)
        }

        val filteredServers = servers.filter {
            val addr = it.hostAddress
            addr != null && addr != "10.0.0.1" && addr != "127.0.0.1"
        }

        if (filteredServers.isEmpty()) {
            try {
                return listOf(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("1.1.1.1")
                )
            } catch (_: Exception) {}
        }
        return filteredServers
    }

    private val uidPackageCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private fun getAppPackageForConnection(srcPort: Int, destIpStr: String, protocol: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val remote = InetSocketAddress(InetAddress.getByName(destIpStr), PORT_DNS)
            val localIps = listOf("10.0.0.2", "0.0.0.0", "::")
            for (ip in localIps) {
                try {
                    val local = InetSocketAddress(InetAddress.getByName(ip), srcPort)
                    val uid = cm.getConnectionOwnerUid(protocol, local, remote)
                    if (uid != -1) {
                        return uidPackageCache.getOrPut(uid) {
                            packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "system"
                        }
                    }
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get connection owner UID", e)
            null
        }
    }

    private fun startForegroundNotification() {
        val channelId = "lykon_vpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Lykon Shield VPN", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lykon Shield Active")
            .setContentText("Blocking ads, trackers, and DNS bypass attempts")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }
}
