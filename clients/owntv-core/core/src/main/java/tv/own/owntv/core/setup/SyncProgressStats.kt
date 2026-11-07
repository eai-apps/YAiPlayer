package tv.own.owntv.core.setup

/**
 * High-level counts and progress status reported during source import / synchronization.
 * Shown live on the setup screens (AddSourceScreen on TV, AddSourceForm on mobile).
 */
data class SyncProgressStats(
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val isEpgReady: Boolean = false,
    val isEpgInProgress: Boolean = false,
    val isRunning: Boolean = false,
    val isSuccess: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
)
