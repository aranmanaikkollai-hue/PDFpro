# PDFBox Android - missing JP2 classes
-dontwarn com.gemalto.jp2.**

# BouncyCastle
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# PDFBox
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Room
-keep class com.propdf.editor.data.local.** { *; }
-keepclassmembers class com.propdf.editor.data.local.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# General Android
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
