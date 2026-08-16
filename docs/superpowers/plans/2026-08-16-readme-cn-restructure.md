# README 中文版重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `README_cn.md` 重写为自顶向下、以 Quick Start 为入口且与当前实现一致的中文项目主文档。

**Architecture:** README 先提供可运行的 Spring Boot 最小路径，再通过整体架构图、角色表和两种装配图解释系统。事务、多 DataSource、Spring 注册和扩展点只描述当前稳定契约，详细矩阵和 benchmark 数据链接到专门文档。

**Tech Stack:** Markdown、Mermaid、Maven、Java、Spring Boot

---

### Task 1: 核对文档事实来源

**Files:**
- Read: `pom.xml`
- Read: `lite-orm-examples/basic-mapper/pom.xml`
- Read: `lite-orm-examples/basic-mapper/README.md`
- Read: `lite-orm-core/src/main/java/org/liteorm/LiteOrm.java`
- Read: `lite-orm-core/src/main/java/org/liteorm/JdbcAssembly.java`
- Read: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Read: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`

- [x] **Step 1: 核对 Maven 坐标和 Java/Spring 版本**

Run:

```bash
rg -n "<groupId>|<artifactId>|<version>|maven.compiler|java.version|spring-boot" pom.xml lite-orm-examples/basic-mapper/pom.xml lite-orm-spring-boot-starter/pom.xml
```

Expected: 找到 README Quick Start 所需的准确依赖坐标和版本要求。

- [x] **Step 2: 核对 Mapper 生成和 Spring 注册行为**

Run:

```bash
rg -n "BeanDefinition|beanName|decapitalize|SqlExecutor|SpringConnectionHandleFactory|mapper-bindings" lite-orm-spring-boot-starter/src/main/java lite-orm-core/src/main/java
```

Expected: 确认生成 Mapper 无 Spring 注解，Starter 注册生成实现并注入对应 `SqlExecutor`。

- [x] **Step 3: 核对事务和多 DataSource 契约**

Run:

```bash
rg -n "rollback|nested|TransactionDomain|data-source|package-name|overlap" lite-orm-core/src/main/java lite-orm-spring-boot-starter/src/main/java docs/core-ga-contract.md docs/extensions.md
```

Expected: 确认 core 简单本地事务、Spring 事务参与以及 Mapper/DataSource 1:1 包绑定规则。

### Task 2: 重写中文版 README

**Files:**
- Modify: `README_cn.md`

- [x] **Step 1: 用目标章节替换旧结构**

按以下一级章节顺序重写全文：

```text
项目定位
Quick Start
整体设计
完整角色表
两种装配方式
SQL 与映射能力
事务模型
多 DataSource
Spring Boot 接入
扩展点
MyBatis 兼容与迁移
性能基线
深入文档
```

- [x] **Step 2: 添加三张 Mermaid 图**

必须包含：

```text
编译期与运行期总链路
Standalone JDBC 装配依赖
Spring Boot 装配依赖
```

依赖方向必须与 `JdbcAssembly`、`SpringConnectionHandleFactory` 和生成 Mapper 构造器一致。

- [x] **Step 3: 补全公开角色表**

角色表必须明确包含：

```text
Mapper 接口
生成 MapperImpl
LiteOrmProcessor/CompilePipeline
ExecutionPlan/SqlResult
SqlExecutor/JdbcSqlExecutor
ConnectionHandleFactory
TransactionalExecutor
SqlProvider
ParameterBinder
RowMapper
ExecutionInterceptor
Spring Starter Registrar
```

- [x] **Step 4: 删除过时和重复内容**

删除历史 Engine 清理、已完成实施任务、重复职责说明，以及任何暗示 `bean-name-prefix`、隐式默认 DataSource、运行期 Mapper 代理或 Provider 自动注册为 Spring Bean 的表述。

### Task 3: 验证并提交

**Files:**
- Verify: `README_cn.md`

- [x] **Step 1: 检查章节顺序和关键术语**

Run:

```bash
rg -n '^#{1,3} |SqlProvider|mapper-bindings|package-name|data-source|SimpleTransactionalExecutor|SpringConnectionHandleFactory|bean-name-prefix' README_cn.md
```

Expected: 章节顺序符合设计，关键角色存在，`bean-name-prefix` 不存在。

- [x] **Step 2: 检查仓库内链接和 Markdown 空白错误**

Run:

```bash
git diff --check
```

Expected: 命令退出码为 0。

- [x] **Step 3: 对照实现复核配置和代码示例**

Run:

```bash
mvn -pl lite-orm-examples/basic-mapper -am -DskipTests compile
```

Expected: Maven 构建成功，README 中引用的注解处理和生成 Mapper 路径仍有效。

- [x] **Step 4: 提交文档重构**

```bash
git add README_cn.md docs/superpowers/plans/2026-08-16-readme-cn-restructure.md
git commit -m "docs: restructure chinese readme"
```
