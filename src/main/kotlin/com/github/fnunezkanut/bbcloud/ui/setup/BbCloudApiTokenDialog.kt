@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalJewelApi::class)

package com.github.fnunezkanut.bbcloud.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import java.awt.Component
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

/**
 * Modal that hosts the API token field, Save button, and token-creation instructions from the Settings tab.
 *
 * It binds to the same [BbCloudSettingsViewModel] instance as the Settings page, so Compose snapshot state is shared
 * and the token's save/validation/workspace-chooser logic is reused unchanged. The dialog has only a Close action; the
 * token is saved by the in-content Save button, and a successful save auto-closes the dialog (decision 3a).
 */
internal class BbCloudApiTokenDialog(parent: Component, private val viewModel: BbCloudSettingsViewModel) : DialogWrapper(parent, true) {
    init {
        title = BbCloudBundle.message("setup.authentication.apiToken.dialog.title")
        init()
        setCancelButtonText(BbCloudBundle.message("setup.authentication.apiToken.dialog.close"))
        // Auto-close on a successful save. persist() runs on a background dispatcher, so the close is marshaled to the
        // EDT with ModalityState.any() so it runs while this modal is showing.
        viewModel.setApiTokenSaveListener {
            ApplicationManager.getApplication().invokeLater(
                { if (!isDisposed) close(OK_EXIT_CODE) },
                ModalityState.any()
            )
        }
    }

    override fun createCenterPanel(): JComponent {
        val host = compose { ApiTokenDialogContent(viewModel = viewModel) }
        host.preferredSize = Dimension(JBUI.scale(760), JBUI.scale(420))
        return host
    }

    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }

    override fun dispose() {
        // Clear the listener and any half-typed token on every close path (Close button, Escape, window X, auto-close).
        viewModel.onApiTokenDialogClosed()
        super.dispose()
    }
}

/** The body of the API token modal: the shared API token component bound to the Settings view model. */
@Composable
private fun ApiTokenDialogContent(viewModel: BbCloudSettingsViewModel) {
    val state by viewModel.state
    var tokenFieldValue by remember { mutableStateOf(TextFieldValue()) }

    LaunchedEffect(state.tokenFieldText) {
        if (tokenFieldValue.text != state.tokenFieldText) {
            tokenFieldValue = TextFieldValue(state.tokenFieldText)
        }
    }

    VerticallyScrollableContainer(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ApiTokenSection(
                state = state,
                tokenFieldValue = tokenFieldValue,
                onTokenChange = { value ->
                    tokenFieldValue = value
                    viewModel.setTokenFieldText(value.text)
                },
                onSave = viewModel::onSaveClicked
            )
        }
    }
}