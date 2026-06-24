package com.example.delaycam

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView

    private var delaySeconds = 3
    private lateinit var btnPlus: Button
    private lateinit var btnMinus: Button
    private lateinit var txtDelay: TextView


    private val buffer = ArrayDeque<Pair<Long, Bitmap>>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.delayedPreviewImageView)
        btnPlus = findViewById(R.id.btnPlus)
        btnMinus = findViewById(R.id.btnMinus)
        txtDelay = findViewById(R.id.txtDelay)

        txtDelay.text = getString(R.string.delay_format, delaySeconds)

        btnPlus.setOnClickListener {
            if (delaySeconds < 10) {
                delaySeconds++
                txtDelay.text =
                    if (delaySeconds == 0)
                        getString(R.string.live_label)
                    else
                        getString(R.string.delay_format, delaySeconds)
            }
        }

        btnMinus.setOnClickListener {
            if (delaySeconds > 0) {
                delaySeconds--
                txtDelay.text =
                    if (delaySeconds == 0)
                        getString(R.string.live_label)
                    else
                        getString(R.string.delay_format, delaySeconds)
            }
        }

        startCamera()
        startRenderLoop()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(cameraExecutor) { image ->

                try {
                    val bitmap = createBitmap(image.width, image.height)
                    bitmap.copyPixelsFromBuffer(image.planes[0].buffer)

                    val rotation = image.imageInfo.rotationDegrees

                    val finalBitmap = if (rotation != 0) {
                        val matrix = Matrix().apply {
                            postRotate(rotation.toFloat())
                        }

                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                    } else bitmap

                    val now = System.currentTimeMillis()

                    synchronized(buffer) {
                        buffer.add(now to finalBitmap)

                        // prevent memory crash
                        if (buffer.size > 120) {
                            buffer.removeFirst().second.recycle()
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRenderLoop() {
        val handler = Handler(Looper.getMainLooper())

        handler.post(object : Runnable {
            override fun run() {

                val targetTime = System.currentTimeMillis() - (delaySeconds * 1000L)

                val frame = synchronized(buffer) {
                    buffer.lastOrNull { it.first <= targetTime }
                }

                frame?.second?.let { bitmap ->
                    imageView.setImageBitmap(bitmap)
                }

                handler.postDelayed(this, 50)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        cameraExecutor.shutdown()

        synchronized(buffer) {
            buffer.forEach { it.second.recycle() }
            buffer.clear()
        }
    }
}