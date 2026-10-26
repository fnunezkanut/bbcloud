package com.github.fnunezkanut.bbcloud.ui.diff

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.layout.onSizeChanged
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
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.editor.insertComponentAfter
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessorListener
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.Side
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
import org.jetbrains.jewel.bridge.compose
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class BbCloudPullRequestDiffCommentService(private val project: Project) : Disposable {
    private val registeredTextViewers = ConcurrentHashMap.newKeySet<DiffViewerBase>()
    private val registeredProcessors = ConcurrentHashMap.newKeySet<DiffRequestProcessor>()
    private val viewerSessions = ConcurrentHashMap<DiffViewerBase, Disposable>()
    private val fileEditorSessions = ConcurrentHashMap<TextEditor, Disposable>()
    private val avatarCache = AuthorAvatarCache(avatarSize = AVATAR_SIZE)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controllers = ConcurrentHashMap<String, EditorCommentThreadController>()
    private val composerSessions = ConcurrentHashMap<String, ComposerSession>()
    private val addCommentGutterEditors = ConcurrentHashMap.newKeySet<EditorEx>()

    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    scheduleAttach(source = source, file = file)
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    source.getEditors(file).forEach { editor ->
                        clearFileEditorSession(fileEditor = editor)
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
        val key = "$pullRequestId:${PullRequestPathMatcher.normalize(relativePath)}"
        if (composerSessions[key] != null) {
            composerSessions[key]?.host?.requestFocusInWindow()
            LOG.info("File comment composer already open for $relativePath in pull request #$pullRequestId; focused it")
            return
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val session = newSession()
        val host = DiffCommentThreadHost(editor = editor)
        val composerSession = ComposerSession(disposable = session, host = host, editor = editor)
        composerSessions[key] = composerSession
        Disposer.register(session) { composerSessions.remove(key, composerSession) }
        val input = CommentInput(
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
        val composerViewModel = CommentComposerViewModel(input = input, startsExpanded = true)
        var composePanel: ComposePanel? = null
        val content = compose(config = { composePanel = this }) {
            CommentComposerScreen(
                viewModel = composerViewModel,
                avatarIcon = avatarCache.iconFor(context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl),
                showsCollapsedField = false,
                onCollapsed = { Disposer.dispose(session) },
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        host.syncComposeContentHeight(contentHeightPx = size.height)
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
            LOG.warn("Could not insert file comment composer for pull request #$pullRequestId at $relativePath")
            return
        }
        scheduleDeferredInlayRemeasure(host = host)
        Disposer.register(session) { Disposer.dispose(inlay) }
        LOG.debug("Opened file comment composer for pull request #$pullRequestId at $relativePath")
    }

    /**
     * Opens a line-anchored inline comment composer for [fileOneBasedLine] (one-based) on [side], mirroring
     * [openFileScopedComposer]. Dedupes on the per-line session key so re-triggering focuses the open composer
     * (decision 5a). The composer submits through [BbCloudWorktreePullRequestContext.submitInlineLineComment] with the
     * correct Bitbucket inline side.
     */
    fun openInlineLineComposer(editor: EditorEx, relativePath: String, pullRequestId: Long, fileOneBasedLine: Int, side: InlineCommentSide) {
        val normalizedPath = PullRequestPathMatcher.normalize(relativePath)
        val key = "$pullRequestId:$normalizedPath:$side:$fileOneBasedLine"
        if (composerSessions[key] != null) {
            composerSessions[key]?.host?.requestFocusInWindow()
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
        composerSessions[key] = composerSession
        Disposer.register(session) {
            composerSessions.remove(key, composerSession)
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
            CommentComposerScreen(
                viewModel = composerViewModel,
                avatarIcon = avatarCache.iconFor(context.binding?.currentUserAvatarUrl ?: context.currentUserAvatarUrl),
                showsCollapsedField = false,
                onCollapsed = { Disposer.dispose(session) },
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { size ->
                        host.syncComposeContentHeight(contentHeightPx = size.height)
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
        scheduleDeferredInlayRemeasure(host = host)
        Disposer.register(session) { Disposer.dispose(inlay) }
        refreshAddCommentGutters()
        LOG.debug("Opened line comment composer for pull request #$pullRequestId at $relativePath:$fileOneBasedLine on $side")
    }

    /**
     * The inline side [editor] comments against when it is one side of a registered side-by-side diff viewer:
     * [InlineCommentSide.BEFORE] for the left (pre-image) editor, [InlineCommentSide.AFTER] for the right (source)
     * editor. Returns null when [editor] is not a registered diff side (a plain work-tree file editor).
     */
    internal fun diffSideForEditor(editor: EditorEx): InlineCommentSide? {
        registeredTextViewers.forEach { textViewer ->
            if (textViewer is TwosideTextDiffViewer) {
                if (textViewer.getEditor(Side.LEFT) === editor) {
                    return InlineCommentSide.BEFORE
                }
                if (textViewer.getEditor(Side.RIGHT) === editor) {
                    return InlineCommentSide.AFTER
                }
            }
        }
        return null
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
     * Installs the custom green chat icon on [editor] for the fixed inline [side]. The highlighter, renderer listeners,
     * and free-painters reservation all live for exactly [session], so a rediff or editor close releases the gutter width.
     */
    private fun installAddCommentGutter(editor: EditorEx, relativePath: String, pullRequestId: Long, side: InlineCommentSide, session: Disposable) {
        val renderer = PullRequestAddCommentGutterRenderer(
            project = project,
            editor = editor,
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            side = side,
            service = this
        )
        val highlighter = editor.markupModel.addRangeHighlighter(
            null,
            0,
            editor.document.textLength,
            DiffDrawUtil.LST_LINE_MARKER_LAYER,
            HighlighterTargetArea.LINES_IN_RANGE
        ).apply {
            setGreedyToLeft(true)
            setGreedyToRight(true)
            setLineMarkerRenderer(renderer)
        }
        editor.gutterComponentEx.reserveLeftFreePaintersAreaWidth(
            session,
            addCommentGutterReserveWidthPx()
        )
        addCommentGutterEditors.add(editor)
        Disposer.register(session) {
            addCommentGutterEditors.remove(editor)
            highlighter.dispose()
            Disposer.dispose(renderer)
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
        when (val editorViewer = fileEditor.editorViewer) {
            is TwosideTextDiffViewer -> registerTextViewer(textViewer = editorViewer, parentDisposable = editorViewer.disposable)
            is DiffRequestProcessor -> registerProcessor(processor = editorViewer)
            else -> LOG.info("Pull request diff comments skipped: unsupported diff editor viewer ${editorViewer.javaClass.simpleName}")
        }
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

    private fun registerTextViewer(textViewer: TwosideTextDiffViewer, parentDisposable: Disposable) {
        if (!registeredTextViewers.add(textViewer)) {
            attachFromTextViewer(textViewer = textViewer, request = textViewer.request)
            return
        }
        textViewer.addListener(
            object : DiffViewerListener() {
                override fun onAfterRediff() {
                    attachFromTextViewer(textViewer = textViewer, request = textViewer.request)
                }
            }
        )
        Disposer.register(parentDisposable) {
            registeredTextViewers.remove(textViewer)
            clearSession(textViewer = textViewer)
        }
        attachFromTextViewer(textViewer = textViewer, request = textViewer.request)
    }

    private fun attachFromProcessor(processor: DiffRequestProcessor) {
        val activeViewer = processor.activeViewer
        val textViewer = resolveTwosideTextDiffViewer(activeViewer = activeViewer)
        if (textViewer == null) {
            LOG.info("Pull request diff comments skipped: diff tool has no side-by-side text viewer yet")
            return
        }
        val request = processor.activeRequest
        attachFromTextViewer(textViewer = textViewer, request = request)
    }

    private fun attachFromTextViewer(textViewer: TwosideTextDiffViewer, request: DiffRequest?) {
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

        val change = resolveChange(request = request, textViewer = textViewer)
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
        attachComments(textViewer = textViewer, relativePath = relativePath, anchors = anchors, pullRequestId = pullRequestId)
    }

    private fun attachComments(textViewer: TwosideTextDiffViewer, relativePath: String, anchors: List<InlineCommentAnchor>, pullRequestId: Long) {
        clearSession(textViewer = textViewer)
        LOG.info("Attaching ${anchors.size} inline comment thread(s) to diff for pull request #$pullRequestId at $relativePath")
        val session = newSession()
        viewerSessions[textViewer] = session
        // Decision 1b/3.2: the right editor is the source (AFTER) side and is the only diff pane with the hover icon.
        if (shouldOfferAddCommentGutter(side = InlineCommentSide.AFTER, isFileEditor = false)) {
            installAddCommentGutter(
                editor = textViewer.getEditor(Side.RIGHT),
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                side = InlineCommentSide.AFTER,
                session = session
            )
        }
        val insertedInlays = newInlayList(session = session)
        anchors.forEachIndexed { index, anchor ->
            val side = anchor.side.toDiffSide()
            installThread(
                editor = textViewer.getEditor(side),
                anchor = anchor,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                priority = anchors.size - index,
                session = session,
                insertedInlays = insertedInlays
            )
        }
        installCommentLineIndicators(
            editor = textViewer.getEditor(Side.RIGHT),
            anchors = anchors.filter { anchor -> anchor.side == InlineCommentSide.AFTER },
            session = session
        )
        installCommentLineIndicators(
            editor = textViewer.getEditor(Side.LEFT),
            anchors = anchors.filter { anchor -> anchor.side == InlineCommentSide.BEFORE },
            session = session
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
        clearFileEditorSession(textEditor = textEditor)
        val session = newSession()
        fileEditorSessions[textEditor] = session
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
    }

    private fun installThread(
        editor: Editor,
        anchor: InlineCommentAnchor,
        relativePath: String,
        pullRequestId: Long,
        priority: Int,
        session: Disposable,
        insertedInlays: MutableList<Inlay<*>>
    ) {
        val editorLine = mapFileLineToEditorLine(fileOneBasedLine = anchor.startLine, editor = editor) ?: run {
            LOG.debug("Could not map Bitbucket line ${anchor.startLine} for $relativePath on ${anchor.side}; skipping thread inlay")
            return
        }
        if (editor !is EditorEx) {
            LOG.debug("Inline comment thread for pull request #$pullRequestId skipped at $relativePath: editor is not an EditorEx")
            return
        }
        val inlayLine = if (anchor.isFileScoped) 0 else editorLine
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

    private fun resolveChange(request: DiffRequest?, textViewer: TwosideTextDiffViewer): Change? {
        return textViewer.request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY)
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

    private fun resolveTwosideTextDiffViewer(activeViewer: FrameDiffTool.DiffViewer?): TwosideTextDiffViewer? {
        return activeViewer as? TwosideTextDiffViewer
    }

    private fun installCommentLineIndicators(editor: Editor, anchors: List<InlineCommentAnchor>, session: Disposable) {
        if (anchors.isEmpty()) {
            return
        }
        val ranges = indicatorRangesForAnchors(anchors = anchors) { anchor ->
            mapFileLineRangeToEditorLines(
                startOneBasedLine = anchor.startLine,
                endOneBasedLine = anchor.endLine,
                editor = editor
            )
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
        val content = compose(config = { composePanel = this }) {
            DiffInlineCommentThreadCard(
                thread = anchor.thread,
                host = host,
                controller = controller
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
        scheduleDeferredInlayRemeasure(host = host)
        insertedInlays.add(inlay)
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

    private fun InlineCommentSide.toDiffSide(): Side {
        return when (this) {
            InlineCommentSide.BEFORE -> Side.LEFT
            InlineCommentSide.AFTER -> Side.RIGHT
        }
    }

    private fun clearComposerSessions(fileEditor: FileEditor) {
        val editor = when (fileEditor) {
            is TextEditor -> fileEditor.editor as? EditorEx
            is DiffEditorViewerFileEditor -> when (val viewer = fileEditor.editorViewer) {
                is TwosideTextDiffViewer -> viewer.getEditor(Side.RIGHT)
                is DiffRequestProcessor -> (viewer.activeViewer as? TwosideTextDiffViewer)?.getEditor(Side.RIGHT)
                else -> null
            }
            else -> null
        } ?: return
        composerSessions.values
            .filter { composerSession -> composerSession.editor === editor }
            .forEach { composerSession -> Disposer.dispose(composerSession.disposable) }
    }

    private fun clearFileEditorSession(fileEditor: FileEditor) {
        when (fileEditor) {
            is TextEditor -> clearFileEditorSession(textEditor = fileEditor)
            is DiffEditorViewerFileEditor -> clearDiffFileEditorSession(fileEditor = fileEditor)
        }
    }

    private fun clearDiffFileEditorSession(fileEditor: DiffEditorViewerFileEditor) {
        when (val editorViewer = fileEditor.editorViewer) {
            is TwosideTextDiffViewer -> {
                clearSession(textViewer = editorViewer)
                registeredTextViewers.remove(editorViewer)
            }
            is DiffRequestProcessor -> {
                registeredProcessors.remove(editorViewer)
                resolveTwosideTextDiffViewer(activeViewer = editorViewer.activeViewer)?.let { textViewer ->
                    clearSession(textViewer = textViewer)
                    registeredTextViewers.remove(textViewer)
                }
            }
        }
    }

    private fun clearFileEditorSession(textEditor: TextEditor) {
        fileEditorSessions.remove(textEditor)?.let { session -> Disposer.dispose(session) }
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

    /**
     * [side] and [editorLine] are set only for line-anchored composers; the file-scoped composer leaves them null.
     */
    private data class ComposerSession(
        val disposable: Disposable,
        val host: DiffCommentThreadHost,
        val editor: EditorEx,
        val side: InlineCommentSide? = null,
        val editorLine: Int? = null
    )

    companion object {
        private val LOG = logger<BbCloudPullRequestDiffCommentService>()

        /** Inline threads sit in the editor gutter area, so a smaller avatar than the activity list keeps them compact. */
        private const val AVATAR_SIZE = 16

        fun getInstance(project: Project): BbCloudPullRequestDiffCommentService {
            return project.getService(BbCloudPullRequestDiffCommentService::class.java)
        }
    }
}