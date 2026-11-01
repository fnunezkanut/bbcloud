package com.github.fnunezkanut.bbcloud.external

/** Browser origins that may call the bbcloud REST endpoint `/api/bbcloud/open`. */
object BbCloudExternalOrigins {
    /** The scheme prefix of every Chrome extension origin. */
    const val CHROME_EXTENSION_PREFIX = "chrome-extension://"

    /**
     * The exact origin of the bbcloud Chrome extension, which the extension sends as its `Origin` header.
     *
     * PLACEHOLDER: the extension manifest has no fixed `key` yet, so its ID is unknown. This value matches no real
     * extension, so every open request is rejected until it is replaced with `chrome-extension://<extension-id>`.
     * The ping still works, because it accepts any Chrome extension origin.
     */
    const val ALLOWED_EXTENSION_ORIGIN = "chrome-extension://knninefpkpeielbdhlbhdheabjmkebpb"
}