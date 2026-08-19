# LiteORM Roadmap 双向并行分工

> 配套文档:`docs/superpowers/plans/2026-08-17-liteorm-adoption-roadmap.md`(19 个 Task 的实施计划)与
> `docs/superpowers/specs/2026-08-17-liteorm-adoption-roadmap-design.md`(设计稿)。
> 本文只描述**如何在两名开发者之间并行推进**该路线图,不改变任何 Task 的验收标准。

**开发者:**

- **qingbozhang** —— 经验丰富的老手,负责 core 编译器/运行时主干。
- **yywang** —— 热情的开源开发者,负责全部独立模块,含两大核心工具模块。

## 1. 目标:双向开工

两条 lane **尽量不产生关联、互不阻塞**:各自第一天就有数周的独立跑道,中途只有少数几个"延迟绑定"的同步点。分工**沿模块/目录边界切**,而不是沿单个 Task 内部的测试/实现接缝切——后者会让两人争抢同一个文件。

## 2. 目录归属(只有归属者改该目录的 main 代码)

| 归属 | 目录 |
| --- | --- |
| **qingbozhang** | `lite-orm-core/src/main`(`runtime` + `org.liteorm.compile`)、`lite-orm-processor/`、核心 `pom.xml`、LiteORM DTD 离线 resolver、Revapi 配置 |
| **yywang** | `lite-orm-spring-boot-starter/`、`lite-orm-benchmarks/`、`lite-orm-test-support/`、`lite-orm-migration/`、`lite-orm-generator/`、`lite-orm-examples/`、`docs/site/`、`.github/` 与根治理文件 |

**唯一物理接触点:**

1. 根 `pom.xml` 的 `<modules>` 列表 —— 各自追加自己的模块。
2. `lite-orm-core/src/test` —— yywang 因 Task 6 只**新增** MySQL 测试文件,不改 qingbozhang 的既有测试。

约定"**只增不改他人行**",这两处即基本零冲突。

## 3. 任务分工

### 3.1 qingbozhang —— core 主干(内部串行)

这些 Task 都会改 `CompilePipeline`、SQL parsers、core `pom.xml` 等热点文件,归一个人维护才不互相打架:

```text
Task 1  JDBC 结果/cleanup 语义收口 + SecureXml 安全解析
  -> Task 4  用 JDK-only JavaSourceGenerator 替换 FreeMarker
  -> Task 5  拆分 lite-orm-core / lite-orm-processor
  -> Task 2  冻结 JDBC 类型矩阵 + standalone/Spring 生命周期契约
  -> Task 3  冻结 public API + Revapi 二进制兼容门禁
  -> Task 12 XML P1 增量(foreach index、XML generated key、属性审计、完整性校验)
  -> Task 17 GA 证据收集 + 契约冻结
```

额外职责:

- Task 11 的 **LiteORM DTD 离线 resolver**(安全相关)。
- **Task 15 写盘安全设计的强制评审人**(见 3.3)。
- 各里程碑(0.1.0 / 0.2.0 / 0.3.0 / 1.0.0)发布门禁的最终签发。

### 3.2 yywang —— 全独立模块(三条并行线)

| 线 | 任务 | 性质 |
| --- | --- | --- |
| **核心模块** | Task 18 DB→Mapper 生成器;Task 14 兼容性扫描器;Task 15 Safe Rewriter;Task 16 真实项目迁移验证 | 真核心工程,住在 `lite-orm-generator` / `lite-orm-migration`,零共享文件 |
| **外围模块** | Task 8 Starter 收窄;Task 9 Benchmark 基线;Task 6 `lite-orm-test-support`;Task 7 外部消费夹具 | 各自独立模块 |
| **文档/治理** | Task 10 仓库治理文件;Task 19 P3 准入文档;Task 11 站点+示例;Task 13 multi-DS 与迁移示例 | 非 core 代码 |

`lite-orm-migration` 的扫描器(14)、改写器(15)、验证(16)归**同一人**,使整条迁移工具链在一个模块内闭环,耦合最小。

### 3.3 关于 Task 15 Safe Rewriter 的安全门

Safe Rewriter **会写入用户磁盘**,所以所有权与安全评审分离:

- **yywang 拥有实现**:确定性 AST 改写、幂等性(二次改写零变更)、"不猜"语义(复杂 resultMap / plugin / cache / nested select 只报告不改动)、`--dry-run` / `--apply` + 单次 `.liteorm-backup` 保护。
- **qingbozhang 是强制评审人**:`--apply` 与 backup 保护的设计必须经他签字;golden、幂等、backup-protection 测试全绿方可合并。

所有权交给 yywang,写盘安全门由 qingbozhang 守住。

## 4. 延迟绑定的同步点(共 3 个)

真正的跨 lane 依赖只有这三处,处理办法都是"先各干各的,最后做一次机械对接":

1. **拆包(Task 5)改 `annotationProcessorPaths`** 会波及 starter / examples / benchmarks 的 pom。
   yywang 先做**不依赖 processor path 的活**(Task 8、9、10、19、生成器、扫描器);qingbozhang 落地 Task 5 后,统一改一次 pom(纯机械)。
2. **API 冻结(Task 3)** 之前不定稿示例。
   yywang 照**当前 API** 把示例业务逻辑与测试写完,冻结后只做一遍"跟随新 API"的调整。
3. **DTD 与 XML 契约(Task 11 / 12)冻结** 影响扫描器/生成器的 XML 部分。
   Task 18 生成器几乎不依赖 XML 契约,**优先做**;Task 14 扫描器先按现有 XML 起骨架,契约冻结后校准 golden。

## 5. 与原计划编号的关系

本分工**打破了原 plan 中 Task 1→19 的严格串号**(原编号按发布里程碑串行)。这是并行开发的有意取舍:只要各里程碑的发布门禁仍由 qingbozhang 在合流点按原计划校验,并行推进不影响里程碑验收标准。原 plan 的每个 Task 的 Files、Steps 与验收命令保持权威,本文只叠加 owner 与并行次序。
