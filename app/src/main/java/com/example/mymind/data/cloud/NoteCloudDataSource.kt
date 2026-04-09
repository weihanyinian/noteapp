package com.example.mymind.data.cloud

import com.example.mymind.data.local.entity.NoteEntity

interface NoteCloudDataSource {
    suspend fun upsert(note: NoteEntity)
}

class NoOpNoteCloudDataSource : NoteCloudDataSource {
    override suspend fun upsert(note: NoteEntity) = Unit
}

