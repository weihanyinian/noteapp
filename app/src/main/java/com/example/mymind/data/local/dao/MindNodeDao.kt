package com.example.mymind.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mymind.data.local.entity.MindNodeEntity

@Dao
interface MindNodeDao {

    @Query("SELECT * FROM mind_nodes WHERE mindMapId = :mindMapId AND isDeleted = 0 ORDER BY depth ASC, branchOrder ASC, id ASC")
    fun observeByMindMapId(mindMapId: Long): LiveData<List<MindNodeEntity>>

    @Query("SELECT * FROM mind_nodes WHERE mindMapId = :mindMapId AND isDeleted = 0 ORDER BY depth ASC, branchOrder ASC, id ASC")
    suspend fun getByMindMapId(mindMapId: Long): List<MindNodeEntity>

    @Query("SELECT * FROM mind_nodes WHERE mindMapId = :mindMapId ORDER BY depth ASC, branchOrder ASC, id ASC")
    suspend fun getAllByMindMapId(mindMapId: Long): List<MindNodeEntity>

    @Query("SELECT * FROM mind_nodes WHERE id = :nodeId LIMIT 1")
    suspend fun getById(nodeId: Long): MindNodeEntity?

    @Query("SELECT * FROM mind_nodes WHERE noteId = :noteId LIMIT 100")
    suspend fun getByNoteId(noteId: Long): List<MindNodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: MindNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<MindNodeEntity>)

    @Update
    suspend fun update(node: MindNodeEntity)

    @Query("UPDATE mind_nodes SET noteId = :noteId WHERE id = :nodeId")
    suspend fun bindNote(nodeId: Long, noteId: Long?)

    @Query("UPDATE mind_nodes SET posX = :posX, posY = :posY WHERE id = :nodeId")
    suspend fun updatePosition(nodeId: Long, posX: Float?, posY: Float?)

    @Query("UPDATE mind_nodes SET content = :content WHERE id = :nodeId")
    suspend fun updateContent(nodeId: Long, content: String)

    @Query("UPDATE mind_nodes SET isCollapsed = :isCollapsed WHERE id = :nodeId")
    suspend fun updateCollapsed(nodeId: Long, isCollapsed: Boolean)

    @Query("UPDATE mind_nodes SET backgroundColor = :backgroundColor, textColor = :textColor, textSizeSp = :textSizeSp WHERE id = :nodeId")
    suspend fun updateStyle(nodeId: Long, backgroundColor: Int?, textColor: Int?, textSizeSp: Float?)

    @Query("UPDATE mind_nodes SET posX = NULL, posY = NULL WHERE mindMapId = :mindMapId")
    suspend fun resetPositions(mindMapId: Long)

    @Query("UPDATE mind_nodes SET isDeleted = 1, deleteTime = :deleteTime WHERE id IN (:nodeIds)")
    suspend fun softDelete(nodeIds: List<Long>, deleteTime: Long)

    @Query("UPDATE mind_nodes SET isDeleted = 0, deleteTime = NULL WHERE id IN (:nodeIds)")
    suspend fun restore(nodeIds: List<Long>)

    @Query("DELETE FROM mind_nodes WHERE id = :nodeId")
    suspend fun delete(nodeId: Long)
}
