package com.example.mymind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val inkJson: String? = null,
    val attachmentUri: String? = null,
    val attachmentMime: String? = null,
    val attachmentPageIndex: Int = 0,
    val isDeleted: Boolean = false,
    val deleteTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
