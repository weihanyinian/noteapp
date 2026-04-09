package com.example.mymind.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.data.local.relation.MindMapWithNodes

@Dao
interface MindMapDao {

    @Query("SELECT * FROM mind_maps WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun observeAll(): LiveData<List<MindMapEntity>>

    @Query(
        "SELECT * FROM mind_maps " +
            "WHERE isDeleted = 0 AND (" +
            "title LIKE '%' || :query || '%' OR rootNodeTitle LIKE '%' || :query || '%'" +
            ") " +
            "ORDER BY updatedAt DESC"
    )
    fun observeSearch(query: String): LiveData<List<MindMapEntity>>

    @Query("SELECT * FROM mind_maps WHERE id = :mindMapId LIMIT 1")
    suspend fun getById(mindMapId: Long): MindMapEntity?

    @Query("SELECT * FROM mind_maps WHERE isDeleted = 1 ORDER BY deleteTime DESC, updatedAt DESC")
    fun observeTrash(): LiveData<List<MindMapEntity>>

    @Transaction
    @Query("SELECT * FROM mind_maps WHERE id = :mindMapId LIMIT 1")
    fun observeMindMapWithNodes(mindMapId: Long): LiveData<MindMapWithNodes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mindMap: MindMapEntity): Long

    @Update
    suspend fun update(mindMap: MindMapEntity)

    @Query("UPDATE mind_maps SET updatedAt = :updatedAt WHERE id = :mindMapId")
    suspend fun touch(mindMapId: Long, updatedAt: Long)

    @Query("UPDATE mind_maps SET rootNodeTitle = :rootNodeTitle, updatedAt = :updatedAt WHERE id = :mindMapId")
    suspend fun updateRootNodeTitle(mindMapId: Long, rootNodeTitle: String, updatedAt: Long)

    @Query("UPDATE mind_maps SET isDeleted = 1, deleteTime = :deleteTime, updatedAt = :updatedAt WHERE id = :mindMapId")
    suspend fun softDelete(mindMapId: Long, deleteTime: Long, updatedAt: Long)

    @Query("UPDATE mind_maps SET isDeleted = 0, deleteTime = NULL, updatedAt = :updatedAt WHERE id = :mindMapId")
    suspend fun restore(mindMapId: Long, updatedAt: Long)

    @Query("DELETE FROM mind_maps WHERE isDeleted = 1 AND deleteTime IS NOT NULL AND deleteTime <= :cutoffTime")
    suspend fun purgeDeletedBefore(cutoffTime: Long): Int

    @Query("DELETE FROM mind_maps WHERE id = :mindMapId")
    suspend fun permanentDelete(mindMapId: Long)

    @Query("DELETE FROM mind_maps WHERE isDeleted = 1")
    suspend fun purgeAllDeleted()

    @Query("SELECT COUNT(*) FROM mind_maps")
    suspend fun count(): Int
}
