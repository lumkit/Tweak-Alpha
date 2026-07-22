package io.github.lumkit.tweak.ui.screen.crash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.lumkit.tweak.LocalSnackBarHostState
import io.github.lumkit.tweak.common.component.ScreenSurface
import io.github.lumkit.tweak.common.component.TopBar
import io.github.lumkit.tweak.common.utils.copyTextToClipboard
import io.github.lumkit.tweak.common.utils.formatDateTime
import io.github.lumkit.tweak.common.utils.rememberLayerBackdropColor
import io.github.lumkit.tweak.common.utils.restartApp
import io.github.lumkit.tweak.model.CrashReport
import io.github.lumkit.tweak.model.CrashSession
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_crash
import tweak_alpha.shared.generated.resources.text_crash_app_info
import tweak_alpha.shared.generated.resources.text_crash_copy
import tweak_alpha.shared.generated.resources.text_crash_copy_success
import tweak_alpha.shared.generated.resources.text_crash_device
import tweak_alpha.shared.generated.resources.text_crash_empty
import tweak_alpha.shared.generated.resources.text_crash_exception
import tweak_alpha.shared.generated.resources.text_crash_message
import tweak_alpha.shared.generated.resources.text_crash_package_name
import tweak_alpha.shared.generated.resources.text_crash_restart
import tweak_alpha.shared.generated.resources.text_crash_sdk
import tweak_alpha.shared.generated.resources.text_crash_stack_trace
import tweak_alpha.shared.generated.resources.text_crash_thread
import tweak_alpha.shared.generated.resources.text_crash_time
import tweak_alpha.shared.generated.resources.text_crash_version

@Composable
fun CrashScreen() {
    val report = remember { CrashSession.peek() }
    val direction = LocalLayoutDirection.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdropColor()
    val snackbarHostState = LocalSnackBarHostState.current
    val scope = rememberCoroutineScope()
    val copySuccess = stringResource(Res.string.text_crash_copy_success)
    val copyLabel = stringResource(Res.string.text_crash_copy)

    fun restartAfterCrash() {
        CrashSession.clear()
        restartApp()
    }

    BackHandler(onBack = ::restartAfterCrash)

    ScreenSurface {
        Scaffold(
            topBar = {
                TopBar(
                    title = stringResource(Res.string.text_crash),
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    actions = {
                        TooltipBox(text = copyLabel) {
                            IconButton(
                                onClick = {
                                    val text = report?.toCopyText().orEmpty()
                                    if (text.isBlank()) {
                                        return@IconButton
                                    }
                                    copyTextToClipboard(text = text, label = copyLabel)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(copySuccess)
                                    }
                                },
                                enabled = report != null,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Copy,
                                    contentDescription = copyLabel,
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = ::restartAfterCrash,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(
                            color = MiuixTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(text = stringResource(Res.string.text_crash_restart))
                    }
                }
            },
            containerColor = MiuixTheme.colorScheme.surface,
        ) { padding ->
            Column(
                modifier = Modifier
                    .layerBackdrop(backdrop)
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            start = padding.calculateStartPadding(direction) + 16.dp,
                            top = padding.calculateTopPadding() + 12.dp,
                            end = padding.calculateEndPadding(direction) + 16.dp,
                            bottom = padding.calculateBottomPadding() + 12.dp,
                        )
                    ),
            ) {
                if (report == null) {
                    Text(
                        text = stringResource(Res.string.text_crash_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                } else {
                    CrashInfoSection(report = report)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(Res.string.text_crash_stack_trace),
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = report.stackTrace,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashInfoSection(report: CrashReport) {
    val timeText = remember(report.timestamp) {
        formatDateTime(report.timestamp, "yyyy-MM-dd HH:mm:ss")
    }
    val items = listOf(
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_package_name),
            value = report.packageName,
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_version),
            value = "v${report.versionName} (${report.versionCode})",
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_device),
            value = "${report.brand} ${report.model}",
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_sdk),
            value = "${report.sdkRelease} (API ${report.sdkInt})",
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_time),
            value = timeText,
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_thread),
            value = report.threadName,
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_exception),
            value = report.exceptionName,
        ),
        CrashDetailItem(
            title = stringResource(Res.string.text_crash_message),
            value = report.message.ifBlank { "--" },
        ),
    )

    Text(
        text = stringResource(Res.string.text_crash_app_info),
        style = MiuixTheme.textStyles.title4,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    CrashDetailColumn(items = items)
}

private data class CrashDetailItem(
    val title: String,
    val value: String,
)

@Composable
private fun CrashDetailColumn(items: List<CrashDetailItem>) {
    val titleStyle = MiuixTheme.textStyles.body2
    val textMeasurer = rememberTextMeasurer()
    val labelWidth = with(LocalDensity.current) {
        remember(items, titleStyle) {
            items.maxOf { item ->
                textMeasurer.measure(
                    text = item.title,
                    style = titleStyle,
                ).size.width.toDp()
            }
        }
    }

    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                CrashDetailRow(
                    title = item.title,
                    value = item.value,
                    labelWidth = labelWidth,
                )
            }
        }
    }
}

@Composable
private fun CrashDetailRow(
    title: String,
    value: String,
    labelWidth: Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            modifier = Modifier.width(labelWidth),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
