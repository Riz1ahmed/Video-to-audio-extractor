package com.learner.videotoaudioconverter

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.learner.videotoaudioconverter.databinding.ActivityMainBinding
import com.learner.videotoaudioconverter.utils.DefaultFilePicker
import com.learner.videotoaudioconverter.utils.MimeType
import com.learner.videotoaudioconverter.utils.StorageUtils
import com.learner.videotoaudioconverter.utils.converters.AudioConverter
import com.video_lab.permission_controller.PermissionListener
import com.video_lab.permission_controller.PermissionsController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val defaultFilePicker = DefaultFilePicker(this)
    private var videoUri: Uri? = null
    val mediaPlayer = MediaPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAudioPicker.setOnClickListener {
            PermissionsController.check(
                this, arrayListOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                object : PermissionListener {
                    override fun allGranted() {
                        defaultFilePicker
                            .openPicker(MimeType.VIDEO) { updateData(it) }
                    }
                })
        }

        binding.btnProcess.setOnClickListener {
            if (videoUri == null) Toast.makeText(this, "null Uri", Toast.LENGTH_SHORT).show()
            else {
                //VideoResolutionChanger(this).changeResolution(videoUri!!)
                AudioConverter.extractAudio(this, videoUri!!) { audioFile ->
                    //Log.d(TAG, "onCreate: Extract complete...")
                    binding.txtPercent.text = "Process done"

                    mediaPlayer.setDataSource(audioFile.absolutePath)
                    mediaPlayer.prepare()
                    binding.btnPlay.setOnClickListener {
                        //MediaUtils.playAudio(this, Uri.fromFile(audioFile))
                        mediaPlayer.apply { if (isPlaying) stop() else start() }
                    }
                }
                Toast.makeText(this, "Extracting Audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateData(fileUri: Uri) {
        videoUri = fileUri
        StorageUtils.getFileName(this, fileUri) { name ->
            binding.txtName.text = name
        }
    }

    companion object{
        const val TAG = "xyz"
    }
}