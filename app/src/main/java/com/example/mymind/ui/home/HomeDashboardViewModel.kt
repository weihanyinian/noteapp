package com.example.mymind.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mymind.MyMindApplication
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.data.local.entity.NoteEntity
import kotlinx.coroutines.launch

class HomeDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val mindMapRepository = (application as MyMindApplication).repository
    private val noteRepository = (application as MyMindApplication).noteRepository

    private val queryLiveData = MutableLiveData("")

    private val mindMapsAll = mindMapRepository.observeMindMaps()
    private val notesAll = noteRepository.observeNotes()

    private val mindMapsSearch = MediatorLiveData<List<MindMapEntity>>().apply {
        addSource(queryLiveData) { q ->
            val trimmed = q.trim()
            if (trimmed.isBlank()) return@addSource
            val src = mindMapRepository.observeMindMapsSearch(trimmed)
            addSource(src) { value = it }
        }
    }

    private val notesSearch = MediatorLiveData<List<NoteEntity>>().apply {
        addSource(queryLiveData) { q ->
            val trimmed = q.trim()
            if (trimmed.isBlank()) return@addSource
            val src = noteRepository.observeSearch(trimmed)
            addSource(src) { value = it }
        }
    }

    val recentMindMaps: LiveData<List<MindMapEntity>> = MediatorLiveData<List<MindMapEntity>>().apply {
        fun compute() {
            val q = queryLiveData.value.orEmpty().trim()
            value = if (q.isBlank()) {
                (mindMapsAll.value ?: emptyList()).take(4)
            } else {
                (mindMapsSearch.value ?: emptyList()).take(12)
            }
        }
        addSource(mindMapsAll) { compute() }
        addSource(mindMapsSearch) { compute() }
        addSource(queryLiveData) { compute() }
    }

    val recentNotes: LiveData<List<NoteEntity>> = MediatorLiveData<List<NoteEntity>>().apply {
        fun compute() {
            val q = queryLiveData.value.orEmpty().trim()
            value = if (q.isBlank()) {
                (notesAll.value ?: emptyList()).take(4)
            } else {
                (notesSearch.value ?: emptyList()).take(12)
            }
        }
        addSource(notesAll) { compute() }
        addSource(notesSearch) { compute() }
        addSource(queryLiveData) { compute() }
    }

    private val _openMindMapEvent = MutableLiveData<Long?>()
    val openMindMapEvent: LiveData<Long?> = _openMindMapEvent

    fun setQuery(query: String) {
        queryLiveData.value = query
    }

    fun createMindMap() {
        viewModelScope.launch {
            val id = mindMapRepository.createDefaultMindMap()
            _openMindMapEvent.value = id
        }
    }

    fun consumeOpenMindMapEvent() {
        _openMindMapEvent.value = null
    }
}
