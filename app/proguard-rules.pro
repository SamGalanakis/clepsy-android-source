# Keep Kotlin serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.clepsy.android.**$$serializer { *; }
-keepclassmembers class com.clepsy.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.clepsy.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used for JSON serialization
-keep class com.clepsy.android.models.** { *; }
-keep class com.clepsy.android.network.** { *; }

# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
