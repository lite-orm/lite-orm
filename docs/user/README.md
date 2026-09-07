# LiteORM User Documentation

LiteORM documentation is organized by the task or module a user is working with. Start with the quick start, then open only the module-specific guide you need.

## Start Here

- [Quick start](getting-started.md): add LiteORM, define a Mapper, and configure annotation processing.
- [Architecture](architecture.md): understand the compile-time and runtime boundaries.

## Core

- [Core guides](core/README.md): compilation, mapping, extensions, and standalone runtime.
- [Choosing a value or row mapping](core/mapping.md): choose between JDBC type mappings, parameter binders, generated result mapping, and row mappers.
- [Choosing an extension](core/extensions.md): select the narrowest provider, binder, mapper, interceptor, decorator, or raw-JDBC boundary.
- [Standalone JDBC](core/standalone.md): assemble generated Mappers without Spring.

## Integrations

- [Spring guides](spring/README.md): Spring-owned assembly and transaction participation.
- [Spring Boot](spring/spring-boot.md): register Mapper packages and participate in Spring transactions.
- [Database type modules](database-types/README.md): select a complete database-family mapping collection.
- [PostgreSQL JDBC types](database-types/postgresql.md): use the official PostgreSQL mapping collection.
- [MySQL JDBC types](database-types/mysql.md): use the official MySQL mapping collection.

## Migration

- [Migration guides](migration/README.md): compatibility-first migration entry point.
- [Migrating from MyBatis](migration/from-mybatis.md): migrate supported Mapper declarations and replace unsupported runtime features explicitly.

Normative behavior is defined in the [reference documentation](../README.md#reference). Active implementation work is tracked in GitHub Issues rather than user guides.
