package com.joshuastar.windroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import android.os.Bundle
import java.io.PrintWriter
import java.net.Socket
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import android.graphics.Color
import android.widget.Switch
import android.widget.TextView
import android.os.Environment

const val PORT = 1234
private const val REQUEST_MEDIA_PROJECTION = 1003

class MainActivity : ComponentActivity() {
    private var pendingStartMirror = false
    companion object {
        var serverIp: String = ""
    }
    private var shouldFinishAfterPermissions = false
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == "com.joshuastar.windroid.START_MIRROR") {
            Log.d("MIRROR", "Start mirror command received")
            pendingStartMirror = true
        }

        if (intent.action == "com.joshuastar.windroid.STOP_MIRROR") {
            stopScreenMirror()
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        serverIp = intent.getStringExtra("server_ip")
            ?: getSharedPreferences(PREF_NAME, MODE_PRIVATE).getString(PREF_SERVER_IP, "")
                    ?: ""
        super.onCreate(savedInstanceState)

        createUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                shouldFinishAfterPermissions = true
                requestOverlayPermission()
                return
            }
        }

        checkNotificationListenerPermission()
        startClipboardService()
        ConnectionManager.connect(serverIp, PORT,this)
        val filter = IntentFilter().apply {
            addAction("com.joshuastar.windroid.START_MIRROR")
            addAction("com.joshuastar.windroid.STOP_MIRROR")
        }
        registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        // Handle mirror start command from PC
    }
    override fun onResume() {
        super.onResume()

        if (pendingStartMirror) {
            pendingStartMirror = false
            startScreenMirror()
        }
    }
    private fun sendHandshake() {
        val phoneName = Build.MODEL
        val fingerprint = Build.FINGERPRINT.take(16).replace("/", "-")
        Thread {
            try {
                val socket = Socket(serverIp, PORT)
                val out = PrintWriter(socket.getOutputStream(), true)
                out.println("HELLO|$phoneName|$fingerprint")
                out.close()
                socket.close()
            } catch (e: Exception) {
                Log.e("HANDSHAKE", "Failed", e)
            }
        }.start()
    }
    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(commandReceiver)
        } catch (_: Exception) {}

        ConnectionManager.disconnect()
    }
    private fun checkNotificationListenerPermission() {
        val flat = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val enabled = flat?.contains(packageName) == true

        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Notification Access")
                .setMessage("Allow Windroid to read notifications so they can be forwarded to your PC.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
                .setNegativeButton("Skip", null)
                .show()
        }
    }
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.joshuastar.windroid.START_MIRROR" -> startScreenMirror()
                "com.joshuastar.windroid.STOP_MIRROR"  -> stopScreenMirror()
            }
        }
    }



    private fun createUI() {

        val prefs = getSharedPreferences("features", MODE_PRIVATE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val title = TextView(this).apply {
            text = "Windroid"
            textSize = 22f
            setTextColor(Color.BLACK)
        }
        layout.addView(title)

        val ipText = TextView(this).apply {
            text = "Connected PC: $serverIp"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 40)
        }
        layout.addView(ipText)

        val forgetBtn = Button(this).apply {
            text = "Forget PC & Reconnect"
            setOnClickListener {
                getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().clear().apply()
                startActivity(Intent(this@MainActivity, DiscoveryActivity::class.java))
                finish()
            }
        }
        layout.addView(forgetBtn)

        val mouseBtn = Button(this).apply {
            text = "Mouse Control"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, MouseActivity::class.java))
            }
        }

        layout.addView(mouseBtn)

        val lockBtn = Button(this).apply {
            text = "Lock PC"
            setOnClickListener {
                ConnectionManager.send("CMD:LOCK_PC")
                Toast.makeText(this@MainActivity, "Locking PC...", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(lockBtn)

        val signOutBtn = Button(this).apply {
            text = "Sign Out PC"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sign Out")
                    .setMessage("This will close all apps and sign out. Continue?")
                    .setPositiveButton("Sign Out") { _, _ ->
                        ConnectionManager.send("CMD:SIGNOUT_PC")
                        Toast.makeText(this@MainActivity, "Signing out PC...", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        layout.addView(signOutBtn)

        val controllerBtn = Button(this).apply {
            text = "Game Controller"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GameControllerActivity::class.java))
            }
        }
        layout.addView(controllerBtn)

        val fileBtn = Button(this).apply {
            text = "File Access"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PcFileBrowserActivity::class.java))
            }
        }
        layout.addView(fileBtn)

        setContentView(layout)
    }
    private fun startScreenMirror() {

        // Permission invalid → request again
        if (ScreenMirrorService.resultCode == 0 ||
            ScreenMirrorService.resultData == null) {

            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE)
                        as android.media.projection.MediaProjectionManager

            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            )

            return
        }

        // Permission valid → start directly
        val intent = Intent(this, ScreenMirrorService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)
    }

    private fun stopScreenMirror() {
        val intent = Intent(this, ScreenMirrorService::class.java).apply {
            action = "STOP_MIRROR"
        }
        startService(intent)
        Toast.makeText(this, "Screen mirror stopped", Toast.LENGTH_SHORT).show()
    }

    private fun testClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip

        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isBlank()) {
            Toast.makeText(this, "Clipboard is blank", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Sending: ${text.take(30)}...", Toast.LENGTH_SHORT).show()
        ConnectionManager.send("CLIPBOARD=$text")
    }

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Display Over Other Apps Permission")
            .setMessage("This app needs permission to display over other apps to read clipboard in the background.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1002)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Overlay permission is required for clipboard sync", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                ScreenMirrorService.resultCode = resultCode
                ScreenMirrorService.resultData = data
                val intent = Intent(this, ScreenMirrorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "✓ Screen mirroring started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "✗ Screen mirroring permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == 1002) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    startClipboardService()
                    if (shouldFinishAfterPermissions) {
                        Toast.makeText(this, "✓ All permissions granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Permission denied. Clipboard sync may not work.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    requestOverlayPermission()
                    return
                }
            }
            startClipboardService()
        }
    }

    private fun startClipboardService() {
        val intent = Intent(this, ClipboardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

