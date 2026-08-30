# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
