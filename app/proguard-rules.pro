# Proguard rules for Akistoy
-keep class com.akistoy.app.** { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class androidx.work.impl.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-dontwarn org.jetbrains.annotations.**
