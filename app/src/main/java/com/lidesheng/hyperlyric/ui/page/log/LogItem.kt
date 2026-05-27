package com.lidesheng.hyperlyric.ui.page.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun formatTimestamp(raw: String): String {
    val dotIndex = raw.lastIndexOf('.')
    return if (dotIndex != -1 && raw.length - dotIndex == 4) raw.substring(0, dotIndex) else raw
}

@Composable
fun LogItem(
    entry: LogEntry,
    copiedMsg: String,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(12.dp),
        showIndication = true,
        onClick = { expanded = !expanded },
        onLongPress = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("HyperLyric Log", entry.rawLog)
            clipboard.setPrimaryClip(clip)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = copiedMsg,
                    duration = SnackbarDuration.Custom(2000L)
                )
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(entry.levelColorBg, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = entry.displayLevel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = entry.levelColorText
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.displaySource,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTimestamp(entry.timestamp),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.animateContentSize(),
            text = entry.message,
            fontSize = 13.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            color = if (entry.level == "E" || entry.level == "C") Color(0xFFF44336) else MiuixTheme.colorScheme.onSurface
        )
    }
}
