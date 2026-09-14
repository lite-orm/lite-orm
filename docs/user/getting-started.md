# Quick Start

Kervix requires Java 21. It generates ordinary Java Mapper implementations during annotation processing and executes them through a small JDBC runtime.

## 0. Install the Kervix Skill (recommended)

Kervix is designed for agent-led development. Install the unified `kervix` skill before starting so your agent can scan the project, create a bounded TODO list, configure Kervix incrementally, and switch into migration mode when it finds an existing MyBatis project. The recommended commands download the small GitHub Release artifact rather than the full Kervix source tree; each is a single copy-paste line. Pin a versioned Release URL when reproducibility matters.

Codex:

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/kervix/kervix/releases/latest/download/kervix-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/kervix-skill" "${CODEX_HOME:-$HOME/.codex}/skills/kervix"
```

Claude Code:

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/kervix/kervix/releases/latest/download/kervix-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p "$HOME/.claude/skills" && cp -R "$tmp/kervix-skill" "$HOME/.claude/skills/kervix"
```

Cursor (project-local):

```bash
tmp=$(mktemp -d) && curl -fsSL https://github.com/kervix/kervix/releases/latest/download/kervix-skill.tar.gz | tar -xz -C "$tmp" && mkdir -p .cursor/skills && cp -R "$tmp/kervix-skill" .cursor/skills/kervix
```

If `curl` is unavailable, use this `wget` one-liner for Codex:

```bash
tmp=$(mktemp -d) && wget -qO "$tmp/kervix-skill.tar.gz" https://github.com/kervix/kervix/releases/latest/download/kervix-skill.tar.gz && tar -xzf "$tmp/kervix-skill.tar.gz" -C "$tmp" && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/kervix-skill" "${CODEX_HOME:-$HOME/.codex}/skills/kervix"
```

From a local checkout, the equivalent one-liner is:

```bash
tmp=$(mktemp -d) && git clone --depth 1 https://github.com/kervix/kervix.git "$tmp/kervix" >/dev/null && mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills" && cp -R "$tmp/kervix/skills/kervix" "${CODEX_HOME:-$HOME/.codex}/skills/kervix"
```

Then ask your agent to use `$kervix`. The same skill covers new integrations and MyBatis migration; it scans first, writes a reviewable TODO list, and pauses when a decision needs you.

## 1. Add the Dependency

For Spring Boot:

```xml
<dependency>
    <groupId>io.github.kervix</groupId>
    <artifactId>kervix-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For standalone JDBC, depend on `kervix-core` and add `kervix-processor` to the compiler's
annotation processor path. While working from this repository, install snapshots locally first:

```bash
mvn -DskipTests install
```

Explicitly enable the processor with Maven:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <proc>full</proc>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.kervix</groupId>
                <artifactId>kervix-processor</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
        <annotationProcessors>
            <annotationProcessor>io.github.kervix.compile.KervixProcessor</annotationProcessor>
        </annotationProcessors>
    </configuration>
</plugin>
```

## 2. Define a Mapper

```java
package com.example.user.mapper;

public record User(Long id, String name) {
}
```

```java
package com.example.user.mapper;

import io.github.kervix.annotation.Insert;
import io.github.kervix.annotation.Mapper;
import io.github.kervix.annotation.Param;
import io.github.kervix.annotation.Select;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO users (id, name) VALUES (#{id}, #{name})")
    int insert(@Param("id") Long id, @Param("name") String name);

    @Select("SELECT id, name FROM users WHERE id = #{id}")
    User findById(@Param("id") Long id);
}
```

Compilation generates `UserMapperImpl` under `target/generated-sources/annotations`. The implementation directly implements `UserMapper` and receives one `SqlExecutor` through its constructor.

## 3. Assemble the Mapper

For Spring Boot, bind the Mapper package to a named DataSource:

```yaml
kervix:
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: dataSource
```

For standalone use, create a [JDBC assembly](core/standalone.md) and construct the generated implementation directly.

## Next Steps

- Read the [architecture overview](architecture.md).
- Choose a [value or row mapping strategy](core/mapping.md).
- Configure the [Spring Boot integration](spring/spring-boot.md).
- Run the [basic Mapper example](../../kervix-examples/basic-mapper/README.md).
