package com.learner.videotoaudioconverter.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.learner.videotoaudioconverter.MainActivity.Companion.TAG
import java.io.File
import java.nio.ByteBuffer

object MediaUtils {
    fun extractAudio(context: Context, videoUri: Uri, block: (File) -> Unit) {
        MediaAPIUtils.getSpecificFile(context, videoUri, "audio/") { extractor, idx ->
            Log.d(TAG, "extractAudio: audio found")
            extractor.selectTrack(idx)
            val audioFormat = extractor.getTrackFormat(idx)
            val audioFile = getAudioFilePath(context)
            val muxer =
                MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val idxInMux = muxer.addTrack(audioFormat)//Add audio file to muxer
            muxer.start()

            val bufferSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            else DEFAULT_BUFFER_SIZE
            Log.d(TAG, "extractAudio: bufferSize=$bufferSize")
            val byteBuf = ByteBuffer.allocate(725)
            val bufferInfo = MediaCodec.BufferInfo()
            //Coping data
            while (extractor.readSampleData(byteBuf, 0).also { bufferInfo.size = it } > 0) {
                Log.d(TAG, "extractAudio: bufferSize=${bufferInfo.size}")
                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(idxInMux, byteBuf, bufferInfo)
                extractor.advance()
            }
            //Release
            bufferInfo.size = 0
            muxer.stop()
            muxer.release()

            block(audioFile)//Notify done
        }
    }

    fun getAudioFilePath(context: Context): File {
        val file = File(context.filesDir.absolutePath + "/audio.mp3")
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