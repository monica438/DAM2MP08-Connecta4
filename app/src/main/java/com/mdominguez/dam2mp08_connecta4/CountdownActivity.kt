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

class CountdownActivity : AppCompatActivity() {

    private lateinit var myApp: MyApp
    private lateinit var countdownValue: TextView
    private lateinit var player1Name: String
    private lateinit var player2Name: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_countdown)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Obtener nombres de los jugadores
        player1Name = intent.getStringExtra("playerName") ?: myApp.currentPlayerName
        player2Name = intent.getStringExtra("opponentName") ?: "Oponente"

        countdownValue = findViewById(R.id.countdownValue)

        // Configurar callback para mensajes del servidor
        myApp.setMessageCallback { message ->
            processMessage(message)
        }

        // Mostrar mensaje inicial
        findViewById<TextView>(R.id.textView).text = "PREPARADOS PARA JUGAR\n$player1Name vs $player2Name"
        countdownValue.text = "5"
    }

    private fun processMessage(message: String) {
        Log.d("COUNTDOWN", "Procesando mensaje: $message")
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            when (type) {
                "countdown" -> {
                    val countdownVal = jsonObject.optInt("value", 0)
                    runOnUiThread {
                        updateCountdown(countdownVal)
                    }
                }
                "serverData" -> {
                    // Verificar si el juego ha comenzado
                    val game = jsonObject.optJSONObject("game")
                    val status = game?.optString("status", "")

                    if (status == "playing") {
                        runOnUiThread {
                            goToGame()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("COUNTDOWN", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun updateCountdown(value: Int) {
        countdownValue.text = if (value == 0) "¡GO!" else value.toString()

        if (value == 0) {
            // Esperar un segundo y luego ir al juego
            android.os.Handler().postDelayed({
                goToGame()
            }, 1000)
        }
    }

    private fun goToGame() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("playerName", player1Name)
            putExtra("opponentName", player2Name)
        }
        startActivity(intent)
        finish()
    }
}