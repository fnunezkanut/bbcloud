package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * A Bitbucket inline comment range. Lines are one-based and inclusive on [side]. Bitbucket Cloud reports a range with
 * `start_to`/`to` or `start_from`/`from`; comments without those start fields are single-line anchors.
 */
data class InlineCommentAnchor(
    val startLine: Int,
    val endLine: Int,
    val side: InlineCommentSide,
    val thread: TimelineEntry.CommentThread,
    val isFileScoped: Boolean = false
)

/** Inline review threads keyed by normalized repository-relative path. */
class PullRequestInlineCommentIndex private constructor(private val anchorsByPath: Map<String, List<InlineCommentAnchor>>) {
    fun totalInlineAnchorCount(): Int {
        return anchorsByPath.values.sumOf { anchors -> anchors.size }
    }

    fun indexedPaths(): Set<String> {
        return anchorsByPath.keys
    }

    /**
     * Anchors that can be shown in the real file editor. Only [InlineCommentSide.AFTER] threads qualify: the checked-out
     * branch is the pull request source, so those line numbers are the lines of the file on disk. Threads on
     * [InlineCommentSide.BEFORE] refer to the pre-image and stay visible in the diff viewer only.
     */
    fun anchorsForFileView(projectBasePath: String?, fileRelativePath: String): List<InlineCommentAnchor> {
        return anchorsForPath(projectBasePath = projectBasePath, changeRelativePath = fileRelativePath)
            .filter { anchor -> anchor.side == InlineCommentSide.AFTER }
    }

    fun anchorsForPath(projectBasePath: String?, changeRelativePath: String): List<InlineCommentAnchor> {
        val normalizedChangePath = PullRequestPathMatcher.normalize(changeRelativePath)
        return anchorsByPath.flatMap { (inlinePath, anchors) ->
            if (PullRequestPathMatcher.matches(
                    projectBasePath = projectBasePath,
                    changeRelativePath = normalizedChangePath,
                    bitbucketPath = inlinePath
                )
            ) {
                anchors
            } else {
                emptyList()
            }
        }
    }

    companion object {
        fun from(comments: List<PullRequestComment>): PullRequestInlineCommentIndex {
            val threads = PullRequestCommentThreads.groupIntoThreads(comments = comments)
            val byPath = buildMap<String, MutableList<InlineCommentAnchor>> {
                threads.forEach { thread ->
                    val root = thread.root
                    val path = root.inlinePath ?: return@forEach
                    val normalizedPath = PullRequestPathMatcher.normalize(path)
                    val startLine = root.inlineLine
                    val endLine = root.inlineLineEnd ?: startLine
                    val side = root.inlineSide
                    val anchor = when {
                        startLine != null && endLine != null && side != null -> InlineCommentAnchor(
                            startLine = startLine,
                            endLine = endLine,
                            side = side,
                            thread = thread
                        )
                        startLine == null -> InlineCommentAnchor(
                            startLine = 1,
                            endLine = 1,
                            side = InlineCommentSide.AFTER,
                            thread = thread,
                            isFileScoped = true
                        )
                        else -> return@forEach
                    }
                    getOrPut(normalizedPath) { mutableListOf() }.add(anchor)
                }
            }
            val sortedByPath = byPath.mapValues { (_, anchors) ->
                val order = PullRequestCommentThreads.sortedLikeConversation(anchors.map { anchor -> anchor.thread })
                    .withIndex()
                    .associate { indexed -> indexed.value.root.id to indexed.index }
                anchors.sortedWith(
                    compareBy<InlineCommentAnchor> { anchor -> order[anchor.thread.root.id] ?: Int.MAX_VALUE }
                        .thenBy { anchor -> anchor.startLine }
                        .thenBy { anchor -> anchor.thread.root.id }
                )
            }
            return PullRequestInlineCommentIndex(anchorsByPath = sortedByPath)
        }
    }
}