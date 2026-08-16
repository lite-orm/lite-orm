# External Maven Annotation Processor Example

This directory is an independent Maven consumer project used by the
`lite-orm-core` Maven Invoker verification.

It is intentionally not listed in the repository root `<modules>` section.
During `mvn -pl lite-orm-core verify`, Maven Invoker:

1. installs the built `lite-orm-core` artifact into an isolated repository;
2. copies this project into the core build directory;
3. replaces `@project.version@` with the version being verified;
4. runs `clean verify` as an external Maven build;
5. confirms that `LiteOrmProcessor` generates `ExternalUserMapperImpl` and that
   ordinary consumer code can compile against the generated implementation.

This verifies the published annotation-processor consumption path rather than
reactor-internal dependency resolution.
