package com.lidesheng.hyperlyric.root.utils

import android.util.Log
import java.lang.reflect.Method

/**
 * 动态发现工具类，用于在混淆后的代码中定位关键 Hook 点。
 */
object DynamicFinder {
    private const val TAG = "HyperLyricFinder"

    /**
     * 在指定的 ClassLoader 中全量扫描类，寻找包含特定常量字符串（如 TAG）的类。
     */
    fun findClassByConstantString(
        loader: ClassLoader,
        targetPackage: String,
        targetString: String
    ): Class<*>? {
        try {
            // 获取 BaseDexClassLoader 的 pathList
            val pathListField = loader.javaClass.superclass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(loader)

            // 获取 DexPathList 的 dexElements
            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val dexElements = dexElementsField.get(pathList) as Array<*>

            for (element in dexElements) {
                if (element == null) continue
                val dexFileField = element.javaClass.getDeclaredField("dexFile")
                dexFileField.isAccessible = true
                val dexFile = dexFileField.get(element) as? dalvik.system.DexFile ?: continue

                @Suppress("DEPRECATION")
                val entries = dexFile.entries()
                while (entries.hasMoreElements()) {
                    val className = entries.nextElement()
                    if (className.startsWith(targetPackage)) {
                        try {
                            val clazz = loader.loadClass(className)
                            // 检查类中的静态常量字段（通常 TAG 是 static final String）
                            for (field in clazz.declaredFields) {
                                if (field.type == String::class.java) {
                                    field.isAccessible = true
                                    val value = field.get(null) as? String
                                    if (value == targetString) {
                                        Log.d(TAG, "Found class by TAG '$targetString': $className")
                                        return clazz
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                            // 忽略加载失败的类
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning Dex for constant string: $targetString", e)
        }
        return null
    }

    /**
     * 智能寻找方法，自动适配 Kotlin 协程生成的挂起函数（带 Continuation 参数）。
     */
    fun findMethodSuspendAware(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        returnType: Class<*>? = null
    ): Method? {
        // 1. 首先尝试查找普通方法
        for (method in clazz.declaredMethods) {
            if (method.name == methodName && method.parameterTypes.contentEquals(parameterTypes)) {
                if (returnType == null || method.returnType == returnType) {
                    Log.d(TAG, "Found normal method: ${clazz.name}.$methodName")
                    return method
                }
            }
        }

        // 2. 如果找不到，尝试查找协程版本 (额外增加一个 Continuation 参数)
        for (method in clazz.declaredMethods) {
            if (method.name == methodName && method.parameterTypes.size == parameterTypes.size + 1) {
                val lastParam = method.parameterTypes.last()
                if (lastParam.name.contains("kotlin.coroutines.Continuation")) {
                    // 检查前面的参数是否匹配
                    val leadingParams = method.parameterTypes.copyOfRange(0, parameterTypes.size)
                    if (leadingParams.contentEquals(parameterTypes)) {
                        Log.d(TAG, "Found suspend method: ${clazz.name}.$methodName")
                        return method
                    }
                }
            }
        }
        return null
    }

    /**
     * 在指定的类集合中寻找符合签名的方法。
     */
    fun findMethodBySignature(
        loader: ClassLoader,
        classNames: List<String>,
        parameterTypes: Array<Class<*>>,
        returnType: Class<*>,
        predicate: ((Method) -> Boolean)? = null
    ): Method? {
        for (className in classNames) {
            try {
                val clazz = loader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    if (method.parameterTypes.contentEquals(parameterTypes) &&
                        method.returnType == returnType
                    ) {
                        if (predicate == null || predicate(method)) {
                            Log.d(TAG, "Found method by signature: ${clazz.name}.${method.name}")
                            return method
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore class not found
            }
        }
        return null
    }
}
