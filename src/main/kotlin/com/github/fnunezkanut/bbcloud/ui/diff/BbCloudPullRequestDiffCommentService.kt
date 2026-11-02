package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentAnchor
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.review.PullRequestCommentDecorationContext
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.github.fnunezkanut.bbcloud.ui.main.CommentComposerScreen
import com.github.fnunezkanut.bbcloud.ui.main.CommentComposerViewModel
import com.github.fnunezkanut.bbcloud.ui.main.CommentInput
import com.github.fnunezkanut.bbcloud.ui.main.ComposerDraftCache
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestPageRevealer
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsRenderer
import com.intellij.collaboration.ui.codereview.editor.insertComponentAfter
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.compose
import java.awt.KeyboardFocusManager
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class BbCloudPullRequestDiffCommentService(private val project: Project) : Disposable {
    private val registeredTextViewers = ConcurrentHashMap.newKeySet<DiffViewerBase>()
    private val registeredProcessors = ConcurrentHashMap.newKeySet<DiffRequestProcessor>()
    private val viewerSessions = ConcurrentHashMap<DiffViewerBase, Disposable>()
    private val fileEditorSessions = ConcurrentHashMap<TextEditor, Disposable>()
    private val avatarCache = AuthorAvatarCache(avatarSize = AVATAR_SIZE)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controllers = ConcurrentHashMap<String, EditorCommentThreadController>()
    private val composerSessions = ConcurrentHashMap<ComposerKey, ComposerSession>()
    private val addCommentGutterEditors = ConcurrentHashMap.newKeySet<EditorEx>()

    /** Which thread each inline thread host shows, so focus can be restored into the right host after a rebuild. EDT-only. */
    private val hostThreads = java.util.WeakHashMap<DiffCommentThreadHost, HostThread>()

    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    scheduleAttach(source = source, file = file)
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    source.getEditors(file).forEach { editor ->
                        clearFileEditorSession(fileEditor = editor, releaseDrafts = true)
                        clearComposerSessions(fileEditor = editor)
                    }
                }
            }
        )
    }

    fun refreshOpenEditors() {
        if (project.isDisposed) {
            return
        }
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.forEach { file -> scheduleAttach(source = fileEditorManager, file = file) }
    }

    fun openFileScopedComposer(editor: EditorEx, relativePath: String, pullRequestId: Long) {
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        openTopOfFileComposer(
            editor = editor,
            key = "$pullRequestId:${PullRequestPathMatcher.normalize(relativePath)}",
            logContext = "File comment composer",
            input = { session ->
                CommentInput(
                    label = "file comment",
                    placeholder = BbCloudBundle.message("main.branch.comments.placeholder"),
                    submitLabel = BbCloudBundle.message("main.branch.comments.submit"),
                    submitHint = BbCloudBundle.message("main.branch.comments.submitHint", CommentInputActionsComponentFactory.submitShortcutText),
                    failureMessage = BbCloudBundle.message("main.branch.comments.failed.createFileScoped"),
                    submit = { content, onResult ->
                        context.submitFileScopedComment(
                            pullRequestId = pullRequestId,
                            path = relativePath,
                            content = content,
                            onResult = { accepted ->
                                onResult(accepted)
                                if (accepted) Disposer.dispose(session)
                            }
                        )
                    }
                )
            },
            onInserted = {
                LOG.debug("Opened file comment composer for pull request #$pullRequestId at $relativePath")
            },
            onInsertFailed = {
                LOG.warn("Could not insert file comment composer for pull request #$pullRequestId at $relativePath")
            },
            alreadyOpenLogSuffix = "$relativePath in pull request #$pullRequestId"
        )
    }

    /**
     * Opens a pull-request-level comment composer at the top of [editor], used for a deleted file whose path no longer
     * exists on the source branch. Submitting posts a comment on the whole pull request ([CommentChange.Create], no
     * inline anchor) and, on success, closes the composer and reveals the pull request page scrolled to the newest
     * activity. The composer content is never logged.
     */
    fun openPullRequestLevelComposer(editor: EditorEx, relativePath: String, pullRequestId: Long) {
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val normalizedPath = PullRequestPathMatcher.normalize(relativePath)
        openTopOfFileComposer(
            editor = editor,
            key = "$pullRequestId:pr-level:$normalizedPath",
            logContext = "Pull request comment composer",
            input = { session ->
                CommentInput(
                    label = "pull request comment",
                    placeholder = BbCloudBundle.message("main.branch.comments.placeholder"),
                    submitLabel = BbCloudBundle.message("main.branch.comments.submit"),
                    submitHint = BbCloudBundle.message("main.branch.comments.submitHint", CommentInputActionsComponentFactory.submitShortcutText),
                    failureMessage = BbCloudBundle.message("main.branch.comments.failed.inline"),
                    initialText = BbCloudBundle.message("main.branch.comments.deletedFile.initialText", normalizedPath),
                    submit = { content, onResult ->
                        context.submitPullRequestComment(
                            pullRequestId = pullRequestId,
                            content = content,
                            onResult = { accepted ->
                                onResult(accepted)
                                if (accepted) {
                                    Disposer.dispose(session)
                                    BbCloudPullRequestPageRevealer.getInstance(project).revealNewestActivity(pullRequestId = pullRequestId)
                                }
                            }
                        )
                    }
                )
            },
            onInserted = {
                LOG.debug("Opened pull request comment composer for pull request #$pullRequestId from deleted file $relativePath")
            },
            onInsertFailed = {
                LOG.warn("Could not insert pull request comment composer for pull request #$pullRequestId from deleted file $relativePath")
            },
            alreadyOpenLogSuffix = "$relativePath in pull request #$pullRequestId"
        )
    }

    /**
     * Shared Compose/inlay body of the top-of-file composers ([openFileScopedComposer] and
     * [openPullRequestLevelComposer]). It dedupes on [key] (focusing the open composer instead of opening a second
     * one), hosts the composer in a [DiffCommentThreadHost] inlay at the top of [editor], and disposes everything
     * through the session. [input] receives the session so its submit callback can dispose the composer after a
     * successful submission. The line-anchored composer is intentionally not routed through here: it inserts at a
     * mapped line and refreshes the add-comment gutters.
     */
    private fun openTopOfFileComposer(
        editor: EditorEx,
        key: String,
        logContext: String,
        input: (session: Disposable) -> CommentInput,
        onInserted: () -> Unit,
        onInsertFailed: () -> Unit,
        alreadyOpenLogSuffix: String
    ) {
        val composerKey = ComposerKey(scope = key, editor = editor)
        if (focusLiveComposer(composerKey = composerKey)) {
            LOG.info("$logContext already open for $alreadyOpenLogSuffix; focused it")
            return
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val session = newSession()
        val host = DiffCommentThreadHost(editor = editor)
        val composerSession = ComposerSession(disposable = session, host = host, editor = editor)
        composerSessions[composerKey] = composerSession
        Disposer.register(session) { composerSessions.remove(composerKey, composerSession) }
        val composerViewModel = CommentComposerViewModel(input = input(session), startsExpanded = true)
        var composePanel: ComposePanel? = null
        val content = compose(config = { composePanel = this }) {
            val density = LocalDensity.current.density
            CommentComposerScreen(
                viewModel = composerViewModel,
                avatarIcon = avatarCache.iconFor(context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl),
                showsCollapsedField = false,
                showsDottedBorder = true,
                requestEditorFocus = true,
                focusRequestKey = composerSession.focusGeneration.value,
                onEditorFocusRequested = { host.requestComposeFocus() },
                onCollapsed = { Disposer.dispose(session) },
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        host.syncComposeContentHeight(contentHeightPx = size.height, density = density)
                    }
            )
        }
        // The host measures the Compose content at the full editor viewport text width and relays the inlay on
        // viewport and content resizes; it owns the preferred-size math, so no fixed width is set here.
        composePanel?.let { panel -> host.attachComposePanel(panel = panel) }
        host.notifyEmbeddedHeightChanged()
        val inlay = editor.insertComponentAfter(0, host, Int.MAX_VALUE)
        if (inlay == null) {
            Disposer.dispose(session)
            onInsertFailed()
            return
        }
        composerSession.inlay = inlay
        scheduleDeferredInlayRemeasure(host = host)
        Disposer.register(session) { Disposer.dispose(inlay) }
        onInserted()
    }

    /**
     * Opens a line-anchored inline comment composer for [fileOneBasedLine] (one-based) on [side], mirroring
     * [openFileScopedComposer]. Dedupes on the per-line session key so re-triggering focuses the open composer
     * (decision 5a). The composer submits through [BbCloudWorktreePullRequestContext.submitInlineLineComment] with the
     * correct Bitbucket inline side.
     */
    fun openInlineLineComposer(editor: EditorEx, relativePath: String, pullRequestId: Long, fileOneBasedLine: Int, side: InlineCommentSide) {
        val normalizedPath = PullRequestPathMatcher.normalize(relativePath)
        val composerKey = ComposerKey(scope = "$pullRequestId:$normalizedPath:$side:$fileOneBasedLine", editor = editor)
        if (focusLiveComposer(composerKey = composerKey)) {
            LOG.info("Line comment composer already open for $relativePath:$fileOneBasedLine ($side) in pull request #$pullRequestId; focused it")
            return
        }
        val editorLine = mapFileLineToEditorLine(fileOneBasedLine = fileOneBasedLine, editor = editor)
        if (editorLine == null) {
            LOG.info("Cannot open line comment composer for pull request #$pullRequestId: line $fileOneBasedLine is outside $relativePath")
            return
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val session = newSession()
        val host = DiffCommentThreadHost(editor = editor)
        val composerSession = ComposerSession(disposable = session, host = host, editor = editor, side = side, editorLine = editorLine)
        composerSessions[composerKey] = composerSession
        Disposer.register(session) {
            composerSessions.remove(composerKey, composerSession)
            refreshAddCommentGutters()
        }
        val input = CommentInput(
            label = "line comment",
            placeholder = BbCloudBundle.message("main.branch.comments.placeholder"),
            submitLabel = BbCloudBundle.message("main.branch.comments.submit"),
            submitHint = BbCloudBundle.message("main.branch.comments.submitHint", CommentInputActionsComponentFactory.submitShortcutText),
            failureMessage = BbCloudBundle.message("main.branch.comments.failed.createInlineLine"),
            submit = { content, onResult ->
                context.submitInlineLineComment(
                    pullRequestId = pullRequestId,
                    path = relativePath,
                    line = fileOneBasedLine,
                    side = side,
                    content = content,
                    onResult = { accepted ->
                        onResult(accepted)
                        if (accepted) Disposer.dispose(session)
                    }
                )
            }
        )
        val composerViewModel = CommentComposerViewModel(input = input, startsExpanded = true)
        var composePanel: ComposePanel? = null
        val content = compose(config = { composePanel = this }) {
            val density = LocalDensity.current.density
            CommentComposerScreen(
                viewModel = composerViewModel,
                avatarIcon = avatarCache.iconFor(context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl),
                showsCollapsedField = false,
                showsDottedBorder = true,
                requestEditorFocus = true,
                focusRequestKey = composerSession.focusGeneration.value,
                onEditorFocusRequested = { host.requestComposeFocus() },
                onCollapsed = { Disposer.dispose(session) },
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        host.syncComposeContentHeight(contentHeightPx = size.height, density = density)
                    }
            )
        }
        composePanel?.let { panel -> host.attachComposePanel(panel = panel) }
        host.notifyEmbeddedHeightChanged()
        val inlay = editor.insertComponentAfter(editorLine, host, Int.MAX_VALUE)
        if (inlay == null) {
            Disposer.dispose(session)
            LOG.warn("Could not insert line comment composer for pull request #$pullRequestId at $relativePath:$fileOneBasedLine")
            return
        }
        composerSession.inlay = inlay
        scheduleDeferredInlayRemeasure(host = host)
        Disposer.register(session) { Disposer.dispose(inlay) }
        refreshAddCommentGutters()
        LOG.debug("Opened line comment composer for pull request #$pullRequestId at $relativePath:$fileOneBasedLine on $side")
    }

    /**
     * Focuses the composer already open for [composerKey] and returns true. Composers whose editor was released or
     * whose inlay is gone (a rediff, a file switch in the diff chain, or a closed editor) are disposed first, in every
     * editor, so a stale entry can no longer swallow a request to open a new composer on that line. Runs on the EDT.
     */
    private fun focusLiveComposer(composerKey: ComposerKey): Boolean {
        composerSessions.values
            .filterNot(ComposerSession::isLive)
            .forEach { staleSession ->
                LOG.info("Disposed a stale comment composer: its editor or inlay is gone")
                Disposer.dispose(staleSession.disposable)
            }
        val openSession = composerSessions[composerKey] ?: return false
        with(openSession) {
            focusGeneration.value++
            host.requestComposeFocus()
        }
        return true
    }

    /**
     * The inline side [editor] comments against when it is one side of a registered diff viewer:
     * [InlineCommentSide.BEFORE] for the left (pre-image) editor of a side-by-side diff,
     * [InlineCommentSide.AFTER] for the right (source) editor of a side-by-side diff or the single editor of an
     * added-file oneside diff. Also scans viewers reached through a registered [DiffRequestProcessor]. Returns null
     * when [editor] is not a registered diff side (a plain work-tree file editor).
     */
    internal fun diffSideForEditor(editor: EditorEx): InlineCommentSide? {
        return sideOfEditor(editor = editor, viewers = registeredTextViewers + registeredProcessors)
    }

    /** Repaints the custom add-comment gutter after a composer opens or closes. */
    private fun refreshAddCommentGutters() {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            addCommentGutterEditors.forEach { editor -> editor.gutterComponentEx.repaint() }
        }
    }

    /**
     * Installs the platform add-comment gutter icon on [editor] for the fixed inline [side]. The highlighter, renderer
     * listeners, and gutter width all live for exactly [session], so a rediff or editor close releases the gutter width.
     */
    private fun installAddCommentGutter(editor: EditorEx, relativePath: String, pullRequestId: Long, side: InlineCommentSide, session: Disposable) {
        val model = PullRequestAddCommentGutterModel(
            canComment = { line ->
                line in 0 until editor.document.lineCount &&
                    PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
                        project = project,
                        file = editor.virtualFile,
                        pullRequestId = pullRequestId
                    )
            },
            onRequestNewComment = { line ->
                LOG.info("Add Review Comment via gutter icon for pull request #$pullRequestId at line ${line + 1} ($side)")
                openInlineLineComposer(
                    editor = editor,
                    relativePath = relativePath,
                    pullRequestId = pullRequestId,
                    fileOneBasedLine = line + 1,
                    side = side
                )
            }
        )
        // render() switches to the EDT itself and disposes its highlighter and renderer when the job is cancelled
        val job = coroutineScope.launch {
            CodeReviewEditorGutterControlsRenderer.render(model = model, editor = editor)
        }
        addCommentGutterEditors.add(editor)
        LOG.debug("Installed add-comment gutter for pull request #$pullRequestId on $side")
        Disposer.register(session) {
            addCommentGutterEditors.remove(editor)
            job.cancel()
            LOG.debug("Disposed add-comment gutter for pull request #$pullRequestId on $side")
        }
    }

    private fun scheduleAttach(source: FileEditorManager, file: VirtualFile) {
        AppExecutorUtil.getAppScheduledExecutorService().execute {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                source.getEditors(file).forEach { fileEditor -> attachIfNeeded(fileEditor = fileEditor) }
            }
        }
    }

    private fun attachIfNeeded(fileEditor: FileEditor) {
        when (fileEditor) {
            is TextEditor -> attachToTextEditor(textEditor = fileEditor)
            is DiffEditorViewerFileEditor -> attachToDiffEditor(fileEditor = fileEditor)
            else -> LOG.info("Pull request diff comments skipped: unsupported editor ${fileEditor.javaClass.simpleName}")
        }
    }

    private fun attachToDiffEditor(fileEditor: DiffEditorViewerFileEditor) {
        val editorViewer = fileEditor.editorViewer
        if (editorViewer is DiffRequestProcessor) {
            registerProcessor(processor = editorViewer)
            return
        }
        val commentable = PullRequestCommentableDiffViewer.from(viewer = editorViewer)
        if (commentable != null) {
            registerTextViewer(commentable = commentable, parentDisposable = commentable.viewer)
            return
        }
        LOG.info("Pull request diff comments skipped: unsupported diff editor viewer ${editorViewer.javaClass.simpleName}")
    }

    /**
     * Shows the same threads as the diff viewer in the real file editor. Only threads anchored on the source side are
     * placed here; pre-image threads have no line in the file on disk and stay in the diff viewer.
     */
    private fun attachToTextEditor(textEditor: TextEditor) {
        // Clear first so a reload that removes the last thread cannot leave a stale inlay behind.
        clearFileEditorSession(textEditor = textEditor)
        val virtualFile = textEditor.file
        // Decision 9a: the pull request summary tab is not a work-tree file and gets no leave-comment affordances.
        if (virtualFile is BbCloudPullRequestVirtualFile) {
            return
        }
        val pullRequestId = PullRequestCommentDecorationContext.resolvePullRequestId(project = project)
        if (pullRequestId == null) {
            LOG.info("Pull request file editor comments skipped: no active pull request")
            return
        }
        // Decision 4b: without a loaded index, request a reload and offer nothing until it lands.
        val index = PullRequestCommentDecorationContext.resolveInlineCommentIndex(project = project, pullRequestId = pullRequestId)
        if (index == null) {
            LOG.info("Pull request file editor comments skipped: inline comments not loaded for pull request #$pullRequestId")
            return
        }
        // Decision 6a: a non-reviewable pull request (no open-PR binding) gets no leave-comment affordances.
        if (!PullRequestReviewCommentTarget.isPullRequestReviewable(project = project, pullRequestId = pullRequestId)) {
            LOG.info("Pull request file editor comments skipped: pull request #$pullRequestId is not reviewable")
            return
        }
        val relativePath = PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = project.basePath,
            absoluteOrRelativePath = virtualFile.path
        )
        val anchors = index.anchorsForFileView(projectBasePath = project.basePath, fileRelativePath = relativePath)
        // Decision 4b/7.4: gutter controls attach once the index is loaded even when the file has zero threads, so the
        // green plus is available; the thread inlays and balloons are added only when there are source-side anchors.
        attachCommentsToFileEditor(
            editor = textEditor.editor,
            relativePath = relativePath,
            anchors = anchors,
            pullRequestId = pullRequestId,
            textEditor = textEditor
        )
    }

    private fun registerProcessor(processor: DiffRequestProcessor) {
        if (!registeredProcessors.add(processor)) {
            attachFromProcessor(processor = processor)
            return
        }
        processor.addListener(
            object : DiffRequestProcessorListener {
                override fun onViewerChanged() {
                    attachFromProcessor(processor = processor)
                }
            },
            processor
        )
        Disposer.register(processor) {
            registeredProcessors.remove(processor)
        }
        attachFromProcessor(processor = processor)
    }

    private fun registerTextViewer(commentable: PullRequestCommentableDiffViewer, parentDisposable: Disposable) {
        val textViewer = commentable.viewer
        if (!registeredTextViewers.add(textViewer)) {
            attachFromTextViewer(commentable = commentable, request = textViewer.request)
            return
        }
        textViewer.addListener(
            object : DiffViewerListener() {
                override fun onAfterRediff() {
                    // The viewer keeps the same editors across a rediff, so the wrapper can be reused.
                    attachFromTextViewer(commentable = commentable, request = textViewer.request)
                }
            }
        )
        Disposer.register(parentDisposable) {
            registeredTextViewers.remove(textViewer)
            clearSession(textViewer = textViewer)
        }
        attachFromTextViewer(commentable = commentable, request = textViewer.request)
    }

    private fun attachFromProcessor(processor: DiffRequestProcessor) {
        val activeViewer = processor.activeViewer
        val commentable = PullRequestCommentableDiffViewer.from(viewer = activeViewer)
        if (commentable == null) {
            LOG.info(
                "Pull request diff comments skipped: diff tool has no commentable text viewer (${activeViewer?.javaClass?.simpleName})"
            )
            return
        }
        val request = processor.activeRequest
        attachFromTextViewer(commentable = commentable, request = request)
    }

    private fun attachFromTextViewer(commentable: PullRequestCommentableDiffViewer, request: DiffRequest?) {
        val textViewer = commentable.viewer
        // Clear first so a rediff or comment reload that produces no anchors removes stale inlays.
        clearSession(textViewer = textViewer)
        val pullRequestId = PullRequestCommentDecorationContext.resolvePullRequestId(project = project)
        if (pullRequestId == null) {
            LOG.info("Pull request diff comments skipped: no active pull request")
            return
        }
        val index = PullRequestCommentDecorationContext.resolveInlineCommentIndex(project = project, pullRequestId = pullRequestId)
        if (index == null) {
            LOG.info("Pull request diff comments skipped: inline comments not loaded for pull request #$pullRequestId")
            return
        }

        // Decision 6a: a non-reviewable pull request gets no leave-comment affordances in the diff viewer either.
        if (!PullRequestReviewCommentTarget.isPullRequestReviewable(project = project, pullRequestId = pullRequestId)) {
            LOG.info("Pull request diff comments skipped: pull request #$pullRequestId is not reviewable")
            return
        }

        val change = resolveChange(request = request, viewer = textViewer)
        val relativePath = resolveRelativePath(change = change, request = request)
        if (relativePath == null) {
            LOG.info("Pull request diff comments skipped: could not resolve file path for diff")
            return
        }

        val anchors = index.anchorsForPath(projectBasePath = project.basePath, changeRelativePath = relativePath)
        if (anchors.isEmpty()) {
            val indexedPaths = index.indexedPaths()
            if (indexedPaths.isNotEmpty()) {
                LOG.info(
                    "Pull request diff comments: no inline threads for '$relativePath'; installing add-comment gutter only " +
                        "(indexed paths: ${indexedPaths.joinToString()})"
                )
            } else {
                LOG.info("Pull request diff comments: pull request #$pullRequestId has no inline comment threads; installing add-comment gutter only")
            }
        }

        // Decision 4b/7.4: gutter controls attach once the index is loaded even when this file has zero threads.
        attachComments(commentable = commentable, relativePath = relativePath, anchors = anchors, pullRequestId = pullRequestId)
    }

    private fun attachComments(commentable: PullRequestCommentableDiffViewer, relativePath: String, anchors: List<InlineCommentAnchor>, pullRequestId: Long) {
        val viewer = commentable.viewer
        // Remember a composer that currently has keyboard focus before the rebuild disposes its host, so focus can be
        // restored into the rebuilt card.
        rememberFocusedComposer()
        clearSession(textViewer = viewer)
        val viewerKind = if (commentable.isOneside) "oneside" else "side-by-side"
        LOG.info(
            "Attaching ${anchors.size} inline comment thread(s) to $viewerKind diff for pull request #$pullRequestId at $relativePath"
        )
        val session = newSession()
        viewerSessions[viewer] = session
        // Decision 1b/3.2: the AFTER (source) editor is the only diff pane with the hover icon.
        val afterEditor = commentable.editorFor(side = InlineCommentSide.AFTER)
        if (afterEditor != null && shouldOfferAddCommentGutter(side = InlineCommentSide.AFTER, isFileEditor = false)) {
            installAddCommentGutter(
                editor = afterEditor,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                side = InlineCommentSide.AFTER,
                session = session
            )
        }
        val insertedInlays = newInlayList(session = session)
        // Edge case 5c: BEFORE anchors with no BEFORE editor (an added-file oneside diff) are shown at the top of the
        // AFTER editor, like a file-scoped thread; they are also indicated at line 0 below.
        val topOfFileAnchors = mutableListOf<InlineCommentAnchor>()
        anchors.forEachIndexed { index, anchor ->
            val editorForSide = commentable.editorFor(side = anchor.side)
            val forceTopOfFile = editorForSide == null && commentable.isOneside && anchor.side == InlineCommentSide.BEFORE
            val targetEditor = editorForSide ?: if (forceTopOfFile) afterEditor else null
            if (targetEditor == null) {
                LOG.debug(
                    "Inline comment thread on ${anchor.side} line ${anchor.startLine} of $relativePath skipped: no editor for that side"
                )
                return@forEachIndexed
            }
            if (forceTopOfFile) {
                topOfFileAnchors += anchor
                LOG.debug(
                    "Inline comment thread on BEFORE line ${anchor.startLine} of added file $relativePath shown at top of file"
                )
            }
            installThread(
                editor = targetEditor,
                anchor = anchor,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                priority = anchors.size - index,
                session = session,
                insertedInlays = insertedInlays,
                forceTopOfFile = forceTopOfFile
            )
        }
        commentable.editorFor(side = InlineCommentSide.AFTER)?.let { editor ->
            installCommentLineIndicators(
                editor = editor,
                anchors = anchors.filter { anchor -> anchor.side == InlineCommentSide.AFTER },
                session = session,
                topOfFileAnchors = topOfFileAnchors
            )
        }
        commentable.editorFor(side = InlineCommentSide.BEFORE)?.let { editor ->
            installCommentLineIndicators(
                editor = editor,
                anchors = anchors.filter { anchor -> anchor.side == InlineCommentSide.BEFORE },
                session = session
            )
        }
        closeComposerIfOrphaned(
            controller = controllerFor(relativePath = relativePath, pullRequestId = pullRequestId),
            anchors = anchors,
            pullRequestId = pullRequestId
        )
    }

    /** Installs the add-comment gutter icon plus the same threads as the diff viewer into the real file editor. */
    private fun attachCommentsToFileEditor(
        editor: Editor,
        relativePath: String,
        anchors: List<InlineCommentAnchor>,
        pullRequestId: Long,
        textEditor: TextEditor
    ) {
        LOG.info(
            "Attaching ${anchors.size} inline comment thread(s) to file editor for pull request #$pullRequestId at $relativePath"
        )
        // Remember a composer that currently has keyboard focus before the rebuild disposes its host.
        rememberFocusedComposer()
        clearFileEditorSession(textEditor = textEditor)
        val session = newSession()
        fileEditorSessions[textEditor] = session
        val controller = controllerFor(relativePath = relativePath, pullRequestId = pullRequestId)
        // The file on disk is the pull request source, so its gutter icon anchors to the AFTER side (inline.to).
        if (editor is EditorEx && shouldOfferAddCommentGutter(side = InlineCommentSide.AFTER, isFileEditor = true)) {
            installAddCommentGutter(
                editor = editor,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                side = InlineCommentSide.AFTER,
                session = session
            )
        }
        if (anchors.isEmpty()) {
            closeComposerIfOrphaned(controller = controller, anchors = anchors, pullRequestId = pullRequestId)
            return
        }
        val insertedInlays = newInlayList(session = session)
        anchors.forEachIndexed { index, anchor ->
            installThread(
                editor = editor,
                anchor = anchor,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                priority = anchors.size - index,
                session = session,
                insertedInlays = insertedInlays
            )
        }
        installCommentLineIndicators(editor = editor, anchors = anchors, session = session)
        closeComposerIfOrphaned(controller = controller, anchors = anchors, pullRequestId = pullRequestId)
    }

    /**
     * If the keyboard focus is inside an inline thread host whose composer is open, marks a focus restore for that
     * host's editor and comment, so the rebuilt card takes focus again. Top-of-file and line composers live in
     * [composerSessions] and are not rebuilt, so they are intentionally not handled here. Runs on the EDT.
     */
    private fun rememberFocusedComposer() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
        val host = SwingUtilities.getAncestorOfClass(DiffCommentThreadHost::class.java, focusOwner) as? DiffCommentThreadHost ?: return
        val hostThread = hostThreads[host] ?: return
        val target = hostThread.controller.openComposer ?: return
        if (target.commentId in hostThread.threadCommentIds) {
            hostThread.controller.markFocusRestore(editor = hostThread.editor, commentId = target.commentId)
            LOG.debug("Remembering focused composer of comment ${target.commentId} for focus restore after rebuild")
        }
    }

    /**
     * Closes the controller's open composer when its comment is no longer shown by any of [anchors] (deleted remotely,
     * or resolved and collapsed), so no orphaned composer lingers hidden. Uses the full anchor list of the attach, not
     * only the threads installed successfully, so a thread skipped because its side has no editor does not close a
     * composer that is still shown in another editor.
     */
    private fun closeComposerIfOrphaned(controller: EditorCommentThreadController, anchors: List<InlineCommentAnchor>, pullRequestId: Long) {
        val openCommentId = controller.openComposer?.commentId ?: return
        val shownIds = anchors.flatMap { anchor -> threadCommentIds(anchor = anchor) }.toSet()
        if (ComposerDraftCache.isOrphaned(openCommentId = openCommentId, shownIds = shownIds)) {
            LOG.info("Closed the open composer of comment $openCommentId in pull request #$pullRequestId: the comment is no longer shown")
            controller.closeComposer()
        }
    }

    private fun installThread(
        editor: Editor,
        anchor: InlineCommentAnchor,
        relativePath: String,
        pullRequestId: Long,
        priority: Int,
        session: Disposable,
        insertedInlays: MutableList<Inlay<*>>,
        forceTopOfFile: Boolean = false
    ) {
        // A thread forced to the top of the file (an orphan BEFORE anchor on an added file) does not map its line into
        // this editor: the pre-image line number does not refer to the added file, so line 0 is used directly.
        val mappedLine = if (forceTopOfFile) {
            null
        } else {
            mapFileLineToEditorLine(fileOneBasedLine = anchor.startLine, editor = editor)
        }
        val inlayLine = threadInlayLine(anchor = anchor, mappedLine = mappedLine, forceTopOfFile = forceTopOfFile)
        if (inlayLine == null) {
            LOG.debug("Could not map Bitbucket line ${anchor.startLine} for $relativePath on ${anchor.side}; skipping thread inlay")
            return
        }
        if (editor !is EditorEx) {
            LOG.debug("Inline comment thread for pull request #$pullRequestId skipped at $relativePath: editor is not an EditorEx")
            return
        }
        installInlay(
            editor = editor,
            editorLine = inlayLine,
            anchor = anchor,
            pullRequestId = pullRequestId,
            relativePath = relativePath,
            priority = priority,
            insertedInlays = insertedInlays
        )
        prefetchThreadAvatars(anchor = anchor, pullRequestId = pullRequestId)
    }

    private fun newSession(): Disposable {
        return Disposer.newDisposable("BbCloudPullRequestComments")
    }

    /** Returns the inlay list of [session] and makes the session dispose every inlay it holds. */
    private fun newInlayList(session: Disposable): MutableList<Inlay<*>> {
        val insertedInlays = mutableListOf<Inlay<*>>()
        Disposer.register(session) {
            insertedInlays.forEach { inlay -> Disposer.dispose(inlay) }
            insertedInlays.clear()
        }
        return insertedInlays
    }

    /** Downloads the avatars of the thread; the editors are refreshed once they land so placeholders are replaced. */
    private fun prefetchThreadAvatars(anchor: InlineCommentAnchor, pullRequestId: Long) {
        val urls = listOfNotNull(anchor.thread.root.author?.avatarUrl) +
            anchor.thread.replies.mapNotNull { reply -> reply.author?.avatarUrl }
        if (urls.isEmpty()) {
            return
        }
        avatarCache.prefetch(
            urls = urls,
            coroutineScope = coroutineScope,
            onLoaded = {
                LOG.debug("Refreshing inline comment threads of pull request #$pullRequestId after avatars loaded")
                refreshOpenEditors()
            }
        )
    }

    private fun resolveChange(request: DiffRequest?, viewer: DiffViewerBase): Change? {
        return viewer.request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY)
            ?: request?.getUserData(ChangeDiffRequestProducer.CHANGE_KEY)
    }

    private fun resolveRelativePath(change: Change?, request: DiffRequest?): String? {
        if (change != null) {
            changeRelativePath(change = change)?.let { path -> return path }
        }
        return relativePathFromDiffRequest(request = request)
    }

    private fun relativePathFromDiffRequest(request: DiffRequest?): String? {
        if (request !is ContentDiffRequest) {
            return null
        }
        val paths = request.contents.mapNotNull { content ->
            (content as? DocumentContent)?.highlightFile?.path
        }
        if (paths.isEmpty()) {
            return null
        }
        val projectBasePath = project.basePath
        return paths
            .map { path -> PullRequestPathMatcher.relativeProjectPath(projectBasePath = projectBasePath, absoluteOrRelativePath = path) }
            .firstOrNull { path -> path.isNotBlank() }
    }

    private fun installCommentLineIndicators(
        editor: Editor,
        anchors: List<InlineCommentAnchor>,
        session: Disposable,
        topOfFileAnchors: List<InlineCommentAnchor> = emptyList()
    ) {
        val allAnchors = anchors + topOfFileAnchors
        if (allAnchors.isEmpty()) {
            return
        }
        val topOfFileAnchorSet = topOfFileAnchors.toSet()
        val ranges = indicatorRangesForAnchors(anchors = allAnchors) { anchor ->
            if (anchor in topOfFileAnchorSet) {
                0..0
            } else {
                mapFileLineRangeToEditorLines(
                    startOneBasedLine = anchor.startLine,
                    endOneBasedLine = anchor.endLine,
                    editor = editor
                )
            }
        }
        if (ranges.isEmpty()) {
            return
        }
        applyCommentLineIndicatorContentInset(editor = editor, session = session)
        val markupModel = editor.markupModel
        ranges.forEach { range ->
            val startOffset = if (editor.document.lineCount == 0) {
                0
            } else {
                editor.document.getLineStartOffset(range.startLine)
            }
            val endOffset = if (editor.document.lineCount == 0) {
                0
            } else {
                editor.document.getLineEndOffset(range.endLine)
            }
            val highlighter = markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                DiffDrawUtil.LST_LINE_MARKER_LAYER,
                null,
                HighlighterTargetArea.LINES_IN_RANGE
            ).also { installedHighlighter ->
                installedHighlighter.customRenderer = PullRequestCommentLineIndicatorRenderer(
                    startLine = range.startLine,
                    endLine = range.endLine
                )
            }
            Disposer.register(session) {
                markupModel.removeHighlighter(highlighter)
            }
        }
    }

    private fun applyCommentLineIndicatorContentInset(editor: Editor, session: Disposable) {
        val content = editor.contentComponent
        val previousBorder = content.border
        content.border = JBUI.Borders.emptyLeft(commentLineIndicatorContentInsetPx())
        Disposer.register(session) {
            content.border = previousBorder
        }
    }

    private fun mapFileLineRangeToEditorLines(startOneBasedLine: Int, endOneBasedLine: Int, editor: Editor): IntRange? {
        val lineCount = editor.document.lineCount
        if (lineCount == 0) {
            return 0..0
        }
        if (startOneBasedLine < 1 || endOneBasedLine < startOneBasedLine || startOneBasedLine > lineCount || endOneBasedLine > lineCount) {
            return null
        }
        return (startOneBasedLine - 1)..(endOneBasedLine - 1)
    }

    private fun installInlay(
        editor: EditorEx,
        editorLine: Int,
        anchor: InlineCommentAnchor,
        pullRequestId: Long,
        relativePath: String,
        priority: Int,
        insertedInlays: MutableList<Inlay<*>>
    ) {
        val host = DiffCommentThreadHost(editor = editor)
        var composePanel: ComposePanel? = null
        val controller = controllerFor(relativePath = relativePath, pullRequestId = pullRequestId)
        controller.prefetch(thread = anchor.thread)
        val threadCommentIds = threadCommentIds(anchor = anchor)
        // A rebuild during an open composer marks a focus restore for this editor and comment; consume it here so the
        // rebuilt card takes keyboard focus once.
        val restoreFocus = controller.consumeFocusRestore(editor = editor, threadCommentIds = threadCommentIds)
        val content = compose(config = { composePanel = this }) {
            DiffInlineCommentThreadCard(
                thread = anchor.thread,
                host = host,
                controller = controller,
                composerScope = editor,
                requestComposerFocus = restoreFocus
            )
        }
        // The inlay takes its height from the preferred size of the host, and the host measures the Compose content at
        // the full editor viewport text width, relaying the inlay on viewport and content resizes. Otherwise the
        // content would be measured unconstrained and report the height of a single line.
        composePanel?.let { panel -> host.attachComposePanel(panel = panel) }
        host.notifyEmbeddedHeightChanged()
        val inlay = editor.insertComponentAfter(editorLine, host, priority)
        if (inlay == null) {
            LOG.warn(
                "Could not insert inline comment inlay for pull request #$pullRequestId at $relativePath " +
                    "line ${anchor.startLine} on ${anchor.side}"
            )
            return
        }
        hostThreads[host] = HostThread(controller = controller, editor = editor, threadCommentIds = threadCommentIds)
        Disposer.register(inlay) { hostThreads.remove(host) }
        scheduleDeferredInlayRemeasure(host = host)
        insertedInlays.add(inlay)
    }

    /** The root id plus the reply ids of the thread an anchor carries. */
    private fun threadCommentIds(anchor: InlineCommentAnchor): Set<Long> {
        return (listOf(anchor.thread.root.id) + anchor.thread.replies.map { reply -> reply.id }).toSet()
    }

    /** When attach runs before the editor viewport is laid out, remeasure once on the next EDT tick. */
    private fun scheduleDeferredInlayRemeasure(host: DiffCommentThreadHost) {
        if (host.currentInlayWidth() > 0) {
            return
        }
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            host.remeasureInlayWidth()
        }
    }

    private fun controllerFor(relativePath: String, pullRequestId: Long): EditorCommentThreadController {
        val key = "$pullRequestId:${PullRequestPathMatcher.normalize(relativePath)}"
        return controllers.computeIfAbsent(key) {
            EditorCommentThreadController(
                projectValue = project,
                pullRequestIdValue = pullRequestId,
                relativePath = relativePath,
                coroutineScope = coroutineScope
            )
        }
    }

    private fun changeRelativePath(change: Change): String? {
        val filePath = ChangesUtil.getFilePath(change)
        return PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = project.basePath,
            absoluteOrRelativePath = filePath.path
        )
    }

    private fun mapFileLineToEditorLine(fileOneBasedLine: Int, editor: Editor): Int? {
        if (fileOneBasedLine <= 0) {
            return null
        }
        val zeroBased = fileOneBasedLine - 1
        if (editor.document.lineCount == 0 && fileOneBasedLine == 1) {
            return 0
        }
        if (zeroBased >= editor.document.lineCount) {
            return null
        }
        return zeroBased
    }

    private fun clearComposerSessions(fileEditor: FileEditor) {
        val editor = when (fileEditor) {
            is TextEditor -> fileEditor.editor as? EditorEx
            is DiffEditorViewerFileEditor -> when (val viewer = fileEditor.editorViewer) {
                is DiffRequestProcessor -> {
                    PullRequestCommentableDiffViewer.from(viewer = viewer.activeViewer)?.editorFor(side = InlineCommentSide.AFTER)
                }
                else -> {
                    PullRequestCommentableDiffViewer.from(viewer = viewer)?.editorFor(side = InlineCommentSide.AFTER)
                }
            }
            else -> null
        } ?: return
        composerSessions.values
            .filter { composerSession -> composerSession.editor === editor }
            .forEach { composerSession -> Disposer.dispose(composerSession.disposable) }
    }

    private fun clearFileEditorSession(fileEditor: FileEditor, releaseDrafts: Boolean = false) {
        when (fileEditor) {
            is TextEditor -> clearFileEditorSession(textEditor = fileEditor, releaseDrafts = releaseDrafts)
            is DiffEditorViewerFileEditor -> clearDiffFileEditorSession(fileEditor = fileEditor, releaseDrafts = releaseDrafts)
        }
    }

    private fun clearDiffFileEditorSession(fileEditor: DiffEditorViewerFileEditor, releaseDrafts: Boolean = false) {
        val editorViewer = fileEditor.editorViewer
        if (editorViewer is DiffRequestProcessor) {
            registeredProcessors.remove(editorViewer)
            PullRequestCommentableDiffViewer.from(viewer = editorViewer.activeViewer)?.let { commentable ->
                clearSession(textViewer = commentable.viewer)
                registeredTextViewers.remove(commentable.viewer)
                if (releaseDrafts) {
                    forgetEditorScopes(commentable = commentable)
                }
            }
            return
        }
        PullRequestCommentableDiffViewer.from(viewer = editorViewer)?.let { commentable ->
            clearSession(textViewer = commentable.viewer)
            registeredTextViewers.remove(commentable.viewer)
            if (releaseDrafts) {
                forgetEditorScopes(commentable = commentable)
            }
        }
    }

    private fun clearFileEditorSession(textEditor: TextEditor, releaseDrafts: Boolean = false) {
        fileEditorSessions.remove(textEditor)?.let { session -> Disposer.dispose(session) }
        if (releaseDrafts) {
            (textEditor.editor as? EditorEx)?.let { editor -> forgetEditorScope(editor = editor) }
        }
    }

    /** Drops the cached composer drafts whose scope is [editor] from every controller, when that editor is closed. */
    private fun forgetEditorScope(editor: EditorEx) {
        controllers.values.forEach { controller -> controller.forgetScope(scope = editor) }
    }

    /** Drops the cached composer drafts of both diff sides of [commentable] when the diff editor is closed. */
    private fun forgetEditorScopes(commentable: PullRequestCommentableDiffViewer) {
        commentable.editorFor(side = InlineCommentSide.AFTER)?.let { editor -> forgetEditorScope(editor = editor) }
        commentable.editorFor(side = InlineCommentSide.BEFORE)?.let { editor -> forgetEditorScope(editor = editor) }
    }

    private fun clearSession(textViewer: DiffViewerBase) {
        viewerSessions.remove(textViewer)?.let { session -> Disposer.dispose(session) }
    }

    override fun dispose() {
        viewerSessions.values.forEach { session -> Disposer.dispose(session) }
        viewerSessions.clear()
        fileEditorSessions.values.forEach { session -> Disposer.dispose(session) }
        fileEditorSessions.clear()
        addCommentGutterEditors.clear()
        registeredTextViewers.clear()
        registeredProcessors.clear()
        controllers.clear()
        composerSessions.values.forEach { composerSession -> Disposer.dispose(composerSession.disposable) }
        composerSessions.clear()
        coroutineScope.cancel()
    }

    /** A composer is scoped to one editor, so the same line can have a composer in the file editor and in the diff. */
    private data class ComposerKey(val scope: String, val editor: EditorEx)

    /**
     * [side] and [editorLine] are set only for line-anchored composers; the file-scoped composer leaves them null.
     */
    private data class ComposerSession(
        val disposable: Disposable,
        val host: DiffCommentThreadHost,
        val editor: EditorEx,
        val side: InlineCommentSide? = null,
        val editorLine: Int? = null,
        // Bumped when an already-open composer is re-triggered, so its text area re-requests keyboard focus. A Compose
        // state so the composer recomposes and its focus LaunchedEffect(focusRequestKey) re-runs. EDT-only.
        val focusGeneration: MutableState<Int> = mutableStateOf(0)
    ) {
        /** Set once the composer inlay is inserted; the composer is shown only while this inlay is valid. EDT-only. */
        var inlay: Inlay<*>? = null

        val isLive: Boolean
            get() {
                return isComposerSessionLive(isEditorDisposed = editor.isDisposed, isInlayValid = inlay?.isValid == true)
            }
    }

    /** Identifies the thread an inline host shows, so a rebuild can restore focus into the composer of that thread. */
    private data class HostThread(val controller: EditorCommentThreadController, val editor: EditorEx, val threadCommentIds: Set<Long>)

    companion object {
        private val LOG = logger<BbCloudPullRequestDiffCommentService>()

        /** Inline threads sit in the editor gutter area, so a smaller avatar than the activity list keeps them compact. */
        private const val AVATAR_SIZE = 16

        fun getInstance(project: Project): BbCloudPullRequestDiffCommentService {
            return project.getService(BbCloudPullRequestDiffCommentService::class.java)
        }
    }
}