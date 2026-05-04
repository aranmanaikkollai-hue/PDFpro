# ProPDF Editor ProGuard Rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManagerHolder { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep iText7
-keep class com.itextpdf.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.itextpdf.**

# Keep PDFBox
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep BouncyCastle
# ProGuard rules for BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Activities with @AndroidEntryPoint
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep data classes
-keepclassmembers class com.propdf.editor.data.** {
    <fields>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}
