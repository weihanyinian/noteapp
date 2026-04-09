package com.example.mymind.ui.trash

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

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MyMindApplication).repository

    private val trashedNotes = repository.observeTrashedNotes()
    private val trashedMindMaps = repository.observeTrashedMindMaps()

    // 当前 Tab：0=笔记，1=思维导图
    private val _currentTab = MutableLiveData(0)

    val trashItems: LiveData<List<TrashItem>> = MediatorLiveData<List<TrashItem>>().apply {
        addSource(trashedNotes) { notes ->
            if (_currentTab.value == 0) value = notes.toNoteTrashItems()
        }
        addSource(trashedMindMaps) { maps ->
            if (_currentTab.value == 1) value = maps.toMindMapTrashItems()
        }
        addSource(_currentTab) { tab ->
            value = when (tab) {
                0 -> trashedNotes.value?.toNoteTrashItems() ?: emptyList()
                else -> trashedMindMaps.value?.toMindMapTrashItems() ?: emptyList()
            }
        }
    }

    fun setTab(tab: Int) { _currentTab.value = tab }

    fun restore(item: TrashItem) {
        viewModelScope.launch {
            when (item.type) {
                TrashItem.Type.NOTE -> repository.restoreNote(item.id)
                TrashItem.Type.MIND_MAP -> repository.restoreMindMap(item.id)
            }
        }
    }

    fun permanentDelete(item: TrashItem) {
        viewModelScope.launch {
            when (item.type) {
                TrashItem.Type.NOTE -> repository.permanentDeleteNote(item.id)
                TrashItem.Type.MIND_MAP -> repository.permanentDeleteMindMap(item.id)
            }
        }
    }

    fun emptyCurrentTab() {
        viewModelScope.launch {
            when (_currentTab.value) {
                0 -> repository.permanentDeleteAllTrashedNotes()
                else -> repository.permanentDeleteAllTrashedMindMaps()
            }
        }
    }

    private fun List<NoteEntity>.toNoteTrashItems(): List<TrashItem> = map {
        TrashItem(id = it.id, title = it.title, deleteTime = it.deleteTime, type = TrashItem.Type.NOTE)
    }

    private fun List<MindMapEntity>.toMindMapTrashItems(): List<TrashItem> = map {
        TrashItem(id = it.id, title = it.title, deleteTime = it.deleteTime, type = TrashItem.Type.MIND_MAP)
    }
}
