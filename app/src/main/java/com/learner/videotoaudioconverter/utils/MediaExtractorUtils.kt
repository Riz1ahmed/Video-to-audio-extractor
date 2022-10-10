package com.learner.videotoaudioconverter.utils

import android.annotation.SuppressLint
import android.media.*
import android.util.Log
import com.learner.videotoaudioconverter.MainActivity.Companion.TAG
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max


object MediaExtractorUtils {
    /**
     * @param srcPath the path of source video file.
     * @param dstPath the path of destination video file.
     * @param startMs starting time in milliseconds for trimming. Set to
     * negative if starting from beginning.
     * @param endMs end time for trimming in milliseconds. Set to negative if
     * no trimming at the end.
     * @param useAudio true if keep the audio track from the source.
     * @param useVideo true if keep the video track from the source.
     * @throws IOException
     */
    @SuppressLint("NewApi")
    @Throws(IOException::class)
    fun genVideoUsingMuxer(
        srcPath: String?, dstPath: String?,
        startMs: Int, endMs: Int,
        useAudio: Boolean, useVideo: Boolean
    ) {
        // Set up MediaExtractor to read from the source.
        val extractor = MediaExtractor()
        extractor.setDataSource(srcPath!!)
        val trackCount = extractor.trackCount
        // Set up MediaMuxer for the destination.
        val muxer = MediaMuxer(dstPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Set up the tracks and retrieve the max buffer size for selected tracks.
        val indexMap = HashMap<Int, Int>(trackCount)
        var bufferSize = -1
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            var selectCurrentTrack = false
            if (mime!!.startsWith("audio/") && useAudio) {
                selectCurrentTrack = true
            } else if (mime.startsWith("video/") && useVideo) {
                selectCurrentTrack = true
            }
            if (selectCurrentTrack) {
                extractor.selectTrack(i)
                val dstIndex = muxer.addTrack(format)
                indexMap[i] = dstIndex
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = max(newSize, bufferSize)
                }
            }
        }
        if (bufferSize < 0) bufferSize = DEFAULT_BUFFER_SIZE
        // Set up the orientation and starting time for extractor.
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(srcPath)
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toInt()?.let { degree ->
                muxer.setOrientationHint(degree)
            }
        mmr.release()

        if (startMs > 0) extractor
            .seekTo((startMs * 1000).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        /** Copy the samples from MediaExtractor to MediaMuxer. We will loop
        for copying each sample and stop when we get to the end of the source
        file or exceed the end time of the trimming.*/
        val offset = 0
        var trackIndex = -1
        val dstBuf: ByteBuffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        muxer.start()

        while (extractor.readSampleData(dstBuf, 0).also { bufferInfo.size = it } > 0) {
            val curTime = extractor.sampleTime
            if (endMs > 0 && curTime > endMs * 1000) break//Destined to EndTime as given.

            bufferInfo.presentationTimeUs = curTime
            bufferInfo.flags = extractor.sampleFlags
            trackIndex = extractor.sampleTrackIndex// the i of extractor.selectTrack(i)
            muxer.writeSampleData(indexMap[trackIndex]!!, dstBuf, bufferInfo)
            extractor.advance()
        }

        while (true) {
            bufferInfo.offset = offset
            bufferInfo.size = extractor.readSampleData(dstBuf, offset)
            if (bufferInfo.size < 0) {
                Log.d(TAG, "Saw input EOS.")
                bufferInfo.size = 0
                break
            } else {
                bufferInfo.presentationTimeUs = extractor.sampleTime
                if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {
                    Log.d(TAG, "The current sample is over the trim end time.")
                    break
                } else {
                    bufferInfo.flags = extractor.sampleFlags
                    trackIndex = extractor.sampleTrackIndex
                    muxer.writeSampleData(indexMap[trackIndex]!!, dstBuf, bufferInfo)
                    extractor.advance()
                }
            }
        }
        muxer.stop()
        muxer.release()
        return
    }
}