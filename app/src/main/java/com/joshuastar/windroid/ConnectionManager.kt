package com.joshuastar.windroid

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.PrintWriter
import java.net.Socket
import android.os.Build

object ConnectionManager {

    private const val TAG = "ConnectionManager"
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var running = false
    private var appContext: Context? = null
    fun isConnected(): Boolean {
        return socket?.isConnected == true &&
                socket?.isClosed == false &&
                writer != null
    }
    fun connect(ip: String, port: Int, context: Context? = null) {
        appContext = context?.applicationContext
        if (running && isConnected()) return
        if (running) {
            // was running but disconnected — reset
            running = false
            try { socket?.close() } catch (e: Exception) {}
            socket = null
            writer = null
        }
        running = true

        Thread {
            while (running) {
                try {
                    Log.d(TAG, "Connecting to $ip:$port...")
                    socket = Socket(ip, port)
                    writer = PrintWriter(socket!!.getOutputStream(), true)

                    // Send handshake immediately on connect
                    val phoneName = Build.MODEL
                    val fingerprint = Build.FINGERPRINT.take(16).replace("/", "-")
                    writer!!.println("HELLO|$phoneName|$fingerprint")

                    PhoneStatusSender.start(appContext!!)
                    FlashlightController.init(appContext!!)

                    Log.d(TAG, "Connected and handshake sent")

                    val prefs = appContext?.getSharedPreferences("features", Context.MODE_PRIVATE)

                    // Keep reading any server responses (keeps connection alive)
                    val reader = socket!!.getInputStream().bufferedReader()
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("CMD:")) {
                            handleCommand(line.substring(4))
                        } else if (line.startsWith("CLIPBOARD=")) {
                            val text = line.removePrefix("CLIPBOARD=")
                            val ctx = appContext ?: continue
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("windroid", text)
                            clipboard.setPrimaryClip(clip)
                            Log.d(TAG, "PC clipboard set on Android: ${text.take(30)}")
                        } else if (line.startsWith("MEDIA=")) {
                            val json = line.removePrefix("MEDIA=")
                            val ctx = appContext ?: continue
                            Log.d("MEDIA", "Received: $json")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (MediaNotificationManager.mediaSession == null) {
                                    MediaNotificationManager.init(ctx)
                                }
                                MediaNotificationManager.handle(ctx, json)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection lost: ${e.message}")
                    writer = null
                    socket = null
                }

                if (running) {
                    Log.d(TAG, "Reconnecting in 3s...")
                    Thread.sleep(3000)
                }
            }
        }.start()
    }

    fun send(message: String) {
        Thread {
            try {
                var attempts = 0
                while (!isConnected() && attempts < 10) {
                    Thread.sleep(300)
                    attempts++
                }

                if (isConnected()) {
                    writer?.println(message)
                    Log.d(TAG, "Sent: $message")
                } else {
                    Log.w(TAG, "Failed to send (not connected): $message")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}")
            }
        }.start()
    }


    fun disconnect() {
        running = false
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        writer = null
    }
    private fun handleCommand(command: String) {

        Log.d(TAG, "Received command: $command")

        when (command) {

            "START_MIRROR" -> {
                val ctx = appContext ?: return

                val intent = Intent(ctx, MainActivity::class.java).apply {
                    action = "com.joshuastar.windroid.START_MIRROR"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                ctx.startActivity(intent)
            }

            "STOP_MIRROR" -> {
                val ctx = appContext ?: return

                val stopIntent = Intent(ctx, ScreenMirrorService::class.java).apply {
                    action = "STOP_MIRROR"
                }

                ctx.startService(stopIntent)
            }

            "FLASHLIGHT_ON" -> {
                FlashlightController.set(true)
            }

            "FLASHLIGHT_OFF" -> {
                FlashlightController.set(false)
            }
        }
    }
}