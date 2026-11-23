// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v54 -> v55: movies.ageRating and series.ageRating (the portal's age limit, owner 23.11). Additive. */
val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(connection: SQLiteConnection) {
        for (table in listOf("movies", "series")) {
            if (!OwnTVDatabase.hasColumn(connection, table, "ageRating")) {
                connection.execSQL("ALTER TABLE `$table` ADD COLUMN `ageRating` TEXT")
            }
        }
        OwnTVDatabase.healSchema(connection)
    }
}
