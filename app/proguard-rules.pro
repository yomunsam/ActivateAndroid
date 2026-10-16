# Activate Android — keep reflection helpers for DeX detection
-keepclassmembers class * {
    public static int semDesktopModeEnabled;
}
