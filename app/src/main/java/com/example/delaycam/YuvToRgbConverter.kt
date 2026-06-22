package com.example.delaycam

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.*
import androidx.camera.core.ImageProxy

class YuvToRgbConverter(context: Context) {

    private val rs = RenderScript.create(context)
    private val script = ScriptIntrinsicYuvToRGB.create(
        rs,
        Element.U8_4(rs)
    )

    private var inputAllocation: Allocation? = null
    private var outputAllocation: Allocation? = null

    private var yuvBytes: ByteArray? = null
    private var lastWidth = 0
    private var lastHeight = 0

    @Synchronized
    fun yuvToRgb(image: ImageProxy, output: Bitmap) {

        val width = image.width
        val height = image.height

        if (width != lastWidth || height != lastHeight) {
            val size = width * height * 3 / 2
            yuvBytes = ByteArray(size)

            val type = Type.Builder(rs, Element.U8(rs))
                .setX(size)
                .create()

            inputAllocation = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT)

            val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(width)
                .setY(height)
                .create()

            outputAllocation = Allocation.createTyped(rs, rgbaType, Allocation.USAGE_SCRIPT)

            lastWidth = width
            lastHeight = height
        }

        imageToNV21(image, yuvBytes!!)

        inputAllocation!!.copyFrom(yuvBytes)
        script.setInput(inputAllocation)
        script.forEach(outputAllocation)
        outputAllocation!!.copyTo(output)
    }

    private fun imageToNV21(image: ImageProxy, output: ByteArray) {

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = image.width * image.height

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        yBuffer.get(output, 0, ySize)

        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2

        var pos = ySize

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {

                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride

                output[pos++] = vBuffer.get(vIndex)
                output[pos++] = uBuffer.get(uIndex)
            }
        }
    }
}