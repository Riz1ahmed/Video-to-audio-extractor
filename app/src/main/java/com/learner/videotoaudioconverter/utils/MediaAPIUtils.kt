package com.learner.videotoaudioconverter.utils

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import com.learner.videotoaudioconverter.MainActivity
import java.io.File
import java.nio.ByteBuffer

object MediaAPIUtils {

    fun extractAudio(context: Context, videoUri: Uri, block: (File) -> Unit) {
        getSpecificFile(context, videoUri, "audio/") { extractor, idx ->
            extractor.selectTrack(idx)
            val audioFormat = extractor.getTrackFormat(idx)
            val audioFile = StorageUtils.getAudioFilePath(context)
            val muxer =
                MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val idxInMux = muxer.addTrack(audioFormat)//Add audio file to muxer
            muxer.start()

            val mxBufSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            else DEFAULT_BUFFER_SIZE
            val byteBuf = ByteBuffer.allocate(mxBufSize)
            val bufferInfo = MediaCodec.BufferInfo()
            //Coping data
            while (extractor.readSampleData(byteBuf, 0).also { bufferInfo.size = it } > 0) {
                bufferInfo.offset = 0
                bufferInfo.presentationTimeUs = extractor.sampleTime//current data time
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

    fun getVideoRotatedDegree(context: Context, videoUri: Uri): Int? {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, videoUri)
        val degree = mmr
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt()
        mmr.release()
        return if (degree != null && degree > 0) degree else null
    }

    /**@return the 1st file which [MediaFormat.KEY_MIME] matched with [fileMimeType]*/
    fun getSpecificFile(
        context: Context, mediaUri: Uri, fileMimeType: String,
        callBack: (MediaExtractor, Int) -> Unit
    ) {
        MediaExtractor().use { extractor ->
            extractor.setDataSource(context, mediaUri, null)
            for (i in 0 until extractor.trackCount) {
                val mediaFormat = extractor.getTrackFormat(i)
                val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
                Log.d(MainActivity.TAG, "getSpecificFile: $mime==$fileMimeType")
                if (mime?.startsWith(fileMimeType) == true) {
                    callBack(extractor, i)
                    break
                }
            }
        }
    }
}

fun MediaExtractor.use(block: (MediaExtractor) -> Unit) {
    block(this)
    this.release()
}