package com.mdominguez.dam2mp08_connecta4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var myApp: MyApp
    private val columnes = 7
    private val files = 6
    private lateinit var taulell: MutableList<MutableList<ImageView>>
    private var myPlayerName = ""
    private var opponentName = ""
    private var myRole = ""
    private var isMyTurn = false
    private var gameStarted = false
    private var gameFinished = false // Nueva variable para controlar estado de partida terminada

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Obtener nombres de los jugadores
        myPlayerName = intent.getStringExtra("playerName") ?: myApp.currentPlayerName
        opponentName = intent.getStringExtra("opponentName") ?: "Oponente"

        // Actualizar UI con nombres
        findViewById<TextView>(R.id.player1Main).text = myPlayerName
        findViewById<TextView>(R.id.player2Main).text = opponentName
        findViewById<TextView>(R.id.turnoMain).text = "El juego va a comenzar..."

        // Configurar callback para mensajes del servidor
        myApp.setMessageCallback { message ->
            processMessage(message)
        }

        construirTaulell()
    }

    override fun onResume() {
        super.onResume()
        myApp.setMessageCallback { message -> processMessage(message) }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun processMessage(message: String) {
        Log.d("MAIN_ACTIVITY", "Procesando mensaje: $message")
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            if (type == "serverData") {
                processGameState(jsonObject)
            }
        } catch (e: Exception) {
            Log.e("MAIN_ACTIVITY", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun processGameState(gameState: JSONObject) {
        runOnUiThread {
            try {
                val game = gameState.optJSONObject("game")
                val clientsList = gameState.optJSONArray("clientsList")

                // Determinar mi rol
                if (clientsList != null) {
                    for (i in 0 until clientsList.length()) {
                        val client = clientsList.getJSONObject(i)
                        if (client.optString("name") == myPlayerName) {
                            myRole = client.optString("role", "")
                            break
                        }
                    }
                }

                if (game != null) {
                    val status = game.optString("status", "")
                    val turnPlayer = game.optString("turn", "")
                    val winner = game.optString("winner", "")

                    isMyTurn = turnPlayer == myPlayerName

                    // Resetear estado finished si el juego vuelve a empezar
                    if (status == "playing" && gameFinished) {
                        gameFinished = false
                    }

                    // El juego ha comenzado cuando el estado es "playing"
                    gameStarted = (status == "playing")

                    Log.d("GAME", "Estado: $status, Mi turno: $isMyTurn, GameStarted: $gameStarted, GameFinished: $gameFinished")

                    when (status) {
                        "playing" -> {
                            updateBoard(game.optJSONArray("board"))
                            updateTurnInfo()
                        }
                        "win", "draw" -> {
                            if (!gameFinished) {
                                updateBoard(game.optJSONArray("board"))
                                gameFinished = true
                                if (status == "win") {
                                    if (winner == myPlayerName) {
                                        goToResults("¡HAS GANADO!")
                                    } else {
                                        goToResults("$winner ha ganado")
                                    }
                                } else {
                                    goToResults("¡EMPATE!")
                                }
                            }
                        }
                        else -> {
                            // Estado COUNTDOWN o waiting
                            findViewById<TextView>(R.id.turnoMain).text = "Preparando juego..."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MAIN_ACTIVITY", "Error procesando estado del juego: ${e.message}")
            }
        }
    }

    private fun updateTurnInfo() {
        val turnText = if (isMyTurn) {
            "TU TURNO"
        } else {
            "TURNO DE $opponentName"
        }
        findViewById<TextView>(R.id.turnoMain).text = turnText
    }

    private fun updateBoard(boardArray: JSONArray?) {
        if (boardArray == null) return

        try {
            // Actualizar tablero visualmente
            for (fila in 0 until boardArray.length()) {
                val rowArray = boardArray.getJSONArray(fila)
                for (columna in 0 until rowArray.length()) {
                    val cellValue = rowArray.getString(columna)
                    if (cellValue != " " && cellValue != "null" && cellValue.isNotEmpty()) {
                        val visualFila = fila + 1 // Ajustar porque la fila 0 es la de botones
                        if (visualFila < taulell.size) {
                            val casella = taulell[visualFila][columna]

                            when (cellValue) {
                                "R" -> {
                                    casella.setImageResource(R.drawable.cercle_vermell)
                                    casella.tag = "vermell"
                                }
                                "Y" -> {
                                    casella.setImageResource(R.drawable.cercle_groc)
                                    casella.tag = "groc"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MAIN_ACTIVITY", "Error actualizando tablero: ${e.message}")
        }
    }

    private fun goToResults(message: String) {
        val intent = Intent(this, ResultsActivity::class.java).apply {
            putExtra("resultMessage", message)
        }
        startActivity(intent)
        finish()
    }

    private fun construirTaulell() {
        val baseLayout = findViewById<ConstraintLayout>(R.id.main)
        val vsViewTitle = findViewById<TextView>(R.id.vsMain)

        // Crear TableLayout
        val tableLayout = TableLayout(this).apply {
            id = View.generateViewId()
            layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            isShrinkAllColumns = true
            isStretchAllColumns = true
        }

        baseLayout.addView(tableLayout)

        // Situar el taulell bajo el textView del título
        val constraintSet = ConstraintSet()
        constraintSet.clone(baseLayout)
        constraintSet.connect(tableLayout.id, ConstraintSet.TOP, vsViewTitle.id, ConstraintSet.BOTTOM, 100)
        constraintSet.connect(tableLayout.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(tableLayout.id, ConstraintSet.END, ConstraintSet.PARENT_ID,ConstraintSet.END)
        constraintSet.applyTo(baseLayout)

        // Calcular tamaño de celda
        val ampladaPantalla = resources.displayMetrics.widthPixels
        val tamanyCasella = ampladaPantalla / columnes

        // Inicializar el taulell
        taulell = mutableListOf()

        // Fila extra de botones
        val filaBotons = mutableListOf<ImageView>()
        val filaBotonsLayout = TableRow(this)

        for (columna in 0 until columnes) {
            val boto = Button(this).apply {
                text = "↓"
                layoutParams = TableRow.LayoutParams(tamanyCasella, tamanyCasella)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setOnClickListener {
                    onColumnClick(columna)
                }
            }
            // Añadir ImageView invisible para mantener estructura consistente
            val imageView = ImageView(this).apply {
                layoutParams = TableRow.LayoutParams(0, 0)
                visibility = View.GONE
            }
            filaBotons.add(imageView)
            filaBotonsLayout.addView(boto)
        }
        tableLayout.addView(filaBotonsLayout)
        taulell.add(filaBotons)

        // Las resto de files
        for (fila in 0 until files) {
            val filaImageViews = mutableListOf<ImageView>()
            val filaLayout = TableRow(this)

            for (columna in 0 until columnes) {
                val casella = ImageView(this).apply {
                    layoutParams = TableRow.LayoutParams(tamanyCasella,tamanyCasella).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageResource(R.drawable.cercle_buit)
                    tag = "buit"
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                filaImageViews.add(casella)
                filaLayout.addView(casella)
            }
            tableLayout.addView(filaLayout)
            taulell.add(filaImageViews)
        }
    }

    private fun onColumnClick(columna: Int) {
        Log.d("GAME", "Botón pulsado - Columna: $columna, GameStarted: $gameStarted, isMyTurn: $isMyTurn")

        if (!gameStarted) {
            Log.d("GAME", "Juego no ha comenzado todavía")
            findViewById<TextView>(R.id.turnoMain).text = "El juego no ha comenzado"

            // Mostrar Toast temporal
            android.widget.Toast.makeText(
                this,
                "El juego no ha comenzado todavía",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isMyTurn) {
            Log.d("GAME", "No es tu turno - Turno de: $opponentName")

            // MOSTRAR TOAST TEMPORAL en lugar de cambiar el texto permanente
            android.widget.Toast.makeText(
                this,
                "Espera tu turno! - Turno de $opponentName",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Solo enviar jugada si es tu turno Y el juego ha comenzado
        Log.d("GAME", "Enviando jugada - Columna: $columna")

        // Enviar jugada al servidor
        val playData = JSONObject().apply {
            put("type", "clientPlay")
            put("value", JSONObject().apply {
                put("column", columna)
            })
        }

        myApp.sendWebSocketMessage(playData.toString())
        Log.d("GAME", "Jugada enviada: columna $columna")

        // Deshabilitar temporalmente
        findViewById<TextView>(R.id.turnoMain).text = "Jugada enviada..."
    }

    override fun onDestroy() {
        super.onDestroy()
        // No cerrar la conexión WebSocket - se mantiene en MyApp
    }
}