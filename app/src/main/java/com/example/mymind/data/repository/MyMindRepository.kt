package com.example.mymind.data.repository

import androidx.lifecycle.LiveData
import com.example.mymind.data.local.dao.MindMapDao
import com.example.mymind.data.local.dao.MindNodeDao
import com.example.mymind.data.local.dao.NoteDao
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.data.local.entity.MindNodeEntity
import com.example.mymind.data.local.entity.NoteEntity
// MindMapWithNodes 通过全限定名在 observeMindMapWithNodes 中直接引用
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MyMindRepository(
    private val noteDao: NoteDao,
    private val mindMapDao: MindMapDao,
    private val mindNodeDao: MindNodeDao
) {

    fun observeNotes(): LiveData<List<NoteEntity>> = noteDao.observeAll()

    fun observeNote(noteId: Long): LiveData<NoteEntity?> = noteDao.observeById(noteId)

    fun observeTrashedNotes(): LiveData<List<NoteEntity>> = noteDao.observeTrash()

    fun observeMindMaps(): LiveData<List<MindMapEntity>> = mindMapDao.observeAll()

    fun observeMindMapsSearch(query: String): LiveData<List<MindMapEntity>> = mindMapDao.observeSearch(query)

    fun observeTrashedMindMaps(): LiveData<List<MindMapEntity>> = mindMapDao.observeTrash()

    fun observeMindMapWithNodes(mindMapId: Long): LiveData<com.example.mymind.data.local.relation.MindMapWithNodes?> =
        mindMapDao.observeMindMapWithNodes(mindMapId)

    suspend fun saveNote(noteId: Long?, title: String, content: String) {
        val now = System.currentTimeMillis()
        val finalTitle = title.ifBlank { extractTitleFromContent(content) }
        val safeTitle = finalTitle.ifBlank { "未命名笔记" }
        val existing = noteId?.let { noteDao.getById(it) }

        if (existing == null) {
            noteDao.insert(
                NoteEntity(
                    title = safeTitle,
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            noteDao.update(
                existing.copy(
                    title = safeTitle,
                    content = content,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun trashNote(noteId: Long) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(noteId = noteId, deleteTime = now, updatedAt = now)
    }

    suspend fun restoreNote(noteId: Long) {
        noteDao.restore(noteId = noteId, updatedAt = System.currentTimeMillis())
    }

    suspend fun createDefaultMindMap(title: String = "新建思维导图"): Long {
        val now = System.currentTimeMillis()
        val rootTitle = "中心主题"
        val mindMapId = mindMapDao.insert(
            MindMapEntity(
                title = title,
                rootNodeTitle = rootTitle,
                createdAt = now,
                updatedAt = now
            )
        )

        val rootNodeId = mindNodeDao.insert(
            MindNodeEntity(
                mindMapId = mindMapId,
                parentNodeId = null,
                content = rootTitle,
                branchOrder = 0,
                depth = 0,
                isRoot = true
            )
        )

        mindNodeDao.insertAll(
            listOf(
                MindNodeEntity(
                    mindMapId = mindMapId,
                    parentNodeId = rootNodeId,
                    content = "分支主题 1",
                    branchOrder = 1,
                    depth = 1
                ),
                MindNodeEntity(
                    mindMapId = mindMapId,
                    parentNodeId = rootNodeId,
                    content = "分支主题 2",
                    branchOrder = 2,
                    depth = 1
                ),
                MindNodeEntity(
                    mindMapId = mindMapId,
                    parentNodeId = rootNodeId,
                    content = "分支主题 3",
                    branchOrder = 3,
                    depth = 1
                ),
                MindNodeEntity(
                    mindMapId = mindMapId,
                    parentNodeId = rootNodeId,
                    content = "分支主题 4",
                    branchOrder = 4,
                    depth = 1
                )
            )
        )

        return mindMapId
    }

    suspend fun updateMindMapTitle(mindMapId: Long, title: String) {
        val current = mindMapDao.getById(mindMapId) ?: return
        val safeTitle = title.ifBlank { current.title }

        mindMapDao.update(
            current.copy(
                title = safeTitle,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun trashMindMap(mindMapId: Long) {
        val now = System.currentTimeMillis()
        mindMapDao.softDelete(mindMapId = mindMapId, deleteTime = now, updatedAt = now)
    }

    suspend fun restoreMindMap(mindMapId: Long) {
        mindMapDao.restore(mindMapId = mindMapId, updatedAt = System.currentTimeMillis())
    }

    suspend fun purgeTrashedDataBefore(cutoffTime: Long) {
        noteDao.purgeDeletedBefore(cutoffTime)
        mindMapDao.purgeDeletedBefore(cutoffTime)
    }

    /** 永久删除单条笔记 */
    suspend fun permanentDeleteNote(noteId: Long) {
        noteDao.permanentDelete(noteId)
    }

    /** 永久删除单条思维导图 */
    suspend fun permanentDeleteMindMap(mindMapId: Long) {
        mindMapDao.permanentDelete(mindMapId)
    }

    /** 清空回收站所有笔记 */
    suspend fun permanentDeleteAllTrashedNotes() {
        noteDao.purgeAllDeleted()
    }

    /** 清空回收站所有思维导图 */
    suspend fun permanentDeleteAllTrashedMindMaps() {
        mindMapDao.purgeAllDeleted()
    }

    suspend fun addNode(mindMapId: Long, content: String = "新节点"): Long {
        val rootId = getRootNodeId(mindMapId) ?: return -1L
        return addChildNode(mindMapId = mindMapId, parentNodeId = rootId, content = content)
    }

    /** 将节点绑定到已有笔记 */
    suspend fun bindNoteToNode(nodeId: Long, noteId: Long?) {
        mindNodeDao.bindNote(nodeId, noteId)
        val mindMapId = mindNodeDao.getById(nodeId)?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, System.currentTimeMillis())
        }
    }

    /** 新建笔记并绑定到节点，返回笔记ID */
    suspend fun createNoteForNode(nodeId: Long, mindMapId: Long): Long {
        val node = mindNodeDao.getById(nodeId) ?: return -1L
        val now = System.currentTimeMillis()
        val noteTitle = "节点笔记：${node.content}"
        val newNoteId = noteDao.insert(
            NoteEntity(
                title = noteTitle,
                content = "",
                createdAt = now,
                updatedAt = now
            )
        )
        mindNodeDao.bindNote(nodeId, newNoteId)
        mindMapDao.touch(mindMapId, System.currentTimeMillis())
        return newNoteId
    }

    /** 编辑节点文字内容 */
    suspend fun editNodeContent(nodeId: Long, newContent: String) {
        val node = mindNodeDao.getById(nodeId) ?: return
        mindNodeDao.updateContent(nodeId, newContent)
        if (node.isRoot) {
            mindMapDao.updateRootNodeTitle(
                mindMapId = node.mindMapId,
                rootNodeTitle = newContent,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            mindMapDao.touch(node.mindMapId, System.currentTimeMillis())
        }
    }

    suspend fun getNode(nodeId: Long): MindNodeEntity? = mindNodeDao.getById(nodeId)

    suspend fun getRootNodeId(mindMapId: Long): Long? {
        return mindNodeDao.getAllByMindMapId(mindMapId).firstOrNull { it.isRoot }?.id
    }

    suspend fun addChildNode(
        mindMapId: Long,
        parentNodeId: Long,
        content: String = "新节点"
    ): Long {
        val allNodes = mindNodeDao.getAllByMindMapId(mindMapId)
        val parent = allNodes.firstOrNull { it.id == parentNodeId } ?: return -1L
        val siblings = allNodes.filter { it.parentNodeId == parentNodeId }
        val nextOrder = (siblings.maxOfOrNull { it.branchOrder } ?: 0) + 1
        val newId = mindNodeDao.insert(
            MindNodeEntity(
                mindMapId = mindMapId,
                parentNodeId = parentNodeId,
                content = content,
                branchOrder = nextOrder,
                depth = parent.depth + 1
            )
        )
        mindMapDao.touch(mindMapId, System.currentTimeMillis())
        return newId
    }

    suspend fun updateNodePosition(nodeId: Long, posX: Float?, posY: Float?) {
        mindNodeDao.updatePosition(nodeId = nodeId, posX = posX, posY = posY)
        val mindMapId = mindNodeDao.getById(nodeId)?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, System.currentTimeMillis())
        }
    }

    suspend fun setNodeCollapsed(nodeId: Long, isCollapsed: Boolean) {
        mindNodeDao.updateCollapsed(nodeId, isCollapsed)
        val mindMapId = mindNodeDao.getById(nodeId)?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, System.currentTimeMillis())
        }
    }

    suspend fun updateNodeStyle(nodeId: Long, backgroundColor: Int?, textColor: Int?, textSizeSp: Float?) {
        mindNodeDao.updateStyle(nodeId, backgroundColor, textColor, textSizeSp)
        val mindMapId = mindNodeDao.getById(nodeId)?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, System.currentTimeMillis())
        }
    }

    suspend fun resetMindMapLayout(mindMapId: Long) {
        mindNodeDao.resetPositions(mindMapId)
        mindMapDao.touch(mindMapId, System.currentTimeMillis())
    }

    suspend fun getSubtreeNodeIds(nodeId: Long): List<Long> {
        val node = mindNodeDao.getById(nodeId) ?: return emptyList()
        val nodes = mindNodeDao.getAllByMindMapId(node.mindMapId)
        val childrenByParent = nodes.groupBy { it.parentNodeId }
        val result = ArrayList<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(nodeId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            result.add(current)
            val children = childrenByParent[current].orEmpty()
            children.forEach { child -> stack.addLast(child.id) }
        }
        return result
    }

    suspend fun softDeleteNodes(nodeIds: List<Long>) {
        if (nodeIds.isEmpty()) return
        val now = System.currentTimeMillis()
        mindNodeDao.softDelete(nodeIds, now)
        val mindMapId = mindNodeDao.getById(nodeIds.first())?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, now)
        }
    }

    suspend fun restoreNodes(nodeIds: List<Long>) {
        if (nodeIds.isEmpty()) return
        val now = System.currentTimeMillis()
        mindNodeDao.restore(nodeIds)
        val mindMapId = mindNodeDao.getById(nodeIds.first())?.mindMapId
        if (mindMapId != null) {
            mindMapDao.touch(mindMapId, now)
        }
    }

    suspend fun getNodePositions(mindMapId: Long): Map<Long, Pair<Float?, Float?>> {
        return mindNodeDao.getByMindMapId(mindMapId).associate { it.id to (it.posX to it.posY) }
    }

    suspend fun updateNodePositions(mindMapId: Long, positions: Map<Long, Pair<Float?, Float?>>) {
        positions.forEach { (id, pos) ->
            mindNodeDao.updatePosition(id, pos.first, pos.second)
        }
        mindMapDao.touch(mindMapId, System.currentTimeMillis())
    }

    /**
     * 自动布局（为没有坐标的节点生成坐标并写回数据库）。
     *
     * 默认布局方向为“向右扩展”：将角度范围限制在右半平面 [-90°, +90°]。
     * 这样新建导图/新增节点时，结构优先从根节点往右展开，而不是往下堆叠。
     */
    suspend fun autoLayout(mindMapId: Long): Map<Long, Pair<Float?, Float?>> {
        val nodes = mindNodeDao.getByMindMapId(mindMapId)
        if (nodes.isEmpty()) return emptyMap()
        val root = nodes.firstOrNull { it.isRoot } ?: nodes.minByOrNull { it.depth } ?: return emptyMap()

        val nodesById = nodes.associateBy { it.id }
        val childrenByParent = nodes
            .filter { it.parentNodeId != null }
            .groupBy { it.parentNodeId!! }
            .mapValues { (_, list) -> list.sortedWith(compareBy<MindNodeEntity> { it.branchOrder }.thenBy { it.id }) }

        val leafCountById = HashMap<Long, Int>()
        fun leafCount(nodeId: Long): Int {
            leafCountById[nodeId]?.let { return it }
            val node = nodesById[nodeId] ?: return 1
            if (node.isCollapsed) {
                leafCountById[nodeId] = 1
                return 1
            }
            val children = childrenByParent[nodeId].orEmpty()
            if (children.isEmpty()) {
                leafCountById[nodeId] = 1
                return 1
            }
            val sum = children.sumOf { leafCount(it.id) }.coerceAtLeast(1)
            leafCountById[nodeId] = sum
            return sum
        }

        val angleById = HashMap<Long, Float>()
        angleById[root.id] = 0f

        fun assignAngles(parentId: Long, startAngle: Float, endAngle: Float) {
            val parent = nodesById[parentId] ?: return
            if (parent.isCollapsed) return
            val children = childrenByParent[parentId].orEmpty()
            if (children.isEmpty()) return
            val total = children.sumOf { leafCount(it.id) }.coerceAtLeast(1)
            var cursor = startAngle
            children.forEach { child ->
                val span = (endAngle - startAngle) * (leafCount(child.id).toFloat() / total.toFloat())
                val childStart = cursor
                val childEnd = cursor + span
                angleById[child.id] = (childStart + childEnd) / 2f
                cursor = childEnd
                assignAngles(child.id, childStart, childEnd)
            }
        }

        assignAngles(
            parentId = root.id,
            startAngle = (-PI / 2.0).toFloat(),
            endAngle = (PI / 2.0).toFloat()
        )

        val rootChildren = childrenByParent[root.id].orEmpty()
        val firstRadius = (320f + max(0, rootChildren.size - 4) * 28f).coerceAtMost(560f)
        val radiusStep = 240f

        val positions = LinkedHashMap<Long, Pair<Float, Float>>()
        positions[root.id] = 0f to 0f

        val sorted = nodes.sortedWith(compareBy<MindNodeEntity> { it.depth }.thenBy { it.branchOrder }.thenBy { it.id })
        sorted.forEach { node ->
            if (node.id == root.id) return@forEach
            val parentAngle = node.parentNodeId?.let { angleById[it] }
            val angle = angleById[node.id] ?: parentAngle ?: 0f
            val depth = max(1, node.depth)
            val radius = if (depth == 1) firstRadius else firstRadius + (depth - 1) * radiusStep
            val x = cos(angle.toDouble()).toFloat() * radius
            val y = sin(angle.toDouble()).toFloat() * radius
            positions[node.id] = x to y
        }

        positions.forEach { (id, pos) -> mindNodeDao.updatePosition(id, pos.first, pos.second) }
        mindMapDao.touch(mindMapId, System.currentTimeMillis())
        return positions.mapValues { it.value.first to it.value.second }
    }

    suspend fun seedIfEmpty() {
        if (noteDao.count() == 0) {
            noteDao.insert(
                NoteEntity(
                    title = "欢迎使用 MyMind",
                    content = "<h2>富文本笔记</h2><p>这里可以记录灵感、会议纪要与任务清单。</p><ul><li>支持标题</li><li>支持粗体与下划线</li><li>可继续接入更多编辑能力</li></ul>"
                )
            )
        }

        if (mindMapDao.count() == 0) {
            createDefaultMindMap("项目规划")
        }
    }

    private fun extractTitleFromContent(content: String): String {
        return content
            .replace("<[^>]*>".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(20)
    }
}
