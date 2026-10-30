# CHANGELOG

## *1.3.0* - 2026-10-30
* Changed: "Project - Open PRs" and "Project - Completed PRs" are merged into one "Current Project - PRs" tab
* Changed: "Workspace - My PRs" and "Workspace - Assigned PRs" are merged into one "Workspace - PRs" tab
* Added: "Exclude:" filters on both PR tabs — Resolved (on by default, Current Project - PRs only), Mine (hides PRs you created), Approved by me, Drafts (on by default); each tab remembers its choices
* Added: Compatibility with IntelliJ 2026.3 Beta

## *1.2.0* - 2020-10-29
* Changed: the add-comment icon in diff gutters uses the standard IDE "+" icon
* Fixed: Clipping of comment boxes on multi-monitor (with different scales) setup
* Fixed: emoji in pull-request descriptions, comments and tasks are shown as the emoji instead of Bitbucket markup such as `:slight_smile:{: data-emoji-id='1f642' ... }`
* Fixed: Backspace, Delete, Select All, arrow keys, copy and cut in comment boxes of diff views act on the comment text instead of the file ("This view is read-only")
* Fixed: inline code and code blocks in comments and descriptions keep text in angle brackets, instead of losing it as HTML
* Fixed: plain emoji shortcodes such as `:thumbs_up:` are shown as the emoji
* Fixed: use of internal apis

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