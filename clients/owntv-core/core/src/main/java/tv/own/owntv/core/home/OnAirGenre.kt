package tv.own.owntv.core.home

import tv.own.owntv.core.R

/**
 * The 7 genre categories for "On Air Now" feed plus the default ALL category.
 */
enum class OnAirGenre(val titleRes: Int) {
    ALL(R.string.on_air_genre_all),
    MOVIES(R.string.on_air_genre_movies),
    SERIES(R.string.on_air_genre_series),
    SPORTS(R.string.on_air_genre_sports),
    NEWS(R.string.on_air_genre_news),
    KIDS(R.string.on_air_genre_kids),
    MUSIC(R.string.on_air_genre_music),
    EDUCATIONAL(R.string.on_air_genre_educational);
}
