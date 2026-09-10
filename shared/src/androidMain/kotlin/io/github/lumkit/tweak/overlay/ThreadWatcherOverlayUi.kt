package io.github.lumkit.tweak.overlay

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumkit.tweak.model.ThreadInfo
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ThreadWatcherPanel(
    title: String,
    loading: Boolean,
    threads: List<ThreadInfo>,
    message: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.width(150.dp),
    ) {
        ThreadWatcherHead(
            title = title,
            onClose = onClose,
        )
        ThreadWatcherList(
            loading = loading,
            threads = threads,
            message = message,
        )
    }
}

@Composable
private fun ThreadWatcherHead(
    title: String,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                ),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )

            Text(
                text = "CPU% Top15",
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 7.sp,
                    lineHeight = 7.sp,
                ),
                color = Color.White,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = MiuixIcons.Close,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
                .padding(10.dp)
                .clickable(
                    indication = null,
                    interactionSource = null
                ) {
                    onClose()
                },
            tint = Color.White,
        )
    }
}

@Composable
private fun ThreadWatcherList(
    loading: Boolean,
    threads: List<ThreadInfo>,
    message: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CPU%",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = "CPUS",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = "TID",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(28.dp),
        )

        Text(
            text = "COMM",
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f)
                .basicMarquee(),
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
            .sizeIn(maxHeight = 100.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        when {
            loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            }

            threads.isEmpty() -> {
                item {
                    Text(
                        text = message ?: "暂无线程数据",
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 7.sp,
                            lineHeight = 7.sp,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            else -> {
                items(items = threads) { thread ->
                    ThreadWatcherItem(
                        cpuLoad = "${thread.cpuLoad}",
                        cpus = thread.cpusAllowedList,
                        tid = thread.tid.toString(),
                        common = thread.name,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadWatcherItem(
    cpuLoad: String,
    cpus: String,
    tid: String,
    common: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cpuLoad,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = cpus,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(22.dp),
        )

        Text(
            text = tid,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.width(28.dp),
        )

        Text(
            text = common,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 7.sp,
                lineHeight = 7.sp,
            ),
            color = Color.White,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.weight(1f)
                .basicMarquee(),
        )
    }
}
