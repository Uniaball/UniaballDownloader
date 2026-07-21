# Checklist

- [x] `ScreenTransitions.kt` 已创建并导出 `screenTransitionSpec()`，三处屏幕已改用共享实现
- [x] `UniaballRepository` 五个 API 方法均通过 `withRateLimit { ... }` 调用，文件内不再有 `checkRateLimit()` + `try/catch HttpException 403` 的重复样板
- [x] `UniaballRepository.jdkKeywords(version)` 已定义并被 `listOpenJdkRuns` 与 `OpenJdkViewModel.lookupCachedItems` 共同复用
- [x] `OpenJdkViewModel.lookupCachedItems` 通过 `buildItemsFromPage` 辅助函数处理 4 个缓存源，文件内不再有 4 段近乎一致的"过滤 → take(5) → 查 artifacts → flatten"重复代码
- [x] `buildItemsFromPage` 保留原实现差异：allRuns 分支按版本关键字过滤，version 分支不过滤直接 take(5)
- [x] `MobileGlScreen` 与 `SettingsScreen` 已改用 `collectAsStateWithLifecycle()`，无残留 `collectAsState()` 调用
- [x] `UniaballRepository.kt` 顶部已 import `Context` 与 `SharedPreferences`，文件内不再出现 `android.content.` 全限定前缀
- [x] `UniaballRepository.listOpenJdkWorkflowRuns` / `listMobileGlWorkflowRuns` 已删除
- [x] `UniaballRepository` 常量 `OPENJDK_WORKFLOW_ID` / `MOBILEGL_WORKFLOW_ID` 已删除
- [x] `GitHubApi.listWorkflowRuns` 已删除
- [x] `GitHubModels.Workflow` / `GitHubModels.WorkflowPage` 已删除
- [x] `./gradlew assembleDebug --no-daemon` 编译通过，无未解析引用或类型错误 —— **沙箱环境无 Android SDK 与外网，无法运行真实构建**；已通过全工程 grep 验证：被删符号（`listOpenJdkWorkflowRuns` / `listMobileGlWorkflowRuns` / `listWorkflowRuns` / `OPENJDK_WORKFLOW_ID` / `MOBILEGL_WORKFLOW_ID` / `Workflow` / `WorkflowPage`）无任何残留引用；新增符号（`withRateLimit` / `jdkKeywords` / `screenTransitionSpec` / `buildItemsFromPage`）定义且被正确调用；所有 import 一致；无残留 `collectAsState()` 或 `android.content.` FQN
- [x] 所有用户可见行为（网络请求、缓存、节流、镜像、UI 动画、错误提示）与优化前等价
