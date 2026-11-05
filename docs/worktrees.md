# Git Work Trees 101

What's a git worktree? And how do they help with pull request reviews?? 

![whats a git worktree](docs/worktrees.jpeg)

![why worktrees for pr reviews](docs/worktrees_2.jpeg)

## What is a Git Worktree?

By default, a Git repository maintains a single working tree attached to your current branch. When you switch branches with `git checkout` or `git switch`, Git updates the files in that single directory to match the target branch.

A Git worktree (`git worktree`) lets you attach multiple working directories to a single local Git repository at the same time. You can have several branches checked out in separate folders on your machine, all sharing the same `.git` history database.

### Basic Commands

| Task | Command |
|------|---------|
| Create a new worktree with a new branch | `git worktree add -b branch-name ../path-to-dir` |
| Create a worktree for an existing branch | `git worktree add ../path-to-dir existing-branch` |
| List all active worktrees | `git worktree list` |
| Remove a worktree | `git worktree remove ../path-to-dir` |

## Advantages of Git Worktrees for PR Review

Reviewing a pull request normally requires stashing your current changes, checking out the PR branch, reviewing and testing it, and then switching back to your feature branch. Git worktrees remove this overhead.

### 1. No Context Switching and No Stashing

- **The traditional way:** You are working on Feature A. A colleague asks for a PR review. You must commit or stash your work in progress, switch to the PR branch, run your build tools, review the code, and switch back.
- **The worktree way:** Your Feature A directory stays untouched. You open a separate directory (for example, `../pr-reviews/pr-123`) dedicated to that PR. Your own development environment is not interrupted.

### 2. Run Multiple Branches Simultaneously

Each worktree is its own directory, so you can run your main application locally (for example, on port 3000) in your main workspace while running the PR branch from its own directory (for example, on port 3001).

This lets you compare behavior side by side, test bug fixes, or verify database migrations without stopping and restarting your local development servers.

### 3. Faster Setup and Disk Efficiency

Worktrees share the same `.git` object database, so you do not need to clone the repository a second time.

Creating a new worktree is nearly instantaneous and uses little extra disk space, because Git reuses the existing object store instead of duplicating the repository history.

### 4. Clean State Isolation

PR review often involves running test suites, installing temporary packages, or generating build artifacts.

Doing this in an isolated worktree ensures that test caches, generated lock files, or temporary configuration do not leak into your primary working branch.

## Opening a PR whose branch you are already on

Git will not check the same branch out in two places at once. So if your current project is already sitting on the pull request's branch, that branch cannot also be checked out in a separate work tree, and the work tree cannot be created.

The plugin handles this for you. When the clone on the PR branch is this window's own checkout and the working tree is clean, the plugin switches your current project to the repository's main branch first, frees the PR branch, and then opens the PR in its own work tree. A short notification confirms the switch.

If your working tree has uncommitted or untracked changes, the plugin does not switch, so your work in progress is never disturbed. Instead it shows a warning asking you to commit or stash those changes first, then open the PR again. Any unpushed commits on the PR branch are kept: switching away leaves them on the local branch, and the work tree checks out that same branch.

## Keeping branches up to date

In a PR work tree window, bbcloud keeps the local source and destination branches of the pull request in line with
Bitbucket. It fetches both branches and fast-forwards the local ones when the window opens, when the work tree is
opened or focused again from the main window, and when you click refresh (blue bar or PR page).

A branch is moved automatically only when that is safe:

- A local branch that does not exist yet is created from its Bitbucket counterpart and tracks it.
- A branch that is not checked out anywhere is fast-forwarded.
- A branch checked out in the PR work tree is fast-forwarded when the work tree has no uncommitted changes.
- A branch checked out in another work tree (usually `main` in your main clone) is never touched automatically.
- A branch with local commits that are not on Bitbucket is never touched automatically.

If a branch is still behind after that, the PR page shows a red warning with the branch, the number of commits it is
behind, and the reason it was not updated. One link updates every branch that is behind. That link also fast-forwards a
clean other work tree, and it pulls a diverged source branch into the PR work tree. The background poll only warns; it
never moves a branch under you while you review.

The diff, the line counts and the file list compare the source branch against `origin/<destination>`, the same way
Bitbucket does, so a stale local destination branch cannot inflate them.

## Automatic cleanup

When the Current Project tab loads its pull request list, the work trees of merged or declined PRs are removed in the background, both the folder and its git link. No prompt is shown. The IDE window of such a work tree is closed first, and uncommitted changes in it are discarded. A work tree that is being opened is skipped. Results are logged in `idea.log`.

## References

* [Beginners Guide](https://jonesrussell.github.io/blog/beginners-guide-git-worktrees/)
* [Git documentation](https://git-scm.com/docs/git-worktree)