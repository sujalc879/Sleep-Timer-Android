package com.sujal.sleeptimer.surface.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/** Bounded raster for RemoteViews; callers update on state changes/minute ticks, never each second. */
internal fun widgetRing(size: Int, fraction: Float, active: Int, track: Int): Bitmap {
    val pixels = size.coerceIn(MIN_PIXELS, MAX_PIXELS)
    val bitmap = createBitmap(pixels, pixels)
    val canvas = Canvas(bitmap)
    val stroke = pixels * STROKE_FRACTION
    val inset = stroke
    val bounds = RectF(inset, inset, pixels - inset, pixels - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap =
            Paint.Cap.ROUND
    }
    paint.color = track
    canvas.drawOval(bounds, paint)
    paint.color = active
    canvas.drawArc(bounds, START_ANGLE, FULL_CIRCLE * fraction.coerceIn(0f, 1f), false, paint)
    return bitmap
}

private const val MIN_PIXELS = 48
private const val MAX_PIXELS = 640
private const val STROKE_FRACTION = 0.065f
private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f
