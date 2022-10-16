package com.learner.videotoaudioconverter.utils.converters

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.learner.videotoaudioconverter.utils.StorageUtils
import java.io.File
import java.nio.ByteBuffer

object AudioConverter {

    fun extractAudio(context: Context, videoUri: Uri, block: (File) -> Unit) {
        MediaAPIUtils.getSpecificFile(context, videoUri, "audio/") { extractor, idx ->
            extractor.selectTrack(idx)
            val audioFormat = extractor.getTrackFormat(idx)
            val audioFile = StorageUtils.getAudioFile(context)
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

                /**@param idxInMux in which idx data will write.
                 * @param byteBuf current buffer data/chunk
                 * @param bufferInfo current buffer info/details/metadata*/
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
}