package com.mdominguez.dam2mp08_connecta4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class ChoosingActivity : AppCompatActivity() {

    private lateinit var spinnerJugadores: Spinner
    private lateinit var txtStatus: TextView
    private val connectedUsers = mutableListOf<String>()
    private lateinit var myApp: MyApp
    private var currentUserName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_choosing)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Obtener datos de la actividad anterior
        currentUserName = intent.getStringExtra("currentPlayerName") ?: myApp.currentPlayerName
        val initialClientsJson = intent.getStringExtra("initialClients")

        spinnerJugadores = findViewById<Spinner>(R.id.spinnerJugadores)
        txtStatus = findViewById<TextView>(R.id.txtStatus)

        // Reset completo del estado
        resetChoosingState()

        // Procesar lista inicial SI existe
        if (!initialClientsJson.isNullOrEmpty()) {
            try {
                val initialClientsArray = JSONArray(initialClientsJson)
                processClientsListData(initialClientsArray)
                Log.d("CHOOSING", "✅ Lista inicial procesada: ${connectedUsers.size} usuarios")
            } catch (e: Exception) {
                Log.e("CHOOSING", "❌ Error procesando lista inicial: ${e.message}")
            }
        }

        // Configurar spinner
        updateSpinner()
        updateStatus()

        // Configurar el callback para mensajes FUTUROS
        myApp.setMessageCallback { message ->
            processMessage(message)
        }

        val playBtn = findViewById<Button>(R.id.playBtn)
        playBtn.setOnClickListener {
            val selectedOpponent = spinnerJugadores.selectedItem as? String
            if (selectedOpponent != null && selectedOpponent != currentUserName) {
                sendInvitation(selectedOpponent)
            } else {
                showAlert("Selecciona un oponente válido")
            }
        }

        Log.d("CHOOSING", "🔁 Activity creada - Lista de usuarios: ${connectedUsers.size}")
    }

    override fun onResume() {
        super.onResume()
        // Reset cada vez que la actividad se hace visible
        resetChoosingState()
        Log.d("CHOOSING", "🔁 Activity resumida - Estado limpio")
    }

    private fun resetChoosingState() {
        // NO usar variable isProcessingInvitation - resetear completamente
        clearStatusMessages()
        Log.d("CHOOSING", "🔄 ESTADO COMPLETAMENTE RESETADO")
    }

    private fun clearStatusMessages() {
        runOnUiThread {
            val userCount = connectedUsers.size
            val statusText = when (userCount) {
                0 -> "Conectado - Esperando más jugadores..."
                1 -> "Conectado - 1 jugador disponible"
                else -> "Conectado - $userCount jugadores disponibles"
            }
            txtStatus.text = statusText
            txtStatus.setTextColor(resources.getColor(android.R.color.black, null))
        }
    }

    private fun processMessage(message: String) {
        Log.d("CHOOSING", "📥 Mensaje recibido: $message")
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            when (type) {
                "clients" -> {
                    Log.d("CHOOSING", "🔄 Lista actualizada recibida")
                    val listArray = jsonObject.optJSONArray("list")
                    if (listArray != null) {
                        processClientsListData(listArray)
                    }
                }
                "userJoined" -> {
                    val userName = jsonObject.optString("userName", "")
                    Log.d("CHOOSING", "👤 Usuario conectado: $userName")
                    // Pedir lista actualizada al servidor
                    requestClientsList()
                }
                "userLeft" -> {
                    val userName = jsonObject.optString("userName", "")
                    Log.d("CHOOSING", "🚪 Usuario desconectado: $userName")
                    // Pedir lista actualizada al servidor
                    requestClientsList()
                }
                "invite to play" -> {
                    val origin = jsonObject.optString("origin", "")
                    val messageText = jsonObject.optString("message", "")
                    Log.d("CHOOSING", "🎮 Invitación recibida de: $origin")

                    runOnUiThread {
                        showInvitationAlert(origin, messageText)
                    }
                }
                "invite response" -> {
                    val origin = jsonObject.optString("origin", "")
                    val accepted = jsonObject.optBoolean("accepted", false)
                    Log.d("CHOOSING", "📨 Respuesta de invitación: $origin - $accepted")

                    runOnUiThread {
                        if (accepted) {
                            txtStatus.text = "$origin aceptó tu invitación!"
                            if (!isFinishing) {
                                goToPairing(origin, isInviter = true)
                            }
                        } else {
                            txtStatus.text = "$origin rechazó tu invitación"
                            showTemporaryAlert("$origin ha rechazado tu invitación")
                            android.os.Handler().postDelayed({
                                resetChoosingState()
                            }, 2000)
                        }
                    }
                }
                "nameClient" -> {
                    val player1 = jsonObject.optString("player1", "")
                    val player2 = jsonObject.optString("player2", "")
                    Log.d("CHOOSING", "🎯 Emparejamiento confirmado: $player1 vs $player2")

                    if (player1 == currentUserName || player2 == currentUserName) {
                        val opponent = if (player1 == currentUserName) player2 else player1
                        val isInviter = (player1 == currentUserName)

                        Log.d("CHOOSING", "🎯 Rol detectado: ${if (isInviter) "INVITADOR" else "INVITADO"}")

                        runOnUiThread {
                            goToPairing(opponent, isInviter)
                        }
                    }
                }
                "entersPlayer1", "entersPlayer2" -> {
                    Log.d("CHOOSING", "🚪 Jugador entró al emparejamiento")
                }
            }
        } catch (e: Exception) {
            Log.e("CHOOSING", "❌ Error procesando mensaje: ${e.message}")
        }
    }

    private fun requestClientsList() {
        // Enviar mensaje al servidor para solicitar lista actualizada
        val request = JSONObject().apply {
            put("type", "getClients")
        }
        myApp.sendWebSocketMessage(request.toString())
        Log.d("CHOOSING", "📋 Solicitando lista actualizada de clientes")
    }

    private fun showTemporaryAlert(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun processClientsListData(listArray: JSONArray) {
        runOnUiThread {
            try {
                Log.d("CHOOSING", "📋 Procesando lista de ${listArray.length()} clientes")
                val previousCount = connectedUsers.size
                connectedUsers.clear()

                if (listArray.length() > 0) {
                    for (i in 0 until listArray.length()) {
                        val userName = listArray.getString(i)
                        if (userName != currentUserName) {
                            connectedUsers.add(userName)
                        }
                    }
                    updateSpinner()
                    updateStatus()
                    Log.d("CHOOSING", "✅ Lista actualizada: ${connectedUsers.size} jugador(es) - Antes: $previousCount")
                } else {
                    connectedUsers.clear()
                    updateSpinner()
                    updateStatus()
                    Log.d("CHOOSING", "📭 Lista vacía - No hay otros jugadores")
                }
            } catch (e: Exception) {
                Log.e("CHOOSING", "❌ Error procesando lista de clientes: ${e.message}")
                txtStatus.text = "Error al cargar lista"
            }
        }
    }

    private fun goToPairing(opponent: String, isInviter: Boolean) {
        Log.d("CHOOSING", "🎯 Redirigiendo a PairingActivity como ${if (isInviter) "INVITADOR" else "INVITADO"}")
        val intent = Intent(this, PairingActivity::class.java).apply {
            putExtra("playerName", currentUserName)
            putExtra("opponentName", opponent)
            putExtra("isInviter", isInviter)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun updateSpinner() {
        runOnUiThread {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectedUsers)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerJugadores.adapter = adapter

            if (connectedUsers.isNotEmpty()) {
                spinnerJugadores.setSelection(0)
            }
            Log.d("CHOOSING", "🔄 Spinner actualizado con ${connectedUsers.size} usuarios")
        }
    }

    private fun updateStatus() {
        runOnUiThread {
            val userCount = connectedUsers.size
            val statusText = when (userCount) {
                0 -> "Conectado - Esperando más jugadores..."
                1 -> "Conectado - 1 jugador disponible"
                else -> "Conectado - $userCount jugadores disponibles"
            }
            txtStatus.text = statusText
        }
    }

    private fun sendInvitation(opponent: String) {
        runOnUiThread {
            txtStatus.text = "Enviando invitación a $opponent..."
        }

        val invitation = JSONObject().apply {
            put("type", "invite to play")
            put("destination", opponent)
            put("message", "¿Quieres jugar Connecta4?")
        }
        myApp.sendWebSocketMessage(invitation.toString())
        Log.d("INVITATION", "✉️ Invitación enviada a: $opponent")

        goToPairing(opponent, isInviter = true)
    }

    private fun showInvitationAlert(origin: String, message: String) {
        Log.d("CHOOSING", "🎯 Mostrando alerta de invitación de: $origin")

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Invitación de $origin")
                .setMessage(message)
                .setPositiveButton("Aceptar") { dialog, which ->
                    Log.d("CHOOSING", "✅ Usuario aceptó invitación de: $origin")
                    val response = JSONObject().apply {
                        put("type", "invite response")
                        put("destination", origin)
                        put("accepted", true)
                    }
                    myApp.sendWebSocketMessage(response.toString())
                    runOnUiThread {
                        txtStatus.text = "Aceptaste la invitación de $origin"
                    }
                    // Ir directamente a Pairing como INVITADO después de aceptar
                    android.os.Handler().postDelayed({
                        goToPairing(origin, isInviter = false)
                    }, 500)
                }
                .setNegativeButton("Rechazar") { dialog, which ->
                    Log.d("CHOOSING", "❌ Usuario rechazó invitación de: $origin")
                    val response = JSONObject().apply {
                        put("type", "invite response")
                        put("destination", origin)
                        put("accepted", false)
                    }
                    myApp.sendWebSocketMessage(response.toString())
                    runOnUiThread {
                        txtStatus.text = "Rechazaste la invitación"
                    }
                    android.os.Handler().postDelayed({
                        resetChoosingState()
                    }, 2000)
                }
                .setOnCancelListener {
                    // Si el usuario cancela el diálogo, resetear estado
                    Log.d("CHOOSING", "❌ Diálogo cancelado - Reseteando estado")
                    resetChoosingState()
                }
                .setCancelable(true)
                .show()
        }
    }

    private fun showAlert(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}