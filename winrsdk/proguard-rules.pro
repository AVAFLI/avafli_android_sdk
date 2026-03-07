# WINR SDK ProGuard Rules

# Keep public API
-keep class com.avafli.winrsdk.WINR { *; }
-keep class com.avafli.winrsdk.WINRConfiguration { *; }
-keep class com.avafli.winrsdk.WINREnvironment { *; }
-keep class com.avafli.winrsdk.WINRError { *; }
-keep class com.avafli.winrsdk.WINROptions { *; }
-keep class com.avafli.winrsdk.WINRUser { *; }
-keep class com.avafli.winrsdk.WINRBranding { *; }
-keep class com.avafli.winrsdk.domain.DailyEntryGrant { *; }
-keep class com.avafli.winrsdk.domain.Campaign { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.avafli.winrsdk.**$$serializer { *; }
-keepclassmembers class com.avafli.winrsdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.avafli.winrsdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
