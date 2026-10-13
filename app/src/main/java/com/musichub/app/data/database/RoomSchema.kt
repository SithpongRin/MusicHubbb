package com.musichub.app.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["isFavorite"]),
        Index(value = ["dateAdded"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val artworkUri: String?,
    val dateAdded: Long,
    val playCount: Int = 0,
    val lastPlayed: Long? = null,
    val isFavorite: Boolean = false,
    val format: String = "mp3",
    val bitRate: String? = null,
    val sizeBytes: Long = 0L,
    val isDownloaded: Boolean = true
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val artworkUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId", "position"],
    indices = [Index(value = ["playlistId"]), Index(value = ["songId"])]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val quality: String,
    val progress: Int,
    val status: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val errorMessage: String?
)

/** Cached output from a versioned bundled model; no raw audio leaves the device. */
@Entity(
    tableName = "mood_predictions",
    primaryKeys = ["songId", "modelVersion"],
    indices = [Index(value = ["modelVersion"])]
)
data class MoodPredictionEntity(
    val songId: String,
    val modelVersion: String,
    val valence: Float,
    val arousal: Float,
    val calm: Float,
    val energetic: Float,
    val sad: Float,
    val romantic: Float,
    val confidence: Float,
    val analyzedAt: Long
) {
    fun scores() = floatArrayOf(calm, energetic, sad, romantic)
}

// --- Room DAOs ---

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY dateAdded DESC")
    fun getDownloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: String, timestamp: Long)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
    """)
    fun getSongsForPlaylist(playlistId: String): Flow<List<SongEntity>>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY rowid DESC")
    fun getAllDownloads(): Flow<List<DownloadTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MoodPredictionDao {
    @Query("SELECT * FROM mood_predictions WHERE songId = :songId AND modelVersion = :modelVersion LIMIT 1")
    suspend fun get(songId: String, modelVersion: String): MoodPredictionEntity?

    @Query("SELECT * FROM mood_predictions WHERE modelVersion = :modelVersion")
    suspend fun getAllForModel(modelVersion: String): List<MoodPredictionEntity>

    @Query("SELECT * FROM mood_predictions WHERE modelVersion = :modelVersion")
    fun getAllForModelSync(modelVersion: String): List<MoodPredictionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prediction: MoodPredictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSync(prediction: MoodPredictionEntity)
}

// --- Room Database ---

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        DownloadTaskEntity::class,
        MoodPredictionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun moodPredictionDao(): MoodPredictionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "musichub_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS mood_predictions (
                        songId TEXT NOT NULL,
                        modelVersion TEXT NOT NULL,
                        valence REAL NOT NULL,
                        arousal REAL NOT NULL,
                        calm REAL NOT NULL,
                        energetic REAL NOT NULL,
                        sad REAL NOT NULL,
                        romantic REAL NOT NULL,
                        confidence REAL NOT NULL,
                        analyzedAt INTEGER NOT NULL,
                        PRIMARY KEY(songId, modelVersion)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mood_predictions_modelVersion ON mood_predictions(modelVersion)")
            }
        }
    }
}
