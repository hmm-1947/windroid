package com.joshuastar.windroid

import android.os.Environment
import java.io.File
import android.util.Base64

object FileAccessManager {

    fun handleCommand(message: String) {

        // PC requesting Android folder
        if (message.startsWith("ANDROID_REQ_LIST|")) {

            val path = message.removePrefix("ANDROID_REQ_LIST|")
            val dir = File(path)

            if (!dir.exists() || !dir.isDirectory) return

            val builder = StringBuilder("ANDROID_RES_LIST|")

            dir.listFiles()?.forEach {
                builder.append(it.name)
                    .append(",")
                    .append(if (it.isDirectory) "DIR" else "FILE")
                    .append(";")
            }

            ConnectionManager.send(builder.toString())
        }

        // PC requesting Android file
        else if (message.startsWith("ANDROID_REQ_DOWNLOAD|")) {

            val path = message.removePrefix("ANDROID_REQ_DOWNLOAD|")
            val file = File(path)

            if (!file.exists() || !file.isFile) return

            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            ConnectionManager.send(
                "ANDROID_RES_FILE|${file.name}|$base64"
            )
        }

        // Android receiving laptop file
        else if (message.startsWith("FILE_RES_FILE|")) {

            val parts = message.split("|", limit = 3)
            val name = parts[1]
            val base64 = parts[2]

            val bytes = Base64.decode(base64, Base64.DEFAULT)

            val file = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                ),
                name
            )

            file.writeBytes(bytes)
        }
    }
}