package com.github.fnunezkanut.bbcloud.review

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * The persisted backing store of the recently-seen mention users, separate from `BbCloudSettings` so its XML is its
 * own file. Registered as an `applicationService` in `plugin.xml`.
 */
@State(name = "BbCloudKnownUsers", storages = [Storage("bbcloud-known-users.xml")])
class BbCloudKnownUsersState : SimplePersistentStateComponent<BbCloudKnownUsersState.State>(State()) {
    class State : BaseState() {
        /** One encoded row per remembered user; see [BbCloudKnownUserRow]. Capped, oldest first out. */
        var rows by list<String>()
    }

    companion object {
        fun getInstance(): BbCloudKnownUsersState {
            return ApplicationManager.getApplication().service()
        }
    }
}