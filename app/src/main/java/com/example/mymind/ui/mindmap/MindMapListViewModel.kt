package com.example.mymind.ui.mindmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.mymind.MyMindApplication
import com.example.mymind.data.local.entity.MindMapEntity
import kotlinx.coroutines.launch

class MindMapListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MyMindApplication).repository
    private val _openMindMapEvent = MutableLiveData<Long?>()
    private val queryLiveData = MutableLiveData("")

    val mindMaps: LiveData<List<MindMapEntity>> = queryLiveData.switchMap { query: String ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            repository.observeMindMaps()
        } else {
            repository.observeMindMapsSearch(trimmed)
        }
    }
    val openMindMapEvent: LiveData<Long?> = _openMindMapEvent

    fun setQuery(query: String) {
        queryLiveData.value = query
    }

    fun createMindMap() {
        viewModelScope.launch {
            val mapId = repository.createDefaultMindMap()
            _openMindMapEvent.value = mapId
        }
    }

    fun trashMindMap(mindMapId: Long) {
        viewModelScope.launch {
            repository.trashMindMap(mindMapId)
        }
    }

    fun restoreMindMap(mindMapId: Long) {
        viewModelScope.launch {
            repository.restoreMindMap(mindMapId)
        }
    }

    fun consumeOpenMindMapEvent() {
        _openMindMapEvent.value = null
    }
}
