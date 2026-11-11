// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import tv.own.owntv.core.content.providerSearchText

val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(connection: SQLiteConnection) {
        for (table in listOf("movies", "series")) {
            for (column in listOf("originalTitle", "genres", "country", "director", "actors")) {
                if (!OwnTVDatabase.hasColumn(connection, table, column)) {
                    connection.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` TEXT")
                }
            }
            if (!OwnTVDatabase.hasColumn(connection, table, "searchText")) {
                connection.execSQL("ALTER TABLE `$table` ADD COLUMN `searchText` TEXT NOT NULL DEFAULT ''")
            }
            if (table == "series" && !OwnTVDatabase.hasColumn(connection, table, "durationSecs")) {
                connection.execSQL("ALTER TABLE `series` ADD COLUMN `durationSecs` INTEGER")
            }
            // Bounded memory, stable ids: history, resume and favourites remain attached.
            var afterId = -1L
            while (true) {
                val rows = ArrayList<Pair<Long, String>>(500)
                connection.prepare("SELECT id, name, year, plot FROM `$table` WHERE id > ? ORDER BY id LIMIT 500").use { read ->
                    read.bindLong(1, afterId)
                    while (read.step()) {
                        rows += read.getLong(0) to providerSearchText(read.getText(1),
                            if (read.isNull(2)) null else read.getLong(2),
                            if (read.isNull(3)) null else read.getText(3))
                    }
                }
                if (rows.isEmpty()) break
                connection.prepare("UPDATE `$table` SET searchText = ? WHERE id = ?").use { write ->
                    for ((id, text) in rows) {
                        write.bindText(1, text)
                        write.bindLong(2, id)
                        write.step()
                        write.reset()
                    }
                }
                afterId = rows.last().first
            }
        }
        OwnTVDatabase.healSchema(connection)
    }
}
