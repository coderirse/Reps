# Reps 开发文档

> 版本: v0.1（MVP 规划稿） · 日期: 2026-08-30
> 配套文档: [产品文档](./PRODUCT.md)
> 原则: 本文中的版本号与骨架以 `gradle/libs.versions.toml` 现状为基线；引入新依赖时用当时最新稳定版，不锁定本文中的示例小版本号。

---

## 1. 技术栈基线（现有骨架）

| 项 | 现状 |
|---|---|
| AGP | 9.2.1（compileSdk 36.1 新语法 `release(36){minorApiLevel=1}`） |
| Kotlin | 2.2.10 + Compose Compiler Gradle 插件 |
| Compose BOM | 2026.02.01，Material 3 |
| minSdk / target | 骨架现值 34 / 36；**已决策（2026-08-30）下调 minSdk 至 26**，`app/build.gradle.kts` 的修改随 Phase 1 一起提交 |
| 包名 | 骨架现值 `com.example.reps`；**已决策（2026-08-30）改为 `io.github.coderirse.reps`**（仓库 `coderirse/Reps`），与 minSdk 一起在 Phase 1 落地 |
| 已有 | 仅 Compose 基础三件套 + lifecycle-runtime，**无 Room / Navigation / DataStore** |

## 2. 新增依赖（写入 `libs.versions.toml`）

| 用途 | 坐标 | 说明 |
|---|---|---|
| 数据库 | `androidx.room:room-runtime` / `room-ktx` / `room-compiler`(KSP) | 全离线存储核心；`exportSchema=true` |
| KSP 插件 | `com.google.devtools.ksp` | 版本与 Kotlin 2.2.10 对齐 |
| 导航 | `androidx.navigation:navigation-compose` + `org.jetbrains.kotlinx:kotlinx-serialization-json` | 类型安全路由（2.8+ 支持 @Serializable 路由） |
| 偏好设置 | `androidx.datastore:datastore-preferences` | 夜间模式/字体/会话提醒开关 |
| ViewModel Compose | `androidx.lifecycle:lifecycle-viewmodel-compose` / `lifecycle-runtime-compose` | 与骨架 lifecycle 2.11.0 同源 |
| 进程级生命周期 | `androidx.lifecycle:lifecycle-process` | 后台兜底保存 |
| CSV 解析 | `com.github.doyaaaaaken:kotlin-csv-jvm` | RFC 4180 引号/内嵌换行，纯 JVM，体积小 |
| 协程 | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 显式引入便于管理 |
| 测试 | `androidx.room:room-testing`、`kotlinx-coroutines-test` | 其余复用骨架 |

**编码检测不引第三方库**：minSdk 26（API 26）≥ 24，可直接用内置 `android.icu.text.CharsetDetector`，零依赖完成 GBK/UTF-8 识别。

**依赖注入**：MVP 采用**手动 DI**（`RepsApplication` 中构建 `AppContainer`，ViewModel 通过 `viewModel(factory = ...)` 获取）。理由：单模块小项目，引入 Hilt 的构建复杂度（KSP 链路）与收益不成正比；若后续模块化再迁移。

## 3. 工程结构（单模块，清晰分包）

```
com.example.reps/
├─ RepsApplication.kt        # AppContainer：db / repos / settings 单例
├─ MainActivity.kt           # 单 Activity，setContent { RepsApp() }
├─ core/                     # Result 封装、时间、随机洗牌等工具
├─ data/
│  ├─ db/                    # RepsDatabase, entity/, dao/, Converters
│  ├─ csv/                   # EncodingDetector, CsvQuestionParser, CsvValidator
│  ├─ prefs/                 # SettingsRepository (DataStore)
│  └─ repo/                  # SubjectRepository / StudySessionRepository /
│                            # WrongBookRepository / FavoriteRepository / NoteRepository
├─ ui/
│  ├─ theme/                 # 现有 theme 扩展：深色三态、字体缩放
│  ├─ navigation/            # RepsNavHost + 序列化路由
│  ├─ home/  import/  study/  wrongbook/  favorites/  settings/
│  └─ components/            # 通用组件（空状态、确认对话框等）
└─ util/
```

分层遵循官方架构指南：**UI (Compose + ViewModel) → Repository → DataSource (Room/DataStore/SAF)**。MVP 不建独立 domain/usecase 层，业务规则先放在 Repository；出现复用再抽取。

## 4. 数据库设计（Room）

### 4.1 实体与关系

```
subjects 1 ──< questions 1 ──1 wrong_answers
                        1 ──1 favorites
                        1 ──1 notes
          1 ──< study_sessions 1 ──< session_answers >── 1 questions
```

### 4.2 表定义

```kotlin
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val questionCount: Int,          // 导入时写入，删除题按需重算
    val createdAt: Long,
)

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["subjectId", "orderIndex"], unique = true),
        Index(value = ["subjectId", "category"]),
        Index(value = ["subjectId", "chapter"]),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val orderIndex: Int,             // 题号（顺序练习依据）
    val type: String,                // SINGLE / JUDGE / MULTI / BLANK
    val content: String,
    val optionA: String?, val optionB: String?, val optionC: String?,
    val optionD: String?, val optionE: String?,
    val correctAnswer: String,       // 归一化："A" / "对" / "A,C"
    val explanation: String?,
    val category: String?,
    val chapter: String?,
)

@Entity(tableName = "study_sessions", indices = [Index("subjectId"), Index("status")])
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val practiceType: String,        // SEQUENTIAL / RANDOM / CATEGORY / WRONG_BOOK / FAVORITE
    val filterValue: String?,        // 专项练习的章节或分类值
    val reciteMode: String,          // MODE_A_BROWSE / MODE_B_TEST
    val questionIds: String,         // 快照 JSON 数组：会话题序（已按方式排序/洗牌）
    val currentIndex: Int,           // questionIds 的下标
    val selectedAnswer: String?,     // 当前题已选答案（模式B）
    val answerRevealed: Boolean,
    val randomSeed: Long,            // 洗牌种子（持久化，恢复后题序一致）
    val startedAt: Long,
    val lastActiveAt: Long,
    val accumulatedMs: Long,         // 累计刷题时长（5s 粒度近似）
    val status: Int,                 // 0 ACTIVE / 1 COMPLETED / 2 EXPIRED
)

@Entity(tableName = "session_answers", indices = [Index("sessionId"), Index("sessionId","questionId", unique = true)])
data class SessionAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val actionType: String,          // SELECTED（模式B作答）/ BROWSED（模式A浏览）
    val selectedAnswer: String?,     // SELECTED 时非空
    val isCorrect: Boolean?,         // BROWSED 为 null
    val answeredAt: Long,
    val dwellMs: Long?,              // 该题停留时长
)

// 以下三表以 questionId 为主键，删除题目时级联清理
@Entity(tableName = "wrong_answers")
data class WrongAnswerEntity(
    @PrimaryKey val questionId: Long,
    val wrongCount: Int,             // 累计做错次数
    val reviewCorrectCount: Int,     // 错题重练中连续做对次数
    val lastWrongAt: Long,
    val mastered: Boolean = false,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val questionId: Long,
    val createdAt: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val questionId: Long,
    val content: String,
    val updatedAt: Long,
)
```

设计要点：

1. **会话题序用快照 JSON**（`questionIds`）而非每次重算：错题重练过程中题目被「掌握」移出错题本时，本会话队列不受影响；恢复会话零成本。随机洗牌只发生在快照生成时（seeded Fisher–Yates），`randomSeed` 持久化用于测试与追溯。
2. **每个会话每题一行 `session_answers`**（unique 索引 + UPSERT）：答题卡四态（未做/对/错/浏览）直接由该表聚合查询；模式 A/B 切换的状态恢复也从这里读。
3. 同一 subject 同时只允许一个 `ACTIVE` 会话；开新会话将旧会话置 `COMPLETED`。恢复弹窗列出所有 ACTIVE 会话（通常 ≤1）。
4. **级联删除**：删除 subject → 其 questions → wrong_answers/favorites/notes/session（Room `@Relation` 不自动级联，DAO 中用 `@Transaction` + 显式 DELETE，或外键 `onDelete = CASCADE`）。

### 4.3 版本与迁移

- `exportSchema = true`，schema JSON 提交至 `app/schemas/`（Room 配置 `room.schemaLocation`）。
- v1.0 发布前允许破坏性变更（`fallbackToDestructiveMigration` 仅 debug）；发布后一律写 `Migration` 并配套 `MigrationTestHelper` 测试。

## 5. 关键流程

### 5.1 CSV 导入管线

```
SAF 选文件 (ACTION_OPEN_DOCUMENT, mime text/csv,text/plain)
  → ContentResolver 读字节流
  → EncodingDetector.detect(bytes):
      ① BOM: EF BB BF → UTF-8；FF FE / FE FF → UTF-16
      ② 无 BOM：UTF-8 严格解码（CodingErrorAction.REPORT）成功 → UTF-8
      ③ 失败 → GB18030 严格解码（GBK/GB2312 超集）
      ④ 仍失败/含大量 U+FFFD → 报错（提示另存为 UTF-8）
      ⑤ 兜底用 android.icu.text.CharsetDetector 交叉验证
  → CsvQuestionParser（kotlin-csv）: 表头映射 → 逐行解析
  → CsvValidator:
      必填 content + correct_answer；answer 归一化（大小写/全半角/对错别名）
      type 白名单过滤（blank 等不支持题型计入"跳过数"，不报错）
      收集逐行错误 {lineNo, reason}，总行数统计
  → 预览页（前 50 题 + 统计 + 错误列表），用户命名
  → 确认入库：单事务写入 subjects + questions（chunk 500/批），
     完成后更新 questionCount；失败整体回滚
```

实现约束：解析、校验、写库全部在 `Dispatchers.IO`；大文件用 `callbackFlow` 上报进度（解析中 x% → 写库中 y%）。

### 5.2 会话保存与恢复

**保存触发（三级）**：

| 触发点 | 实现 |
|---|---|
| 切题/作答/切模式 | ViewModel 即刻调用 `sessionRepo.save()`（suspend，IO） |
| 每 5 秒 | `viewModelScope` 定时任务；**脏标记**无变化则只跳过（仍需偶尔刷新 `lastActiveAt`） |
| 进入后台/退出 | `lifecycle-process` 的 `ON_STOP` + Activity `onDestroy` 兜底 |

关键实现细节：**兜底保存必须跑在 application 级 CoroutineScope**（`SupervisorJob() + Dispatchers.IO`），不能挂在即将销毁的 `viewModelScope` 上，否则退出瞬间保存被取消。

**恢复流程**：App 启动 → HomeViewModel 查询 `status = ACTIVE` 会话 → `now - lastActiveAt > 7天` 的置 `EXPIRED`（顺带清理，不再提示）→ 其余弹恢复对话框（题库名/题号/时长/上次活跃时间）→ 三选项：继续（原样进入刷题页）/ 重新开始（置 COMPLETED，按同参数新开会话）/ 下次不再询问（写 DataStore 标志；题库入口保留「继续上次」）。

**时长统计**：定时保存时 `accumulatedMs += (now - lastTick)`，仅在前台累计，粒度 5s，足够展示。

### 5.3 模式 A/B 切换（状态保持）

UI 层维护 `perQuestionState: Map<questionId, QuestionUiState>`（selected / revealed / isCorrect）：

| 切换 | 实现步骤 |
|---|---|
| B → A | 保留当前题 local 状态；`revealed = true`；`reciteMode` 写会话 |
| A → B | 查当前会话 `session_answers` 中该题 `actionType = SELECTED` 的记录：有 → 恢复 selected/isCorrect/revealed；无 → 清空 selected、`revealed = false` |

> 决策依据（产品文档 §7.3）：状态记忆范围为**当前会话**；跨会话不恢复历史选择，防止翻记录试答案。

**判分与错题联动（模式 B，SELECTED 落库时同一事务）**：

```
判分: 单选/JUDGE 严格相等；多选(P1) 集合全等
错 → wrong_answers UPSERT: wrongCount+1, lastWrongAt=now,
     mastered=false, reviewCorrectCount=0（曾掌握的错题重新入本）
对 → 若该题存在于 wrong_answers 且未掌握: reviewCorrectCount+1;
     reviewCorrectCount >= 2 → mastered=true（自动移出错题本列表）
```

### 5.4 随机与专项题序生成

会话创建时一次性生成快照：

```
subset = 按 practiceType 取题集（顺序: orderIndex ASC；专项: 按 filterValue 过滤）
seed = 若 RANDOM/CATEGORY-随机: 随机 Long 并存入会话
洗牌: seeded Fisher-Yates（java.util.Random(seed)），仅对 subset
questionIds = JSON 快照存入会话
```

## 6. 导航（类型安全路由）

```kotlin
@Serializable data object Home
@Serializable data class ImportPreview(val uri: String)
@Serializable data class Study(
    val subjectId: Long,
    val practiceType: String,
    val filterValue: String? = null,
    val resumeSessionId: Long? = null,   // 恢复会话时传入
)
@Serializable data object WrongBook
@Serializable data object Favorites
@Serializable data object Settings
```

- 底栏 4 Tab（题库/错题本/收藏/设置）用 `NavHost` + `saveState/restoreState`；刷题页为独立全屏 destination，进入时隐藏底栏。
- 导入流程用 `LaunchedEffect` 请求 `ActivityResultContracts.OpenDocument`。

## 7. 刷题页组件分解（StudyScreen）

```
StudyScreen (Scaffold, 全屏)
├─ StudyTopBar        返回 / 题库名 / 模式切换区（练习方式下拉 + A/B SegmentedButton + 夜间开关）
├─ ProgressIndicator   "12/500" + LinearProgressIndicator
├─ QuestionPager       HorizontalPager(按 questionIds) —— 手势切题即切题保存
│  └─ QuestionCard
│     ├─ TypeChip(单选/判断) + CategoryChip(可选)
│     ├─ 题干 Text
│     ├─ OptionList     clickable(仅模式B未答) + 水波纹；判定后正确绿/错误红/其余灰
│     ├─ AnimatedVisibility(答案解析区)   expandVertically + fade
│     └─ NoteEntry(P1)  收藏按钮常驻右上
├─ BottomBar           上一题 / 下一题（首末题禁用态）
└─ AnswerCardSheet     ModalBottomSheet + LazyVerticalGrid 题号格（四色图例，点击跳转）
```

动画要求（产品 §7.3）：答案展开 `AnimatedVisibility` 平滑过渡；选项反馈用 Material3 state layer；切题默认走 Pager 自带滑动。

## 8. 主题与设置实现

- **深色模式三态**：DataStore 存 `SYSTEM/LIGHT/DARK` → `RepsTheme(darkTheme = 设置覆盖 ?: isSystemInDarkTheme())`。
- **字体缩放**：DataStore 存倍率（0.85/1.0/1.15/1.3）→ 在 App 根部用 `CompositionLocalProvider(LocalDensity provides Density(density, fontScale * userScale))` 全局生效，正文长文本自动换行已按此验证。
- **清除数据**：`RepsDatabase` 实例 `clearAllTables()` + 清 DataStore，二次确认对话框。

## 9. 性能与可靠性守则

1. 主线程零 DB IO：DAO 全部 `suspend` / `Flow`；列表用 Room Flow 自动响应。
2. 写库合并：同一题的会话状态 5s 内多次变化只在脏标记下写一次；`session_answers` 用 `@Upsert`。
3. 千题级题库：导入解析流式处理，写库单事务（SQLite 单事务万级行 <1s）；答题卡 `LazyVerticalGrid` 虚拟化。
4. 状态兜底：见 §5.2 三级保存 + application 级作用域。
5. ProGuard/R8：骨架当前 release 关闭优化；发布前开启并保留 Room/kotlinx-serialization 规则（骨架已有 `keepRules/rules.keep`）。

## 10. 测试策略

| 层 | 用例（示例） |
|---|---|
| 单元（JVM） | EncodingDetector：UTF-8 无/有 BOM、GBK、乱码文件；CsvParser：引号内逗号/换行、列缺失、id 重复、answer 归一化（`b`→`B`、`true`→`对`）；洗牌确定性（同 seed 同序）；错题状态机（错→进本；对×2→掌握；再错→回炉）；模式切换规则 |
| 仪器 | Room DAO CRUD + 级联删除 + `@Upsert`；Migration 测试（v2 起） |
| Compose UI | 模式 A↔B 切换后选中态保持；答题卡跳转；空状态展示 |
| 手工验收 | 按产品文档 §9 各阶段 DoD 走查；杀进程/后台恢复专项 |

## 11. 阶段 → 工程任务映射

| 阶段 | 工程任务 |
|---|---|
| P1 骨架 | 依赖入 toml；Room 建库（全部实体 DAO）；NavHost + 4 Tab；首页空状态；主题三态与字体缩放框架 |
| P2 刷题闭环 | EncodingDetector + CsvParser + Validator（含单元测试）；导入预览页；题库列表；StudyScreen 全组件；快照/洗牌；模式 A/B + 判分 |
| P3 状态闭环 | 三级保存 + 脏标记；恢复弹窗 + 过期清理；session_answers 落库与答题卡聚合；错题联动 + 错题本页；收藏 + 收藏页 |
| P4 打磨 | 深色/字体设置页；清数据；关于页；空态与错误文案；R8 与发布检查 |

## 12. 发布前检查清单

- [x] `applicationId` 定为 `io.github.coderirse.reps`，`namespace` 同步修改（2026-08-30 决策，Phase 1 落地）
- [x] `minSdk` 决策落地：26（2026-08-30 决策，Phase 1 落地）
- [x] release 签名密钥已生成（2026-08-30）：`keystore/reps-release.jks`（alias `reps-release`），密码存放于 `keystore/keystore.properties`；两者均已 gitignore，**严禁入库、务必异地备份**（丢失将无法发布更新）
- [ ] 接入 `signingConfig`：build 脚本读取 `keystore/keystore.properties`，存在则签 release，缺失则回退 debug 签名（本地构建不因缺密钥而失败）
- [ ] 开启 R8 优化 + 确认 keep 规则
- [ ] `AndroidManifest` 无 `INTERNET` 权限（CI 中加断言：解析 merger 输出检查）
- [ ] MIT LICENSE 文件 + 源码头注释
- [ ] README：slogan、截图、CSV 格式说明、下载（GitHub Releases / F-Droid 计划）
- [ ] 版本号 `versionCode/versionName` 策略（1.x.y，SemVer）
