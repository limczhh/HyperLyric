package com.lidesheng.hyperlyric.root

import android.util.Log
import io.github.libxposed.api.XposedModule

internal fun XposedModule.log(msg: String) {
    // 统一使用 HyperLyric 作为 Tag，Log.INFO 作为默认优先级
    this.log(Log.INFO, "HyperLyric", msg)
}
