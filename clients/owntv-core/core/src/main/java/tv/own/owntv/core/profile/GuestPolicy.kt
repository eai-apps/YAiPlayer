package tv.own.owntv.core.profile

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * Enforces guest profile limitations:
 * - A guest may have at most 1 playlist (M3U or Xtream) and at most 1 Stalker portal.
 * - Regular (registered / non-guest) profiles have no limits.
 * - Hook card on Home is shown once per week to guest users.
 */
object GuestPolicy {
    const val MAX_PLAYLISTS = 1
    const val MAX_STALKER_PORTALS = 1

    /** 7 days in milliseconds */
    const val HOOK_DISMISS_DURATION_MS = 7L * 24 * 60 * 60 * 1000L

    fun isPlaylist(type: SourceType): Boolean =
        type == SourceType.M3U || type == SourceType.XTREAM || type == SourceType.LOCAL_BACKUP

    fun isStalker(type: SourceType): Boolean =
        type == SourceType.STALKER

    fun playlistCount(sources: Collection<SourceEntity>): Int =
        sources.count { isPlaylist(it.type) }

    fun stalkerCount(sources: Collection<SourceEntity>): Int =
        sources.count { isStalker(it.type) }

    /**
     * Whether a new source of [type] can be added.
     * Regular profiles ([isGuest] == false) are unlimited.
     */
    fun canAddSource(isGuest: Boolean, currentSources: Collection<SourceEntity>, type: SourceType): Boolean {
        if (!isGuest) return true
        return when {
            isPlaylist(type) -> playlistCount(currentSources) < MAX_PLAYLISTS
            isStalker(type) -> stalkerCount(currentSources) < MAX_STALKER_PORTALS
            else -> true
        }
    }

    /**
     * Whether any new source (playlist or stalker) can be added under guest limits.
     */
    fun canAddAnySource(isGuest: Boolean, currentSources: Collection<SourceEntity>): Boolean {
        if (!isGuest) return true
        return playlistCount(currentSources) < MAX_PLAYLISTS || stalkerCount(currentSources) < MAX_STALKER_PORTALS
    }

    /**
     * Whether the limit for [type] has been reached.
     */
    fun isLimitReached(isGuest: Boolean, currentSources: Collection<SourceEntity>, type: SourceType): Boolean =
        !canAddSource(isGuest, currentSources, type)

    /**
     * Whether the hook card ("Зарегистрируйтесь — плейлисты синхронизируются...") should be shown.
     */
    fun shouldShowHook(isGuest: Boolean, dismissedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!isGuest) return false
        if (dismissedAt <= 0L) return true
        return (now - dismissedAt) >= HOOK_DISMISS_DURATION_MS
    }
}
