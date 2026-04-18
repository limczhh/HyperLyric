package com.lidesheng.hyperlyric.ui.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class LyricModule(
    val packageInfo: PackageInfo,
    val label: String,
    val description: String?,
    val author: String?,
    val category: String?,
    val isCertified: Boolean = false
)

data class ProviderUiState(
    val modules: List<LyricModule> = emptyList(),
    val isLoading: Boolean = false
)

data class ModuleCategory(
    val name: String,
    val items: List<LyricModule>
)

object LyricProviderManager {


    suspend fun loadProviders(context: Context, stateFlow: MutableStateFlow<ProviderUiState>) {
        stateFlow.update { it.copy(isLoading = true) }

        withContext(Dispatchers.IO) {
            try {
                val packageManager = context.packageManager
                @Suppress("DEPRECATION")
                val getSignFlag =
                    PackageManager.GET_SIGNING_CERTIFICATES

                // 获取全部包名列表，减少初次获取的数据量
                val packageInfos = packageManager.getInstalledPackages(PackageManager.GET_META_DATA or getSignFlag)
                
                val targetPackages = packageInfos.filter { packageInfo ->
                    isValidModule(packageInfo)
                }

                if (targetPackages.isEmpty()) {
                    stateFlow.update { it.copy(isLoading = false, modules = emptyList()) }
                    return@withContext
                }

                val collator = Collator.getInstance(Locale.getDefault())
                val loadedModules = mutableListOf<LyricModule>()

                targetPackages.chunked(6).forEach { batch ->
                    val batchResults = batch.mapNotNull { processPackage(packageManager, it) }
                    loadedModules.addAll(batchResults)

                    val sortedList = loadedModules.sortedWith { m1, m2 ->
                        if (m1.isCertified != m2.isCertified) {
                            m2.isCertified.compareTo(m1.isCertified)
                        } else {
                            collator.compare(m1.label, m2.label)
                        }
                    }

                    stateFlow.update { 
                        it.copy(
                            modules = sortedList.toList(),
                            isLoading = loadedModules.size < targetPackages.size
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stateFlow.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun isValidModule(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        // 严格遵循 lyricon 逻辑：非系统应用且非更新后的系统应用 (即：!(isSystem || isUpdatedSystem))
        return !(isSystem || isUpdatedSystem) && appInfo.metaData?.getBoolean("lyricon_module") == true
    }

    private fun processPackage(pm: PackageManager, packageInfo: PackageInfo): LyricModule? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val metaData = appInfo.metaData ?: return null
            val label = appInfo.loadLabel(pm).toString()

            val isCertified = validateSignature(packageInfo)

            LyricModule(
                packageInfo = packageInfo,
                label = label,
                description = metaData.getString("lyricon_module_description"),
                author = metaData.getString("lyricon_module_author"),
                category = metaData.getString("lyricon_module_category"),
                isCertified = isCertified
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun validateSignature(packageInfo: PackageInfo): Boolean {
        // 简化版的签名校验逻辑，实际项目中可能需要更复杂的实现
        // 这里暂时返回 false，或者你可以实现完整的校验
        return false 
    }

    fun categorizeModules(modules: List<LyricModule>, defaultCategory: String): List<ModuleCategory> {
        if (modules.isEmpty()) return emptyList()
        val grouped = modules.groupBy { it.category ?: defaultCategory }
        
        if (grouped.size == 1 && grouped.containsKey(defaultCategory)) {
            return listOf(ModuleCategory("", grouped[defaultCategory]!!))
        }
        
        return grouped.map { (name, items) -> ModuleCategory(name, items) }
            .sortedBy { it.name }
    }
}
