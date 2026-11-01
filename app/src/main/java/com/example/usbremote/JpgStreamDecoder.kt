package com.example.usbremote

import android.graphics.BitmapFactory
import android.util.Log
import android.view.Surface
import java.io.InputStream
import java.nio.ByteBuffer

class JpgStreamDecoder(private val surface: Surface) {
    private var isRunning = false
    private var socketInputStream: InputStream? = null
    private var decodeThread: Thread? = null

    companion object {
        private const val TAG = "JpgStreamDecoder"
    }

    fun start(inputStream: InputStream) {
        if (isRunning) return
        this.socketInputStream = inputStream
        this.isRunning = true

        decodeThread = Thread {
            readSocketAndRenderLoop()
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun readSocketAndRenderLoop() {
        val lengthHeader = ByteArray(4)
        val inputStream = socketInputStream ?: return

        try {
            while (isRunning) {
                // Đọc 4 byte chiều dài gói tin JPEG
                if (!readFully(inputStream, lengthHeader, 4)) break
                val dataLength = ByteBuffer.wrap(lengthHeader).int
                if (dataLength <= 0 || dataLength > 10 * 1024 * 1024) continue

                // Đọc đúng số byte của khung hình JPEG
                val jpegBytes = ByteArray(dataLength)
                if (!readFully(inputStream, jpegBytes, dataLength)) break

                // Giải mã JPEG thành Bitmap và vẽ lên Surface
                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bitmap != null) {
                    val canvas = surface.lockCanvas(null)
                    if (canvas != null) {
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                        surface.unlockCanvasAndPost(canvas)
                    }
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc socket hoặc render ảnh", e)
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
        try {
            decodeThread?.interrupt()
        } catch (e: Exception) {}
        socketInputStream = null
    }
}
