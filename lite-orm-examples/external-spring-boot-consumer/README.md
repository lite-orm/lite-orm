# External Spring Boot Consumer

This fixture verifies that a published-style Maven consumer can compile a
generated LiteORM Mapper alongside the Spring Boot Starter. It is independent
from the repository reactor and keeps the annotation processor on the compiler
path only.

Override `spring-boot.version` to probe a supported release anchor:

```bash
mvn -f lite-orm-examples/external-spring-boot-consumer/pom.xml \
  -Dspring-boot.version=3.1.5 clean verify
```

The fixture is compile-only. It does not start an application or connect to a
database; runtime DataSource binding and transaction behavior remain covered by
the Starter's PostgreSQL/MySQL Testcontainers suite.
