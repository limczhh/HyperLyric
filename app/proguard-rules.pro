# 保护 libxposed 接口
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# --- HyperLyric Plugin API ---
# Dynamically loaded plugin DEX files resolve this package through the host
# classloader, so its public names and method descriptors are part of the ABI.
-keep,allowoptimization public interface com.lidesheng.hyperlyric.plugin.api.** {
    public *;
}
-keep,allowoptimization public class com.lidesheng.hyperlyric.plugin.api.** {
    public <init>(...);
    public *;
}

# --- Shizuku User Service ---
-keep,allowoptimization class com.lidesheng.hyperlyric.service.utils.shizuku.PrivilegedServiceImpl {
    public <init>();
}

# --- SuperLyric API ---
-keep class com.hchen.superlyricapi.* { *; }
-dontwarn android.os.ServiceManager
