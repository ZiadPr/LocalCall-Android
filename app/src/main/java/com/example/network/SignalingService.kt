package com.example.network

import android.util.Log
import com.example.data.model.CallMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket

class SignalingService {
    companion object {
        private const val TAG = "SignalingService"
        private const val PORT = 55001
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val messageAdapter = moshi.adapter(CallMessage::class.java)

    private var serverSocket: ServerSocket? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    var messageListener: ((message: CallMessage, senderIp: String) -> Unit)? = null

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                val server = ServerSocket(PORT).apply {
                    reuseAddress = true
                }
                serverSocket = server
                Log.d(TAG, "TCP Server listening on port $PORT")

                while (isRunning && !server.isClosed) {
                    val clientSocket = server.accept()
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Exception in TCP server listener", e)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ServerSocket", e)
        }
        serverSocket = null
        scope.cancel()
    }

    private fun handleClient(socket: Socket) {
        val senderIp = socket.inetAddress.hostAddress ?: ""
        try {
            BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                val lines = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    lines.append(line)
                }
                val payload = lines.toString().trim()
                if (payload.isNotEmpty()) {
                    Log.d(TAG, "TCP received from $senderIp: $payload")
                    val message = messageAdapter.fromJson(payload)
                    if (message != null) {
                        messageListener?.invoke(message, senderIp)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from client socket ($senderIp)", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun sendMessage(targetIp: String, message: CallMessage, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch {
            var success = false
            var socket: Socket? = null
            try {
                Log.d(TAG, "TCP sending message to $targetIp: ${message.type}")
                socket = Socket()
                socket.connect(java.net.InetSocketAddress(targetIp, PORT), 5000)
                socket.soTimeout = 5000
                OutputStreamWriter(socket.getOutputStream()).use { writer ->
                    val payload = messageAdapter.toJson(message)
                    writer.write(payload)
                    writer.flush()
                }
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send TCP message to $targetIp", e)
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // ignore
                }
                onComplete?.invoke(success)
            }
        }
    }
}
