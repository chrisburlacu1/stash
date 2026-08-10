package com.example.stash.data.local

import android.content.Context
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stash_items")
data class StashEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val category: String,
    val headline: String = "",
    val summary: String,
    val tags: String,
    val readTime: String,
    val savedAtEpochMillis: Long,
    val isRead: Boolean = false,
    val aiState: String,
    /**
     * Filename (not a full path) of the header image inside the app-private images dir, or "" when
     * the page had no og:image or the download failed. Stored as a bare name so the row survives
     * the app's data dir moving between installs/backups; [RoomStashRepository] resolves it.
     *
     * Deliberately not a remote URL: images are downloaded once at save time and rendered from
     * disk, so scrolling the feed never touches the network.
     */
    val imageFile: String = "",
)

@Fts5(prefix = [2, 3, 4])
@Entity(tableName = "stash_search")
data class StashSearchEntity(
    val id: String,
    val title: String,
    val domain: String,
    val category: String,
    val summary: String,
    val tags: String,
)

@Dao
interface StashDao {
    @Query("SELECT * FROM stash_items ORDER BY savedAtEpochMillis DESC")
    fun observeAll(): Flow<List<StashEntity>>

    @Query("SELECT * FROM stash_items WHERE tags = :tag OR tags LIKE :tag || ' | %' OR tags LIKE '% | ' || :tag OR tags LIKE '% | ' || :tag || ' | %' ORDER BY savedAtEpochMillis DESC")
    fun observeTag(tag: String): Flow<List<StashEntity>>

    @Query("""
        SELECT stash_items.* FROM stash_items
        JOIN stash_search ON stash_items.id = stash_search.id
        WHERE stash_search MATCH :ftsQuery
        AND (:tag IS NULL OR stash_items.tags = :tag OR stash_items.tags LIKE :tag || ' | %' OR stash_items.tags LIKE '% | ' || :tag OR stash_items.tags LIKE '% | ' || :tag || ' | %')
        ORDER BY bm25(stash_search, 0.0, 6.0, 2.0, 2.0, 3.0, 5.0), stash_items.savedAtEpochMillis DESC
    """)
    fun search(ftsQuery: String, tag: String?): Flow<List<StashEntity>>

    @Query("SELECT * FROM stash_items WHERE id = :id")
    fun observeItem(id: String): Flow<StashEntity?>

    @Query("SELECT tags FROM stash_items WHERE tags != ''")
    fun observeAllTags(): Flow<List<String>>

    @Query("SELECT tags FROM stash_items WHERE tags != ''")
    suspend fun allTags(): List<String>

    @Query("SELECT COUNT(*) FROM stash_items")
    suspend fun count(): Int

    @Query("UPDATE stash_items SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean)

    @Query("DELETE FROM stash_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    /** Both tables must drop the row; stash_search has no foreign key to cascade from. */
    @Transaction
    suspend fun delete(id: String) {
        deleteItem(id)
        deleteSearch(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: StashEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(item: StashSearchEntity)

    /**
     * stash_search is an FTS5 virtual table, so `id` carries no uniqueness constraint and
     * OnConflictStrategy.REPLACE has nothing to conflict on — inserting the same id twice
     * appends a duplicate row. addUrl() upserts each item at least twice (placeholder, then
     * AI result), so the stale row must be deleted explicitly or the search JOIN emits the
     * item more than once and LazyColumn crashes on the duplicate key.
     */
    @Query("DELETE FROM stash_search WHERE id = :id")
    suspend fun deleteSearch(id: String)

    @Transaction
    suspend fun upsert(item: StashEntity) {
        upsertItem(item)
        deleteSearch(item.id)
        upsertSearch(
            StashSearchEntity(item.id, item.title, item.domain, item.category, item.summary, item.tags)
        )
    }
}

@Database(
    entities = [StashEntity::class, StashSearchEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class StashDatabase : RoomDatabase() {
    abstract fun stashDao(): StashDao

    companion object {
        @Volatile private var instance: StashDatabase? = null

        /**
         * Rebuilds stash_search from stash_items, the deduplicated source of truth.
         *
         * The FTS options below must be quoted with backticks: Room compares its generated
         * FtsTableInfo against the live table as literal text, and single quotes here fail
         * validation with "Migration didn't properly handle: stash_search" even though the
         * columns are identical.
         */
        private fun fts5Migration(startVersion: Int) = Migration(startVersion, 3) { connection ->
            if (startVersion == 1) {
                connection.execSQL("ALTER TABLE stash_items ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
            connection.execSQL("DROP TABLE IF EXISTS stash_search")
            connection.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS stash_search USING FTS5(
                    id, title, domain, category, summary, tags,
                    prefix=`2 3 4`, tokenize=`unicode61`
                )
            """.trimIndent())
            connection.execSQL("""
                INSERT INTO stash_search(id, title, domain, category, summary, tags)
                SELECT id, title, domain, category, summary, tags FROM stash_items
            """.trimIndent())
        }

        /** Adds the feed's short headline column; existing rows fall back to their summary. */
        private val migration3To4 = Migration(3, 4) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN headline TEXT NOT NULL DEFAULT ''")
        }

        /** Adds the read/unread flag; everything saved before this is treated as unread. */
        private val migration4To5 = Migration(4, 5) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
        }

        /**
         * Adds the cached header image filename. Rows saved before this have no image and keep
         * the empty default — the card just renders without one, so no backfill is needed.
         */
        private val migration5To6 = Migration(5, 6) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN imageFile TEXT NOT NULL DEFAULT ''")
        }

        fun get(context: Context): StashDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StashDatabase::class.java,
                "stash.db",
            ).setDriver(BundledSQLiteDriver())
                .addMigrations(
                    fts5Migration(1),
                    fts5Migration(2),
                    migration3To4,
                    migration4To5,
                    migration5To6,
                )
                .build().also { instance = it }
        }
    }
}
