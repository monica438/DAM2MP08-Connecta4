package com.mdominguez.dam2mp08_connecta4

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class ConfigurationActivity : AppCompatActivity() {

    private lateinit var txtMessage: TextView
    private lateinit var editPlayerName: EditText
    private lateinit var editProtocol: EditText
    private lateinit var editServerIP: EditText
    private lateinit var editPort: EditText
    private lateinit var myApp: MyApp
    private var initialClientsList = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_configuration)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        myApp = application as MyApp

        // Inicializar vistas
        txtMessage = findViewById(R.id.txtMessage)
        editPlayerName = findViewById(R.id.editPlayerName)
        editProtocol = findViewById(R.id.editProtocol)
        editServerIP = findViewById(R.id.editServerIP)
        editPort = findViewById(R.id.editPort)

        val connectBtn = findViewById<Button>(R.id.connectBtn)
        connectBtn.setOnClickListener {
            connectToServer()
        }

        val localBtn = findViewById<Button>(R.id.localBtn)
        localBtn.setOnClickListener {
            editProtocol.setText("ws")
            editServerIP.setText("172.30.70.227") //10.0.2.2
            editPort.setText("3000")
        }

        val proxmoxBtn = findViewById<Button>(R.id.proxmoxBtn)
        proxmoxBtn.setOnClickListener {
            editProtocol.setText("wss")
            editServerIP.setText("mdominguezaguirre.ieti.site")
            editPort.setText("443")
        }
    }

    private fun connectToServer() {
        val playerName = editPlayerName.text.toString().trim()
        if (playerName.isEmpty()) {
            showMessage("Ingresa tu nombre", true)
            editPlayerName.requestFocus()
            return
        }

        val protocol = editProtocol.text.toString()
        val host = editServerIP.text.toString()
        val port = editPort.text.toString()

        if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
            showMessage("Completa todos los campos de conexión", true)
            return
        }

        showMessage("Conectando...", false)

        val serverUrl = "$protocol://$host:$port"
        Log.d("CONFIG", "Conectando a: $serverUrl")

        // Usar la Application para conectar UNA sola vez
        myApp.connectWebSocket(serverUrl, playerName) { message ->
            processInitialMessage(message, playerName)
        }

        // Verificar conexión después de 3 segundos
        Thread {
            Thread.sleep(3000)
            runOnUiThread {
                if (myApp.isConnected) {
                    showMessage("Conectado! Esperando confirmación...", false)
                } else {
                    showMessage("No se pudo conectar al servidor", true)
                }
            }
        }.start()
    }

    private fun processInitialMessage(message: String, playerName: String) {
        try {
            val jsonObject = JSONObject(message)
            val type = jsonObject.optString("type", "")

            when (type) {
                "clients" -> {
                    // Guardamos la lista inicial de clientes (no le da tiempo a recibirlo desde la actividad de Choosing)
                    val listArray = jsonObject.optJSONArray("list")
                    if (listArray != null) {
                        initialClientsList = listArray
                        Log.d("CONFIG", "Lista inicial guardada: ${initialClientsList.length()} clientes")
                    }

                    // Conexión exitosa
                    Log.d("CONFIG", "Conexión verificada con lista de clientes")
                    runOnUiThread {
                        showMessage("Conectado! Redirigiendo...", false)
                        val intent = Intent(this, ChoosingActivity::class.java).apply {
                            // Pasar la lista inicial como extra
                            putExtra("initialClients", initialClientsList.toString())
                            putExtra("currentPlayerName", playerName)
                        }
                        startActivity(intent)
                    }
                }
                "error" -> {
                    val errorMsg = jsonObject.optString("message", "")
                    runOnUiThread {
                        showMessage("Error: $errorMsg", true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CONFIG", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun showMessage(message: String, isError: Boolean) {
        runOnUiThread {
            txtMessage.text = message
            if (isError) {
                txtMessage.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            } else {
                txtMessage.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            }
        }
    }
}