package com.example.contrastapp.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class EditorViewModel : ViewModel() {

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _brightness = MutableStateFlow(0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _contrast = MutableStateFlow(1f)
    val contrast: StateFlow<Float> = _contrast.asStateFlow()

    private val _saturation = MutableStateFlow(1f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    fun loadImage(uri: Uri) {
        _imageUri.value = uri
    }

    fun setBrightness(v: Float) { _brightness.value = v }
    fun setContrast(v: Float) { _contrast.value = v }
    fun setSaturation(v: Float) { _saturation.value = v }

    fun reset() {
        _brightness.value = 0f
        _contrast.value = 1f
        _saturation.value = 1f
    }

    private fun buildMatrix(): ColorMatrix {
        val matrix = ColorMatrix()

        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(_saturation.value)
        matrix.postConcat(satMatrix)

        val c = _contrast.value
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, (1f - c) * 127.5f,
                0f, c, 0f, 0f, (1f - c) * 127.5f,
                0f, 0f, c, 0f, (1f - c) * 127.5f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)

        val b = _brightness.value * 255f
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(brightnessMatrix)

        return matrix
    }

    fun buildColorFilter(): ColorFilter {
        return ColorFilter.colorMatrix(buildMatrix().asAndroidColorFilter())
    }

    fun saveToGallery(context: Context) {
        val uri = _imageUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "decode failed", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                val paint = Paint()
                paint.colorFilter = ColorMatrixColorFilter(buildMatrix())
                canvas.drawBitmap(bitmap, 0f, 0f, paint)

                val saved = saveBitmapToMediaStore(context, result)

                withContext(Dispatchers.Main) {
                    if (saved) {
                        Toast.makeText(context, "saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "save failed", Toast.LENGTH_SHORT).show()
                    }
                }

                bitmap.recycle()
                result.recycle()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap): Boolean {
        val filename = "Contrast_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ContrastApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return false

        return try {
            val out: OutputStream = resolver.openOutputStream(uri) ?: return false
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
