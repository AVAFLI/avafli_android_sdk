# Avafli SDK Consumer ProGuard Rules
# These rules are automatically applied to apps that use the SDK

# Keep public API
-keep class com.avafli.avaflisdk.Avafli { *; }
-keep class com.avafli.avaflisdk.AvafliConfiguration { *; }
-keep class com.avafli.avaflisdk.AvafliEnvironment { *; }
-keep class com.avafli.avaflisdk.AvafliError { *; }
-keep class com.avafli.avaflisdk.AvafliOptions { *; }
-keep class com.avafli.avaflisdk.AvafliUser { *; }
-keep class com.avafli.avaflisdk.domain.DailyEntryGrant { *; }
-keep class com.avafli.avaflisdk.domain.Campaign { *; }

# Keep serialization models
-keep,includedescriptorclasses class com.avafli.avaflisdk.**$$serializer { *; }
-keepclassmembers class com.avafli.avaflisdk.** {
    *** Companion;
}
-keepclasseswithmembers class com.avafli.avaflisdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
