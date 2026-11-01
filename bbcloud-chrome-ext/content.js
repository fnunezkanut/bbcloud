// Content script for bitbucket.org pages.
//
// Finds pull request links by their URL structure (never by CSS class, which
// Bitbucket changes between frontend releases) and injects an icon-only link
// (the bbcloud plugin glyph) next to the first anchor for each pull request.
// Clicking it asks the background service worker to forward the request to a
// running IntelliJ IDE's built-in HTTP server.

// Matches /<workspace>/<repo>/pull-requests/<n> with an optional trailing
// slash. Deliberately skips sub-pages such as /pull-requests/123/diff.
const PR_PATH = /^\/([^/]+)\/([^/]+)\/pull-requests\/(\d+)\/?$/;

// The plugin glyph, bundled with the extension and exposed via
// web_accessible_resources so a content script can load it by extension URL.
const ICON_URL = chrome.runtime.getURL("icons/icon48.png");
const RETRY_RESET_MS = 4000;

function createOpenLink(workspace, repo, pr) {
  const defaultTitle = `Open pull request #${pr} in IntelliJ (${workspace}/${repo})`;

  const link = document.createElement("a");
  link.href = "#";
  link.title = defaultTitle;
  link.setAttribute("aria-label", defaultTitle);
  link.style.display = "inline-flex";
  link.style.alignItems = "center";
  link.style.marginLeft = "8px";
  link.style.verticalAlign = "middle";
  link.style.lineHeight = "0";
  link.dataset.bbcloudInjected = "1";

  const icon = document.createElement("img");
  icon.src = ICON_URL;
  icon.alt = "Open in IntelliJ";
  icon.width = 16;
  icon.height = 16;
  icon.style.display = "block";
  icon.style.transition = "opacity 120ms ease";
  link.appendChild(icon);

  let inFlight = false;

  link.addEventListener("click", async (event) => {
    event.preventDefault();
    if (inFlight) {
      return;
    }
    inFlight = true;
    // Dim the icon while the open is in flight; the title carries the state so
    // the icon-only link stays accessible.
    icon.style.opacity = "0.4";
    link.title = `Opening pull request #${pr} in IntelliJ…`;

    let result;
    try {
      result = await chrome.runtime.sendMessage({
        type: "open",
        workspace,
        repo,
        pr: Number(pr),
      });
    } catch (error) {
      result = { ok: false, message: "IntelliJ not running" };
    }

    if (!result) {
      // sendMessage can resolve to undefined when the worker closed the
      // channel without replying (e.g. it was terminated mid-flight).
      result = { ok: false, message: "IntelliJ not running" };
    }

    icon.style.opacity = result.ok ? "1" : "0.5";
    link.title = result.ok
      ? `Opened pull request #${pr} in IntelliJ`
      : result.message || "Could not open in IntelliJ";

    // Re-enable after a short delay so the user can retry.
    window.setTimeout(() => {
      inFlight = false;
      icon.style.opacity = "1";
      link.title = defaultTitle;
    }, RETRY_RESET_MS);
  });

  return link;
}

function addLinks() {
  // De-duplicate per pull request within this pass: a single row can hold
  // several anchors to the same PR (title, number, avatar). We inject next to
  // the first matching anchor only.
  const seen = new Set();

  for (const anchor of document.querySelectorAll('a[href*="/pull-requests/"]')) {
    if (anchor.dataset.bbcloud || anchor.dataset.bbcloudInjected) {
      continue;
    }

    let pathname;
    try {
      pathname = new URL(anchor.href).pathname;
    } catch (error) {
      continue;
    }

    const match = pathname.match(PR_PATH);
    if (!match) {
      continue;
    }

    // Mark before injecting so a re-entrant observer callback cannot
    // double-process this anchor.
    anchor.dataset.bbcloud = "1";

    const [, workspace, repo, pr] = match;
    const key = `${workspace}/${repo}/${pr}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);

    const link = createOpenLink(workspace, repo, pr);
    anchor.after(link);
  }
}

// Bitbucket is a single-page app; rows load after the initial render. Coalesce
// observer callbacks with requestAnimationFrame so a burst of DOM mutations
// triggers at most one addLinks pass per frame.
let scheduled = false;

function scheduleAddLinks() {
  if (scheduled) {
    return;
  }
  scheduled = true;
  window.requestAnimationFrame(() => {
    scheduled = false;
    addLinks();
  });
}

addLinks();
new MutationObserver(scheduleAddLinks).observe(document.body, {
  childList: true,
  subtree: true,
});
