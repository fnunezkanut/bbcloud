package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.github.fnunezkanut.bbcloud.review.WorktreeTodoBannerViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Weighted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * The pull-request view blue bar panel stacked in [com.intellij.ui.EditorNotificationProvider] above each editor in a
 * worktree project.
 *
 * A lower [getWeight] keeps this row above the per-file review bar when both are visible. Every panel reads the shared
 * [WorktreeTodoBannerViewModel] so all open editors show the same header and refresh state.
 */
internal class WorktreePrViewBlueBarPanel private constructor() :
    JPanel(BorderLayout()),
    Disposable,
    Weighted {
        private var composePanel: ComposePanel? = null
        private var disposed = false
        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        init {
            isOpaque = false
        }

        override fun getWeight(): Double {
            return WEIGHT
        }

        @OptIn(ExperimentalComposeUiApi::class)
        override fun dispose() {
            if (disposed) {
                return
            }
            disposed = true
            coroutineScope.cancel()
            composePanel?.dispose()
        }

        companion object {
            private const val WEIGHT = 0.0

            fun create(project: Project): WorktreePrViewBlueBarPanel {
                val banner = WorktreePrViewBlueBarPanel()
                val viewModel = WorktreeTodoBannerViewModel.getInstance(project)
                viewModel.ensureInitialized()
                var panel: ComposePanel? = null
                val content = compose(config = { panel = this }) {
                    val state by viewModel.state
                    WorktreePrViewBlueBar(
                        state = state,
                        avatarIconFor = viewModel::avatarIconFor,
                        onOpenChangedFiles = {
                            WorktreeTodoBannerActions.openChangedFiles(project = project, state = viewModel.state.value)
                        },
                        onOpenPrPage = {
                            WorktreeTodoBannerActions.openPrPage(project = project, state = viewModel.state.value)
                        },
                        onOpenMainProject = {
                            WorktreeTodoBannerActions.openMainProject(
                                project = project,
                                state = viewModel.state.value,
                                coroutineScope = banner.coroutineScope
                            )
                        },
                        onOpenPullRequest = {
                            WorktreeTodoBannerActions.openPullRequest(project = project, state = viewModel.state.value)
                        },
                        onRefresh = {
                            WorktreeTodoBannerActions.refresh(project = project, state = viewModel.state.value)
                        }
                    )
                }
                banner.composePanel = panel
                banner.add(content, BorderLayout.CENTER)
                return banner
            }
        }
    }