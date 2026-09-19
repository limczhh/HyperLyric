package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Adds the MIUI application-name line above the native title. */
internal object IslandExpandedMediaMiuiAppNameController {
    private val states = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, AppNameState>()
    )

    fun apply(
        elements: IslandExpandedMediaElements,
        appName: CharSequence?,
        textColor: Int?
    ) {
        val player = elements.player as? ViewGroup ?: return
        val title = elements.title as? TextView ?: return
        val artist = elements.artist as? TextView ?: title
        if (appName.isNullOrBlank()) {
            states.remove(player)?.restore()
            return
        }
        val state = states[player]
            ?: AppNameState.create(player, title)?.also { states[player] = it }
            ?: return
        state.apply(appName, artist, textColor)
    }

    fun refreshColor(elements: IslandExpandedMediaElements) {
        val player = elements.player as? ViewGroup ?: return
        val reference = elements.artist as? TextView ?: elements.title as? TextView ?: return
        states[player]?.applyColor(reference)
    }

    fun restore(elements: IslandExpandedMediaElements) {
        val player = elements.player as? ViewGroup ?: return
        states.remove(player)?.restore()
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        referenceElements: IslandExpandedMediaElements,
        appName: CharSequence?,
        textColor: Int?
    ) {
        val titleId = referenceElements.title.id
        val artistId = referenceElements.artist.id
        if (titleId == 0) return
        val title = fakeExpandedView.findViewById<View>(titleId) as? TextView ?: return
        val artist = fakeExpandedView.findViewById<View>(artistId) as? TextView ?: title
        val player = title.parent as? ViewGroup ?: return
        if (appName.isNullOrBlank()) {
            states.remove(player)?.restore()
            return
        }
        val state = states[player]
            ?: AppNameState.create(player, title)?.also { states[player] = it }
            ?: return
        state.apply(appName, artist, textColor)
    }

    private data class AppNameState(
        val label: TextView,
        val titleConstraintState: TitleVerticalConstraintState,
        val titleGap: Int
    ) {
        fun apply(appName: CharSequence, reference: TextView, textColor: Int?) {
            label.text = appName
            label.visibility = View.VISIBLE
            applyColor(reference, textColor)
            titleConstraintState.connectBelow(label.id, titleGap)
        }

        fun applyColor(reference: TextView, textColor: Int? = null) {
            label.setTextColor(textColor ?: reference.currentTextColor)
            label.alpha = reference.alpha
            label.typeface = reference.typeface
            label.includeFontPadding = reference.includeFontPadding
            label.letterSpacing = reference.letterSpacing
            label.fontFeatureSettings = reference.fontFeatureSettings
            label.invalidate()
        }

        fun restore() {
            titleConstraintState.restore()
            (label.parent as? ViewGroup)?.removeView(label)
        }

        companion object {
            fun create(player: ViewGroup, title: TextView): AppNameState? = runCatching {
                val context = player.context
                val density = context.resources.displayMetrics.density
                val horizontalMargin = (
                    IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP * density
                    ).roundToInt()
                val topMargin = (
                    IslandExpandedMediaMiuiMetrics.APP_NAME_TOP_DP * density
                    ).roundToInt()
                val titleGap = context.islandExpandedMediaDp(2f)
                val label = TextView(context).apply {
                    id = View.generateViewId()
                    setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        IslandExpandedMediaMiuiMetrics.APP_NAME_TEXT_SIZE_SP
                    )
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    isSingleLine = true
                    includeFontPadding = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val layoutParams = createLayoutParams(player, title)
                setConstraint(layoutParams, "startToStart", 0)
                setConstraint(layoutParams, "endToEnd", 0)
                setConstraint(layoutParams, "topToTop", 0)
                layoutParams.marginStart = horizontalMargin
                layoutParams.marginEnd = horizontalMargin
                layoutParams.topMargin = topMargin
                val titleConstraintState =
                    requireNotNull(TitleVerticalConstraintState.capture(title))
                player.addView(label, layoutParams)
                titleConstraintState.connectBelow(label.id, titleGap)
                AppNameState(label, titleConstraintState, titleGap)
            }.getOrNull()
        }
    }

    private data class TitleVerticalConstraintState(
        val title: TextView,
        val originalValues: Map<String, Int>,
        val originalTopMargin: Int
    ) {
        fun connectBelow(anchorId: Int, topMargin: Int) {
            updateLayoutParams { params, fields ->
                fields.forEach { (name, field) ->
                    field.setInt(params, if (name == "topToBottom") anchorId else -1)
                }
                params.topMargin = topMargin
            }
        }

        fun restore() {
            updateLayoutParams { params, fields ->
                fields.forEach { (name, field) ->
                    originalValues[name]?.let { value -> field.setInt(params, value) }
                }
                params.topMargin = originalTopMargin
            }
        }

        private fun updateLayoutParams(
            block: (ViewGroup.MarginLayoutParams, Map<String, Field>) -> Unit
        ) {
            val params = title.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val fields = constraintFields(params)
            block(params, fields)
            title.layoutParams = params
            title.requestLayout()
        }

        companion object {
            private val VERTICAL_FIELDS = listOf(
                "topToTop",
                "topToBottom",
                "bottomToTop",
                "bottomToBottom",
                "baselineToBaseline",
                "baselineToTop",
                "baselineToBottom"
            )

            fun capture(title: TextView): TitleVerticalConstraintState? = runCatching {
                val params = title.layoutParams as ViewGroup.MarginLayoutParams
                val fields = constraintFields(params)
                require(fields.containsKey("topToTop") && fields.containsKey("topToBottom"))
                TitleVerticalConstraintState(
                    title = title,
                    originalValues = fields.mapValues { (_, field) -> field.getInt(params) },
                    originalTopMargin = params.topMargin
                )
            }.getOrNull()

            private fun constraintFields(params: ViewGroup.MarginLayoutParams): Map<String, Field> {
                return VERTICAL_FIELDS.mapNotNull { name ->
                    runCatching { params.javaClass.getField(name) }
                        .getOrNull()
                        ?.let { field -> name to field }
                }.toMap()
            }
        }
    }

    private fun createLayoutParams(
        player: ViewGroup,
        title: TextView
    ): ViewGroup.MarginLayoutParams {
        val paramsClass = title.layoutParams?.javaClass
            ?: Class.forName(
                "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams",
                false,
                player.javaClass.classLoader
            )
        return paramsClass.getConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).newInstance(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            as ViewGroup.MarginLayoutParams
    }

    private fun setConstraint(params: ViewGroup.MarginLayoutParams, name: String, value: Int) {
        params.javaClass.getField(name).setInt(params, value)
    }
}
