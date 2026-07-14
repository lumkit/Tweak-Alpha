## 1. 项目概况

**项目名称**: Tweak-Alpha  
**定位**: Android 高级工具箱。  
**仓库根目录**: `D:\Projects\lumkit\Tweak-Alpha`  
**包名**: `io.github.lumkit.tweak`（主应用）/ `io.github.lumkit.tweak.sharednative`（Native 层）  
**最低 SDK**: 24 (Android 7.0)  
**目标 SDK / 编译 SDK**: 37

---

## 2. 技术栈

| 层面 | 技术选型 |
|------|---------|
| 语言 | Kotlin 2.4.0 + C++ 17 (NDK) |
| 跨平台框架 | Kotlin Multiplatform (KMP)，当前仅 Android 目标 |
| UI 框架 | Jetpack Compose Multiplatform 1.11.1 |
| UI 组件库 | Miuix KMP 0.9.2（仿 MIUI 风格） |
| 导航 | Navigation3 1.1.1（`NavKey` / `NavDisplay` / `entryProvider`） |
| 状态管理 | ViewModel + StateFlow + Compose State |
| 序列化 | kotlinx-serialization 1.11.0 |
| 持久化 | DataStore Preferences 1.2.1 + Room 2.8.4 + SQLite Bundled |
| 图片加载 | Coil 3.5.0（含 GIF/SVG/Ktor 网络支持） |
| Root 支持 | libsu 6.0.0（TopJohnWu） |
| Shizuku 支持 | Shizuku API 13.1.5 |
| 视觉效果 | backdrop 模块（Liquid Glass / 内阴影 / 模糊 / RuntimeShader）+ kyant-shapes 1.2.0 |
| 构建系统 | Gradle (Kotlin DSL) + AGP 9.2.1 + Version Catalog + build-logic Convention Plugins |
| JNI | CMake 3.22.1，C++ 17，`tweak_shared_native.so` |
| IPC | AIDL (`IRootFileService`) |

---

## 3. 模块架构

```
Tweak-Alpha/
├── androidApp/           # Android Application 壳模块
│   ├── 职责: Activity 入口、广播接收器、前台服务、签名配置、构建变体
│   └── 依赖: shared
│
├── shared/               # KMP 共享核心模块（commonMain + androidMain）
│   ├── commonMain/       # 平台无关代码
│   │   ├── model/        # 数据模型 (AndroidSoc, CpuState, RuntimeMode, GlobalViewModel...)
│   │   ├── navigation/   # 导航框架 (Screen, Navigator, NavigationState)
│   │   ├── common/
│   │   │   ├── base/     # BaseViewModel（LoadState 状态机）
│   │   │   ├── shell/    # Shell 抽象层 (KeepShell, ReusableShell, ReusableShells)
│   │   │   ├── feature/  # 系统功能框架 (UpdateEngine 相关)
│   │   │   ├── component/# 通用 Compose 组件 (Block, TopAppBar, LintChart, CategoryCard...)
│   │   │   └── utils/    # 工具集 (CPU/GPU/电池/温度/存储/DataStore/文件/Shell...)
│   │   └── ui/
│   │       ├── screen/   # 各功能页面 (Splash, Main, Info, Feature, Settings, UpdateSys)
│   │       └── theme/    # 主题 (Colors, ThemeDesign)
│   └── androidMain/      # Android 平台实现
│       ├── shell/        # KeepShell/ReusableShells 的 Android 实现
│       ├── utils/        # 平台 actual 实现（NativeFileAccess, BatteryUtils, TweakDataStore...）
│       └── service/      # Android Service 实现 (UpdateEngineService)
│
├── androidSharedNative/  # Android Native (C++ / JNI / AIDL) 模块
│   ├── cpp/              # JNI 实现：文件操作、GPU 信息、存储、系统属性
│   ├── aidl/             # IRootFileService.aidl (Root IPC 接口)
│   └── kotlin/           # JNI Bridge 层 (NativeFileBridge, GpuInfoBridge, StorageBridge...)
│
├── backdrop/             # Liquid Glass 视觉效果库（KMP 库模块）
│   └── 模糊、阴影、高光、RuntimeShader 渲染管线
│
├── glass/                # Backdrop Demo / Catalog 模块
│   └── 各种视觉效果展示 Demo
│
└── build-logic/          # 构建约定插件
    └── convention/       # AndroidApplicationFlavorsConventionPlugin
```

### 3.1 模块依赖关系

```
androidApp → shared → androidSharedNative
                    → backdrop → kyant-shapes
glass (独立 demo 模块)
```

---

## 4. 核心架构模式

### 4.1 运行模式 (RuntimeMode)

应用支持三种运行模式，决定系统能力边界：

```kotlin
enum class RuntimeMode(val user: Int) {
    Unknow(-1),  // 未授权，阻止进入
    Root(0),     // Root 模式（最高权限）
    Shizuku(1)   // Shizuku 模式（受限权限）
}
```

所有功能通过 `Capability` 声明权限需求：
- `ROOT_ONLY` — 仅 Root 可用
- `SHIZUKU_OR_ROOT` — Shizuku 或 Root 均可

### 4.2 Feature 注册系统

采用 Provider 模式动态注册功能模块：

```
FeatureRegistry (单例) 
  └── List<FeatureCategory>
       └── Set<FeatureProvider>
            ├── feature: Feature（元数据：key, title, icon, capabilities, route, rule）
            └── Content()（Compose UI）
```

**新增功能模块的标准流程**:
1. 创建 `FeatureProvider` 实现类
2. 定义 `Feature` 元数据（包含 `Capability` 权限声明）
3. 在 `FeatureRegistry.init` 中注册到对应 `FeatureCategory`
4. 在 `Screen` 密封类中添加路由
5. 在 `AppRoute` 的 `entryProvider` 中注册页面映射

### 4.3 Shell 执行引擎

三层 Shell 抽象：

```
ReusableShells (管理器，expect/actual)
  └── ReusableShell (可复用会话)
       └── KeepShell (底层 Shell，expect/actual)
```

- `ReusableShell` 使用 `START_TAG` / `END_TAG` 标记协议解析命令输出
- 所有 Shell 命令通过 `suspend` 挂起函数执行
- 支持同步执行 (`doCmdSync`)、流式监听 (`doCmdAsFlow`)、回调监听 (`doCmdWithListener`)

### 4.4 特权文件服务 (NativeFileService)

统一的文件操作抽象，支持三种后端：

```
NativeFileService (interface)
  ├── User 后端 — 普通用户态
  ├── ROOT 后端 — Root IPC (AIDL: IRootFileService)
  └── SHIZUKU 后端 — Shizuku IPC
```

操作：exists / list / readBytes / readText / writeBytes / writeText / delete / mkdirs / copy / move / chmod / unzipFromUri / zipEntries

返回值统一使用 `NativeFileResult<T>` 密封接口（Success / Failure）。

### 4.5 导航框架

基于 Navigation3（非传统 Navigation Compose）：

```
Navigator (导航控制器)
  └── NavigationState
       ├── topLevelRoute: NavKey
       └── backStacks: Map<NavKey, MutableList<NavKey>>
```

- 顶级路由切换不创建新栈，直接切换 `topLevelRoute`
- 子路由 push 到当前栈
- 支持 `singleTop` 防止重复页面

### 4.6 BaseViewModel 状态机

```kotlin
sealed class LoadState(id, message) {
    Loading → Success / Failure
}
```

所有 ViewModel 继承 `BaseViewModel`，统一管理加载/成功/失败状态。

## 5. 关键文件索引

| 文件 | 职责 |
|------|------|
| `shared/.../App.kt` | 应用入口 Composable，全局 CompositionLocal 提供 |
| `shared/.../TweakTheme.kt` | 主题封装（MiuixTheme） |
| `shared/.../navigation/Navigator.kt` | 导航控制器 |
| `shared/.../navigation/Screen.kt` | 路由声明（密封类） |
| `shared/.../model/GlobalViewModel.kt` | 全局状态（运行模式、刷新间隔） |
| `shared/.../model/RuntimeMode.kt` | 运行模式枚举 |
| `shared/.../common/base/BaseViewModel.kt` | ViewModel 基类 |
| `shared/.../common/shell/KeepShell.kt` | Shell 底层抽象 |
| `shared/.../common/shell/ReusableShell.kt` | 可复用 Shell 会话 |
| `shared/.../common/shell/ReusableShells.kt` | Shell 管理器 |
| `shared/.../common/utils/NativeFileAccess.kt` | 特权文件服务抽象 |
| `shared/.../common/utils/TweakDataStore.kt` | DataStore 持久化 |
| `shared/.../ui/screen/feature/FeatureRegistry.kt` | 功能注册中心 |
| `shared/.../ui/screen/feature/FeatureProvider.kt` | 功能提供者接口 |
| `shared/.../ui/screen/feature/model/Feature.kt` | 功能元数据 |
| `shared/.../ui/screen/feature/model/Capability.kt` | 权限能力枚举 |
| `shared/.../ui/screen/info/DeviceInfoViewModel.kt` | 设备信息 ViewModel |
| `shared/.../common/feature/UpdateEngineClient.kt` | OTA 更新客户端 |
| `androidSharedNative/.../NativeFileBridge.kt` | JNI Bridge |
| `androidSharedNative/.../IRootFileService.aidl` | Root IPC 接口 |
| `androidSharedNative/src/main/cpp/` | C++ JNI 实现 |
| `androidApp/.../AndroidManifest.xml` | 应用清单 |
| `androidApp/build.gradle.kts` | 应用构建配置 |
| `build-logic/.../AndroidApplicationFlavorsConventionPlugin.kt` | 构建变体插件 |

---

## 6. 编码规范与约束

### 6.1 语言与风格
- Kotlin 代码风格遵循 `kotlin.code.style=official`
- JVM 目标: Java 11
- C++ 标准: C++17
- Compose 函数命名: PascalCase
- 状态管理: 优先使用 `StateFlow` + `collectAsStateWithLifecycle`

### 6.2 KMP expect/actual 规则
- `expect` 声明放在 `commonMain`
- `actual` 实现放在 `androidMain`
- 当前平台目标列表中仅有 `android`，但架构已预留 KMP 扩展能力
- 新增平台相关功能必须遵循 expect/actual 模式

### 6.3 模块边界
- `androidApp` **不得**包含业务逻辑，仅作为壳模块
- `shared/commonMain` **不得**引用 Android SDK 类（`android.*`），使用 expect/actual 隔离
- `shared/androidMain` 可引用 Android SDK
- `androidSharedNative` 仅暴露 Bridge 层给 `shared`，不直接暴露 JNI 函数

### 6.4 Shell 操作规范
- 所有 Shell 命令必须通过 `ReusableShells` 执行，禁止直接创建 `Process`
- Shell 命令必须在协程中执行（`suspend` 函数）
- 长时间 Shell 任务使用独立 `getInstance(key)` 获取专用会话

### 6.5 文件操作规范
- 特权文件操作必须通过 `NativeFileService` 接口
- 返回值统一使用 `NativeFileResult<T>`（Success / Failure）
- 禁止在 `commonMain` 中使用 `java.io.File`

### 6.6 Feature 扩展规范
- 每个功能模块必须实现 `FeatureProvider` 接口
- 必须声明 `Capability` 权限需求
- 路由必须在 `Screen` 密封类中注册
- 功能注册统一在 `FeatureRegistry` 中完成

### 6.7 构建约束
- 启用 Gradle Configuration Cache 和 Build Cache
- R 类使用非传递模式 (`android.nonTransitiveRClass=true`)
- Release 构建启用 minify + shrink + R8 + 全版本签名 (v1-v4)
- ProGuard 规则按构建类型区分（`release-rules.pro` / `prod-rules.pro`）

---

## 7. Agent 操作指南

### 7.1 新增功能页面

```
1. 在 Screen.kt 中添加路由:
   @Serializable data object MyFeature: Screen()

2. 创建 FeatureProvider 实现:
   object MyFeatureProvider : FeatureProvider {
       override val feature = Feature(
           key = "my_feature",
           title = Res.string.xxx,
           icon = Res.drawable.xxx,
           description = Res.string.xxx,
           capabilities = setOf(Capability.SHIZUKU_OR_ROOT),
           route = Screen.MyFeature,
           defaultState = FeatureState.ENABLED,
           ruleDescription = { null },
       )
       @Composable override fun Content() { /* UI */ }
   }

3. 在 FeatureRegistry.init 中注册到对应类目

4. 在 App.kt 的 entryProvider 中添加路由映射:
   entry<Screen.MyFeature> { MyFeatureProvider.Content() }
```

### 7.2 新增 Shell 命令交互

```kotlin
// 单次命令
val result = ReusableShells.execSync("your_command")

// 专用会话（长任务）
val shell = ReusableShells.getInstance("task_name")
val result = shell.commitCmdSync("your_command")

// 流式输出
shell.doCmdAsFlow("tail -f /some/log").collect { line -> /* process */ }
```

### 7.3 新增特权文件操作

```kotlin
val service = PlatformNativeFileServices.getOrNull(NativeFileBackend.ROOT)
    ?: return

when (val result = service.readText("/sys/some/path")) {
    is NativeFileResult.Success -> result.value
    is NativeFileResult.Failure -> result.error.message
}
```

### 7.4 新增设备信息采集项

1. 在 `common/utils/` 中创建工具类
2. 使用 `expect/actual` 模式处理平台差异
3. 通过 `KernelProps`、`NativeFileService` 或 `ReusableShells` 读取 sysfs 节点
4. 在对应 ViewModel 中集成采集逻辑
5. 注意添加缓存机制（参考 `BatteryUtils` 的 500ms 缓存窗口设计）

### 7.5 修改 JNI / Native 代码

1. C++ 源码位于 `androidSharedNative/src/main/cpp/`
2. JNI 函数在 Kotlin Bridge 类中声明（`NativeFileBridge`、`GpuInfoBridge` 等）
3. CMake 配置在 `CMakeLists.txt`
4. Release 构建启用 LTO、隐藏符号、段优化
5. 导出符号通过 `exported_symbols.map` 控制

### 7.6 DataStore 持久化

```kotlin
// 读取
val flow: Flow<T> = TweakDataStore.xxxFlow()

// 写入
TweakDataStore.setXxx(value)
```

新增持久化字段:
1. 在 `TweakDataStore` 中定义 `PreferencesKey`
2. 添加 `Flow` 读取函数和 `suspend` 写入函数
3. 如需全局订阅，在 `GlobalViewModel` 中创建 `StateFlow`

---

## 8. 构建与运行

```bash
# 开发版 Debug
./gradlew :androidApp:assembleDevDebug

# 开发版 Release
./gradlew :androidApp:assembleDevRelease

# 生产版 Release
./gradlew :androidApp:assembleProdRelease

# 生产版 Dict（字典混淆）
./gradlew :androidApp:assembleProdDict

# 运行测试
./gradlew :shared:testDebugUnitTest
```

---