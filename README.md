# 激活 Android / Activate Android

模仿 Windows「激活 Windows」水印的 Android 悬浮窗应用：半透明双行无衬线文字，默认可配置，设置页可调样式与各布局模式锚点。

## 包名映射

| 用户期望 | 实际使用 | 原因 |
|----------|----------|------|
| `app.nekonya.activate-android` | **`app.nekonya.activateandroid`** | Android `applicationId` / namespace **不能包含 `-`** |

- **applicationId**: `app.nekonya.activateandroid`
- **namespace**: `app.nekonya.activateandroid`
- **应用标签**: 激活 Android / Activate Android
- **versionName**: `0.1.0` · **versionCode**: `1`

## 功能

- **设置页**（启动入口）
  - **全局**：标题/副标题、填充色、描边色与宽度、不透明度；悬浮窗权限；启停叠加服务。
  - **按模式 Tab**（手机 / 平板 / DeX）：字号（标题+副标题）、偏移 X/Y、锚点、是否展示 — 各模式独立存储。
  - 预览区文字与叠加层一致为**左对齐**（`TextAlign.Start` / `Paint.Align.LEFT`）。
- **叠加层**：`TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`；自定义 View 双 `TextPaint`（填充 + 可选描边）；前台服务（`specialUse`）保活。检测到当前 `LayoutMode` 后应用该模式的字号/偏移/锚点/显示开关。
- **默认文案（Android 风）**
  - 标题：`激活 Android`
  - 副标题：`转到“设置”以激活 Android。`
- **默认样式**：约 45% 不透明度、右下角（bottom-end）、浅灰、无重阴影。

## 布局模式检测

实现见 `LayoutModeDetector`：

1. **Samsung DeX**：`Settings.System` 键 `sem_desktop_mode_enabled`，或反射 `Configuration.semDesktopModeEnabled` / `SEM_DESKTOP_MODE_ENABLED`。
2. **桌面 UI**：`Configuration.UI_MODE_TYPE_DESKTOP`（若平台字段存在；否则回退常量 `0x0f`）。
3. **启发式**：`smallestScreenWidthDp >= 600` → `expanded`；更大且带键鼠时倾向 `dex`；否则 `phone`。

配置变更与显示增删时（`onConfigurationChanged` + `DisplayListener`）会重新锚定，并在 DeX 下挂到桌面/外接显示的 WindowManager。

## Samsung DeX / 副屏叠加

Samsung 文档对副屏 / DeX 叠加的官方路径是标准 **`TYPE_APPLICATION_OVERLAY` + `SYSTEM_ALERT_WINDOW`**，**不需要**专有 DeX-only overlay API。

本应用 best-effort 行为：

1. 注册 `DisplayManager.DisplayListener`：副屏 / DeX 桌面显示晚于服务启动时也会重新挂载（`onConfigurationChanged`  alone 不够）。
2. 当检测到 DeX 时，优先 `getDisplays("com.samsung.android.hardware.display.category.DESKTOP")`，再按 `STATE_ON` / `FLAG_PRESENTATION` / 分辨率对非默认 Display 打分选取。
3. 使用 `createDisplayContext(display)` 取得该显示的 `WindowManager`，再 `addView`（`TYPE_APPLICATION_OVERLAY` | `FLAG_NOT_FOCUSABLE` 等）。
4. DeX 已开但桌面显示尚未出现时**不**挂到手机主屏（经典 DeX 下主屏常为黑屏）；等 DisplayListener 后再挂。副屏 WM 失败时再回退主屏。

Manifest 侧：`resizeableActivity`、较完整的 `configChanges`（含 density/uiMode），以及可选 meta-data `com.samsung.android.multidisplay.keep_process_alive` / `com.samsung.android.keepalive.density`。

**限制**：

- 无法覆盖系统 UI（状态栏、导航栏、DeX 任务栏等）。
- 部分机型 / One UI 版本上副屏 context 可能不可用；此时可能需手动「启动 / 刷新」或仅出现在可用显示上。

## DataStore 形状

- **全局键**：`title` / `subtitle` / `fill_color` / `stroke_color` / `stroke_width_dp` / `opacity` / `overlay_running`
- **每模式**（`phone_*` / `expanded_*` / `dex_*`）：`title_size_sp`、`subtitle_size_sp`、`offset_x_dp`、`offset_y_dp`、`anchor_v2`、`show`
- 旧版单组字号/偏移/`show_watermark`/锚点键仍可读，用于迁移种子。

## 环境与构建

本机约定（Cloud Agents / Origin 不可用时）：

- JDK 17：`/workspace/jdk-17`
- Android SDK：`/workspace/android-sdk`
- `local.properties`：`sdk.dir=/workspace/android-sdk`（已 gitignore）

```bash
cd /workspace/projects/activate-android
JAVA_HOME=/workspace/jdk-17 ANDROID_HOME=/workspace/android-sdk \
  ./gradlew assembleDebug --no-daemon -Dorg.gradle.jvmargs="-Xmx2g"
```

成功后 APK：

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/debug/activate-android-0.1.0-debug.apk
```

仅 debug 签名；`abiFilters`: `arm64-v8a`。

## 权限

- `SYSTEM_ALERT_WINDOW`（悬浮窗）
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`（Android 13+，通知渠道展示保活状态）

`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 说明此 FGS 仅用于用户可控的半透明水印叠加，无媒体投影/摄像头。

## 技术栈

- Kotlin、AGP 8.5+、compileSdk/targetSdk **35**、minSdk **26**
- Jetpack DataStore Preferences
- Compose Material 3 设置页（TabRow 分模式）
- View 绘制叠加层

## 使用

1. 安装 debug APK  
2. 打开应用 → 授予「显示在其他应用上层」  
3. 打开「运行叠加服务」  
4. 在三个 Tab 中分别调整各模式布局；通知栏可停止；设置变更会经 DataStore 实时刷新叠加层  

## 许可

自用项目；请勿用于误导他人系统未激活的欺诈场景。本应用仅为视觉趣味/提醒样式演示。
