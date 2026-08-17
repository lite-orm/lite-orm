# LiteORM 推广与生态路线图设计

**日期：** 2026-08-17  
**状态：** 已确认设计，待拆分实施计划

## 1. 目标

LiteORM 下一阶段的目标不是继续横向复制 MyBatis 功能，而是建立一个可公开发布、可迁移、可学习、可诊断的采用闭环：

> 一个普通 Spring Boot 团队能够从公共 Maven 仓库引入 LiteORM，在半天内迁移一个只使用常见注解/XML、动态 SQL 和 Spring 事务的真实 MyBatis 模块，并能够仅依赖官方文档和示例完成排错。

该目标要求同时完成四个层次：

1. core 和 Spring 集成达到公开发布所需的正确性与稳定性；
2. runtime、processor、Maven、Gradle 和发布产物边界清晰；
3. XML、注解、迁移工具和示例覆盖真实 MyBatis 迁移路径；
4. GitHub Pages、API 文档、错误目录和版本发布流程形成长期维护入口。

## 2. 基本原则

### 2.1 保持 LiteORM 的轻量模型

LiteORM 保持以下核心定位：

- 没有公开 `SqlSession`；
- 没有运行期 Mapper 代理；
- 没有运行期 XML 和 OGNL 解释；
- 没有一级或二级 ORM 缓存；
- 没有自动 count、自动 SQL 包装或框架 `Page<T>`；
- 生成 Mapper 只依赖一个 `SqlExecutor`；
- `JdbcSqlExecutor` 控制固定 JDBC 生命周期；
- Spring 只接管 IOC、连接参与和事务边界；
- Mapper 与 DataSource 域保持一对一关系。

### 2.2 不以 MyBatis API 数量作为完成度

MyBatis 能力分为三类：

1. LiteORM 应直接支持的常见 SQL Mapper 能力；
2. 应由迁移工具转换为 LiteORM 静态表达的能力；
3. 与 session、缓存、懒加载、运行期插件或复杂对象图绑定，明确不进入 LiteORM core 的能力。

### 2.3 先冻结契约，再构建工具

实施顺序必须遵守：

```text
Core 正确性
    -> API 冻结
    -> Runtime/Processor 拆包
    -> Maven/Gradle 消费验证
    -> XML DTD 和正式 XML 契约
    -> Spring Starter 收口
    -> 公共预览版发布
    -> 官方站点和完整示例
    -> MyBatis 迁移工具
    -> DB to Mapper Generator
    -> IDE 和其他生态
```

迁移工具和 generator 不得基于仍会变化的注解、XML 或 artifact 结构提前实现。

## 3. 优先级总览

| 优先级 | 阶段 | 目标 |
| --- | --- | --- |
| P0 | 公开 GA 底座 | 正确性、API、拆包、构建工具、Spring、benchmark、发布治理 |
| P1 | 迁移与文档闭环 | LiteORM DTD、XML/注解契约、GitHub Pages、examples、MyBatis migration |
| P2 | DB-first 开发效率 | 数据库元数据到 model/Mapper/XML 或注解的受控生成 |
| P3 | 生态扩展 | IDE、Kotlin、Micrometer、OpenTelemetry、GraalVM 和更多宿主框架 |

## 4. P0：公开发布底座

### 4.1 Core 正确性收口

公开 artifact 和 API 冻结前必须完成：

#### 4.1.1 Interceptor 最终状态

当前 `JdbcSqlExecutor` 在资源 cleanup 前调用 `afterSuccess`。如果 cleanup 随后失败，interceptor 已收到成功，但 Mapper 最终抛出 `SqlExecutionException`。

目标顺序：

```text
execute/read/map
    -> cleanup
    -> 生成最终 ExecutionOutcome
    -> afterSuccess 或 afterFailure
    -> 返回或抛出最终结果
```

要求：

- cleanup failure 必须进入最终 failure outcome；
- 原始执行失败仍是 primary failure；
- cleanup failure 继续作为 suppressed failure；
- terminal interceptor 自身失败不能覆盖 SQL 或 cleanup failure；
-普通执行和 cursor 执行保持一致语义。

#### 4.1.2 Mapper 方法重载

当前生成的 plan factory、provider 字段名和 statement ID 只编码方法名。XML statement ID 也无法可靠表达重载方法。

首个公开版本明确禁止 Mapper SQL 方法重载：

- 编译期检测同名 Mapper 方法；
- 给出 Mapper 接口和冲突签名；
- 不通过签名哈希或数字后缀隐藏该限制；
- Object 方法和被覆盖的同签名继承方法不误报。

#### 4.1.3 编译器诊断

移除编译器中的：

- `System.err.println(...)`；
- `printStackTrace()`；
- XML 解析失败后返回 null 并伪装成 statement 缺失的行为。

所有失败通过 javac `Messager` 输出，并尽可能附着到 Mapper 方法。XML 诊断至少包含：

- XML 资源路径；
- Mapper namespace；
- statement ID；
- 标签或属性名；
- 原始 parser 错误摘要。

#### 4.1.4 XML 安全解析

抽取单一内部安全 XML parser factory，并应用于：

- Mapper XML；
- 注解 `<script>`；
- migration tool 的 XML 扫描；
-未来 generator 读取的 XML 配置。

统一禁用：

- 外部通用实体；
- 外部参数实体；
- 非本地外部 DTD 加载；
- XInclude；
- 未声明的网络资源访问。

LiteORM 自有 DTD 通过内置 resolver 离线解析。

#### 4.1.5 常用 JDBC 类型矩阵

当前支持的数值、字符串、布尔、enum、`BigDecimal`、`LocalDate`、`LocalDateTime`、`Instant` 和 `byte[]` 保持稳定。

公开版本前必须决策并测试：

- `UUID`；
- `LocalTime`；
- `OffsetDateTime`。

每个支持类型必须覆盖：

- 方法参数绑定；
- 标量返回；
- record 返回；
- JavaBean 返回；
- PostgreSQL；
- MySQL；
- H2 development fixture。

不支持类型必须在编译期要求显式 `ParameterBinder` 或 `RowMapper`。

#### 4.1.6 Standalone 与 Spring 生命周期一致性

建立共享 characterization suite，验证：

- connection acquire/release；
- prepare/bind/execute/read/map/cleanup 顺序；
- generated key；
- batch；
- cursor；
- interceptor 顺序；
- cleanup failure；
- transaction-bound connection participation。

Spring 只允许替换 `ConnectionHandleFactory` 和事务宿主，不允许复制或重写 `JdbcSqlExecutor`。

#### 4.1.7 MySQL Testcontainers 完整矩阵

MySQL 不只作为 core 的单个兼容性测试存在，而是作为公开发布前的跨模块 integration gate：

- 固定使用仓库 property 管理的 MySQL 8.4 LTS container image；
- 建立非发布的 test-support module，统一 container lifecycle、DataSource、schema 初始化和诊断输出；
- core 覆盖 JDBC 类型、generated key、batch、cursor、事务和 cleanup failure；
- processor 覆盖 annotation/XML 生成代码在真实 MySQL 上的编译与执行；
- Spring Starter 覆盖 physical MySQL DataSource、`@Transactional` 和 rollback；
- generator 覆盖 MySQL metadata、identity、nullable 和类型映射；
- GitHub Actions 使用独立 MySQL Testcontainers job，Docker 不可用时本地允许明确 skip，发布 CI 不允许 skip。

Testcontainers 及 MySQL 测试支持不得进入任何用户 runtime artifact。

### 4.2 API 冻结

在拆包和发布前重新审核 `PublicApiSurfaceTest`：

- 注解和应用扩展契约保持 public；
- 编译器 AST、parser、model 和 generator 保持 internal；
- 判断 `JdbcSqlExecutor`、`SimpleTransactionDomainGuard` 等实现类是否必须作为长期 public API；
- 为每个 public 类型补齐英文 Javadocs；
- 明确 thread-safety、nullability、ownership 和 failure contract；
- 引入 API binary compatibility gate，禁止非预期破坏。

### 4.3 Runtime 与 Processor 拆包

目标模块：

```text
lite-orm-core
    runtime annotations
    public API
    JdbcSqlExecutor
    standalone transaction
    built-in interceptors

lite-orm-processor
    LiteOrmProcessor
    CompilePipeline
    SQL parsers
    AST and compilation model
    JDK-only source generator
    processor service metadata

lite-orm-spring-boot-starter
    depends on lite-orm-core only at runtime
```

约束：

- 所有公开 artifact dependency tree 不包含 FreeMarker 或其他模板引擎；
- Starter runtime dependency tree 不包含 compiler implementation；
- processor 通过 Maven annotation processor path 和 Gradle `annotationProcessor` 引入；
- processor 版本必须与 core 版本一致；
- processor 生成代码只能引用 core public API。

processor 使用专用 `JavaSourceGenerator` 和小型 source writer 直接渲染 package、imports、class、fields、constructor、Mapper methods 与 execution plan factories。替代实现必须满足：

- 与现有 golden source 和 compilation tests 保持语义等价；
- 输出顺序、换行、缩进和 import 顺序确定；
- 不实现通用模板语言、表达式执行或运行期模板加载；
- 删除 `FreemarkerCodeGenerator`、`mapper-impl.ftl`、FreeMarker dependency 和相关配置；
- 生成失败通过现有 compiler diagnostics 报告，不泄漏不完整源码。

### 4.4 Maven 与 Gradle 外部消费夹具

保留独立 Maven consumer，并新增独立 Gradle consumer：

```text
lite-orm-examples/external-maven-processor
lite-orm-examples/external-gradle-processor
```

两个项目均保持在根 reactor 外，模拟真实外部项目，并验证：

- clean local repository；
- processor 显式配置；
- generated sources；
- generated Mapper 的 downstream compilation；
- XML resource discovery；
- core runtime 不依赖 processor；
- JDK 显式 annotation processing 配置。

### 4.5 Spring Boot Starter 收口

删除当前未被运行时消费的配置项：

- `slow-query-threshold`；
- `slow-query-monitoring`；
- `sql-logging`；
- `log-parameters`；
- `audit-enabled`；
- `audit-async`。

日志、慢查询和审计继续通过显式 `ExecutionInterceptor` Bean 提供。

Starter 保留：

- `enabled`；
- `mapper-bindings[].package-name`；
- `mapper-bindings[].data-source`。

建立 Spring Boot 兼容矩阵：

- 选择一个仍受支持的 Spring Boot 3.x 作为编译基线；
- 验证当前 Spring Boot 4.x；
- 同一生成 Mapper 和 core runtime 在两个测试矩阵中复用；
- 不通过反射兼容大量历史 Spring 版本。

### 4.6 Benchmark 修正

正式宣传数据前必须：

- 对齐 Direct JDBC、LiteORM 和 MyBatis 的 transaction/auto-commit 边界；
- timing、GC allocation 和 JFR 分开运行；
- 所有场景先验证结果一致；
- 增加 Spring transaction 场景；
- README 只展示可复现且边界一致的数据；
- benchmark 文档明确硬件、JDK、数据库、fork、warmup 和 measurement；
- 不使用带 profiler 的 timing 替代正式时间基线。

### 4.7 发布工程与仓库治理

公开预览版本前补齐：

- LICENSE；
-项目 URL、SCM、developer 和 license POM metadata；
- source JAR；
- Javadocs JAR；
- artifact signing；
- Maven Central 发布配置；
- GitHub Actions build/test/release；
- PostgreSQL/MySQL CI matrix；
- MySQL Testcontainers core/processor/Starter/generator matrix；
- API compatibility check；
- dependency update automation；
- CHANGELOG；
- SECURITY；
- CONTRIBUTING；
- issue templates；
- pull request template；
- release tag 和 release notes 流程。

公开发布前确定：

- 最终 GitHub organization 和 repository URL；
- Maven `groupId` namespace 所有权；
- artifact 命名；
- `0.1.0` 公开预览与 `1.0.0` GA 的边界。

推荐先发布 `0.1.0`，在真实迁移反馈后冻结 `1.0.0`。

## 5. P1：XML 与注解契约

### 5.1 LiteORM Mapper DTD

定义版本化 LiteORM DTD：

```xml
<!DOCTYPE mapper
    PUBLIC "-//LiteORM//DTD Mapper 1.0//EN"
    "https://lite-orm.github.io/lite-orm/dtd/liteorm-mapper-1.0.dtd">
```

要求：

- DTD 描述 LiteORM 实际支持的 XML 子集；
- processor JAR 内置同版本 DTD；
- parser 通过 public ID/system ID 映射到本地资源；
- 编译不依赖网络；
- GitHub Pages 发布不可变版本 URL；
- MyBatis DTD 可作为 migration input 被识别；
- migration tool 输出 LiteORM DTD；
- DTD 负责 IDE completion 和基础结构，processor 继续负责语义校验。

### 5.2 当前 XML 支持基线

稳定支持：

- statements：`select`、`insert`、`update`、`delete`、LiteORM `batch`；
- fragments：`sql`、`include`；
- dynamic tags：`if`、`choose`、`when`、`otherwise`、`trim`、`where`、`set`、`foreach`、`bind`；
- `resultType`；
- XML 优先于同方法 SQL annotation，并输出 warning；
- 受控 OGNL 风格表达式编译为 Java；
- `${...}` 始终拒绝。

### 5.3 P1 XML 增量

#### 5.3.1 `foreach index`

为 `ForeachNode` 增加可选 index，并覆盖：

- `List<T>`；
-数组；
- `Iterable<T>`；
- item 与 index 同时用于 SQL/参数表达式；
-嵌套 foreach 的变量作用域；
-变量名冲突诊断。

`Map` foreach 不自动并入该任务，必须先定义 key/value 语义。

#### 5.3.2 XML generated key

支持受控 XML 属性：

```xml
<insert id="insert" useGeneratedKeys="true" keyColumn="id">
```

语义与 `@GeneratedKey("id")` 一致：

- generated key 通过 Mapper 方法返回；
- 不向输入对象回写 `keyProperty`；
- 只支持一个明确键列；
- dynamic SQL、batch 和 provider 继续不支持 generated key，除非独立设计更新契约。

#### 5.3.3 Statement 属性审计

盘点 MyBatis 项目中常见的：

- `timeout`；
- `fetchSize`；
- `resultSetType`。

只有能够静态编译到 `StatementOptions` 且有明确 JDBC 语义的属性才允许加入。不创建综合型 `@Options`。

#### 5.3.4 XML 完整性校验

增加：

- namespace 必须等于 Mapper 全限定名；
- duplicate statement ID 编译失败；
- statement 必须对应 Mapper 方法；
- Mapper SQL 方法不允许重载；
-未知 statement attribute 编译失败；
-孤立 `sql` fragment 和 include cycle 给出确定性诊断。

### 5.4 明确不支持的 XML

以下能力不进入 core：

-复杂 `resultMap`；
- association；
- collection；
- discriminator；
- nested select；
- lazy loading；
- cache；
- cache-ref；
- callable statement；
-多 result set；
-运行期 databaseId 切换；
-基于 `${}` 的 include property substitution。

简单扁平 `resultMap` 由迁移工具转换为：

1. SQL column alias；
2. record/JavaBean `@Column`；
3. 少数情况 `@UseRowMapper`。

### 5.5 注解契约

稳定注解：

- `@Mapper`；
- `@Select`；
- `@Insert`；
- `@Update`；
- `@Delete`；
- `@Batch`；
- `@Param`；
- `@Column`；
- `@GeneratedKey`；
- `@UseSqlProvider`；
- `@UseParameterBinder`；
- `@UseRowMapper`。

不照搬 MyBatis 注解：

| MyBatis 能力 | LiteORM 迁移策略 |
| --- | --- |
| `@Results`、`@Result`、`@ResultMap` | SQL alias、`@Column`、`@UseRowMapper` |
| `@Options` | 拆成有明确需求的窄能力；默认不新增 |
| `@SelectKey` | `@GeneratedKey`、显式 SQL 或 Provider |
| `@InsertProvider` 等 | 统一为 `@UseSqlProvider` |
| `@One`、`@Many` | Service 层显式查询 |
| `@CacheNamespace` | Spring Cache 或业务缓存 |
| `@Lang` | 不支持运行期 language driver |
| `@MapKey` | 先返回 `List<T>`，真实需求后再评估 |
| `@Flush` | 不存在 session batch flush |
| `@ResultType` | Mapper 方法返回类型即编译期结果类型 |

## 6. P1：Examples

目标结构：

```text
lite-orm-examples/
├── basic-mapper
├── spring-boot-single-datasource
├── spring-boot-multi-datasource
├── xml-feature-showcase
├── mybatis-migration
├── external-maven-processor
└── external-gradle-processor
```

### 6.1 `basic-mapper`

继续覆盖：

- annotation CRUD；
- scalar、record、JavaBean；
- batch；
- generated key；
- `SqlProvider`；
- `ParameterBinder`；
- `RowMapper`；
- cursor；
- standalone transaction；
- rollback-only；
- SQL pagination。

### 6.2 `xml-feature-showcase`

覆盖全部正式 XML：

- LiteORM DTD；
- CRUD 和 batch；
- `sql/include`；
- `if/choose/trim/where/set`；
- `foreach item/index`；
- `bind`；
- XML generated key；
-动态分页参数；
- XML error examples 与诊断说明。

### 6.3 `spring-boot-single-datasource`

覆盖：

- `mapper-bindings`；
- Mapper Bean 默认名称；
- IOC 构造器注入；
- `@Transactional`；
- ordered interceptor Beans；
- physical DataSource；
- integration test。

### 6.4 `spring-boot-multi-datasource`

覆盖：

- 两个不重叠 Mapper 包；
- 两个 DataSource；
- 两个 transaction manager；
-正确 transaction manager；
-错误 transaction manager 的 domain mismatch；
- routing DataSource 边界说明。

### 6.5 `mybatis-migration`

提供 before/after 对照：

```text
before/
    MyBatis annotation Mapper
    MyBatis XML Mapper
    Spring configuration

after/
    LiteORM annotation Mapper
    LiteORM XML Mapper
    mapper-bindings
    migration report
```

## 7. P1：MyBatis Migration Tool

迁移工具分为两个阶段。

### 7.1 Compatibility Scanner

输入 MyBatis 项目，输出：

- 可自动迁移；
-需要人工确认；
- LiteORM 明确不支持；
-建议保留 MyBatis；
-建议改为 raw JDBC。

扫描范围：

- Java annotation imports；
- Mapper XML DTD、tags 和 attributes；
- OGNL；
- `${}`；
- resultMap；
- provider；
- plugin；
- cache；
- nested query；
- generated key；
- Spring Mapper scan 和 DataSource 配置。

报告必须包含文件、行号、Mapper、方法、分类和建议动作。

### 7.2 Safe Rewriter

只自动转换确定性内容：

- MyBatis annotations import 到 LiteORM annotations；
-基础 CRUD annotations；
- `@Param`；
- provider annotations 到 `@UseSqlProvider` 骨架；
- MyBatis DTD 到 LiteORM DTD；
-受支持动态 SQL；
-简单 flat resultMap 到 SQL alias 或 `@Column`；
- Spring Mapper scan 到 `mapper-bindings` 提示。

复杂 resultMap、插件、cache、nested select 和 arbitrary OGNL 只报告，不自动猜测。

第一版实现为一个 CLI 模块。只有 Java 源码转换复杂度证明需要时，再拆出 OpenRewrite recipes。

## 8. P1：GitHub Pages 和文档

官方站点包含：

```text
Quick Start
Why LiteORM
Architecture
Annotation Reference
XML Reference
Dynamic SQL
Result Mapping
Transactions
Multiple DataSources
Spring Boot
Interceptors
MyBatis Migration
Error Catalog
Database Compatibility
Benchmarks
Examples
API Javadocs
Changelog
```

要求：

- 文档源保存在仓库；
- GitHub Actions 自动构建 GitHub Pages；
-站点发布版本化 DTD；
-站点链接版本化 Javadocs；
- README 保持 Quick Start 和架构摘要，不复制完整参考文档；
- benchmark 页面保留可复现命令和方法学；
-错误目录按 compiler/runtime/transaction/Spring 分类。

## 9. P2：DB to Mapper Generator

第一版 generator 只提供稳定、可 diff 的 DB-first 骨架生成：

```text
Database metadata
    -> record 或 JavaBean
    -> Mapper interface
    -> annotation SQL 或 LiteORM XML
```

支持：

- PostgreSQL；
- MySQL；
- schema/table include/exclude；
- column 到 Java type；
- snake_case 到 camelCase；
- primary key；
- identity/generated key；
- nullable；
-基础 CRUD；
-按主键查询；
-显式 `LIMIT/OFFSET` 分页方法；
- annotation 或 XML 输出模式；
- dry-run；
-已存在文件保护；
- deterministic output。

不支持：

-自动生成复杂 join；
-自动关系映射；
-业务 search DSL；
- active record；
-通用 Repository；
-覆盖人工修改文件。

## 10. P3：生态候选

只有真实用户需求出现后才考虑：

- IntelliJ Mapper/XML 导航与 completion；
- VS Code language server；
- Kotlin/KSP；
- Micrometer interceptor module；
- OpenTelemetry interceptor module；
- GraalVM native-image；
- Quarkus/Micronaut integration；
- Java 17 支持；
- database dialect helper；
- generator 模板扩展；
- benchmark dashboard。

## 11. 明确不进入本路线图

- ORM 一级缓存；
- ORM 二级缓存；
- `SqlSession`；
-自动分页 count；
-框架 `Page<T>`；
-运行期 SQL 重写插件；
-复杂对象图和 lazy loading；
-分布式事务；
-同一 Mapper 动态绑定多个 DataSource；
-完整 MyBatis 插件兼容；
-完整 MyBatis XML/annotation API 镜像。

## 12. 发布里程碑

### 12.1 `0.1.0` Public Preview

必须完成：

- P0 全部任务；
- LiteORM Mapper DTD 和离线 resolver；
-核心文档站点；
- single DataSource Spring example；
- XML feature showcase 的正式支持子集；
- Maven 和 Gradle external fixtures；
- Maven Central 发布；
-公平 benchmark；
- XML/annotation compatibility matrix。

### 12.2 `0.2.0` Migration Preview

必须完成：

- XML generated key 和 `foreach index` 增量；
- multi DataSource Spring example；
- migration scanner；
- migration before/after example。

### 12.3 `0.3.0` Migration Automation

必须完成：

- safe rewriter；
-真实开源 MyBatis 项目迁移验证；
-迁移报告稳定格式；
- migration documentation。

### 12.4 `1.0.0` GA

必须满足：

- core 和 starter public API 冻结；
-至少两个非仓库内部项目完成试用；
- Maven/Gradle/Spring 文档完整；
-兼容性和 migration contract 稳定；
-所有已发布 DTD URL 永久可用；
-数据库和 Spring Boot CI matrix 稳定；
-无未关闭的 P0 correctness issue。

DB generator 不强制阻塞 `1.0.0`，可以作为独立 `0.x` 工具发布。

## 13. 验收标准

本路线图完成时应满足：

1. 所有用户 artifact 和 processor classpath 均不包含 FreeMarker，用户 runtime classpath 不包含 processor；
2. Maven 和 Gradle 外部项目都能 clean build；
3. Spring Boot single/multi DataSource 示例可直接运行；
4. LiteORM XML 使用自有版本化 DTD，并离线编译；
5. 所有支持 XML/annotation 能力都有 example 和 compatibility test；
6. migration scanner 能准确区分自动迁移、人工迁移和不支持能力；
7. Maven Central artifacts 包含完整 metadata、sources、Javadocs 和签名；
8. GitHub Pages 提供 Quick Start、参考文档、错误目录、benchmark 和 Javadocs；
9. benchmark 三方事务边界一致且可以复现；
10. core 不引入 session、cache、自动分页或通用插件链。
