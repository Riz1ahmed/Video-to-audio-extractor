package com.learner.videotoaudioconverter.utils.converters

import android.content.Context
import android.media.*
import android.net.Uri
import android.util.Log
import com.learner.videotoaudioconverter.MainActivity
import com.learner.videotoaudioconverter.utils.StorageUtils
import java.io.File
import java.nio.ByteBuffer

object MediaAPIUtils {

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