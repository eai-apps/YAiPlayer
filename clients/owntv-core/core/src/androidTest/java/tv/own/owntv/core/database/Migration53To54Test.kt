// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.content.providerSearchText
import tv.own.owntv.core.database.entity.MovieEntity

@RunWith(AndroidJUnit4::class)
class Migration53To54Test {
    @Test fun migrationPreservesRowsAndSearchMatchesProviderFieldsInBothAlphabets() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "operator-migration-54-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val schema = JSONObject(instrumentation.context.assets
                .open("tv.own.owntv.core.database.OwnTVDatabase/53.json").bufferedReader().use { it.readText() })
                .getJSONObject("database")
            createSchema(old, schema)
            old.execSQL("INSERT INTO sources (id,name,type,url,syncLive,syncMovies,syncSeries,hlsSupported,preferHls,livePrerollSecs,liveLatencyCustomSecs,maxConnections,maxConnectionsProbedAt,createdAt) VALUES (10,'Portal','STALKER','http://example.test',1,1,1,0,0,-1,-1,0,0,2)")
            old.execSQL("INSERT INTO movies (id,sourceId,name,streamUrl,sortOrder,year) VALUES (30,10,'Старый фильм','http://example.test/movie',0,2024)")
            old.execSQL("INSERT INTO series (id,sourceId,name,sortOrder) VALUES (40,10,'Сериал',0)")
            old.version = 53
        }
        val db = Room.databaseBuilder(context, OwnTVDatabase::class.java, name)
            .addMigrations(*OwnTVDatabase.ALL_MIGRATIONS).setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries().build()
        try {
            db.useWriterConnection { } // Executes migration and Room's schema validation.
            val movie = db.movieDao().getById(30)!!
            assertEquals("Старый фильм", movie.name)
            assertEquals(2024, movie.year)
            assertNull(movie.originalTitle)
            assertEquals("старый фильм 2024", movie.searchText)
            assertEquals("Сериал", db.seriesDao().getSeriesById(40)!!.name)
            val updated = movie.copy(originalTitle = "Original Film", genres = "Драма", country = "Германия",
                director = "Режиссёр", actors = "Actor Name",
                searchText = providerSearchText(movie.name, "Original Film", "Драма", movie.year, "Германия", "Режиссёр", "Actor Name"))
            db.movieDao().updateAll(listOf(updated))
            for (query in listOf("ORIGINAL", "ДРАМА", "2024", "ГЕРМАНИЯ", "Режиссер", "ACTOR")) {
                val result = db.movieDao().searchAll(providerSearchText(query), listOf(10))
                    .load(PagingSource.LoadParams.Refresh(null, 10, false))
                assertTrue(query, result is PagingSource.LoadResult.Page<*, *>)
                assertEquals(listOf(30L), (result as PagingSource.LoadResult.Page<Int, MovieEntity>).data.map(MovieEntity::id))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createSchema(db: SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
    }
}
