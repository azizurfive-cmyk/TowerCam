package com.example.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.location.GpsLocationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WatermarkHelper {

    fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotationDegrees = image.imageInfo.rotationDegrees

        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
        } else {
            originalBitmap
        }
    }

    /**
     * Bakes the dynamic watermark overlay directly onto the image canvas.
     */
    fun applyWatermark(
        sourceBitmap: Bitmap,
        siteCode: String,
        location: GpsLocationData,
        timestampString: String
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        // Create mutable copy
        val watermarkedBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarkedBitmap)

        // Scale factors relative to typical 1080p image
        val baseDim = kotlin.math.min(width, height)
        val scale = baseDim / 1000f

        val padding = 24f * scale
        val cardMargin = 32f * scale
        val cornerRadius = 14f * scale

        val titleTextSize = 28f * scale
        val bodyTextSize = 20f * scale
        val metaTextSize = 16f * scale
        val lineSpacing = 8f * scale

        // Pre-compute text strings
        val line1Site = "SITE: ${siteCode.trim().uppercase()}"
        val line2Gps = "LAT: ${String.format(Locale.US, "%.6f°", location.latitude)}  LON: ${String.format(Locale.US, "%.6f°", location.longitude)}"
        val line3Time = "TIME: $timestampString"
        val line4Details = "ACC: ${location.formattedAccuracy}  |  ALT: ${location.formattedAltitude}"
        val line5Brand = "TOWERCAM • TELECOM FIELD TELEMETRY"

        // Paints
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bodyTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            setShadowLayer(4f * scale, 1f, 1f, Color.BLACK)
        }

        val sitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 211, 238) // Electric cyan
            textSize = titleTextSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(4f * scale, 1f, 1f, Color.BLACK)
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            textSize = metaTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            setShadowLayer(3f * scale, 1f, 1f, Color.BLACK)
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11) // Warm Amber
            textSize = metaTextSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(3f * scale, 1f, 1f, Color.BLACK)
        }

        // Measure widest text to size the card
        val maxTextWidth = listOf(
            sitePaint.measureText(line1Site),
            textPaint.measureText(line2Gps),
            textPaint.measureText(line3Time),
            textPaint.measureText(line4Details),
            brandPaint.measureText(line5Brand)
        ).maxOrNull() ?: (300f * scale)

        val cardWidth = maxTextWidth + (padding * 2) + (10f * scale)
        val totalTextHeight = titleTextSize + (bodyTextSize * 3) + metaTextSize + (lineSpacing * 5)
        val cardHeight = totalTextHeight + (padding * 2)

        val left = cardMargin
        val bottom = height - cardMargin
        val top = bottom - cardHeight
        val right = left + cardWidth

        // Draw HUD background box
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 15, 23, 42) // Dark Navy slate HUD
            style = Paint.Style.FILL
        }
        val cardRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

        // Draw left cyan indicator stripe
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(6, 182, 212) // Cyan stripe
            style = Paint.Style.FILL
        }
        val stripeRect = RectF(left, top, left + (6f * scale), bottom)
        canvas.drawRoundRect(stripeRect, cornerRadius, cornerRadius, stripePaint)

        // Draw border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 6, 182, 212)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
        }
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        // Draw texts
        var currentY = top + padding + titleTextSize * 0.85f
        val textLeft = left + padding + (8f * scale)

        // Line 1: Site Code
        canvas.drawText(line1Site, textLeft, currentY, sitePaint)
        currentY += titleTextSize + lineSpacing

        // Line 2: GPS
        canvas.drawText(line2Gps, textLeft, currentY, textPaint)
        currentY += bodyTextSize + lineSpacing

        // Line 3: Time
        canvas.drawText(line3Time, textLeft, currentY, textPaint)
        currentY += bodyTextSize + lineSpacing

        // Line 4: Altitude & Accuracy
        canvas.drawText(line4Details, textLeft, currentY, metaPaint)
        currentY += metaTextSize + lineSpacing

        // Line 5: Brand / Inspection Tag
        canvas.drawText(line5Brand, textLeft, currentY, brandPaint)

        return watermarkedBitmap
    }

    /**
     * Saves watermarked bitmap into device MediaStore / Pictures / TowerCam folder.
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        siteCode: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val sanitizedSite = siteCode.trim().uppercase().replace("[^A-Za-z0-9_\\-]".toRegex(), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "TowerCam_${sanitizedSite}_${timestamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TowerCam")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        try {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            if (outputStream == null) {
                return@withContext Result.failure(Exception("Failed to open output stream"))
            }

            outputStream.use { stream ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                if (!compressed) {
                    return@withContext Result.failure(Exception("Failed to compress bitmap as JPEG"))
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.d("TowerCamSave", "Saved watermarked photo: $uri")
            Result.success(uri)
        } catch (e: Exception) {
            Log.e("TowerCamSave", "Error saving image to gallery", e)
            Result.failure(e)
        }
    }
}
