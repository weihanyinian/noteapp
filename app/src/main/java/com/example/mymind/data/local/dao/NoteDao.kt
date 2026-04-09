package com.example.mymind.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mymind.data.local.entity.NoteEntity

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): LiveData<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE isDeleted = 0 AND (" +
            "title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'" +
            ") " +
            "ORDER BY updatedAt DESC"
    )
    fun observeSearch(query: String): LiveData<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    fun observeById(noteId: Long): LiveData<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deleteTime DESC, updatedAt DESC")
    fun observeTrash(): LiveData<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, deleteTime = :deleteTime, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun softDelete(noteId: Long, deleteTime: Long, updatedAt: Long)

    @Query("UPDATE notes SET isDeleted = 0, deleteTime = NULL, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun restore(noteId: Long, updatedAt: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deleteTime IS NOT NULL AND deleteTime <= :cutoffTime")
    suspend fun purgeDeletedBefore(cutoffTime: Long): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun permanentDelete(noteId: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun purgeAllDeleted()

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
