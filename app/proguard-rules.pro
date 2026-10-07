# ProGuard rules for MusicHub Production Release

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**
