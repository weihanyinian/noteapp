package com.example.mymind.ui.note

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.mymind.MyMindApplication
import com.example.mymind.data.local.entity.NoteEntity

class NoteEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MyMindApplication).noteRepository

    fun observeNote(noteId: Long): LiveData<NoteEntity?> = repository.observeNote(noteId)

    suspend fun upsert(
        noteId: Long?,
        title: String,
        content: String,
        inkJson: String?,
        attachmentUri: String?,
        attachmentMime: String?,
        attachmentPageIndex: Int,
        paperStyle: Int
    ): Long = repository.upsert(
        noteId = noteId,
        title = title,
        content = content,
        inkJson = inkJson,
        attachmentUri = attachmentUri,
        attachmentMime = attachmentMime,
        attachmentPageIndex = attachmentPageIndex,
        paperStyle = paperStyle
    )

    suspend fun trash(noteId: Long) = repository.trash(noteId)
}
