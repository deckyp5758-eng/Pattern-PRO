# ProGuard / R8 rules for Pattern-PRO release build (isMinifyEnabled = true)
-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep model classes, Room entities, DAOs, and BuildConfig intact
-keep class com.example.BuildConfig { *; }
-keep class com.example.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn javax.annotation.**

# OkHttp & Kotlin Coroutines safety
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
