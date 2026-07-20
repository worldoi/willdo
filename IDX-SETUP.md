# 迁移到 Google Project IDX（云端开发环境）

本仓库是 **Android + Kotlin (Gradle)** 单模块应用，Android 工程位于 `CalendarAssistant/` 子目录。
**IDX 对纯 Android Gradle 项目可以编译出 APK，但云端没有模拟器/真机**，运行/调试需靠你的实体机通过 `adb` 无线调试。

---

## 1. 推送代码到 GitHub

仓库已存在远程 `https://github.com/AIXINJUELUOAI/Will-do`（分支 `main`），Android 工程在 `CalendarAssistant/` 子目录。

```bash
cd /path/to/Will-do
git add -A
git commit -m "chore: prep for IDX (dev.nix, start.sh, .gitignore, drop hardcoded JDK path)"
git push origin main
```

> 说明：`dev.nix`、`start.sh`、`.gitignore`、`DEPENDENCIES.md` 已放在**仓库根目录**（`Will-do/`），因为 IDX 从仓库根读取 `dev.nix`。

---

## 2. 在 IDX 中导入

1. 打开 https://idx.google.com
2. **New Workspace → Import from GitHub**
3. 选择 `AIXINJUELUOAI/Will-do` 仓库
4. IDX 读取根目录 `dev.nix`，自动安装 **JDK 17** 并配置环境变量
5. 工作区就绪后，在终端执行：

```bash
./start.sh
```

`start.sh` 会依次：
1. 安装 Android commandline-tools（仅首次）
2. 接受 SDK 许可证
3. 安装 SDK 包：`platform-tools`、`platforms;android-37`、`build-tools;35.0.0`（若 android-37 不可用则回退 `android-36`）
4. 编译 `CalendarAssistant` 的 debug APK

产物位置：`CalendarAssistant/app/build/outputs/apk/debug/app-debug.apk`

---

## 3. 在真机上运行（IDX 无模拟器）

```bash
# 手机：设置 → 开发者选项 → 无线调试 → 使用配对码配对
adb pair <手机IP>:<配对端口>      # 输入 6 位码
adb connect <手机IP>:<端口>
adb -s <设备ID> install -r CalendarAssistant/app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. 已知风险 / 注意事项

- **`compileSdk = "android-37.0"`** 是较新的 SDK。若 IDX 的 SDK 仓库暂未提供 `platforms;android-37`，
  `start.sh` 会回退安装 `android-36`，但编译会因 `compileSdk` 不匹配报错。
  **兜底方案**：把 `CalendarAssistant/app/build.gradle.kts` 的 `compileSdkVersion("android-37.0")` 改为 `36`，
  并同步 `CalendarAssistant/gradle.properties` 里的 `android.suppressUnsupportedCompileSdk=37,37.0` 改为 `36`。
- **已移除硬编码 JDK 路径**：原 `gradle.properties` 中的
  `org.gradle.java.home=C:/Users/Administer/dev/jdk/corretto-17` 已删除（机器专属绝对路径会破坏云端构建）。
  本地构建请 `export JAVA_HOME=<你的 JDK17 路径>`，或在 Android Studio 里指定 JDK 17。
- **`local.properties` 未被 git 跟踪**（仅 `gradle.properties` 被跟踪），其中的 `sdk.dir` 不会污染仓库；
  IDX 用 `ANDROID_HOME` 环境变量定位 SDK。
- **NDK 不需要**：项目仅用 `abiFilters` 裁剪 so，没有 `externalNativeBuild`，无需安装 NDK。
- **网络**：`settings.gradle.kts` 里的 `maven.aliyun.com` 等镜像在云端可用；若拉取慢，可注释掉，
  仅保留 `google()` + `mavenCentral()`。
- 完整依赖列表见同目录 `DEPENDENCIES.md`。

---

## 5. 项目结构是否标准？（检查结论）

当前结构**已经是标准 Android Gradle 单模块布局**，无需大改：

```
Will-do/                         <- git 仓库根（IDX 工作区根）
├── dev.nix                      <- IDX 环境配置（新增）
├── start.sh                     <- 云端构建脚本（新增）
├── .gitignore                   <- 忽略 build/、local.properties、*.log、*.apk 等（新增）
├── DEPENDENCIES.md              <- 依赖清单（新增）
├── IDX-SETUP.md                 <- 本文
├── CalendarAssistant/           <- Android 工程
│   ├── settings.gradle.kts      <- 单模块 :app，版本目录
│   ├── build.gradle.kts         <- 根工程配置 + 架构守卫任务
│   ├── gradle.properties        <- 已移除机器专属 JDK 路径
│   ├── gradle/libs.versions.toml<- 版本目录（标准做法）
│   └── app/
│       ├── build.gradle.kts     <- 应用依赖与编译配置
│       └── src/main/java/com/antgskds/calendarassistant/
│           ├── ui/              <- 界面层（按功能/页面组织）
│           ├── service/         <- 服务层
│           ├── platform/        <- 平台/系统能力（无障碍、悬浮窗等）
│           ├── data/            <- 数据层（Room、仓库）
│           └── ...              <- 其他按职责划分的包
```

**关于"按功能模块重新组织文件夹"的建议**：
- 当前按**职责分层 + 包内按功能**组织，对一个单模块应用来说已是合理且标准的做法，**不建议**在迁移前做大规模重构。
- 如果你后续希望进一步"模块化"，可以把 `ui/` 下的大功能（如日程、随口记、设置）拆成独立的 Gradle 模块
  （`:feature-schedule`、`:feature-quickmemo` 等），但这属于较大的架构改造，建议在云端环境跑通构建后再做，
  以免在迁移关键期引入不确定性。
