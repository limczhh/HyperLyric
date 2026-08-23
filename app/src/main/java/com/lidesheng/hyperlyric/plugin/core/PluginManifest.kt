package com.lidesheng.hyperlyric.plugin.core

import com.lidesheng.hyperlyric.plugin.api.PluginSettingOption
import com.lidesheng.hyperlyric.plugin.api.PluginSettingGroup
import com.lidesheng.hyperlyric.plugin.api.PluginSettingInputType
import com.lidesheng.hyperlyric.plugin.api.PluginSettingSpec
import com.lidesheng.hyperlyric.plugin.api.PluginSettingType
import com.lidesheng.hyperlyric.plugin.api.PluginSettingValuePresentation
import com.lidesheng.hyperlyric.plugin.api.PluginSettingsSchema
import org.json.JSONArray
import org.json.JSONObject

data class PluginManifest(
    val id: String,
    val name: String,
    val summary: String = "",
    val version: String,
    val apiVersion: Int,
    val entry: String,
    val author: String? = null,
    val nameByLocale: Map<String, String> = emptyMap(),
    val summaryByLocale: Map<String, String> = emptyMap(),
    val activationSettingKey: String? = null,
    val settings: PluginSettingsSchema = PluginSettingsSchema(),
    val cacheScopes: List<PluginCacheScope> = emptyList(),
)

/** Manifest-only description of a plugin cache scope; the App supplies the UI. */
data class PluginCacheScope(
    val id: String,
    val title: String,
    val summary: String? = null,
    val titleByLocale: Map<String, String> = emptyMap(),
    val summaryByLocale: Map<String, String> = emptyMap(),
)

object PluginManifestCodec {
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    private val entryPattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")

    fun decode(jsonText: String): PluginManifest {
        val json = JSONObject(jsonText)
        val id = json.requiredString("id")
        val name = json.requiredString("name")
        val summary = json.optionalString("summary").orEmpty()
        val author = json.optionalString("author")?.takeIf { it.isNotBlank() }
        val version = json.requiredString("version")
        val apiVersion = json.optInt("apiVersion", -1)
        val entry = json.requiredString("entry")
        val activationSettingKey = json.optionalString("activationSettingKey")

        require(idPattern.matches(id)) { "Invalid plugin id" }
        require(name.isNotBlank()) { "Plugin name is blank" }
        require(version.isNotBlank()) { "Plugin version is blank" }
        require(apiVersion > 0) { "Invalid plugin apiVersion" }
        require(entryPattern.matches(entry)) { "Invalid plugin entry" }

        val settings = decodeSettings(json.optJSONArray("settings"))
        val groups = decodeSettingGroups(json.optJSONArray("settingGroups"))
        val cacheScopes = decodeCacheScopes(json.optJSONArray("cacheScopes"))
        activationSettingKey?.let { key ->
            val setting = settings.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Activation setting does not exist: $key")
            require(setting.type == PluginSettingType.SWITCH) {
                "Activation setting must be a switch: $key"
            }
        }

        return PluginManifest(
            id = id,
            name = name,
            summary = summary,
            version = version,
            apiVersion = apiVersion,
            entry = entry,
            author = author,
            nameByLocale = decodeLocalizedMap(json.optJSONObject("nameLocales")),
            summaryByLocale = decodeLocalizedMap(json.optJSONObject("summaryLocales")),
            activationSettingKey = activationSettingKey,
            settings = PluginSettingsSchema(settings = settings, groups = groups),
            cacheScopes = cacheScopes
        )
    }

    fun encode(manifest: PluginManifest): String {
        val json = JSONObject()
            .put("id", manifest.id)
            .put("name", manifest.name)
            .also { json ->
                manifest.summary.takeIf { it.isNotBlank() }?.let { json.put("summary", it) }
                manifest.author?.takeIf { it.isNotBlank() }?.let { json.put("author", it) }
                putLocalizedMap(json, "nameLocales", manifest.nameByLocale)
                putLocalizedMap(json, "summaryLocales", manifest.summaryByLocale)
                manifest.activationSettingKey?.let { json.put("activationSettingKey", it) }
            }
            .put("version", manifest.version)
            .put("apiVersion", manifest.apiVersion)
            .put("entry", manifest.entry)

        val settings = JSONArray()
        manifest.settings.settings.forEach { setting ->
            val settingJson = JSONObject()
                .put("type", setting.type.wireName)
                .put("key", setting.key)
                .put("title", setting.title)
            setting.summary?.let { settingJson.put("summary", it) }
            putLocalizedMap(settingJson, "titleLocales", setting.titleByLocale)
            putLocalizedMap(settingJson, "summaryLocales", setting.summaryByLocale)
            setting.dialogSummary?.let { settingJson.put("dialogSummary", it) }
            putLocalizedMap(settingJson, "dialogSummaryLocales", setting.dialogSummaryByLocale)
            setting.emptyValueSummary?.let { settingJson.put("emptyValueSummary", it) }
            putLocalizedMap(
                settingJson,
                "emptyValueSummaryLocales",
                setting.emptyValueSummaryByLocale
            )
            if (setting.valuePresentation != PluginSettingValuePresentation.DEFAULT) {
                settingJson.put("valuePresentation", setting.valuePresentation.wireName)
            }
            if (setting.previewLineCount != 2) {
                settingJson.put("previewLineCount", setting.previewLineCount)
            }
            if (setting.inputType != PluginSettingInputType.DEFAULT) {
                settingJson.put("inputType", setting.inputType.wireName)
            }
            if (setting.conflictsWith.isNotEmpty()) {
                settingJson.put("conflictsWith", JSONArray(setting.conflictsWith))
            }
            if (!setting.backup) {
                settingJson.put("backup", false)
            }
            setting.group?.takeIf { it.isNotBlank() }?.let { settingJson.put("group", it) }
            setting.defaultValue?.let { settingJson.put("default", encodeDefault(setting.type, it)) }
            if (setting.options.isNotEmpty()) {
                settingJson.put(
                    "options",
                    JSONArray().apply {
                        setting.options.forEach { option ->
                            put(
                                JSONObject()
                                    .put("value", option.value)
                                    .put("label", option.label)
                                    .also { optionJson ->
                                        putLocalizedMap(
                                            optionJson,
                                            "labelLocales",
                                            option.labelByLocale
                                        )
                                    }
                            )
                        }
                    }
                )
            }
            setting.min?.let { settingJson.put("min", it) }
            setting.max?.let { settingJson.put("max", it) }
            setting.step?.let { settingJson.put("step", it) }
            settings.put(settingJson)
        }
        if (settings.length() > 0) json.put("settings", settings)
        val groups = JSONArray()
        manifest.settings.groups.forEach { group ->
            groups.put(
                JSONObject()
                    .put("id", group.id)
                    .put("title", group.title)
                    .also { groupJson ->
                        putLocalizedMap(groupJson, "titleLocales", group.titleByLocale)
                    }
            )
        }
        if (groups.length() > 0) json.put("settingGroups", groups)
        if (manifest.cacheScopes.isNotEmpty()) {
            json.put(
                "cacheScopes",
                JSONArray().apply {
                    manifest.cacheScopes.forEach { scope ->
                        put(
                            JSONObject()
                                .put("id", scope.id)
                                .put("title", scope.title)
                                .also { scopeJson ->
                                    scope.summary?.let { scopeJson.put("summary", it) }
                                    putLocalizedMap(scopeJson, "titleLocales", scope.titleByLocale)
                                    putLocalizedMap(scopeJson, "summaryLocales", scope.summaryByLocale)
                                }
                        )
                    }
                }
            )
        }
        return json.toString()
    }

    private fun decodeCacheScopes(array: JSONArray?): List<PluginCacheScope> {
        if (array == null) return emptyList()
        require(array.length() <= MAX_CACHE_SCOPES) { "Too many plugin cache scopes" }
        val ids = HashSet<String>()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("Plugin cache scope must be an object")
                val id = item.requiredString("id")
                require(idPattern.matches(id)) { "Invalid plugin cache scope id" }
                require(ids.add(id)) { "Duplicate plugin cache scope id: $id" }
                add(
                    PluginCacheScope(
                        id = id,
                        title = item.requiredString("title"),
                        summary = item.optionalString("summary"),
                        titleByLocale = decodeLocalizedMap(item.optJSONObject("titleLocales")),
                        summaryByLocale = decodeLocalizedMap(item.optJSONObject("summaryLocales"))
                    )
                )
            }
        }
    }

    private fun decodeSettingGroups(array: JSONArray?): List<PluginSettingGroup> {
        if (array == null) return emptyList()
        require(array.length() <= MAX_SETTING_GROUPS) { "Too many plugin setting groups" }
        val ids = HashSet<String>()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("Plugin setting group must be an object")
                val id = item.requiredString("id")
                require(idPattern.matches(id)) { "Invalid plugin setting group id" }
                require(ids.add(id)) { "Duplicate plugin setting group id: $id" }
                add(
                    PluginSettingGroup(
                        id = id,
                        title = item.requiredString("title"),
                        titleByLocale = decodeLocalizedMap(item.optJSONObject("titleLocales"))
                    )
                )
            }
        }
    }

    private fun decodeSettings(array: JSONArray?): List<PluginSettingSpec> {
        if (array == null) return emptyList()
        val keys = HashSet<String>()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("Plugin setting must be an object")
                val type = PluginSettingType.fromWire(item.requiredString("type"))
                    ?: throw IllegalArgumentException("Unsupported plugin setting type")
                val key = item.requiredString("key")
                val title = item.requiredString("title")
                require(key.matches(Regex("[A-Za-z0-9._-]{1,128}"))) {
                    "Invalid plugin setting key"
                }
                require(keys.add(key)) { "Duplicate plugin setting key: $key" }

                val options = decodeOptions(item.optJSONArray("options"))
                if (type == PluginSettingType.SELECT || type == PluginSettingType.MULTI_SELECT) {
                    require(options.isNotEmpty()) { "Selection setting has no options: $key" }
                }

                add(
                    PluginSettingSpec(
                        type = type,
                        key = key,
                        title = title,
                        summary = item.optionalString("summary"),
                        defaultValue = item.optionalValue("default"),
                        options = options,
                        min = item.optNullableFloat("min"),
                        max = item.optNullableFloat("max"),
                        step = item.optNullableFloat("step"),
                        titleByLocale = decodeLocalizedMap(item.optJSONObject("titleLocales")),
                        summaryByLocale = decodeLocalizedMap(item.optJSONObject("summaryLocales")),
                        dialogSummary = item.optionalString("dialogSummary"),
                        dialogSummaryByLocale = decodeLocalizedMap(
                            item.optJSONObject("dialogSummaryLocales")
                        ),
                        emptyValueSummary = item.optionalString("emptyValueSummary"),
                        emptyValueSummaryByLocale = decodeLocalizedMap(
                            item.optJSONObject("emptyValueSummaryLocales")
                        ),
                        valuePresentation = PluginSettingValuePresentation.fromWire(
                            item.optString(
                                "valuePresentation",
                                PluginSettingValuePresentation.DEFAULT.wireName
                            )
                        ) ?: throw IllegalArgumentException("Unsupported setting value presentation"),
                        previewLineCount = item.optInt("previewLineCount", 2).coerceAtLeast(1),
                        inputType = PluginSettingInputType.fromWire(
                            item.optString("inputType", PluginSettingInputType.DEFAULT.wireName)
                        ) ?: throw IllegalArgumentException("Unsupported setting input type"),
                        conflictsWith = decodeStringArray(item.optJSONArray("conflictsWith")),
                        backup = item.optBoolean("backup", true),
                        group = item.optionalString("group")?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun decodeOptions(array: JSONArray?): List<PluginSettingOption> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                if (item is JSONObject) {
                    add(
                        PluginSettingOption(
                            value = item.requiredString("value"),
                            label = item.requiredString("label"),
                            labelByLocale = decodeLocalizedMap(item.optJSONObject("labelLocales"))
                        )
                    )
                } else if (item != null && item !== JSONObject.NULL) {
                    val value = item.toString()
                    add(PluginSettingOption(value = value, label = value))
                }
            }
        }
    }

    private fun encodeDefault(type: PluginSettingType, value: String): Any = when (type) {
        PluginSettingType.SWITCH -> value.toBooleanStrictOrNull() ?: value
        PluginSettingType.NUMBER -> value.toLongOrNull() ?: value
        PluginSettingType.SLIDER -> value.toFloatOrNull() ?: value
        else -> value
    }

    private fun decodeStringArray(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun decodeLocalizedMap(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        return buildMap(json.length()) {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key).takeIf { it.isNotBlank() } ?: continue
                put(key, value)
            }
        }
    }

    private fun putLocalizedMap(
        json: JSONObject,
        key: String,
        values: Map<String, String>
    ) {
        if (values.isEmpty()) return
        json.put(key, JSONObject().apply {
            values.forEach { (locale, value) ->
                if (locale.isNotBlank() && value.isNotBlank()) put(locale, value)
            }
        })
    }

    private fun JSONObject.requiredString(key: String): String =
        optionalString(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing manifest field: $key")

    private fun JSONObject.optionalString(key: String): String? =
        opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()

    private fun JSONObject.optionalValue(key: String): String? = optionalString(key)

    private fun JSONObject.optNullableFloat(key: String): Float? =
        opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()?.toFloatOrNull()

    private const val MAX_CACHE_SCOPES = 32
    private const val MAX_SETTING_GROUPS = 32
}
