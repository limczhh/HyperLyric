-keep class com.lidesheng.hyperlyric.root.** { *; }
-keep class com.lidesheng.hyperlyric.Constants { *; }

# 保护 libxposed 库的接口不被混淆
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }

# 保护 Kotlin 元数据
-keep class kotlin.Metadata { *; }

# --- Compose 相关规则 (防止误删) ---
-keepattributes *Annotation*, Signature, InnerClasses
-dontwarn androidx.compose.**

# --- Serialization 和在线网络模型防止混淆 ---
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keep class com.lidesheng.hyperlyric.model.** { *; }
-keep class com.lidesheng.hyperlyric.online.source.** { *; }

# --- KavaRef & YukiHookAPI ---
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn com.highcapable.kavaref.**
-dontwarn com.highcapable.yukihookapi.**