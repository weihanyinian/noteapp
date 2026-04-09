package com.example.mymind.data.repository

import androidx.lifecycle.LiveData
import com.example.mymind.data.cloud.NoteCloudDataSource
import com.example.mymind.data.cloud.NoOpNoteCloudDataSource
import com.example.mymind.data.local.dao.NoteDao
import com.example.mymind.data.local.entity.NoteEntity

class NoteRepository(
    private val noteDao: NoteDao,
    private val cloudDataSource: NoteCloudDataSource = NoOpNoteCloudDataSource()
) {

    fun observeNotes(): LiveData<List<NoteEntity>> = noteDao.observeAll()

    fun observeSearch(query: String): LiveData<List<NoteEntity>> = noteDao.observeSearch(query)

    fun observeNote(noteId: Long): LiveData<NoteEntity?> = noteDao.observeById(noteId)

    suspend fun getNote(noteId: Long): NoteEntity? = noteDao.getById(noteId)

    suspend fun upsert(
        noteId: Long?,
        title: String,
        content: String,
        inkJson: String? = null,
        attachmentUri: String? = null,
        attachmentMime: String? = null,
        attachmentPageIndex: Int = 0
    ): Long {
        val now = System.currentTimeMillis()
        val existing = noteId?.let { noteDao.getById(it) }
        val safeTitle = title.ifBlank { extractTitleFromContent(content).ifBlank { "未命名笔记" } }

        return if (existing == null) {
            val newId = noteDao.insert(
                NoteEntity(
                    title = safeTitle,
                    content = content,
                    inkJson = inkJson,
                    attachmentUri = attachmentUri,
                    attachmentMime = attachmentMime,
                    attachmentPageIndex = attachmentPageIndex,
                    createdAt = now,
                    updatedAt = now
                )
            )
            cloudDataSource.upsert(
                NoteEntity(
                    id = newId,
                    title = safeTitle,
                    content = content,
                    inkJson = inkJson,
                    attachmentUri = attachmentUri,
                    attachmentMime = attachmentMime,
                    attachmentPageIndex = attachmentPageIndex,
                    createdAt = now,
                    updatedAt = now
                )
            )
            newId
        } else {
            val updated = existing.copy(
                title = safeTitle,
                content = content,
                inkJson = inkJson ?: existing.inkJson,
                attachmentUri = attachmentUri ?: existing.attachmentUri,
                attachmentMime = attachmentMime ?: existing.attachmentMime,
                attachmentPageIndex = if (attachmentUri != null) attachmentPageIndex else existing.attachmentPageIndex,
                updatedAt = now
            )
            noteDao.update(updated)
            cloudDataSource.upsert(updated)
            existing.id
        }
    }

    suspend fun trash(noteId: Long) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(noteId = noteId, deleteTime = now, updatedAt = now)
    }

    suspend fun restore(noteId: Long) {
        noteDao.restore(noteId = noteId, updatedAt = System.currentTimeMillis())
    }

    private fun extractTitleFromContent(content: String): String {
        return content
            .replace("<[^>]*>".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(20)
    }
}
