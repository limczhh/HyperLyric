package com.lidesheng.hyperlyric.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.utils.ThemeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class LicenseItem(
    val name: String,
    val author: String,
    val url: String
)

class LicensesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                LicensesScreen()
            }
        }
    }

    @Composable
    fun LicensesScreen() {
        val context = LocalContext.current
        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )

        val licenses = remember {
            listOf(
                LicenseItem("miuix", "YuKongA", "https://github.com/Yukonga/MIUIX"),
                LicenseItem("libxposed API", "libxposed", "https://github.com/libxposed/api"),
                LicenseItem("lyricon", "tomakino", "https://github.com/tomakino/lyricon"),
                LicenseItem("LyricProvider", "tomakino", "https://github.com/tomakino/LyricProvider"),
                LicenseItem("haze", "chrisbanes", "https://github.com/chrisbanes/haze"),
                LicenseItem("retrofit", "square", "https://github.com/square/retrofit"),
                LicenseItem("okhttp", "square", "https://github.com/square/okhttp"),
                LicenseItem("kotlinx.serialization", "Kotlin", "https://github.com/Kotlin/kotlinx.serialization"),
                LicenseItem("Jetpack Compose", "Google", "https://developer.android.com/jetpack/compose"),
                LicenseItem("Lyrico", "Replica0110", "https://github.com/Replica0110/Lyrico"),
                LicenseItem("LDDC", "chenmozhijin", "https://github.com/chenmozhijin/LDDC"),
                LicenseItem("HyperCeiler", "HyperCeiler团队", "https://github.com/ReChronoRain/HyperCeiler"),
                LicenseItem("HyperOShape","xzakota","https://github.com/xzakota/HyperOShape")
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    color = Color.Transparent,
                    title = "项目引用与参考",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { finish() }
                        ) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    start = 12.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding() + 20.dp
                )
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column {
                            licenses.forEach { license ->
                                ArrowPreference(
                                    title = license.name,
                                    summary = license.author,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, license.url.toUri())
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
