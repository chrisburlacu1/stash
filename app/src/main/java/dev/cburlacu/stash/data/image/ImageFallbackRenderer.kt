package dev.cburlacu.stash.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.PathParser
import java.io.ByteArrayOutputStream

object ImageFallbackRenderer {

    private const val X_PATH_DATA =
        "M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"

    /**
     * Synthesizes an 800x450 dark obsidian canvas with the centered X glyph for Twitter/X cards
     * when offline or when media extraction fails.
     */
    fun generateXFallbackImage(width: Int = 800, height: Int = 450): ByteArray = runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                0xFF0F1419.toInt(),
                0xFF16181C.toInt(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val path = PathParser.createPathFromPathData(X_PATH_DATA)
        val targetHeight = height * 0.40f
        val scale = targetHeight / 24f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            val scaledW = 24f * scale
            val scaledH = 24f * scale
            postTranslate((width - scaledW) / 2f, (height - scaledH) / 2f)
        }
        path.transform(matrix)

        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE7E9EA.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, glyphPaint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        stream.toByteArray()
    }.getOrDefault(ByteArray(0))
}
