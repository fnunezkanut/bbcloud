@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.github.fnunezkanut.bbcloud.ui.markdowndiff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.components.BbCloudCompactOutlinedIconButton
import com.github.fnunezkanut.bbcloud.ui.main.ActionButtonOutlines
import com.intellij.diff.util.TextDiffType
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.tables.create
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableProcessorExtension
import org.jetbrains.jewel.markdown.extensions.github.tables.GitHubTableRendererExtension
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.awt.Color as AwtColor

/**
 * Renders a side-by-side Markdown diff. Each row holds one top-level block of the old document on the left and the
 * matching block of the new document on the right, so both panes scroll together and stay aligned.
 *
 * Added blocks are green, removed blocks red, and modified blocks blue, with the colors of the IDE diff viewer. The
 * change map on the right marks every change; clicking it jumps to that row.
 */
@Composable
fun MarkdownDiffScreen(viewModel: MarkdownDiffViewModel) {
    val state by viewModel.state
    val styling = remember(JewelTheme.instanceUuid) { MarkdownStyling.create() }
    val processor = remember { MarkdownProcessor(extensions = listOf(GitHubTableProcessorExtension)) }
    val blockRenderer = remember(styling) { createDiffBlockRenderer(styling = styling) }
    val colors = remember(JewelTheme.instanceUuid) { MarkdownDiffColors.fromIde() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.scrollRequest) {
        val request = state.scrollRequest ?: return@LaunchedEffect
        listState.animateScrollToItem(index = request.rowIndex.coerceIn(0, (state.rows.size - 1).coerceAtLeast(0)))
    }

    ProvideMarkdownStyling(markdownStyling = styling, markdownProcessor = processor, markdownBlockRenderer = blockRenderer) {
        Column(modifier = Modifier.fillMaxSize()) {
            MarkdownDiffToolbar(state = state, viewModel = viewModel, colors = colors)
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            MarkdownDiffPaneLabels(leftLabel = state.leftLabel, rightLabel = state.rightLabel)
            Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
            val message = statusMessageOf(state = state)
            if (message != null) {
                Text(text = message, modifier = Modifier.padding(16.dp))
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        itemsIndexed(items = state.rows) { _, row ->
                            MarkdownDiffRowView(
                                row = row,
                                showWordChanges = state.showWordChanges,
                                processor = processor,
                                blockRenderer = blockRenderer,
                                colors = colors
                            )
                        }
                    }
                    Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight())
                    MarkdownDiffChangeMap(
                        rows = state.rows,
                        listState = listState,
                        colors = colors,
                        onRowSelected = viewModel::onRowSelected
                    )
                }
            }
        }
    }
}

private fun statusMessageOf(state: MarkdownDiffUiState): String? {
    return when {
        state.errorMessage != null -> state.errorMessage
        state.isLoading && state.rows.isEmpty() -> BbCloudBundle.message("markdownDiff.loading")
        state.rows.isEmpty() -> BbCloudBundle.message("markdownDiff.empty")
        else -> null
    }
}

@Composable
private fun MarkdownDiffToolbar(state: MarkdownDiffUiState, viewModel: MarkdownDiffViewModel, colors: MarkdownDiffColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.comparisons.size > 1) {
            ListComboBox(
                items = state.comparisons.map { comparison -> comparison.label },
                selectedIndex = state.selectedComparisonIndex,
                onSelectedItemChange = viewModel::onComparisonSelected,
                modifier = Modifier.width(260.dp)
            )
        } else {
            state.comparisons.firstOrNull()?.let { comparison -> Text(text = comparison.label) }
        }
        BbCloudCompactOutlinedIconButton(
            icon = AllIconsKeys.Actions.PreviousOccurence,
            contentDescription = BbCloudBundle.message("markdownDiff.previousChange"),
            outlineColor = ActionButtonOutlines.neutral(),
            enabled = state.isPreviousChangeEnabled,
            onClick = viewModel::onPreviousChange
        )
        BbCloudCompactOutlinedIconButton(
            icon = AllIconsKeys.Actions.NextOccurence,
            contentDescription = BbCloudBundle.message("markdownDiff.nextChange"),
            outlineColor = ActionButtonOutlines.neutral(),
            enabled = state.isNextChangeEnabled,
            onClick = viewModel::onNextChange
        )
        MarkdownDiffSummary(state = state, colors = colors)
        CheckboxRow(
            text = BbCloudBundle.message("markdownDiff.wordChanges"),
            checked = state.showWordChanges,
            onCheckedChange = viewModel::onWordChangesToggled
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(onClick = viewModel::onRefreshRequested, enabled = !state.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(key = AllIconsKeys.Actions.Refresh, contentDescription = null)
                Text(text = BbCloudBundle.message("markdownDiff.refresh"))
            }
        }
    }
}

@Composable
private fun MarkdownDiffSummary(state: MarkdownDiffUiState, colors: MarkdownDiffColors) {
    if (state.isLoading || state.errorMessage != null) {
        return
    }
    if (!state.hasChanges) {
        Text(text = BbCloudBundle.message("markdownDiff.noChanges"))
        return
    }
    val position = if (state.currentChangeIndex >= 0) "${state.currentChangeIndex + 1}/" else ""
    val summary = buildAnnotatedString {
        append(BbCloudBundle.message("markdownDiff.changeCount", position, state.changeRowIndices.size))
        append("  ")
        withStyle(SpanStyle(color = colors.addedMarker, fontWeight = FontWeight.Bold)) { append("+${state.addedCount}") }
        append(" ")
        withStyle(SpanStyle(color = colors.removedMarker, fontWeight = FontWeight.Bold)) { append("−${state.removedCount}") }
        append(" ")
        withStyle(SpanStyle(color = colors.modifiedMarker, fontWeight = FontWeight.Bold)) { append("~${state.modifiedCount}") }
    }
    Text(text = summary)
}

@Composable
private fun MarkdownDiffPaneLabels(leftLabel: String, rightLabel: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = leftLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = PANE_PADDING))
        Text(text = rightLabel, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = PANE_PADDING))
        Spacer(modifier = Modifier.width(CHANGE_MAP_WIDTH))
    }
}

/** One aligned row: both halves get the same width, and the row is as tall as the taller half. */
@Composable
private fun MarkdownDiffRowView(
    row: MarkdownDiffRow,
    showWordChanges: Boolean,
    processor: MarkdownProcessor,
    blockRenderer: MarkdownBlockRenderer,
    colors: MarkdownDiffColors
) {
    val leftBackground = colors.backgroundOf(kind = row.kind, isLeft = true)
    val rightBackground = colors.backgroundOf(kind = row.kind, isLeft = false)
    val leftMarker = colors.markerOf(kind = row.kind, isLeft = true)
    val rightMarker = colors.markerOf(kind = row.kind, isLeft = false)
    val dividerColor = colors.divider
    Layout(
        content = {
            MarkdownDiffCell(
                block = row.left,
                words = row.leftWords.takeIf { showWordChanges && row.kind == MarkdownDiffKind.MODIFIED },
                wordHighlight = colors.removedWord,
                strikeChangedWords = true,
                processor = processor,
                blockRenderer = blockRenderer
            )
            MarkdownDiffCell(
                block = row.right,
                words = row.rightWords.takeIf { showWordChanges && row.kind == MarkdownDiffKind.MODIFIED },
                wordHighlight = colors.addedWord,
                strikeChangedWords = false,
                processor = processor,
                blockRenderer = blockRenderer
            )
        },
        modifier = Modifier.fillMaxWidth().drawBehind {
            val dividerWidth = 1.dp.toPx()
            val markerWidth = 3.dp.toPx()
            val half = (size.width - dividerWidth) / 2f
            drawRect(color = leftBackground, topLeft = Offset.Zero, size = Size(half, size.height))
            drawRect(color = rightBackground, topLeft = Offset(half + dividerWidth, 0f), size = Size(half, size.height))
            drawRect(color = leftMarker, topLeft = Offset.Zero, size = Size(markerWidth, size.height))
            drawRect(color = rightMarker, topLeft = Offset(half + dividerWidth, 0f), size = Size(markerWidth, size.height))
            drawRect(color = dividerColor, topLeft = Offset(half, 0f), size = Size(dividerWidth, size.height))
        }
    ) { measurables, constraints ->
        val dividerWidth = 1.dp.roundToPx()
        val width = constraints.maxWidth
        val half = ((width - dividerWidth) / 2).coerceAtLeast(0)
        val cellConstraints = Constraints(minWidth = half, maxWidth = half)
        val placeables = measurables.map { measurable -> measurable.measure(cellConstraints) }
        val height = placeables.maxOf { placeable -> placeable.height }
        layout(width = width, height = height) {
            placeables[0].place(x = 0, y = 0)
            placeables[1].place(x = half + dividerWidth, y = 0)
        }
    }
}

@Composable
private fun MarkdownDiffCell(
    block: MarkdownSourceBlock?,
    words: List<WordDiffSegment>?,
    wordHighlight: Color,
    strikeChangedWords: Boolean,
    processor: MarkdownProcessor,
    blockRenderer: MarkdownBlockRenderer
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = PANE_PADDING, vertical = 4.dp)) {
        if (block == null) {
            return@Box
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            val blocks = remember(block.text) { processor.processMarkdownDocument(block.text) }
            Markdown(
                markdownBlocks = blocks,
                markdown = block.text,
                modifier = Modifier.fillMaxWidth(),
                selectable = true,
                onUrlClick = ::openDiffLink,
                blockRenderer = blockRenderer
            )
            if (words != null) {
                Text(
                    text = remember(words, wordHighlight) { wordChangesText(words = words, highlight = wordHighlight, strike = strikeChangedWords) },
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

private fun wordChangesText(words: List<WordDiffSegment>, highlight: Color, strike: Boolean): AnnotatedString {
    return buildAnnotatedString {
        words.forEach { segment ->
            if (segment.changed) {
                val decoration = if (strike) TextDecoration.LineThrough else null
                withStyle(SpanStyle(background = highlight, textDecoration = decoration)) { append(segment.text) }
            } else {
                append(segment.text)
            }
        }
    }
}

/**
 * A narrow strip with one marker per changed row, positioned by row index, plus the visible range. Clicking the strip
 * jumps to the row under the pointer.
 */
@Composable
private fun MarkdownDiffChangeMap(rows: List<MarkdownDiffRow>, listState: LazyListState, colors: MarkdownDiffColors, onRowSelected: (Int) -> Unit) {
    val rowCount = rows.size
    val firstVisible = listState.firstVisibleItemIndex
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
    Canvas(
        modifier = Modifier
            .width(CHANGE_MAP_WIDTH)
            .fillMaxHeight()
            .pointerInput(rowCount) {
                detectTapGestures { offset ->
                    if (rowCount > 0 && size.height > 0) {
                        val rowIndex = (offset.y / size.height * rowCount).toInt().coerceIn(0, rowCount - 1)
                        onRowSelected(rowIndex)
                    }
                }
            }
    ) {
        if (rowCount == 0) return@Canvas
        val rowHeight = size.height / rowCount
        val markerHeight = rowHeight.coerceAtLeast(3.dp.toPx())
        drawRect(
            color = colors.viewport,
            topLeft = Offset(0f, firstVisible * rowHeight),
            size = Size(size.width, (visibleCount * rowHeight).coerceAtLeast(markerHeight))
        )
        val half = size.width / 2f
        rows.forEachIndexed { index, row ->
            val top = index * rowHeight
            when (row.kind) {
                MarkdownDiffKind.UNCHANGED -> Unit

                MarkdownDiffKind.REMOVED -> drawRect(color = colors.removedMarker, topLeft = Offset(0f, top), size = Size(half, markerHeight))

                MarkdownDiffKind.ADDED -> drawRect(color = colors.addedMarker, topLeft = Offset(half, top), size = Size(half, markerHeight))

                MarkdownDiffKind.MODIFIED -> drawRect(color = colors.modifiedMarker, topLeft = Offset(0f, top), size = Size(size.width, markerHeight))
            }
        }
    }
}

/** Builds the block renderer with the GFM table renderer. The table styling follows the current IDE theme. */
private fun createDiffBlockRenderer(styling: MarkdownStyling): MarkdownBlockRenderer {
    return MarkdownBlockRenderer.create(
        styling = styling,
        rendererExtensions = listOf(GitHubTableRendererExtension(GfmTableStyling.create(), styling))
    )
}

private fun openDiffLink(url: String) {
    LOG.info("User opened a link from the Markdown diff in the browser")
    BrowserUtil.browse(url)
}

/** The diff colors of the current IDE theme, taken from the IDE diff viewer so the tab looks like a regular diff. */
private data class MarkdownDiffColors(
    val addedBackground: Color,
    val removedBackground: Color,
    val modifiedBackground: Color,
    val addedMarker: Color,
    val removedMarker: Color,
    val modifiedMarker: Color,
    val addedWord: Color,
    val removedWord: Color,
    val divider: Color,
    val viewport: Color
) {
    fun backgroundOf(kind: MarkdownDiffKind, isLeft: Boolean): Color {
        return when (kind) {
            MarkdownDiffKind.UNCHANGED -> Color.Transparent
            MarkdownDiffKind.ADDED -> if (isLeft) Color.Transparent else addedBackground
            MarkdownDiffKind.REMOVED -> if (isLeft) removedBackground else Color.Transparent
            MarkdownDiffKind.MODIFIED -> modifiedBackground
        }
    }

    fun markerOf(kind: MarkdownDiffKind, isLeft: Boolean): Color {
        return when (kind) {
            MarkdownDiffKind.UNCHANGED -> Color.Transparent
            MarkdownDiffKind.ADDED -> if (isLeft) Color.Transparent else addedMarker
            MarkdownDiffKind.REMOVED -> if (isLeft) removedMarker else Color.Transparent
            MarkdownDiffKind.MODIFIED -> modifiedMarker
        }
    }

    companion object {
        fun fromIde(): MarkdownDiffColors {
            val inserted = TextDiffType.INSERTED
            val deleted = TextDiffType.DELETED
            val modified = TextDiffType.MODIFIED
            return MarkdownDiffColors(
                addedBackground = inserted.getColor(null).toCompose(alpha = BLOCK_ALPHA),
                removedBackground = deleted.getColor(null).toCompose(alpha = BLOCK_ALPHA),
                modifiedBackground = modified.getColor(null).toCompose(alpha = BLOCK_ALPHA),
                addedMarker = inserted.let { type -> type.getMarkerColor(null) ?: type.getColor(null) }.toCompose(alpha = 1f),
                removedMarker = deleted.let { type -> type.getMarkerColor(null) ?: type.getColor(null) }.toCompose(alpha = 1f),
                modifiedMarker = modified.let { type -> type.getMarkerColor(null) ?: type.getColor(null) }.toCompose(alpha = 1f),
                addedWord = inserted.getColor(null).toCompose(alpha = 1f),
                removedWord = deleted.getColor(null).toCompose(alpha = 1f),
                divider = ActionButtonOutlines.neutral().copy(alpha = 0.5f),
                viewport = ActionButtonOutlines.neutral().copy(alpha = 0.25f)
            )
        }

        private fun AwtColor.toCompose(alpha: Float): Color {
            return Color(red = red, green = green, blue = blue, alpha = (alpha * 255).toInt())
        }

        private const val BLOCK_ALPHA = 0.55f
    }
}

private object MarkdownDiffScreenLogCategory

private val LOG = logger<MarkdownDiffScreenLogCategory>()

private val PANE_PADDING = 12.dp
private val CHANGE_MAP_WIDTH = 14.dp