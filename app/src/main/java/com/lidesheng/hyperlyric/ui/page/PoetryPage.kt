package com.lidesheng.hyperlyric.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.Quotes
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.SearchBarFake
import com.lidesheng.hyperlyric.ui.component.SearchBox
import com.lidesheng.hyperlyric.ui.component.SearchPager
import com.lidesheng.hyperlyric.ui.component.SearchStatus
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun PoetryPage() {
    val navigator = LocalNavigator.current
    val searchLabel = stringResource(R.string.search)
    var searchStatus by remember { mutableStateOf(SearchStatus(label = searchLabel)) }

    val filteredQuotes = remember(searchStatus.searchText) {
        if (searchStatus.searchText.isBlank()) {
            Quotes.list
        } else {
            Quotes.list.filter { it.contains(searchStatus.searchText, ignoreCase = true) }
        }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showFab by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val density = LocalDensity.current

    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )

    Scaffold(
        topBar = {
            searchStatus.TopAppBarAnim(backgroundColor = Color.Transparent) {
                TopAppBar(
                    color = Color.Transparent,
                    title = "HyperLyric",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { navigator.pop() }
                        ) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                        }
                    },
                    bottomContent = {
                        Box(
                            modifier = Modifier
                                .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    with(density) {
                                        val newOffsetY = coordinates.positionInWindow().y.toDp()
                                        if (searchStatus.offsetY != newOffsetY) {
                                            searchStatus = searchStatus.copy(offsetY = newOffsetY)
                                        }
                                    }
                                }
                                .then(
                                    if (searchStatus.isCollapsed()) {
                                        Modifier.pointerInput(Unit) {
                                            detectTapGestures {
                                                searchStatus = searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            SearchBarFake(stringResource(R.string.search))
                        }
                    },
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab && searchStatus.shouldCollapsed(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.back_to_top),
                        modifier = Modifier.rotate(90f),
                        tint = Color.White
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
            ) {
                if (searchStatus.searchText.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredQuotes) { quote ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = quote,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        searchStatus.SearchBox {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .hazeSource(state = hazeState)
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp
                )
            ) {
                items(filteredQuotes) { quote ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = quote,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
