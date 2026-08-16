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
  - 已保留不可变执行计划、`SqlExecutor`、事务和观察契约；旧 JDBC 运行时已删除并等待按新角色重新实现。
- `lite-orm-spring-boot-starter`
  - 提供 Spring Boot 自动配置入口。
  - 扫描并注册编译期生成的 Mapper 实现。
  - 当前仅保留生成 Mapper 注册入口；默认 JDBC 与 Spring 事务装配将在新地基完成后实现。

编译期与运行期闭环已经可用。旧 `*Engine`、processor chain、可变 `ExecutionContext`、连接提供器/事务协调器和全局配置单例已经物理删除。生成 Mapper 只依赖 `SqlExecutor`；core 提供固定 JDBC 执行器，Spring 通过事务适配器参与连接生命周期。

`org.liteorm.compile` 中只有 javac 需要加载的 `LiteOrmProcessor` 是公共类型；SQL 解析器、AST、编译模型和代码生成器均为内部实现，不作为应用扩展 API。

已验证能力包括：

- LiteORM 自有 `org.liteorm.annotation` 注解；项目不在 MyBatis/iBatis 命名空间下发布任何类。
- 使用 `org.liteorm.annotation.Mapper` 声明的 MyBatis 风格 Mapper 接口。
- `@Select`、`@Insert`、`@Update`、`@Delete` 注解输入。
- XML-backed Mapper 方法。
- `@Param`、`param1`、`arg0`、`list`、`collection`、`array` 等常见参数命名。
- 动态 SQL 标签：`if`、`choose`、`when`、`otherwise`、`trim`、`where`、`set`、`foreach`、`sql`、`include`。
- JDBC 批处理：注解 `@Batch` 与 XML `<batch>`，编译期生成逐项参数绑定循环，返回 JDBC `int[]` 更新计数。
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
SqlExecutor
        |
        v
JdbcSqlExecutor (一个实例绑定一个 DataSource/事务域)
        |
        +--> ExecutionInterceptor
        +--> ConnectionHandleFactory
        +--> ConnectionHandle.connection
        +--> PreparedStatement / 参数绑定 / JDBC 执行
        +--> 结果提取与反向资源释放
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

固定 JDBC 阶段不是开放的责任链扩展点。日志、指标、审计和慢查询使用有序 `ExecutionInterceptor`；特殊 SQL、参数和结果能力分别使用编译期绑定的 Provider、Binder 和 RowMapper。

### core 与 Spring 的职责边界

这里不能简单理解成“`Simple*` 在生产环境全部替换掉”。core 中同时存在四类内容：框架永久保留的领域模型、Spring 需要实现的宿主 SPI、无容器环境的默认实现，以及本来就属于应用基础设施的能力。

| 分类 | 判断标准 | Spring 环境中的处理方式 |
| --- | --- | --- |
| core 永久保留 | ORM 自身的编译、执行和扩展语义，与依赖注入容器无关 | 继续直接使用，不委托给 Spring |
| core 定义 SPI，宿主提供实现 | core 必须依赖抽象，但连接参与方式取决于运行环境 | Spring Starter 提供对应实现并注入 core 组件 |
| core 默认实现 | 为无 Spring 环境提供可直接运行的最小实现 | Spring 环境通常不创建这些默认实现 |
| 外部基础设施 | 不属于 ORM 的职责 | 由应用、连接池、数据库或 Spring 基础设施提供 |

完整角色区分如下：

| 能力/角色 | core 中的定义或实现 | Spring 生产环境 | 归属结论 |
| --- | --- | --- | --- |
| Mapper 编译模型、XML/注解解析、动态 SQL AST、Java 代码生成 | `CompilePipeline`、`MapperCompilationModel`、`CodeGenerator` 等 | 仍由 LiteORM 注解处理器在编译期完成 | **core 永久保留，不委托 Spring** |
| 生成 Mapper | `XxxMapperImpl`，构造器只依赖 `SqlExecutor` | Starter 仅负责注册和注入这个普通 Java 类 | **生成代码属于 LiteORM；Bean 生命周期委托 Spring** |
| SQL 执行入口 | `SqlExecutor`、`ExecutionPlan`、`SqlResult` | 原样使用 | **core 永久保留** |
| 固定 JDBC 生命周期 | `JdbcSqlExecutor` | 原样使用，由 Spring 装配 `SpringConnectionHandleFactory` | **core 永久保留，不由 Spring 重写 SQL 执行** |
| 参数和结果适配 | `ParameterBinder`、`RowMapper`、`ResultValueConverters` | 原样使用，必要时作为 Spring Bean 被装配 | **core 永久保留；对象发现可委托 Spring** |
| 执行观察 | `ExecutionInterceptor`、`ExecutionOutcome` | Spring 发现并按 ordering 排序 interceptor Bean | **契约和执行时机属于 core；实例管理委托 Spring** |
| 单次执行的连接句柄 | SPI：`ConnectionHandle`，只允许取得连接和释放资源 | 实现：`SpringConnectionHandle`，通过 `DataSourceUtils` 取得或释放连接 | **抽象属于 core；连接参与语义委托 Spring** |
| 连接句柄工厂 | SPI：`ConnectionHandleFactory` | 实现：`SpringConnectionHandleFactory` | **抽象属于 core；宿主实现由 Starter 提供** |
| 无容器事务句柄 | `SimpleTransaction` | 通常不创建 | **core 默认实现，仅用于独立运行或测试** |
| 无容器连接句柄工厂 | `SimpleConnectionHandleFactory` | 使用 `SpringConnectionHandleFactory` 代替 | **core 默认实现被 Spring 实现替代** |
| 事务边界控制 | SPI：`TransactionalExecutor`；默认实现：`SimpleTransactionalExecutor` | 使用 `@Transactional`、`TransactionTemplate` 和 `PlatformTransactionManager` | **边界抽象属于 core；生产边界时机委托 Spring** |
| 本地事务域保护 | `TransactionDomain`、`TransactionDomainGuard`、`SimpleTransactionDomainGuard` | 通过明确匹配的 executor、DataSource 和 transaction manager 保持同一约束；后续补充 Spring 多数据源启动校验 | **不直接一对一替换，但必须保持相同事务不变量** |
| 单 DataSource 手工装配 | `LiteOrm.jdbc(...)`、`JdbcAssembly` | `LiteOrmAutoConfiguration` 创建同样的 executor 组件图 | **领域组件不变；创建和依赖注入委托 Spring** |
| 多 DataSource 包绑定 | core 保持一个 Mapper 只依赖一个 `SqlExecutor` | Starter 根据 `mapper-bindings[].data-source` 装配并注入 executor | **包规则属于 Starter 装配；Mapper 热路径不感知 DataSource** |
| 动态 DataSource 路由 | core 不提供重复路由系统 | 使用 `AbstractRoutingDataSource`、dynamic-datasource 或其他 DataSource 代理 | **路由属于 DataSource 基础设施，LiteORM 只消费最终 DataSource** |
| 异常模型 | `LiteOrmException`、`SqlExecutionException`、`TransactionException` 等 | 原样向上层传播，保留 JDBC/Spring cause | **core 永久保留** |
| DataSource 和连接池 | core 只消费 `javax.sql.DataSource` | 应用配置 DataSource，通常由 HikariCP 和 Spring Boot 管理 | **外部基础设施，LiteORM 不实现** |
| 事务传播、隔离级别、超时、回滚规则 | core 简单实现只保证最小本地事务正确性 | 由 Spring `PlatformTransactionManager` 和 `@Transactional` 配置决定 | **生产高级事务策略委托 Spring** |
| 分布式事务 | core 不提供 | 需要应用引入外部事务系统 | **不属于 LiteORM core 或普通 Starter** |

最关键的判断是：Spring **不会替代 `SqlExecutor`、`JdbcSqlExecutor`、执行计划、生成 Mapper 和 JDBC 固定生命周期**。Spring 接管的是组件创建、连接线程绑定和事务边界时机。

两种装配后的依赖关系如下：

```text
独立运行：
Generated Mapper -> JdbcSqlExecutor -> SimpleConnectionHandleFactory -> SimpleTransaction -> DataSource
                              |
                              +-> SimpleTransactionalExecutor 控制事务边界

Spring 生产：
Generated Mapper -> JdbcSqlExecutor -> SpringConnectionHandleFactory -> SpringConnectionHandle -> DataSourceUtils
                                                                      |
                                                                      +-> Spring 绑定连接

@Transactional / PlatformTransactionManager --------------------------+-> 控制 begin/commit/rollback
```

### 事务与多数据源

- `ConnectionHandle` 只负责取得当前执行使用的连接和释放句柄，不暴露 `commit` 或 `rollback`。
- `ConnectionHandleFactory` 为每次执行返回一个知道连接参与方式的句柄；`SqlExecutor` 不判断连接由 core 还是 Spring 持有。
- core 的提交和回滚由 `SimpleTransactionalExecutor` 与内部 `SimpleTransaction` 控制；Spring 事务继续由 Spring 决定何时开始、提交和回滚。
- 一个 `JdbcSqlExecutor` 永久绑定一个 DataSource/事务域。多数据源通过多个独立、具名的 executor 组件图和 Mapper 实例装配，不把数据源名塞进 `ExecutionPlan`。
- core 不隐式协调跨数据源提交，也不提供分布式事务。动态租户、分片或读写路由优先由绑定的路由 `DataSource` 负责；只有无法由 DataSource 表达时，才显式增加更高层 `SqlExecutor` 装饰器。

## 独立 JDBC 接入

一个 `JdbcAssembly` 对应一个 DataSource 和事务域：

```java
JdbcAssembly assembly = LiteOrm.jdbc(dataSource)
    .domain("users")
    .build();

UserMapper userMapper = new UserMapperImpl(assembly.sqlExecutor());
```

事务外的 Mapper 调用使用临时 auto-commit 连接句柄。多个 Mapper 调用需要共用一个连接并一起提交或回滚时，使用回调式事务边界：

```java
User user = assembly.transactionalExecutor().execute(() -> {
    userMapper.insert(1L, "Alice", "alice@example.com", 30);
    return userMapper.findById(1L);
});
```

回调不暴露事务完成句柄，普通业务代码只调用生成 Mapper。嵌套回调加入当前根事务；嵌套工作失败会把这个本地事务标记为仅回滚，避免异常被外层捕获后错误提交。core 不实现传播枚举、保存点或声明式隔离/只读策略，这些线上事务能力由 Spring 或其他宿主事务管理器负责。

## Spring Boot 接入

Starter 不创建运行时 Mapper 代理，而是注册编译期已经生成的 `*MapperImpl`：

```yaml
lite-orm:
  enabled: true
  mapper-bindings:
    - package-name: com.example.mapper
      data-source: dataSource
```

应用启动时，Starter 扫描配置包中的生成类，解析 `data-source` 指定的 Spring `DataSource` Bean，并通过 `SpringConnectionHandleFactory` 与 core `JdbcAssembly` 创建 Mapper 所需的 `SqlExecutor`。该 Bean 可以是真实连接池，也可以是 `AbstractRoutingDataSource` 或 dynamic-datasource 提供的路由代理。Mapper 调用热路径不再查找 Spring Bean。

每个 Mapper 包和 Mapper 接口只绑定一个 DataSource 域。同一个包不能重复绑定，父子包规则也不能重叠；应用存在多个 DataSource 时，使用互不重叠的 Mapper 包分别绑定。单 DataSource 应用同样保留显式 `package-name + data-source` 配置，Starter 不推断默认 DataSource 或扫描包。

生成的 `*MapperImpl` 不包含 Spring `@Component`、注入或条件注解。Starter 在 IOC BeanDefinition 注册阶段发现生成类，按 Mapper 接口名的 JavaBeans decapitalize 规则注册一次，例如 `UserMapper` 注册为 `userMapper`，并注入该包对应的唯一 `SqlExecutor`。

Starter 始终使用应用提供的 `DataSource`。在 Spring `@Transactional` 范围内复用 Spring 绑定到当前线程的连接；事务外按数据源默认的 auto-commit 行为执行，并在每次调用后释放 JDBC 资源。

`@Transactional` 使用的 `PlatformTransactionManager` 必须管理 Mapper 绑定的同一个 DataSource。若当前活动事务没有绑定该 DataSource，LiteORM 会显式报告事务域不匹配，而不是静默脱离预期事务执行。

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

## 当前文档与后续路线

当前唯一有效的实施计划是：

- [LiteORM Runtime Architecture Implementation Plan](docs/plans/liteorm-runtime-architecture-implementation-plan.md)
- [MyBatis 兼容矩阵](docs/mybatis-compatibility.md)
- [MyBatis 迁移指南](docs/migration-guide.md)
- [扩展契约](docs/extensions.md)

当前固定 JDBC 生命周期、Standalone/Spring 事务适配、显式多数据源装配、外部 Maven 编译夹具和生成源码诊断均已实现。下一阶段是完成最终架构评审，再以可复现 benchmark 作为任何缓存或性能优化的准入条件。

判断标准很简单：每个阶段都必须产出可运行、可测试、可解释的能力，而不是只增加抽象。
