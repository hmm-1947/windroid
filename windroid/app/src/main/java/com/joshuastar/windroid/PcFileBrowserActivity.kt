package com.joshuastar.windroid

import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity

class PcFileBrowserActivity : ComponentActivity() {
    private lateinit var listView: ListView
    private var currentPath = "C:\\Users"
    private val pathHistory = mutableListOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val backButton = Button(this)
        backButton.text = "⬅ Back"
        listView = ListView(this)
        layout.addView(backButton)
        layout.addView(listView)
        setContentView(layout)
        BrowserHolder.activity = this
        backButton.setOnClickListener {
            if (pathHistory.isNotEmpty()) {
                val previous = pathHistory.removeAt(pathHistory.lastIndex)
                requestFolder(previous)
            } else {
                finish()
            }
        }
        requestFolder(currentPath)
    }

    override fun onDestroy() {
        super.onDestroy()
        BrowserHolder.activity = null
    }

    private fun requestFolder(path: String) {
        currentPath = path
        ConnectionManager.send("FILE_REQ_LIST|$path")
    }

    fun updateList(items: List<Pair<String, String>>) {
        val display = items.map {
            val icon = if (it.second == "DIR") "📁" else "📄"
            "$icon ${it.first}"
        }

        runOnUiThread {
            listView.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                display
            )

            listView.setOnItemClickListener { _, _, position, _ ->
                val (name, type) = items[position]
                if (type == "DIR") {
                    pathHistory.add(currentPath)
                    requestFolder("$currentPath\\$name")
                } else {
                    ConnectionManager.send("FILE_REQ_DOWNLOAD|$currentPath\\$name")
                    Toast.makeText(this, "Downloading $name", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (pathHistory.isNotEmpty()) {
            val previous = pathHistory.removeAt(pathHistory.lastIndex)
            requestFolder(previous)
        } else {
            super.onBackPressed()
        }
    }
}