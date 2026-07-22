# UniaballDownloader ProGuard 规则

# 保留 kotlinx-serialization 序列化类
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx-serialization: 保留 @Serializable 注解的类及其序列化器
-keep,includedescriptorclasses class com.uniaball.downloader.**$$serializer { *; }
-keepclassmembers class com.uniaball.downloader.** {
    *** Companion;
}
-keepclasseswithmembers class com.uniaball.downloader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Retrofit (if used)
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Compose: 保留 Composable 函数（R8 通常自动处理，但显式保留更安全）
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# 保留数据模型类（防止 R8 移除反射使用的字段）
-keep class com.uniaball.downloader.data.model.** { *; }

# 保留 FileProvider
-keep class androidx.core.content.FileProvider { *; }

# 保留 OkHttp 拦截器
-keep class com.uniaball.downloader.util.** { *; }
