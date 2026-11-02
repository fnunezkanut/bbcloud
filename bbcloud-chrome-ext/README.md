# Bitbucket Cloud: Open in IntelliJ (Chrome extension)

A Manifest V3 Chrome extension that adds an **Open in IntelliJ** icon (the bbcloud plugin glyph) next to each pull
request on `bitbucket.org`. Clicking it opens that pull request in a git worktree in a running IntelliJ IDE that has
the **bbcloud** plugin installed.


## Install for your own use

1. Open `chrome://extensions`.
2. Turn on **Developer mode** (top-right).
3. Click **Load unpacked** and select this `bbcloud-chrome-ext/` directory.
4. Open a pull request list or a pull request page on `bitbucket.org`. An **Open in IntelliJ** icon appears next to
   each PR in a list, or beside the PR title on detail pages (`/overview`, `/diff`, `/commits`, and other sub-tabs).


## How it works

```
bitbucket.org page            this extension                         IntelliJ (bbcloud plugin)
──────────────────            ──────────────                         ─────────────────────────
content.js adds     ──click──▶ background.js finds the   ──HTTP──▶   /api/bbcloud/open
the plugin icon                IDE port and POSTs the                 opens the PR worktree
                    ◀─result── {workspace, repo, pr}     ◀──JSON──    and replies with JSON
```

- `content.js` runs on `https://bitbucket.org/*`.
  - **List pages:** finds pull request links by **URL structure** (`/<workspace>/<repo>/pull-requests/<n>`), not by
    CSS class, because Bitbucket's class names change between frontend releases.
  - **Detail and sub-tab pages** (`/<workspace>/<repo>/pull-requests/<n>/overview`, `/diff`, `/commits`, etc.): reads
    `workspace`, `repo`, and `pr` from the browser URL and injects the icon beside the PR title (`h1`), using stable
    `data-testid` selectors where available.
  - A `MutationObserver` and history hooks handle Bitbucket's single-page-app navigation after the initial render.
- `background.js` (the service worker) scans ports **63342–63361** for a running JetBrains IDE that answers the
  bbcloud ping, caches the port for the browser session, then forwards the open request as a `POST`. The request has
  to come from the service worker because Chrome's Private Network Access and CORS rules block the page itself from
  calling `localhost`.

## Requirements and limitations

- **The IDE must already be running.** This extension cannot start a closed IDE; it only talks to a live one.
- The bbcloud plugin's REST endpoint must be installed and the IDE built-in server must be enabled (it is by default:
  Settings → Build, Execution, Deployment → Debugger → Built-in Server).
- Chrome 116 or newer.

## Privacy

The extension only ever talks to `127.0.0.1` / `localhost`. It sends only three values — the `workspace`, `repo`
slug, and `pr` number parsed from the pull request URL — to the local IDE. It sends nothing to any external server.
.

## Extension ID 

`knninefpkpeielbdhlbhdheabjmkebpb`


## Files

| File | Purpose |
| --- | --- |
| `manifest.json` | MV3 manifest: permissions, content script match, service worker, icons, web-accessible icon. |
| `content.js` | Injects the Open in IntelliJ icon link on bitbucket.org pages. |
| `background.js` | Service worker: IDE port discovery, caching, and the POST request. |
| `icons/` | Toolbar icons (16/48/128 px). |
