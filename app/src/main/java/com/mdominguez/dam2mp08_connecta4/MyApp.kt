package com.mdominguez.dam2mp08_connecta4

import android.app.Application
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class MyApp : Application() {

    companion object {
        lateinit var instance: MyApp
    }

    private var webSocketClient: WebSocketClient? = null
    var isConnected = false
    var currentPlayerName = ""
    private var messageCallback: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun connectWebSocket(serverUrl: String, playerName: String, onMessage: (String) -> Unit) {
        // Para controlar la reconexión: si ya estamos conectados con el mismo nombre, no hacer nada
        if (isConnected && currentPlayerName == playerName) {
            Log.d("MyApp", "Ya conectado como $playerName - Reutilizando conexión")
            messageCallback = onMessage
            return
        }

        currentPlayerName = playerName
        messageCallback = onMessage

        try {
            // Cerrar conexión anterior si existe
            webSocketClient?.close()

            val uri = URI(serverUrl)
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    Log.d("MyApp", "WebSocket conectado")
                    isConnected = true

                    // Enviar información del usuario
                    val userInfo = JSONObject().apply {
                        put("type", "userInfo")
                        put("userName", playerName)
                    }
                    send(userInfo.toString())
                }

                override fun onMessage(message: String?) {
                    Log.d("MyApp", "Mensaje: $message")
                    message?.let {
                        messageCallback?.invoke(it)
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.d("MyApp", "WebSocket cerrado: $reason")
                    isConnected = false
                }

                override fun onError(ex: Exception?) {
                    Log.e("MyApp", "WebSocket error: ${ex?.message}")
                    isConnected = false
                }
            }

            webSocketClient?.connect()

        } catch (e: Exception) {
            Log.e("MyApp", "Error al conectar WebSocket: ${e.message}")
        }
    }

    fun sendWebSocketMessage(message: String) {
        if (isConnected && webSocketClient != null) {
            webSocketClient?.send(message)
        } else {
            Log.e("MyApp", "WebSocket no conectado, no se puede enviar mensaje")
        }
    }

    fun setMessageCallback(callback: (String) -> Unit) {
        this.messageCallback = callback
    }
}