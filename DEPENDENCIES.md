# 依赖清单（Dependency Manifest）

> 本项目是 **Android + Kotlin (Gradle)** 单模块应用（模块名 `:app`），**不存在** `package.json` / `requirements.txt` / `pubspec.yaml`。
> Android 的依赖清单等价物就是 **Gradle 版本目录 `gradle/libs.versions.toml`** + **`app/build.gradle.kts` 中的依赖声明**。
> 下方为完整、可读的依赖列表（含版本号），便于在 IDX / CI 等云端环境核对。

## 工具链（Toolchain）

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin (AGP) | 8.13.2 |
| Kotlin | 2.3.21 |
| KSP（注解处理） | 2.3.8 |
| Gradle | 由 `gradle/wrapper` 锁定（使用项目自带 `gradlew`，无需系统 Gradle） |
| compileSdk | android-37.0 |
| minSdk | 33 |
| targetSdk | 36 |
| JDK | 17 |
| NDK | 不需要（仅 `abiFilters` 裁剪，无 `externalNativeBuild`） |

## AndroidX / Jetpack

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| androidx.core:core-ktx | 1.17.0 | 核心 KTX |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.10.0 | 生命周期 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.7 | ViewModel + Compose |
| androidx.activity:activity-compose | 1.12.2 | Compose Activity |
| androidx.compose:compose-bom | 2024.09.00 | Compose 物料清单（统一管理 ui/material3 等版本） |
| androidx.compose.material3 | (由 BOM 管理) | Material 3 |
| androidx.compose.material:material-icons-extended | 1.5.4 | 扩展图标 |
| androidx.navigation:navigation-compose | 2.8.0 | 导航 |
| androidx.room:room-runtime | 2.8.4 | Room 数据库 |
| androidx.room:room-ktx | 2.8.4 | Room KTX |
| androidx.room:room-compiler | 2.8.4 | Room 注解处理器（ksp） |
| androidx.work:work-runtime-ktx | 2.9.0 | WorkManager |
| com.android.tools:desugar_jdk_libs | 2.0.4 | Java 8+ API 脱糖 |

## 第三方库

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.6.0 | JSON 序列化 |
| com.google.code.gson:gson | 2.10.1 | JSON（旧路径兼容） |
| io.ktor:ktor-client-android | 2.3.7 | 网络（AI 请求，Android 引擎） |
| io.ktor:ktor-client-core | 2.3.7 | 网络核心 |
| io.ktor:ktor-client-content-negotiation | 2.3.7 | 内容协商 |
| io.ktor:ktor-serialization-kotlinx-json | 2.3.7 | Ktor JSON 序列化 |
| com.google.mlkit:text-recognition-chinese | 16.0.1 | OCR 中文识别 |
| com.google.mlkit:barcode-scanning | 17.3.0 | 条码/二维码识别 |
| com.google.zxing:core | 3.5.3 | 二维码生成 |
| com.bihe0832.android:lib-sherpa-onnx | 6.25.21 | 离线语音转写（预编译 .so） |
| dev.rikka.shizuku:api | 13.1.5 | Shizuku 静默提权 |
| dev.rikka.shizuku:provider | 13.1.5 | Shizuku Provider |
| dev.rikka.shizuku:aidl | 13.1.5 | Shizuku AIDL |
| io.noties.markwon:core | 4.6.2 | 便签 Markdown 渲染 |
| io.noties.markwon:editor | 4.6.2 | Markdown 编辑器 |
| io.noties.markwon:ext-strikethrough | 4.6.2 | 删除线扩展 |
| io.noties.markwon:ext-tables | 4.6.2 | 表格扩展 |
| io.noties.markwon:ext-tasklist | 4.6.2 | 任务列表扩展 |
| io.noties.markwon:linkify | 4.6.2 | 链接识别 |

## 测试依赖

| 依赖 | 版本 |
| --- | --- |
| junit:junit | 4.13.2 |
| androidx.test.ext:junit | 1.3.0 |
| androidx.test.espresso:espresso-core | 3.7.0 |
| androidx.compose.ui:ui-test-junit4 | (由 BOM 管理) |
| androidx.compose.ui:ui-test-manifest | (由 BOM 管理) |
| androidx.compose.ui:ui-tooling | (由 BOM 管理) |

## 仓库（Repositories）

`settings.gradle.kts` 中声明：`google()`、`mavenCentral()`、`https://jitpack.io`、
`https://maven.aliyun.com/repository/public`（国内镜像）、`https://s01.oss.sonatype.org/content/repositories/snapshots/`。
云端构建若拉取慢，可临时注释掉阿里云镜像，仅保留 `google()` + `mavenCentral()`。

## 安装/同步命令（云端等价物）

- **安装依赖**：Android 没有独立的 "install" 步骤，依赖由 Gradle 在构建时按上述版本解析下载。
- **构建**：`./gradlew assembleDebug`（详见仓库根 `start.sh`）。
- **数据库迁移**：Room 的 Migration 已编译进 APK，在应用首次启动/升级时自动执行，**无需手动 migrate 命令**。
