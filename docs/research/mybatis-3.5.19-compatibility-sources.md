# MyBatis 3.5.19 Compatibility Evidence

## Scope

This note records primary-source evidence for Issue #17's compatibility matrix. MyBatis
references are pinned to the `mybatis-3.5.19` tag. LiteORM statements are taken from the
repository contracts listed below. A statement marked **Inference** is a compatibility
conclusion, not a claim about MyBatis behavior.

Primary MyBatis entry points:

- [3.5.19 reference documentation](https://mybatis.org/mybatis-3/)
- [3.5.19 source tree](https://github.com/mybatis/mybatis-3/tree/mybatis-3.5.19)

LiteORM contract sources:

- [`docs/reference/mybatis-compatibility.md`](../reference/mybatis-compatibility.md)
- [`docs/reference/core-contract.md`](../reference/core-contract.md)
- [`docs/reference/extensions.md`](../reference/extensions.md)
- [`docs/user/migration/from-mybatis.md`](../user/migration/from-mybatis.md)

## Evidence Matrix

| Domain | MyBatis 3.5.19 fact | LiteORM contract and compatibility implication |
| --- | --- | --- |
| Annotations | Mapper annotations are runtime-retained method/type metadata. `@Select` can carry SQL, `databaseId`, and `affectData`; `@Result` can carry column/property, Java/JDBC types, a TypeHandler, and `one`/`many` relationships. `@Options` also controls cache, statement, fetch, timeout, and generated-key behavior. | LiteORM uses its own annotations and validates them during annotation processing. Static CRUD, flat results, explicit generated keys, providers, binders, and row mappers are supported. There is no `org.apache.ibatis.annotations` compatibility package or LiteORM `@Options` contract. **Inference:** imports and annotation semantics require migration even when the Java method shape is similar. |
| XML and dynamic SQL | MyBatis parses mapper XML into configuration elements and statement nodes. `XMLScriptBuilder.parseScriptNode()` builds `SqlNode` trees; dynamic statements become `DynamicSqlSource`, whose `getBoundSql()` evaluates the tree for each invocation. `IfSqlNode` and `ForEachSqlNode` evaluate expressions at execution time through `ExpressionEvaluator`/`OgnlCache`. The default language also expands `${...}` text and binds `#{...}` parameters. | LiteORM accepts a controlled XML/`<script>` subset, translates supported expressions to Java, and rejects arbitrary OGNL, unsafe `${...}`, and unsupported tags at compile time. XML is not interpreted at runtime. **Inference:** deterministic XML can be migrated or compiled, but runtime expression semantics, custom language drivers, and dynamic text substitution are boundaries rather than drop-in compatibility. |
| Result mapping | `ResultMap` stores mappings for properties and constructors and records nested result-map/query relationships. Each `ResultMapping` carries `javaType`, optional `jdbcType`, and a resolved `TypeHandler`; `DefaultResultSetHandler` calls the handler to read each mapped value. Auto-mapping matches columns to properties ignoring case, and the documented model includes nested associations, collections, discriminators, and lazy nested queries. | LiteORM generates scalar, record, JavaBean, list, and optional mapping, with flat `resultMap` support and method-scoped `RowMapper` escape hatches. Its Core contract uses typed `QueryResult<T>` with immutable non-null rows and keeps mapping inside the fixed JDBC lifecycle. Complex graphs, associations, collections, nested selects, lazy loading, and discriminator-style aggregation are outside the contract. **Inference:** simple `resultType`/flat `resultMap` cases are candidates for direct migration; graph mapping needs explicit follow-up queries, a row mapper, or raw JDBC. |
| Sessions and transactions | `SqlSession` is MyBatis's primary execution interface and explicitly includes mapped commands, Mapper acquisition, and transaction management. `DefaultSqlSession.selectOne()` returns `null` for zero rows and throws for more than one; `commit()`, `rollback()`, and `close()` operate on its executor. The official lifecycle guide says `SqlSession` is not thread-safe and should be scoped per request/method, while `SqlSessionFactory` is application-scoped. | Generated LiteORM Mappers depend only on `SqlExecutor`; Core owns one fixed JDBC statement lifecycle. Standalone transaction completion belongs to `TransactionalExecutor`; Spring owns hosted transaction completion. There is no `SqlSession`, runtime Mapper proxy, session-scoped state, or distributed transaction contract. **Inference:** migration must replace session acquisition/closing and session-level transaction calls with explicit Mapper assembly and the appropriate LiteORM transaction boundary. |
| Cache | MyBatis enables local session caching by default and can enable namespace-level second-level caching with `<cache>`. `CachingExecutor` wraps the delegate executor, reads/writes query results through `TransactionalCacheManager`, flushes caches for writes when required, and coordinates cache commit/rollback. | LiteORM explicitly has no first- or second-level ORM cache and no cache namespace contract. Application caches remain outside LiteORM. **Inference:** a MyBatis cache declaration cannot be preserved as an equivalent LiteORM Mapper feature; its invalidation, identity, and transaction behavior must move to the application or be removed deliberately. |
| Plugins/interceptors | MyBatis `Interceptor` wraps low-level `Executor`, `ParameterHandler`, `ResultSetHandler`, and `StatementHandler` calls. `InterceptorChain.pluginAll()` successively decorates targets, so plugins can alter execution, binding, mapping, and transaction-adjacent methods. | LiteORM exposes typed `ExecutionInterceptor` observation around the fixed executor lifecycle. It does not provide a general runtime SQL-rewrite or low-level handler plugin chain. Providers, binders, row mappers, and decorators each have narrower ownership. **Inference:** logging/metrics/audit can map to `ExecutionInterceptor`; behavior-changing MyBatis plugins require a supported typed extension, application/DataSource policy, or raw JDBC. |
| Spring integration | MyBatis core documentation treats dependency-injection integration as a separate concern and points users to MyBatis-Spring/ MyBatis-Guice for thread-safe transactional sessions and mappers. The 3.5.19 core APIs themselves center on `SqlSessionFactory`/`SqlSession`, not Spring beans. | LiteORM Spring Starter registers generated classes, binds Mapper packages to named DataSources, supplies host-aware connection participation, and uses Spring transaction managers. Generated Mappers and Core execution remain Spring-neutral; the starter must not duplicate JDBC execution. **Inference:** Spring compatibility is an assembly/transaction boundary, not compatibility with MyBatis's session proxy model. |
| Migration boundaries | MyBatis supports both annotations and XML, peer XML loading for annotated Mappers, named statements, generated keys, `RowBounds`, cursors, custom language drivers, TypeHandler registries, nested mappings, caches, and plugins. | LiteORM directly targets deterministic SQL that can become ordinary generated Java. It supports annotation/XML CRUD, controlled dynamic SQL, typed result variants, explicit providers/binders/row mappers, cursors, batch, generated keys, standalone transactions, and Spring participation. Runtime XML reload, arbitrary OGNL, runtime proxies, complex object graphs, caches, full plugins, same-Mapper multi-DataSource binding, and distributed transactions are explicit non-goals. **Inference:** the scanner/matrix should classify each Mapper method by these ownership boundaries instead of treating "uses MyBatis XML" as a compatibility verdict. |

## Source Details

### Annotations and Statement Construction

- [`org/apache/ibatis/annotations/Select.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/annotations/Select.java):
  runtime-retained method annotation with `String[] value`, `databaseId`, and `affectData`.
- [`org/apache/ibatis/annotations/Result.java`](https://github.com/mybatis/mybatis-3.5.19/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/annotations/Result.java):
  result mapping metadata including `column`, `property`, `javaType`, `jdbcType`,
  `typeHandler`, `one`, and `many`.
- [`org/apache/ibatis/annotations/Options.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/annotations/Options.java):
  statement options including cache policy, statement type, fetch size, timeout,
  and generated-key settings.
- [`org/apache/ibatis/builder/annotation/MapperAnnotationBuilder.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/builder/annotation/MapperAnnotationBuilder.java):
  `parse()` loads a peer XML resource when present, parses cache metadata, result maps,
  and annotated statements.
- [Getting Started: mapped statements and annotation/XML choice](https://mybatis.org/mybatis-3/getting-started.html#Exploring_Mapped_SQL_Statements):
  annotations are described as suitable for simple statements, while XML is recommended
  for more complicated mappings.

### XML and Dynamic SQL

- [`org/apache/ibatis/builder/xml/XMLMapperBuilder.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/builder/xml/XMLMapperBuilder.java):
  `parse()` processes mapper configuration, cache, result maps, SQL fragments, and
  select/insert/update/delete statements.
- [`org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/XMLScriptBuilder.java):
  turns XML elements into `SqlNode` objects and selects `DynamicSqlSource` when content
  is dynamic.
- [`org/apache/ibatis/scripting/xmltags/DynamicSqlSource.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/DynamicSqlSource.java):
  evaluates the root node and creates `BoundSql` in `getBoundSql(Object parameterObject)`.
- [`org/apache/ibatis/scripting/xmltags/IfSqlNode.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/IfSqlNode.java)
  and [`ForEachSqlNode.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/ForEachSqlNode.java):
  evaluate conditional and iterable expressions against the invocation context.
- [`org/apache/ibatis/scripting/xmltags/TextSqlNode.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/scripting/xmltags/TextSqlNode.java):
  expands `${...}` through OGNL-backed evaluation; this is distinct from `#{...}` prepared
  parameter binding.
- [Dynamic SQL reference](https://mybatis.org/mybatis-3/dynamic-sql.html):
  documents `if`, `choose`, `trim`/`where`/`set`, `foreach`, `script`, `bind`, OGNL
  expressions, and pluggable language drivers.
- [Mapper XML reference](https://mybatis.org/mybatis-3/sqlmap-xml.html#Parameters):
  documents `#{...}` parameter mappings and statement attributes, including generated
  keys, timeout, fetch size, cache, and `databaseId`.

### Result Mapping and JDBC Conversion

- [`org/apache/ibatis/mapping/ResultMapping.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/mapping/ResultMapping.java):
  fields include `property`, `column`, `javaType`, `jdbcType`, `typeHandler`,
  `nestedResultMapId`, and `nestedQueryId`; `Builder.build()` resolves a TypeHandler.
- [`org/apache/ibatis/mapping/ResultMap.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/mapping/ResultMap.java):
  separates constructor, property, ID, and nested-result mappings.
- [`org/apache/ibatis/executor/resultset/DefaultResultSetHandler.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/executor/resultset/DefaultResultSetHandler.java):
  applies explicit mappings and automatic mappings through TypeHandlers.
- [`org/apache/ibatis/type/TypeHandlerRegistry.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/type/TypeHandlerRegistry.java):
  maintains Java-type/JDBC-type handler maps, registrations, and unknown-handler fallback.
- [Result Maps and auto-mapping reference](https://mybatis.org/mybatis-3/sqlmap-xml.html#Result_Maps):
  documents explicit mappings, automatic case-insensitive column/property matching, and
  nested association/collection examples.
- [TypeHandler configuration reference](https://mybatis.org/mybatis-3/configuration.html#typeHandlers):
  states that TypeHandlers are used both for PreparedStatement parameters and ResultSet
  retrieval, and documents default Java/JDBC routes and enum handling.

### Sessions, Transactions, Caches, and Plugins

- [`org/apache/ibatis/session/SqlSession.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/session/SqlSession.java):
  exposes select/insert/update/delete, cursor/result-handler operations, commit, rollback,
  close, configuration, and Mapper acquisition.
- [`org/apache/ibatis/session/defaults/DefaultSqlSession.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/session/defaults/DefaultSqlSession.java):
  implements `selectOne`, `selectList`, transaction methods, resource close, and
  `getMapper`.
- [`org/apache/ibatis/session/defaults/DefaultSqlSessionFactory.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/session/defaults/DefaultSqlSessionFactory.java):
  creates `DefaultSqlSession` instances from configured executors and transactions.
- [`org/apache/ibatis/executor/CachingExecutor.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/executor/CachingExecutor.java):
  caches query results and coordinates cache flush/commit/rollback around a delegate executor.
- [`org/apache/ibatis/plugin/Interceptor.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/plugin/Interceptor.java)
  and [`InterceptorChain.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/plugin/InterceptorChain.java):
  define invocation interception and successive target wrapping.
- [`org/apache/ibatis/transaction/jdbc/JdbcTransaction.java`](https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/transaction/jdbc/JdbcTransaction.java):
  obtains connections lazily and directly performs JDBC commit, rollback, and close.
- [Scope and lifecycle](https://mybatis.org/mybatis-3/getting-started.html#Scope_and_Lifecycle):
  documents application-scoped `SqlSessionFactory`, non-thread-safe `SqlSession`, and
  request/method-scoped Mapper instances.
- [Cache reference](https://mybatis.org/mybatis-3/sqlmap-xml.html#cache) and
  [plugin reference](https://mybatis.org/mybatis-3/configuration.html#plugins):
  document local/second-level cache behavior and the low-level interception points.

## Matrix Guidance

The compatibility matrix should classify a MyBatis Mapper method in this order:

1. **Direct:** static SQL, supported annotations/XML, scalar/record/JavaBean/flat results,
   and standard JDBC values.
2. **Compile-time rewrite:** supported dynamic tags and expressions that can be translated
   to generated Java without retaining an expression engine.
3. **Typed extension:** validated SQL structure (`SqlProvider`), one-value parameter conversion
   (`ParameterBinder`), one-row-shape conversion (`RowMapper`), or lifecycle observation
   (`ExecutionInterceptor`).
4. **Explicit boundary:** nested graph/lazy loading, cache/session semantics, arbitrary OGNL,
   custom language drivers, behavior-changing plugins, or transaction policy owned by a
   framework.
5. **Raw JDBC/application migration:** behavior whose ownership cannot remain within LiteORM's
   fixed executor lifecycle and compile-time Mapper model.

The main compatibility conclusion is therefore **deterministic Mapper semantics, not
runtime architecture parity**. MyBatis's runtime session, expression, cache, plugin, and
object-graph machinery must not be inferred from a method's SQL text alone.
