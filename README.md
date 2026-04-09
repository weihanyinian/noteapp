# MyMind / NoteApp (Android)

一个用于学习与知识管理的 Android 应用，包含：
- 笔记：手写为主（支持手指与触控笔），可导入 PDF/图片并在其上标注
- 思维导图：放射状布局、贝塞尔曲线连线、节点编辑/折叠/拖拽、节点与笔记绑定
- 回收站：笔记/导图可移入回收站并恢复

## 开发环境
- Android Studio（推荐最新版稳定版）
- JDK 17

## 运行
1. 使用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. 运行 `app` 配置到设备或模拟器

命令行构建：
```bash
./gradlew :app:assembleDebug
```

## 目录结构
- `app/src/main/java/.../ui/note`：笔记编辑与手写
- `app/src/main/java/.../ui/mindmap`：思维导图
- `app/src/main/java/.../data`：Room 数据库与仓库层
