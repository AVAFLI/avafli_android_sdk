# Avafli SDK ProGuard Rules

# JDK indy string-concat bootstrap class — referenced by javac/kotlinc output but
# desugared by AGP and never present on the Android runtime. Safe to ignore.
-dontwarn java.lang.invoke.StringConcatFactory

# Keep public API
-keep class com.avafli.avaflisdk.Avafli { *; }
-keep class com.avafli.avaflisdk.AvafliConfiguration { *; }
-keep class com.avafli.avaflisdk.AvafliEnvironment { *; }
-keep class com.avafli.avaflisdk.AvafliError { *; }
-keep class com.avafli.avaflisdk.AvafliOptions { *; }
-keep class com.avafli.avaflisdk.AvafliUser { *; }
-keep class com.avafli.avaflisdk.domain.DailyEntryGrant { *; }
-keep class com.avafli.avaflisdk.domain.Campaign { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

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
-keep class okhttp3.** { *; }

# EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Firebase Cloud Messaging is a compileOnly dependency — the host app supplies
# it and its transitive deps at runtime (see PushNotificationManager, which
# calls FirebaseMessaging.getInstance().token directly). R8 minifying THIS
# module has neither on its classpath, so silence the missing-class warnings;
# the host app's own R8/ProGuard config keeps the real classes.
-dontwarn com.google.firebase.messaging.**
-dontwarn com.google.android.gms.**
