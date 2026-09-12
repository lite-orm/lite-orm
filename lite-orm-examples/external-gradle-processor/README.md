# External Gradle Annotation Processor Example

This directory is an independent Gradle consumer of the locally installed `lite-orm-core` artifact. It is not part of the Maven reactor.

Verify it from the repository root after installing Core:

```bash
mvn -pl lite-orm-core -am -DskipTests install
gradle -p lite-orm-examples/external-gradle-processor clean build
```

The fixture declares the same Core coordinate as both `implementation` and `annotationProcessor`. Compilation proves that Gradle runs `LiteOrmProcessor`, generates `ExternalUserMapperImpl`, and compiles ordinary consumer code against that implementation without a database-specific type artifact.
