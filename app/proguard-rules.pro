# ProGuard/R8 rules for DumbPhone Launcher
# ==========================================

# Keep the launcher activities (referenced in AndroidManifest.xml)
-keep class com.dumbphone.launcher.MainActivity { *; }
-keep class com.dumbphone.launcher.AppDrawerActivity { *; }
-keep class com.dumbphone.launcher.SettingsActivity { *; }

# Keep PrefsManager (used across activities)
-keep class com.dumbphone.launcher.PrefsManager { *; }

# AndroidX / Material Components
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# RecyclerView adapters (inner classes)
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$Adapter {
    <methods>;
}
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    <init>(...);
}

# Keep view binding generated classes
-keep class com.dumbphone.launcher.databinding.** { *; }

# Prevent stripping of Android framework classes used via reflection
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Optimize aggressively - this is a simple launcher
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
