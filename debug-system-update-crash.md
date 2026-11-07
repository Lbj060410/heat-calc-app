# Debug Session: system-update-crash

## Symptom
- 现象：小米红米 K80 更新系统到 3.0.309 后，APP 打不开，直接闪退
- 发生时间：2026-11-06 中午系统更新后
- 设备：Redmi K80，HyperOS 3.0.309（推测是 Android 15/Tiramisu 或新的 MIUI 底层）
- 当前 APP 版本：待确认（可能是本地 debug 包）

## Environment
- PROJECT_ROOT: e:\FOOD
- DEBUG_EVIDENCE_DIR: c:\Users\qq332\AppData\Local\Temp\trae-debug\system-update-crash

## Hypotheses

| # | 假设 | 观察点 | 可证伪 |
|---|------|--------|--------|
| H1 | 数据库迁移失败：AppDatabase version 6→7 加列，新系统下 SQLite 行为变了或 Room 迁移有 bug | `FATAL EXCEPTION` 栈里有 `room` / `sqlite` / `database` 关键词 | 看 logcat 的 FATAL EXCEPTION |
| H2 | FileProvider authority 冲突：Android 15 更严格的 content provider authority 校验 | 栈里有 `FileProvider` / `androidx.core.content.FileProvider` / `SecurityException` | 看 logcat 是否有 authority 相关报错 |
| H3 | DataStore 或 SharedPreferences 文件权限变化：系统更新后私有目录权限重置，读 DataStore 文件失败 | 栈里有 `DataStore` / `SharedPreferences` / `FileNotFoundException` + `EACCES` 或 `ENOENT` | 看 logcat 权限/文件相关 |
| H4 | 开机启动页/引导页 Activity 配置问题：系统更新后 Intent 过滤器或 exported 属性不合规 | 栈里有 `ActivityNotFoundException` / `ExportedActivity` / `IllegalStateException` | 看 Activity 相关异常 |
| H5 | 系统更新过程中 APP 数据目录被清空或部分文件损坏，AppCompatActivity 的某个初始化流程 NPE | 栈里有 `NullPointerException` 在 `onCreate` 或 DI/Hilt 初始化链上 | 看 NPE 的具体类名 |

## Evidence Plan
1. 先抓一次启动的 logcat（重点看 ActivityManager、AndroidRuntime、Crash 关键词）
2. 根据栈确定是哪个假设
3. 做最小修复
4. 本地 build 验证
