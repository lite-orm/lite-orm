# External Maven Annotation Processor Example

This directory is an independent Maven consumer project used by the
`lite-orm-processor` Maven Invoker verification.

It is intentionally not listed in the repository root `<modules>` section.
During `mvn -pl lite-orm-core verify`, Maven Invoker:

1. installs the built `lite-orm-core` and `lite-orm-processor` artifacts into an isolated repository;
2. copies this project into the core build directory;
3. replaces `@project.version@` with the version being verified;
4. runs `clean verify` as an external Maven build;
5. confirms that `LiteOrmProcessor` generates `ExternalUserMapperImpl` and that
   ordinary consumer code can compile against the generated implementation.

This verifies the published annotation-processor consumption path with Core as
the runtime dependency and Processor on the annotation processor path, rather
than relying on reactor-internal dependency resolution.
