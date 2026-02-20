package com.joshuastar.windroid

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

const val MIRROR_PORT = 3333
private const val MIRROR_CHANNEL_ID = "screen_mirror"
private const val ACTION_STOP_MIRROR = "STOP_MIRROR"

class ScreenMirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var imageHandlerThread: HandlerThread? = null
    private var imageHandler: Handler? = null
    private val isSending = AtomicBoolean(false) // Prevent frame queue buildup

    companion object {
        var resultCode: Int = 0
        var resultData: Intent? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, createNotification())

        // Create background thread for image processing
        imageHandlerThread = HandlerThread("ImageProcessing").apply {
            start()
        }
        imageHandler = Handler(imageHandlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == ACTION_STOP_MIRROR) {
            stopMirroring()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            startMirroring()
        }

        return START_NOT_STICKY
    }

    private fun startMirroring() {
        if (resultCode == 0 || resultData == null) {
            Log.e("MIRROR", "No media projection permission")
            stopSelf()
            return
        }

        try {
            val mediaProjectionManager =
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData!!)

            // Register callback BEFORE creating virtual display (required on Android 14+)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d("MIRROR", "MediaProjection stopped")
                    stopMirroring()
                }
            }, handler)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // Reduce resolution more aggressively for better performance
            val scaledWidth = width / 3  // Changed from /2 to /3
            val scaledHeight = height / 3

            imageReader = ImageReader.newInstance(
                scaledWidth,
                scaledHeight,
                PixelFormat.RGBA_8888,
                2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenMirror",
                scaledWidth, scaledHeight, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            isRunning = true

            imageReader?.setOnImageAvailableListener({ reader ->
                // Skip frame if still sending previous one (prevents queue buildup)
                if (isSending.get()) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        isSending.set(true)
                        // Convert to bitmap BEFORE closing the image
                        val bitmap = convertToBitmap(image)
                        // Now we can close the image
                        image.close()
                        // Send the bitmap in background thread
                        sendBitmapToServer(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("MIRROR", "Error processing image: ${e.message}")
                    image?.close()
                    isSending.set(false)
                }
            }, imageHandler) // Use background thread for image processing

            Log.d("MIRROR", "Screen mirroring started (${scaledWidth}x${scaledHeight})")

        } catch (e: Exception) {
            Log.e("MIRROR", "Error starting mirroring", e)
            stopSelf()
        }
    }

    private fun sendBitmapToServer(bitmap: Bitmap) {
        Thread {
            var socket: Socket? = null
            try {
                val outputStream = ByteArrayOutputStream()

                // Lower quality for better performance
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream) // Changed from 70 to 50
                val jpegData = outputStream.toByteArray()

                socket = Socket(MainActivity.serverIp , MIRROR_PORT)
                socket.soTimeout = 1000 // Reduced timeout
                socket.tcpNoDelay = true // Disable Nagle's algorithm for lower latency

                val dos = DataOutputStream(socket.outputStream)

                // Send frame header: "FRAME:" + size
                dos.writeBytes("FRAME:")
                dos.writeInt(jpegData.size)
                dos.write(jpegData)
                dos.flush()

            } catch (e: Exception) {
                // Silent fail to reduce log spam
            } finally {
                bitmap.recycle()
                isSending.set(false)
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }.start()
    }

    private fun convertToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        // Create a copy of the buffer data
        val byteBuffer = ByteBuffer.allocate(buffer.remaining())
        byteBuffer.put(buffer)
        byteBuffer.rewind()

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(byteBuffer)

        // Crop if there's padding
        return if (rowPadding == 0) {
            bitmap
        } else {
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle() // Recycle the original bitmap with padding
            croppedBitmap
        }
    }

    private fun stopMirroring() {

        isRunning = false

        try {
            imageReader?.close()
            imageReader = null
        } catch (_: Exception) {}

        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (_: Exception) {}

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (_: Exception) {}

        try {
            imageHandlerThread?.quitSafely()
            imageHandlerThread = null
            imageHandler = null
        } catch (_: Exception) {}

        // CRITICAL FIX: invalidate permission
        resultCode = 0
        resultData = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d("MIRROR", "Screen mirroring FULLY stopped and permission cleared")
    }
    override fun onDestroy() {
        stopMirroring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MIRROR_CHANNEL_ID,
                "Screen Mirroring",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenMirrorService::class.java).apply {
            action = ACTION_STOP_MIRROR
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MIRROR_CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Your screen is being mirrored to PC")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }
}