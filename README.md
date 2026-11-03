# bbcloud

Free IntelliJ IDEA plugin for Bitbucket Cloud, which helps streamline pull request review without leaving your IDE

[Install **Bitbucket Cloud PR Review** from the JetBrains Marketplace](https://plugins.jetbrains.com/plugin/35502-bitbucket-cloud-pr-review/)

[There is also a Chrome Extension](bbcloud-chrome-ext/README.md) one can use which works on bitbucket.org with this plugin!

## Main features

Helps you review Bitbucket Cloud pull requests without leaving your Jetbrains IDE (and its excellent tooling) with help of [Work Trees](docs/worktrees.md), inline comments and much more.


* 🔐 **Secure authentication** signs you in to Bitbucket Cloud with OAuth so tokens are obtained and stored safely through the IDE.
* 🪟 A **Bitbucket Cloud PR Review** tool window on the bottom strip keeps pull request triage inside IntelliJ instead of the browser.
* 🌳 Opening a PR spins up a separate **PR Work Tree** project window with its own git [work tree](docs/worktrees.md) so reviews never clash with your main branch or other PRs.
* 📂 The **Current Project** tab shows open and completed PRs for the repo in your IDE, with repo links, open-PR counts, reviewer avatars, refresh, and persistent **Exclude** filters and a **Created by** dropdown.
* 🧭 The **Workspace** tab merges PRs you opened and review assignments from watched projects into one newest-first list across your Bitbucket workspace.
* 👀 **Watched Projects** controls which repositories contribute to the workspace queue, with search by slug, folder, PR number, or branch and a master checkbox for matching rows.
* 📋 Each PR gets a dedicated **`<prNum>.pr`** tab plus a blue header stripe with line-change stats, refresh, file changes, and merge, approve, draft, or decline actions.
* 🕐 The PR **Activity** timeline mixes commits, approvals, tasks, builds, and file or inline comment threads in time order, with a box to add PR-scoped comments and reply in threads.
* 📁 The **PR Changes** tool window lists every file in the diff; picking a file opens it in the editor or diff view driven from the stripe’s **Show pull request file changes** button.
* ✏️ **Modified File View** and **Diff View** surface existing comments and let you add line-level feedback from the gutter or context menu, view add/remove counts, and mark files reviewed.
* 🚀 **Create Pull Request** walks you through title, description, source and destination branches, reviewers (including defaults), remote cleanup after merge, then opens the new PR in a work tree window.

### Setup and Settings

Click this icon ![plugin icon](docs/icon.png) in IntelliJ or goto `IntelliJ > Settings > Tools > Bitbucket Cloud PR Review` and configure:
* Specify "Projects Directory" where one usually checks out projects into from bitbucket
* Work with Atlassian Bitbucket Cloud APIs using either:

* * Bitbucket Oauth authentication
* * or an API Token (with required scopes) [one can setup and revoke here](https://id.atlassian.com/manage-profile/security/api-tokens)

### Current Project
One page which shows the open and the completed (merged or declined) PRs for the currently open project in a single listing:
* Clicking the title of an open PR would open that PR in a separate "PR Work Tree" IntelliJ window which has its own git work tree so not to interfere with your work or any other PR reviews; clicking the title of a completed PR opens it in bitbucket cloud
* The header shows the repository slug (a link to bitbucket cloud) and a pill with the number of open PRs
* Each open PR shows up to two reviewer avatars (a green check marks reviewers who approved, a warning badge marks reviewers who requested changes) and a `+N` circle for the remaining reviewers; hover an avatar or the circle to see names and review status
* Refresh button checks bitbucket cloud for any new PRs in this project
* You can also goto the PR view in bitbucket cloud from here
* A filter row lets you hide PRs you do not want to see; each control is remembered across restarts:
  * `Exclude Resolved` (on by default): hide merged and declined PRs (this checkbox is only shown on this tab)
  * `Exclude Approved by me`: hide the PRs you have approved
  * `Exclude Drafts` (on by default): hide draft PRs
  * `Created by any user` / `Created by me` dropdown: with `Created by me`, show only the PRs you created


### Workspace
One page which merges the PRs you opened across your bitbucket workspace and the PRs assigned to you for review (from your "Watched Projects") into a single listing, newest first:
* If the project exists in your Projects directory clicking the title of a PR would open that PR in a separate "PR Work Tree" IntelliJ window otherwise you are redirected to bitbucket cloud PR view
* Each PR shows the same reviewer avatars as the Current Project tab
* The same filter row is offered, remembered independently of the Current Project tab; `Exclude Approved by me`, `Exclude Drafts` and the `Created by` dropdown are shown here (there is no `Exclude Resolved` checkbox, because this listing only holds open PRs)

### Watched Projects
Page which lets you opt into which projects you want to "Watch"; only watched projects contribute PRs to the "Workspace" tab's review queue. A search field above the table filters the projects by slug, local folder, PR number, or branch name; the master checkbox then watches or unwatches only the matching projects.
* Due to Bitbucket API limitations you have to opt into which project you want to "Watch", opting into too many projects may result in hitting api rate limits.

### PR View
Each pull request opens in its own git work tree, so not to interfere with any other reviews or your work.
A blue stripe would contain critical PR information and several buttons such as "Show pull request file changes" and "Refresh" as well as lines changed indicator for PR.
Also, a new tab appear in your main editor window titled `<prNum>.pr` this contains important details for the current PR related to this PR work tree for you to review:

* One page with the pull request description, an "Activity" timeline, and the builds, checks, reviewers and tasks cards, alongside: Merge, Approve, Mark as Draft, Decline buttons. The timeline starts with a box to leave PR scoped comments, then shows the commits, approvals, tasks and builds with the File and Inline scoped comment threads interleaved by the time they were written, and lets you view and reply to those threads

The files the pull request changes are listed in the "PR Changes" tool window, opened with "Show pull request file changes" in the blue stripe. You can click on any file, and it will open in its own editor and / or diff view

### Modified File View
The normal file editor is enriched with comments, you can also leave further comments by hovering over the line number gutter or right-clicking and leaving a comment.
* This way you can leave comments where it matters.
* You can also click on "Diff view" and see the number of lines added / removed in this file
* Finally one can also mark a file as reviewed

### Diff View
Comments also appear in Diff view

### Create Pull Request view
One can create a new PR for their branch
* Provide title, description, source and destination branches, reviewers (includes default reviewers) and whether to cleanup remote branch post merge
* Once created a new PR Work Tree project window is open with that new PR

## Changelog
[Changelog](CHANGELOG.md)

## Contributions

Pull requests and forks are welcome :)

Leave any feedback on [Issues page](https://github.com/fnunezkanut/bbcloud/issues) but please keep in mind this is a **free** plugin maintained on free time, with the selfish aim of helping myself review pull requests faster.
In some cases Atlassian *public* apis are lacking so feature parity with web version is hard to reach. This plugin is not for the hosted version of bitbucket.


## Plugin Development

### Requirements

- **JDK 25.** The build uses `jvmToolchain(25)`, matching the JetBrains Runtime bundled with IntelliJ IDEA 2026.2. [`.sdkmanrc`](.sdkmanrc) pins `java=25.0.4-amzn`; run `sdk env` to switch to it.
- **Gradle 9.8.2.** Provided by the Gradle wrapper (`./gradlew`), so no separate install is needed. It is also pinned in `.sdkmanrc`.
- **IntelliJ IDEA 2026.2.3 (build `262.10968.63`) and newer**

### Build

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the source is organized.

```bash
./gradlew build
```

Unit tests use **JUnit Jupiter 6.1.3** and Kotest assertions, with the `@Tag("unit")` annotation—not JUnit 4. `./gradlew build` runs them through Gradle’s `check` lifecycle (after the formatter on local machines) and runs `buildPlugin`, which produces the installable plugin at `build/distributions/bbcloud-<version>.zip`. The first build downloads IntelliJ IDEA 2026.2.3 into the Gradle cache, so it takes a while.

`./gradlew buildPlugin` is equivalent to the packaging step alone if you only need the ZIP after a prior compile.

To check binary compatibility with 2026.2.3 and newer IDE releases (including EAPs), run the JetBrains Plugin Verifier:

```bash
./gradlew verifyPlugin
```

The first run downloads several IDE builds. Reports are written to `build/reports/pluginVerifier`.

### Linting

Kotlin style is enforced with [ktlint](https://pinterest.github.io/ktlint/latest/) via the [Kotlinter](https://github.com/jeremymailen/kotlinter-gradle) Gradle plugin. Rule overrides live in [`.editorconfig`](.editorconfig).

```bash
./gradlew lint        # check only (alias for lintKotlin)
./gradlew format      # auto-format (alias for formatKotlin)
```

On a local machine, `./gradlew build` runs the formatter first, then compile and tests. In CI (`CI` or `JENKINS_URL` set), lint violations fail the build. The `build` task also runs `lintKotlin` through Gradle’s `check` lifecycle.

### Test in IntelliJ

#### Your own IDE

1. Build the plugin with `./gradlew build`.
2. In IntelliJ IDEA, open **Settings > Plugins**, click the gear icon, and choose **Install Plugin from Disk...**.
3. Select the zip from `build/distributions` and restart the IDE if prompted.

Always install the zip, not a jar from `build/libs`. The jars there contain only the plugin classes, without bundled libraries such as OkHttp, and fail at runtime with `NoClassDefFoundError`.


### Troubleshooting

The sandbox IDE's log is at `.intellijPlatform/sandbox/bbcloud/IU-2026.2.3/log_runIde/idea.log` (in your own IDE, use **Help > Show Log in Finder**).

