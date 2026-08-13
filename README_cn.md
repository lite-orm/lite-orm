# lite-orm

`lite-orm` 是一个以编译期代码生成为核心的 Java ORM 实验项目。它不是要复刻一个更轻的 MyBatis，而是要验证并产品化一个更明确的方向：

> 把 MyBatis 传统上在运行期完成的 Mapper 分发、SQL 渲染、参数绑定和结果映射，尽可能前移到编译期，生成可审查、可调试、可执行的静态 Java 代码。

这个项目的核心资产不是运行时代理，而是编译期模型、动态 SQL AST、代码生成器和一条尽量薄的运行时 JDBC 执行链。

## 核心定位

### 我们要做什么

- 生成 Mapper 接口的静态实现类，避免运行期 Mapper 代理和反射分发。
- 将注解和 XML 中的 SQL 解析为统一编译期模型。
- 将常见 MyBatis 动态 SQL 编译成 Java 条件分支、循环和 SQL renderer。
- 在编译期确定参数绑定顺序、SQL 来源、statement 类型和返回形状。
- 运行期只负责连接、事务、参数绑定、SQL 执行和结果提取这些 JDBC 物理步骤。
- 保留 MyBatis 风格的 Mapper 编写习惯，让现有项目可以渐进迁移。

### 我们暂时不做什么

`lite-orm` 当前阶段不追求 MyBatis 全量无差异兼容。下面这些能力属于后续迭代或明确非 MVP 目标：

- MyBatis 插件体系的完整兼容。
- 任意 OGNL 表达式和所有历史 XML 特性。
- 复杂 `resultMap` 图谱、延迟加载、分步查询、一对多聚合。
- 二级缓存、分页 DSL、分库分表、多租户、读写分离。
- 分布式事务和完整企业级治理能力。

这些能力可以作为平台化方向演进，但不能混入第一阶段目标。第一阶段要先把“编译期 Mapper 子集替代”做扎实。

## 为什么不是重复造 MyBatis

MyBatis 的优势是生态成熟、兼容性强、动态 SQL 表达力好。但它的核心模型仍然偏运行期：Mapper 代理、XML/配置解析、参数解析、映射规则等行为大多隐藏在运行时框架内部。

`lite-orm` 的差异化是把这些行为显式化为编译期产物：

| 维度 | MyBatis | lite-orm 目标 |
| --- | --- | --- |
| Mapper 调用 | 运行期代理和方法分发 | 编译期生成实现类，普通 Java 调用 |
| SQL 动态逻辑 | 运行期解释 XML/OGNL | 编译期生成 Java renderer |
| 参数绑定 | 运行期解析参数名和属性路径 | 编译期确定绑定顺序 |
| 结果映射 | 运行期映射规则和反射路径较多 | 编译期生成静态映射代码 |
| 调试体验 | XML 和代理链偏黑盒 | 生成代码可读、可断点 |
| 静态分析 | 行为隐藏在框架内部 | 模型和生成代码可被 IDE/AI 分析 |

因此，项目真正的方向不是“小 MyBatis”，而是：

> 编译期 Mapper 平台。

## 当前实现状态

当前仓库已经具备一条可验证的核心闭环：

- `lite-orm-core`
  - `LiteOrmProcessor` 扫描 `@Mapper` 接口。
  - `CompilePipeline` 统一 XML 和注解 SQL 输入。
  - `XmlBasedSqlParser` 和 `AnnotationBasedSqlParser` 解析 SQL 来源。
  - `AstNode` 表示动态 SQL 结构。
  - `FreemarkerCodeGenerator` 生成 Mapper 实现和执行计划。
  - `DefaultSqlEngine` 通过 processor chain 执行 SQL。
  - `DefaultTransactionManager` 提供本地事务上下文。
- `lite-orm-spring-boot-starter`
  - 提供 Spring Boot 自动配置入口。
  - 扫描并注册编译期生成的 Mapper 实现。
  - 复用应用 `DataSource` 和 Spring 托管事务连接。

已验证能力包括：

- LiteORM 自有 `org.liteorm.annotation` 注解；项目不在 MyBatis/iBatis 命名空间下发布任何类。
- 使用 `org.liteorm.annotation.Mapper` 声明的 MyBatis 风格 Mapper 接口。
- `@Select`、`@Insert`、`@Update`、`@Delete` 注解输入。
- XML-backed Mapper 方法。
- `@Param`、`param1`、`arg0`、`list`、`collection`、`array` 等常见参数命名。
- 动态 SQL 标签：`if`、`choose`、`when`、`otherwise`、`trim`、`where`、`set`、`foreach`、`sql`、`include`。
- 静态执行计划、静态参数绑定、基础静态结果映射。
- LiteORM 本地事务和 Spring 托管事务参与。

典型导入如下：

```java
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;
```

当前构建验证：

```bash
mvn clean test
```

最近一次验证结果：core `84` 个测试通过，spring starter `2` 个测试通过。

## 架构总览

```text
Mapper interface + XML/annotations
        |
        v
Annotation Processor
        |
        v
Normalized Mapper Model
        |
        +--> Dynamic SQL AST
        +--> Parameter Model
        +--> Result Shape
        +--> Statement Metadata
        |
        v
Generated MapperImpl
        |
        v
ExecutionPlan
        |
        v
Runtime SQL Pipeline
        |
        +--> ConnectionProcessor
        +--> TransactionProcessor
        +--> ParameterProcessor
        +--> ExecutionProcessor
        +--> ResultProcessor
```

### 编译期职责

编译期负责所有能提前确定的事情：

- 找到 Mapper 接口和 SQL 来源。
- 解析 XML 和注解 SQL。
- 将动态 SQL 转成 AST。
- 校验参数引用、方法签名和返回类型。
- 生成 SQL renderer、执行计划、参数绑定和结果映射代码。

### 运行期职责

运行期只保留必要的 JDBC 执行职责：

- 获取和释放数据库连接。
- 参与本地或托管事务。
- 创建 `PreparedStatement` 并绑定参数。
- 执行 SQL。
- 提取 `ResultSet` 为生成代码可消费的结果结构。

运行期处理器可以扩展，但默认链路只包含 SQL 执行的物理必需步骤。

## Spring Boot 接入

Starter 不创建运行时 Mapper 代理，而是注册编译期已经生成的 `*MapperImpl`：

```yaml
lite-orm:
  enabled: true
  mapper-packages:
    - com.example.mapper
```

应用启动时，Starter 扫描配置包中的生成类，按 Mapper 接口类型注册 Spring Bean，并注入自动配置的 `SqlEngine`。启动扫描允许检查类和构造器，但 Mapper 调用、SQL 构造、参数绑定和结果映射仍然是普通 Java 直接调用，不在热路径使用反射。

Starter 始终使用应用提供的 `DataSource`。在 Spring `@Transactional` 范围内复用 Spring 绑定到当前线程的连接；事务外按数据源默认的 auto-commit 行为执行，并在每次调用后释放 JDBC 资源。

## SQL Provider 逃生口

只有当 SQL 无法由当前支持的注解/XML 子集表达时，才使用 `@UseSqlProvider`。Provider 是编译期已知的 `SqlProvider<P>`，需要可访问的无参构造器，并返回由 SQL 文本和有序 `BoundParameter` 列表组成的不可变 `BoundSql`。生成类持有单个 Provider 实例并直接调用普通 Java 方法，不使用反射分发。

Provider Mapper 方法支持零个或一个参数；多个输入应封装为 record。使用 Provider 的方法不能同时声明 XML 或 SQL 注解。

## 自定义 JDBC Adapter

当 JDBC 默认 `setObject` 无法满足特殊值类型时，可以在 Mapper 参数上使用 `@UseParameterBinder`。当返回结构无法由 LiteORM 内建的标量、record 或 JavaBean 映射生成时，可以在查询方法上使用 `@UseRowMapper`。

两种 adapter 都是强类型接口，实现类在编译期确定并需要可访问的无参构造器。生成 Mapper 持有单个 adapter 实例，并通过执行计划传递直接引用。显式 adapter 优先于内建转换。参数为 null 时不调用自定义 binder，而是绑定 SQL `NULL`；只有 `ResultSet.next()` 成功后才调用 row mapper。

## 执行拦截器

可注册 `ExecutionInterceptor`，在受控的 JDBC 执行边界实现日志、指标、审计、授权或路由观察。拦截器只能读取 statement 标识、最终 SQL、有序参数副本、语句/来源类型、耗时、结果数量、失败信息和只读路由元数据，不能替换生成的 SQL、参数 binder 或 row mapper。

`beforeExecution` 按配置顺序执行，`afterSuccess` 和 `afterFailure` 按相反顺序回退。Spring Boot 按 Spring ordering 收集拦截器 bean。回调失败不会阻止 JDBC 资源释放；失败回调抛出的异常会作为 suppressed exception 附加到原始执行异常。

## MyBatis 兼容边界

### 第一阶段支持

- Mapper 接口。
- 注解 SQL。
- XML SQL。
- 常见动态 SQL 标签。
- 显式 `@Param` 和常见 fallback 参数名。
- record class 和基础构造器结果映射。
- 本地事务和 Spring 事务参与。

### 第一阶段不承诺

- 任意 OGNL。
- 复杂 `resultMap`。
- 嵌套对象聚合。
- MyBatis 插件。
- 懒加载。
- 二级缓存。
- 分页插件。
- 全量 XML 标签兼容。

不支持的能力应该尽量在编译期失败，并给出 Mapper 方法或 XML 节点位置，而不是在运行期模糊 fallback。

## 迁移策略

推荐从 MyBatis 项目中选取低风险 Mapper 渐进迁移：

1. 优先迁移查询型 Mapper。
2. 优先选择只使用常见动态 SQL 标签的 XML 或注解方法。
3. 多参数方法显式补充 `@Param`。
4. 对单对象参数统一使用 `#{user.id}`、`#{user.name}` 这类属性路径。
5. 先跑外部 demo 项目和真实数据库 E2E，再扩大迁移范围。

示例：

```java
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;

@Mapper
public interface UserMapper {

    @Select("""
        <script>
        SELECT id, name, email, age
        FROM users
        <where>
            <if test="name != null and name != ''">
                name = #{name}
            </if>
        </where>
        </script>
        """)
    List<User> findByName(@Param("name") String name);
}
```

## SQL 来源优先级

同一个 Mapper 方法可以在迁移期间暂时同时保留 XML SQL 和 SQL 注解。编译器采用以下规则：

1. XML statement 优先于 `@Select`、`@Insert`、`@Update`、`@Delete`。
2. 生成代码只使用 XML statement。
3. 编译期间在对应 Mapper 方法位置输出 WARNING，提示注解已被 XML 覆盖。
4. 只有 XML 文件中存在同名 statement 时才视为冲突；仅存在同 Mapper XML 文件不会导致其他注解方法误报。

动态 SQL 表达式采用受控的 OGNL 风格编译子集。注解处理器直接把支持的表达式翻译成原生 Java 条件、属性访问、循环和 bind 表达式；编译期与运行期都不引入 OGNL、MVEL、SpEL 或其他表达式引擎。超出子集的表达式直接编译失败，不会退回运行时解释执行。

可运行的外部 Maven 示例见：

- [lite-orm-examples/basic-mapper](lite-orm-examples/basic-mapper/README.md)

## 后续路线

后续路线按可独立交付的模块推进，具体任务见：

- [LiteORM Incremental Implementation Plan](docs/plans/liteorm-incremental-implementation-plan.md)
- [MyBatis 兼容矩阵](docs/mybatis-compatibility.md)
- [MyBatis 迁移指南](docs/migration-guide.md)
- [扩展契约](docs/extensions.md)

推荐优先级：

1. MVP 硬化：外部项目 E2E、编译期诊断、安全边界、构建配置。
2. 映射增强：JavaBean、构造器选择、列名映射、基础类型返回。
3. Spring 可用性：Mapper Bean 注册、配置项、事务集成示例。
4. 兼容样例：MyBatis 迁移 fixtures 和差异文档。
5. 平台化能力：元数据导出、可观测性、缓存、分页 DSL、路由策略。

判断标准很简单：每个阶段都必须产出可运行、可测试、可解释的能力，而不是只增加抽象。
