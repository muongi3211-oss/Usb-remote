package com.example.remotecontrol

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private var decoder: H264StreamDecoder? = null
    private var socketThread: Thread? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ẩn thanh trạng thái để hiển thị toàn màn hình (Full-screen)
        supportActionBar?.hide()

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startStreaming(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopStreaming()
            }
        })
    }

    private fun startStreaming(surface: android.view.Surface) {
        isConnected = true
        socketThread = Thread {
            while (isConnected) {
                try {
                    // Kết nối tới localhost:5555 (đã được adb forward từ PC sang cáp USB)
                    val socket = Socket("127.0.0.1", 5555)
                    decoder = H264StreamDecoder(surface)
                    decoder?.start(socket.inputStream)
                    break // Thoát vòng lặp nếu kết nối thành công
                } catch (e: Exception) {
                    // Mất kết nối hoặc chưa cắm cáp -> Thử kết nối lại sau mỗi 2 giây
                    Thread.sleep(2000)
                }
            }
        }.apply { start() }
    }

    private fun stopStreaming() {
        isConnected = false
        socketThread?.interrupt()
        decoder?.stop()
    }
}
