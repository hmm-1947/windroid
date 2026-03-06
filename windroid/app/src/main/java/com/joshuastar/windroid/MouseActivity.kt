package com.joshuastar.windroid

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

class MouseActivity : Activity() {
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var leftHeld = false
    private val MOVE_THRESHOLD = 8f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
        }
        val backBtn = Button(this).apply {
            text = "← Back"
            textSize = 18f
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)

        val touchArea = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(0xFF1A1A2E.toInt())

            setOnTouchListener { _, event ->
                handleTouchArea(event)
                true
            }
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val leftBtn = Button(this).apply {
            text = "Left Click"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, 200, 1f)

            setOnTouchListener { _, event ->
                handleLeftButton(event)
                true
            }
        }

        val rightBtn = Button(this).apply {
            text = "Right Click"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, 200, 1f)

            setOnClickListener {
                ConnectionManager.send("MOUSE_RIGHT_CLICK")
            }
        }

        bottomBar.addView(leftBtn)
        bottomBar.addView(rightBtn)

        root.addView(topBar)
        root.addView(touchArea)
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun handleLeftButton(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                leftHeld = true
                ConnectionManager.send("MOUSE_DOWN")
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                leftHeld = false
                ConnectionManager.send("MOUSE_UP")
            }
        }
    }
    private fun handleTouchArea(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            handleScroll(event)
            return
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                moved = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y

                if (!moved && (abs(event.x - downX) > MOVE_THRESHOLD ||
                            abs(event.y - downY) > MOVE_THRESHOLD)) {
                    moved = true
                }

                if (moved) {
                    ConnectionManager.send("MOUSE_MOVE:${dx.toInt()},${dy.toInt()}")
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    // Tap on touch area = left click
                    ConnectionManager.send("MOUSE_CLICK")
                }
                moved = false
            }
        }
    }
    private var lastScrollY = 0f
    private var scrolling = false

    private fun handleScroll(event: MotionEvent) {
        when (event.actionMasked) {

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastScrollY = event.getY(0)
                scrolling = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scrolling) return
                val currentY = event.getY(0)
                val dy = currentY - lastScrollY
                lastScrollY = currentY
                ConnectionManager.send("MOUSE_SCROLL:${(-dy).toInt()}")
            }

            MotionEvent.ACTION_POINTER_UP -> {
                scrolling = false
            }
        }
    }
}