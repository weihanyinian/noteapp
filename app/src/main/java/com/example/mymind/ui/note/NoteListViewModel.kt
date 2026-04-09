package com.example.mymind.ui.note

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.mymind.MyMindApplication
import com.example.mymind.data.local.entity.NoteEntity
import kotlinx.coroutines.launch

class NoteListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MyMindApplication).noteRepository
    private val queryLiveData = MutableLiveData("")

    val notes: LiveData<List<NoteEntity>> = queryLiveData.switchMap { query: String ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            repository.observeNotes()
        } else {
            repository.observeSearch(trimmed)
        }
    }

    fun setQuery(query: String) {
        queryLiveData.value = query
    }

    fun trashNote(noteId: Long) {
        viewModelScope.launch {
            repository.trash(noteId)
        }
    }

    fun restoreNote(noteId: Long) {
        viewModelScope.launch {
            repository.restore(noteId)
        }
    }
}
