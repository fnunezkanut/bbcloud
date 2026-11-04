# Architecture

This file describes how the bbcloud IntelliJ plugin source is organized. For build commands, coding standards, and
logging rules, see [AGENTS.md](AGENTS.md). For user-facing features, see [README.md](README.md).

All Kotlin sources live under `src/main/kotlin/com/github/fnunezkanut/bbcloud/`. Extension points, services, tool
windows, and editors are registered in [`plugin.xml`](src/main/resources/META-INF/plugin.xml). User-visible strings
live in `BbCloudBundle` (`messages/BbCloudBundle.properties`).

## Layers

```
UI (Compose Screen  <-  UiState  <-  ViewModel, hosted by a Swing *Panel)
        |
Application services (BbCloud*Service, git and project helpers)
        |
Bitbucket clients (OkHttp*Client, Bitbucket*Client)  ->  response parsers (Bitbucket*ResponseParser)
        |
Credentials (PasswordSafe) and persisted settings (BbCloudSettings)
```

- Higher layers depend on lower layers only. Clients return sealed result types (for example
  `PullRequestListResult`, `ReviewerQueueQueryResult`) instead of throwing, and the UI layer decides the user-facing
  outcome and logs the throwable once (see AGENTS.md, Logging).
- Parsers are pure functions over JSON strings. They are unit tested without HTTP.

## Packages

| Package | Responsibility |
| --- | --- |
| `auth` | Bitbucket Cloud OAuth 2.0 (PKCE) login and the access-token layer: the login service and flow, the local callback server, the token-endpoint client, the access-token provider (OAuth first, API token fallback), the online credential validator, and the 401-retry OkHttp `Authenticator`. See "Authentication" below. |
| `bitbucket` | Bitbucket Cloud REST clients, URL builders, JSON parsers, and domain models (pull requests, comments, tasks, build statuses, reviewers). Also holds the application services that combine clients with the access-token provider (`BbCloudAuthoredPullRequestService`, `BbCloudReviewQueueService`, `BbCloudOpenPullRequestService`, `BbCloudProjectRepositoryLinkService`). |
| `config` | `BbCloudConfigurationService`: the single facade the UI uses for setup state, workspace, projects directory, watched projects, and list filters. |
| `credentials` | `BbCloudCredentialStore` and its `PasswordSafe` implementation. Holds the API token and, as a separate entry, the OAuth token bundle (access token, refresh token, expiry); both may exist at once. |
| `git` | Git command execution, worktree creation and removal, branch checkout, and parsers for git output. |
| `project` | Mapping local checkouts and PR worktrees to Bitbucket repositories: watched-project discovery, worktree layout and resolution, prune decisions, opening local projects. |
| `review` | Per-project review state kept while a PR worktree is open: viewed files and threads, the worktree PR context, banners, page reloads, and the persisted recently-seen mention users cache (`BbCloudKnownUsersCache` / `BbCloudKnownUsersState`). |
| `settings` | Persisted plugin settings (`BbCloudSettings`) and the Settings > Tools configurable. |
| `toolwindow` | `BbCloudToolWindowFactory` and the tool window tab ids. |
| `ui` | `BbCloudToolWindowPanel` (the project service that owns the tool window tabs) and the sub-packages below. |
| `ui/main` | Current Project tab: the pull request detail views (overview, conversation, changes, history), comment and description editors, reviewer search, and @mention suggestions (`CommentMentionSuggestions`, `CommentMentionSource`). |
| `ui/pullrequests` | The shared pull request listing (`PullRequestsViewModel`, filtering, merging of listing sources) used by both PR tabs. |
| `ui/otherprojects` | Workspace and Workspace - Watched Projects tabs. |
| `ui/pullrequest/*` | PR-specific editors and IDE integration: `editor` (PR virtual file system, file editor, review bar, banners), `create` (new PR editor), `help` (Work Trees help tab), `decline`, `projectView`, `session`, `changes` (worktree changes tool window). |
| `ui/diff` | Inline comment threads and gutter actions in diff and editor views. |
| `ui/setup` | First-run setup and the Settings screen. |
| `ui/components` | Reusable Compose components. |
| `ui/worktree` | Worktree dialogs and `PullRequestWorktreeOpener`, the one implementation of "open a pull request in its own work tree" used by the Workspace and Current Project panels and by the REST open path. It lives under `ui` because it opens editor tabs and shows notifications. |
| `external` | Browser entry point: `BbCloudOpenPullRequestRestService` at `/api/bbcloud/open` (ping and open), its request validator, and the allowed extension origin. Requests run on a Netty thread, so work is handed to `ui/worktree`. |
| `validation` | Input validators for workspace and repository slugs. |

## UI pattern: UiState / ViewModel / Screen

Each screen has three parts:

- `*UiState`: an immutable data class, plus pure derivation functions that can be unit tested.
- `*ViewModel`: owns a `MutableState<UiState>`, runs the orchestration for the screen (loading, refresh generations,
  user actions), and receives its dependencies as constructor parameters (data-source interfaces or lambdas) so it can
  be tested without the IntelliJ platform.
- `*Screen`: a stateless `@Composable` that renders the state and forwards events to the ViewModel.

Example: `PullRequestOverviewUiState` / `PullRequestOverviewViewModel` / `PullRequestOverviewScreen` in `ui/main`.

`*Panel` classes (`BbCloudMainPanel`, `BbCloudOtherProjectsPanel`, `CheckedOutBranchPanel`) are Swing hosts. They wire
services into ViewModels, switch between not-configured and content cards, and show platform UI that needs a Swing
parent (dialogs, notifications). For example, on the Workspace tabs:

- `WatchedProjectsViewModel` runs bind, rescan, and watched-flag changes against a `WatchedProjectsDataSource`.
- `WorkspacePullRequestsLoader` loads the authored and reviewing sources into the shared `PullRequestsViewModel`
  against a `WorkspacePullRequestsDataSource`.

`BbCloudOtherProjectsPanel` still runs the worktree open, prune, and remove flows itself. They are candidates for the
same extraction. The Watched Projects remove button is the exception that delegates the work: the panel asks for
confirmation, then `BbCloudPullRequestDeclineCoordinator` runs `WatchedWorktreeRemover` on its application scope. The
remover closes an open IDE window on the work tree and then removes it, so the removal survives the project closing.
`WatchedWorktreeRemovalOrchestrator` bounds the progress modal to 5 seconds. The removal continues on the application
scope after the modal closes.

## Threading

- HTTP and git calls run on `Dispatchers.IO` inside coroutines launched from the panel's or service's
  `CoroutineScope`.
- Results go back to the EDT through `ApplicationManager.getApplication().invokeLater`. ViewModels take this hand-off
  as an injectable `runOnEdt` parameter, so tests can run it inline.
- Listings use refresh generations (for example `PullRequestsViewModel.beginRefresh`), so a slow response cannot
  overwrite a newer one.

## HTTP and credentials

- `OkHttpBitbucketPullRequestClient` sends every authenticated GET through one helper (`executeGet`). That helper
  attaches `Authorization: Bearer <token>` only when `BitbucketApiUrlPolicy.isTrustedApiUrl` accepts the URL: HTTPS,
  the default port, and the exact host `api.bitbucket.org`. This guards pagination `next` links, which come from
  response bodies. `OkHttpBitbucketWorkspaceClient` and `OkHttpBitbucketRepositoryClient` apply the same check.
- Avatar images are public CDN URLs and are fetched without the token (`BitbucketAvatarClient`).
- Never log tokens or raw response bodies (see AGENTS.md, Secrets and privacy).

## Authentication (`auth`)

Two credentials may be stored at once, and every API request resolves its bearer token through
`BbCloudAccessTokenProvider` (implemented by the application service `BbCloudAuthService`):

- **OAuth first, API token fallback.** `bearerTokenOrNull()` returns a working OAuth access token when one is
  available, otherwise the stored API token. `BbCloudAccessTokenResolver` holds the logic.
- **Proactive refresh.** An OAuth access token within five minutes of expiry is refreshed under a single-flight
  `Mutex`, so concurrent callers cause one refresh. A permanent refresh failure (`invalid_grant` / `invalid_client`)
  clears the OAuth tokens, shows one balloon, and falls back to the API token. A transient failure keeps the tokens
  and applies a one-minute backoff before retrying.
- **401 retry.** `BbCloudBearerRetryAuthenticator` is attached to the authenticated OkHttp clients. After a 401 on a
  trusted Bitbucket API URL it asks the provider for a refreshed token and retries the request once.

The OAuth login itself:

- `BbCloudOAuthLoginService` is an application service that owns the one login allowed per IDE; every project's
  Settings tab observes its `StateFlow` and can cancel it. `BbCloudOAuthLoginFlow` is the testable core (PKCE request,
  browser launch, callback wait, token exchange, result page).
- `JdkOAuthCallbackServer` is a short-lived `com.sun.net.httpserver` server bound to `127.0.0.1:42424`. It serves
  `/bbcloud` (the OAuth redirect, answered with a 302 to a code-free result page) and `/bbcloud/done` (the result
  page). The OAuth consumer callback URL must be exactly `http://127.0.0.1:42424/bbcloud`.

The configured gate:

- `BbCloudConfigurationService.isConfigured()` requires a workspace slug, an existing projects directory, and a
  credential that `BbCloudCredentialValidator` verifies working online with `GET /2.0/user`. The check result is
  cached for five minutes (30 seconds when the check could not reach Bitbucket). When offline, the validator falls
  back to the last known working method so the UI is not locked.

## Tests

- Unit tests live in `src/test/kotlin/com/github/fnunezkanut/bbcloud/`, in the same package as the code under test,
  and carry `@Tag("unit")`.
- HTTP clients are tested with an OkHttp `Interceptor` that captures requests and returns canned responses. No
  network is used.
- ViewModels are tested with fake data sources, `CoroutineScope(Dispatchers.Unconfined)`, and an inline `runOnEdt`.
- `plugin/` holds descriptor and service-constructor checks for `plugin.xml`.
