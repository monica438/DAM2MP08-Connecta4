package com.mdominguez.dam2mp08_connecta4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class PairingActivity : AppCompatActivity() {

    private lateinit var myApp: MyApp
    private lateinit var player1Name: String
    private var opponentName: String = "?"
    private var invitationAccepted = false
    private var isInviter = false
    private var invitationRejected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pairing)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Obtener nombres y rol
        player1Name = intent.getStringExtra("playerName") ?: myApp.currentPlayerName
        val intendedOpponent = intent.getStringExtra("opponentName") ?: "?"
        isInviter = intent.getBooleanExtra("isInviter", false)

        // Configurar UI según el rol
        setupUI(intendedOpponent)

        // Configurar callback para mensajes del servidor
        myApp.setMessageCallback { message ->
            processMessage(message)
        }
    }

    override fun onResume() {
        super.onResume()
        myApp.setMessageCallback { message -> processMessage(message) }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun setupUI(intendedOpponent: String) {
        if (isInviter) {
            // SOMOS EL INVITADOR: mostramos nuestro nombre y "?" para el oponente
            findViewById<TextView>(R.id.player1Pairing).text = player1Name
            findViewById<TextView>(R.id.player2Pairing).text = "?"
            updateStatus("Esperando respuesta de $intendedOpponent...")
            opponentName = intendedOpponent
        } else {
            // SOMOS EL INVITADO: mostramos ambos nombres
            findViewById<TextView>(R.id.player1Pairing).text = intendedOpponent
            findViewById<TextView>(R.id.player2Pairing).text = player1Name
            opponentName = intendedOpponent
            invitationAccepted = true
            updateStatus("Emparejamiento confirmado - Esperando inicio...")
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            findViewById<TextView?>(R.id.statusPairing)?.text = message
        }
    }

    private fun processMessage(message: String) {
        Log.d("PAIRING", "Procesando mensaje: $message")
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            when (type) {
                "invite response" -> {
                    if (isInviter) {
                        val origin = jsonObject.optString("origin", "")
                        val accepted = jsonObject.optBoolean("accepted", false)
                        Log.d("PAIRING", "Respuesta de invitación recibida: $origin - $accepted")

                        runOnUiThread {
                            if (accepted) {
                                invitationAccepted = true
                                opponentName = origin
                                findViewById<TextView>(R.id.player2Pairing).text = origin
                                updateStatus("¡$origin aceptó! Preparando juego...")

                                // El otro jugador aceptó, podemos continuar
                                checkIfCanProceed()
                            } else {
                                invitationRejected = true
                                updateStatus("$origin rechazó tu invitación")
                                // Usar postDelayed de la view
                                findViewById<TextView>(R.id.statusPairing)?.postDelayed({
                                    returnToChoosing()
                                }, 1500)
                            }
                        }
                    }
                }
                "nameClient" -> {
                    val player1 = jsonObject.optString("player1", "")
                    val player2 = jsonObject.optString("player2", "")
                    Log.d("PAIRING", "Nombres confirmados: $player1 vs $player2")

                    runOnUiThread {
                        findViewById<TextView>(R.id.player1Pairing).text = player1
                        findViewById<TextView>(R.id.player2Pairing).text = player2
                        opponentName = if (player1 == player1Name) player2 else player1
                        updateStatus("Emparejamiento confirmado - Iniciando juego...")

                        if (!isInviter) {
                            // Como invitado, estamos listos para proceder
                            invitationAccepted = true
                            checkIfCanProceed()
                        }
                    }
                }
                "entersPlayer1", "entersPlayer2" -> {
                    Log.d("PAIRING", "Jugador entró al emparejamiento: $type")
                    runOnUiThread {
                        updateStatus("Ambos jugadores listos...")
                    }
                }
                "countdown" -> {
                    val countdownValue = jsonObject.optInt("value", 0)
                    Log.d("PAIRING", "Countdown recibido: $countdownValue")

                    if (countdownValue == 5) {
                        runOnUiThread {
                            goToCountdown()
                        }
                    }
                }
                "serverData" -> {
                    val game = jsonObject.optJSONObject("game")
                    val status = game?.optString("status", "")

                    Log.d("PAIRING", "Estado del juego recibido: $status")

                    if (status == "COUNTDOWN" || status == "playing") {
                        runOnUiThread {
                            goToCountdown()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PAIRING", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun checkIfCanProceed() {
        if (isInviter) {
            // Como invitador, necesitamos que la invitación sea aceptada
            if (invitationAccepted && !invitationRejected) {
                // Esperar un poco antes de proceder para que el usuario vea la confirmación
                findViewById<TextView>(R.id.statusPairing)?.postDelayed({
                    goToCountdown()
                }, 1000)
            }
        } else {
            // Como invitado, proceder directamente
            if (invitationAccepted) {
                findViewById<TextView>(R.id.statusPairing)?.postDelayed({
                    goToCountdown()
                }, 1000)
            }
        }
    }

    private fun returnToChoosing() {
        Log.d("PAIRING", "🔙 Volviendo a ChoosingActivity - Invitación rechazada")
        val intent = Intent(this, ChoosingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("currentPlayerName", myApp.currentPlayerName)
        }
        startActivity(intent)
        finish()
    }

    private fun goToCountdown() {
        if (invitationRejected) {
            return // No proceder si la invitación fue rechazada
        }

        val canProceed = if (isInviter) {
            invitationAccepted && opponentName != "?" && !invitationRejected
        } else {
            invitationAccepted && !invitationRejected
        }

        if (canProceed) {
            Log.d("PAIRING", "Redirigiendo a CountdownActivity")
            val intent = Intent(this, CountdownActivity::class.java).apply {
                putExtra("playerName", player1Name)
                putExtra("opponentName", opponentName)
            }
            startActivity(intent)
            finish()
        }
    }
}