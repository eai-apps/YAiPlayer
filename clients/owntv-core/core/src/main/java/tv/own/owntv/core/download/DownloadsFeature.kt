package tv.own.owntv.core.download

/**
 * Product switch for saving films and episodes onto the device. YAiPlayer is a player for the
 * viewer's own sources and does not offer offline copies, so this is off: every Download action
 * (menus, detail pages, the per-profile "Allow downloads" switch) is hidden and
 * [tv.own.owntv.core.settings.SettingsRepository.downloadsAllowed] reports false for everyone.
 * The engine stays in the tree untouched so the feature can come back by flipping this one value.
 */
object DownloadsFeature {
    const val ENABLED = false
}
