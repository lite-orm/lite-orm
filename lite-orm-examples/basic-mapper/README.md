# LiteORM Basic Mapper Example

This module is an executable Maven consumer of `lite-orm-core`. It demonstrates:

- annotation and XML Mapper compilation;
- native Java dynamic `where`, `choose`, `trim`, `set`, `foreach`, `bind`, and `include` rendering;
- direct SQL provider, parameter binder, and row mapper invocation;
- annotation and XML JDBC batch plans;
- generated-key, scalar, record, and JavaBean result shapes;
- standalone `JdbcAssembly`, local transaction callbacks, rollback, and H2 execution.

Run it from the repository root:

```bash
mvn -pl lite-orm-examples/basic-mapper -am clean test
```

Generated sources are written to:

```text
lite-orm-examples/basic-mapper/target/generated-sources/annotations
```

## Standalone Assembly

The generated implementation receives one `SqlExecutor` from one DataSource assembly:

```java
JdbcAssembly assembly = LiteOrm.jdbc(dataSource)
    .domain("example")
    .interceptors(List.of(new MigrationAuditInterceptor()))
    .build();

UserMapper mapper = new UserMapperImpl(assembly.sqlExecutor());
```

Use the callback executor for several Mapper calls in one local transaction:

```java
User user = assembly.transactionalExecutor().execute(() -> {
    mapper.insert(1L, "Alice", "alice@example.com", 30);
    return mapper.findById(1L);
});
```

The callback commits once when it returns normally and rolls back when it throws. Calls outside the callback use independent auto-commit handles.

## Batch Contract

JDBC batch methods accept exactly one `List<T>` and return the raw `int[]` update counts:

```java
@Batch("INSERT INTO users (id, name) VALUES (#{item.id}, #{item.name})")
int[] insertBatch(List<User> users);
```

The XML equivalent uses `<batch id="insertBatch">`. LiteORM generates the per-item parameter loop as Java source; it does not resolve collection paths through a runtime expression engine.

If a Mapper method has both XML SQL and a SQL annotation, the XML statement wins and javac emits a warning on the corresponding Mapper method.
