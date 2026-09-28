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
    val imageFile: String = "",
    val content: String = "",
    val seedColor: Int = 0,
    val cropBias: Float = 0f,
    val imageUrl: String = "",
    val updatedAtEpochMillis: Long = savedAtEpochMillis,
    val topic: String = "",
)

data class StashListRow(
    val id: String,
    val url: String,
    val title: String,
    val domain: String,
    val category: String,
    val headline: String,
    val summary: String,
    val tags: String,
    val readTime: String,
    val savedAtEpochMillis: Long,
    val isRead: Boolean,
    val aiState: String,
    val imageFile: String,
    val seedColor: Int,
    val cropBias: Float,
    val imageUrl: String = "",
    val updatedAtEpochMillis: Long = savedAtEpochMillis,
    val topic: String = "",
)

data class SeedBackfillRow(
    val id: String,
    val imageFile: String,
    val seedColor: Int = 0,
    val cropBias: Float = 0f,
)

data class TagBackfillRow(
    val id: String,
    val tags: String,
)

data class TopicBackfillRow(
    val id: String,
    val title: String,
    val summary: String,
    val tags: String,
    val topic: String,
)

data class TwitterImageBackfillRow(
    val id: String,
    val url: String,
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
    val topic: String = "",
)

@Dao
interface StashDao {
    @Query("""
        SELECT id, url, title, domain, category, headline, summary, tags, readTime,
               savedAtEpochMillis, isRead, aiState, imageFile, seedColor, cropBias,
               imageUrl, updatedAtEpochMillis, topic
        FROM stash_items ORDER BY savedAtEpochMillis DESC
    """)
    fun observeAll(): Flow<List<StashListRow>>

    @Query("""
        SELECT id, url, title, domain, category, headline, summary, tags, readTime,
               savedAtEpochMillis, isRead, aiState, imageFile, seedColor, cropBias,
               imageUrl, updatedAtEpochMillis, topic
        FROM stash_items
        WHERE tags = :tag OR tags LIKE :tag || ' | %' OR tags LIKE '% | ' || :tag OR tags LIKE '% | ' || :tag || ' | %'
        ORDER BY savedAtEpochMillis DESC
    """)
    fun observeTag(tag: String): Flow<List<StashListRow>>

    @Query("""
        SELECT id, url, title, domain, category, headline, summary, tags, readTime,
               savedAtEpochMillis, isRead, aiState, imageFile, seedColor, cropBias,
               imageUrl, updatedAtEpochMillis, topic
        FROM stash_items
        WHERE topic = :topic
        ORDER BY savedAtEpochMillis DESC
    """)
    fun observeTopic(topic: String): Flow<List<StashListRow>>

    @Query("""
        SELECT stash_items.id, stash_items.url, stash_items.title, stash_items.domain,
               stash_items.category, stash_items.headline, stash_items.summary, stash_items.tags,
               stash_items.readTime, stash_items.savedAtEpochMillis, stash_items.isRead,
               stash_items.aiState, stash_items.imageFile, stash_items.seedColor, stash_items.cropBias,
               stash_items.imageUrl, stash_items.updatedAtEpochMillis, stash_items.topic
        FROM stash_items
        JOIN stash_search ON stash_items.id = stash_search.id
        WHERE stash_search MATCH :ftsQuery
        AND (:tag IS NULL OR stash_items.tags = :tag OR stash_items.tags LIKE :tag || ' | %' OR stash_items.tags LIKE '% | ' || :tag OR stash_items.tags LIKE '% | ' || :tag || ' | %')
        ORDER BY bm25(stash_search, 0.0, 6.0, 2.0, 2.0, 3.0, 5.0, 4.0), stash_items.savedAtEpochMillis DESC
    """)
    fun search(ftsQuery: String, tag: String?): Flow<List<StashListRow>>

    @Query("SELECT * FROM stash_items WHERE id = :id")
    fun observeItem(id: String): Flow<StashEntity?>

    @Query("SELECT * FROM stash_items WHERE id IN (:ids)")
    fun observeItems(ids: List<String>): Flow<List<StashEntity>>

    @Query("SELECT imageFile FROM stash_items WHERE id = :id")
    suspend fun imageFileFor(id: String): String?

    @Query("SELECT tags FROM stash_items WHERE tags != ''")
    fun observeAllTags(): Flow<List<String>>

    @Query("SELECT tags FROM stash_items WHERE tags != ''")
    suspend fun allTags(): List<String>

    @Query("SELECT topic FROM stash_items WHERE topic != ''")
    fun observeAllTopics(): Flow<List<String>>

    @Query("SELECT topic FROM stash_items WHERE topic != ''")
    suspend fun allTopics(): List<String>

    @Query("SELECT COUNT(*) FROM stash_items")
    suspend fun count(): Int

    @Query("SELECT id, imageFile, seedColor, cropBias FROM stash_items WHERE seedColor = 0 AND imageFile != ''")
    suspend fun rowsMissingSeed(): List<SeedBackfillRow>

    @Query("SELECT id, imageFile, seedColor, cropBias FROM stash_items WHERE imageFile != ''")
    suspend fun rowsWithImage(): List<SeedBackfillRow>

    @Query("SELECT id, tags FROM stash_items WHERE tags != ''")
    suspend fun rowsForTagBackfill(): List<TagBackfillRow>

    @Query("SELECT id, title, summary, tags, topic FROM stash_items WHERE topic = '' OR topic IS NULL")
    suspend fun rowsForTopicBackfill(): List<TopicBackfillRow>

    @Query("SELECT id, url FROM stash_items WHERE imageFile = '' AND (url LIKE '%twitter.com%' OR url LIKE '%x.com%')")
    suspend fun rowsMissingTwitterImage(): List<TwitterImageBackfillRow>

    @Query("SELECT id, url, imageFile FROM stash_items WHERE url LIKE '%twitter.com%' OR url LIKE '%x.com%'")
    suspend fun allTwitterRows(): List<TwitterImageBackfillRow>

    @Query("UPDATE stash_items SET imageFile = :imageFile, seedColor = :seedColor, cropBias = :cropBias WHERE id = :id")
    suspend fun setImageData(id: String, imageFile: String, seedColor: Int, cropBias: Float)

    @Query("UPDATE stash_items SET seedColor = :seedColor, cropBias = :cropBias WHERE id = :id")
    suspend fun setSeedAndCrop(id: String, seedColor: Int, cropBias: Float)

    @Query("UPDATE stash_items SET tags = :tags WHERE id = :id")
    suspend fun updateItemTags(id: String, tags: String)

    @Query("UPDATE stash_search SET tags = :tags WHERE id = :id")
    suspend fun updateSearchTags(id: String, tags: String)

    @Transaction
    suspend fun setTags(id: String, tags: String) {
        updateItemTags(id, tags)
        updateSearchTags(id, tags)
    }

    @Query("UPDATE stash_items SET topic = :topic WHERE id = :id")
    suspend fun updateItemTopic(id: String, topic: String)

    @Query("UPDATE stash_search SET topic = :topic WHERE id = :id")
    suspend fun updateSearchTopic(id: String, topic: String)

    @Transaction
    suspend fun setTopic(id: String, topic: String) {
        updateItemTopic(id, topic)
        updateSearchTopic(id, topic)
    }

    @Query("UPDATE stash_items SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: String, isRead: Boolean)

    @Query("DELETE FROM stash_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Transaction
    suspend fun delete(id: String) {
        deleteItem(id)
        deleteSearch(id)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: StashEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(item: StashSearchEntity)

    @Query("DELETE FROM stash_search WHERE id = :id")
    suspend fun deleteSearch(id: String)

    @Transaction
    suspend fun upsert(item: StashEntity) {
        upsertItem(item)
        deleteSearch(item.id)
        upsertSearch(
            StashSearchEntity(item.id, item.title, item.domain, item.category, item.summary, item.tags, item.topic)
        )
    }
}

@Database(
    entities = [StashEntity::class, StashSearchEntity::class],
    version = 11,
    exportSchema = false,
)
abstract class StashDatabase : RoomDatabase() {
    abstract fun stashDao(): StashDao

    companion object {
        @Volatile private var instance: StashDatabase? = null

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

        private val migration3To4 = Migration(3, 4) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN headline TEXT NOT NULL DEFAULT ''")
        }

        private val migration4To5 = Migration(4, 5) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN isRead INTEGER NOT NULL DEFAULT 0")
        }

        private val migration5To6 = Migration(5, 6) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN imageFile TEXT NOT NULL DEFAULT ''")
        }

        private val migration6To7 = Migration(6, 7) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN content TEXT NOT NULL DEFAULT ''")
        }

        private val migration7To8 = Migration(7, 8) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN seedColor INTEGER NOT NULL DEFAULT 0")
        }

        private val migration8To9 = Migration(8, 9) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN cropBias REAL NOT NULL DEFAULT 0")
        }

        private val migration9To10 = Migration(9, 10) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        }

        private val migration10To11 = Migration(10, 11) { connection ->
            connection.execSQL("ALTER TABLE stash_items ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
            connection.execSQL("DROP TABLE IF EXISTS stash_search")
            connection.execSQL("""
                CREATE VIRTUAL TABLE IF NOT EXISTS stash_search USING FTS5(
                    id, title, domain, category, summary, tags, topic,
                    prefix=`2 3 4`, tokenize=`unicode61`
                )
            """.trimIndent())
            connection.execSQL("""
                INSERT INTO stash_search(id, title, domain, category, summary, tags, topic)
                SELECT id, title, domain, category, summary, tags, topic FROM stash_items
            """.trimIndent())
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
                    migration6To7,
                    migration7To8,
                    migration8To9,
                    migration9To10,
                    migration10To11,
                )
                .build().also { instance = it }
        }
    }
}
