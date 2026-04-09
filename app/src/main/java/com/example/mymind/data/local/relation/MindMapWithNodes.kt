package com.example.mymind.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.data.local.entity.MindNodeEntity

data class MindMapWithNodes(
    @Embedded
    val mindMap: MindMapEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mindMapId"
    )
    val nodes: List<MindNodeEntity>
)
