package com.github.fnunezkanut.bbcloud.ui.air

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.EditorTextField
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.util.concurrent.TimeUnit

/**
 * The only code that touches the JetBrains Air plugin.
 *
 * Air is an optional dependency with no public API, so nothing here is compiled against it. Air is detected by its global
 * prompt action, the chat is found by its tool window id, and the prompt input is matched by its class name. When a step
 * fails, the text is copied to the clipboard without a notification.
 */
internal object AirChatBridge {
    private const val CHAT_TOOL_WINDOW_ID = "air.chat"
    private const val GLOBAL_PROMPT_ACTION_ID = "AgentPrompt.OpenGlobalPalette"
    private const val PROMPT_INPUT_CLASS_NAME = "com.intellij.air.frontend.prompt.AirPromptEditorTextField"
    private const val RETRY_ATTEMPTS = 10
    private const val RETRY_DELAY_MS = 50L

    private val LOG = logger<AirChatBridge>()

    /**
     * True when the Air plugin is installed and enabled. The global prompt action is registered only by that plugin, so its
     * presence is the check. The plugin manager is not used because its lookup methods are internal API.
     */
    fun isAvailable(): Boolean {
        return ActionManager.getInstance().getAction(GLOBAL_PROMPT_ACTION_ID) != null
    }

    /**
     * Puts [text] into the Air chat input. It activates the chat tool window first, and opens the global prompt when the chat
     * has no input. Call it on the EDT. Air builds its UI asynchronously, so the insertion happens after a short wait.
     */
    fun sendToChatInput(project: Project, text: String) {
        activateChatToolWindow(project = project)
        awaitPromptInput(project = project, attemptsLeft = RETRY_ATTEMPTS) { chatInput ->
            if (chatInput != null) {
                insertOrCopy(project = project, input = chatInput, text = text)
            } else {
                openGlobalPrompt(project = project)
                awaitPromptInput(project = project, attemptsLeft = RETRY_ATTEMPTS) { paletteInput ->
                    insertOrCopy(project = project, input = paletteInput, text = text)
                }
            }
        }
    }

    private fun activateChatToolWindow(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(CHAT_TOOL_WINDOW_ID)
        if (toolWindow == null) {
            LOG.info("Air chat tool window is not registered")
            return
        }
        toolWindow.activate(null)
    }

    private fun openGlobalPrompt(project: Project) {
        val action = ActionManager.getInstance().getAction(GLOBAL_PROMPT_ACTION_ID)
        val frame = WindowManager.getInstance().getFrame(project)?.rootPane
        if (action == null || frame == null) {
            LOG.info("Air global prompt is not available")
            return
        }
        try {
            ActionManager.getInstance().tryToExecute(action, null, frame, ActionPlaces.UNKNOWN, true)
        } catch (e: RuntimeException) {
            LOG.warn("Could not open the Air global prompt", e)
        }
    }

    /** Calls [onFound] with the first prompt input found, or with null once [attemptsLeft] polls have failed. */
    private fun awaitPromptInput(project: Project, attemptsLeft: Int, onFound: (Editor?) -> Unit) {
        val input = findPromptInput(project = project)
        if (input != null || attemptsLeft <= 0) {
            onFound(input)
            return
        }
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            Runnable {
                ApplicationManager.getApplication().invokeLater(
                    { awaitPromptInput(project = project, attemptsLeft = attemptsLeft - 1, onFound = onFound) },
                    ModalityState.nonModal(),
                    project.disposed
                )
            },
            RETRY_DELAY_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /** The visible, editable Air prompt input. It prefers the input of [project] when several are open. */
    private fun findPromptInput(project: Project): Editor? {
        val candidates = Window.getWindows().asSequence()
            .flatMap { window -> componentTree(root = window) }
            .filterIsInstance<EditorTextField>()
            .filter { field -> field.isShowing && isPromptInput(field = field) }
            .mapNotNull { field -> field.editor }
            .filter { editor -> !editor.isViewer }
            .toList()
        return candidates.firstOrNull { editor -> editor.project == project } ?: candidates.firstOrNull()
    }

    private fun componentTree(root: Component): Sequence<Component> =
        sequence {
            yield(root)
            if (root is Container) {
                for (child in root.components) {
                    yieldAll(componentTree(root = child))
                }
            }
        }

    /** Matches the class name, walking the superclasses, because Air's class is not on the compile classpath. */
    private fun isPromptInput(field: EditorTextField): Boolean {
        return generateSequence<Class<*>>(field.javaClass) { type -> type.superclass }
            .any { type -> type.name == PROMPT_INPUT_CLASS_NAME }
    }

    private fun insertOrCopy(project: Project, input: Editor?, text: String) {
        if (input == null) {
            LOG.info("Air chat input was not found; the comment prompt was copied to the clipboard")
            copyToClipboard(text = text)
            return
        }
        try {
            insertAtCaret(project = project, input = input, text = text)
        } catch (e: RuntimeException) {
            LOG.warn("Could not insert the comment prompt into the Air chat input", e)
            copyToClipboard(text = text)
        }
    }

    /**
     * Inserts [text] at the caret through a write command, so Air's document binding sees the change. A line break separates
     * [text] from existing input that does not end a line.
     */
    private fun insertAtCaret(project: Project, input: Editor, text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val document = input.document
            val offset = input.caretModel.offset
            val separator = if (needsLineBreak(input = input, offset = offset)) "\n" else ""
            document.insertString(offset, separator + text)
            input.caretModel.moveToOffset(offset + separator.length + text.length)
        }
        if (!input.document.text.contains(text)) {
            LOG.info("Air chat input did not keep the comment prompt; the prompt was copied to the clipboard")
            copyToClipboard(text = text)
            return
        }
        IdeFocusManager.getInstance(project).requestFocus(input.contentComponent, true)
        LOG.info("Comment prompt inserted into the Air chat input")
    }

    private fun needsLineBreak(input: Editor, offset: Int): Boolean {
        val document = input.document
        return offset > 0 && document.textLength > 0 && document.charsSequence[offset - 1] != '\n'
    }

    private fun copyToClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}