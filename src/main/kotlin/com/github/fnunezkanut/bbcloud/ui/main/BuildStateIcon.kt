package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.BuildState
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * The build-state icon of the activity feed and of the changed-files list.
 *
 * The icons come from the Jewel icon keys, so they follow the IDE theme instead of being painted from a Swing icon.
 */
internal fun buildStateIconKey(state: BuildState): IconKey {
    return when (state) {
        BuildState.Successful -> AllIconsKeys.RunConfigurations.TestPassed
        BuildState.Failed -> AllIconsKeys.RunConfigurations.TestFailed
        BuildState.InProgress -> AllIconsKeys.Process.Step_1
        BuildState.Stopped -> AllIconsKeys.RunConfigurations.TestIgnored
    }
}