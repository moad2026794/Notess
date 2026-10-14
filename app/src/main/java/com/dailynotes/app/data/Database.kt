package com.dailynotes.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val dayEpoch: Long,
    val pinned: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 AND dayEpoch = :day ORDER BY pinned DESC, updatedAt DESC")
    fun byDay(day: Long): Flow<List<Note>>

    @Query("SELECT DISTINCT dayEpoch FROM notes WHERE deleted = 0")
    fun days(): Flow<List<Long>>

    @Query("SELECT * FROM notes WHERE deleted = 0 AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%') ORDER BY dayEpoch DESC, updatedAt DESC")
    fun search(q: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted = 1 ORDER BY updatedAt DESC")
    fun trash(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Query("SELECT * FROM notes")
    suspend fun all(): List<Note>

    @Upsert
    suspend fun upsert(note: Note): Long

    @Insert
    suspend fun insertAll(notes: List<Note>)

    @Query("UPDATE notes SET deleted = :deleted, updatedAt = :time WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean, time: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purge(id: Long)

    @Query("DELETE FROM notes WHERE deleted = 1")
    suspend fun emptyTrash()
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "notes.db")
                .build().also { instance = it }
        }
    }
}
