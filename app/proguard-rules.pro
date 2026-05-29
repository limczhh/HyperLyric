-keep class com.lidesheng.hyperlyric.root.** { *; }
-keep class com.lidesheng.hyperlyric.common.RootConstants { *; }
-keep class com.lidesheng.hyperlyric.common.ServiceConstants { *; }
-keep class com.lidesheng.hyperlyric.common.UIConstants { *; }

# 保护 libxposed 接口
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
-keep class com.lidesheng.hyperlyric.online.** { *; }

# --- 歌词数据模型（Parcelable + Serializable）---
-keep class com.lidesheng.hyperlyric.lyric.model.** { *; }

# --- Shizuku User Service ---
-keep class com.lidesheng.hyperlyric.service.utils.shizuku.PrivilegedServiceImpl { *; }

# --- SuperLyric API ---
-keep class com.hchen.superlyricapi.* { *; }
-dontwarn android.os.ServiceManager