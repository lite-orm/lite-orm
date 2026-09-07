# Spring Boot Integration

The Spring Boot starter registers generated Mapper implementations and binds each Mapper package to one named DataSource. Generated classes remain Spring-neutral and continue to depend only on `SqlExecutor`.

## Configure Mapper Packages

Registration is explicit even when the application has one DataSource:

```yaml
lite-orm:
  enabled: true
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: dataSource
```

At startup the starter:

1. scans the configured package for generated `*MapperImpl` classes;
2. resolves the named Spring `DataSource` bean;
3. creates one Spring-aware `SqlExecutor` for that DataSource;
4. registers each implementation under the JavaBeans-decapped interface name.

Mapper package bindings must not overlap. Applications with several DataSources use disjoint Mapper packages and a matching transaction manager for each domain.

## Transactions

Inside `@Transactional`, `SpringConnectionHandleFactory` obtains and releases the thread-bound connection through `DataSourceUtils`. Spring owns commit and rollback timing; LiteORM does not duplicate the transaction boundary.

The `PlatformTransactionManager` must manage the same DataSource named by the Mapper package binding. A mismatch fails explicitly.

## Extension Beans

Spring may discover and order `ExecutionInterceptor` beans. SQL providers, parameter binders, type handlers, and row mappers remain compile-time-selected generated dependencies rather than global runtime registries.

See the [Extension contracts](../../reference/extensions.md) for exact validation, lifecycle, and ownership rules.
