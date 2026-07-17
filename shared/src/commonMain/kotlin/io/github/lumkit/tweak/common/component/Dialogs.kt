package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_warm_tip
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AlertDialog(
    show: Boolean,
    title: String = stringResource(Res.string.text_dialog_warm_tip),
    summary: String,
    confirmText: String,
    cancelText: String = stringResource(Res.string.text_dialog_cancel),
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmCountdownSeconds: Int = 0,
    confirmSkipClickCount: Int = 5,
    summaryScrollable: Boolean = false,
    dismissOnOutsideOrBack: Boolean = true,
) {
    var remainingSeconds by remember(show, confirmCountdownSeconds) {
        mutableIntStateOf(confirmCountdownSeconds.coerceAtLeast(0))
    }
    var skipClicks by remember(show, confirmCountdownSeconds) { mutableIntStateOf(0) }

    LaunchedEffect(show, confirmCountdownSeconds) {
        if (!show || confirmCountdownSeconds <= 0) {
            remainingSeconds = 0
            skipClicks = 0
            return@LaunchedEffect
        }
        remainingSeconds = confirmCountdownSeconds
        skipClicks = 0
        while (remainingSeconds > 0) {
            delay(1_000.milliseconds)
            if (remainingSeconds > 0) {
                remainingSeconds--
            }
        }
    }

    val confirmReady = remainingSeconds <= 0
    val displayConfirmText = if (!confirmReady) {
        "$confirmText (${remainingSeconds}s)"
    } else {
        confirmText
    }

    OverlayDialog(
        title = title,
        summary = if (summaryScrollable) "" else summary,
        show = show,
        onDismissRequest = if (dismissOnOutsideOrBack) {
            {
                onDismissRequest()
            }
        } else {
            null
        },
    ) {
        // Miuix DialogContentLayout 在 content 之前注册了 NavigationBackHandler，会驱动弹窗
        // 预测返回动画。外层 PredictiveBackHandler 挡不住。必须在 content 内后注册同系 handler。
        val blockBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = blockBackState,
            isBackEnabled = show && !dismissOnOutsideOrBack,
            onBackCompleted = { /* 禁止关闭：吞掉返回，不关闭弹窗 */ },
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            if (summaryScrollable && summary.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            DialogActionButtons(
                confirmText = displayConfirmText,
                cancelText = cancelText,
                confirmReady = confirmReady,
                onConfirm = {
                    if (confirmReady) {
                        onConfirm()
                    } else {
                        skipClicks++
                        if (skipClicks >= confirmSkipClickCount.coerceAtLeast(1)) {
                            remainingSeconds = 0
                            skipClicks = 0
                        }
                    }
                },
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun DialogActionButtons(
    confirmText: String,
    cancelText: String,
    confirmReady: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            // 倒计时期间仍保持可点，用于累计跳过点击
            onClick = onConfirm,
            colors = if (confirmReady) {
                ButtonDefaults.buttonColorsPrimary()
            } else {
                ButtonDefaults.buttonColorsPrimary(
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.45f),
                )
            },
        ) {
            Text(text = confirmText)
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        ) {
            Text(text = cancelText)
        }
    }
}
