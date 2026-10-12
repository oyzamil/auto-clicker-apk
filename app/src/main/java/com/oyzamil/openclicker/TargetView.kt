package com.oyzamil.openclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Draggable target marker: ring + crosshair (single mode) or numbered badge (multi mode). */
class TargetView(
    ctx: Context,
    private val label: String,
    private val tint: Int,
    private val crosshair: Boolean
) : View(ctx) {

    private val density = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        fill.style = Paint.Style.FILL
        fill.color = (tint and 0x00FFFFFF) or 0x66000000
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 2.5f * density
        ring.color = Color.WHITE
        line.style = Paint.Style.STROKE
        line.strokeWidth = 2f * density
        line.strokeCap = Paint.Cap.ROUND
        line.color = Color.WHITE
        dot.style = Paint.Style.FILL
        dot.color = Color.WHITE
        txt.color = Color.WHITE
        txt.textAlign = Paint.Align.CENTER
        txt.textSize = 16f * density
        txt.isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - ring.strokeWidth
        txt.textSize = min(width, height) * 0.32f
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, ring)
        if (crosshair) {
            val inner = r * 0.35f
            canvas.drawLine(cx, cy - r, cx, cy - inner, line)
            canvas.drawLine(cx, cy + inner, cx, cy + r, line)
            canvas.drawLine(cx - r, cy, cx - inner, cy, line)
            canvas.drawLine(cx + inner, cy, cx + r, cy, line)
            canvas.drawCircle(cx, cy, 3f * density, dot)
        } else {
            canvas.drawText(label, cx, cy - (txt.ascent() + txt.descent()) / 2f, txt)
        }
    }
}
