package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.model.User
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.*
import java.nio.charset.StandardCharsets

class DiscoveryService(private val context: Context) {
    companion object {
        private const val TAG = "DiscoveryService"
        private const val DISCOVERY_PORT = 55000
        private const val SIGNALING_PORT = 55001
        private const val PRUNE_INTERVAL_MS = 1000L
        private const val BROADCAST_INTERVAL_MS = 3000L
        private const val TIMEOUT_MS = 10000L
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val userAdapter = moshi.adapter(User::class.java)

    private val _discoveredUsers = MutableStateFlow<List<User>>(emptyList())
    val discoveredUsers = _discoveredUsers.asStateFlow()

    private val usersMap = java.util.concurrent.ConcurrentHashMap<String, User>()

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    private var multicastLock: WifiManager.MulticastLock? = null
    private var receiverSocket: DatagramSocket? = null

    @Synchronized
    fun start(userId: String, username: String) {
        if (isRunning) return
        isRunning = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Acquire Multicast Lock
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("LocalCallMulticastLock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }

        // Start broadcasting task
        scope.launch {
            while (isActive) {
                val ip = getLocalIpAddress()
                if (ip != null) {
                    broadcastHello(userId, username, ip)
                }
                delay(BROADCAST_INTERVAL_MS)
            }
        }

        // Start scanning task (receiver)
        scope.launch {
            startReceiver()
        }

        // Start pruning task
        scope.launch {
            while (isActive) {
                pruneInactiveUsers()
                delay(PRUNE_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun stop(userId: String? = null, username: String? = null) {
        if (!isRunning) return
        isRunning = false

        // Broadcast BYE message
        if (userId != null && username != null) {
            val ip = getLocalIpAddress() ?: ""
            // Run on global dispatcher to ensure BYE goes out before cancellation
            CoroutineScope(Dispatchers.IO).launch {
                broadcastBye(userId, username, ip)
            }
        }

        try {
            receiverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing receiver socket", e)
        }

        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing multicast lock", e)
        }
        multicastLock = null

        scope.cancel()
        usersMap.clear()
        _discoveredUsers.value = emptyList()
    }

    private fun broadcastHello(userId: String, username: String, ip: String) {
        val payload = """{"userId":"$userId","username":"$username","ip":"$ip","port":$SIGNALING_PORT}"""
        sendUdpPacket(payload)
    }

    private fun broadcastBye(userId: String, username: String, ip: String) {
        val payload = """{"userId":"$userId","username":"$username","ip":"$ip","port":$SIGNALING_PORT,"bye":true}"""
        sendUdpPacket(payload)
    }

    private fun sendUdpPacket(payload: String) {
        try {
            val data = payload.toByteArray(StandardCharsets.UTF_8)
            val broadcastAddress = getSubnetBroadcastAddress() ?: InetAddress.getByName("255.255.255.255")
            Log.d(TAG, "Sending UDP payload: $payload to $broadcastAddress:$DISCOVERY_PORT")
            
            val sendSocket = DatagramSocket()
            sendSocket.broadcast = true
            val packet = DatagramPacket(data, data.size, broadcastAddress, DISCOVERY_PORT)
            sendSocket.send(packet)
            sendSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending UDP packet", e)
        }
    }

    private fun startReceiver() {
        try {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            receiverSocket = socket
            val buffer = ByteArray(2048)
            Log.d(TAG, "UDP Receiver running on port $DISCOVERY_PORT")

            while (isRunning && !socket.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                parseReceivedPayload(text, packet.address.hostAddress ?: "")
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error in UDP receiver socket", e)
            }
        }
    }

    private fun parseReceivedPayload(json: String, senderIp: String) {
        try {
            Log.d(TAG, "Received payload from $senderIp: $json")
            if (json.contains("\"bye\":true")) {
                val map = moshi.adapter(Map::class.java).fromJson(json) ?: return
                val userId = map["userId"] as? String ?: return
                usersMap.remove(userId)
                _discoveredUsers.value = usersMap.values.toList()
                Log.d(TAG, "User $userId sent BYE, removed.")
                return
            }

            val user = userAdapter.fromJson(json)
            if (user != null) {
                val localIp = getLocalIpAddress()
                if (user.ip == localIp || user.userId.isEmpty()) return

                val updatedUser = user.copy(lastSeen = System.currentTimeMillis())
                usersMap[updatedUser.userId] = updatedUser
                _discoveredUsers.value = usersMap.values.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UDP packet json: $json", e)
        }
    }

    private fun pruneInactiveUsers() {
        val now = System.currentTimeMillis()
        var updated = false
        val keys = usersMap.keys()
        for (key in keys) {
            val u = usersMap[key] ?: continue
            if (now - u.lastSeen > TIMEOUT_MS) {
                usersMap.remove(key)
                updated = true
                Log.d(TAG, "Pruned user ${u.username} due to timeout.")
            }
        }
        if (updated) {
            _discoveredUsers.value = usersMap.values.toList()
        }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    fun getSubnetBroadcastAddress(): InetAddress? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null) {
                        return broadcast
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast address", e)
        }
        return null
    }
}
