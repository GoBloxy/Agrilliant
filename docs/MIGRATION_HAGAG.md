# Hagag — Android Migration Notes (Build / Data / IoT track)

> Track-local notes for §8.A of `ANDROID_MIGRATION.md`. Only this file,
> the build config, the DAO/service/server/util layers, the GraalVM
> native-image config, and the Android manifest live in this lane (see
> §6 of the migration plan).
>
> If you want to know what to touch, the source of truth is
> `ANDROID_MIGRATION.md` §6 + §7. This file is **notes**, not rules.

---

## 0. Branch

- Branch: `android/hagag`, forked from `mobile-app` HEAD on
  2026-05-13. (The plan says `android/hagag` off `main`; we forked off
  `mobile-app` because the mobile-app branch already contains the
  cleaned-up desktop baseline the team wants to migrate from.)
- All Hagag commits land here. Merge into `main` only after the
  matching Phase 1 checklist below is green.

## 1. Phase 0 — Joint setup outcomes

### 1.1 Decisions (locked in)

| Decision                | Value                                    | Why                                                                 |
|-------------------------|------------------------------------------|---------------------------------------------------------------------|
| GluonFX Maven plugin    | `1.0.28`                                 | Latest stable on Maven Central (2025-10-24). Targets Android SDK 35 + NDK 28. |
| Min Android API         | `26` (Android 8.0 Oreo)                  | Per `ANDROID_MIGRATION.md` §5.                                       |
| Target / compile SDK    | `35` (Android 15)                        | GluonFX 1.0.27+ default.                                             |
| Android NDK             | `28`                                     | GluonFX 1.0.27+ default.                                             |
| Target ABI              | `aarch64-android` (arm64-v8a only)       | Gluon only supports arm64 on Android.                                |
| JDK (build)             | OpenJDK 17+ for `javac` / Maven          | Project `pom.xml` is `--release 17`. Newer JDKs are fine for build. |
| JDK (AOT image)         | Gluon GraalVM CE / Liberica NIK 24       | Pinned by GluonFX 1.0.28 internally; you don't choose it manually.   |
| Build host              | Linux (Arch on Hagag's machine)          | Gluon docs: "Android can be built only on Linux OS or from Windows WSL2". |

### 1.2 Machine prerequisites

This is what **must** be on the machine before any `mvn -Pandroid gluonfx:*`
goal will work end-to-end.

#### A. OpenJDK 17 (or newer) on the PATH

Arch:
```bash
sudo pacman -S jdk17-openjdk
sudo archlinux-java set java-17-openjdk     # if you have multiple JDKs
```

Verify:
```bash
java -version          # should be >= 17
javac -version
```

> **Note for our setup:** the system currently ships JDK 26 as
> default. `mvn compile` still works because `pom.xml` pins
> `--release 17`, but `javafx:run` is fragile on JDK 26 + plugin
> 0.0.8. If `javafx:run` misbehaves, switch to JDK 17 for the
> desktop run target.

#### B. Maven 3.9+

We bundled `apache-maven-3.9.6` in `maven.zip` at the repo root.
Unzipped into `.maven-bundled/` (gitignored). Either:

```bash
# Option 1: install system-wide
sudo pacman -S maven

# Option 2: use the bundled one (no install, gitignored)
cd /path/to/Agrilliant
unzip maven.zip -d .maven-bundled            # one-time
alias mvn="$(pwd)/.maven-bundled/apache-maven-3.9.6/bin/mvn"
```

GluonFX plugin 1.0.25+ explicitly fixed Maven-3.9+ compatibility, so
3.9.6 is fine.

#### C. GraalVM is **not** required to be pre-installed

The GluonFX plugin (1.0.24+) downloads its own Gluon-tweaked GraalVM
build (currently `GraalVM CE 23-dev+25.1` with JDK 23) the first time
you run `mvn gluonfx:build`. You do **not** need `GRAALVM_HOME` set.

If you'd rather install one yourself (faster first build, more
predictable), use Liberica NIK 24 (Full JDK):
- https://bell-sw.com/pages/downloads/native-image-kit/

Once installed, set:
```bash
export GRAALVM_HOME=/path/to/liberica-nik
export PATH=$GRAALVM_HOME/bin:$PATH
```

#### D. Android SDK + NDK

The GluonFX plugin will auto-download SDK and NDK on first build if
both `ANDROID_SDK` and `ANDROID_NDK` are **unset**. This is the
recommended path — no manual install.

If you want to control the install:

```bash
# Arch: install command-line tools
yay -S android-sdk android-sdk-cmdline-tools-latest android-ndk

# Required packages (per Gluon docs)
sudo sdkmanager "platform-tools" "platforms;android-35" \
                "build-tools;35.0.0" "ndk-bundle" \
                "extras;android;m2repository" \
                "extras;google;m2repository"

# Point GluonFX at it
export ANDROID_SDK=/opt/android-sdk
export ANDROID_NDK=/opt/android-ndk
```

#### E. Linux native build tools (for GraalVM AOT)

```bash
sudo pacman -S gcc make pkgconf glibc zlib base-devel
```

These are usually already installed on a developer Arch machine; if
the first `gluonfx:build` complains about missing `gcc` or `ld`, that
is what to install.

#### F. Working ADB connection (for install/run goals)

```bash
adb devices
```

Phone must have **Developer Options → USB debugging** turned on.
First connection prompts "Allow USB debugging?" on the device.

### 1.3 Verification of the desktop baseline

The plan says Phase 0 ends when `mvn clean javafx:run` works on
`main`. Practical baseline on this branch (`android/hagag`,
pre-Phase-1):

```bash
.maven-bundled/apache-maven-3.9.6/bin/mvn -B compile
```

Result on 2026-05-13: BUILD SUCCESS. Source still compiles cleanly
against the unchanged `pom.xml` baseline before any H-task edits.

Full `javafx:run` requires a graphical session, so we don't smoke-test
it from a headless terminal session — confirm it once interactively
before H1 touches `pom.xml`:

```bash
mvn -Pdesktop javafx:run
```

If the run target fails on JDK 26, drop down to JDK 17 (§1.2.A) for
the desktop variant.

---

## 2. Phase 1 task log

Each `H*` task gets one section here and one commit on `android/hagag`.
The verification gate (per §10 of the migration plan) goes in the
"Verify" subsection.

### H1. GluonFX plugin + Maven profiles
> Status: **done (structural)**, 2026-05-13.

What landed in `pom.xml`:
- `${main.class} = smartfarm.Main`, used by both `javafx-maven-plugin`
  and `gluonfx-maven-plugin`.
- `gluonfx-maven-plugin` 1.0.28 wired at project root with
  `<target>${gluonfx.target}</target>` and empty `reflectionList /
  resourcesList / bundlesList` stubs that H2 populates.
- Gluon nexus added to `<repositories>` (needed for the JavaFX
  static builds used by Android AOT).
- `desktop` profile (default, `activeByDefault=true`):
  - Adds the desktop-only deps (`jSerialComm`, `jfreechart`,
    `jfreechart-fx`).
  - Owns the `maven-shade-plugin` execution that produces the
    `jpackage`-friendly fat JAR.
  - Sets `gluonfx.target=host`.
- `android` profile:
  - Sets `gluonfx.target=android`.
  - **No per-source excludes yet** — see the deferred gate below.

Verification (run 2026-05-13):
| Gate                                               | Result |
|----------------------------------------------------|--------|
| `mvn -Pdesktop validate`                           | BUILD SUCCESS |
| `mvn -Pandroid validate`                           | BUILD SUCCESS |
| `mvn -Pdesktop compile`                            | BUILD SUCCESS, 66 source files |
| Effective POM (android): GluonFX target = android  | confirmed |
| Effective POM (android): mainClass = smartfarm.Main | confirmed |

**Deferred:** `mvn -Pandroid compile` cannot pass yet because
`WorkerController` / `SignInController` import
`smartfarm.service.FingerprintService` (which imports jSerialComm),
and `Main.java` imports `smartfarm.server.FarmServer`. The plan splits
this work across H7 (FingerprintService stub), H9 (FarmServer profile
guard), and 3bdelbary's B1 (Main.java → MobileApplication). Once those
land the android compile gate runs cleanly. Tracking TODO:

- `// TODO(phase-2)`: re-verify `mvn -Pandroid compile` succeeds end-to-end
  once H7+H9+B1 are merged.
- `// TODO(phase-2)`: once `service/desktop/**` exists, add
  `<excludes><exclude>**/smartfarm/service/desktop/**</exclude></excludes>`
  to the android profile's `maven-compiler-plugin` config.


### H2. GraalVM native-image config
> Status: **done (seed)**, 2026-05-13.

Created `src/main/resources/META-INF/native-image/smartfarm/`:
- `reflect-config.json` — 12 FXML controllers, 18 model POJOs, ~30
  JavaFX widget root types referenced in our `.fxml`, Ikonli's
  `FontIcon` + `Feather` enum + its SPI handler/provider, plus seed
  MySQL JDBC driver classes (`Driver`, `NonRegisteringDriver`,
  `SingleConnectionUrl`, `NativeProtocol`, common CJ exceptions).
- `resource-config.json` — pattern includes for `fxml/**`, `css/**`,
  `images/**`, the three `*.properties` files, `META-INF/services/*`
  (JDBC SPI), AtlantaFX theme CSS, and the Ikonli Feather TTF +
  metadata JSON.
- `jni-config.json` — empty array (we don't call native code yet;
  USB OTG fingerprint via Gluon Attach would populate this later).

All three files validated as parseable JSON via `python3 -m json.tool`.

GluonFX plugin discovery: anything under `META-INF/native-image/*/`
is auto-picked up by GraalVM Native Image at AOT time, so the
plugin's empty `<reflectionList>` / `<resourcesList>` / `<bundlesList>`
in `pom.xml` stays empty.

**Deferred:** the only true verification is `mvn -Pandroid
gluonfx:build` (full AOT compile) showing no "method not registered
for reflection" warnings. That run is gated by the other H-tasks +
3bdelbary's UI work; first real APK assembly will surface any missing
entries — fold them in via the agent flow below.

#### Regenerating reflect-config with the GraalVM agent

The reflect-config is a seed; real apps usually need more entries
(especially for `mysql-connector-j` which is reflection-heavy). To
expand it automatically:

```bash
# 1) Run the desktop app with the native-image agent attached,
#    pointing at a scratch dir.
mkdir -p target/native-agent-config
mvn -Pdesktop javafx:run \
  -Djavafx.runtime.argsExtra='-agentlib:native-image-agent=config-output-dir=target/native-agent-config'

# 2) Click through every screen in the app: sign-in, sign-up,
#    dashboard, every nav button, alerts ack, plot create, etc.
#    The agent records every reflective access while you do this.

# 3) Stop the app. Merge the captured config into our committed seed.
#    `--config-merge-dir` overwrites only the keys it has new entries for.
native-image-configure generate \
  --input-dir=target/native-agent-config \
  --output-dir=src/main/resources/META-INF/native-image/smartfarm/

# 4) Diff, sanity-check (the agent over-includes — drop anything
#    in `java.*` or `sun.*` it adds unnecessarily), commit.
```

If `native-image-configure` isn't on the PATH, it lives inside the
GraalVM install (`$GRAALVM_HOME/bin/native-image-configure`). On
machines that don't have it installed yet, the merge can be done
manually — diff `target/native-agent-config/reflect-config.json`
against the committed file and add anything new.

#### TODOs for the merge phase

- `// TODO(phase-2)`: run the agent capture flow on a desktop session,
  fold mysql-connector-j's actual reflection footprint into the seed.
- `// TODO(phase-2)`: confirm Ikonli Feather pack font path matches
  the version we ship — current regex assumes `META-INF/resources/feather/<ver>/Feather.ttf`.

### H3. AndroidManifest.xml + permissions
> Status: **done**, 2026-05-13.

Created `src/android/AndroidManifest.xml`. GluonFX picks this up
verbatim instead of its generated default at
`target/gluonfx/aarch64-android/gensrc/android/AndroidManifest.xml`.

Key decisions:
| Setting | Value | Why |
|---------|-------|-----|
| `package` | `com.smartfarm.agrilliant` | App store identity. Independent of Java packages. |
| `versionCode` / `versionName` | `1` / `1.0` | Incremented per Play Store upload (release config in pom). |
| `minSdkVersion` | `26` | Per plan §5. |
| `targetSdkVersion` | `35` | GluonFX 1.0.28 default. |
| `INTERNET` | required | MySQL JDBC traffic. |
| `ACCESS_NETWORK_STATE` | required | Detect offline before query. |
| `READ_MEDIA_IMAGES` | required, `targetApi=33` | Disease-detection picker on Android 13+. |
| `READ_EXTERNAL_STORAGE` | `maxSdkVersion=32` | Same picker on Android 12 and below. |
| `CAMERA` | optional | Fresh leaf photo capture. `uses-feature ... required=false` so non-camera tablets still install. |
| `WRITE_EXTERNAL_STORAGE` | **omitted** | CSVExporter (H8) uses SAF via Gluon Attach Storage; no write perm needed. |
| `usesCleartextTraffic` | `true` | Dev MySQL hosts often plain-text. **Must flip to false** for any release build (see TODOs). |

Activities:
- `com.gluonhq.helloandroid.MainActivity` — Gluon's bridge activity
  that boots the GraalVM-built native binary. `meta-data main.class
  = smartfarm.Main` tells it which JavaFX entry to instantiate.
- `com.gluonhq.helloandroid.PermissionRequestActivity` — Gluon's
  runtime-permission helper (camera, photo picker).

Icons (`@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`) are
**referenced but not added** — drawables are in 3bdelbary's lane
(B7 generates them at all the Android density buckets).

**Deferred:** verification "manifest merger emits no warnings during
APK packaging" runs only inside the GluonFX plugin's packaging step
(needs Android SDK + GraalVM). XML parses cleanly via Python here as
a baseline.

#### TODOs for the merge phase / release

- `// TODO(phase-2)`: before Play Store upload, flip
  `android:usesCleartextTraffic="false"` and ship a
  `network_security_config.xml` if a production MySQL host needs
  cleartext.
- `// TODO(phase-2)`: bump `versionCode` per upload to Play Console.
- `// TODO(phase-2)`: depends on B7 — confirm `@mipmap/ic_launcher`
  + `@mipmap/ic_launcher_round` exist in `src/android/res/` once
  3bdelbary lands the launcher icons.

### H4. DBConnection rewrite (lazy, layered creds, async helper)
> Status: **pending**.

### H5. DAO sweep (logger, thread-safety doc)
> Status: **pending**.

### H6. SessionManager — Gluon Settings + desktop fallback
> Status: **pending**.

### H7. FingerprintService — Android stub + desktop impl split
> Status: **pending**.

### H8. CSVExporter — Gluon Storage + desktop fallback
> Status: **pending**.

### H9. FarmServer — desktop-only profile guard
> Status: **pending**.

### H10. Logger / ThresholdConfig housekeeping
> Status: **pending**.

### H11. Final notes (this file's TODO log)
> Status: **pending**.

---

## 3. Cross-track TODOs (for Phase 2 merge)

> Things this track must stop short of (because the other track owns
> the file). Each `// TODO(phase-2): ...` comment Hagag adds in code
> gets a corresponding bullet here, with file + reason.

_Empty for now. Append as we go._

---

## 4. Cheat sheet — common GluonFX commands

```bash
# Desktop dev (unchanged from before migration)
mvn -Pdesktop javafx:run

# Android: AOT compile (slow; takes minutes; needs internet first time)
mvn -Pandroid gluonfx:compile

# Android: produce native arm64 binary
mvn -Pandroid gluonfx:build

# Android: package as APK + AAB
mvn -Pandroid gluonfx:package

# Android: install APK on connected device
mvn -Pandroid gluonfx:install

# Android: launch on device with adb logcat streamed
mvn -Pandroid gluonfx:nativerun

# Capture GraalVM reflection config from a desktop run
mvn -Pdesktop -Dagent=true javafx:run
# (then copy native-image-agent output into
#  src/main/resources/META-INF/native-image/smartfarm/)
```
