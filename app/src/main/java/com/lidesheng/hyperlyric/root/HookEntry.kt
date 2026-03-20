package com.lidesheng.hyperlyric.root

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class HookEntry : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        
        if (packageName == "com.android.systemui") {
            log("[HyperLyric] onPackageLoaded: $packageName")
            //MainHook.hookSystemUIDynamicIsland(this, param)
            UnlockIslandWhitelist.hook(this)

            UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
        } else if (packageName == "miui.systemui.plugin") {
            UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
        }
    }
}
