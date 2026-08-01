# 耗电统计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增「耗电统计」页：展示 DISCHARGING 会话的电量曲线+App 图标条、摘要、应用列表与历史；Daemon 写 `.applog`，App 增量导入并建 usage↔sample 多对多。

**Architecture:** 不改 brlog v2。Daemon 与 sample 同拍写 `{startedAt}_{state}.applog`；App 扩展现有 battery_logs Watcher/Importer；Room v2 增加 `battery_app_usage` + `battery_app_usage_sample`；UI 镜像充电统计交互，主图用新组件 `BatteryLevelAppStripChart`。

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform、Room、TweakServer BatteryEngine、现有 `SmoothLineChart` 模式、FeatureRegistry。

**Spec:** [docs/superpowers/specs/2026-08-01-discharge-statistics-design.md](../specs/2026-08-01-discharge-statistics-design.md)

## Global Constraints

- 只展示 `BatteryChargeState.DISCHARGING`；不改 brlog 定长布局。
- 无 applog / 对不齐 sample → 应用列表「暂无应用耗电数据」，曲线与摘要仍可用。
- 读包必须短 TTL 缓存；失败记空包，不阻塞电池采样。
- 理论续航 A→B→C 降级；全失败显示 `--`。
- 应用列表排序仅：时长 / 平均功耗。
- 删除 session 必须同时清 `.brlog*`、`.applog*`、usage/M2M/samples/session、import offset。
- `docs/` 被 gitignore：提交 spec/plan 时用 `git add -f`。

---

## File map

| 路径 | 职责 |
|------|------|
| `shared/.../battery/table/BatteryAppUsageEntity.kt` | usage 表 |
| `shared/.../battery/table/BatteryAppUsageSampleEntity.kt` | M2M 表 |
| `shared/.../battery/BatteryRecordDatabase.kt` | version=2，注册新 entity |
| `shared/.../battery/dao/BatteryRecordDao.kt` | 放电查询 + usage CRUD |
| `shared/.../battery/repos/BatteryRecordRepository.kt` | 对外 API；删除级联 |
| `shared/.../dischargeStatistics/DischargeEta.kt` | 续航 A/B/C 纯函数 |
| `shared/src/commonTest/.../DischargeEtaTest.kt` | ETA 单测 |
| `shared/.../server/battery/ApplogWriter.kt` | 写 applog |
| `shared/.../server/battery/ForegroundPackageReader.kt` | TTL 缓存读包 |
| `shared/.../server/battery/BatteryEngine.kt` | 接入 applog |
| `shared/.../battery/BatteryRecordLogImporter.kt` | 导入 applog |
| `shared/.../battery/BatteryRecordLogWatcher.android.kt` | 识别 `.applog` |
| `shared/.../component/BatteryLevelAppStripChart.kt` | 电量%+图标条 |
| `shared/.../dischargeStatistics/*` | Screen / VM / Mapper / Format / Provider |
| `shared/.../navigation/Screen.kt` | `DischargeStatistics` |
| `shared/.../feature/FeatureRegistry.kt` | 注册入口 |
| `shared/.../composeResources/values/strings.xml` | 文案 |

---

### Task 1: Room v2 — usage 表与放电查询

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/table/BatteryAppUsageEntity.kt`
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/table/BatteryAppUsageSampleEntity.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/BatteryRecordDatabase.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/dao/BatteryRecordDao.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/repos/BatteryRecordRepository.kt`

**Interfaces:**
- Produces:
  - `BatteryAppUsageEntity(id, sessionId, packageName, startedAt, endedAt)`
  - `BatteryAppUsageSampleEntity(usageId, sampleId)` 联合主键
  - `Repository.observeDischargingSessions(): Flow<List<BatteryRecordSessionEntity>>`
  - `Repository.observeDischargingSessionForCharts(): Flow<BatteryRecordSessionEntity?>`
  - `Repository.observeAppUsagesBySessionId(sessionId): Flow<List<BatteryAppUsageEntity>>`
  - `Repository.insertAppUsage` / `insertAppUsageSamples` / `querySampleIdBySessionAndTimestamp`
  - `deleteSession` 内先删 usage_sample → usage → samples → session（日志删除仍走 `BatteryRecordLogSync.deleteLogsForSession`）

- [ ] **Step 1: 新增 entity**

```kotlin
@Entity(
    tableName = "battery_app_usage",
    indices = [Index("sessionId"), Index(value = ["sessionId", "packageName"])],
)
data class BatteryAppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

@Entity(
    tableName = "battery_app_usage_sample",
    primaryKeys = ["usageId", "sampleId"],
    indices = [Index("sampleId"), Index("usageId")],
)
data class BatteryAppUsageSampleEntity(
    val usageId: Long,
    val sampleId: Long,
)
```

- [ ] **Step 2: Database version=2，entities 列表加入上两表**（开发期可用 fallbackToDestructiveMigration 若项目已有；否则加 Migration(1,2) 建表）

- [ ] **Step 3: Dao 增加**

```kotlin
@Query("SELECT * FROM battery_record_session WHERE deleted = 0 AND state = :state ORDER BY startedAt DESC")
fun observeSessionsByState(state: Int): Flow<List<BatteryRecordSessionEntity>>

@Query("SELECT * FROM battery_app_usage WHERE sessionId = :sessionId ORDER BY startedAt ASC")
fun observeAppUsagesBySessionId(sessionId: Long): Flow<List<BatteryAppUsageEntity>>

@Query("SELECT id FROM battery_record_sample WHERE sessionId = :sessionId AND timestamp = :timestamp LIMIT 1")
suspend fun querySampleIdBySessionAndTimestamp(sessionId: Long, timestamp: Long): Long?

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAppUsage(entity: BatteryAppUsageEntity): Long

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAppUsageSamples(rows: List<BatteryAppUsageSampleEntity>)

@Query("DELETE FROM battery_app_usage_sample WHERE usageId IN (SELECT id FROM battery_app_usage WHERE sessionId = :sessionId)")
suspend fun deleteAppUsageSamplesBySessionId(sessionId: Long)

@Query("DELETE FROM battery_app_usage WHERE sessionId = :sessionId")
suspend fun deleteAppUsagesBySessionId(sessionId: Long)
```

- [ ] **Step 4: Repository 封装 discharging 观察与 deleteSession 级联**

```kotlin
fun observeDischargingSessions() =
    dao.observeSessionsByState(BatteryChargeState.DISCHARGING.code)

suspend fun deleteSession(sessionId: Long) {
    val session = dao.querySessionById(sessionId)
    if (session != null) {
        BatteryRecordLogSync.deleteLogsForSession(session.startedAt, session.state, sessionId)
    }
    dao.deleteAppUsageSamplesBySessionId(sessionId)
    dao.deleteAppUsagesBySessionId(sessionId)
    dao.deleteSamplesBySessionId(sessionId)
    dao.deleteSession(sessionId)
}
```

- [ ] **Step 5: Commit**

```bash
git add -A shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/
git commit -m "feat: Room v2 增加应用耗电 usage 与 M2M 表"
```

---

### Task 2: 理论续航 A→B→C 纯函数 + 单测

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeEta.kt`
- Create: `shared/src/commonTest/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeEtaTest.kt`

**Interfaces:**
- Produces: `fun estimateDischargeEtaMs(input: DischargeEtaInput): Long?`
- `DischargeEtaInput(designCapacityMah, avgPowerUw, durationMs, startLevel, endLevel, remainingLevel)`

- [ ] **Step 1: 写失败单测**

```kotlin
@Test
fun prefersCapacityOverSlope() {
    val ms = estimateDischargeEtaMs(
        DischargeEtaInput(
            designCapacityMah = 5000,
            avgPowerUw = 3_530_000L, // 3.53W
            durationMs = 43 * 60_000L,
            startLevel = 100,
            endLevel = 80,
            remainingLevel = 80,
        ),
    )
    // A: Wh≈3.7*5=18.5Wh, W=3.53 → hours = 18.5/3.53
    assertNotNull(ms)
    assertTrue(ms!! > 0)
}

@Test
fun fallsBackToFullSlopeWhenNoCapacity() {
    val ms = estimateDischargeEtaMs(
        DischargeEtaInput(
            designCapacityMah = null,
            avgPowerUw = 0L,
            durationMs = 3_600_000L,
            startLevel = 100,
            endLevel = 50,
            remainingLevel = 50,
        ),
    )
    // B: 1h / 50% * 100% = 2h
    assertEquals(7_200_000L, ms)
}

@Test
fun fallsBackToRemainingSlope() {
    val ms = estimateDischargeEtaMs(
        DischargeEtaInput(
            designCapacityMah = null,
            avgPowerUw = 0L,
            durationMs = 3_600_000L,
            startLevel = 80,
            endLevel = 60,
            remainingLevel = 40,
        ),
    )
    // C: 1h / 20% * 40% = 2h
    assertEquals(7_200_000L, ms)
}
```

注意：A 的 Wh 换算采用名义电压 **3.7V**：`designWh = designCapacityMah / 1000.0 * 3.7`，`avgW = avgPowerUw / 1e6`，`etaMs = designWh / avgW * 3_600_000`。单测用同一公式算期望值。

- [ ] **Step 2: 运行单测确认失败**

```bash
./gradlew :shared:cleanAllTests :shared:allTests --tests "io.github.lumkit.tweak.ui.screen.dischargeStatistics.DischargeEtaTest"
```

Expected: 编译失败或测试失败（符号不存在）。

- [ ] **Step 3: 实现 `DischargeEta.kt`（严格 A→B→C）**

```kotlin
data class DischargeEtaInput(
    val designCapacityMah: Int?,
    val avgPowerUw: Long,
    val durationMs: Long,
    val startLevel: Int,
    val endLevel: Int,
    val remainingLevel: Int,
)

fun estimateDischargeEtaMs(input: DischargeEtaInput): Long? {
    val avgW = input.avgPowerUw / 1_000_000.0
    val designMah = input.designCapacityMah
    if (designMah != null && designMah > 0 && avgW > 0.0) {
        val designWh = designMah / 1000.0 * 3.7
        return (designWh / avgW * 3_600_000.0).toLong().takeIf { it > 0 }
    }
    val dropped = input.startLevel - input.endLevel
    if (input.durationMs > 0 && dropped > 0) {
        // B: 外推掉到 0% 的等价整段时长
        return (input.durationMs.toDouble() / dropped * 100.0).toLong().takeIf { it > 0 }
    }
    if (input.durationMs > 0 && dropped > 0 && input.remainingLevel > 0) {
        // C: 按斜率外推剩余电量可用时长（B 因 dropped<=0 失败时几乎到不了；保留完整降级链）
        return (input.durationMs.toDouble() / dropped * input.remainingLevel)
            .toLong()
            .takeIf { it > 0 }
    }
    return null
}
```

说明：当 `dropped > 0` 时 B 会先返回；C 在 B 条件不满足时才会走到。与 spec 表一致。

- [ ] **Step 4: 跑通单测**

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeEta.kt \
  shared/src/commonTest/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeEtaTest.kt
git commit -m "feat: 耗电理论续航 A/B/C 降级算法"
```

---

### Task 3: ApplogWriter + ForegroundPackageReader + BatteryEngine

**Files:**
- Create: `shared/src/androidMain/kotlin/io/github/lumkit/tweak/server/battery/ApplogWriter.kt`
- Create: `shared/src/androidMain/kotlin/io/github/lumkit/tweak/server/battery/ForegroundPackageReader.kt`
- Modify: `shared/src/androidMain/kotlin/io/github/lumkit/tweak/server/battery/BatteryEngine.kt`
- Create: `shared/src/androidHostTest/kotlin/io/github/lumkit/tweak/server/battery/ApplogCodecTest.kt`（编解码 round-trip，纯 JVM）

**Interfaces:**
- Produces:
  - `ApplogWriter.openSession(meta)` / `append(timestampMs, packageName)` / `closeSession`
  - 文件名 `BrlogWriter` 同规则：`{startedAt}_{state}.applog`
  - Header magic ASCII `TWA1`（4B）+ version + startedAt + state + reserved
  - Record: `int64 timestamp` + `uint16 nameLen` + utf8 bytes（或定长 128B 包名，选 length-prefixed 节省空间）
  - `ForegroundPackageReader.resolve(): String` — TTL 1500ms 缓存；miss 时 dumpsys 解析 `mCurrentFocus`/`mFocusedApp` 包名；失败 `""`

- [ ] **Step 1: 实现 Applog 编解码 + host 单测 round-trip**

- [ ] **Step 2: ForegroundPackageReader**

```kotlin
class ForegroundPackageReader(
    private val ttlMs: Long = 1_500L,
    private val shell: (String) -> String, // 注入 ReusableShells 或 server 本地 exec
) {
    @Volatile private var cached = ""
    @Volatile private var cachedAt = 0L
    fun resolve(): String {
        val now = SystemClock.uptimeMillis()
        if (now - cachedAt <= ttlMs && cached.isNotEmpty()) return cached
        val pkg = runCatching { parseFocus(shell(DUMPSYS_CMD)) }.getOrDefault("")
        cached = pkg
        cachedAt = now
        return pkg
    }
}
```

`DUMPSYS_CMD` 用尽量轻的一条（如 `dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -n 3`），解析第一个 `u0_xxx / package/` 形态包名。

- [ ] **Step 3: BatteryEngine** 在 `writer?.appendSample(...)` 成功后：

```kotlin
val pkg = packageReader.resolve()
applogWriter?.append(now, pkg)
```

`openSession` / `closeSession` 与 brlog 同步开关 applog。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: Daemon 写入 applog 并缓存读取前台包名"
```

---

### Task 4: App 增量导入 `.applog` + Watcher

**Files:**
- Modify: `shared/src/androidMain/kotlin/io/github/lumkit/tweak/common/database/battery/BatteryRecordLogImporter.kt`
- Modify: `shared/src/androidMain/kotlin/io/github/lumkit/tweak/common/database/battery/BatteryRecordLogWatcher.android.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/database/battery/BatteryRecordLogSync.kt`（若需新 API 则加；否则复用 created/appended）

**Interfaces:**
- Consumes: Task 1 repository insert APIs；Applog 二进制格式（Task 3）
- Produces: `importApplog(path)` — 按 header `startedAt/state` 找 session；按 record timestamp 找 sampleId；维护「当前 usage」：包名变化则 end 旧 usage、insert 新 usage；每条非空包名 insert M2M
- `isLogPath` / Watcher `isBatteryLog` 增加 `.applog`
- `deleteLogsForSession` 前缀已是 `${startedAt}_${state}.`，列出 applog 后即可一并删除（确认 `listLogFiles` 过滤包含 applog）

- [ ] **Step 1: 扩展 `isLogPath`**

```kotlin
name.contains(".brlog") || name.contains(".applog") || name.endsWith(".log") || name.contains(".log.")
```

- [ ] **Step 2: `importPathInto` 分支** — 若 path 含 `.applog` 走 `importApplogInto`，否则原 brlog/log 逻辑

- [ ] **Step 3: importApplogInto 伪代码**

```kotlin
// 读 header → session = querySessionByStartedAtAndState
// 若 session == null：跳过（等 brlog 先导入）；offset 不前进或仅记 0
// 从 offset 读 records：
//   sampleId = querySampleIdBySessionAndTimestamp(session.id, ts) ?: continue
//   if (pkg.isBlank()) continue
//   if (pkg != currentPkg) { end previous usage; insert new usage; currentPkg=pkg }
//   insertAppUsageSample(usageId, sampleId)
```

- [ ] **Step 4: 启动 `syncOnStartup` 已遍历目录文件——applog 自动覆盖。充电页/耗电页 Watcher 回调不变。**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: 增量导入 applog 并重建应用耗电关联"
```

---

### Task 5: BatteryLevelAppStripChart 组件

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/component/BatteryLevelAppStripChart.kt`
- 可参考: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/common/component/Charts.kt` 的 `SmoothLineChart` / `LineChartData`

**Interfaces:**
- Produces:

```kotlin
data class AppStripSegment(
    val packageName: String,
    val startRatio: Float, // 0..1 相对会话时间轴
    val endRatio: Float,
    val iconPath: String?,
)

@Composable
fun BatteryLevelAppStripChart(
    levelSeries: LineChartData,
    xAxis: LineChartXAxisData,
    appSegments: List<AppStripSegment>,
    modifier: Modifier = Modifier,
)
```

- 上：电量% 平滑曲线（Y 0–100）；下：按 `startRatio..endRatio` 画圆形 App 图标（Coil/`AsyncImage` 用 `AppsHelper` 图标路径）；`appSegments` 空则只留曲线。

- [ ] **Step 1: 实现组件（先无图标占位圆点，再接 iconPath）**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: 新增电量曲线与 App 图标条图表组件"
```

---

### Task 6: DischargeStatistics ViewModel + 聚合

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeStatisticsViewModel.kt`
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeSessionMapper.kt`
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeSessionFormat.kt`

**Interfaces:**
- 对齐 `ChargeStatisticsViewModel`：
  - `startBatteryLogWatcher()`（与充电页相同，brlog+applog 已由 importer 处理）
  - `observeDischargingSessionForCharts` + `_chartsSessionOverrideId`
  - `chartSamples` / `summary` / `batteryInfoRow` / `appRows` / `etaText`
  - `AppSortMode { Duration, AvgPower }`
  - `deleteSelectedDischargeHistory()` → `repository.deleteSession`
- `DischargeSessionMapper`：
  - samples → level `LineChartData` + 能耗 Wh（复用充电侧梯形积分，放电用绝对值）
  - usages + samples → `AppStripSegment` + `AppUsageRow(avgW, avgTemp, maxTemp, durationMs)`
  - 聚合：usage 关联 sample 的 current/voltage/temp 算 AVG；时长 `endedAt-startedAt` 或首末 sample 时间差

- [ ] **Step 1: 实现 Mapper + Format（时长文案可复用 `formatChargeSessionDuration`）**

- [ ] **Step 2: 实现 ViewModel 状态流与历史 override**

- [ ] **Step 3: ETA 调用 `estimateDischargeEtaMs`，设计容量来自 `BatteryUtils.getDesignCapacity()`**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: 耗电统计 ViewModel 与会话聚合"
```

---

### Task 7: DischargeStatistics UI + 导航入口

**Files:**
- Create: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/dischargeStatistics/DischargeStatisticsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/navigation/Screen.kt` — 增加 `data object DischargeStatistics`
- Modify: `shared/src/commonMain/kotlin/io/github/lumkit/tweak/ui/screen/feature/FeatureRegistry.kt` — 注册 Provider（紧挨 ChargeStatistics）
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` — 标题/摘要/空态/帮助/排序等

**Interfaces:**
- `DischargeStatisticsProvider` 镜像 `ChargeStatisticsProvider`（`Capability.SHIZUKU_OR_ROOT`，`route = Screen.DischargeStatistics`）
- Screen 布局：顶栏历史/删除 → 使用过程卡 → 摘要卡 → 使用场景卡 → 底部快捷链
- 历史 sheet：`observeDischargingSessions`，交互复制充电历史（多选删除对话框）
- 空列表：`stringResource(Res.string.text_discharge_app_usage_empty)` = `暂无应用耗电数据`

- [ ] **Step 1: strings + Screen route + FeatureRegistry**

- [ ] **Step 2: 搭建 Screen 骨架与三张卡片**

- [ ] **Step 3: 接入图表、摘要、应用列表排序切换、历史 sheet**

- [ ] **Step 4: 底部链到校准/充电统计/充电控制（已有 Screen 则 `navigator.push`；充电控制若无路由则隐藏）

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: 耗电统计页面与功能入口"
```

---

### Task 8: 端到端验收与收尾

**Files:** 按验收修 bug；必要时更新 spec 状态为「已实现」

- [ ] **Step 1: 编译**

```bash
./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

Expected: SUCCESS

- [ ] **Step 2: 真机检查清单（对照 spec §9）**
  1. 开启 Daemon，放电中进「耗电统计」→ 曲线与摘要更新
  2. `battery_logs` 出现 `.applog`；列表有应用；排序切换有效
  3. 删历史后文件与列表不再回潮
  4. 旧无 applog 会话 → 空态文案，曲线仍在
  5. 采样日志中 dumpsys 不应每拍都打（缓存命中）

- [ ] **Step 3: 最终 commit（若有修复）**

```bash
git commit -m "fix: 耗电统计验收问题修复"
```

---

## Spec coverage check

| Spec 项 | Task |
|---------|------|
| Room usage + M2M | 1 |
| DISCHARGING 查询 | 1, 6 |
| applog 写 + 读包缓存 | 3 |
| 增量导入 + Watcher | 4 |
| 删除 brlog+applog | 1, 4 |
| 电量%+图标条 | 5, 7 |
| 摘要 + ETA A/B/C | 2, 6, 7 |
| 应用列表排序 | 6, 7 |
| 历史 sheet | 6, 7 |
| Feature 入口 | 7 |
| 空态文案 | 7 |

## Placeholder scan

无 TBD；ETA 电压常数 3.7V 已写明；applog magic `TWA1` 已定。
