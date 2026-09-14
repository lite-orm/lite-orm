# Lynxus Native Image Example

This is an independent Maven consumer that shows the complete Lynxus AOT
boundary:

```text
Mapper source
    -> Lynxus annotation processor
    -> generated NativeImageMapperImpl
    -> Lynxus Core
    -> ARM64 Native Image
```

The example does not use MyBatis, Spring, runtime Mapper proxies, or reflection
configuration. Its `SqlExecutor` is a small deterministic fixture so the
example verifies generated Mapper dispatch and result construction without
requiring a database.

## Run

From the repository root:

```bash
chmod +x lynxus-examples/native-image/verify-native-image.sh
lynxus-examples/native-image/verify-native-image.sh
```

The script installs the local Core and processor artifacts, compiles the
consumer with annotation processing enabled, builds a `linux/arm64`
GraalVM Native Image with `--no-fallback`, and runs the native executable.
Docker is required.

The script passes these proxy variables to Docker:

```text
HTTP_PROXY=http://127.0.0.1:7890
HTTPS_PROXY=http://127.0.0.1:7890
ALL_PROXY=socks5://127.0.0.1:7890
```

Apple Silicon M1/M2/M3 machines are supported because the example targets
`linux/arm64`.
