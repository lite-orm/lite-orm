# LiteORM Adoption Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `0.1.0`、`0.2.0`、`0.3.0` 和 `1.0.0` 四个发布门禁，将 LiteORM 建设成可公开发布、可由 Maven/Gradle 消费、可迁移 MyBatis、可通过官方文档独立排错的轻量编译期 ORM。

**Architecture:** 先在现有 runtime/JDBC 契约上完成正确性收口和 public API 冻结，以 JDK-only `JavaSourceGenerator` 替代 FreeMarker，再把 annotation processor 从 runtime artifact 中拆出。MySQL Testcontainers 通过非发布 test-support module 覆盖 core、processor/XML、Starter 和 generator；XML、示例、文档和迁移工具只依赖冻结后的 core/processor 契约，DB generator 作为独立 CLI 使用 JDBC metadata，不进入 core。

**Tech Stack:** Java 21、Maven、Gradle Wrapper、Javac Annotation Processing、JDBC、Spring Boot 3/4、JUnit 5、Testcontainers、MySQL 8.4 LTS、JMH、Maven Invoker、Revapi、Maven Central、GitHub Actions、MkDocs Material

---

## 实施边界与工作包

本设计覆盖多个可独立交付子系统。为避免跨模块大爆炸，本计划按发布里程碑组织，每个 Task 必须在独立提交中形成可运行、可测试的软件增量：

```text
0.1.0 Public Preview
  Task 1-11: correctness -> API -> source generator -> split -> MySQL matrix -> consumers -> starter -> benchmark -> release -> DTD/docs/examples

0.2.0 Migration Preview
  Task 12-14: XML increments -> multi-DS example -> migration scanner

0.3.0 Migration Automation
  Task 15-16: safe rewriter -> real-project validation

1.0.0 GA and optional P2
  Task 17-19: GA evidence -> DB generator -> demand-gated P3 intake
```

执行约束：

- 不新增 `SqlSession`、runtime Mapper proxy、ORM cache、自动 count、`Page<T>` 或通用 SQL 重写插件链。
- `lite-orm-core` 不得依赖 processor、FreeMarker、Spring 或 migration/generator 模块。
- `lite-orm-processor` 不得依赖 FreeMarker 或其他通用模板引擎。
- `lite-orm-processor` 生成的源码只能引用 `lite-orm-core` 的 public API。
- 每个发布门禁必须从空本地仓库验证外部 Maven/Gradle consumer，不能只依赖 reactor 内构建。
- Docker 不可用时允许数据库 matrix 测试跳过，但发布 workflow 必须在带 Docker 的 GitHub runner 上通过 PostgreSQL/MySQL 测试。

### Task 1: 收口 JDBC 最终结果与编译器失败语义

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/LiteOrmProcessor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/XmlBasedSqlParser.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/compile/SecureXml.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/AnnotationBasedSqlParser.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcCursorExecutionTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/UnsupportedMapperSignatureCompilationTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/compile/SecureXmlTest.java`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/MalformedXmlMapper.xml`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/DoctypeXmlMapper.xml`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/ParameterEntityXmlMapper.xml`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/ExternalDtdXmlMapper.xml`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/XIncludeXmlMapper.xml`
- Create: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/ExternalSchemaXmlMapper.xml`

- [x] **Step 1: 写 cleanup 后才发送 terminal callback 的失败测试**

在 `JdbcSqlExecutorTest` 和 `JdbcCursorExecutionTest` 中增加场景：执行成功但 `ResultSet.close()`、`PreparedStatement.close()` 或 `ConnectionHandle.close()` 失败时只收到一次 `afterFailure`；SQL 执行失败保持 primary，cleanup failure 位于 `getSuppressed()`；terminal interceptor 抛错不覆盖最终 SQL/cleanup failure。

```java
assertEquals(List.of("before", "failure:CLEANUP"), events);
SqlExecutionException failure = assertThrows(SqlExecutionException.class, () -> executor.execute(plan));
assertEquals("execute failed", failure.getCause().getMessage());
assertEquals("statement close failed", failure.getCause().getSuppressed()[0].getMessage());
```

- [x] **Step 2: 运行 JDBC 定向测试并确认 RED**

Run:

```bash
mvn -pl lite-orm-core -Dtest=JdbcSqlExecutorTest,JdbcCursorExecutionTest test
```

Expected: 新增用例失败，现状会在 cleanup 前发送 `afterSuccess` 或产生错误的 primary/suppressed 关系。

- [x] **Step 3: 统一普通执行与 cursor 的 outcome finalization**

在 `JdbcSqlExecutor` 中将资源关闭、最终 `ExecutionOutcome` 创建和 terminal callback 分成三个明确阶段；使用一个私有 helper 合并 primary/cleanup failure，并在 cleanup 完成后调用 `notifySuccess` 或 `notifyFailure`。callback 自身异常逐个记录但不改变业务结果。

```java
private Throwable mergeFailure(Throwable primaryFailure, Throwable cleanupFailure) {
    if (primaryFailure == null) {
        return cleanupFailure;
    }
    if (cleanupFailure != null && cleanupFailure != primaryFailure) {
        primaryFailure.addSuppressed(cleanupFailure);
    }
    return primaryFailure;
}
```

- [x] **Step 4: 写 Mapper 方法重载与 XML parser 诊断测试**

增加 compile-testing fixture，覆盖同接口重载、继承同签名覆盖、Object/default 方法、XML malformed、namespace/statement 上下文和 XXE。重载错误必须同时包含 Mapper 全限定名及两个冲突签名；XML 错误必须包含资源路径、namespace、statement ID 和 parser 摘要。

```java
assertFailure(result,
    "Mapper SQL method overloading is not supported",
    "example.OverloadedMapper#find(java.lang.String)",
    "example.OverloadedMapper#find(long)");
assertFailure(result, "MalformedMapper.xml", "example.MalformedMapper", "find", "XML parse failed");
```

- [x] **Step 5: 引入单一安全 XML factory 并接入两种 parser**

`SecureXml` 负责配置 `DocumentBuilderFactory`、关闭 XInclude、外部实体和外部 DTD，并拒绝非 LiteORM 本地 resolver 的网络资源。`XmlBasedSqlParser` 不再吞掉异常或返回伪装的 statement missing；`AnnotationBasedSqlParser` 的 `<script>` 同样通过 `SecureXml` 解析。

```java
final class SecureXml {
    static DocumentBuilder newDocumentBuilder(EntityResolver resolver)
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(resolver);
        return builder;
    }
}
```

- [x] **Step 6: 通过 `Messager` 附着确定性诊断**

删除 `System.err.println(...)`、`printStackTrace()` 和无上下文 catch；让 parser 抛出包含 `resourcePath`、`namespace`、`statementId`、`tagOrAttribute` 的 package-private compile exception，由 `CompilePipeline` 在 Mapper method element 上调用 `messager.printMessage(Diagnostic.Kind.ERROR, message, method)`。

- [ ] **Step 7: 运行 core 测试并提交**

Run:

```bash
mvn -pl lite-orm-core test
rg -n 'System\.err|printStackTrace' lite-orm-core/src/main/java
```

Expected: Maven PASS；`rg` 无输出。

```bash
git add lite-orm-core
git commit -m "fix: finalize jdbc outcomes after cleanup"
```

### Task 2A: 冻结内置 JDBC 类型契约

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ResultValueConverters.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/JdbcTypeCompilationTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/JdbcTypeRuntimeTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/database/AbstractDatabaseCompatibilityTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/database/PostgresCompatibilityTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/database/MySqlCompatibilityTest.java`
- Modify: `lite-orm-core/src/test/resources/database/schema.sql`
- Modify: `docs/core-ga-contract.md`

- [ ] **Step 1: 把支持范围写成可验证契约**

内置支持 `UUID`、`LocalTime`、`OffsetDateTime`，但只承诺以下语义：PostgreSQL/H2 UUID 使用原生类型，MySQL UUID 使用 `CHAR(36)`，`BINARY(16)` 使用自定义 binder/mapper；`LocalTime` 按列精度保留；`OffsetDateTime` 只保证 instant 一致，不保证原 offset 一致。

- [ ] **Step 2: 写最小 RED 矩阵**

`JdbcTypeCompilationTest` 验证三种类型可生成 scalar/record/JavaBean 映射，且不支持的结果类型仍要求 `RowMapper`。`JdbcTypeRuntimeTest` 用 H2 快速验证读写与 null。PostgreSQL/MySQL 共享 contract 用一个包含全部类型的对象验证驱动差异，不做“每种类型 × 每种返回形态”的重复组合。

```java
assertEquals(expectedUuid, actual.uuid());
assertEquals(expectedLocalTime, actual.localTime());
assertEquals(expectedOffsetDateTime.toInstant(), actual.offsetDateTime().toInstant());
```

- [ ] **Step 3: 实现有证据的最小转换**

`JdbcSqlExecutor` 继续使用当前 `setObject`/`getObject`，不为 typed `getObject(type)` 扩展 `ExecutionPlan`。`ResultValueConverters` 只增加测试驱动实际需要的 `UUID`、`LocalTime` 和 `OffsetDateTime` 转换；不增加推测性的通用字符串时间解析。

- [ ] **Step 4: 验证并更新契约**

```bash
mvn -pl lite-orm-core -Dtest=JdbcTypeCompilationTest,JdbcTypeRuntimeTest test
mvn -pl lite-orm-core -Dtest=PostgresCompatibilityTest,MySqlCompatibilityTest test
```

Expected: 编译和 H2 运行测试 PASS；Docker 可用时 PostgreSQL/MySQL PASS，不可用时明确 skip。`docs/core-ga-contract.md` 记录 SQL 表示、null、精度、offset 语义和扩展点。

```bash
git add lite-orm-core docs/core-ga-contract.md
git commit -m "feat(core): define built-in jdbc type mappings"
```

### Task 2B: 锁定宿主连接与事务边界

**Files:**
- Modify: `docs/core-ga-contract.md`
- Modify: `docs/extensions.md`

- [ ] **Step 1: 复用现有 characterization 测试**

不新建 `JdbcLifecycleContract`、`StandaloneJdbcLifecycleTest` 或 `SpringJdbcLifecycleTest`。`JdbcSqlExecutorTest`、`JdbcCursorExecutionTest`、`SimpleTransactionTest`、`SpringTransactionTest` 和 `LiteOrmAutoConfigurationTest` 已分别覆盖 executor、cursor、Standalone 事务、Spring connection participation 以及 Starter 复用 `JdbcSqlExecutor`。

- [ ] **Step 2: 运行边界验证**

```bash
mvn -pl lite-orm-core -Dtest=JdbcSqlExecutorTest,JdbcCursorExecutionTest,SimpleTransactionTest test
mvn -pl lite-orm-spring-boot-starter -am test
rg -n 'prepareStatement|executeQuery|executeUpdate|getGeneratedKeys' lite-orm-spring-boot-starter/src/main/java
```

Expected: Maven PASS；`rg` 无输出。失败表示 Task 1 尚未满足现有契约，应回到 Task 1 修正，不新增第二套共享生命周期框架。

- [ ] **Step 3: 修正权威文档**

`docs/core-ga-contract.md` 记录 Task 1 后的最终 cleanup/outcome 顺序；`docs/extensions.md` 明确 host 只提供 connection participation 和 transaction ownership，prepare/bind/execute/read/map/cleanup 始终属于 `JdbcSqlExecutor`。

```bash
git add docs/core-ga-contract.md docs/extensions.md
git commit -m "docs: freeze jdbc host ownership boundaries"
```

### Task 3: 冻结 public API 并增加 binary compatibility gate

**Files:**
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/architecture/PublicApiSurfaceTest.java`
- Create: `docs/public-api.md`
- Modify: `lite-orm-core/pom.xml`
- Modify: `pom.xml`
- Modify: every public source file enumerated by `lite-orm-core/src/test/java/org/liteorm/test/architecture/PublicApiSurfaceTest.java`
- Create: `.github/workflows/api-compatibility.yml`

- [ ] **Step 1: 分类当前 public 类型**

保留 annotations、`org.liteorm.api` 扩展契约、`LiteOrm`、`JdbcAssembly`、`JdbcSqlExecutor` 和 standalone transaction 入口；`org.liteorm.compile` 全部迁出 core；仅被内部使用且没有构造稳定性承诺的实现类型改为 package-private 或在 `docs/public-api.md` 标记为 implementation API。

- [ ] **Step 2: 让 API surface test 只断言稳定包**

测试必须显式拒绝 compiler/parser/model/generator 类出现在 core artifact 中，并断言稳定 public class list 没有意外增删。

```java
assertFalse(publicTypes.stream().anyMatch(name -> name.startsWith("org.liteorm.compile.")));
assertTrue(publicTypes.contains("org.liteorm.api.SqlExecutor"));
assertTrue(publicTypes.contains("org.liteorm.jdbc.JdbcSqlExecutor"));
```

- [ ] **Step 3: 补齐 public Javadocs 契约**

每个 public 类型至少说明 thread-safety、nullability、资源 ownership 和 failure contract；运行：

```bash
mvn -pl lite-orm-core -Dmaven.javadoc.failOnWarnings=true javadoc:javadoc
```

Expected: Javadocs PASS 且无 warning。

- [ ] **Step 4: 配置 Revapi 基线**

在 parent plugin management 配置 `revapi-maven-plugin`，从第一个已发布 `0.1.0` 起比较上一发行版。快照阶段使用 profile 跳过不存在的旧版本，release profile 必须启用。

```xml
<oldArtifacts>
  <artifact>org.liteorm:lite-orm-core:${liteorm.api.baseline.version}</artifact>
</oldArtifacts>
```

- [ ] **Step 5: 添加 API workflow 并提交**

workflow 在 pull request 上运行 `mvn -Papi-compatibility -DskipTests verify`。首个 `0.1.0` 发布后，将 baseline property 固定为 `0.1.0`。

```bash
git add pom.xml lite-orm-core docs/public-api.md .github/workflows/api-compatibility.yml
git commit -m "build: freeze liteorm public api surface"
```

### Task 4: 用 JDK-only 源码生成器替代 FreeMarker

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/compile/JavaSourceWriter.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/compile/JavaSourceGenerator.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CodeGenerator.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Delete: `lite-orm-core/src/main/resources/templates/mapper-impl.ftl`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/JavaSourceGeneratorTest.java`
- Delete: `lite-orm-core/src/test/java/org/liteorm/test/FreemarkerCodeGeneratorTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/generated/GeneratedSourceGoldenTest.java`
- Modify: `lite-orm-core/pom.xml`
- Modify: `pom.xml`

- [ ] **Step 1: 冻结 FreeMarker 当前输出为 RED characterization**

扩展 `GeneratedSourceGoldenTest`，固定 package、imports、class declaration、provider/binder/row-mapper fields、constructor、普通方法、cursor 方法、batch、generated key、dynamic SQL 和 execution-plan factory 的完整源码。新增测试直接实例化 `JavaSourceGenerator`，因此在实现前编译失败。

```java
String generated = new JavaSourceGenerator().generateMapperImpl(
    mapperType, compilationModel, elements, types);
assertEquals(normalize(expectedSource), normalize(generated));
assertFalse(generated.contains("freemarker"));
```

- [ ] **Step 2: 运行生成器测试并确认 RED**

Run:

```bash
mvn -pl lite-orm-core -Dtest=JavaSourceGeneratorTest,GeneratedSourceGoldenTest test
```

Expected: 编译失败，提示 `JavaSourceGenerator`/`JavaSourceWriter` 尚不存在。

- [ ] **Step 3: 实现专用 JavaSourceWriter**

writer 只负责 deterministic indentation、line、blank line 和 block，不实现模板表达式、条件语言或文件加载：

```java
final class JavaSourceWriter {
    private final StringBuilder source = new StringBuilder();
    private int indentation;

    JavaSourceWriter line(String value) {
        source.append("    ".repeat(indentation)).append(value).append('\n');
        return this;
    }

    JavaSourceWriter openBlock(String declaration) {
        line(declaration + " {");
        indentation++;
        return this;
    }

    JavaSourceWriter closeBlock() {
        indentation--;
        return line("}");
    }

    String source() {
        return source.toString();
    }
}
```

- [ ] **Step 4: 实现 JavaSourceGenerator 的 class shell**

按固定顺序输出 package、core API imports、generated class、adapter/provider fields、`SqlExecutor` constructor 和 `generateMethodImpl` 结果；复用现有 SQL/return/mapping helper，不在 writer 中复制编译语义。

```java
final class JavaSourceGenerator implements CodeGenerator {
    @Override
    public String generateMapperImpl(TypeElement mapperInterface,
                                     MapperCompilationModel model,
                                     Elements elements,
                                     Types types) throws GenerationException {
        JavaSourceWriter source = new JavaSourceWriter();
        source.line("package " + model.packageName() + ";").line("");
        writeImports(source);
        writeClass(source, model);
        return source.source();
    }
}
```

- [ ] **Step 5: 切换 CompilePipeline 并删除模板引擎**

`CompilePipeline` 只构造 `JavaSourceGenerator`；删除 `FreemarkerCodeGenerator`、`.ftl`、`freemarker.version` 和 dependency management/runtime dependency。生成失败继续包装为现有 `GenerationException` 并通过 javac `Messager` 报告。

- [ ] **Step 6: 验证源码等价、编译成功和依赖清零**

Run:

```bash
mvn -pl lite-orm-core test
mvn -pl lite-orm-core dependency:tree | rg -i 'freemarker|template engine'
rg -n -i 'freemarker|mapper-impl\.ftl' pom.xml lite-orm-core
```

Expected: core tests PASS；后两条命令无输出。

- [ ] **Step 7: 提交 FreeMarker 替代实现**

```bash
git add pom.xml lite-orm-core
git commit -m "refactor: replace freemarker source generation"
```

### Task 5: 拆分 runtime core 与 annotation processor

**Files:**
- Modify: `pom.xml`
- Modify: `lite-orm-core/pom.xml`
- Create: `lite-orm-processor/pom.xml`
- Move: directory `lite-orm-core/src/main/java/org/liteorm/compile/` -> `lite-orm-processor/src/main/java/org/liteorm/compile/`
- Move: `lite-orm-core/src/main/resources/META-INF/services/javax.annotation.processing.Processor` -> `lite-orm-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
- Move: compiler-focused files selected by imports of `org.liteorm.compile.LiteOrmProcessor` from `lite-orm-core/src/test/java/` -> `lite-orm-processor/src/test/java/`
- Move: XML/compiler fixtures referenced by those tests from `lite-orm-core/src/test/resources/` -> `lite-orm-processor/src/test/resources/`
- Modify: `lite-orm-examples/basic-mapper/pom.xml`
- Modify: `lite-orm-benchmarks/pom.xml`
- Modify: `lite-orm-spring-boot-starter/pom.xml`

- [ ] **Step 1: 先写 artifact dependency 失败检查**

Run before changes:

```bash
mvn -pl lite-orm-core dependency:tree -Dscope=runtime
```

Expected: core runtime tree 不含 compiler implementation；Task 4 已保证 FreeMarker 完全不存在。

- [ ] **Step 2: 新建 processor module 并迁移 compiler 源码**

`lite-orm-processor` 只依赖同版本 `lite-orm-core`；core 删除 processor service metadata 和 compiler source。parent modules 顺序为 core、processor、starter、reactor examples、benchmarks。

```xml
<dependency>
  <groupId>org.liteorm</groupId>
  <artifactId>lite-orm-core</artifactId>
</dependency>
```

- [ ] **Step 3: 更新所有 annotation processor path**

Maven consumer 使用：

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.liteorm</groupId>
    <artifactId>lite-orm-processor</artifactId>
    <version>${project.version}</version>
  </path>
</annotationProcessorPaths>
```

所有显式 `org.liteorm.compile.LiteOrmProcessor` 测试移到 processor module；runtime/core 测试不得依赖 processor。

- [ ] **Step 4: 验证生成源码只引用 core public API**

在 `GeneratedSourceGoldenTest` 中扫描 import 和 fully-qualified names，拒绝 `org.liteorm.compile`、FreeMarker 和 Spring 类型。

```java
assertFalse(source.contains("org.liteorm.compile"));
assertFalse(source.contains("freemarker"));
assertTrue(source.contains("org.liteorm.api.SqlExecutor"));
```

- [ ] **Step 5: 验证 dependency tree 和 reactor**

Run:

```bash
mvn -pl lite-orm-core dependency:tree -Dscope=runtime
mvn -pl lite-orm-processor dependency:tree
mvn -pl lite-orm-spring-boot-starter dependency:tree -Dscope=runtime
mvn clean verify
```

Expected: core/processor/starter dependency tree 均不含 FreeMarker；core/starter runtime tree 不含 `lite-orm-processor`；reactor PASS。

- [ ] **Step 6: 提交拆包**

```bash
git add pom.xml lite-orm-core lite-orm-processor lite-orm-spring-boot-starter lite-orm-examples lite-orm-benchmarks
git commit -m "refactor: split runtime core from processor"
```

### Task 6: 建立 MySQL Testcontainers 完整矩阵

**Files:**
- Modify: `pom.xml`
- Create: `lite-orm-test-support/pom.xml`
- Create: `lite-orm-test-support/src/main/java/org/liteorm/testsupport/mysql/MySqlTestDatabase.java`
- Create: `lite-orm-test-support/src/main/java/org/liteorm/testsupport/mysql/MySqlSchema.java`
- Create: `lite-orm-test-support/src/test/java/org/liteorm/testsupport/mysql/MySqlTestDatabaseTest.java`
- Modify: `lite-orm-core/pom.xml`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/database/MySqlCompatibilityTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/MySqlJdbcLifecycleTest.java`
- Modify: `lite-orm-processor/pom.xml`
- Create: `lite-orm-processor/src/test/java/org/liteorm/test/mysql/MySqlGeneratedMapperIntegrationTest.java`
- Create: `lite-orm-processor/src/test/java/org/liteorm/test/mysql/MySqlAnnotationMapper.java`
- Create: `lite-orm-processor/src/test/java/org/liteorm/test/mysql/MySqlXmlMapper.java`
- Create: `lite-orm-processor/src/test/resources/org/liteorm/test/mysql/MySqlXmlMapper.xml`
- Modify: `lite-orm-spring-boot-starter/pom.xml`
- Create: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/MySqlSpringTransactionTest.java`
- Create: `.github/workflows/mysql-testcontainers.yml`
- Modify: `docs/core-ga-contract.md`

- [ ] **Step 1: 写共享 fixture RED 测试**

`MySqlTestDatabaseTest` 验证固定 image、container 启动、DataSource、schema reset 和诊断信息。测试通过 system property 覆盖 image，但默认值固定为 parent POM 的 `mysql.testcontainer.image=mysql:8.4.0`。

```java
try (MySqlTestDatabase database = MySqlTestDatabase.start()) {
    assertTrue(database.dataSource().getConnection().isValid(5));
    database.execute("CREATE TABLE fixture_check (id BIGINT PRIMARY KEY)");
    database.resetSchema();
    assertFalse(database.tableExists("fixture_check"));
}
```

- [ ] **Step 2: 运行 test-support 测试并确认 RED**

Run:

```bash
mvn -pl lite-orm-test-support test
```

Expected: 构建失败，提示 module/fixture 尚不存在。

- [ ] **Step 3: 实现非发布 MySQL test-support module**

module 使用普通 JAR 供其他模块以 test scope 依赖，并设置 `<maven.deploy.skip>true</maven.deploy.skip>`。`MySqlTestDatabase` 封装 `MySQLContainer`、`MysqlDataSource`、schema 清理和容器日志；不暴露给任何 production source set。

```java
public final class MySqlTestDatabase implements AutoCloseable {
    private final MySQLContainer<?> container;

    public static MySqlTestDatabase start() {
        String image = System.getProperty("liteorm.mysql.image", "mysql:8.4.0");
        MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(image));
        container.start();
        return new MySqlTestDatabase(container);
    }

    public DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(container.getJdbcUrl());
        dataSource.setUser(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }
}
```

- [ ] **Step 4: 迁移 core MySQL compatibility 并增加生命周期覆盖**

`MySqlCompatibilityTest` 使用共享 fixture，继续执行类型/record/JavaBean/generated key/batch/cursor/timeout contract；`MySqlJdbcLifecycleTest` 额外验证 transaction commit/rollback、connection release 和 cleanup failure 的最终 outcome。

- [ ] **Step 5: 增加 processor annotation/XML MySQL integration**

测试通过实际 annotation processing 生成两个 Mapper 实现，再在 MySQL container 上执行 annotation CRUD、XML dynamic SQL、`foreach`、generated key 和 statement options。processor 测试不得通过手写 Mapper 实现绕过生成代码。

```java
assertEquals(1L, annotationMapper.insert("Alice"));
assertEquals(List.of("Alice"), xmlMapper.findNames(List.of(1L)));
```

- [ ] **Step 6: 增加 Starter physical MySQL transaction 测试**

使用共享 DataSource 创建真实 `DataSourceTransactionManager`，验证 `@Transactional` commit/rollback、ordered interceptor 和 generated Mapper bean；测试禁止使用 H2 compatibility mode。

- [ ] **Step 7: 建立独立 MySQL CI job**

workflow 在 Linux Docker runner 上运行：

```bash
mvn -pl lite-orm-core,lite-orm-processor,lite-orm-spring-boot-starter -am \
  -Dtest='*MySql*Test' -DfailIfNoTests=false test
```

发布 workflow 必须依赖该 job；若测试因 Docker unavailable 被 skip，CI 增加 surefire XML 检查并使 job 失败。本地开发仍可由 `disabledWithoutDocker=true` 明确 skip。

- [ ] **Step 8: 验证 test-only 依赖边界**

Run:

```bash
mvn -pl lite-orm-test-support,lite-orm-core,lite-orm-processor,lite-orm-spring-boot-starter -am test
mvn -pl lite-orm-core,lite-orm-processor,lite-orm-spring-boot-starter dependency:tree -Dscope=runtime | rg 'testcontainers|lite-orm-test-support'
```

Expected: 测试 PASS；runtime dependency tree 检查无输出。

- [ ] **Step 9: 记录矩阵并提交**

`docs/core-ga-contract.md` 列出固定 MySQL image、覆盖范围、运行命令和本地 skip/CI fail 规则。

```bash
git add pom.xml lite-orm-test-support lite-orm-core lite-orm-processor lite-orm-spring-boot-starter .github/workflows/mysql-testcontainers.yml docs/core-ga-contract.md
git commit -m "test: add full mysql testcontainers matrix"
```

### Task 7: 建立 Maven 与 Gradle 外部消费门禁

**Files:**
- Modify: `lite-orm-examples/external-maven-processor/pom.xml`
- Modify: `lite-orm-examples/external-maven-processor/README.md`
- Create: `lite-orm-examples/external-gradle-processor/settings.gradle.kts`
- Create: `lite-orm-examples/external-gradle-processor/build.gradle.kts`
- Create: `lite-orm-examples/external-gradle-processor/gradlew`
- Create: `lite-orm-examples/external-gradle-processor/gradle/wrapper/gradle-wrapper.properties`
- Create: `lite-orm-examples/external-gradle-processor/src/main/java/org/liteorm/it/ExternalUser.java`
- Create: `lite-orm-examples/external-gradle-processor/src/main/java/org/liteorm/it/ExternalUserMapper.java`
- Create: `lite-orm-examples/external-gradle-processor/src/main/java/org/liteorm/it/GeneratedMapperConsumer.java`
- Create: `lite-orm-examples/external-gradle-processor/src/main/resources/org/liteorm/it/ExternalUserMapper.xml`
- Create: `lite-orm-examples/external-gradle-processor/src/test/java/org/liteorm/it/GeneratedMapperConsumerTest.java`
- Create: `scripts/verify-external-consumers.sh`
- Modify: `lite-orm-processor/pom.xml`
- Create: `.github/workflows/external-consumers.yml`

- [ ] **Step 1: 更新 Maven fixture 为 core + processor 双 artifact**

普通 dependency 仅引入 core，`annotationProcessorPaths` 仅引入 processor，确保 generated mapper 的 downstream compilation 不需要把 processor 放入 runtime classpath。

- [ ] **Step 2: 创建根 reactor 外 Gradle fixture**

`build.gradle.kts` 使用本地临时 Maven repository，显式声明：

```kotlin
dependencies {
    implementation("org.liteorm:lite-orm-core:${property("liteOrmVersion")}")
    annotationProcessor("org.liteorm:lite-orm-processor:${property("liteOrmVersion")}")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
tasks.test { useJUnitPlatform() }
```

- [ ] **Step 3: 写隔离仓库验证脚本**

脚本创建临时 Maven repo，先安装 core/processor，再分别执行 Maven `clean verify` 和 Gradle `clean test`，最后检查 runtime classpath 不含 processor/FreeMarker。

```bash
#!/usr/bin/env bash
set -euo pipefail
repo="$(mktemp -d)"
mvn -Dmaven.repo.local="$repo" -pl lite-orm-core,lite-orm-processor -am install
mvn -Dmaven.repo.local="$repo" -f lite-orm-examples/external-maven-processor/pom.xml clean verify
./lite-orm-examples/external-gradle-processor/gradlew -p lite-orm-examples/external-gradle-processor clean test
```

- [ ] **Step 4: 接入 Maven Invoker 和 CI**

processor module 的 Invoker 验证 Maven fixture；独立 workflow 调用脚本验证 Maven/Gradle，JDK 显式开启 annotation processing。

- [ ] **Step 5: 运行并提交**

Run:

```bash
bash scripts/verify-external-consumers.sh
```

Expected: 两个外部项目 clean build；generated mapper 存在；runtime classpath 检查 PASS。

```bash
git add lite-orm-examples lite-orm-processor scripts .github/workflows/external-consumers.yml
git commit -m "test: verify external maven and gradle consumers"
```

### Task 8: 收口 Spring Boot Starter 和兼容矩阵

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Delete: `lite-orm-spring-boot-starter/src/main/resources/application.yml`
- Modify: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/LiteOrmAutoConfigurationTest.java`
- Modify: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/PackageDataSourceBindingTest.java`
- Create: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/LiteOrmPropertiesTest.java`
- Modify: `lite-orm-spring-boot-starter/pom.xml`
- Create: `.github/workflows/spring-boot-matrix.yml`
- Modify: `docs/extensions.md`

- [ ] **Step 1: 写 properties RED 测试**

只允许 `enabled` 与 `mapper-bindings[].package-name/data-source`；旧的 logging、slow query 和 audit keys 在 strict binding fixture 中必须报告 unknown property，而不是静默生效。

```java
assertThat(properties.isEnabled()).isTrue();
assertThat(properties.getMapperBindings()).singleElement()
    .satisfies(binding -> assertThat(binding.getDataSource()).isEqualTo("primaryDataSource"));
```

- [ ] **Step 2: 删除未消费配置并保持 interceptor Bean 模型**

删除六组字段/getter/setter；Starter 继续按 Spring `Ordered`/`@Order` 收集 `ExecutionInterceptor` Beans，不创建隐式日志、慢查询或审计 interceptor。

- [ ] **Step 3: 建立 Boot 3/4 matrix**

parent 默认编译基线使用仍受支持的 Spring Boot 3.x；先用 Maven Versions Plugin 查询并在 parent POM 的 `spring-boot-4.version` property 中提交执行时最新的稳定 4.x 版本，workflow 通过该 property 覆盖验证。两个矩阵复用相同 generated mapper 和 core runtime tests，不引入反射兼容层。

Run:

```bash
mvn -pl lite-orm-spring-boot-starter -am test
mvn -pl lite-orm-spring-boot-starter -am -Dspring-boot.version="$(mvn help:evaluate -Dexpression=spring-boot-4.version -q -DforceStdout)" test
```

Expected: 两个版本均 PASS；若 Boot 4 的依赖坐标需要调整，只在 POM profile 中表达，不复制 runtime code。

- [ ] **Step 4: 更新扩展文档并提交**

`docs/extensions.md` 明确 logging/slow query/audit 通过显式 interceptor Bean 配置。

```bash
git add lite-orm-spring-boot-starter docs/extensions.md .github/workflows/spring-boot-matrix.yml
git commit -m "refactor: narrow spring boot starter configuration"
```

### Task 9: 修正 benchmark 边界并建立可复现基线

**Files:**
- Modify: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark/BenchmarkSupport.java`
- Modify: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark/ReadBenchmark.java`
- Modify: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark/WriteBenchmark.java`
- Modify: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark/TransactionBenchmark.java`
- Create: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark/SpringTransactionBenchmark.java`
- Modify: `lite-orm-benchmarks/src/test/java/org/liteorm/benchmark/BenchmarkFixtureTest.java`
- Create: `scripts/run-benchmarks.sh`
- Modify: `docs/benchmarks/core-ga-baseline.md`
- Modify: `README.md`
- Modify: `README_cn.md`

- [ ] **Step 1: 先断言三方结果与事务边界一致**

fixture 明确 Direct JDBC、LiteORM、MyBatis 的 auto-commit、commit 次数、rollback 行为和返回数据一致；Spring transaction 场景验证 LiteORM 参与同一 transaction-bound connection。

- [ ] **Step 2: 分离 timing、GC 和 JFR 命令**

`scripts/run-benchmarks.sh` 提供互斥模式：

```bash
scripts/run-benchmarks.sh timing
scripts/run-benchmarks.sh gc
scripts/run-benchmarks.sh jfr
```

`timing` 不带 profiler；`gc` 使用 `-prof gc`；`jfr` 使用 JFR profiler 并不作为正式吞吐结论。

- [ ] **Step 3: 增加 Spring transaction benchmark**

只比较相同 SQL、相同连接池、相同提交边界；benchmark setup 不计入 measurement。

- [ ] **Step 4: 运行 fixture 和 smoke benchmark**

Run:

```bash
mvn -pl lite-orm-benchmarks -am test
mvn -pl lite-orm-benchmarks -am package -DskipTests
java -jar lite-orm-benchmarks/target/benchmarks.jar '.*ReadBenchmark.*' -wi 1 -i 1 -f 1
```

Expected: fixture PASS；JMH smoke run 完成且三方 checksum 相同。

- [ ] **Step 5: 更新 benchmark 方法学并提交**

文档记录硬件、OS、JDK、数据库版本、fork、warmup、measurement、事务边界和原始命令；README 只链接可复现数据。

```bash
git add lite-orm-benchmarks scripts/run-benchmarks.sh docs/benchmarks README.md README_cn.md
git commit -m "perf: align benchmark transaction boundaries"
```

### Task 10: 建立发布工程和仓库治理

**Files:**
- Modify: `pom.xml`
- Create: `LICENSE`
- Create: `CHANGELOG.md`
- Create: `SECURITY.md`
- Create: `CONTRIBUTING.md`
- Create: `.github/workflows/build.yml`
- Create: `.github/workflows/database-matrix.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/dependabot.yml`
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `.github/pull_request_template.md`
- Create: `docs/releasing.md`

- [ ] **Step 1: 确认发布命名空间与许可证门禁**

发布前由 maintainer 在 `docs/releasing.md` 记录 `org.liteorm` Maven namespace 和 `lite-orm/lite-orm` GitHub repository 的控制权证明；本计划使用 Apache License 2.0。任一控制权未确认或许可证未批准时，Task 10 保持阻塞，不上传 Central。

- [ ] **Step 2: 补齐 POM 发布 metadata**

parent POM 写入项目 URL、SCM、developer、license、distribution management，并为所有公开模块生成 sources/Javadocs/signature。使用 `maven-release` profile 激活 Central publishing 和 GPG signing，普通 snapshot build 不要求凭据。

- [ ] **Step 3: 建立 build/database workflows**

`build.yml` 运行 JDK 21 reactor、external consumers 和 API checks；`database-matrix.yml` 分别运行 PostgreSQL/MySQL Testcontainers tests，并保存 surefire reports。

- [ ] **Step 4: 建立 tag 驱动 release workflow**

仅 `v*` tag 触发：校验 tag 与 POM version、运行全量测试、构建 sources/Javadocs、签名并发布 Central，随后生成 GitHub Release notes。凭据只来自 GitHub Environments secrets。

- [ ] **Step 5: 写本地 release dry-run 文档**

`docs/releasing.md` 固定顺序：更新 version/CHANGELOG、运行 `mvn -Prelease clean verify`、创建 tag、观察 Central deployment、验证 consumer、发布 GitHub Release。

- [ ] **Step 6: 验证 artifact 内容**

Run:

```bash
mvn -Prelease -Dgpg.skip=true clean verify
find . -path '*/target/*.jar' -maxdepth 4 -print | sort
jar tf lite-orm-core/target/lite-orm-core-*.jar | rg 'org/liteorm/compile|freemarker'
```

Expected: sources/Javadocs JAR 存在；最后一条无输出。

- [ ] **Step 7: 提交治理文件**

```bash
git add pom.xml LICENSE CHANGELOG.md SECURITY.md CONTRIBUTING.md .github docs/releasing.md
git commit -m "build: prepare public release pipeline"
```

### Task 11: 发布 LiteORM DTD、核心站点和 0.1.0 示例

**Files:**
- Create: `lite-orm-processor/src/main/resources/org/liteorm/dtd/liteorm-mapper-1.0.dtd`
- Create: `lite-orm-processor/src/main/java/org/liteorm/compile/LiteOrmEntityResolver.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/SecureXml.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/XmlBasedSqlParser.java`
- Create: `lite-orm-processor/src/test/java/org/liteorm/test/LiteOrmDtdCompilationTest.java`
- Create: `lite-orm-examples/xml-feature-showcase/pom.xml`
- Create: `lite-orm-examples/xml-feature-showcase/src/main/java/org/liteorm/example/xml/XmlFeature.java`
- Create: `lite-orm-examples/xml-feature-showcase/src/main/java/org/liteorm/example/xml/XmlFeatureMapper.java`
- Create: `lite-orm-examples/xml-feature-showcase/src/main/resources/org/liteorm/example/xml/XmlFeatureMapper.xml`
- Create: `lite-orm-examples/xml-feature-showcase/src/test/java/org/liteorm/example/xml/XmlFeatureShowcaseTest.java`
- Create: `lite-orm-examples/xml-feature-showcase/README.md`
- Modify: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserMapper.java`
- Modify: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserXmlMapper.java`
- Modify: `lite-orm-examples/basic-mapper/src/test/java/org/liteorm/example/StandaloneJdbcUsageTest.java`
- Modify: `lite-orm-examples/basic-mapper/README.md`
- Create: `lite-orm-examples/spring-boot-single-datasource/pom.xml`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/main/java/org/liteorm/example/single/SingleDataSourceApplication.java`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/main/java/org/liteorm/example/single/User.java`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/main/java/org/liteorm/example/single/UserMapper.java`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/main/java/org/liteorm/example/single/UserService.java`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/main/resources/application.yml`
- Create: `lite-orm-examples/spring-boot-single-datasource/src/test/java/org/liteorm/example/single/SingleDataSourceApplicationTest.java`
- Create: `lite-orm-examples/spring-boot-single-datasource/README.md`
- Create: `mkdocs.yml`
- Create: `docs/site/index.md`
- Create: `docs/site/why-liteorm.md`
- Create: `docs/site/architecture.md`
- Create: `docs/site/annotations.md`
- Create: `docs/site/xml.md`
- Create: `docs/site/dynamic-sql.md`
- Create: `docs/site/result-mapping.md`
- Create: `docs/site/transactions.md`
- Create: `docs/site/multiple-datasources.md`
- Create: `docs/site/spring-boot.md`
- Create: `docs/site/interceptors.md`
- Create: `docs/site/error-catalog.md`
- Create: `docs/site/database-compatibility.md`
- Create: `docs/site/benchmarks.md`
- Create: `docs/site/examples.md`
- Create: `.github/workflows/pages.yml`

- [ ] **Step 1: 写 DTD 与离线 resolver RED 测试**

测试使用设计指定 public/system ID，在禁网环境编译有效 XML；未知远程 DTD 必须失败；DTD 约束 statements、fragments、dynamic tags 和已冻结 attributes，processor 继续负责 namespace、statement-method 对应和表达式语义。

- [ ] **Step 2: 实现 immutable 1.0 DTD resolver**

`LiteOrmEntityResolver` 只映射：

```text
-//LiteORM//DTD Mapper 1.0//EN
https://lite-orm.github.io/lite-orm/dtd/liteorm-mapper-1.0.dtd
```

其他 system ID 不发起网络请求。processor JAR 内资源路径必须与 Pages 发布文件字节一致。

- [ ] **Step 3: 创建 XML feature showcase 的 0.1.0 子集**

示例覆盖 CRUD、batch、`sql/include`、`if/choose/trim/where/set`、item-only `foreach`、`bind`、dynamic pagination 和诊断文档；`foreach index` 与 XML generated key 留到 Task 12 后再启用。

- [ ] **Step 4: 补齐 basic-mapper 采用路径**

确认并补齐 annotation CRUD、scalar、record、JavaBean、batch、generated key、`SqlProvider`、`ParameterBinder`、`RowMapper`、cursor、standalone transaction、rollback-only 和显式 SQL pagination；README 中每项都链接一个可运行 test。

- [ ] **Step 5: 创建 single DataSource Spring example**

真实启动 H2 应用，使用 `mapper-bindings`、默认 Mapper bean 名、constructor injection、ordered interceptors 和 `@Transactional` rollback integration test。

- [ ] **Step 6: 建立 MkDocs 站点和 Pages workflow**

站点导航包含设计要求的全部入口；0.1.0 阶段 MyBatis Migration 页面可链接现有人工迁移指南，但必须明确 scanner 尚未发布。workflow 构建站点、复制 DTD、复制版本化 Javadocs 后部署 Pages。

- [ ] **Step 7: 建立 compatibility matrix**

`docs/site/annotations.md` 和 `docs/site/xml.md` 使用表格标明 supported、migration-only、unsupported，并链接对应 example/test。`${}`、complex resultMap、cache、nested select 必须明确拒绝。

- [ ] **Step 8: 运行 0.1.0 release gate**

Run:

```bash
mvn clean verify
bash scripts/verify-external-consumers.sh
mkdocs build --strict
cmp lite-orm-processor/src/main/resources/org/liteorm/dtd/liteorm-mapper-1.0.dtd site/dtd/liteorm-mapper-1.0.dtd
```

Expected: 全部 PASS，两个 DTD 文件字节一致。

- [ ] **Step 9: 发布 0.1.0 并提交**

先将 version 设置为 `0.1.0`，按 `docs/releasing.md` 执行 Central dry-run 和 tag release；确认 Maven Central 后将开发版本推进到 `0.2.0-SNAPSHOT`。

```bash
git add lite-orm-processor lite-orm-examples mkdocs.yml docs/site .github/workflows/pages.yml pom.xml
git commit -m "feat: prepare liteorm 0.1.0 public preview"
```

### Task 12: 完成 XML P1 增量与完整性校验

**Files:**
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/AstNode.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/XmlBasedSqlParser.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/AnnotationBasedSqlParser.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-processor/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-processor/src/main/resources/templates/mapper-impl.ftl`
- Create: `lite-orm-processor/src/main/resources/org/liteorm/dtd/liteorm-mapper-1.1.dtd`
- Modify: `lite-orm-processor/src/test/java/org/liteorm/test/GeneratedKeyCompilationTest.java`
- Modify: `lite-orm-processor/src/test/java/org/liteorm/test/UnsupportedMapperSignatureCompilationTest.java`
- Create: `lite-orm-processor/src/test/java/org/liteorm/test/ForeachIndexCompilationTest.java`
- Modify: `lite-orm-examples/xml-feature-showcase/src/main/java/org/liteorm/example/xml/XmlFeatureMapper.java`
- Modify: `lite-orm-examples/xml-feature-showcase/src/main/resources/org/liteorm/example/xml/XmlFeatureMapper.xml`
- Modify: `lite-orm-examples/xml-feature-showcase/src/test/java/org/liteorm/example/xml/XmlFeatureShowcaseTest.java`
- Modify: `lite-orm-examples/xml-feature-showcase/README.md`

- [ ] **Step 1: 写 `foreach index` RED 测试**

覆盖 `List<T>`、array、`Iterable<T>`、item/index 同时绑定、嵌套 scope、同名冲突；Map 明确编译失败并提示 key/value 语义未支持。

- [ ] **Step 2: 扩展 AST 和代码生成**

`ForeachNode` 增加 nullable `index`；生成循环为每层创建唯一局部变量，index 在 SQL expression 和 `#{...}` binding 中可解析，离开循环后 scope 恢复。

```java
record ForeachNode(
    String collection,
    String item,
    String index,
    String separator,
    String open,
    String close,
    List<AstNode> children) implements AstNode {}
```

- [ ] **Step 3: 写 XML generated key RED 测试**

仅 `<insert useGeneratedKeys="true" keyColumn="id">` 生效；方法返回单个支持 scalar key；缺失 `keyColumn`、非 insert、dynamic SQL、batch、provider、多个 key column 和 `keyProperty` 必须编译失败。

- [ ] **Step 4: 映射 XML 属性到现有 generated-key contract**

parser 把 XML metadata 归一化为与 `@GeneratedKey("id")` 相同的 `MethodModel` 字段，不向输入对象回写值，不复制 runtime 实现。

- [ ] **Step 5: 审计 statement attributes**

支持可静态映射的 `timeout`、`fetchSize`；`resultSetType` 在没有跨驱动稳定语义前明确拒绝并给出诊断。属性进入现有 `StatementOptions`，不新增 `@Options`。

- [ ] **Step 6: 完成 XML 完整性诊断**

验证 namespace 精确等于 Mapper FQN、duplicate statement ID、orphan statement、overload、unknown attribute、orphan fragment、include cycle。每个失败测试断言稳定错误代码前缀，例如 `LITEORM-XML-004`。

- [ ] **Step 7: 发布新的 immutable DTD 版本**

保持 `liteorm-mapper-1.0.dtd` 字节不变，新增 `liteorm-mapper-1.1.dtd` 描述 `foreach index`、`useGeneratedKeys`、`keyColumn`、`timeout` 和 `fetchSize`。resolver 同时支持 1.0/1.1；migration tool 默认输出 1.1，Pages 永久保留两个 URL。

- [ ] **Step 8: 运行 processor 和 showcase 测试**

Run:

```bash
mvn -pl lite-orm-processor,lite-orm-examples/xml-feature-showcase -am test
```

Expected: PASS；showcase 覆盖正式 XML 全集。

- [ ] **Step 9: 提交 XML 增量**

```bash
git add lite-orm-processor lite-orm-examples/xml-feature-showcase docs/site/xml.md docs/site/error-catalog.md
git commit -m "feat: complete liteorm xml 1.0 contract"
```

### Task 13: 增加 multi DataSource 与迁移前后示例

**Files:**
- Create: `lite-orm-examples/spring-boot-multi-datasource/pom.xml`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/multi/MultiDataSourceApplication.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/multi/MultiDataSourceConfiguration.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/account/Account.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/account/AccountMapper.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/audit/AuditEvent.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/java/org/liteorm/example/audit/AuditMapper.java`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/main/resources/application.yml`
- Create: `lite-orm-examples/spring-boot-multi-datasource/src/test/java/org/liteorm/example/MultiDataSourceApplicationTest.java`
- Create: `lite-orm-examples/mybatis-migration/before/pom.xml`
- Create: `lite-orm-examples/mybatis-migration/before/src/main/java/org/liteorm/example/migration/UserMapper.java`
- Create: `lite-orm-examples/mybatis-migration/before/src/main/resources/org/liteorm/example/migration/UserMapper.xml`
- Create: `lite-orm-examples/mybatis-migration/before/src/main/java/org/liteorm/example/migration/MyBatisConfiguration.java`
- Create: `lite-orm-examples/mybatis-migration/after/pom.xml`
- Create: `lite-orm-examples/mybatis-migration/after/src/main/java/org/liteorm/example/migration/UserMapper.java`
- Create: `lite-orm-examples/mybatis-migration/after/src/main/resources/org/liteorm/example/migration/UserMapper.xml`
- Create: `lite-orm-examples/mybatis-migration/after/src/main/resources/application.yml`
- Create: `lite-orm-examples/mybatis-migration/after/src/test/java/org/liteorm/example/migration/MigrationEquivalenceTest.java`
- Create: `lite-orm-examples/mybatis-migration/README.md`
- Modify: `docs/site/multiple-datasources.md`
- Modify: `docs/site/examples.md`

- [ ] **Step 1: 创建两个不重叠 Mapper package 的应用**

定义 `accountDataSource/accountTransactionManager` 和 `auditDataSource/auditTransactionManager`；`mapper-bindings` 将每个 package 绑定到唯一 DataSource。

- [ ] **Step 2: 写正确/错误 transaction manager 集成测试**

正确 manager 回滚对应库；错误 manager 调用必须触发 transaction domain mismatch，且另一个库没有部分写入。routing DataSource 仅在 README 解释边界，不作为同一 Mapper 动态切换方案。

- [ ] **Step 3: 创建 MyBatis before/after fixture**

before 包含 annotations、XML、flat resultMap、provider、Spring Mapper scan；after 使用 LiteORM annotations/XML、SQL alias/`@Column`、`@UseSqlProvider` 和 `mapper-bindings`，并保持同一业务测试结果。

- [ ] **Step 4: 运行示例测试并提交**

Run:

```bash
mvn -pl lite-orm-examples/spring-boot-multi-datasource -am test
mvn -f lite-orm-examples/mybatis-migration/after/pom.xml test
```

Expected: PASS。

```bash
git add lite-orm-examples docs/site
git commit -m "docs: add multi datasource and migration examples"
```

### Task 14: 实现 MyBatis Compatibility Scanner 并发布 0.2.0

**Files:**
- Modify: `pom.xml`
- Create: `lite-orm-migration/pom.xml`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/Main.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/scan/ProjectScanner.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/scan/JavaMapperScanner.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/scan/XmlMapperScanner.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/scan/SpringConfigurationScanner.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/model/Finding.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/model/CompatibilityCategory.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/report/JsonReportWriter.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/report/TextReportWriter.java`
- Create: `lite-orm-migration/src/test/java/org/liteorm/migration/CompatibilityScannerTest.java`
- Create: `lite-orm-migration/src/test/resources/fixtures/annotation-mapper/UserMapper.java`
- Create: `lite-orm-migration/src/test/resources/fixtures/xml-mapper/UserMapper.xml`
- Create: `lite-orm-migration/src/test/resources/fixtures/spring/MyBatisConfiguration.java`
- Create: `lite-orm-migration/src/test/resources/fixtures/expected-report.json`
- Modify: `lite-orm-examples/mybatis-migration/after/migration-report.json`
- Modify: `docs/site/mybatis-migration.md`
- Modify: `docs/site/error-catalog.md`

- [ ] **Step 1: 定义稳定 finding schema**

```java
public record Finding(
    String ruleId,
    CompatibilityCategory category,
    Path file,
    int line,
    String mapper,
    String method,
    String message,
    String recommendedAction) {}
```

分类固定为 `AUTO_MIGRATABLE`、`MANUAL_REVIEW`、`UNSUPPORTED`、`KEEP_MYBATIS`、`USE_RAW_JDBC`；报告按 file/line/ruleId 排序，保证 deterministic output。

- [ ] **Step 2: 写 scanner RED fixture**

fixture 覆盖 annotation imports、XML DTD/tags/attributes、OGNL、`${}`、resultMap、provider、plugin、cache、nested query、generated key、Mapper scan 和 DataSource 配置；每个 finding 断言文件、行号、Mapper、方法、分类和建议动作。

- [ ] **Step 3: 实现只读扫描 CLI**

命令：

```bash
java -jar lite-orm-migration.jar scan /path/to/project --format json --output migration-report.json
```

scanner 不修改输入文件；XML 使用与 processor 相同安全 XML 基础设施。将通用 XML security/resolver 抽到 processor 无关的小内部包或复制为 migration-private implementation，但不能让 migration 进入 core runtime dependency tree。

- [ ] **Step 4: 更新 before/after 固定报告**

对 `lite-orm-examples/mybatis-migration/before` 运行 scanner，提交排序稳定的 JSON golden；测试重新生成并 byte-for-byte 比较。

- [ ] **Step 5: 运行 0.2.0 gate**

Run:

```bash
mvn -pl lite-orm-migration,lite-orm-examples/spring-boot-multi-datasource -am test
java -jar lite-orm-migration/target/lite-orm-migration-*.jar scan lite-orm-examples/mybatis-migration/before --format json --output /tmp/migration-report.json
cmp /tmp/migration-report.json lite-orm-examples/mybatis-migration/after/migration-report.json
```

Expected: PASS 且报告完全一致。

- [ ] **Step 6: 发布 0.2.0 并提交**

按 release 文档发布 `0.2.0`，随后推进 `0.3.0-SNAPSHOT`。

```bash
git add pom.xml lite-orm-migration lite-orm-examples/mybatis-migration docs/site
git commit -m "feat: add mybatis compatibility scanner"
```

### Task 15: 实现确定性 Safe Rewriter

**Files:**
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/rewrite/ProjectRewriter.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/rewrite/JavaMapperRewriter.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/rewrite/XmlMapperRewriter.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/rewrite/SpringConfigurationRewriter.java`
- Create: `lite-orm-migration/src/main/java/org/liteorm/migration/rewrite/RewriteResult.java`
- Create: `lite-orm-migration/src/test/java/org/liteorm/migration/SafeRewriterTest.java`
- Create: `lite-orm-migration/src/test/resources/rewrite/annotations/before/UserMapper.java`
- Create: `lite-orm-migration/src/test/resources/rewrite/annotations/after/UserMapper.java`
- Create: `lite-orm-migration/src/test/resources/rewrite/xml/before/UserMapper.xml`
- Create: `lite-orm-migration/src/test/resources/rewrite/xml/after/UserMapper.xml`
- Create: `lite-orm-migration/src/test/resources/rewrite/spring/before/MyBatisConfiguration.java`
- Create: `lite-orm-migration/src/test/resources/rewrite/spring/after/application.yml`
- Modify: `lite-orm-migration/src/main/java/org/liteorm/migration/Main.java`
- Modify: `docs/site/mybatis-migration.md`

- [ ] **Step 1: 写 idempotent golden tests**

每个 fixture 保存 `before/expected-after`；第一次 rewrite 必须等于 expected，第二次 rewrite 必须零变更；复杂 resultMap、plugin、cache、nested select 和 arbitrary OGNL 保持源文件不变并产生 finding。

- [ ] **Step 2: 实现 Java 确定性转换**

只转换 MyBatis CRUD annotations、`@Param` imports 和 provider annotation 骨架。无法保持语义时不改源码；provider 输出 `@UseSqlProvider(MyProvider.class)` 并在报告中说明需人工实现 `SqlProvider`。

- [ ] **Step 3: 实现 XML 确定性转换**

替换 MyBatis DTD 为 LiteORM DTD，保留正式支持的 dynamic tags；简单 flat resultMap 转换为 SQL alias 或 model `@Column` 仅在列/属性一一对应且无 nested mapping 时执行。

- [ ] **Step 4: 实现 Spring 配置建议输出**

不猜测 bean 名；从 `@MapperScan` package 和可静态识别 DataSource 生成 `mapper-bindings` 建议 patch。存在多个候选 DataSource 时只报告 `MANUAL_REVIEW`。

- [ ] **Step 5: 提供 dry-run 和 apply 模式**

```bash
java -jar lite-orm-migration.jar rewrite project --dry-run --diff
java -jar lite-orm-migration.jar rewrite project --apply --report migration-report.json
```

默认是 dry-run；`--apply` 写入前创建单次 `.liteorm-backup`，目标目录已有 backup 时拒绝覆盖。

- [ ] **Step 6: 运行测试并提交**

Run:

```bash
mvn -pl lite-orm-migration test
```

Expected: golden、idempotency、no-guess 和 backup protection 全部 PASS。

```bash
git add lite-orm-migration docs/site/mybatis-migration.md
git commit -m "feat: add deterministic mybatis rewriter"
```

### Task 16: 用真实项目验证迁移并发布 0.3.0

**Files:**
- Create: `lite-orm-migration/src/test/resources/real-projects/README.md`
- Create: `docs/migration-validation.md`
- Modify: `docs/site/mybatis-migration.md`
- Modify: `CHANGELOG.md`
- Modify: `pom.xml`

- [ ] **Step 1: 选择一个许可兼容的开源 MyBatis 样本**

只提交最小、可再分发 fixture 或固定 commit/下载校验信息；记录上游项目、license、commit SHA、扫描范围和未迁移能力。不要把整个第三方仓库无审查复制进本仓库。

- [ ] **Step 2: 运行 scanner 和 dry-run rewriter**

保存 finding 数量、分类分布、自动改写文件清单和剩余人工项；报告必须可由固定命令重现。

- [ ] **Step 3: 编译迁移后样本并运行等价测试**

至少验证 annotation Mapper、XML Mapper、dynamic SQL、Spring transaction 和 generated key；不支持能力保留 MyBatis 或改为 raw JDBC，并在验证文档说明原因。

- [ ] **Step 4: 冻结 migration report schema**

为 JSON schema 增加版本字段 `schemaVersion: "1.0"`，CLI 后续兼容读取该版本；字段删除或语义变化视为 breaking change。

- [ ] **Step 5: 运行 0.3.0 gate 并发布**

Run:

```bash
mvn clean verify
bash scripts/verify-external-consumers.sh
mkdocs build --strict
```

Expected: 全部 PASS；真实样本验证文档包含可复现命令和明确剩余项。

```bash
git add lite-orm-migration docs CHANGELOG.md pom.xml
git commit -m "docs: validate automated mybatis migration"
```

按 `docs/releasing.md` 发布 `0.3.0`，随后推进下一个 snapshot。

### Task 17: 收集 1.0.0 GA 证据并冻结契约

**Files:**
- Create: `docs/ga-readiness.md`
- Create: `docs/adopters/adopter-template.md`
- Create: `docs/adopters/adopter-trial-01.md`
- Create: `docs/adopters/adopter-trial-02.md`
- Modify: `docs/public-api.md`
- Modify: `docs/site/annotations.md`
- Modify: `docs/site/xml.md`
- Modify: `docs/site/transactions.md`
- Modify: `docs/site/multiple-datasources.md`
- Modify: `docs/site/spring-boot.md`
- Modify: `docs/site/mybatis-migration.md`
- Modify: `docs/site/error-catalog.md`
- Modify: `docs/site/database-compatibility.md`
- Modify: `docs/site/benchmarks.md`
- Modify: `docs/site/examples.md`
- Modify: `CHANGELOG.md`
- Modify: `pom.xml`

- [ ] **Step 1: 建立 GA readiness checklist**

`docs/ga-readiness.md` 对照设计的十条验收标准，逐条链接 CI run、测试类、example、Central artifact、Pages URL 和 migration report。没有证据的条目保持 unchecked，不允许凭口头判断发布 GA。

- [ ] **Step 2: 完成两个仓库外 adopter trial**

每个记录项目类型、原 MyBatis 能力、迁移耗时、阻塞点、最终保留 MyBatis/raw JDBC 的范围和用户确认；必须是非本仓库内部 fixture。

- [ ] **Step 3: 冻结 core/starter/migration contract**

Revapi 对比最新 `0.x` release 无未批准 breaking changes；DTD 1.0 URL byte immutable；migration report schema 1.0 稳定；数据库和 Boot matrix 连续通过。

- [ ] **Step 4: 清零 P0 correctness issue**

使用 GitHub label `priority:P0` 和 milestone `1.0.0` 查询；任何 open correctness issue 阻断发布。feature request 不得伪装成 correctness waiver。

- [ ] **Step 5: 运行 GA release gate**

Run:

```bash
mvn -Prelease -Dgpg.skip=true clean verify
bash scripts/verify-external-consumers.sh
mkdocs build --strict
```

Expected: 全部 PASS；`docs/ga-readiness.md` 全部 checked。

- [ ] **Step 6: 发布 1.0.0**

更新 version/CHANGELOG，执行 tag-driven release；发布后验证 Maven/Gradle 空项目从 Central 构建、Pages/Javadocs/DTD 可访问，再推进 `1.1.0-SNAPSHOT`。

```bash
git add docs CHANGELOG.md pom.xml
git commit -m "release: prepare liteorm 1.0.0"
```

### Task 18: 实现独立 DB-to-Mapper Generator 0.x

**Files:**
- Modify: `pom.xml`
- Create: `lite-orm-generator/pom.xml`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/Main.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/config/GeneratorConfig.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/metadata/DatabaseIntrospector.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/metadata/TableModel.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/type/JdbcJavaTypeMapper.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/render/ModelRenderer.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/render/MapperRenderer.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/render/XmlMapperRenderer.java`
- Create: `lite-orm-generator/src/main/java/org/liteorm/generator/io/ProtectedFileWriter.java`
- Create: `lite-orm-generator/src/test/java/org/liteorm/generator/GeneratorGoldenTest.java`
- Create: `lite-orm-generator/src/test/java/org/liteorm/generator/PostgresGeneratorTest.java`
- Create: `lite-orm-generator/src/test/java/org/liteorm/generator/MySqlGeneratorTest.java`
- Modify: `.github/workflows/mysql-testcontainers.yml`
- Create: `docs/site/generator.md`

- [ ] **Step 1: 定义 deterministic config 和 model**

配置包含 JDBC URL/credentials、schema、table include/exclude、record/bean、annotation/XML output、package、output dir、dry-run；table/column 始终按稳定名称排序。

- [ ] **Step 2: 写 H2 golden 与 PostgreSQL/MySQL metadata tests**

覆盖 snake_case、PK、identity、nullable、UUID/temporal/numeric/binary 类型、include/exclude。`MySqlGeneratorTest` 必须复用 `lite-orm-test-support` 的真实 MySQL 8.4 container，而不是 mock `DatabaseMetaData`；两次生成必须 byte-for-byte 相同。

- [ ] **Step 3: 生成受控骨架**

输出 model、Mapper CRUD、find-by-primary-key 和显式 `LIMIT/OFFSET`；XML 模式使用 LiteORM DTD。不得生成 join、关系映射、Repository、active record 或业务 search DSL。

- [ ] **Step 4: 保护已存在文件**

默认任何目标文件存在即失败并列出路径；`--dry-run` 只输出计划/diff；不提供覆盖人工文件的 `--force`。

- [ ] **Step 5: 验证生成项目可编译**

golden test 将输出写入临时 Maven/Gradle consumer，使用已发布 core/processor 编译 generated model/Mapper/XML。

- [ ] **Step 6: 将 generator 加入 MySQL CI matrix**

更新 `mysql-testcontainers.yml`，在 generator module 创建后把 `lite-orm-generator` 和 `MySqlGeneratorTest` 加入同一发布阻断 job；继续检查 Docker skip 数为 0。

- [ ] **Step 7: 运行测试并独立发布 0.x**

Run:

```bash
mvn -pl lite-orm-generator -am test
```

Expected: H2 PASS；Docker 可用时 PostgreSQL/MySQL PASS；golden deterministic/protection tests PASS。

```bash
git add pom.xml lite-orm-generator docs/site/generator.md mkdocs.yml
git commit -m "feat: add deterministic db mapper generator"
```

generator 使用独立 `0.x` 版本节奏，不阻塞 LiteORM core `1.0.0`。

### Task 19: 建立 P3 真实需求准入机制

**Files:**
- Create: `docs/ecosystem-intake.md`
- Modify: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Modify: `docs/site/index.md`

- [ ] **Step 1: 定义生态能力准入条件**

IntelliJ、VS Code、Kotlin/KSP、Micrometer、OpenTelemetry、GraalVM、Quarkus/Micronaut、Java 17、dialect helper、generator templates 和 dashboard 均需满足：至少两个独立用户场景、明确维护者、不会破坏轻量 core、可独立 module/仓库交付、已有验收样本。

- [ ] **Step 2: 更新 feature request 模板**

模板要求用户提供当前 workaround、受影响项目、期望边界、是否愿意验证 preview；缺少真实场景的请求进入 discussion，不进入 roadmap。

- [ ] **Step 3: 为通过准入的能力另立 spec**

每个 P3 候选必须新建以能力命名的设计稿和实施计划，例如 `docs/superpowers/specs/2026-09-01-micrometer-interceptor-design.md`；不得直接在 core 中试验性加入。

- [ ] **Step 4: 提交准入规则**

```bash
git add docs/ecosystem-intake.md .github/ISSUE_TEMPLATE/feature_request.yml docs/site/index.md
git commit -m "docs: define ecosystem feature intake gates"
```

## 最终验收矩阵

在路线图完成前，执行并保存以下证据：

```bash
mvn -Prelease -Dgpg.skip=true clean verify
bash scripts/verify-external-consumers.sh
mkdocs build --strict
mvn -pl lite-orm-core dependency:tree -Dscope=runtime
mvn -pl lite-orm-processor dependency:tree
mvn -pl lite-orm-spring-boot-starter dependency:tree -Dscope=runtime
mvn -pl lite-orm-core,lite-orm-processor,lite-orm-spring-boot-starter,lite-orm-generator -am \
  -Dtest='*MySql*Test' -DfailIfNoTests=false test
rg -n -i 'freemarker|mapper-impl\.ftl' pom.xml lite-orm-core lite-orm-processor
```

Expected:

- core、processor 和 Starter dependency tree 均不含 FreeMarker，runtime classpath 不含 processor；
- MySQL Testcontainers core/processor/XML/Starter/generator 测试全部执行且 CI skip 数为 0；
- Testcontainers 与 `lite-orm-test-support` 不出现在任何用户 runtime dependency tree；
- Maven/Gradle 外部 fixture clean build；
- single/multi DataSource examples 通过；
- LiteORM DTD 离线编译，Pages 文件与 processor resource 字节一致；
- XML/annotation 每项正式能力均链接 example 和 compatibility test；
- scanner/rewriter 报告稳定、排序确定、复杂能力不被猜测改写；
- release artifact 包含 metadata、sources、Javadocs 和签名；
- Pages 包含 Quick Start、reference、error catalog、benchmark、examples、Javadocs 和 changelog；
- benchmark 三方事务边界一致；
- core API surface 不包含 session、cache、自动分页和通用插件链。
