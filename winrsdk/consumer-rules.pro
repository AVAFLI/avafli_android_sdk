# WINR SDK Consumer ProGuard Rules
# These rules are automatically applied to apps that use the SDK

# Keep public API
-keep class com.avafli.winrsdk.WINR { *; }
-keep class com.avafli.winrsdk.WINRConfiguration { *; }
-keep class com.avafli.winrsdk.WINREnvironment { *; }
-keep class com.avafli.winrsdk.WINRError { *; }
-keep class com.avafli.winrsdk.WINROptions { *; }
-keep class com.avafli.winrsdk.WINRUser { *; }
-keep class com.avafli.winrsdk.domain.DailyEntryGrant { *; }
-keep class com.avafli.winrsdk.domain.Campaign { *; }

# Keep serialization models
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
