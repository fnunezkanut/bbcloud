# bbcloud

IntelliJ IDEA plugin (`com.github.fnunezkanut.bbcloud`) for Bitbucket Cloud. It adds a **Bitbucket Cloud PR Review** tool window on the bottom right strip.

## Main features

### Setup and Settings

Click this icon ![plugin icon](docs/icon.png) in IntelliJ or goto `IntelliJ > Settings > Tools > Bitbucket Cloud PR Review` and configure:
* Interaction with Atlassian Bitbucket Cloud APIs using an API Token (with required scopes) [one can setup and revoke here](https://id.atlassian.com/manage-profile/security/api-tokens)
* Specify "Projects Directory" where one usually checks out projects into from bitbucket

### Project - Open PRs and Completed PRs
Pages which show any currently open and completed PRs for the currently open project:
* Clicking the title of the PR would open that PR in a separate "PR Work Tree" IntelliJ window which has its own git work tree so not to interfere with your work any any other PR reviews
* Refresh button checks bitbucket cloud for any new PRs in this project
* You can also goto the PR view in bitbucket cloud from here

### Workspace - My PRs
Page which shows any PRs you opened in your bitbucket workspace:
* If the project exists in your Projects directory clicking the title of the PR would open that PR in a separate "PR Work Tree" IntelliJ window otherwise you be redirected to bitbucket cloud PR view

### Workspace - Assigned PRs and Watched Projects
Pages which shows any PRs you have been assigned to review from the list on the "Watched Projects" page only
* Due to Bitbucket API limitations you have to opt into which project you want to "Watch", opting into too many projects may result in hitting api rate limits.

### PR View
Each pull request opens in its own git work tree, so not to interfere with any other reviews or your work.
A blue stripe would contain critical PR information and several buttons such as "Show pull request file changes" and "Refresh" as well as lines changed indicator for PR.
Also, a new tab appear in your main editor window titled `<prNum>.pr` this contains important details for the current PR related to this PR work tree for you to review:

* "Overview" tab containing (builds, checks, reviewers and tasks) as well as the pull request description, alongside: Merge, Approve, Mark as Draft, Decline buttons
* "Conversation" tab where one can leave PR scoped comments, as well as view and reply to File and Inline scoped comment threads
* "Changes" tab which shows which files changed and other PR and branch related activities

You can click on any file, and it will open in its own editor and / or diff view

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

## Requirements

- **JDK 25.** The build uses `jvmToolchain(25)`, matching the JetBrains Runtime bundled with IntelliJ IDEA 2026.2. [`.sdkmanrc`](.sdkmanrc) pins `java=25.0.4-amzn`; run `sdk env` to switch to it.
- **Gradle 9.8.0.** Provided by the Gradle wrapper (`./gradlew`), so no separate install is needed. It is also pinned in `.sdkmanrc`.
- **IntelliJ IDEA 2026.2.3 (build `262.10968.63`) or newer.**

## Build

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

## Test in IntelliJ

### Your own IDE

1. Build the plugin with `./gradlew build`.
2. In IntelliJ IDEA, open **Settings > Plugins**, click the gear icon, and choose **Install Plugin from Disk...**.
3. Select the zip from `build/distributions` and restart the IDE if prompted.

Always install the zip, not a jar from `build/libs`. The jars there contain only the plugin classes, without bundled libraries such as OkHttp, and fail at runtime with `NoClassDefFoundError`.

### Files changed tab (manual check)

After opening a pull request in the **Bitbucket Cloud PR Review** tool window, if the working tree is clean or the PR source branch is already checked out, the source branch is checked out automatically. If there are uncommitted changes on a different branch, a confirmation dialog appears asking to stash or commit outstanding work before switching. Once confirmed or if no warning is needed, the source branch is checked out:

1. Open the **Files changed** tab and confirm a hierarchical file list loads (same style as the IDE **Changes** browser).
2. Compare the list with `git diff <destination>...<source>` in the project repository; paths should match for up-to-date remotes.
3. Double-click a file (or select it and press **Ctrl+D** / **Cmd+D**) and confirm the IntelliJ diff viewer opens.
4. Use **Refresh** on the tab after a new commit and confirm the list updates.

### Troubleshooting

The sandbox IDE's log is at `.intellijPlatform/sandbox/bbcloud/IU-2026.2.3/log_runIde/idea.log` (in your own IDE, use **Help > Show Log in Finder**).
