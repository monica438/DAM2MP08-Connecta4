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
    private var isProcessingInvitation = false

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

        // Reset del estado
        resetChoosingState()

        // Procesar lista inicial si existe
        if (!initialClientsJson.isNullOrEmpty()) {
            try {
                val initialClientsArray = JSONArray(initialClientsJson)
                processClientsListData(initialClientsArray)
                Log.d("CHOOSING", "Lista inicial procesada: ${connectedUsers.size} usuarios")
            } catch (e: Exception) {
                Log.e("CHOOSING", "Error procesando lista inicial: ${e.message}")
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

        Log.d("CHOOSING", "Activity creada - Lista de usuarios: ${connectedUsers.size}")
    }

    override fun onResume() {
        super.onResume()
        myApp.setMessageCallback { message -> processMessage(message) }
        Log.d("CHOOSING", "onResume - Reiniciando estado")
        resetChoosingState()

    }

    override fun onPause() {
        super.onPause()
        Log.d("CHOOSING", "onPause - Limpiando estado")
        isProcessingInvitation = false
    }

    private fun resetChoosingState() {
        isProcessingInvitation = false
        clearStatusMessages()
        Log.d("CHOOSING", "Estado reseteado")
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
        Log.d("CHOOSING", "Mensaje recibido: $message")
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            when (type) {
                "clients" -> {
                    Log.d("CHOOSING", "Lista actualizada recibida")
                    val listArray = jsonObject.optJSONArray("list")
                    if (listArray != null) {
                        processClientsListData(listArray)
                    }
                }
                "userJoined" -> {
                    val userName = jsonObject.optString("userName", "")
                    Log.d("CHOOSING", "Usuario conectado: $userName")
                }
                "userLeft" -> {
                    val userName = jsonObject.optString("userName", "")
                    Log.d("CHOOSING", "Usuario desconectado: $userName")
                }
                "invite to play" -> {
                    handleInvitationReceived(jsonObject)
                }
                // El invite response la recibiremos en la vista de Pairing
                "entersPlayer1", "entersPlayer2" -> {
                    Log.d("CHOOSING", "Mensaje de emparejamiento: $type - IGNORADO")
                }
            }
        } catch (e: Exception) {
            Log.e("CHOOSING", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun handleInvitationReceived(jsonObject: JSONObject) {
        val origin = jsonObject.optString("origin", "")
        val messageText = jsonObject.optString("message", "")

        Log.d("CHOOSING", "INVITACIÓN RECIBIDA de: $origin")

        // Si ya estamos procesando una invitación, ignorar esta
        if (isProcessingInvitation) {
            Log.d("CHOOSING", "IGNORANDO invitación - Ya hay una en proceso")
            return
        }

        // Marcar que estamos procesando esta invitación
        isProcessingInvitation = true

        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Log.d("CHOOSING", "Mostrando alerta de invitación")
                showInvitationAlert(origin, messageText)
            } else {
                Log.d("CHOOSING", "Activity no activa - No se muestra alerta")
                resetChoosingState()
            }
        }
    }


    private fun processClientsListData(listArray: JSONArray) {
        runOnUiThread {
            try {
                Log.d("CHOOSING", "Procesando lista de ${listArray.length()} clientes")
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
                    Log.d("CHOOSING", "Lista actualizada: ${connectedUsers.size} jugador(es)")
                } else {
                    connectedUsers.clear()
                    updateSpinner()
                    updateStatus()
                    Log.d("CHOOSING", "Lista vacía - No hay otros jugadores")
                }
            } catch (e: Exception) {
                Log.e("CHOOSING", "Error procesando lista de clientes: ${e.message}")
                txtStatus.text = "Error al cargar lista"
            }
        }
    }

    private fun goToPairing(opponent: String, isInviter: Boolean) {
        Log.d("CHOOSING", "REDIRIGIENDO a PairingActivity - Rol: ${if (isInviter) "INVITADOR" else "INVITADO"}, Opponent: $opponent")

        // Limpiar estado antes de ir a Pairing
        isProcessingInvitation = false

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
            Log.d("CHOOSING", "Spinner actualizado con ${connectedUsers.size} usuarios")
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
        Log.d("CHOOSING", "ENVIANDO invitación a: $opponent")

        runOnUiThread {
            txtStatus.text = "Enviando invitación a $opponent..."
        }

        val invitation = JSONObject().apply {
            put("type", "invite to play")
            put("destination", opponent)
            put("message", "¿Quieres jugar Connecta4?")
        }
        myApp.sendWebSocketMessage(invitation.toString())

        // Como INVITADOR, vamos DIRECTAMENTE a Pairing después de enviar invitación
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                Log.d("CHOOSING", "Como INVITADOR, yendo a Pairing inmediatamente")
                goToPairing(opponent, isInviter = true)
            }
        }
    }

    private fun showInvitationAlert(origin: String, message: String) {
        Log.d("CHOOSING", "MOSTRANDO ALERTA de invitación de: $origin")

        runOnUiThread {
            if (isFinishing || isDestroyed) {
                Log.d("CHOOSING", "Activity no activa - No se muestra alerta")
                resetChoosingState()
                return@runOnUiThread
            }

            try {
                val alertDialog = AlertDialog.Builder(this)
                    .setTitle("Invitación de $origin")
                    .setMessage(message)
                    .setPositiveButton("Aceptar") { dialog, which ->
                        Log.d("CHOOSING", "USUARIO ACEPTÓ invitación de: $origin")

                        // Enviar respuesta de aceptación
                        val response = JSONObject().apply {
                            put("type", "invite response")
                            put("destination", origin)
                            put("accepted", true)
                        }
                        myApp.sendWebSocketMessage(response.toString())

                        runOnUiThread {
                            txtStatus.text = "Aceptaste la invitación de $origin"
                            Log.d("CHOOSING", "Como INVITADO, yendo a Pairing después de aceptar")
                            // Como INVITADO, vamos a Pairing inmediatamente después de aceptar
                            goToPairing(origin, isInviter = false)
                        }
                    }
                    .setNegativeButton("Rechazar") { dialog, which ->
                        Log.d("CHOOSING", "USUARIO RECHAZÓ invitación de: $origin")

                        val response = JSONObject().apply {
                            put("type", "invite response")
                            put("destination", origin)
                            put("accepted", false)
                        }
                        myApp.sendWebSocketMessage(response.toString())

                        runOnUiThread {
                            txtStatus.text = "Rechazaste la invitación"
                            resetChoosingState()
                        }
                    }
                    .setOnCancelListener {
                        Log.d("CHOOSING", "DIÁLOGO CANCELADO - Considerado como rechazo")

                        val response = JSONObject().apply {
                            put("type", "invite response")
                            put("destination", origin)
                            put("accepted", false)
                        }
                        myApp.sendWebSocketMessage(response.toString())

                        runOnUiThread {
                            txtStatus.text = "Invitación cancelada"
                            resetChoosingState()
                        }
                    }
                    .setCancelable(true)
                    .create()

                alertDialog.show()

            } catch (e: Exception) {
                Log.e("CHOOSING", "Error mostrando alerta: ${e.message}")
                resetChoosingState()
            }
        }
    }

    private fun showAlert(message: String) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}