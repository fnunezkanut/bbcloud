package com.github.fnunezkanut.bbcloud.ui.setup

/** Immutable state rendered by the Compose Settings screen. */
data class SettingsUiState(
    val hasStoredToken: Boolean = false,
    val projectsDirectory: String? = null,
    val tokenFieldText: String = "",
    val errorMessage: String? = null,
    // "Save succeeded" affordance shown next to the Save button; null hides it.
    val successMessage: String? = null,
    val isBusy: Boolean = false,
    val pluginVersionFooter: String
)