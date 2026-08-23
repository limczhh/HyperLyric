# 两个规则缺一不可，均由"插件 dex 运行在 parent-first 宿主类加载器之下"导致：
#
# 1. -dontoptimize：R8 优化阶段对 TtmlCache 的匿名 LinkedHashMap 子类做构造器
#    重绑定/类合并，产出被 ART 校验拒绝的非法字节码
#    （VerifyError: 'this' not instance of java.util.LinkedHashMap）。
#
# 2. -dontobfuscate：混淆会把插件类（如 AmllTtmlProcessor）改成 l、a 等短名，
#    parent-first 委派下插件对 l 的引用会先命中宿主 dex 中同名的混淆类，
#    加载到无关类后抛 NoSuchMethodError。保持原类名可彻底规避：
#    插件自有包名与自带 stdlib 全名（宿主 stdlib 已全部混淆成短名）
#    在宿主 dex 中均不存在，不会冲突。
#
# 两条规则均不影响 shrinking（未使用代码仍被裁剪），仅放弃优化与改名。
-dontoptimize
-dontobfuscate

# Manifest 通过原始二进制名反射入口类。
-keep,allowoptimization class com.lidesheng.hyperlyric.plugin.amll.ttml.AmllTtmlPlugin {
    <init>();
}

# Runtime 通过 HyperLyricPlugin 协议调用生命周期方法。
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin {
    public void onLoad(com.lidesheng.hyperlyric.plugin.api.PluginContext);
    public void onEnable();
    public void onConfigChanged(com.lidesheng.hyperlyric.plugin.api.PluginConfig);
    public void onUnload();
}

# Runtime 通过 PluginCacheExtension 协议调用缓存管理方法（宿主 App 列出/清理插件缓存）。
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension {
    public java.util.List listEntries();
    public void clearAll();
    public boolean clearEntry(java.lang.String);
}

# Runtime 通过 API 接口反射扩展属性与处理器。
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.HyperLyricExtension {
    public java.lang.String getId();
    public com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage getStage();
}
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension {
    public com.lidesheng.hyperlyric.plugin.api.PluginSongResult processResult(
        com.lidesheng.hyperlyric.plugin.api.PluginSong
    );
    public com.lidesheng.hyperlyric.plugin.api.PluginSongResult processResult(
        com.lidesheng.hyperlyric.plugin.api.PluginSong,
        com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
    );
}

# 保留跨宿主/插件 ClassLoader 的协议 DTO 与枚举字段。
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginSong { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginSongResult { *; }
-keep,allowoptimization,allowobfuscation public enum com.lidesheng.hyperlyric.plugin.api.PluginSongField { *; }
-keep,allowoptimization,allowobfuscation public enum com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode { *; }
-keep,allowoptimization,allowobfuscation public enum com.lidesheng.hyperlyric.plugin.api.PluginLyricField { *; }
-keep,allowoptimization,allowobfuscation public enum com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginMediaInfo { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginLyricLine { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginWord { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginMetadata { *; }
-keep,allowoptimization,allowobfuscation public interface com.lidesheng.hyperlyric.plugin.api.PluginCache { *; }
-keep,allowoptimization,allowobfuscation public interface com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension { *; }
-keep,allowoptimization,allowobfuscation public class com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry { *; }
