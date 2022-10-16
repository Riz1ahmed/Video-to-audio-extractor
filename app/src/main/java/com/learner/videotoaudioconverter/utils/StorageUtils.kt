package com.learner.videotoaudioconverter.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object StorageUtils {

    fun getAudioFile(context: Context): File {
        val file = File(context.filesDir.absolutePath + "/audio.mp3")
        if (!file.exists()) file.createNewFile()
        return file
    }

    fun getVideoFile(context: Context, name: String): File {
        val file = File(context.filesDir.absolutePath + "/$name.mp4")
        if (!file.exists()) file.createNewFile()
        return file
    }


    fun getFileName(context: Context, fileUri: Uri, block: (String) -> Unit) {
        context.contentResolver.query(fileUri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                block(name)
            }
        }
    }
}