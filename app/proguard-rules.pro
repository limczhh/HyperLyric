# 保护 libxposed 接口
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# --- HyperLyric Plugin API ---
-keep interface com.lidesheng.hyperlyric.plugin.api.** { *; }
-keep class com.lidesheng.hyperlyric.plugin.api.** { *; }

# --- Shizuku User Service ---
-keep,allowoptimization class com.lidesheng.hyperlyric.service.utils.shizuku.PrivilegedServiceImpl {
    public <init>();
}

# --- SuperLyric API ---
-keep class com.hchen.superlyricapi.* { *; }
-dontwarn android.os.ServiceManager
