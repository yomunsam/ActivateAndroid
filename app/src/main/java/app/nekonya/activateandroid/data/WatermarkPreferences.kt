package app.nekonya.activateandroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "watermark_settings")

class WatermarkPreferences(private val context: Context) {

    private object Keys {
        // Global
        val TITLE = stringPreferencesKey("title")
        val SUBTITLE = stringPreferencesKey("subtitle")
        val FILL_COLOR = intPreferencesKey("fill_color")
        val STROKE_COLOR = intPreferencesKey("stroke_color")
        val STROKE_WIDTH_DP = floatPreferencesKey("stroke_width_dp")
        val OPACITY = floatPreferencesKey("opacity")
        val OVERLAY_RUNNING = booleanPreferencesKey("overlay_running")

        // Legacy single-value keys (migration sources)
        val LEGACY_TITLE_SIZE_SP = floatPreferencesKey("title_size_sp")
        val LEGACY_SUBTITLE_SIZE_SP = floatPreferencesKey("subtitle_size_sp")
        val LEGACY_OFFSET_X_DP = floatPreferencesKey("offset_x_dp")
        val LEGACY_OFFSET_Y_DP = floatPreferencesKey("offset_y_dp")
        val LEGACY_SHOW_WATERMARK = booleanPreferencesKey("show_watermark")
        val LEGACY_PHONE_ANCHOR = stringPreferencesKey("phone_anchor")
        val LEGACY_EXPANDED_ANCHOR = stringPreferencesKey("expanded_anchor")
        val LEGACY_DEX_ANCHOR = stringPreferencesKey("dex_anchor")

        // Per-mode: phone
        val PHONE_TITLE_SIZE = floatPreferencesKey("phone_title_size_sp")
        val PHONE_SUBTITLE_SIZE = floatPreferencesKey("phone_subtitle_size_sp")
        val PHONE_OFFSET_X = floatPreferencesKey("phone_offset_x_dp")
        val PHONE_OFFSET_Y = floatPreferencesKey("phone_offset_y_dp")
        val PHONE_ANCHOR = stringPreferencesKey("phone_anchor_v2")
        val PHONE_SHOW = booleanPreferencesKey("phone_show")

        // Per-mode: expanded
        val EXPANDED_TITLE_SIZE = floatPreferencesKey("expanded_title_size_sp")
        val EXPANDED_SUBTITLE_SIZE = floatPreferencesKey("expanded_subtitle_size_sp")
        val EXPANDED_OFFSET_X = floatPreferencesKey("expanded_offset_x_dp")
        val EXPANDED_OFFSET_Y = floatPreferencesKey("expanded_offset_y_dp")
        val EXPANDED_ANCHOR = stringPreferencesKey("expanded_anchor_v2")
        val EXPANDED_SHOW = booleanPreferencesKey("expanded_show")

        // Per-mode: dex
        val DEX_TITLE_SIZE = floatPreferencesKey("dex_title_size_sp")
        val DEX_SUBTITLE_SIZE = floatPreferencesKey("dex_subtitle_size_sp")
        val DEX_OFFSET_X = floatPreferencesKey("dex_offset_x_dp")
        val DEX_OFFSET_Y = floatPreferencesKey("dex_offset_y_dp")
        val DEX_ANCHOR = stringPreferencesKey("dex_anchor_v2")
        val DEX_SHOW = booleanPreferencesKey("dex_show")
    }

    val settingsFlow: Flow<WatermarkSettings> = context.dataStore.data.map { prefs ->
        prefs.toSettings()
    }

    suspend fun update(transform: (WatermarkSettings) -> WatermarkSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            writeSettings(prefs, next)
        }
    }

    suspend fun setOverlayRunning(running: Boolean) {
        update { it.copy(overlayRunning = running) }
    }

    private fun Preferences.toSettings(): WatermarkSettings {
        val legacyTitleSize = this[Keys.LEGACY_TITLE_SIZE_SP] ?: 18f
        val legacySubtitleSize = this[Keys.LEGACY_SUBTITLE_SIZE_SP] ?: 13f
        val legacyOffsetX = this[Keys.LEGACY_OFFSET_X_DP] ?: 24f
        val legacyOffsetY = this[Keys.LEGACY_OFFSET_Y_DP] ?: 48f
        val legacyShow = this[Keys.LEGACY_SHOW_WATERMARK] ?: true
        val legacyPhoneAnchor = WatermarkSettings.Anchor.fromKey(
            this[Keys.LEGACY_PHONE_ANCHOR] ?: WatermarkSettings.Anchor.BOTTOM_END.key
        )
        val legacyExpandedAnchor = WatermarkSettings.Anchor.fromKey(
            this[Keys.LEGACY_EXPANDED_ANCHOR] ?: WatermarkSettings.Anchor.BOTTOM_END.key
        )
        val legacyDexAnchor = WatermarkSettings.Anchor.fromKey(
            this[Keys.LEGACY_DEX_ANCHOR] ?: WatermarkSettings.Anchor.BOTTOM_END.key
        )

        return WatermarkSettings(
            title = this[Keys.TITLE] ?: WatermarkSettings.DEFAULT_TITLE,
            subtitle = this[Keys.SUBTITLE] ?: WatermarkSettings.DEFAULT_SUBTITLE,
            fillColor = this[Keys.FILL_COLOR] ?: WatermarkSettings.DEFAULT_FILL_COLOR,
            strokeColor = this[Keys.STROKE_COLOR] ?: WatermarkSettings.DEFAULT_STROKE_COLOR,
            strokeWidthDp = this[Keys.STROKE_WIDTH_DP] ?: 0f,
            opacity = this[Keys.OPACITY] ?: 0.45f,
            overlayRunning = this[Keys.OVERLAY_RUNNING] ?: false,
            phone = ModeLayoutSettings(
                titleSizeSp = this[Keys.PHONE_TITLE_SIZE] ?: legacyTitleSize,
                subtitleSizeSp = this[Keys.PHONE_SUBTITLE_SIZE] ?: legacySubtitleSize,
                offsetXDp = this[Keys.PHONE_OFFSET_X] ?: legacyOffsetX,
                offsetYDp = this[Keys.PHONE_OFFSET_Y] ?: legacyOffsetY,
                anchor = WatermarkSettings.Anchor.fromKey(
                    this[Keys.PHONE_ANCHOR] ?: legacyPhoneAnchor.key
                ),
                showWatermark = this[Keys.PHONE_SHOW] ?: legacyShow,
            ),
            expanded = ModeLayoutSettings(
                titleSizeSp = this[Keys.EXPANDED_TITLE_SIZE] ?: legacyTitleSize,
                subtitleSizeSp = this[Keys.EXPANDED_SUBTITLE_SIZE] ?: legacySubtitleSize,
                offsetXDp = this[Keys.EXPANDED_OFFSET_X] ?: legacyOffsetX,
                offsetYDp = this[Keys.EXPANDED_OFFSET_Y] ?: legacyOffsetY,
                anchor = WatermarkSettings.Anchor.fromKey(
                    this[Keys.EXPANDED_ANCHOR] ?: legacyExpandedAnchor.key
                ),
                showWatermark = this[Keys.EXPANDED_SHOW] ?: legacyShow,
            ),
            dex = ModeLayoutSettings(
                titleSizeSp = this[Keys.DEX_TITLE_SIZE] ?: legacyTitleSize,
                subtitleSizeSp = this[Keys.DEX_SUBTITLE_SIZE] ?: legacySubtitleSize,
                offsetXDp = this[Keys.DEX_OFFSET_X] ?: legacyOffsetX,
                offsetYDp = this[Keys.DEX_OFFSET_Y] ?: legacyOffsetY,
                anchor = WatermarkSettings.Anchor.fromKey(
                    this[Keys.DEX_ANCHOR] ?: legacyDexAnchor.key
                ),
                showWatermark = this[Keys.DEX_SHOW] ?: legacyShow,
            ),
        )
    }

    private fun writeSettings(prefs: MutablePreferences, next: WatermarkSettings) {
        prefs[Keys.TITLE] = next.title
        prefs[Keys.SUBTITLE] = next.subtitle
        prefs[Keys.FILL_COLOR] = next.fillColor
        prefs[Keys.STROKE_COLOR] = next.strokeColor
        prefs[Keys.STROKE_WIDTH_DP] = next.strokeWidthDp
        prefs[Keys.OPACITY] = next.opacity
        prefs[Keys.OVERLAY_RUNNING] = next.overlayRunning

        writeMode(prefs, next.phone, ModeKeys.PHONE)
        writeMode(prefs, next.expanded, ModeKeys.EXPANDED)
        writeMode(prefs, next.dex, ModeKeys.DEX)
    }

    private object ModeKeys {
        data class Bundle(
            val titleSize: Preferences.Key<Float>,
            val subtitleSize: Preferences.Key<Float>,
            val offsetX: Preferences.Key<Float>,
            val offsetY: Preferences.Key<Float>,
            val anchor: Preferences.Key<String>,
            val show: Preferences.Key<Boolean>,
        )

        val PHONE = Bundle(
            Keys.PHONE_TITLE_SIZE, Keys.PHONE_SUBTITLE_SIZE,
            Keys.PHONE_OFFSET_X, Keys.PHONE_OFFSET_Y,
            Keys.PHONE_ANCHOR, Keys.PHONE_SHOW,
        )
        val EXPANDED = Bundle(
            Keys.EXPANDED_TITLE_SIZE, Keys.EXPANDED_SUBTITLE_SIZE,
            Keys.EXPANDED_OFFSET_X, Keys.EXPANDED_OFFSET_Y,
            Keys.EXPANDED_ANCHOR, Keys.EXPANDED_SHOW,
        )
        val DEX = Bundle(
            Keys.DEX_TITLE_SIZE, Keys.DEX_SUBTITLE_SIZE,
            Keys.DEX_OFFSET_X, Keys.DEX_OFFSET_Y,
            Keys.DEX_ANCHOR, Keys.DEX_SHOW,
        )
    }

    private fun writeMode(
        prefs: MutablePreferences,
        mode: ModeLayoutSettings,
        keys: ModeKeys.Bundle,
    ) {
        prefs[keys.titleSize] = mode.titleSizeSp
        prefs[keys.subtitleSize] = mode.subtitleSizeSp
        prefs[keys.offsetX] = mode.offsetXDp
        prefs[keys.offsetY] = mode.offsetYDp
        prefs[keys.anchor] = mode.anchor.key
        prefs[keys.show] = mode.showWatermark
    }
}
