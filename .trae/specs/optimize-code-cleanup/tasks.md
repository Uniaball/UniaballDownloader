# Tasks

- [x] Task 1: 在 `ui/` 包下新增 `ScreenTransitions.kt`，提供共享 `screenTransitionSpec()` 函数
  - [ ] SubTask 1.1: 创建 `app/src/main/java/com/uniaball/downloader/ui/ScreenTransitions.kt`，定义返回 `AnimatedContentTransitionSpec<...>` 的顶层函数，内部实现 `fadeIn(tween(220)) + slideInVertically(tween(220), initialOffsetY = { it/8 }) togetherWith fadeOut(tween(180)) + slideOutVertically(tween(180), targetOffsetY = { -it/8 })`
  - [ ] SubTask 1.2: 在 `DesktopGluesScreen.kt` / `OpenJdkScreen.kt` / `MobileGlScreen.kt` 的 `AnimatedContent.transitionSpec` 中改用 `screenTransitionSpec()`，删除三处内联定义

- [x] Task 2: 统一 `UniaballRepository` 中的 403 退避处理
  - [ ] SubTask 2.1: 在 `UniaballRepository` 中新增私有挂起辅助 `private suspend fun <T> withRateLimit(block: () -> T): T`，内部依次执行 `checkRateLimit()`、`try { block() } catch (e: retrofit2.HttpException) { if (e.code() == 403) { markRateLimited(); throw RateLimitedException(...) } else throw e }`
  - [ ] SubTask 2.2: 把 `listDesktopGluesReleases`、`listArtifactsForRun`、`listAllRunsCached`、`listOpenJdkRuns`、`listMobileGlRuns` 五个方法的 `checkRateLimit` + `try/catch 403` 样板替换为 `withRateLimit { ... }` 调用

- [x] Task 3: 抽取 OpenJDK 版本关键字过滤逻辑
  - [ ] SubTask 3.1: 在 `UniaballRepository` 中新增私有函数 `private fun jdkKeywords(version: Int): List<String> = listOf("jdk$version", "openjdk$version", "java$version")`
  - [ ] SubTask 3.2: `listOpenJdkRuns` 内部 `lowerKeywords` 改为调用 `jdkKeywords(jdkVersion)`
  - [ ] SubTask 3.3: `OpenJdkViewModel.lookupCachedItems` 内部 `lowerKeywords` 改为调用 `UniaballRepository` 暴露的关键字函数（需把 `jdkKeywords` 改为 `internal` 可见性以供 ViewModel 复用）

- [x] Task 4: 重构 `OpenJdkViewModel.lookupCachedItems` 消除 4 段重复代码
  - [ ] SubTask 4.1: 在 `OpenJdkViewModel` 中新增私有挂起辅助 `private suspend fun buildItemsFromPage(page: WorkflowRunPage?): List<OpenJdkBuildItem>`，内部：null/空 → 返回 emptyList；否则按 `jdkKeywords` 过滤 → take(5) → 对每个 run 查 artifacts（同步 mapNotNull，与原实现一致） → flatten
  - [ ] SubTask 4.2: `lookupCachedItems` 改为依次对 `getCachedAllRuns()`、`loadAllRunsFromDisk()`、`getCachedOpenJdkRuns(version)`、`loadOpenJdkRunsFromDisk(version)` 调用 `buildItemsFromPage`，第一个非空即返回；逻辑与原实现等价
  - [ ] SubTask 4.3: 验证原实现中"allRuns 内存/磁盘"分支的过滤关键字为 `jdkKeywords(version)`，"version 内存/磁盘"分支不过滤（直接 take(5)）——重构后 `buildItemsFromPage` 需要接收 `applyVersionFilter: Boolean` 参数以保留这一差异

- [x] Task 5: 统一 `collectAsState` 用法
  - [ ] SubTask 5.1: `MobileGlScreen.kt` 中 `viewModel.uiState.collectAsState()` 与 `viewModel.isRefreshing.collectAsState()` 改为 `collectAsStateWithLifecycle()`，并补充 `import androidx.lifecycle.compose.collectAsStateWithLifecycle`、移除不再使用的 `import androidx.compose.runtime.collectAsState`
  - [ ] SubTask 5.2: `SettingsScreen.kt` 中 `UniaballRepository.isMirrorEnabled.collectAsState()` 改为 `collectAsStateWithLifecycle()`，并补充 import、移除 `collectAsState` import

- [x] Task 6: 清理 `UniaballRepository.kt` 全限定类名
  - [ ] SubTask 6.1: 在文件顶部 import `android.content.Context` 与 `android.content.SharedPreferences`
  - [ ] SubTask 6.2: 删除文件内 6 处 `android.content.` 前缀（`appContext: android.content.Context?`、`init(context: android.content.Context)`、`getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)` 两处、`prefs(): android.content.SharedPreferences?`）

- [x] Task 7: 删除死代码
  - [ ] SubTask 7.1: 从 `UniaballRepository.kt` 删除 `listOpenJdkWorkflowRuns(jdkVersion: Int)`、`listMobileGlWorkflowRuns()`、常量 `OPENJDK_WORKFLOW_ID` 与 `MOBILEGL_WORKFLOW_ID`
  - [ ] SubTask 7.2: 从 `GitHubApi.kt` 删除 `listWorkflowRuns` 接口方法及其 `@Query("branch") branch: String?` 等参数
  - [ ] SubTask 7.3: 从 `GitHubModels.kt` 删除 `Workflow` 与 `WorkflowPage` 数据类

- [x] Task 8: 构建验证（沙箱无 Android SDK / 外网，改为全工程 grep 验证）
  - [x] SubTask 8.1: 沙箱环境 `./gradlew assembleDebug` 因无 Android SDK 且无法下载 AGP 插件而无法执行；改为通过 Grep 全工程扫描确认被删符号（`listOpenJdkWorkflowRuns` / `listMobileGlWorkflowRuns` / `listWorkflowRuns` / `OPENJDK_WORKFLOW_ID` / `MOBILEGL_WORKFLOW_ID` / `Workflow` / `WorkflowPage`）无任何残留引用，新增符号（`withRateLimit` / `jdkKeywords` / `screenTransitionSpec` / `buildItemsFromPage`）均定义并被正确调用
  - [x] SubTask 8.2: Grep 确认所有屏幕已迁移到 `collectAsStateWithLifecycle()`，无残留 `collectAsState()`；`UniaballRepository.kt` 内无 `android.content.` FQN 残留（仅 import 语句中存在）

# Task Dependencies
- Task 1 独立，可与其他任务并行
- Task 2 独立，可与其他任务并行
- Task 3 是 Task 4 的前置（`lookupCachedItems` 重构依赖 `jdkKeywords` 抽取）
- Task 4 依赖 Task 3
- Task 5、6、7 互相独立，可并行
- Task 8 依赖所有其他任务完成
