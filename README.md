# Uniaball 下载站

[uniaball.github.io](https://uniaball.github.io) 的非官方 Android 客户端复刻，使用 Kotlin + Jetpack Compose + Material Design 3 开发。

## 功能特性

- **首页**：三个项目入口卡片，点击进入对应下载页面
- **DesktopGlues Releases**：加载 GitHub Releases 列表，支持 Markdown 格式描述完整渲染，通过 gh-proxy.com 镜像下载各版本 Asset，可切换直连下载
- **OpenJDK-Android**：选择 JDK 版本（17/21/25/26/27/28），从 GitHub Actions 构建记录中获取 artifacts，通过 nightly.link 下载构建产物
- **MobileGL Actions**：拉取 MobileGL 工作流的构建产物，支持下拉刷新获取最新 APK
- **设置页**：可开关「使用 gh-proxy.com 镜像下载」、查看 README 说明、显示应用版本号
- **Material Design 3**：支持动态取色和深色模式

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.20 |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 状态管理 | ViewModel + StateFlow |
| 网络请求 | Retrofit 2.11.0 + kotlinx-serialization |
| 图片加载 | Coil 2.7.0 |
| Markdown 渲染 | compose-markdown 0.5.6 |
| 构建 | Gradle 8.9 + AGP 8.5.2 |
| CI/CD | GitHub Actions |

## 构建指南

### 本地构建

**环境要求：**

- JDK 17
- Android SDK（compileSdk 34、minSdk 24）
- Gradle 8.9（项目自带 gradlew）

**构建命令：**

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需配置签名）
./gradlew assembleRelease
```

## 项目结构

```
uniaball-downloader/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/com/uniaball/downloader/
│   │       ├── data/
│   │       │   ├── api/              # Retrofit 接口
│   │       │   │   └── GitHubApi.kt
│   │       │   ├── model/            # 数据模型
│   │       │   │   └── GitHubModels.kt
│   │       │   └── repository/       # 数据仓库（缓存、速率限制、镜像）
│   │       │       └── UniaballRepository.kt
│   │       ├── ui/
│   │       │   ├── components/       # 共享 UI 组件
│   │       │   │   ├── DownloadProgressDialog.kt
│   │       │   │   ├── DownloadScreenScaffold.kt
│   │       │   │   ├── StateViews.kt
│   │       │   │   └── StatusChip.kt
│   │       │   ├── screens/          # 各页面
│   │       │   │   ├── HomeScreen.kt
│   │       │   │   ├── DesktopGluesScreen.kt
│   │       │   │   ├── OpenJdkScreen.kt
│   │       │   │   ├── MobileGlScreen.kt
│   │       │   │   └── SettingsScreen.kt
│   │       │   ├── theme/            # Material3 主题
│   │       │   │   ├── Color.kt
│   │       │   │   ├── Theme.kt
│   │       │   │   └── Type.kt
│   │       │   ├── Destination.kt    # 导航定义
│   │       │   ├── EntranceAnimation.kt  # 入场动画
│   │       │   ├── MainScreen.kt     # 主屏幕
│   │       │   └── ScreenTransitions.kt  # 屏幕切换动画
│   │       ├── util/
│   │       │   ├── DownloadUtil.kt       # 下载入口
│   │       │   ├── FormatUtil.kt         # 格式化工具
│   │       │   ├── InAppDownloadManager.kt  # 应用内下载
│   │       │   └── LogUtil.kt            # 日志工具
│   │       ├── MainActivity.kt
│   │       └── UniaballApp.kt        # Application 初始化
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml            # 版本目录
├── .github/workflows/build.yml       # CI 工作流
├── settings.gradle.kts
└── build.gradle.kts
```

## 帮助与反馈

- 原网站：[uniaball.github.io](https://uniaball.github.io)
- 如有 Bug 或功能建议，请在 GitHub Issues 中提交