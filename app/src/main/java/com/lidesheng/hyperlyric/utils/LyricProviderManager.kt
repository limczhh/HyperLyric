package com.lidesheng.hyperlyric.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.icon.GeminiColor
import java.text.Collator
import java.util.Locale

data class ModuleTag(
    val iconRes: Int? = null,
    val imageVector: ImageVector? = null,
    val title: String? = null,
    val titleRes: Int = -1,
    val isRainbow: Boolean = false
)

data class LyricModule(
    val packageInfo: PackageInfo,
    val label: String,
    val description: String?,
    val author: String?,
    val category: String?,
    val isCertified: Boolean = false,
    val tags: List<ModuleTag> = emptyList()
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
    val providerToTargetMap = mapOf(
        "io.github.proify.lyricon.cmprovider" to listOf("com.netease.cloudmusic", "com.hihonor.cloudmusic"), //网易云、荣耀定制版网易云
        "io.github.proify.lyricon.qmprovider" to listOf("com.tencent.qqmusic"), //QQ音乐
        "io.github.proify.lyricon.kwprovider" to listOf("cn.kuwo.player"), //酷我音乐
        "io.github.proify.lyricon.kgprovider" to listOf("com.kugou.android", "com.kugou.android.lite"), //酷狗、酷狗极速版
        "io.github.proify.lyricon.spotifyprovider" to listOf("com.spotify.music"), //Spotify
        "io.github.proify.lyricon.amprovider" to listOf("com.apple.android.music"), //Apple Music
        "io.github.proify.lyricon.localprovider" to listOf("com.salt.music"), //椒盐音乐
        "io.github.proify.lyricon.saltprovider" to listOf("com.salt.music"), //椒盐音乐
        "io.github.proify.lyricon.qmhdprovider" to listOf("com.tencent.qqmusicpad"), //QQ音乐HD
        "io.github.proify.lyricon.qishuiprovider" to listOf("com.luna.music"), //汽水音乐
        "io.github.proify.lyricon.paprovider" to listOf("com.maxmpz.audioplayer"), //Poweramp
        "io.github.proify.lyricon.musicfreeprovider" to listOf("fun.upup.musicfree"), //MusicFree
        "io.github.proify.lyricon.lxprovider" to listOf("cn.toside.music.mobile", "com.ikunshare.music.mobile", "com.lxnetease.music.mobile"), //落雪、IKunMusic、LxNetease
        "io.github.proify.lyricon.gramophoneprovider" to listOf("org.akanework.gramophone"), //Gramophone
    )




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
                LogManager.e("LyricProviderManager", "加载歌词模块失败", e)
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
                isCertified = isCertified,
                tags = extractTags(pm, appInfo, metaData)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTags(pm: PackageManager, appInfo: ApplicationInfo, metaData: android.os.Bundle): List<ModuleTag> {
        val tagsResId = metaData.getInt("lyricon_module_tags")
        val rawTags = if (tagsResId != 0) {
            runCatching {
                val resources = pm.getResourcesForApplication(appInfo)
                resources.getStringArray(tagsResId).toList()
            }.getOrDefault(emptyList())
        } else {
            metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
        }
        return rawTags.mapNotNull { tagKey ->
            if (tagKey.isBlank()) return@mapNotNull null
            getPredefinedTag(tagKey) ?: ModuleTag(title = tagKey)
        }
    }

    private fun getPredefinedTag(key: String): ModuleTag? {
        return when (key) {
            $$"$syllable" -> ModuleTag(
                imageVector = GeminiColor,
                titleRes = R.string.module_tag_syllable,
                isRainbow = true
            )
            $$"$translation" -> ModuleTag(
                iconRes = R.drawable.translate_24px,
                titleRes = R.string.module_tag_translation
            )
            $$"$bluetooth" -> ModuleTag(titleRes = R.string.module_tag_bluetooth)
            else -> null
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
