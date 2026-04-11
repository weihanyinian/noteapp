package com.example.mymind.ui.mindmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.mymind.MyMindApplication
import com.example.mymind.data.local.entity.MindNodeEntity
import kotlinx.coroutines.launch

class MindMapDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as MyMindApplication).repository
    private val mindMapIdLiveData = MutableLiveData<Long>()
    private var currentMindMapId: Long? = null

    val mindMapWithNodes = mindMapIdLiveData.switchMap { mindMapId: Long ->
        repository.observeMindMapWithNodes(mindMapId)
    }

    // 单次事件：打开笔记编辑器
    private val _openNoteEvent = MutableLiveData<Long?>()
    val openNoteEvent: LiveData<Long?> get() = _openNoteEvent

    private val _nodeAddedEvent = MutableLiveData<Long?>()
    val nodeAddedEvent: LiveData<Long?> get() = _nodeAddedEvent

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> get() = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> get() = _canRedo

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    fun loadMindMap(mindMapId: Long) {
        if (currentMindMapId == mindMapId) return
        currentMindMapId = mindMapId
        mindMapIdLiveData.value = mindMapId
    }

    /** 将节点绑定到已有笔记 */
    fun bindNoteToNode(nodeId: Long, noteId: Long?) {
        viewModelScope.launch {
            val old = repository.getNode(nodeId)?.noteId
            if (old == noteId) return@launch
            repository.bindNoteToNode(nodeId, noteId)
            push(BindNoteCommand(nodeId = nodeId, fromNoteId = old, toNoteId = noteId))
        }
    }

    /** 创建新笔记并绑定到节点，返回新笔记ID（通过 openNoteEvent 触发） */
    fun createNoteAndBind(nodeId: Long, mindMapId: Long) {
        viewModelScope.launch {
            val old = repository.getNode(nodeId)?.noteId
            val noteId = repository.createNoteForNode(nodeId, mindMapId)
            _openNoteEvent.value = noteId
            push(BindNoteCommand(nodeId = nodeId, fromNoteId = old, toNoteId = noteId))
        }
    }

    fun consumeNodeAddedEvent() {
        _nodeAddedEvent.value = null
    }

    fun addChildNode(parentNodeId: Long?) {
        val mindMapId = currentMindMapId ?: return
        viewModelScope.launch {
            val actualParentId = parentNodeId ?: repository.getRootNodeId(mindMapId) ?: return@launch
            val newNodeId = repository.addChildNode(mindMapId = mindMapId, parentNodeId = actualParentId)
            push(AddNodeCommand(nodeId = newNodeId))
            _nodeAddedEvent.value = newNodeId
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val command = undoStack.removeLast()
        viewModelScope.launch {
            command.undo()
            redoStack.addLast(command)
            updateUndoRedoState()
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val command = redoStack.removeLast()
        viewModelScope.launch {
            command.redo()
            undoStack.addLast(command)
            updateUndoRedoState()
        }
    }

    fun renameNode(nodeId: Long, newContent: String) {
        viewModelScope.launch {
            val node = repository.getNode(nodeId) ?: return@launch
            val trimmed = newContent.trim()
            if (trimmed.isBlank() || trimmed == node.content) return@launch
            repository.editNodeContent(nodeId, trimmed)
            push(RenameCommand(nodeId = nodeId, from = node.content, to = trimmed))
        }
    }

    fun deleteSubtree(nodeId: Long) {
        viewModelScope.launch {
            val mindMapId = currentMindMapId ?: return@launch
            val rootId = repository.getRootNodeId(mindMapId) ?: return@launch
            if (nodeId == rootId) return@launch

            val ids = repository.getSubtreeNodeIds(nodeId)
            if (ids.isEmpty()) return@launch
            repository.softDeleteNodes(ids)
            push(DeleteSubtreeCommand(nodeIds = ids))
        }
    }

    fun updateNodePosition(nodeId: Long, fromX: Float?, fromY: Float?, toX: Float?, toY: Float?) {
        if (fromX == toX && fromY == toY) return
        viewModelScope.launch {
            repository.updateNodePosition(nodeId, toX, toY)
            push(MoveCommand(nodeId = nodeId, fromX = fromX, fromY = fromY, toX = toX, toY = toY))
        }
    }

    fun toggleCollapse(nodeId: Long) {
        viewModelScope.launch {
            val node = repository.getNode(nodeId) ?: return@launch
            val to = !node.isCollapsed
            repository.setNodeCollapsed(nodeId, to)
            push(CollapseCommand(nodeId = nodeId, from = node.isCollapsed, to = to))
        }
    }

    fun applyStylePreset(nodeId: Long, backgroundColor: Int?, textColor: Int?, textSizeSp: Float?) {
        viewModelScope.launch {
            val node = repository.getNode(nodeId) ?: return@launch
            val from = NodeStyle(
                backgroundColor = node.backgroundColor,
                textColor = node.textColor,
                textSizeSp = node.textSizeSp
            )
            val to = NodeStyle(
                backgroundColor = backgroundColor,
                textColor = textColor,
                textSizeSp = textSizeSp
            )
            if (from == to) return@launch
            repository.updateNodeStyle(nodeId, backgroundColor, textColor, textSizeSp)
            push(StyleCommand(nodeId = nodeId, from = from, to = to))
        }
    }

    fun autoLayout() {
        val mindMapId = currentMindMapId ?: return
        viewModelScope.launch {
            val oldPositions = repository.getNodePositions(mindMapId)
            val newPositions = repository.autoLayout(mindMapId)
            push(AutoLayoutCommand(mindMapId = mindMapId, fromPositions = oldPositions, toPositions = newPositions))
        }
    }

    fun trashMindMap(mindMapId: Long) {
        viewModelScope.launch {
            repository.trashMindMap(mindMapId)
        }
    }

    fun saveTitle(title: String) {
        val mindMapId = currentMindMapId ?: return
        viewModelScope.launch {
            repository.updateMindMapTitle(mindMapId, title)
        }
    }

    fun consumeOpenNoteEvent() { _openNoteEvent.value = null }

    private fun push(command: Command) {
        undoStack.addLast(command)
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private data class NodeStyle(
        val backgroundColor: Int?,
        val textColor: Int?,
        val textSizeSp: Float?
    )

    private interface Command {
        suspend fun undo()
        suspend fun redo()
    }

    private inner class AddNodeCommand(
        private val nodeId: Long
    ) : Command {
        private var deletedIds: List<Long> = listOf(nodeId)
        override suspend fun undo() {
            deletedIds = repository.getSubtreeNodeIds(nodeId).ifEmpty { listOf(nodeId) }
            repository.softDeleteNodes(deletedIds)
        }
        override suspend fun redo() {
            repository.restoreNodes(deletedIds)
        }
    }

    private inner class DeleteSubtreeCommand(
        private val nodeIds: List<Long>
    ) : Command {
        override suspend fun undo() {
            repository.restoreNodes(nodeIds)
        }
        override suspend fun redo() {
            repository.softDeleteNodes(nodeIds)
        }
    }

    private inner class RenameCommand(
        private val nodeId: Long,
        private val from: String,
        private val to: String
    ) : Command {
        override suspend fun undo() { repository.editNodeContent(nodeId, from) }
        override suspend fun redo() { repository.editNodeContent(nodeId, to) }
    }

    private inner class MoveCommand(
        private val nodeId: Long,
        private val fromX: Float?,
        private val fromY: Float?,
        private val toX: Float?,
        private val toY: Float?
    ) : Command {
        override suspend fun undo() { repository.updateNodePosition(nodeId, fromX, fromY) }
        override suspend fun redo() { repository.updateNodePosition(nodeId, toX, toY) }
    }

    private inner class BindNoteCommand(
        private val nodeId: Long,
        private val fromNoteId: Long?,
        private val toNoteId: Long?
    ) : Command {
        override suspend fun undo() { repository.bindNoteToNode(nodeId, fromNoteId) }
        override suspend fun redo() { repository.bindNoteToNode(nodeId, toNoteId) }
    }

    private inner class CollapseCommand(
        private val nodeId: Long,
        private val from: Boolean,
        private val to: Boolean
    ) : Command {
        override suspend fun undo() { repository.setNodeCollapsed(nodeId, from) }
        override suspend fun redo() { repository.setNodeCollapsed(nodeId, to) }
    }

    private inner class StyleCommand(
        private val nodeId: Long,
        private val from: NodeStyle,
        private val to: NodeStyle
    ) : Command {
        override suspend fun undo() { repository.updateNodeStyle(nodeId, from.backgroundColor, from.textColor, from.textSizeSp) }
        override suspend fun redo() { repository.updateNodeStyle(nodeId, to.backgroundColor, to.textColor, to.textSizeSp) }
    }

    private inner class AutoLayoutCommand(
        private val mindMapId: Long,
        private val fromPositions: Map<Long, Pair<Float?, Float?>>,
        private val toPositions: Map<Long, Pair<Float?, Float?>>
    ) : Command {
        override suspend fun undo() { repository.updateNodePositions(mindMapId, fromPositions) }
        override suspend fun redo() { repository.updateNodePositions(mindMapId, toPositions) }
    }
}
