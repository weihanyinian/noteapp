package com.example.mymind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mymind.data.local.dao.MindMapDao
import com.example.mymind.data.local.dao.MindNodeDao
import com.example.mymind.data.local.dao.NoteDao
import com.example.mymind.data.local.entity.MindMapEntity
import com.example.mymind.data.local.entity.MindNodeEntity
import com.example.mymind.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class, MindMapEntity::class, MindNodeEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun mindMapDao(): MindMapDao
    abstract fun mindNodeDao(): MindNodeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 → v2：notes 表新增 isDeleted + deleteTime；
         *           mind_maps 表新增 isDeleted + deleteTime
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN deleteTime INTEGER")
                db.execSQL("ALTER TABLE mind_maps ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mind_maps ADD COLUMN deleteTime INTEGER")
            }
        }

        /**
         * v2 → v3：mind_nodes 表新增 noteId 字段（绑定笔记）
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN noteId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mind_nodes_noteId ON mind_nodes(noteId)")
            }
        }

        /**
         * v3 → v4：mind_nodes 表新增 posX + posY 字段（节点拖拽布局）
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN posX REAL")
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN posY REAL")
            }
        }

        /**
         * v4 → v5：mind_nodes 表新增折叠与样式字段
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN isCollapsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN backgroundColor INTEGER")
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN textColor INTEGER")
                db.execSQL("ALTER TABLE mind_nodes ADD COLUMN textSizeSp REAL")
            }
        }

        /**
         * v5 → v6：notes 表新增手写与附件字段
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN inkJson TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN attachmentUri TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN attachmentMime TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN attachmentPageIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymind_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // 兜底：如果设备数据库版本极旧（如开发调试阶段），允许破坏性迁移
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
