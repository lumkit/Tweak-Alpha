# 耗电统计（Discharge Statistics）设计说明

> 日期：2026-08-01  
> 状态：已批准（方案 1：旁路 `.applog` + 镜像充电统计架构）  
> 范围：耗电统计页、DISCHARGING 会话、应用耗电旁路日志与增量导入、电量%+App 图标条图表

## 1. 背景与目标

### 1.1 背景

- 电池库已写入 `DISCHARGING` / `CHARGING` / `FULL` session 与 sample，但 UI 仅有充电统计。
- Scene 式「耗电统计」需要：放电过程曲线、摘要、按前台应用聚合、历史切换/删除。
- sample 表无前台包名字段；本期用旁路表 + 多对多关联 sample，不改 brlog v2 定长布局。

### 1.2 目标

1. 新页面「耗电统计」，只展示 `BatteryChargeState.DISCHARGING` 会话。
2. 本次耗电会话：电量% 曲线 + App 图标条；图下展示电池信息（Wh / 温度 / 电压 / 充电状态）。
3. 耗电摘要：平均功耗、已使用时长、理论续航（A→B→C 降级）。
4. 使用场景（应用列表）：按时长 / 平均功耗排序；无数据时空态「暂无应用耗电数据」。
5. 历史切换与删除：交互对齐充电统计；删除时清理 Room + `.brlog` + `.applog`。
6. Daemon 采样写 `.applog`；App 增量导入；耗电页与充电页一样监听日志目录更新 Room。

### 1.3 非目标

- 不修改 brlog v2 定长 record 布局。
- 不做分 App 续航预测。
- 不在本期大改充电统计 UI。
- 无 Daemon / 无 applog 的旧会话不强行伪造应用耗电数据。

## 2. 总体架构

```text
TweakServer (BatteryEngine)
  ├─ BrlogWriter → {startedAt}_{state}.brlog
  └─ ApplogWriter → {startedAt}_{state}.applog   // 与 sample 同拍，包名高效缓存

App
  ├─ BatteryRecordLogWatcher（battery_logs 目录）
  │     onCreated/Appended/Removed → brlog + applog 增量导入
  ├─ Room battery_record.db
  │     session / sample / app_usage / app_usage_sample
  └─ DischargeStatisticsScreen
        默认最新/活跃放电 session；历史 override；进页启动 Watcher
```

目录：`/data/local/tmp/tweak-alpha/daemon/battery_logs`（与现有 brlog 相同）。

## 3. 数据模型

### 3.1 沿用

- `battery_record_session`、`battery_record_sample` 不变。
- 耗电页查询：`state = DISCHARGING`（`confirmed` 放电默认 true）。

### 3.2 新增（Room version 1 → 2）

**`battery_app_usage`**

| 字段 | 说明 |
|------|------|
| id | PK |
| sessionId | FK → session |
| packageName | 前台包名 |
| startedAt | 段开始 epoch ms |
| endedAt | 段结束；进行中可 null |

**`battery_app_usage_sample`（多对多）**

| 字段 | 说明 |
|------|------|
| usageId | FK → app_usage |
| sampleId | FK → sample |
| 联合主键 | (usageId, sampleId) |

删除 session 时级联删除 usage 与 M2M 行。

### 3.3 磁盘旁路 `.applog`

- 文件名：`{startedAt}_{state}.applog`（可分片，规则对齐 brlog）。
- Header：magic、version、`startedAt`、`state`、与会话对齐的元数据。
- Record：`sampleTimestamp` + `packageName`（便于导入时按时间对齐已入库 sample）。
- 仅包名变化时写清段边界；连续同包按各 sample 时间点挂 M2M，避免冗余大段重复元数据。

## 4. 采样与读包效率

### 4.1 写入时机

`BatteryEngine` 每次成功写入 brlog sample 时，同步解析前台包名并写入 applog（同 `timestampMs`）。

### 4.2 读包策略（强化效率）

1. **短 TTL 缓存**（约 1–2s）：未过期直接复用上一次包名。
2. **轻量路径优先**：可复用 Server 侧已有焦点/缓存时不跑 dumpsys。
3. **必要时**再 `dumpsys window` / activity 焦点解析。
4. 失败则本拍记空包名，**绝不阻塞**电池采样主路径。

### 4.3 App 增量导入

- 扩展现有 `BatteryRecordLogSync` / `BatteryRecordLogWatcher` / Importer：识别 `.applog`。
- 启动有预算增量 + 耗电统计页进页 Watcher（与充电统计相同模式）。
- 导入顺序：对应 sample（同 session + timestamp）已在 Room → 创建/合并 usage → 写 M2M；对不齐则跳过该点。
- 无 applog 的会话：应用列表展示「暂无应用耗电数据」。

### 4.4 删除

`BatteryRecordRepository.deleteSession`：

1. 按 `startedAt`+`state` 删除 `.brlog*` 与 `.applog*`，清理 import offset。
2. 删除 usage / M2M / samples / session。

## 5. UI

### 5.1 入口

- `Screen.DischargeStatistics`，标题「耗电统计」。
- `FeatureRegistry` 性能类目增加 Provider；权限要求对齐充电统计（Shizuku/Root）。
- 页底快捷链：电流单位校准 / 充电统计 / 充电控制（有路由则跳转）。

### 5.2 页面结构

1. **顶栏**：返回 · 标题 · 历史 · 删除（对齐充电统计：历史 sheet 多选删除；顶栏删除语义与充电页一致）。
2. **使用过程**
   - 标题 + 帮助；右侧电量%。
   - 新组件 `BatteryLevelAppStripChart`：电量%折线 + 时间轴；下方 App 图标条按 usage 时段对齐；无应用数据则只画曲线。
   - 图下信息行：能耗 Wh、温度、电压、充电状态（放电会话一般为未充电）。
3. **耗电摘要**：平均功耗 · 已使用时长 · 理论续航。
4. **使用场景**：排序切换（时长 / 平均功耗）；列表项含图标、名称、AVG W/℃、MAX ℃、时长；空态文案见上。
5. **历史 BottomSheet**：放电 session 列表、点选覆盖主图、长按多选删除。

### 5.3 会话绑定

- 默认：活跃或最近一条 `DISCHARGING` session。
- 历史：`chartsSessionOverrideId` 覆盖（同充电统计）。

## 6. 指标算法

### 6.1 平均功耗 / 时长 / 能耗

- 复用现有 session sample 聚合：`averagePowerUw`、时长 `endedAt - startedAt`（进行中用 now）、梯形积分能耗 Wh。

### 6.2 理论续航（逐步降级）

| 级 | 公式 | 失效条件 |
|----|------|----------|
| A | `设计容量Wh / 平均功耗W`（或等价：容量与均功率均有效） | 无设计容量、均功率≤0 |
| B | `(已用时长 / 已掉电量%) × 100%` | 掉电量%≤0 或时长无效 |
| C | `(已用时长 / 已掉电量%) × 当前剩余%` | 掉电量%≤0、剩余%无效或时长无效 |
| — | 显示 `--` | A/B/C 皆失败 |

## 7. 错误处理

- 读包失败：空包，不拖慢采样。
- applog 缺失/错位：曲线与摘要仍可用；应用列表空态。
- 导入或删文件失败：打日志，UI 不崩溃。
- 续航降级见 §6.2。

## 8. 主要改动面

| 区域 | 文件/模块（预期） |
|------|-------------------|
| Room | `BatteryRecordDatabase` v2；新 entity/dao；Repository |
| Daemon | `ApplogWriter`；`BatteryEngine` + 前台包缓存 |
| 导入 | `BatteryRecordLogImporter` / Sync / Watcher 扩展 `.applog` |
| UI | `dischargeStatistics/*`；`BatteryLevelAppStripChart`；FeatureRegistry；strings |
| 删除 | 扩展现有 `deleteLogsForSession` 覆盖 applog |

## 9. 验收要点

1. 放电中进入耗电统计：电量曲线随采样更新；摘要三项合理。
2. 有 applog 时图标条与应用列表有数据；可按时长/功耗排序。
3. 无 applog 旧会话：列表「暂无应用耗电数据」，页面其余可用。
4. 历史切换/删除后记录不回潮（brlog+applog 均已删）。
5. 读包缓存命中时 dumpsys 不明显抬高采样抖动。
