# TweakServer app_process 常驻服务（C2）设计说明

> 日期：2026-07-29  
> 状态：已批准（用户确认 C2 + Binder IPC）  
> 范围：去掉 native `tweakd`；电池采样 + 无障碍巡检迁入 `app_process`；高性能电池日志 + App 增量同步

## 1. 背景与目标

### 1.1 为什么改

- 长时间采样放在无障碍服务会导致部分 ROM（如 ColorOS）反复弹权限窗。
- 纯 native `tweakd`（shell uid）在骁龙等机型上 SELinux 拒绝 battery sysfs；`dumpsys` 文本常拿不到电流。
- BatteryRecorder 证明：`app_process` + `IBatteryPropertiesRegistrar` 可在 ADB/shell 下可靠取电流。
- App 启动全量扫日志入库过慢；需高性能存储与按事件分流的增量同步。

### 1.2 目标（C2）

1. **唯一常驻特权进程**：`TweakServer`（`app_process`），**删除 `tweakd` ELF 及全部依赖**。
2. **电池采样**在 `TweakServer` 内完成（Sysfs 优先，失败回退 Binder `batteryproperties`）。
3. **无障碍巡检**从 tweakd 迁入同一进程（受「无障碍 Daemon」开关控制，默认关）。
4. **高性能电池日志 v2** 写入 `TWEAK_ALPHA_ROOT`；App **不**在启动路径全量重扫。
5. **IPC 使用 Binder**（参考 BR：Server 向 App 投递 Binder；App 经 ContentProvider/回调持有）。
6. 适配 Android **API 24–37**；Root 与 Shizuku（shell uid=2000）均可启动。

### 1.3 非目标（本期不做）

- 不在 `TweakServer` 内直接写 App 私有 Room（跨 uid/路径复杂）；Room 仍由 App 增量导入。
- 不做分 App 续航预测（可不记前台包名；预留扩展点即可）。
- 不保留 tweakd TCP/`tweakd.port` 协议兼容（彻底替换）。

## 2. 总体架构

```text
Root / Shizuku (uid 0 | 2000)
  └─ libtweak_starter.so
       └─ app_process --nice-name=tweak_server
            └─ TweakServer.Main
                 ├─ BatteryEngine（采样 + v2 日志）
                 ├─ A11yWatchEngine（读 a11y_watch.conf）
                 ├─ TweakServerBinder (AIDL Stub)
                 └─ BinderSender → 投递到 App BinderProvider

App (正常应用 uid)
  ├─ NativeDaemonController → 启停 starter / 持有 ITweakServer
  ├─ 写 conf：battery_record.conf / a11y_watch.conf
  ├─ syncOnStartup()（后台、有预算）
  └─ 充电页 FileObserver(+轮询兜底)
       → syncOnLogCreated / Appended / Removed → Room
```

### 2.1 工作区（统一，不区分 Root/Shizuku）

根目录：`/data/local/tmp/tweak-alpha`（`ConstCommon.Path.TWEAK_ALPHA_ROOT`）

| 路径 | 用途 |
|------|------|
| `…/daemon/` | 服务工作目录，`0777` |
| `…/daemon/tweak_server.pid` | 单例 pid |
| `…/daemon/battery_record.conf` | 电池采样配置 |
| `…/daemon/a11y_watch.conf` | 无障碍巡检配置 |
| `…/daemon/battery_logs/` | v2 电池日志 |
| `…/daemon/tweak_server.log` | 可选文本诊断日志 |

## 3. 进程启动与生命周期

### 3.1 starter（native 极小 ELF）

- 产物名建议：`libtweak_starter.so`（放入 jniLibs / nativeLibraryDir，对齐 BR）。
- 仅允许 `uid==0 || uid==2000`。
- `fork` + `setsid` + `exec app_process`：
  - `CLASSPATH` / `-Djava.class.path` = App APK path（`pm path` 或 `--apk=`）。
  - 入口：`io.github.lumkit.tweak.server.TweakServerMain`（最终包名以实现为准）。
  - `--nice-name=tweak_server`。
- App 侧：`NativeDaemonController.setEnabled(true)` → 特权 shell 执行 starter；`false` → Binder `stop()` 或 `kill` pid。

### 3.2 TweakServer 进程内

- `Looper.prepareMainLooper()` + 后台 HandlerThread（采样）。
- `FakeContext`（systemMain / systemContext），包名对外表现为 root/shell 语义（对齐 BR）。
- 启动时：写 pid、加载 conf、选 Sampler、启动 Battery + A11y 引擎、注册 BinderSender。
- 单例：已有存活 pid 则拒绝重复拉起（或握手后退出新实例）。

### 3.3 删除 tweakd

删除或停止维护：

- `androidSharedNative/.../tweakd/*` 可执行目标与 assets 拷贝
- CMake `add_executable(tweakd)`
- `TweakDaemon` 基于 TCP port 的 install/start/status（替换为 starter + Binder）
- `NativeDaemonController` 中 `a11y_watch=1`/`battery_log=1`/`tcp=` 字符串探测（改为 Binder `status()`）

## 4. Binder IPC

### 4.1 传输模型（对齐 BatteryRecorder）

1. App 暴露 `ContentProvider`（如 `TweakBinderProvider`），供特权进程 `call`/`insert` 写入 `Binder` 包裹。
2. `TweakServer` 内 `BinderSender`：在 App uid 变为 active / 前台时再次投递 Binder（防 App 进程重启丢句柄）。
3. App 持有 `ITweakServer`（AIDL），死亡监听 `linkToDeath` → 更新 UI「未运行」并按需重启。

### 4.2 AIDL 表面（最小集）

```text
interface ITweakServer {
  String ping();                    // "PONG"
  String status();                  // 结构化键值或 JSON 一行
  void stop();                      // 优雅退出
  void reloadConfig();              // 重读 conf（可选，也可用 inotify）
  // 可选扩展：getLastBatterySample() 仅调试
}
```

`status()` 至少包含：`running`、`pid`、`version`、`battery_enabled`、`battery_interval_ms`、`a11y_enabled`、`a11y_interval_ms`、`uid`。

### 4.3 与设置页

- **Native Daemon** 开关：启停 `TweakServer`；`checked` 绑定 `ITweakServer!=null && ping` 成功（或 `status.running`）。
- **无障碍 Daemon** 开关：只改 `a11y_watch.conf` 的 `enabled`，默认 `false`；不自动等于开 Native Daemon。

## 5. 电池采样

### 5.1 Sampler 策略

1. **SysfsSampler**（JNI 或 Kotlin 读节点）：Root 或可读 sysfs 时使用；打开失败则禁用。
2. **BinderBatterySampler**（主路径 for shell）：
   - `ServiceManager.getService("batteryproperties")` → `IBatteryPropertiesRegistrar.getProperty(CURRENT_NOW/CAPACITY/STATUS/…)`
   - 电压/温度：`battery` 服务 `dump(pfd)` 解析（可 JNI 加速，对齐 BR）。
3. 亮屏：`IPowerManager.isInteractive`（Binder）；失败再 dumpsys。

单位与现有 Room 对齐入库前换算：`voltageMv`、`currentMa`、`temperatureC`、`level`、`screenOn`、充放电 `state`。

### 5.2 会话状态机

与原 `BatteryRecordSampler` / tweakd 逻辑一致：

- 充↔放切换 → 结束旧 session 文件，开新文件。
- 充电 `confirmMs`（默认 5s）内确认；窗口内回放 → 标记 deleted。
- 间隔读 `battery_record.conf` 的 `interval_ms`（Slider 写入）；`inotify` 或 `reloadConfig`。

## 6. 高性能电池日志 v2

### 6.1 文件布局

目录：`…/daemon/battery_logs/`

- 主文件：`{startedAt}_{state}.brlog`
- 分片：`{startedAt}_{state}.brlog.001` …

### 6.2 文件头（定长 64 字节）

| 偏移 | 类型 | 含义 |
|------|------|------|
| 0 | 4B magic | `TWB2` |
| 4 | u16 version | `2` |
| 6 | u16 headerSize | `64` |
| 8 | u16 recordSize | 定长样本字节数（如 32） |
| 10 | u16 flags | reserved |
| 12 | i32 intervalMs | |
| 16 | i32 state | 0/1 |
| 20 | i32 confirmMs | |
| 24 | i64 startedAt | |
| 32 | i64 createdAt | |
| 40 | i8 confirmed | |
| 41 | i8 deleted | |
| 42 | i16 pad | |
| 44 | i64 endedAt | `-1` 表示进行中 |
| 52–63 | reserved | |

### 6.3 定长样本（建议 32 字节）

| 字段 | 类型 | 空值 |
|------|------|------|
| timestamp | i64 | — |
| level | i32 | — |
| voltageMv | i32 | `Int.MIN_VALUE` |
| currentMa | i32 | `Int.MIN_VALUE` |
| tempCenti | i16 | `Short.MIN_VALUE`（℃×100） |
| screenOn | u8 | — |
| pad | 3B | — |

追加：`fflush` 或按批 flush，便于 App `CLOSE_WRITE`/`MODIFY` 感知。

### 6.4 配置 `battery_record.conf`

```text
enabled=1
interval_ms=1000
max_part_bytes=8388608
```

`enabled` 跟随 Native Daemon 开启写 `1`；关闭服务时可不写停采样。

### 6.5 旧格式

- **一期**：导入器优先 v2；若存在旧空格 `.log`，保留只读兼容路径（可选预算导入），避免升级丢历史。
- 新写入一律 v2。

## 7. App 增量同步（高性能）

### 7.1 状态

`filesDir/battery_log_import_state.json`：`path → byteOffset`（v2 应对齐到 `headerSize + n*recordSize`）。

### 7.2 API

| API | 触发 | 行为 |
|-----|------|------|
| `syncOnStartup()` | 特权就绪后后台 | 扫目录；仅 `length>offset`；总字节预算（如 2MB）；批量 `insertSamples`；不阻塞 Splash |
| `syncOnLogAppended(path)` | `MODIFY`/`CLOSE_WRITE` | 只读该文件尾部；定长对齐；批量入库；更新 offset |
| `syncOnLogCreated(path)` | `CREATE`/`MOVED_TO` | 读头建/匹配 session；offset 从 header 后开始 ingest |
| `syncOnLogRemoved(path)` | `DELETE` | 删除 state 中 offset；**不**删 Room |

去重：同 session 内 `timestamp <= maxCached` 跳过；可选 DB 唯一索引 `(sessionId, timestamp)` 作双保险。

### 7.3 启动路径

`PrivilegedAppInitializer`：**禁止**阻塞式全量同步；`bgScope.launch { syncOnStartup() }`。

## 8. FileObserver 与工作区

### 8.1 结论

对 `/data/local/tmp/tweak-alpha/daemon/battery_logs`：

- 在目录 `0777`、App 可 `list`/`read` 时，**多数设备 FileObserver（inotify）可用**。
- 非 100%：OEM SELinux、目录删除重建会导致 watch 失效。

### 8.2 实现要求

1. 启动前：`mkdirs` + `canRead` + `list` 探测；失败 → **轮询兜底**（充电页 1–2s 比 length）。
2. Watch 事件：`CREATE | MODIFY | MOVED_TO | CLOSE_WRITE | DELETE`；`DELETE_SELF` 后重挂。
3. 防抖 100–300ms 合并回调。
4. 按事件分发到 §7.2 对应函数，禁止页面里调用全目录重扫。

## 9. 无障碍引擎（迁入 TweakServer）

- 配置：`a11y_watch.conf`（`enabled` / `interval_ms` / `component`）。
- `enabled` 仅当用户打开「无障碍 Daemon」；默认关。
- 实现：`settings` 命令或等价 API；尊重既有 ColorOS 静默策略（不在不允许的 ROM 上疯狂 `settings put`）。
- 开机广播：仅 `a11yDaemonEnabled==true` 时 `AccessibilityBootstrap`。
- 无障碍服务内：**不再**启动电池采样；可选仍请求 `ensureRunningIfEnabled` 拉 `TweakServer`。

## 10. Hidden API / 依赖

- 引入或内嵌 stub：`ServiceManager`、`IBatteryPropertiesRegistrar`、`BatteryProperty`、以及 a11y/power 所需接口（可参考 BR `hiddenapi` 模块，按需裁剪）。
- 编译进 App APK，供 `app_process` 与 App 共用 classpath（server 代码可放在 `shared` android 源集或独立 `server` 模块打进 APK）。

## 11. 测试计划

1. Root：Sysfs 采样有电流；日志为 v2；Room 增量正确。
2. Shizuku：无 sysfs 权限时 Binder 有电流；不再出现长期 `current=-`（机型本身不暴露除外）。
3. 关闭 Native Daemon：进程退出，停止写日志。
4. 无障碍 Daemon 默认关：无自动 `settings put`；打开后巡检生效。
5. 启动耗时：Splash 不被同步阻塞；logcat 有 `syncOnStartup` 预算日志。
6. 充电页：追加写触发 `syncOnLogAppended`；新建分片触发 `Created`；FileObserver 失败时机型走轮询仍能更新。
7. 升级：旧 `.log` 可选导入；新文件仅为 `.brlog`。
8. App 被杀重启：BinderSender 重新投递后设置页状态恢复。

## 12. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Hidden API 变动 | stub + 多版本探测；失败降级 dumpsys |
| FileObserver 不可用 | 探测失败强制轮询 |
| App 与 Server 争用文件 | 仅 Server 写日志；App 只读 + offset |
| starter 被杀 | 开机/特权就绪/无障碍连接时 `ensureRunningIfEnabled` |
| 模块体积 | stub 精简；server 与 UI 分包但同 APK |

## 13. 决策记录

| 项 | 决定 |
|----|------|
| 方案 | **C2**：电池 + a11y 均迁入 app_process，**去掉 tweakd** |
| IPC | **Binder**（Provider 接收 + BinderSender 投递） |
| 工作区 | 统一 `TWEAK_ALPHA_ROOT` |
| 落盘 | 高性能 **v2 定长日志**；App 增量入库 Room |
| 旧日志 | 一期只读兼容可选 |
| 无障碍 Daemon | 默认关，与 Native Daemon 解耦 |

## 14. 实现分期建议

1. **P0**：starter + TweakServer 骨架 + Binder 连通 + status/stop  
2. **P1**：电池 Sampler + v2 日志 + conf  
3. **P2**：App 同步 API 改造 + FileObserver/轮询  
4. **P3**：A11yWatch 迁入；删除 tweakd 与 TCP 客户端  
5. **P4**：清理、回归、文档  

---

**Self-review**：无 TBD；C2/Binder/工作区/v2/同步 API/FileObserver 结论一致；范围可单计划执行（按分期拆 PR）。
