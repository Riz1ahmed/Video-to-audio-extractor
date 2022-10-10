package com.learner.videotoaudioconverter.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.learner.videotoaudioconverter.MainActivity.Companion.TAG
import java.io.File

object MediaUtils {
    fun getFileName(context: Context, fileUri: Uri, block: (String) -> Unit) {
        context.contentResolver.query(fileUri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                block(name)
            }
        }
    }

    fun extractAudio(context: Context, videoUri: Uri) {
        getSpecificFile(context, videoUri, "audio/") { extractor, idx ->
            extractor.selectTrack(idx)
            val audioFormat = extractor.getTrackFormat(idx)

            Log.d(TAG, "extractAudio: audio found")
            val audioFile = getAudioFilePath(context)
            val muxer =
                MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.addTrack(audioFormat)//Add audio file to muxer


        }
    }

    private fun getAudioFilePath(context: Context): File {
        val file = File(context.filesDir.absolutePath + "/audio.mp3")
        if (!file.exists()) file.createNewFile()
        return file
    }

    private fun getSpecificFile(
        context: Context, mediaUri: Uri, mimeType: String, callBack: (MediaExtractor, Int) -> Unit
    ) {
        MediaExtractor().use { extractor ->
            extractor.setDataSource(context, mediaUri, null)
            for (i in 0 until extractor.trackCount) {
                val mediaFormat = extractor.getTrackFormat(i)
                val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
                Log.d(TAG, "getSpecificFile: $mime")
                extractor.selectTrack(i)
                if (mime?.startsWith(mimeType) == true) callBack(extractor, i)
            }

            /*val buffer = ByteBuffer.allocate(16)
            while (extractor.readSampleData(buffer, 0) >= 0) {
                //buffer.
                extractor.advance()
            }*/
        }
    }
}

private fun MediaExtractor.use(block: (MediaExtractor) -> Unit) {
    block(this)
    this.release()
}