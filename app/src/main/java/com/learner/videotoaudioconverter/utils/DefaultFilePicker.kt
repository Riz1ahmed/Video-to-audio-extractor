package com.learner.videotoaudioconverter.utils

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class DefaultFilePicker(activity: ComponentActivity) {

    fun openPicker(mimeType: MimeType, block: (Uri) -> Unit) {
        this.block = block
        this.mimeType = mimeType
        pickerLauncher.launch(mediaIntent)
    }


    private var mimeType = MimeType.VIDEO
    private val mediaIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).also {
        it.addCategory(Intent.CATEGORY_OPENABLE)
        it.type = mimeType.mime
        it.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    private val pickerLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) it.data?.data?.let { videoUri ->
                block?.invoke(videoUri)
            }
        }

    private var block: ((Uri) -> Unit)? = null

}