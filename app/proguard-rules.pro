# Filament
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.gltfio.** { *; }

# OpenSCAD JNI
-keep class com.openscad.jni.** { *; }
-keep class com.openscad.viewer.render.** { *; }

# Room
-keep class com.openscad.viewer.data.** { *; }

# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep native methods
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep JNI classes
-keep class com.openscad.jni.OpenSCADNative { *; }