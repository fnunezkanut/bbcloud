# CHANGELOG

## *1.7.0* - 2026-11-04
* Added: a red delete button beside each PR work tree on Watched Projects removes that work tree after confirmation, and closes its IDE window first if one is open; the progress dialog closes after 5 seconds while the removal keeps running in the background
* Added: @mention users in comments and replies
* Added: PRs assigned to me option on Current Project and Workspace tabs in the dropdown

## *1.6.0* - 2026-11-03
* Fixed: the Previous, Next, and Add Comment buttons in the diff review bar no longer show a hover tooltip that covered the buttons and did not close
* Changed: when every other file is marked as viewed, the diff review bar shows this as text next to the disabled Previous and Next buttons
* Added: the Create Pull Request form prefills the title and description from the selected source branch's last commit, and updates them when you switch branches unless you have edited the fields
* Changed: the "Mine" exclude checkbox on both PR tabs is replaced by a "Created by any user" / "Created by me" dropdown, and the remaining checkboxes are relabeled "Exclude Resolved", "Exclude Approved by me", and "Exclude Drafts"; each tab remembers its choice
* Changed: Watched Projects now caps how many projects you can watch to help avoid Bitbucket rate limits, and the footer and workspace scope notes link to the related tab
* Changed: `@mentions` in the Activity timeline show a "loading…" placeholder until the account name resolves

## *1.5.0* - 2026-11-02
* Bugfix: Unable to leave multiple comments on same line, thanks Olszewski-Jakub!

## *1.4.0* - 2026-11-01
* Fixed: keyboard input in diff and file-editor comment boxes (including paste) edits the comment instead of the read-only file
* Changed: reply, edit, line, and file comment boxes take focus when opened and keep their text and focus when comments refresh; reply and edit boxes close if the comment was deleted or resolved elsewhere
* Fixed: inline and file comments on added files appear in the file diff view, as in the file editor
* Fixed: "Add Comment" in added-file, deleted-file, and unified diffs opens the correct comment UI; deleted-file comments are created on the pull request and shown on the pull request page
* Changed: Bitbucket sign-in uses OAuth or an API token
* Removed: Overview, Conversation, and Changes tabs on the pull request page; that content is on one page, and changed files are listed in the PR Changes tool window
* Added: PR Changes tool window from "Show changed files" in a work tree — file list with line and comment counts, branch names only in the header, Enter to open the selected diff, purple tick instead of line counts for viewed files, and optional diff navigation over unviewed files only
* Changed: the History card is renamed Activity; commits, approvals, tasks, builds, and comment threads appear in chronological order, with the main comment box at the top
* Changed: opening a PR work tree while the project is on that PR's branch switches to the main branch first; uncommitted changes trigger a warning instead of switching
* Added: a local REST endpoint at `/api/bbcloud/open` on the IDE built-in server, used by the bbcloud Chrome extension to open a pull request in its own work tree from bitbucket.org. The IDE must be running; open requests are accepted only from the extension origin

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