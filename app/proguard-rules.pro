# Keep serialization models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.kentji.lanclient.data.** { *** Companion; }
-keepclasseswithmembers class com.kentji.lanclient.data.** { kotlinx.serialization.KSerializer serializer(...); }
