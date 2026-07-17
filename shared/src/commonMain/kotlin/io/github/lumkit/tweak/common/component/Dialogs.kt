package io.github.lumkit.tweak.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_dialog_cancel
import tweak_alpha.shared.generated.resources.text_dialog_warm_tip

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
) {
    OverlayDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        DialogActionButtons(
            confirmText = confirmText,
            cancelText = cancelText,
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun DialogActionButtons(
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColorsPrimary()
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