# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ==================== CRITICAL: KEEP MAIN ACTIVITY ====================
# MUST keep MainActivity or app will crash on launch
-keep class com.pos.cosmos.MainActivity { *; }

# Preserve line number information for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Signature

# ==================== ANDROID FRAMEWORK ====================
# Keep Activities, Services, Providers, Receivers - MANDATORY
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.app.IntentService { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends androidx.fragment.app.Fragment { *; }

# Keep Application subclasses
-keep public class * extends android.app.Application { *; }

# Keep all Views and their constructors (used by Compose)
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ==================== RETROFIT ====================
# Keep Retrofit service interfaces
-keep interface com.pos.cosmos.api.** { *; }

# Keep Retrofit built-in types
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# ==================== GSON ====================
# Keep all model/data classes used by Gson
-keep class com.pos.cosmos.models.** { *; }
-keep class com.google.gson.** { *; }

# Keep all classes with @SerializedName annotation
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Preserve class and method names used by Gson reflection
-keepclassmembers class * {
    <init>();
}

# ==================== OKHTTP ====================
# Keep OkHttp internal classes
-keep class okhttp3.internal.** { *; }
-keep class okhttp3.** { *; }

# Keep OkHttp public classes
-keep public class okhttp3.** { *; }

# Keep platform names for OkHttp
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ==================== KOTLIN COROUTINES ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler

# ==================== GENERAL ====================
# Keep all Java serializable classes
-keep class * implements java.io.Serializable { *; }

# Keep BuildConfig
-keep class com.pos.cosmos.BuildConfig { *; }

# Keep R class (resources)
-keep class com.pos.cosmos.R$* { *; }

# Keep all classes in the app package (defensive measure)
-keep class com.pos.cosmos.** { *; }

# ==================== ITEXT7 ====================
# Keep iText core classes
-keep class com.itextpdf.** { *; }
-keep interface com.itextpdf.** { *; }
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }

# Keep BouncyCastle factory classes
-keep class com.itextpdf.bouncycastle.BouncyCastleFactory { *; }
-keep class com.itextpdf.bouncycastlefips.BouncyCastleFipsFactory { *; }
-keep class com.itextpdf.commons.bouncycastle.** { *; }

# Keep SLF4J
-keep class org.slf4j.** { *; }
-keep interface org.slf4j.** { *; }
-keep class org.slf4j.impl.** { *; }

# Keep Jackson (used by iText)
-keep class com.fasterxml.jackson.** { *; }
-keep interface com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep interface com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep interface com.fasterxml.jackson.databind.** { *; }

# Keep AWT classes (used by iText for image handling)
-keep class java.awt.** { *; }
-keep class javax.imageio.** { *; }

# Don't warn about missing classes from these packages
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn com.fasterxml.jackson.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**