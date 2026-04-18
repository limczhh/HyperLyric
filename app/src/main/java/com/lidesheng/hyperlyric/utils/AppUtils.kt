package com.lidesheng.hyperlyric.utils

import android.app.Application
import android.content.Context
import android.os.Build
import com.lidesheng.hyperlyric.Constants

object AppUtils {
    @JvmStatic
    fun initPredictiveBackGesture(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val prefs = application.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
            val enablePredictiveBack = prefs.getBoolean(Constants.KEY_PREDICTIVE_BACK_GESTURE, Constants.DEFAULT_PREDICTIVE_BACK_GESTURE)
            runCatching {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
                val method = android.content.pm.ApplicationInfo::class.java.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(application.applicationInfo, enablePredictiveBack)
            }
        }
    }
}
