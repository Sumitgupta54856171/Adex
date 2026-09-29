package com.example.a_dex.network

import com.example.a_dex.model.InputEvent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.net.ServerSocket
import java.net.Socket

class InputServer {
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
    private val gson = Gson()
    var virtualDisplayId = 0
    private var newProcessMethod: Method? = null

    init {
        try {
            newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod?.isAccessible = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Shizuku newProcess method")
        }
    }

    suspend fun startServer() = withContext(Dispatchers.IO) {
        isRunning = true
        try {
            serverSocket = ServerSocket(8766)
            Timber.d("Input Server listening on 8766")
            while (isRunning) {
                val client = serverSocket?.accept() ?: break
                Timber.d("TV Client connected for input!")
                handleClient(client)
            }
        } catch (e: Exception) {
            Timber.e(e, "Input server error")
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            while (isRunning) {
                val line = reader.readLine() ?: break
                val event = gson.fromJson(line, InputEvent::class.java)
                injectEvent(event)
            }
        } catch (e: Exception) {
            Timber.e("Input client disconnected")
        } finally {
            try { client.close() } catch (e: Exception) {}
        }
    }

    private fun injectEvent(event: InputEvent) {
        if (newProcessMethod == null) return
        try {
            val cmd: Array<String> = when (event.type) {
                "key" -> {
                    val code = event.keyCode ?: return
                    val action = event.action ?: 0
                    Timber.d("Phone injectEvent: keyCode=$code, action=$action, displayId=$virtualDisplayId")
                    // Android's "input keyevent" command performs a full KEY_DOWN + KEY_UP press.
                    // Filter out ACTION_UP (action == 1) so keys aren't injected twice (e.g. typing "mm" instead of "m").
                    if (action != 0) return
                    arrayOf("input", "-d", "$virtualDisplayId", "keyevent", "$code")
                }
                "mouse_move" -> {
                    val x = event.x ?: return
                    val y = event.y ?: return
                    arrayOf("input", "-d", "$virtualDisplayId", "motionevent", "MOVE", "$x", "$y")
                }
                "mouse_click" -> {
                    val x = event.x ?: return
                    val y = event.y ?: return
                    val action = event.action ?: return
                    val actStr = if (action == 0) "DOWN" else "UP"
                    arrayOf("input", "-d", "$virtualDisplayId", "motionevent", actStr, "$x", "$y")
                }
                else -> return
            }

            val process = newProcessMethod?.invoke(null, cmd, null, null) as? Process
            process?.waitFor()
        } catch (e: Exception) {
            Timber.e(e, "Failed to inject event")
        }
    }

    fun stopServer() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }
}
