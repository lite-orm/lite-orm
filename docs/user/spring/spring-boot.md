# Spring Boot Integration

The Spring Boot starter registers generated Mapper implementations and binds each Mapper package to one named DataSource. Generated classes remain Spring-neutral and continue to depend only on `SqlExecutor`.

## Configure Mapper Packages

Registration is explicit even when the application has one DataSource:

```yaml
lite-orm:
  enabled: true
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: usersDataSource
    - package-name: com.example.order.mapper
      data-source: ordersDataSource
```

Each binding owns the generated Mappers in that package and its subpackages:
`com.example.user.mapper` uses `usersDataSource`, while
`com.example.order.mapper` uses `ordersDataSource`. Packages must be disjoint;
overlapping parent and child rules fail at startup instead of selecting a
DataSource implicitly.

At startup the starter:

1. scans the configured package for generated `*MapperImpl` classes;
2. resolves the named Spring `DataSource` bean;
3. creates one Spring-aware `SqlExecutor` for that DataSource;
4. registers each implementation under the JavaBeans-decapped interface name.

Mapper package bindings must not overlap. Applications with several DataSources use disjoint Mapper packages and a matching transaction manager for each domain.

Package binding selects only the Spring `DataSource` domain. It does not select a
JDBC mapping family. Standard Java/JDBC values are routed by Core's fixed,
database-independent `TypeHandlerManager`; the Starter does not inspect the
database product, register package mappings, or choose handlers per package.

## Transactions

Inside `@Transactional`, `SpringConnectionHandleFactory` obtains and releases the thread-bound connection through `DataSourceUtils`. Spring owns commit and rollback timing; LiteORM does not duplicate the transaction boundary.

The `PlatformTransactionManager` must manage the same DataSource named by the Mapper package binding. A mismatch fails explicitly.

## Version and Consumer Verification

Java 21 is the current baseline. The repository build uses Spring Boot 3.1.5;
the external consumer and runtime Starter suite are verified against Boot 3.1.5,
3.5.16, and 4.1.1. These anchors do not automatically cover every minor or
patch release; an unlisted line remains unverified until it passes both gates. The
independent consumer fixture is documented in
[`external-spring-boot-consumer`](../../../lite-orm-examples/external-spring-boot-consumer/README.md).

## Extension Beans

Spring may discover and order `ExecutionInterceptor` beans. SQL providers, parameter binders, type handlers, and row mappers remain compile-time-selected generated dependencies rather than global runtime registries.

See the [Extension contracts](../../reference/extensions.md) for exact validation, lifecycle, and ownership rules.
