// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.account

/**
 * Where the account server and the cabinet site live. A rebrand / operator build changes only this file
 * (docs/REBRAND.md): the operator's server runs the same code on his domain (yai-cloud worker/deploy).
 */
object AccountConfig {
    const val API_BASE = "https://yaiplayer-api.yai-media-player.workers.dev"
    const val SITE_URL = "https://yaiplayer.pages.dev"
    const val TENANT = "yai"

    /**
     * Shows the account tile on the profile chooser (TV and phone). On in the branch so the owner can try the
     * debug APK; switch to false to ship a release without it.
     */
    const val ENABLED = true
}
