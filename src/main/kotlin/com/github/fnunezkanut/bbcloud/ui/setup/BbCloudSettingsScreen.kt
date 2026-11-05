@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    ExperimentalJewelApi::class,
    ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.setup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.auth.BbCloudAuthenticationMethod
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities

private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(
    "com.github.fnunezkanut.bbcloud.ui.setup.BbCloudSettingsScreen"
)

/** Compose/Jewel implementation of the Settings tab embedded in the Bitbucket Cloud PR Review tool window. */
@Composable
fun BbCloudSettingsScreen(viewModel: BbCloudSettingsViewModel) {
    val state by viewModel.state
    val hostComponent = LocalComponent.current

    VerticallyScrollableContainer(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProjectsDirectorySection(
                state = state,
                onSelectDirectory = {
                    LOG.info("User clicked Select Projects Directory on Settings")
                    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle(BbCloudBundle.message("setup.projectsDirectory.browse.title"))
                        .withDescription(BbCloudBundle.message("setup.projectsDirectory.browse.description"))
                    val selectedFolder = FileChooser.chooseFile(descriptor, hostComponent, null, null)
                    if (selectedFolder != null) {
                        val path = selectedFolder.toNioPath().toString()
                        LOG.info("Select Projects Directory chosen: $path")
                        viewModel.onProjectsDirectoryChosen(path)
                    }
                }
            )
            AuthenticationSection(
                state = state,
                onOAuthLogin = viewModel::onOAuthLoginClicked,
                onOAuthCancel = viewModel::onOAuthLoginCancelClicked,
                onApiToken = {
                    viewModel.onApiTokenDialogOpened()
                    // Compose click handlers run on the EDT, so showing the modal here is safe. show() blocks until
                    // the dialog closes and needs nothing afterwards.
                    BbCloudApiTokenDialog(parent = hostComponent, viewModel = viewModel).show()
                }
            )
            ResetSection(
                isBusy = state.isBusy,
                onReset = viewModel::onResetClicked
            )
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            FooterContent(footer = state.pluginVersionFooter)
        }
    }
}

/**
 * Renders the Settings footer with the plugin-name label as a clickable link to the project on GitHub, followed by the
 * version and disclaimer remainder as plain text. The stored [footer] is "<label><suffix>", so the known label prefix
 * is split off to keep a single source of truth for the footer text in [SetupFooterText].
 */
@Composable
private fun FooterContent(footer: String) {
    val label = SetupFooterText.label()
    val suffix = footer.removePrefix(label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Link(
            text = label,
            onClick = {
                LOG.info("User opened the plugin project link ${SetupFooterText.PROJECT_URL}")
                BrowserUtil.browse(SetupFooterText.PROJECT_URL)
            }
        )
        Text(
            text = suffix,
            color = JewelTheme.globalColors.text.info,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProjectsDirectorySection(state: SettingsUiState, onSelectDirectory: () -> Unit) {
    GroupHeader(text = BbCloudBundle.message("setup.section.projectsDirectory.title"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onSelectDirectory, modifier = handModifier()) {
            ButtonContent(
                icon = AllIconsKeys.Nodes.Folder,
                iconDescription = "Projects directory",
                text = BbCloudBundle.message("setup.projectsDirectory.browse.button")
            )
        }
        if (state.projectsDirectory == null) {
            StatusContent(
                icon = AllIconsKeys.General.Warning,
                iconDescription = "Projects directory is not set",
                text = BbCloudBundle.message("setup.projectsDirectory.status.notSet"),
                color = JewelTheme.globalColors.text.warning
            )
        } else {
            StatusContent(
                icon = AllIconsKeys.General.InspectionsOK,
                iconDescription = "Projects directory is set",
                text = BbCloudBundle.message("setup.projectsDirectory.status.set", state.projectsDirectory),
                color = SUCCESS_COLOR
            )
        }
    }
}

/**
 * The Authentication section: an intro line, the OAuth and API Token buttons, OAuth login progress with a Cancel link
 * (and a browser-fallback URL when the browser could not be opened), an OAuth status message, and a status label that
 * reflects the working authentication method.
 */
@Composable
private fun AuthenticationSection(state: SettingsUiState, onOAuthLogin: () -> Unit, onOAuthCancel: () -> Unit, onApiToken: () -> Unit) {
    GroupHeader(text = BbCloudBundle.message("setup.section.authentication.title"))
    Text(text = BbCloudBundle.message("setup.authentication.intro"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // OAuth login runs in the application login service; the button is disabled while a login or save is active.
        DefaultButton(
            onClick = onOAuthLogin,
            enabled = !state.isBusy && !state.isOAuthLoginInProgress,
            modifier = handModifier()
        ) {
            ButtonContent(
                icon = AllIconsKeys.General.User,
                iconDescription = "OAuth login",
                text = BbCloudBundle.message("setup.authentication.oauth.button")
            )
        }
        OutlinedButton(onClick = onApiToken, enabled = !state.isBusy, modifier = handModifier()) {
            ButtonContent(
                icon = AllIconsKeys.Actions.MenuSaveall,
                iconDescription = "API token",
                text = BbCloudBundle.message("setup.authentication.apiToken.button")
            )
        }
    }
    if (state.isOAuthLoginInProgress) {
        OAuthLoginProgress(fallbackUrl = state.oauthBrowserFallbackUrl, onCancel = onOAuthCancel)
    }
    if (state.oauthMessage != null) {
        if (state.oauthMessageIsError) {
            StatusContent(
                icon = AllIconsKeys.General.Warning,
                iconDescription = "OAuth error",
                text = state.oauthMessage,
                color = JewelTheme.globalColors.text.warning
            )
        } else {
            StatusContent(
                icon = AllIconsKeys.General.InspectionsOK,
                iconDescription = "OAuth success",
                text = state.oauthMessage,
                color = SUCCESS_COLOR
            )
        }
    }
    AuthenticationStatus(state = state)
}

/** The waiting line and Cancel link while an OAuth login runs, plus the browser-fallback URL and a Copy URL link. */
@Composable
private fun OAuthLoginProgress(fallbackUrl: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = BbCloudBundle.message("setup.authentication.oauth.waiting"))
            Link(text = BbCloudBundle.message("setup.authentication.oauth.cancel"), onClick = onCancel)
        }
        if (fallbackUrl != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = BbCloudBundle.message("setup.authentication.oauth.error.browser", fallbackUrl),
                    color = JewelTheme.globalColors.text.warning,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Link(
                    text = BbCloudBundle.message("setup.authentication.oauth.copyUrl"),
                    onClick = {
                        LOG.info("User copied the OAuth authorize URL")
                        CopyPasteManager.getInstance().setContents(StringSelection(fallbackUrl))
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticationStatus(state: SettingsUiState) {
    if (state.hasStoredButNotWorkingCredentials) {
        StatusContent(
            icon = AllIconsKeys.General.Warning,
            iconDescription = "Credentials not working",
            text = BbCloudBundle.message("setup.authentication.status.notWorking"),
            color = JewelTheme.globalColors.text.warning
        )
        return
    }
    val method = state.authenticationMethod
    val text = AuthenticationStatusText.build(method = method, workspaceSlug = state.workspaceSlug)
    when (method) {
        BbCloudAuthenticationMethod.None -> StatusContent(
            icon = AllIconsKeys.General.Warning,
            iconDescription = "Not authenticated",
            text = text,
            color = JewelTheme.globalColors.text.warning
        )

        BbCloudAuthenticationMethod.ApiToken -> StatusContent(
            icon = AllIconsKeys.General.InspectionsOK,
            iconDescription = "Authenticated with API token",
            text = text,
            color = SUCCESS_COLOR
        )

        BbCloudAuthenticationMethod.OAuth -> StatusContent(
            icon = AllIconsKeys.General.InspectionsOK,
            iconDescription = "Authenticated with OAuth",
            text = text,
            color = OAUTH_COLOR
        )
    }
}

/**
 * The API token component: the token field, the Save button, the inline error/success status, and the token-creation
 * instructions. Kept as a shared composable so the Settings tab's API token dialog and any future host render exactly
 * the same control.
 */
@Composable
internal fun ApiTokenSection(state: SettingsUiState, tokenFieldValue: TextFieldValue, onTokenChange: (TextFieldValue) -> Unit, onSave: () -> Unit) {
    GroupHeader(text = BbCloudBundle.message("setup.section.apiToken.title"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = tokenFieldValue,
            onValueChange = onTokenChange,
            modifier = Modifier.width(300.dp),
            enabled = !state.isBusy,
            outline = if (state.hasStoredToken) {
                // Jewel 0.42 has no success outline; Outline.None preserves the native field while the placeholder
                // and status affordance communicate that the stored token is available.
                Outline.None
            } else {
                Outline.Warning
            },
            placeholder = if (state.hasStoredToken) {
                { Text("********") }
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default
        )
        DefaultButton(onClick = onSave, enabled = !state.isBusy, modifier = handModifier()) {
            ButtonContent(
                icon = AllIconsKeys.Actions.MenuSaveall,
                iconDescription = "Save API token",
                text = BbCloudBundle.message("setup.save.apiToken")
            )
        }
        if (state.errorMessage != null) {
            StatusContent(
                icon = AllIconsKeys.General.Error,
                iconDescription = "Setup error",
                text = state.errorMessage,
                color = JewelTheme.globalColors.text.error
            )
        } else if (state.successMessage != null) {
            StatusContent(
                icon = AllIconsKeys.General.InspectionsOK,
                iconDescription = "API token saved",
                text = state.successMessage,
                color = SUCCESS_COLOR
            )
        }
    }
    InstructionsBlock()
}

@Composable
internal fun InstructionsBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = BbCloudBundle.message("setup.instructions.intro"))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepsPanel(modifier = Modifier.weight(1f))
            ScopeTable(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StepsPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "1. Go to ")
            Link(
                text = BbCloudSetupInstructionsHtml.API_TOKENS_URL,
                onClick = {
                    LOG.info("User opened setup instructions link ${BbCloudSetupInstructionsHtml.API_TOKENS_URL}")
                    BrowserUtil.browse(BbCloudSetupInstructionsHtml.API_TOKENS_URL)
                }
            )
        }
        Text(text = "2. Click \"Create API token with scopes\"")
        Text(text = "3. Create a Bitbucket App API token with the following scopes:")
    }
}

@Composable
private fun ScopeTable(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ScopeRow(scope = "Scope", access = "Access", isHeader = true)
        BbCloudRequiredApiTokenScopes.entries.forEachIndexed { index, entry ->
            ScopeRow(scope = entry.scope, access = entry.access, isHeader = false, rowIndex = index)
        }
    }
}

@Composable
private fun ScopeRow(scope: String, access: String, isHeader: Boolean, rowIndex: Int? = null) {
    val stripeColor = JewelTheme.globalColors.borders.normal.copy(alpha = 0.12f)
    val rowModifier = if (rowIndex != null && rowIndex % 2 == 1) {
        Modifier.background(color = stripeColor)
    } else {
        Modifier
    }
    Row(
        modifier = rowModifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = access,
            modifier = Modifier.width(72.dp),
            fontWeight = if (isHeader) FontWeight.Bold else null
        )
        Text(
            text = scope,
            modifier = Modifier.weight(1f),
            fontWeight = if (isHeader) FontWeight.Bold else null
        )
    }
}

@Composable
private fun ResetSection(isBusy: Boolean, onReset: () -> Unit) {
    GroupHeader(text = BbCloudBundle.message("setup.section.resetPlugin.title"))
    OutlinedButton(onClick = onReset, enabled = !isBusy, modifier = handModifier()) {
        ButtonContent(
            icon = AllIconsKeys.Actions.GC,
            iconDescription = "Reset plugin",
            text = BbCloudBundle.message("setup.reset.plugin"),
            color = JewelTheme.globalColors.text.error
        )
    }
}

@Composable
internal fun ButtonContent(icon: IconKey, iconDescription: String, text: String, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(key = icon, contentDescription = iconDescription, tint = color)
        Text(text = text, color = color)
    }
}

@Composable
internal fun StatusContent(icon: IconKey, iconDescription: String, text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(key = icon, contentDescription = iconDescription, tint = color)
        Text(text = text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun chooseWorkspaceWithDialog(slugs: List<String>, preselectedSlug: String): String? {
    return runOnEdtAndWait {
        Messages.showEditableChooseDialog(
            BbCloudBundle.message("setup.workspace.resolve.message"),
            BbCloudBundle.message("setup.workspace.resolve.title"),
            Messages.getQuestionIcon(),
            slugs.toTypedArray(),
            preselectedSlug,
            WorkspaceSlugValidator(slugs = slugs)
        )
    }
}

internal fun confirmResetWithDialog(): Boolean {
    return runOnEdtAndWait {
        Messages.showYesNoDialog(
            BbCloudBundle.message("setup.reset.confirm.message"),
            BbCloudBundle.message("setup.reset.confirm.title"),
            Messages.getWarningIcon()
        ) == Messages.YES
    }
}

private fun <T> runOnEdtAndWait(action: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return action()
    }
    var result: T? = null
    // ModalityState.any() lets the workspace chooser run on the EDT even while the API token modal is showing;
    // the default modality would queue the runnable until the modal closes, deadlocking the Save that opened it.
    ApplicationManager.getApplication().invokeAndWait({ result = action() }, ModalityState.any())
    @Suppress("UNCHECKED_CAST")
    return result as T
}

private class WorkspaceSlugValidator(private val slugs: List<String>) : InputValidator {
    override fun checkInput(inputString: String?): Boolean {
        return slugs.contains(inputString?.trim())
    }

    override fun canClose(inputString: String?): Boolean {
        return checkInput(inputString)
    }
}

/** The clickable-hand hover cursor used on every actionable Settings button, matching the rest of the plugin UI. */
private fun handModifier(): Modifier {
    return Modifier.pointerHoverIcon(PointerIcon.Hand)
}

internal val SUCCESS_COLOR = Color(0xFF4CAF50)

// IntelliJ New UI link blue, used for the OAuth authentication status label when OAuth is the working method.
private val OAUTH_COLOR = Color(0xFF3574F0)