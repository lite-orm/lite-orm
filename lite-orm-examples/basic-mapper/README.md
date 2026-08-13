# LiteORM Basic Mapper Example

This module demonstrates LiteORM from the perspective of an external Maven project rather than an internal compiler unit test.

It covers:

- explicit registration of `org.liteorm.compile.LiteOrmProcessor` in `maven-compiler-plugin`;
- an annotation-backed Mapper with insert, select, update, and delete methods;
- an XML-backed Mapper loaded from the normal classpath resource location;
- generated `UserMapperImpl` and `UserXmlMapperImpl` classes;
- XML dynamic `where`, `foreach`, and `set` behavior;
- a compile-time-bound SQL provider, custom parameter binder, and custom row mapper;
- an execution interceptor observing generated Mapper execution without changing dispatch;
- real execution and result assertions against an H2 in-memory database.

If a Mapper method contains both an XML statement and a SQL annotation, LiteORM compiles the XML statement and emits a method-scoped compiler warning explaining that XML overrides the annotation.

Run the example from the repository root:

```bash
mvn -pl lite-orm-examples/basic-mapper -am clean test
```

Generated sources are written under:

```text
lite-orm-examples/basic-mapper/target/generated-sources/annotations
```

Application code constructs a generated Mapper with a LiteORM `ConnectionManager`:

```java
SimpleConnectionManager connectionManager = new SimpleConnectionManager(dataSource);
UserMapper mapper = new UserMapperImpl(connectionManager);
```
