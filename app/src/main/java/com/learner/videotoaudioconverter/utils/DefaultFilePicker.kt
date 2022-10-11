package com.learner.videotoaudioconverter.utils

import android.content.Context
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
                getAccessOfThis(activity, videoUri)
                block?.invoke(videoUri)
            }
        }

    private fun getAccessOfThis(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private var block: ((Uri) -> Unit)? = null

}