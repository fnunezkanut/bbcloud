@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDeclineDecision
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

/** Modal that keeps Cancel distinct from confirming an empty decline reason. */
internal class BbCloudDeclineReasonDialog(project: Project, private val pullRequest: PullRequestSummary) : DialogWrapper(project) {
    private val reason = mutableStateOf(TextFieldValue())

    init {
        title = BbCloudBundle.message("main.branch.decline.reason.title", pullRequest.id.toString())
        init()
        setOKButtonText(BbCloudBundle.message("main.branch.decline.reason.confirm"))
        setCancelButtonText(BbCloudBundle.message("main.branch.decline.reason.cancel"))
    }

    fun showAndGetDecision(): PullRequestDeclineDecision {
        return if (showAndGet()) {
            PullRequestDeclineDecision.confirmed(input = reason.value.text)
        } else {
            PullRequestDeclineDecision.Cancel
        }
    }

    override fun createCenterPanel(): JComponent {
        val host = compose {
            DeclineReasonContent(
                prompt = BbCloudBundle.message("main.branch.decline.reason.prompt", pullRequest.id.toString()),
                placeholder = BbCloudBundle.message("main.branch.decline.reason.placeholder"),
                value = reason.value,
                onValueChange = { updated -> reason.value = updated }
            )
        }
        host.preferredSize = Dimension(JBUI.scale(520), JBUI.scale(170))
        return host
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction, cancelAction)
    }
}

/** The body of the decline-reason modal: the prompt and the reason the user types. */
@OptIn(ExperimentalJewelApi::class)
@Composable
private fun DeclineReasonContent(prompt: String, placeholder: String, value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = prompt)
        TextArea(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text(text = placeholder) },
            outline = Outline.Error
        )
    }
}