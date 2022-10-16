package com.learner.videotoaudioconverter.utils.converters.video_resize

import android.content.Context
import android.media.*
import android.net.Uri
import android.view.Surface
import com.learner.videotoaudioconverter.utils.StorageUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference


class VideoResolutionChanger(private val context: Context) {
    private var mWidth = 1280
    private var mHeight = 720
    private var mOutputFile: String? = null

    //private lateinit var mInputFile: String
    lateinit var videoUri: Uri

    @Throws(Throwable::class)
    fun changeResolution(videoUri: Uri): String? {
        this.videoUri = videoUri

        mOutputFile = StorageUtils.getVideoFile(context, "out").absolutePath
        ChangerWrapper.changeResolutionInSeparatedThread(this)
        return mOutputFile
    }

    private class ChangerWrapper private constructor(private val mChanger: VideoResolutionChanger) :
        Runnable {
        private var mThrowable: Throwable? = null
        override fun run() {
            try {
                mChanger.prepareAndChangeResolution()
            } catch (th: Throwable) {
                mThrowable = th
            }
        }

        companion object {
            @Throws(Throwable::class)
            fun changeResolutionInSeparatedThread(changer: VideoResolutionChanger) {
                val wrapper = ChangerWrapper(changer)
                val th = Thread(wrapper, ChangerWrapper::class.java.simpleName)
                th.start()
                th.join()
                if (wrapper.mThrowable != null) throw wrapper.mThrowable!!
            }
        }
    }

    @Throws(Exception::class)
    private fun prepareAndChangeResolution() {
        var exception: Exception? = null
        val videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE) ?: return
        val audioCodecInfo = selectCodec(OUTPUT_AUDIO_MIME_TYPE) ?: return
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var outputSurface: OutputSurface? = null
        var videoDecoder: MediaCodec? = null
        var audioDecoder: MediaCodec? = null
        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: InputSurface? = null
        try {
            videoExtractor = createExtractor()
            val videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor)
            val inputFormat = videoExtractor.getTrackFormat(videoInputTrack)
            //Get w & h
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, videoUri)
            var inputWidth: Int
            var inputHeight: Int
            try {
                inputWidth =
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
                inputHeight =
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
            } catch (e: Exception) {
                val thumbnail = mmr.frameAtTime
                inputWidth = thumbnail!!.width
                inputHeight = thumbnail.height
                thumbnail.recycle()
            }

            //If input regu & myRegu not same then swap
            if ((inputWidth > inputHeight) != (mWidth > mHeight)) {
                val w = mWidth
                mWidth = mHeight
                mHeight = w
            }

            val outputVideoFormat =
                MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight)
            outputVideoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT
            )
            outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE)
            outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE)
            outputVideoFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL
            )
            val inputSurfaceReference = AtomicReference<Surface>()
            videoEncoder = createVideoEncoder(
                videoCodecInfo, outputVideoFormat, inputSurfaceReference
            )
            inputSurface = InputSurface(inputSurfaceReference.get())
            inputSurface.makeCurrent()
            outputSurface = OutputSurface()
            videoDecoder = createVideoDecoder(inputFormat, outputSurface.surface)
            audioExtractor = createExtractor()
            val audioInputTrack = getAndSelectAudioTrackIndex(audioExtractor)
            val inputAudioFormat = audioExtractor.getTrackFormat(audioInputTrack)
            val outputAudioFormat = MediaFormat.createAudioFormat(
                inputAudioFormat.getString(MediaFormat.KEY_MIME)!!,
                inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            )
            outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_AUDIO_BIT_RATE)
            outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE)
            audioEncoder = createAudioEncoder(audioCodecInfo, outputAudioFormat)
            audioDecoder = createAudioDecoder(inputAudioFormat)
            muxer = MediaMuxer(mOutputFile!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            changeResolution(
                videoExtractor, audioExtractor,
                videoDecoder, videoEncoder,
                audioDecoder, audioEncoder,
                muxer, inputSurface, outputSurface
            )
        } finally {
            try {
                videoExtractor?.release()
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                audioExtractor?.release()
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                if (videoDecoder != null) {
                    videoDecoder.stop()
                    videoDecoder.release()
                }
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                outputSurface?.release()
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                if (videoEncoder != null) {
                    videoEncoder.stop()
                    videoEncoder.release()
                }
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                if (audioDecoder != null) {
                    audioDecoder.stop()
                    audioDecoder.release()
                }
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                if (audioEncoder != null) {
                    audioEncoder.stop()
                    audioEncoder.release()
                }
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
            try {
                inputSurface?.release()
            } catch (e: Exception) {
                if (exception == null) exception = e
            }
        }
        if (exception != null) throw exception
    }

    @Throws(IOException::class)
    private fun createExtractor(): MediaExtractor {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, videoUri, null)
        return extractor
    }

    @Throws(IOException::class)
    private fun createVideoDecoder(inputFormat: MediaFormat, surface: Surface?): MediaCodec {
        val decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat)!!)
        decoder.configure(inputFormat, surface, null, 0)
        decoder.start()
        return decoder
    }

    @Throws(IOException::class)
    private fun createVideoEncoder(
        codecInfo: MediaCodecInfo, format: MediaFormat,
        surfaceReference: AtomicReference<Surface>
    ): MediaCodec {
        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surfaceReference.set(encoder.createInputSurface())
        encoder.start()
        return encoder
    }

    @Throws(IOException::class)
    private fun createAudioDecoder(inputFormat: MediaFormat): MediaCodec {
        val decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat)!!)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        return decoder
    }

    @Throws(IOException::class)
    private fun createAudioEncoder(codecInfo: MediaCodecInfo, format: MediaFormat): MediaCodec {
        val encoder = MediaCodec.createByCodecName(codecInfo.name)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        return encoder
    }

    private fun getAndSelectVideoTrackIndex(extractor: MediaExtractor?): Int {
        for (index in 0 until extractor!!.trackCount) {
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index)
                return index
            }
        }
        return -1
    }

    private fun getAndSelectAudioTrackIndex(extractor: MediaExtractor?): Int {
        for (index in 0 until extractor!!.trackCount) {
            if (isAudioFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index)
                return index
            }
        }
        return -1
    }

    private fun changeResolution(
        videoExtractor: MediaExtractor?, audioExtractor: MediaExtractor?,
        videoDecoder: MediaCodec?, videoEncoder: MediaCodec?,
        audioDecoder: MediaCodec?, audioEncoder: MediaCodec?,
        muxer: MediaMuxer,
        inputSurface: InputSurface, outputSurface: OutputSurface
    ) {
        var videoDecoderInputBuffers: Array<ByteBuffer?>? = null
        var videoDecoderOutputBuffers: Array<ByteBuffer?>? = null
        var videoEncoderOutputBuffers: Array<ByteBuffer?>? = null
        var videoDecoderOutputBufferInfo: MediaCodec.BufferInfo? = null
        var videoEncoderOutputBufferInfo: MediaCodec.BufferInfo? = null
        videoDecoderInputBuffers = videoDecoder!!.inputBuffers
        videoDecoderOutputBuffers = videoDecoder.outputBuffers
        videoEncoderOutputBuffers = videoEncoder!!.outputBuffers
        videoDecoderOutputBufferInfo = MediaCodec.BufferInfo()
        videoEncoderOutputBufferInfo = MediaCodec.BufferInfo()
        var audioDecoderInputBuffers: Array<ByteBuffer?>? = null
        var audioDecoderOutputBuffers: Array<ByteBuffer>? = null
        var audioEncoderInputBuffers: Array<ByteBuffer>? = null
        var audioEncoderOutputBuffers: Array<ByteBuffer?>? = null
        var audioDecoderOutputBufferInfo: MediaCodec.BufferInfo? = null
        var audioEncoderOutputBufferInfo: MediaCodec.BufferInfo? = null
        audioDecoderInputBuffers = audioDecoder!!.inputBuffers
        audioDecoderOutputBuffers = audioDecoder.outputBuffers
        audioEncoderInputBuffers = audioEncoder!!.inputBuffers
        audioEncoderOutputBuffers = audioEncoder.outputBuffers
        audioDecoderOutputBufferInfo = MediaCodec.BufferInfo()
        audioEncoderOutputBufferInfo = MediaCodec.BufferInfo()
        var decoderOutputVideoFormat: MediaFormat? = null
        var decoderOutputAudioFormat: MediaFormat? = null
        var encoderOutputVideoFormat: MediaFormat? = null
        var encoderOutputAudioFormat: MediaFormat? = null
        var outputVideoTrack = -1
        var outputAudioTrack = -1
        var videoExtractorDone = false
        var videoDecoderDone = false
        var videoEncoderDone = false
        var audioExtractorDone = false
        var audioDecoderDone = false
        var audioEncoderDone = false
        var pendingAudioDecoderOutputBufferIndex = -1
        var muxing = false
        while (!videoEncoderDone || !audioEncoderDone) {
            while (!videoExtractorDone
                && (encoderOutputVideoFormat == null || muxing)
            ) {
                val decoderInputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                val decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex]
                val size = videoExtractor!!.readSampleData(decoderInputBuffer!!, 0)
                val presentationTime = videoExtractor.sampleTime
                if (size >= 0) {
                    videoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        videoExtractor.sampleFlags
                    )
                }
                videoExtractorDone = !videoExtractor.advance()
                if (videoExtractorDone) videoDecoder.queueInputBuffer(
                    decoderInputBufferIndex,
                    0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                break
            }
            while (!audioExtractorDone
                && (encoderOutputAudioFormat == null || muxing)
            ) {
                val decoderInputBufferIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                val decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex]
                val size = audioExtractor!!.readSampleData(decoderInputBuffer!!, 0)
                val presentationTime = audioExtractor.sampleTime
                if (size >= 0) audioDecoder.queueInputBuffer(
                    decoderInputBufferIndex, 0, size,
                    presentationTime, audioExtractor.sampleFlags
                )
                audioExtractorDone = !audioExtractor.advance()
                if (audioExtractorDone) audioDecoder.queueInputBuffer(
                    decoderInputBufferIndex, 0, 0,
                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                break
            }
            while (!videoDecoderDone
                && (encoderOutputVideoFormat == null || muxing)
            ) {
                val decoderOutputBufferIndex = videoDecoder.dequeueOutputBuffer(
                    videoDecoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoDecoderOutputBuffers = videoDecoder.outputBuffers
                    break
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputVideoFormat = videoDecoder.outputFormat
                    break
                }
                val decoderOutputBuffer = videoDecoderOutputBuffers!![decoderOutputBufferIndex]
                if (videoDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    != 0
                ) {
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                    break
                }
                val render = videoDecoderOutputBufferInfo.size != 0
                videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render)
                if (render) {
                    outputSurface.awaitNewImage()
                    outputSurface.drawImage()
                    inputSurface.setPresentationTime(
                        videoDecoderOutputBufferInfo.presentationTimeUs * 1000
                    )
                    inputSurface.swapBuffers()
                }
                if ((videoDecoderOutputBufferInfo.flags
                            and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                ) {
                    videoDecoderDone = true
                    videoEncoder.signalEndOfInputStream()
                }
                break
            }
            while (!audioDecoderDone && pendingAudioDecoderOutputBufferIndex == -1 && (encoderOutputAudioFormat == null || muxing)) {
                val decoderOutputBufferIndex = audioDecoder.dequeueOutputBuffer(
                    audioDecoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioDecoderOutputBuffers = audioDecoder.outputBuffers
                    break
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputAudioFormat = audioDecoder.outputFormat
                    break
                }
                val decoderOutputBuffer = audioDecoderOutputBuffers!![decoderOutputBufferIndex]
                if (audioDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    != 0
                ) {
                    audioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                    break
                }
                pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex
                break
            }
            while (pendingAudioDecoderOutputBufferIndex != -1) {
                val encoderInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                val encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex]
                val size = audioDecoderOutputBufferInfo.size
                val presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs
                if (size >= 0) {
                    val decoderOutputBuffer =
                        audioDecoderOutputBuffers!![pendingAudioDecoderOutputBufferIndex]
                            .duplicate()
                    decoderOutputBuffer.position(audioDecoderOutputBufferInfo.offset)
                    decoderOutputBuffer.limit(audioDecoderOutputBufferInfo.offset + size)
                    encoderInputBuffer.position(0)
                    encoderInputBuffer.put(decoderOutputBuffer)
                    audioEncoder.queueInputBuffer(
                        encoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        audioDecoderOutputBufferInfo.flags
                    )
                }
                audioDecoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false)
                pendingAudioDecoderOutputBufferIndex = -1
                if ((audioDecoderOutputBufferInfo.flags
                            and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                ) audioDecoderDone = true
                break
            }
            while (!videoEncoderDone
                && (encoderOutputVideoFormat == null || muxing)
            ) {
                val encoderOutputBufferIndex = videoEncoder.dequeueOutputBuffer(
                    videoEncoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoEncoderOutputBuffers = videoEncoder.outputBuffers
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputVideoFormat = videoEncoder.outputFormat
                    break
                }
                val encoderOutputBuffer = videoEncoderOutputBuffers!![encoderOutputBufferIndex]
                if (videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    != 0
                ) {
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                    break
                }
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(
                        outputVideoTrack, encoderOutputBuffer!!, videoEncoderOutputBufferInfo
                    )
                }
                if (videoEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    != 0
                ) {
                    videoEncoderDone = true
                }
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                break
            }
            while (!audioEncoderDone
                && (encoderOutputAudioFormat == null || muxing)
            ) {
                val encoderOutputBufferIndex = audioEncoder.dequeueOutputBuffer(
                    audioEncoderOutputBufferInfo, TIMEOUT_USEC.toLong()
                )
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioEncoderOutputBuffers = audioEncoder.outputBuffers
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputAudioFormat = audioEncoder.outputFormat
                    break
                }
                val encoderOutputBuffer = audioEncoderOutputBuffers!![encoderOutputBufferIndex]
                if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    != 0
                ) {
                    audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                    break
                }
                if (audioEncoderOutputBufferInfo.size != 0) muxer.writeSampleData(
                    outputAudioTrack, encoderOutputBuffer!!, audioEncoderOutputBufferInfo
                )
                if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    != 0
                ) audioEncoderDone = true
                audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                break
            }
            if (!muxing && encoderOutputAudioFormat != null
                && encoderOutputVideoFormat != null
            ) {
                outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat)
                outputAudioTrack = muxer.addTrack(encoderOutputAudioFormat)
                muxer.start()
                muxing = true
            }
        }
    }

    companion object {
        private const val TIMEOUT_USEC = 10000
        private const val OUTPUT_VIDEO_MIME_TYPE = "video/avc"
        private const val OUTPUT_VIDEO_BIT_RATE = 2048 * 1024
        private const val OUTPUT_VIDEO_FRAME_RATE = 30
        private const val OUTPUT_VIDEO_IFRAME_INTERVAL = 10
        private const val OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        private const val OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm"
        private const val OUTPUT_AUDIO_CHANNEL_COUNT = 2
        private const val OUTPUT_AUDIO_BIT_RATE = 128 * 1024
        private const val OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectHE
        private const val OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100
        private fun isVideoFormat(format: MediaFormat): Boolean {
            return getMimeTypeFor(format)!!.startsWith("video/")
        }

        private fun isAudioFormat(format: MediaFormat): Boolean {
            return getMimeTypeFor(format)!!.startsWith("audio/")
        }

        private fun getMimeTypeFor(format: MediaFormat): String? {
            return format.getString(MediaFormat.KEY_MIME)
        }

        private fun selectCodec(mimeType: String): MediaCodecInfo? {
            val numCodecs = MediaCodecList.getCodecCount()
            for (i in 0 until numCodecs) {
                val codecInfo = MediaCodecList.getCodecInfoAt(i)
                if (!codecInfo.isEncoder) continue
                val types = codecInfo.supportedTypes
                for (j in types.indices)
                    if (types[j].equals(mimeType, ignoreCase = true))
                        return codecInfo
            }
            return null
        }
    }
}