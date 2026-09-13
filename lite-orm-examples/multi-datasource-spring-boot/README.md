# Spring Boot Multi-DataSource Example

This independent consumer demonstrates the supported Spring Boot ownership
model: each disjoint Mapper package is bound to one named Spring `DataSource`.
LiteORM creates one `SqlExecutor` per binding; Spring still owns connection and
transaction participation.

The example uses inert `DriverManagerDataSource` instances so the context test
does not require a database. Replace the URLs and credentials with real pools
in an application, then use a transaction manager for the same DataSource as
the Mapper package binding.

Configure the package bindings as follows:

```yaml
lite-orm:
  mapper-bindings:
    - package-name: org.liteorm.example.multidatasource.user
      data-source: usersDataSource
    - package-name: org.liteorm.example.multidatasource.order
      data-source: ordersDataSource
```

Run it independently after installing the local LiteORM artifacts:

```bash
mvn -pl lite-orm-processor -am -DskipTests install
mvn -f lite-orm-examples/multi-datasource-spring-boot/pom.xml clean verify
```

For before/after migration guidance, see the
[`mybatis-migration-fixture`](../mybatis-migration-fixture/README.md) and the
[migration skill](../../docs/user/migration/using-migration-skill.md).
