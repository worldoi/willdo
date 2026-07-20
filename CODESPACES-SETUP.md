# 在 GitHub Codespaces 中编译 CalendarAssistant（Android）

本仓库是 **Android + Kotlin (Gradle)** 单模块应用，工程位于 `CalendarAssistant/` 子目录。
GitHub Codespaces 通过仓库根目录的 `.devcontainer/devcontainer.json` 自动配置 **JDK 17 + 基础 Android SDK**，
随后用根目录的 `start.sh` 安装完整 SDK 并编译出 APK。**云端无模拟器/真机**，运行需靠实体机 `adb` 无线调试。

> 对照文档：Google IDX 用 `dev.nix` / `IDX-SETUP.md`；GitHub Codespaces 用 `.devcontainer/devcontainer.json`（本文件所述）。
> 两者共享同一个 `start.sh` 构建脚本，逻辑一致。

---

## 1. 在 GitHub 上开启 Codespaces

1. 打开 https://github.com/worldoi/willdo
2. 进入 **Settings → General**（仓库设置）→ 确认 **Codespaces** 相关可见性即可（默认公开仓库可直接用）。
3. 你的个人账号需有 Codespaces 额度：**Settings → Billing & plans → Codespaces** 能看到剩余时长/存储。

---

## 2. 创建 Codespace

1. 仓库页面顶部 **Code ▸ Codespaces** 标签页 → **Create codespace on main**
2. 区域（Region）选离你近的（如 Southeast Asia），机型选 **4 核 8 GB**（与 `devcontainer.json` 的 `hostRequirements` 对应；更小的机型编译可能 OOM）。
3. 点 **Create**。

首次创建时，GitHub 会：
- 拉取 `mcr.microsoft.com/devcontainers/android:0-17` 镜像（含 JDK 17 + 基础 Android SDK + gradle）
- 按 `.devcontainer/devcontainer.json` 注入 `ANDROID_HOME/JAVA_HOME` 等环境变量
- 安装列出的 4 个 VS Code 扩展
- 执行 `onCreateCommand` 给 `start.sh`、`gradlew` 加执行权限

环境就绪后自动打开一个浏览器里的 VS Code。

---

## 3. 编译 APK

在 Codespaces 的终端（Terminal ▸ New Terminal）执行：

```bash
./start.sh
```

`start.sh` 会依次：
1. 下载并解压 Android commandline-tools（仅首次）
2. `yes | sdkmanager --licenses` 接受所有许可证
3. 安装 SDK 包：`platform-tools`、`platforms;android-37`、`build-tools;35.0.0`（android-37 不可用时回退 `android-36`）
4. `cd CalendarAssistant && ./gradlew assembleDebug --no-daemon` 编译 debug APK

产物：`CalendarAssistant/app/build/outputs/apk/debug/app-debug.apk`

> 想单独重新编译（不改 SDK）可只在 `CalendarAssistant/` 里跑：`./gradlew assembleDebug --no-daemon`

---

## 4. 在真机上安装运行（Codespaces 无模拟器）

```bash
# 手机：设置 → 开发者选项 → 无线调试 → 使用配对码配对
adb pair <手机IP>:<配对端口>        # 输入 6 位码
adb connect <手机IP>:<端口>
adb -s <设备ID> install -r CalendarAssistant/app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. 已知风险 / 注意事项

- **`compileSdk = "android-37.0"`** 较新。若 SDK 仓库暂未提供 `platforms;android-37`，`start.sh` 回退装 `android-36`，
  但编译会因 `compileSdk` 不匹配报错。**兜底**：把 `CalendarAssistant/app/build.gradle.kts` 的
  `compileSdk` 改为 `36`，并把 `CalendarAssistant/gradle.properties` 里
  `android.suppressUnsupportedCompileSdk=37,37.0` 改为 `36`。
- **机器专属 JDK 路径已移除**：`gradle.properties` 里原来 `org.gradle.java.home=C:/Users/.../corretto-17`
  已删除（会破坏云端构建）。Codespaces 用 `JAVA_HOME` 环境变量定位 JDK 17，由 devcontainer 注入。
- **`local.properties` 未被 git 跟踪**，其中 `sdk.dir` 不会污染仓库；Codespaces/IDX 都用 `ANDROID_HOME`。
- **NDK 不需要**：项目仅用 `abiFilters` 裁剪 so，无 `externalNativeBuild`。
- **网络/依赖**：`settings.gradle.kts` 里的阿里云镜像在云端可用；若拉取慢可临时注释，仅留 `google()` + `mavenCentral()`。
- **磁盘**：compileSdk 37 + Gradle 缓存 + SDK 约占数 GB，已要求 `hostRequirements.storage = 32gb`。
- 完整依赖见同目录 `DEPENDENCIES.md`。

---

## 6. 与 Google IDX 的区别

| 项 | GitHub Codespaces | Google IDX |
| --- | --- | --- |
| 配置文件 | `.devcontainer/devcontainer.json` | `dev.nix` |
| 镜像 | `mcr.microsoft.com/devcontainers/android:0-17` | IDX 托管 Nix 环境 |
| JDK17 | 镜像自带 + 环境变量注入 | `pkgs.jdk17` |
| 构建脚本 | 共用 `start.sh` | 共用 `start.sh` |
| 真机运行 | `adb` 无线调试 | `adb` 无线调试 |

两者最终都跑同一个 `start.sh`，产物位置一致。
