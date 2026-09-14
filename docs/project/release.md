# Release Engineering

This document defines the repeatable checks and ownership boundaries for a
Lynxus public release. It is a release checklist, not a promise that a release
has been published.

## Release gates

Run these commands from a clean checkout of the release commit:

```bash
scripts/verify-release-metadata.sh
mvn --batch-mode --no-transfer-progress test
scripts/verify-database-test-matrix.sh
mvn --batch-mode --no-transfer-progress -pl lynxus-processor -am install -DskipTests
mvn --batch-mode --no-transfer-progress \
  -f lynxus-examples/external-spring-boot-consumer/pom.xml \
  -Dspring-boot.version=3.5.16 clean verify
gradle -p lynxus-examples/external-gradle-processor --no-daemon clean build
```

The database matrix gate must report a non-zero test count and zero failures,
errors, and skips for every PostgreSQL and MySQL suite. External consumers are
release evidence because a reactor-only build can hide missing published
artifact boundaries.

## Artifact policy

The root POM is the metadata owner for the Maven coordinates, project URL, and
Apache License 2.0 declaration inherited by published modules. The annotation
processor remains a separate artifact and must not be placed on a runtime
consumer classpath. Test support, benchmarks, and example applications are
verification fixtures, not public runtime artifacts.

Before publishing, the release owner must confirm the version is no longer a
`SNAPSHOT`, inspect the generated POMs, and verify that every distributed
artifact contains the license and required notices.

## Signing and publication

Publication requires a release-only Maven profile with the repository owner's
signing credentials. Credentials must be supplied through the CI secret store or
a local Maven `settings.xml`; never commit them or place them in a POM. The
publish job must run only after all release gates pass and must retain the
staging repository identifier and build logs as release evidence.

## Rollback

If publication is incomplete or an artifact is invalid:

1. stop the publish job and do not retry blindly;
2. close or drop the repository staging repository before releasing it;
3. remove or mark the corresponding Git tag and GitHub release as needed;
4. fix the source on a new commit and rerun every release gate;
5. never overwrite an immutable released version—increment the version instead.

Maven Central artifacts are treated as immutable. Rollback therefore means
stopping promotion and publishing a corrected version, not replacing bytes at
an existing coordinate.

## Governance

The release owner records the release commit, version, gate outputs, staging
identifier, published coordinates, and any known limitations in the release
notes. A release claim must not include unverified Spring Boot lines, skipped
database jobs, or migration behavior outside the compatibility contract.
