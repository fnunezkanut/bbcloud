// Background service worker (Manifest V3).
//
// Discovers the port of a running IntelliJ IDE that has the bbcloud plugin
// installed (the IDE built-in server uses the next free port in 63342-63361),
// caches it for the browser session, and forwards "open" requests from the
// content script as a POST to the plugin's REST endpoint.
//
// The request must originate here, in the service worker, rather than from the
// page: Chrome's Private Network Access and CORS rules block bitbucket.org
// pages (and MV3 content scripts, which use the page origin) from calling
// localhost.

const PORTS = Array.from({ length: 20 }, (_, i) => 63342 + i); // 63342-63361
const PING_TIMEOUT_MS = 300;

function endpointUrl(port) {
  return `http://127.0.0.1:${port}/api/bbcloud/open`;
}

// Returns true only for a port answering with this plugin's ping response, so
// other JetBrains IDEs listening in the same range are skipped.
async function ping(port) {
  try {
    const response = await fetch(endpointUrl(port), {
      signal: AbortSignal.timeout(PING_TIMEOUT_MS),
    });
    if (!response.ok) {
      return false;
    }
    const body = await response.json();
    return body && body.plugin === "bbcloud";
  } catch (error) {
    return false;
  }
}

async function getCachedPort() {
  try {
    const stored = await chrome.storage.session.get("port");
    return stored.port;
  } catch (error) {
    return undefined;
  }
}

async function setCachedPort(port) {
  try {
    await chrome.storage.session.set({ port });
  } catch (error) {
    // A storage failure only costs us a rescan next time; ignore it.
  }
}

async function clearCachedPort() {
  try {
    await chrome.storage.session.remove("port");
  } catch (error) {
    // Ignore; a stale value will be revalidated by ping on the next lookup.
  }
}

async function findPort() {
  const cached = await getCachedPort();
  if (cached && (await ping(cached))) {
    return cached;
  }
  if (cached) {
    await clearCachedPort();
  }

  for (const port of PORTS) {
    if (await ping(port)) {
      await setCachedPort(port);
      return port;
    }
  }
  return null;
}

async function openPullRequest(msg) {
  const port = await findPort();
  if (!port) {
    return { ok: false, message: "IntelliJ not running" };
  }

  try {
    const response = await fetch(endpointUrl(port), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // Forward only the three contract fields, never the raw message.
      body: JSON.stringify({
        workspace: msg.workspace,
        repo: msg.repo,
        pr: msg.pr,
      }),
    });
    const body = await response.json();
    return {
      ok: body.status === "accepted",
      message: body.message ?? body.status,
    };
  } catch (error) {
    // The IDE may have been quit between the ping and the POST; drop the cache
    // so the next click rescans.
    await clearCachedPort();
    return { ok: false, message: "IntelliJ not reachable" };
  }
}

chrome.runtime.onMessage.addListener((msg, _sender, reply) => {
  if (!msg || msg.type !== "open") {
    return false;
  }
  (async () => {
    reply(await openPullRequest(msg));
  })();
  // Keep the reply channel open for the async work.
  return true;
});
