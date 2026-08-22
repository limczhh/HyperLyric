# The manifest resolves the entry class by its original binary name.
-keep,allowoptimization class com.lidesheng.hyperlyric.plugin.ai.translation.AiTranslationPlugin {
    <init>();
}

# Runtime calls the lifecycle methods through the HyperLyricPlugin protocol.
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin {
    public void onLoad(com.lidesheng.hyperlyric.plugin.api.PluginContext);
    public void onEnable();
    public void onConfigChanged(com.lidesheng.hyperlyric.plugin.api.PluginConfig);
    public void onUnload();
}

# Runtime invokes extension properties and processors through these API interfaces.
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
-keepclassmembers,allowoptimization class * implements com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension {
    public java.util.List listEntries();
    public void clearAll();
    public boolean clearEntry(java.lang.String);
}
