# CHANGELOG

## *1.1.0* - 2020-10-28
* Added: "Request Changes" action on pull requests; it changes to "Changes Requested" when the current user has requested changes, and is unavailable on your own pull requests
* Added: `@mention` tokens in comments show the mentioned account's name, resolved from comment authors, workspace members, or a direct user lookup
* Added: "Prune Work Trees" action in the Watched Projects footer removes PR work trees of all watched projects, then reports the totals in one notification
* Changed: the "Activities" tab of a pull request is renamed to "History", and its day groups and rows are reworked
* Fixed: pull-request descriptions, comment bodies and task text can be selected and copied
* Fixed: Conversation threads on files that no longer exist show the pull-request icon instead of a dead file link
* Fixed: the pull-request blue bar and the review bar ("Viewed", "Add comment") are shown in diff views of a pull-request worktree, including diffs opened from the "Pull request changes" view
* Fixed: the blue bar updates in editors that were open before the pull-request state was resolved
* Fixed: inline comment threads in diff views no longer fail with "layout state is not idle" when Compose content is measured

## *1.0.0* - 2020-10-25
* Initial release