# 代码优化与清理 Spec

## Why
当前 Uniaball Downloader 代码中存在大量重复样板（403 退避处理、ViewModel 错误处理、缓存查找分支、UI 过渡动画 spec）、明显的死代码（从未被调用的 `listOpenJdkWorkflowRuns` / `listMobileGlWorkflowRuns` / `GitHubApi.listWorkflowRuns` 及未被引用的 `Workflow` / `WorkflowPage` 模型），以及若干一致性缺陷（`MobileGlScreen` 用 `collectAsState()` 而其他屏幕用 `collectAsStateWithLifecycle()`、Repository 中使用全限定类名 `android.content.Context` 而非 import）。这些拖累了可读性与可维护性，需要在不改变任何对外行为的前提下进行一次系统化优化。

## What Changes
- **统一 403 退避处理**：在 `UniaballRepository` 中新增私有挂起辅助 `withRateLimit`，将 `checkRateLimit()` + `try/catch HttpException 403 → markRateLimited + throw RateLimitedException` 的重复模式封装为单一入口，5 处调用点全部收敛
- **统一缓存查找逻辑**：把 `OpenJdkViewModel.lookupCachedItems` 中 4 段近乎一致的"过滤 runs → take(5) → 查 artifacts → flatten"逻辑抽取为单一私有挂起函数，消除 ~50 行重复代码
- **抽取共享 UI 过渡动画 spec**：把 `DesktopGluesScreen` / `OpenJdkScreen` / `MobileGlScreen` 中三份完全相同的 `fadeIn + slideInVertically togetherWith fadeOut + slideOutVertically` transitionSpec 抽到 `ui/` 包内一个共享顶层函数，三处调用点改用共享实现
- **统一 `collectAsState` 用法**：`MobileGlScreen` 与 `SettingsScreen` 中的 `collectAsState()` 改为 `collectAsStateWithLifecycle()`，与其他屏幕保持一致
- **清理 Repository 全限定类名**：在 `UniaballRepository.kt` 顶部 import `android.content.Context` 与 `android.content.SharedPreferences`，删除文件内 6 处 `android.content.` 前缀
- **删除死代码**：
  - 移除 `UniaballRepository.listOpenJdkWorkflowRuns(jdkVersion: Int)`（从未被调用）
  - 移除 `UniaballRepository.listMobileGlWorkflowRuns()`（从未被调用）
  - 移除 `GitHubApi.listWorkflowRuns`（仅被上述两个死方法引用）
  - 移除 `GitHubModels.kt` 中的 `Workflow` 与 `WorkflowPage` 数据类（从未被引用）
  - 移除 `UniaballRepository` 中仅被死代码使用的常量 `OPENJDK_WORKFLOW_ID` 与 `MOBILEGL_WORKFLOW_ID`
- **抽取版本过滤关键字逻辑**：将 `listOpenJdkRuns` 与 `OpenJdkViewModel.lookupCachedItems` 中重复的 `"jdk$versionStr", "openjdk$versionStr", "java$versionStr"` 关键字构造逻辑收敛到 `UniaballRepository` 的一个私有函数中

## Impact
- Affected specs: 无对外 spec，本次为内部代码质量优化
- Affected code:
  - [UniaballRepository.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/data/repository/UniaballRepository.kt) — 403 退避封装、死代码删除、过滤关键字抽取、import 清理
  - [GitHubApi.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/data/api/GitHubApi.kt) — 删除 `listWorkflowRuns`
  - [GitHubModels.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/data/model/GitHubModels.kt) — 删除 `Workflow` / `WorkflowPage`
  - [OpenJdkScreen.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/ui/screens/OpenJdkScreen.kt) — `lookupCachedItems` 重构、复用关键字过滤、共享 transitionSpec
  - [DesktopGluesScreen.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/ui/screens/DesktopGluesScreen.kt) — 共享 transitionSpec
  - [MobileGlScreen.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/ui/screens/MobileGlScreen.kt) — 共享 transitionSpec、`collectAsStateWithLifecycle`
  - [SettingsScreen.kt](file:///workspace/app/src/main/java/com/uniaball/downloader/ui/screens/SettingsScreen.kt) — `collectAsStateWithLifecycle`
  - 新增 `ui/ScreenTransitions.kt` — 共享过渡 spec
- 行为不变：所有用户可见的网络请求、缓存、节流、镜像、UI 动画、错误提示与原实现等价

## ADDED Requirements

### Requirement: 统一的 API 速率限制退避封装
`UniaballRepository` 内所有发往 GitHub API 的挂起方法 SHALL 通过统一的私有辅助函数 `withRateLimit` 处理 403 退避，避免每处重复 `checkRateLimit` + `try/catch` 样板。

#### Scenario: 请求前已触发退避
- **WHEN** 调用任意 API 方法时 `rateLimitUntil` 仍在未来
- **THEN** 直接抛出 `RateLimitedException`，不发起网络请求

#### Scenario: 请求返回 403
- **WHEN** GitHub API 返回 HTTP 403
- **THEN** 标记 `rateLimitUntil = now + 5min` 并抛出 `RateLimitedException`

#### Scenario: 请求返回非 403 错误或成功
- **WHEN** HTTP 状态码非 403
- **THEN** 不修改 `rateLimitUntil`，原样向上抛出或返回结果

### Requirement: 共享 UI 状态过渡动画 spec
三个详情屏（DesktopGlues / OpenJdk / MobileGL）的 `AnimatedContent` SHALL 复用同一份 `screenTransitionSpec()`，避免重复定义。

#### Scenario: 任一屏幕状态切换
- **WHEN** UI state 变化触发 `AnimatedContent` 过渡
- **THEN** 使用与原实现等价的 `fadeIn(220) + slideInVertically(it/8) togetherWith fadeOut(180) + slideOutVertically(-it/8)` 效果

### Requirement: 共享关键字过滤逻辑
OpenJDK 版本匹配关键字列表（`jdk$V` / `openjdk$V` / `java$V`）SHALL 由 `UniaballRepository` 单一私有函数生成，并被 `listOpenJdkRuns` 与 `OpenJdkViewModel.lookupCachedItems` 共同复用。

#### Scenario: 给定 JDK 版本号
- **WHEN** 调用 `listOpenJdkRuns(17)` 或缓存查找
- **THEN** 使用的 lowercase 关键字集合为 `["jdk17", "openjdk17", "java17"]`，与原实现一致

## MODIFIED Requirements

### Requirement: OpenJDK 缓存查找
`OpenJdkViewModel.lookupCachedItems` SHALL 通过单一私有辅助函数处理 4 个缓存源（allRuns 内存、allRuns 磁盘、version 内存、version 磁盘），每个缓存源的处理逻辑为：过滤 runs → take(5) → 异步查 artifacts → 拼装为 `OpenJdkBuildItem` 列表；任一缓存源命中非空即返回。查找顺序、过滤规则与原实现完全等价。

#### Scenario: allRuns 内存缓存命中
- **WHEN** `getCachedAllRuns()` 返回非空且过滤后存在匹配 runs 且 artifacts 已缓存
- **THEN** 返回拼装的 `OpenJdkBuildItem` 列表，不再查磁盘

#### Scenario: 所有缓存源均未命中
- **WHEN** 4 个缓存源均无可用数据
- **THEN** 返回空列表，调用方据此进入网络请求分支

## REMOVED Requirements

### Requirement: 未使用的 Workflow Runs 接口
**Reason**: `GitHubApi.listWorkflowRuns` 与其包装 `UniaballRepository.listOpenJdkWorkflowRuns` / `listMobileGlWorkflowRuns` 自首次引入以来从未被任何 ViewModel 或 Screen 调用；实际生产路径走 `listAllRuns` 后在客户端按 `run.name` 过滤。
**Migration**: 无需迁移。相关常量 `OPENJDK_WORKFLOW_ID` / `MOBILEGL_WORKFLOW_ID` 一并删除。

### Requirement: 未使用的 Workflow / WorkflowPage 数据模型
**Reason**: `GitHubModels.kt` 中的 `Workflow` 与 `WorkflowPage` 数据类从未在任何 API 接口签名或业务代码中被引用，是早期设计残留。
**Migration**: 无需迁移。
