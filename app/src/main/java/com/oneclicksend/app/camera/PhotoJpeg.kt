package com.oneclicksend.app.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.File

fun ImageProxy.saveJpegUpright(dest: File, quality: Int = 85) {
    val rotation = imageInfo.rotationDegrees
    val source = toBitmap()
    val output = if (rotation == 0) {
        source
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also { rotated ->
            if (rotated != source) source.recycle()
        }
    }
    try {
        dest.outputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                "Не удалось записать фото"
            }
        }
    } finally {
        output.recycle()
    }
}
