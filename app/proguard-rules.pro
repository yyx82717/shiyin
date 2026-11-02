# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.shiyin.player.**$$serializer { *; }
-keepclassmembers class com.shiyin.player.** { *** Companion; }
-keepclasseswithmembers class com.shiyin.player.** { kotlinx.serialization.KSerializer serializer(...); }

# Media3
-dontwarn androidx.media3.**
