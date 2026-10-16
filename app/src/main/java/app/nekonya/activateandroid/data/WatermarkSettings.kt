package app.nekonya.activateandroid.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.nekonya.activateandroid.overlay.LayoutMode

/**
 * Per-layout-mode watermark layout (font sizes, offset, anchor, visibility).
 * Independent values for PHONE / EXPANDED / DEX.
 */
data class ModeLayoutSettings(
    val titleSizeSp: Float = 18f,
    val subtitleSizeSp: Float = 13f,
    val offsetXDp: Float = 24f,
    val offsetYDp: Float = 48f,
    val anchor: WatermarkSettings.Anchor = WatermarkSettings.Anchor.BOTTOM_END,
    val showWatermark: Boolean = true,
)

/**
 * Persistent watermark configuration.
 * Global: text, colors, stroke, opacity, overlay running.
 * Per-mode: sizes, offset, anchor, show — see [phone]/[expanded]/[dex].
 * Colors are ARGB ints (alpha in color is ignored for fill; use [opacity] instead).
 */
data class WatermarkSettings(
    val title: String = DEFAULT_TITLE,
    val subtitle: String = DEFAULT_SUBTITLE,
    val fillColor: Int = DEFAULT_FILL_COLOR,
    val strokeColor: Int = DEFAULT_STROKE_COLOR,
    val strokeWidthDp: Float = 0f,
    val opacity: Float = 0.45f,
    val overlayRunning: Boolean = false,
    val phone: ModeLayoutSettings = ModeLayoutSettings(),
    val expanded: ModeLayoutSettings = ModeLayoutSettings(),
    val dex: ModeLayoutSettings = ModeLayoutSettings(),
) {
    enum class Anchor(val key: String) {
        TOP_START("top_start"),
        TOP_END("top_end"),
        BOTTOM_START("bottom_start"),
        BOTTOM_END("bottom_end"),
        CENTER("center");

        companion object {
            fun fromKey(key: String): Anchor =
                entries.find { it.key == key } ?: BOTTOM_END
        }
    }

    fun forMode(mode: LayoutMode): ModeLayoutSettings = when (mode) {
        LayoutMode.PHONE -> phone
        LayoutMode.EXPANDED -> expanded
        LayoutMode.DEX -> dex
    }

    fun withMode(mode: LayoutMode, layout: ModeLayoutSettings): WatermarkSettings = when (mode) {
        LayoutMode.PHONE -> copy(phone = layout)
        LayoutMode.EXPANDED -> copy(expanded = layout)
        LayoutMode.DEX -> copy(dex = layout)
    }

    companion object {
        const val DEFAULT_TITLE = "激活 Android"
        const val DEFAULT_SUBTITLE = "转到“设置”以激活 Android。"
        val DEFAULT_FILL_COLOR: Int = Color(0xFFB0B0B0).toArgb()
        val DEFAULT_STROKE_COLOR: Int = Color(0xFF808080).toArgb()
    }
}
