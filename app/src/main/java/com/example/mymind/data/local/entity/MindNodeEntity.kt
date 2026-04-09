package com.example.mymind.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mind_nodes",
    foreignKeys = [
        ForeignKey(
            entity = MindMapEntity::class,
            parentColumns = ["id"],
            childColumns = ["mindMapId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mindMapId"), Index("parentNodeId"), Index("noteId")]
)
data class MindNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mindMapId: Long,
    val parentNodeId: Long?,
    val content: String,
    val branchOrder: Int,
    val depth: Int,
    val isRoot: Boolean = false,
    val isDeleted: Boolean = false,
    val deleteTime: Long? = null,
    /** 绑定笔记的 ID，null 表示未绑定 */
    val noteId: Long? = null,
    val posX: Float? = null,
    val posY: Float? = null,
    val isCollapsed: Boolean = false,
    val backgroundColor: Int? = null,
    val textColor: Int? = null,
    val textSizeSp: Float? = null
)
