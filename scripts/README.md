# scripts/

Helper scripts for the Agrilliant project. None are required for normal
development — the desktop build (`mvn -Pdesktop javafx:run`) works without
any of these.

## Files

### `wsl-setup-android-build-env.sh`

One-shot installer for a local Linux Android build environment. Use this
**only if** you want to run `mvn -Pandroid gluonfx:build` on your own Linux
host (WSL2 Ubuntu on Windows, or a real Linux machine).

The primary build path for Windows users is the **GitHub Actions workflow**
at `.github/workflows/android-build.yml` — see `docs/CI_ANDROID_BUILD.md`
for usage. That path needs zero local Linux setup.

This script is the fallback for developers who want a local build (faster
iteration on AOT reflection-config issues, no network round-trip per build).

#### Usage

Inside an Ubuntu 22.04 (or newer) shell — WSL2 or native:

```bash
bash scripts/wsl-setup-android-build-env.sh
```

Idempotent. Installs ~10 GB:

- apt deps (gcc, g++, libgtk, libfreetype, libavcodec, etc.)
- SDKMAN
- Liberica NIK 24 (GraalVM-compatible JDK with native-image)
- Android cmdline-tools, platform-tools, `platforms;android-34`,
  `build-tools;34.0.0`, `ndk;26.1.10909125`

Persists env vars (`GRAALVM_HOME`, `JAVA_HOME`, `ANDROID_HOME`,
`ANDROID_NDK`) into `~/.bashrc`. Open a new shell after running.

#### Override versions

Set env vars before invoking:

```bash
NIK_VERSION=24.1.r23-nik \
ANDROID_SDK_PLATFORM=34 \
ANDROID_NDK_VERSION=26.1.10909125 \
bash scripts/wsl-setup-android-build-env.sh
```

#### Then build

```bash
# Apply the SQL migration on your MySQL first (one-time):
mysql -h <host> -u <user> -p <db> < docs/sql/2026-05-14-fix-growthstage-growing.sql

# Then:
mvn -Pandroid clean compile           # cheap sanity gate (~30s)
mvn -Pandroid gluonfx:build           # AOT, ~10-30 min
mvn -Pandroid gluonfx:package         # → target/gluonfx/aarch64-android/gvm/*.apk
```

## Lane note

These scripts and the GitHub Actions workflow are *build environment* assets
(traditionally Hagag's lane per `ANDROID_MIGRATION.md` §6). They were added
in the 3bdelbary Phase 1 sweep with explicit cross-track authorization,
mirroring the JFreeChart `pom.xml` and `LifecycleService` work documented in
`docs/MIGRATION_3BDELBARY.md`.
