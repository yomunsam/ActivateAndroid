package app.nekonya.activateandroid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.view.PointerIcon as AndroidPointerIcon
import app.nekonya.activateandroid.ActivateApp
import app.nekonya.activateandroid.data.ModeLayoutSettings
import app.nekonya.activateandroid.data.WatermarkSettings
import app.nekonya.activateandroid.overlay.LayoutMode
import app.nekonya.activateandroid.overlay.LayoutModeDetector
import app.nekonya.activateandroid.overlay.WatermarkOverlayService
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* overlay can still run without notif on older; Android 13+ best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val prefs = (application as ActivateApp).preferences
        // DeX / desktop: default arrow cursor for the activity chrome; Compose Text
        // otherwise forces an I-beam over labels. Text fields still override to text.
        window.decorView.pointerIcon = AndroidPointerIcon.getSystemIcon(
            this,
            AndroidPointerIcon.TYPE_ARROW
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerHoverIcon(PointerIcon.Default)
                ) {
                    SettingsScreen(prefs)
                }
            }
        }
    }
}

private val TAB_MODES = listOf(LayoutMode.PHONE, LayoutMode.EXPANDED, LayoutMode.DEX)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: app.nekonya.activateandroid.data.WatermarkPreferences) {
    val settings by prefs.settingsFlow.collectAsState(initial = WatermarkSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val overlayGranted = remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    val currentMode = remember { LayoutModeDetector.detect(context) }
    var selectedTab by remember {
        mutableIntStateOf(TAB_MODES.indexOf(currentMode).coerceAtLeast(0))
    }
    val selectedMode = TAB_MODES[selectedTab]
    val modeLayout = settings.forMode(selectedMode)

    fun update(transform: (WatermarkSettings) -> WatermarkSettings) {
        scope.launch { prefs.update(transform) }
    }

    fun updateMode(transform: (ModeLayoutSettings) -> ModeLayoutSettings) {
        update { s -> s.withMode(selectedMode, transform(s.forMode(selectedMode))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("激活 Android", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Activate Android · 当前 ${settingsLabel(currentMode)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Preview uses selected tab's per-mode sizes; text left-aligned like overlay
            PreviewCard(settings, modeLayout)

            // Global: overlay permission + start/stop
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("叠加层", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (overlayGranted.value) "悬浮窗权限：已授予"
                            else "悬浮窗权限：未授予",
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                overlayGranted.value = Settings.canDrawOverlays(context)
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("授权")
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("运行叠加服务", modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.overlayRunning,
                            onCheckedChange = { checked ->
                                overlayGranted.value = Settings.canDrawOverlays(context)
                                if (checked && !overlayGranted.value) {
                                    Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                scope.launch {
                                    prefs.setOverlayRunning(checked)
                                    if (checked) {
                                        WatermarkOverlayService.start(context)
                                    } else {
                                        WatermarkOverlayService.stop(context)
                                    }
                                }
                            }
                        )
                    }
                    Button(
                        onClick = {
                            overlayGranted.value = Settings.canDrawOverlays(context)
                            if (!overlayGranted.value) {
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                prefs.setOverlayRunning(true)
                                WatermarkOverlayService.start(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("启动 / 刷新水印服务")
                    }
                }
            }

            // Global: text
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文案（全局）", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = settings.title,
                        onValueChange = { v -> update { it.copy(title = v) } },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settings.subtitle,
                        onValueChange = { v -> update { it.copy(subtitle = v) } },
                        label = { Text("副标题") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Global: appearance (colors / stroke / opacity)
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("外观（全局）", style = MaterialTheme.typography.titleMedium)

                    Text("不透明度 ${(settings.opacity * 100).toInt()}%")
                    Slider(
                        value = settings.opacity,
                        onValueChange = { v -> update { it.copy(opacity = v) } },
                        valueRange = 0f..1f
                    )

                    Text("描边宽度 ${"%.1f".format(settings.strokeWidthDp)} dp（0 = 关闭）")
                    Slider(
                        value = settings.strokeWidthDp,
                        onValueChange = { v -> update { it.copy(strokeWidthDp = v) } },
                        valueRange = 0f..3f
                    )

                    ColorRow(
                        label = "填充颜色",
                        color = Color(settings.fillColor),
                        onPick = { c -> update { it.copy(fillColor = c.copy(alpha = 1f).toArgb()) } }
                    )
                    ColorRow(
                        label = "描边颜色",
                        color = Color(settings.strokeColor),
                        onPick = { c -> update { it.copy(strokeColor = c.copy(alpha = 1f).toArgb()) } }
                    )
                }
            }

            // Per-mode tab bar
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column {
                    Text(
                        "布局模式",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    Text(
                        "各模式独立：字号、偏移、锚点、是否展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("手机（窄屏）") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("平板（展开）") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("桌面（DeX）") }
                        )
                    }
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("是否展示水印", modifier = Modifier.weight(1f))
                            Switch(
                                checked = modeLayout.showWatermark,
                                onCheckedChange = { checked ->
                                    updateMode { it.copy(showWatermark = checked) }
                                }
                            )
                        }

                        Text("标题字号 ${modeLayout.titleSizeSp.toInt()} sp")
                        Slider(
                            value = modeLayout.titleSizeSp,
                            onValueChange = { v -> updateMode { it.copy(titleSizeSp = v) } },
                            valueRange = 10f..36f
                        )

                        Text("副标题字号 ${modeLayout.subtitleSizeSp.toInt()} sp")
                        Slider(
                            value = modeLayout.subtitleSizeSp,
                            onValueChange = { v -> updateMode { it.copy(subtitleSizeSp = v) } },
                            valueRange = 8f..28f
                        )

                        Text("偏移 X ${modeLayout.offsetXDp.toInt()} dp")
                        Slider(
                            value = modeLayout.offsetXDp,
                            onValueChange = { v -> updateMode { it.copy(offsetXDp = v) } },
                            valueRange = 0f..120f
                        )

                        Text("偏移 Y ${modeLayout.offsetYDp.toInt()} dp")
                        Slider(
                            value = modeLayout.offsetYDp,
                            onValueChange = { v -> updateMode { it.copy(offsetYDp = v) } },
                            valueRange = 0f..200f
                        )

                        AnchorPicker(
                            label = "锚点",
                            selected = modeLayout.anchor,
                            onSelect = { a -> updateMode { it.copy(anchor = a) } }
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun PreviewCard(settings: WatermarkSettings, mode: ModeLayoutSettings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E))
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            // Position via Box align (anchor preview ≈ bottom-end default),
            // but glyphs themselves are left-aligned like the real overlay.
            Column(
                modifier = Modifier.align(previewAlignment(mode.anchor)),
                horizontalAlignment = Alignment.Start
            ) {
                val alpha = settings.opacity.coerceIn(0f, 1f)
                if (mode.showWatermark) {
                    Text(
                        settings.title,
                        color = Color(settings.fillColor).copy(alpha = alpha),
                        fontSize = mode.titleSizeSp.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        settings.subtitle,
                        color = Color(settings.fillColor).copy(alpha = alpha),
                        fontSize = mode.subtitleSizeSp.sp,
                        textAlign = TextAlign.Start
                    )
                } else {
                    Text(
                        "（此模式已隐藏）",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

private fun previewAlignment(anchor: WatermarkSettings.Anchor): Alignment = when (anchor) {
    WatermarkSettings.Anchor.TOP_START -> Alignment.TopStart
    WatermarkSettings.Anchor.TOP_END -> Alignment.TopEnd
    WatermarkSettings.Anchor.BOTTOM_START -> Alignment.BottomStart
    WatermarkSettings.Anchor.BOTTOM_END -> Alignment.BottomEnd
    WatermarkSettings.Anchor.CENTER -> Alignment.Center
}

@Composable
private fun ColorRow(label: String, color: Color, onPick: (Color) -> Unit) {
    // Fully opaque solids (纯色). Opacity is controlled only by the global slider;
    // WatermarkView strips stored alpha and re-applies opacity.
    val presets = listOf(
        Color(0xFF000000), // black
        Color(0xFF404040), // dark gray
        Color(0xFF808080), // mid gray
        Color(0xFFFFFFFF), // white
        Color(0xFFE53935), // red
        Color(0xFF1E88E5), // blue
        Color(0xFF43A047), // green
        Color(0xFFFDD835), // yellow
    )
    val selectedRgb = color.copy(alpha = 1f).toArgb()
    Column {
        Text(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            presets.forEach { c ->
                val solid = c.copy(alpha = 1f)
                val selected = solid.toArgb() == selectedRgb
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(solid, CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else Color.Gray.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onPick(solid) }
                )
            }
        }
    }
}

@Composable
private fun AnchorPicker(
    label: String,
    selected: WatermarkSettings.Anchor,
    onSelect: (WatermarkSettings.Anchor) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WatermarkSettings.Anchor.entries.forEach { anchor ->
                FilterChip(
                    selected = selected == anchor,
                    onClick = { onSelect(anchor) },
                    label = { Text(anchorShort(anchor), fontSize = 11.sp) }
                )
            }
        }
    }
}

private fun anchorShort(a: WatermarkSettings.Anchor): String = when (a) {
    WatermarkSettings.Anchor.TOP_START -> "左上"
    WatermarkSettings.Anchor.TOP_END -> "右上"
    WatermarkSettings.Anchor.BOTTOM_START -> "左下"
    WatermarkSettings.Anchor.BOTTOM_END -> "右下"
    WatermarkSettings.Anchor.CENTER -> "居中"
}

private fun settingsLabel(mode: LayoutMode): String = when (mode) {
    LayoutMode.PHONE -> "手机"
    LayoutMode.EXPANDED -> "平板"
    LayoutMode.DEX -> "DeX"
}
