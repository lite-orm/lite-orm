# LiteORM Basic Mapper Example

This module demonstrates LiteORM from the perspective of an external Maven project rather than an internal compiler unit test.

It covers:

- explicit registration of `org.liteorm.compile.LiteOrmProcessor` in `maven-compiler-plugin`;
- an annotation-backed Mapper with insert, select, update, and delete methods;
- an XML-backed Mapper loaded from the normal classpath resource location;
- generated `UserMapperImpl` and `UserXmlMapperImpl` classes;
- XML dynamic `where`, `foreach`, and `set` behavior;
- a compile-time-bound SQL provider, custom parameter binder, and custom row mapper;
- an execution interceptor compiled against the read-only observation contracts;
- generated JDBC batch plans from both `@Batch` and XML `<batch>` declarations.

If a Mapper method contains both an XML statement and a SQL annotation, LiteORM compiles the XML statement and emits a method-scoped compiler warning explaining that XML overrides the annotation.

Run the example from the repository root:

```bash
mvn -pl lite-orm-examples/basic-mapper -am clean test
```

Generated sources are written under:

```text
lite-orm-examples/basic-mapper/target/generated-sources/annotations
```

Generated Mapper implementations take the stable `SqlExecutor` role:

```java
SqlExecutor sqlExecutor = plan -> {
    throw new UnsupportedOperationException("Configure JdbcSqlExecutor when available");
};
UserMapper mapper = new UserMapperImpl(sqlExecutor);
```

JDBC batch methods accept exactly one `List<T>` and return the raw JDBC `int[]` update counts:

```java
@Batch("INSERT INTO users (id, name) VALUES (#{item.id}, #{item.name})")
int[] insertBatch(List<User> users);
```

The XML equivalent uses `<batch id="insertBatch">`. LiteORM generates the per-item parameter loop as Java source; it does not interpret the collection path at runtime.

The obsolete engine/connection-provider examples were removed. End-to-end JDBC and transaction examples will return with `JdbcSqlExecutor` and the new assembly roles.
