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

    // Delay in seconds (0 = live view)
    private var delaySeconds = 0
    private lateinit var btnPlus: Button
    private lateinit var btnMinus: Button
    private lateinit var txtDelay: TextView

    // Frame buffer: (timestamp, bitmap)
    private val buffer = ArrayDeque<Pair<Long, Bitmap>>()
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        bindViews()

        updateDelayText()

        setupControls()

        startCamera()
        startRenderLoop()
    }

    private fun bindViews() {
        imageView = findViewById(R.id.delayedPreviewImageView)
        btnPlus = findViewById(R.id.btnPlus)
        btnMinus = findViewById(R.id.btnMinus)
        txtDelay = findViewById(R.id.txtDelay)
    }

    private fun setupControls() {

        btnPlus.setOnClickListener {
            if (delaySeconds < 4) {
                delaySeconds++
                updateDelayText()
            }
        }

        btnMinus.setOnClickListener {
            if (delaySeconds > 0) {
                delaySeconds--
                updateDelayText()
            }
        }
    }

    private fun updateDelayText() {
        txtDelay.text =
            if (delaySeconds == 0) {
                getString(R.string.live_label)
            } else {
                getString(R.string.delay_format, delaySeconds)
            }
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
                    val now = System.currentTimeMillis()

                    // Convert camera frame -> Bitmap
                    val bitmap = image.toBitmap()

                    // Apply rotation from camera sensor
                    val finalBitmap = bitmap.applyRotation(
                        image.imageInfo.rotationDegrees
                    )

                    synchronized(buffer) {
                        buffer.addLast(now to finalBitmap)

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

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
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

    /**
     * Apply rotation if needed
     */
    private fun Bitmap.applyRotation(rotation: Int): Bitmap {

        if (rotation == 0) return this

        val matrix = Matrix().apply {
            postRotate(rotation.toFloat())
        }

        return Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            matrix,
            true
        )
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