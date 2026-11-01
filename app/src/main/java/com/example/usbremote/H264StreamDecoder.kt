
package com.example.usbremote

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.io.InputStream
import java.nio.ByteBuffer

class H264StreamDecoder(private val surface: Surface) {
    private var mediaCodec: MediaCodec? = null
    private var isRunning = false
    private var socketInputStream: InputStream? = null
    private var decodeThread: Thread? = null
    private var hasKeyframeReceived = false

    companion object {
        private const val TAG = "H264StreamDecoder"
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
    }

    fun start(inputStream: InputStream) {
        if (isRunning) return
        this.socketInputStream = inputStream
        this.isRunning = true
        this.hasKeyframeReceived = false

        try {
            mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, 
                    VIDEO_WIDTH, 
                    VIDEO_HEIGHT
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger("vendor.qti-video-decoder.avc.enable-low-latency", 1)
                }

                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {}

                    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        try {
                            codec.releaseOutputBuffer(index, true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Lỗi render frame", e)
                        }
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(TAG, "Lỗi MediaCodec", e)
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
                })

                configure(format, surface, null, 0)
                start()
            }

            decodeThread = Thread {
                readSocketAndDecodeLoop()
            }.apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Khởi tạo decoder thất bại", e)
            stop()
        }
    }

    private fun readSocketAndDecodeLoop() {
        val lengthHeader = ByteArray(4)
        val codec = mediaCodec ?: return
        val inputStream = socketInputStream ?: return

        try {
            while (isRunning) {
                if (!readFully(inputStream, lengthHeader, 4)) break
                val dataLength = ByteBuffer.wrap(lengthHeader).int
                if (dataLength <= 0 || dataLength > 10 * 1024 * 1024) continue

                val naluBytes = ByteArray(dataLength)
                if (!readFully(inputStream, naluBytes, dataLength)) break

                if (naluBytes.isNotEmpty()) {
                    val nalHeader = naluBytes[0].toInt()
                    val nalUnitType = nalHeader and 0x1F

                    when (nalUnitType) {
                        5, 7, 8 -> {
                            if (!hasKeyframeReceived) {
                                hasKeyframeReceived = true
                            }
                        }
                        1 -> {
                            if (!hasKeyframeReceived) continue
                        }
                    }
                }

                if (!hasKeyframeReceived) continue

                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(naluBytes)
                        codec.queueInputBuffer(inputBufferIndex, 0, dataLength, System.nanoTime() / 1000, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc socket", e)
        } finally {
            stop()
        }
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int): Boolean {
        var bytesRead = 0
        while (bytesRead < length) {
            val read = stream.read(buffer, bytesRead, length - bytesRead)
            if (read == -1) return false
            bytesRead += read
        }
        return true
    }

    fun stop() {
        isRunning = false
        hasKeyframeReceived = false
        try {
            decodeThread?.interrupt()
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {}
        mediaCodec = null
        socketInputStream = null
    }
}
