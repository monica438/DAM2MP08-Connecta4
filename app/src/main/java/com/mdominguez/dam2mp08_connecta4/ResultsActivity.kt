package com.mdominguez.dam2mp08_connecta4

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ResultsActivity : AppCompatActivity() {

    private lateinit var myApp: MyApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_results)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Obtener mensaje del resultado
        val resultMessage = intent.getStringExtra("resultMessage") ?: "Partida finalizada"

        // Mostrar resultado
        findViewById<TextView>(R.id.resultText).text = resultMessage

        val backBtn = findViewById<Button>(R.id.backBtn)
        backBtn.setOnClickListener {
            // Volver a ChoosingActivity solicitando lista actualizada
            val intent = Intent(this, ChoosingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("currentPlayerName", myApp.currentPlayerName)
            }
            startActivity(intent)
            finish()
        }

        val exitBtn = findViewById<Button>(R.id.exitBtn)
        exitBtn.setOnClickListener {
            finishAffinity()
        }
    }

}