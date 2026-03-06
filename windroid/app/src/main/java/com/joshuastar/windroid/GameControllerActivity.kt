package com.joshuastar.windroid

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import kotlin.math.abs

data class ButtonConfig(
    val key: String,
    val label: String,
    var x: Float,
    var y: Float,
    var size: Float,
    val color: String = "#2A2A2A"
)

class GameControllerActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var gyroEnabled = false
    private var editMode = false

    private lateinit var container: FrameLayout
    private lateinit var editBtn: TextView
    private lateinit var resetBtn: TextView
    private lateinit var gyroToggleBtn: TextView
    private lateinit var gyroLabel: TextView

    private val activeKeys = mutableSetOf<String>()
    private val buttonViews = mutableMapOf<String, View>()

    private var filteredX = 0f
    private val alpha = 0.08f
    private val deadZone = 3.0f
    private val sensitivity = 1.0f

    private val defaultConfigs = listOf(
        ButtonConfig("W","▲",0.12f,0.30f,190f),
        ButtonConfig("A","◀",0.06f,0.55f,190f),
        ButtonConfig("S","▼",0.12f,0.80f,190f),
        ButtonConfig("D","▶",0.22f,0.55f,190f),
        ButtonConfig("SPACE","NOS",0.45f,0.80f,210f,"#1E88E5"),
        ButtonConfig("ENTER","GO",0.65f,0.80f,200f,"#43A047"),
        ButtonConfig("SHIFT","BRK",0.32f,0.80f,180f),
        ButtonConfig("Q","L1",0.75f,0.30f,180f),
        ButtonConfig("E","R1",0.90f,0.30f,180f),
        ButtonConfig("R","RST",0.82f,0.30f,180f,"#C62828"),
        ButtonConfig("F","CAM",0.75f,0.60f,180f),
        ButtonConfig("Z","MAP",0.90f,0.60f,180f),
        ButtonConfig("ESC","ESC",0.92f,0.18f,170f)
    )

    private val configs = mutableListOf<ButtonConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        loadConfigs()

        container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0F0F14"))
        }

        setContentView(container)

        buildTopBar()
        buildButtons()
        buildGyroLabel()
    }

    private fun buildTopBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 80, 32, 24)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#15151A"), Color.parseColor("#101015"))
            )
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP }
        }

        val back = createChip("Back")
        back.setOnClickListener { finish() }

        val title = TextView(this).apply {
            text = "Racing Controller"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }

        gyroToggleBtn = createChip("Gyro OFF")
        gyroToggleBtn.setOnClickListener {
            toggleGyro()
        }

        editBtn = createChip("Edit")
        editBtn.setOnClickListener { toggleEditMode() }

        resetBtn = createChip("Reset").apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#FF6B6B"))
            setOnClickListener { resetToDefault() }
        }

        bar.addView(back)
        bar.addView(title)
        bar.addView(gyroToggleBtn)
        bar.addView(editBtn)
        bar.addView(resetBtn)

        container.addView(bar)
    }

    private fun createChip(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(36,16,36,16)
            background = GradientDrawable().apply {
                cornerRadius = 70f
                setColor(Color.parseColor("#2A2A33"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 18
            layoutParams = lp
        }
    }

    private fun buildButtons() {
        buttonViews.values.forEach { container.removeView(it) }
        buttonViews.clear()

        container.post {
            val w = container.width.toFloat()
            val h = container.height.toFloat()

            configs.forEach { cfg ->
                val btn = createGameButton(cfg,w,h)
                container.addView(btn)
                buttonViews[cfg.key] = btn
            }
        }
    }

    private fun createGameButton(cfg: ButtonConfig, screenW: Float, screenH: Float): View {
        val drawable = GradientDrawable().apply {
            cornerRadius = cfg.size
            setColor(Color.parseColor(cfg.color))
        }

        val btn = TextView(this).apply {
            text = cfg.label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = (cfg.size/7f).coerceIn(18f,32f)
            background = drawable
            elevation = 16f
            layoutParams = FrameLayout.LayoutParams(
                cfg.size.toInt(), cfg.size.toInt()
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = (cfg.x*screenW - cfg.size/2).toInt()
                topMargin = (cfg.y*screenH - cfg.size/2).toInt()
            }
        }

        var dragOffsetX = 0f
        var dragOffsetY = 0f
        var lastPinchDist = -1f

        btn.setOnTouchListener { v, event ->
            if (!editMode) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sendKeyDown(cfg.key)
                        v.scaleX = 0.9f
                        v.scaleY = 0.9f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        sendKeyUp(cfg.key)
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
                true
            } else {
                val lp = v.layoutParams as FrameLayout.LayoutParams
                val screenW = container.width.toFloat()
                val screenH = container.height.toFloat()

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragOffsetX = event.rawX - lp.leftMargin
                        dragOffsetY = event.rawY - lp.topMargin
                        lastPinchDist = -1f
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        // Second finger touched — init pinch
                        lastPinchDist = -1f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount == 1) {
                            // Single finger — drag
                            val newLeft = (event.rawX - dragOffsetX)
                                .toInt().coerceIn(0, (screenW - cfg.size).toInt())
                            val newTop = (event.rawY - dragOffsetY)
                                .toInt().coerceIn(0, (screenH - cfg.size).toInt())

                            lp.leftMargin = newLeft
                            lp.topMargin = newTop
                            v.layoutParams = lp

                            cfg.x = (newLeft + cfg.size / 2) / screenW
                            cfg.y = (newTop + cfg.size / 2) / screenH

                        } else if (event.pointerCount == 2) {
                            // Two fingers — pinch to resize
                            val dx = event.getX(0) - event.getX(1)
                            val dy = event.getY(0) - event.getY(1)
                            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                            if (lastPinchDist > 0) {
                                val scale = dist / lastPinchDist
                                val newSize = (cfg.size * scale).coerceIn(80f, 300f)
                                cfg.size = newSize

                                lp.width = newSize.toInt()
                                lp.height = newSize.toInt()
                                lp.leftMargin = lp.leftMargin
                                    .coerceIn(0, (screenW - newSize).toInt())
                                lp.topMargin = lp.topMargin
                                    .coerceIn(0, (screenH - newSize).toInt())
                                v.layoutParams = lp

                                // Update text size with button size
                                (v as? TextView)?.textSize = (newSize / 7f).coerceIn(18f, 32f)

                                // Update drawable corner radius
                                (v.background as? GradientDrawable)?.cornerRadius = newSize
                            }
                            lastPinchDist = dist
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        lastPinchDist = -1f
                    }
                }
                true
            }
        }

        return btn
    }

    private fun toggleGyro() {
        gyroEnabled = !gyroEnabled
        if (gyroEnabled) {
            gyroToggleBtn.text = "Gyro ON"
            gyroToggleBtn.setTextColor(Color.parseColor("#4CAF50"))
            sensorManager.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME)
        } else {
            gyroToggleBtn.text = "Gyro OFF"
            gyroToggleBtn.setTextColor(Color.WHITE)
            sensorManager.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if(!gyroEnabled) return
        if(event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val rawX = -event.values[1]

        filteredX = alpha * rawX + (1 - alpha) * filteredX

        val steering = -filteredX * sensitivity

        if(abs(steering) < deadZone){
            ConnectionManager.send("STEER:0")
            gyroLabel.text = "Steering: 0"
        } else {
            ConnectionManager.send("STEER:${"%.2f".format(steering)}")
            gyroLabel.text = "Steering: ${"%.1f".format(steering)}"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildGyroLabel() {
        gyroLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#777799"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 50
            }
        }
        container.addView(gyroLabel)
    }

    private fun toggleEditMode() {
        editMode = !editMode
        editBtn.text = if(editMode)"Save" else "Edit"
        resetBtn.visibility = if(editMode)View.VISIBLE else View.GONE
        if(!editMode) saveConfigs()
    }

    private fun resetToDefault() {
        configs.clear()
        defaultConfigs.forEach { configs.add(it.copy()) }
        buildButtons()
    }

    private fun sendKeyDown(key:String){
        if(activeKeys.add(key)) ConnectionManager.send("KEY_DOWN:$key")
    }

    private fun sendKeyUp(key:String){
        if(activeKeys.remove(key)) ConnectionManager.send("KEY_UP:$key")
    }

    private fun saveConfigs(){
        val prefs = getSharedPreferences("controller_layout",MODE_PRIVATE)
        val e = prefs.edit()
        configs.forEach{
            e.putFloat("${it.key}_x",it.x)
            e.putFloat("${it.key}_y",it.y)
            e.putFloat("${it.key}_size",it.size)
        }
        e.apply()
    }

    private fun loadConfigs(){
        configs.clear()
        defaultConfigs.forEach{ configs.add(it.copy()) }
        val prefs = getSharedPreferences("controller_layout",MODE_PRIVATE)
        configs.forEach{
            it.x = prefs.getFloat("${it.key}_x",it.x)
            it.y = prefs.getFloat("${it.key}_y",it.y)
            it.size = prefs.getFloat("${it.key}_size",it.size)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}