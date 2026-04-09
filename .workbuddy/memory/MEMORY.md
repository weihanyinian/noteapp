# MEMORY.md - MyMind 项目长期记忆

## 项目基本信息
- **项目名称：** MyMind（思维导图 + 富文本笔记综合 App）
- **项目路径：** `e:\MyMind`
- **包名：** `com.example.mymind`
- **语言：** Kotlin
- **架构：** MVVM + Room + LiveData
- **最低 SDK：** 26，目标 SDK：34
- **构建系统：** Gradle KTS

## 数据库
- **当前版本：** 3
- **Migration 路径：** 1→2（新增 isDeleted/deleteTime），2→3（新增 noteId to mind_nodes）
- **表：** notes, mind_maps, mind_nodes

## 已完成功能
1. 富文本笔记（RichEditor + 自动保存 + 搜索 + 滑动删除到回收站）
2. 思维导图（节点 CRUD + 绑定笔记 + 双向联动）
3. AI 辅助生成（Retrofit + DeepSeek API，AiConfig 持久化配置）
4. 回收站（15 天暂存 + WorkManager 自动清理）
5. 主界面菜单（回收站入口 + AI 设置）
6. 蓝色主题（Material 3，#1565C0）
7. 平板适配（sw600dp，NavigationRail + 笔记编辑器居中 80%）

## 依赖亮点
- `jp.wasabeef:richeditor-android:1.2.2` — 富文本编辑器
- `com.google.android.material:material:1.11.0` — 含 NavigationRailView
- WorkManager 2.9.0，Room 2.6.1，Retrofit 2.9.0

## 用户偏好
- 语言：中文
- 主色调：蓝色系 #2196F3 / #1565C0
- 功能原则：简洁实用，保留核心操作
