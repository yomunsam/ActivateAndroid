package app.nekonya.activateandroid.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import app.nekonya.activateandroid.data.ModeLayoutSettings
import app.nekonya.activateandroid.data.WatermarkSettings
import kotlin.math.max

/**
 * Draws a two-line translucent watermark (fill + optional light stroke).
 * Glyphs are left-aligned within the view (Paint.Align.LEFT), Windows-like.
 * Parent WindowManager.LayoutParams places the view via gravity + offset.
 */
class WatermarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isFakeBoldText = false
        textAlign = Paint.Align.LEFT
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 2f
        textAlign = Paint.Align.LEFT
    }

    private var settings: WatermarkSettings = WatermarkSettings()
    private var mode: ModeLayoutSettings = ModeLayoutSettings()
    private var title = settings.title
    private var subtitle = settings.subtitle

    fun applySettings(newSettings: WatermarkSettings, newMode: ModeLayoutSettings) {
        settings = newSettings
        mode = newMode
        title = newSettings.title
        subtitle = newSettings.subtitle
        visibility = if (newMode.showWatermark) VISIBLE else INVISIBLE
        requestLayout()
        invalidate()
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fillPaint.textSize = sp(mode.titleSizeSp)
        val titleW = fillPaint.measureText(title)
        fillPaint.textSize = sp(mode.subtitleSizeSp)
        val subW = fillPaint.measureText(subtitle)
        val lineGap = dp(4f)
        val strokePad = dp(settings.strokeWidthDp).coerceAtLeast(0f) * 2f
        val width = (max(titleW, subW) + strokePad + dp(4f)).toInt()
        val height = (
            sp(mode.titleSizeSp) +
                lineGap +
                sp(mode.subtitleSizeSp) +
                strokePad +
                dp(4f)
            ).toInt()
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!mode.showWatermark) return

        val alpha = (settings.opacity.coerceIn(0f, 1f) * 255).toInt()
        val fillColor = (settings.fillColor and 0x00FFFFFF) or (alpha shl 24)
        val strokeColor = (settings.strokeColor and 0x00FFFFFF) or (alpha shl 24)

        fillPaint.color = fillColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = dp(settings.strokeWidthDp)
        fillPaint.textAlign = Paint.Align.LEFT
        strokePaint.textAlign = Paint.Align.LEFT

        val lineGap = dp(4f)
        val strokePad = dp(settings.strokeWidthDp).coerceAtLeast(0f)
        var y = strokePad + sp(mode.titleSizeSp)

        // Title — left-aligned within view
        fillPaint.textSize = sp(mode.titleSizeSp)
        strokePaint.textSize = sp(mode.titleSizeSp)
        if (settings.strokeWidthDp > 0f) {
            canvas.drawText(title, strokePad, y, strokePaint)
        }
        canvas.drawText(title, strokePad, y, fillPaint)

        // Subtitle
        y += lineGap + sp(mode.subtitleSizeSp)
        fillPaint.textSize = sp(mode.subtitleSizeSp)
        strokePaint.textSize = sp(mode.subtitleSizeSp)
        if (settings.strokeWidthDp > 0f) {
            canvas.drawText(subtitle, strokePad, y, strokePaint)
        }
        canvas.drawText(subtitle, strokePad, y, fillPaint)
    }
}
