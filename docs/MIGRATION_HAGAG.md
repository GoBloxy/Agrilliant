# Hagag — Android Migration Notes (Build / Data / IoT track)

> Track-local notes for §8.A of `ANDROID_MIGRATION.md`. Only this file,
> the build config, the DAO/service/server/util layers, the GraalVM
> native-image config, and the Android manifest live in this lane (see
> §6 of the migration plan).
>
> If you want to know what to touch, the source of truth is
> `ANDROID_MIGRATION.md` §6 + §7. This file is **notes**, not rules.

---

## Status snapshot (2026-05-13)

Phase 1 — Hagag track is **done** on `android/hagag`. Every H-task
landed as its own commit with a verification gate logged below.

| §     | Task                                                            | Commit      | Status |
|-------|-----------------------------------------------------------------|-------------|--------|
| Phase 0 | Branch, toolchain notes, baseline desktop compile              | `97abe9b`   | ✅ done |
| H1    | GluonFX plugin + desktop / android Maven profiles              | `d68fc9e`   | ✅ done (structural) |
| H2    | GraalVM `reflect/resource/jni-config.json` seeds               | `0bcb451`   | ✅ done (seed) |
| H3    | `src/android/AndroidManifest.xml`                              | `4131483`   | ✅ done |
| H4    | `util/DBConnection` lazy + async + Settings layer              | `59da285`   | ✅ done |
| H5    | `util/Logger` API + uniform DAO thread-safety javadoc          | `d4248e3`   | ✅ done |
| H6    | `service/SessionManager` via Gluon Settings + file fallback    | `c4cc9f5`   | ✅ done (smoke-test green) |
| H7    | FingerprintService Android-safe facade + desktop impl split    | `3879a06`   | ✅ done |
| H8    | `util/CSVExporter.saveCsv` via Gluon Storage / `~/Downloads`   | `01aa8af`   | ✅ done |
| H9    | `util/Constants.IS_ANDROID` + "desktop only" headers on server | `0932ab1`   | ✅ done (final gate gated on B1) |
| H10   | Logger → `android.util.Log` + `ThresholdConfig` from properties| `b2f511a`   | ✅ done |
| H11   | This file                                                       | _this commit_ | ✅ done |

Three Phase-1 verifications can only be run after 3bdelbary's tree
lands and a real Android SDK + GraalVM are on the build host:
1. `mvn -Pandroid gluonfx:compile` end-to-end (needs B1).
2. APK on emulator smoke-test (needs the full toolchain).
3. `adb logcat` confirmation that `Logger` lines surface.

The rest of the gates ran here on the desktop profile and are
logged in the per-task tables below.

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
> Status: **done**, 2026-05-13.

`util/DBConnection.java` rewritten to keep the existing public
surface (`Connection getInstance()`) and add three new entry points:

- `static <T> CompletableFuture<T> runAsync(Callable<T> task)` —
  schedules `task` on a 2-thread daemon executor so DB work never
  blocks the FX UI thread. Wraps checked exceptions into the future.
- `static void reset()` — drops the cached `Connection` so the next
  `getInstance()` re-opens. Safe from any thread (atomic ref).
- `static void closeQuietly()` — for app shutdown; resets the cache
  and shuts the executor down. Wire from `Main#stop()` on desktop
  and from a Gluon `LifecycleService.PAUSE` listener on Android.

Credential layering, in resolution order (first non-null wins, cached
on the first call):

1. **Env vars** `DB_URL`, `DB_USER`, `DB_PASSWORD` — desktop dev / CI.
2. **Gluon Attach `SettingsService`** keys `db.url`, `db.user`,
   `db.password` — Android `SharedPreferences` or desktop user-config.
   Looked up via `Services.get(SettingsService.class)`; a missing
   service (no Attach runtime) returns `Optional.empty()` and we
   fall through silently.
3. **Classpath resource** `db.properties` — bundled fallback.
   New `src/main/resources/db.properties.example` documents the keys
   for first-time contributors; the real file stays gitignored.

The cached `Connection` is held in an `AtomicReference`. On any
`SQLException` during `getInstance()` we null it out so the next
call retries — survives Android's "OS freezes our process for a few
minutes" scenario.

#### pom.xml changes (Hagag's lane)
- New property `<attach.version>4.0.23</attach.version>`.
- New compile deps: `com.gluonhq.attach:{util, settings, storage,
  lifecycle}:4.0.23`. Storage/lifecycle are added now so H8 and the
  Phase 2 lifecycle wiring don't have to touch the pom later.
- `gluonfx-maven-plugin` config gains `<attachVersion>` +
  `<attachList>` (settings, storage, lifecycle). This is how the
  plugin auto-selects per-platform Attach impls at AOT time.

#### Verification (run 2026-05-13)
| Gate                                  | Result |
|---------------------------------------|--------|
| `mvn -Pdesktop compile`               | BUILD SUCCESS — Attach API jars resolved from Maven Central |
| `mvn -Pandroid validate`              | BUILD SUCCESS |
| DBConnection.java javadoc + members   | matches plan §H4 acceptance |

**Deferred:** "connects, runs `SELECT 1`, disconnects without
throwing" requires a real MySQL host. None available from this
session; the team should smoke-test the happy path once a dev MySQL
is reachable.

#### TODOs for the merge phase

- `// TODO(phase-2)`: wire `DBConnection.closeQuietly()` into
  `Main#stop()` once 3bdelbary's B1 settles the `Main.java`
  rewrite, and into a Gluon `LifecycleService.PAUSE` listener.
- `// TODO(phase-2)`: add a "Settings" admin screen where the user
  can enter `db.url`/`db.user`/`db.password` once at first run; the
  values go through `SettingsService.store(...)` and layer 2 above
  picks them up next launch. 3bdelbary owns this screen.

### H5. DAO sweep (logger, thread-safety doc)
> Status: **done**, 2026-05-13.

The literal "replace `e.printStackTrace()` with `Logger.e(...)`"
sweep is a no-op on this codebase — none of the 11 active DAOs catch
exceptions; they all propagate via `throws SQLException` so the
caller decides what to do. No `System.out.println` in hot loops
either. So H5's behaviour is unchanged; what landed is documentation
plus an explicit TODO about the cached `Connection` field.

#### 1. Logger upgrade
`util/Logger.java` rewritten with an Android-style API:
```
Logger.d(tag, msg)
Logger.i(tag, msg)
Logger.w(tag, msg)            Logger.w(tag, msg, t)
Logger.e(tag, msg)            Logger.e(tag, msg, t)
```
Initial impl prints `"<LEVEL>/<tag>: <msg>"` to stdout (info/debug)
or stderr (warn/error). H10 will overlay an `android.util.Log` delegate
through reflection so the same call sites go through logcat on a
device.

The old `Logger.logger(Runnable)` method had zero callers — safe to
drop.

#### 2. DAO sweep
Applied via the one-shot script `/tmp/h5_sweep.py` (recorded here
because it's a one-time helper, not project code). For every DAO
except `GenericDAO.java` (interface) and `UserDAO.java` (deprecated):
- Prepended a uniform class-level javadoc block declaring thread
  safety + the "never from the FX UI thread" rule + the recommended
  routing through `DBConnection.runAsync(...)`.
- Inserted a `// TODO(phase-2)` comment above the cached
  `private final Connection conn = DBConnection.getInstance();`
  field, noting the recommended switch to a per-method
  `private Connection conn() { return DBConnection.getInstance(); }`
  call so H4's auto-reconnect logic actually fires.

Files touched:
```
AdminDAO.java       AlertDAO.java        AttendanceDAO.java
CropDAO.java        DeviceDAO.java       HarvestDAO.java
ManagerDAO.java     PlotDAO.java         SensorDAO.java
TaskDAO.java        WorkerDAO.java
```

Public method signatures: unchanged. SQL: unchanged. Behaviour:
unchanged.

#### Verification (run 2026-05-13)
| Gate                                            | Result |
|-------------------------------------------------|--------|
| `mvn -Pdesktop compile` (all 13 DAOs compile)   | BUILD SUCCESS |
| `grep printStackTrace src/main/java/smartfarm/dao` | (no matches) |

#### TODOs for the merge phase

- `// TODO(phase-2)`: act on the per-DAO cached-conn-field TODOs once
  3bdelbary's controllers are wrapping calls in async — the field-
  to-method-call refactor is mechanical but ~90 touches and should
  be one self-contained merge-phase commit.
- `// TODO(phase-2)`: when controllers start using `Logger.e(...)`
  the captured exceptions in DAOs may want to be wrapped in a custom
  `DataAccessException` so the UI layer doesn't need to import
  `SQLException`. Out of scope for Phase 1.

### H6. SessionManager — Gluon Settings + desktop fallback
> Status: **done**, 2026-05-13.

`service/SessionManager.java` rewritten. Public surface is unchanged:
```
SessionManager.saveSession(email);
String email = SessionManager.loadSession();
SessionManager.clearSession();
```
Storage now layers like H4 does: try Gluon Attach `SettingsService`
first, fall back to the original `~/.agrilliant/session.properties`
file. The HMAC-SHA256 tamper check is copy-paste identical to the
pre-H6 implementation — only the storage layer changed.

Keys written to either backend:
- `session.email` — the user's email
- `session.token` — HMAC-SHA256(email, SECRET_KEY), Base64

A migration step also runs in `loadSession()`: if Attach Settings is
present but empty, and the legacy file has a valid session, we copy
it into Settings and delete the file. Existing desktop installs
transition transparently after one launch.

Logging: replaced the file's `System.err.println(...)` calls with
`Logger.w(TAG, ...)` / `Logger.e(TAG, ...)` (H5's new API).

#### Verification (run 2026-05-13)

Wrote a one-off smoke test at `target/h6-smoke/H6Smoke.java`
(gitignored). On a bare desktop run (Attach Settings desktop impl
absent), it confirmed:

| Step                                              | Result |
|---------------------------------------------------|--------|
| `clearSession()` → `loadSession()` returns null   | OK    |
| `saveSession(email)` → `loadSession()` returns email | OK |
| Hand-edit `session.properties` → `loadSession()` returns null and clears | OK |

Logger output confirmed the new `W/SessionManager: Session tampered (file) — clearing` format from H5.

`mvn -Pdesktop compile` and `mvn -Pandroid validate` both green.

**Deferred:** "saved email survives an app restart on the Android
emulator" — needs the full GluonFX Android build to run on an
emulator; gated by the same things as the other Android runtime
checks.

#### TODOs for the merge phase

- `// TODO(phase-2)`: after 3bdelbary's B1 lands, decide if the
  `SECRET_KEY` constant should move out of source into a build-time
  secret (Git history will leak it otherwise; current placement
  matches the pre-migration behaviour).

### H7. FingerprintService — Android stub + desktop impl split
> Status: **done**, 2026-05-13.

`service/FingerprintService.java` is now a thin facade over a
`FingerprintService.Backend` SPI:
- Public surface is **identical** to the pre-H7 implementation —
  every method signature 3bdelbary's controllers call
  (`new FingerprintService()`, `autoConnect()`, `disconnect()`,
  `isConnected()`, `scanAndMatch()`, `getTemplateCount()`,
  `deleteTemplate(int)`, `enroll(int, EnrollCallback)`,
  `static String[] getAvailablePorts()`) lives in the facade and just
  forwards to the chosen backend.
- The facade resolves the backend lazily via
  `Class.forName("smartfarm.service.desktop.FingerprintServiceDesktop")`.
  On desktop this succeeds and you get full hardware functionality;
  on Android the class is absent (Maven excludes it) and we fall
  through to `NULL_BACKEND`, which returns `false` / `-1` / `"N/A"`
  for every method and emits a single startup warning via
  `Logger.w(...)`.

`service/desktop/FingerprintServiceDesktop.java` is the original
implementation, moved into its own sub-package and made to
`implements FingerprintService.Backend`. Same protocol, same
`jSerialComm` usage, same logs (now via the H5 Logger).

The `EnrollCallback` interface is owned by the facade only (one
type used by both backends — keeps the lambda compatibility the
controllers depend on).

#### pom.xml change
- `android` profile's `maven-compiler-plugin` now excludes
  `**/smartfarm/service/desktop/**/*.java` (and preemptively
  `**/smartfarm/server/**/*.java` for H9). Excludes work cleanly for
  `service/desktop/**` because nothing imports it statically —
  javac's sourcepath has nothing to pull back in.

#### Verification (run 2026-05-13)
| Gate                                                              | Result |
|-------------------------------------------------------------------|--------|
| `mvn -Pdesktop clean compile` (71 sources, incl. FingerprintServiceDesktop) | BUILD SUCCESS |
| `mvn -Pandroid clean compile` (67 sources, FingerprintServiceDesktop absent) | BUILD SUCCESS |
| `dependency:list -Pandroid \| grep jSerialComm`                   | (no matches) |
| `dependency:list -Pdesktop \| grep jSerialComm`                   | present |
| `target/h7-smoke/H7Smoke.java`: facade returns N/A + -1 + false when no ESP32 attached | OK |
| same smoke: `autoConnect()` actually scans serial ports via the real backend | OK (`W/FingerprintDesktop: ESP32 not found on any COM port`) |

#### Note on `server/**` exclude
The android profile also excludes `server/**`, but as expected javac
still implicit-compiles `FarmServer.java` and `SensorHandler.java`
because `Main.java` statically imports them. The `.class` files end
up in `target/classes`. **H9** + 3bdelbary's **B1** address the
remaining server-on-Android issue — see §H9 in this file.

#### TODOs for the merge phase

- `// TODO(phase-2)`: once 3bdelbary's B1 drops the `import
  smartfarm.server.FarmServer;` from `Main.java`, the
  `**/smartfarm/server/**/*.java` exclude in the android profile
  becomes effective end-to-end.
- `// TODO(phase-2)`: Android USB-OTG fingerprint support via Gluon
  Attach would slot in here as a third `Backend` impl
  (`FingerprintServiceAndroid`). Out of scope for Phase 1 — see
  plan §M9 "Known follow-ups".

### H8. CSVExporter — Gluon Storage + desktop fallback
> Status: **done**, 2026-05-13.

`util/CSVExporter.java` keeps the two pure-string functions
3bdelbary's controllers already call:
- `String exportSensorData(List<SensorReading>)`
- `String exportHarvestData(List<HarvestRecord>)`

…and adds **one** new method:
```
File saveCsv(String content, String suggestedName)  // throws IOException
```

`saveCsv` picks a target directory based on the platform:

| Platform | Target directory | Mechanism |
|----------|------------------|-----------|
| Android  | `<public>/Documents/` | `Services.get(StorageService.class).flatMap(s -> s.getPublicStorage("Documents"))` |
| Android (no public dir, rare SD-card case) | app-private storage | `StorageService.getPrivateStorage()` |
| Desktop  | `~/Downloads/` | direct file write |

The platform check is `com.gluonhq.attach.util.Platform.isAndroid()`,
wrapped in a `try { ... } catch (Throwable t) { return false; }` so
the desktop build keeps compiling even if Attach is missing.

`saveCsv` logs `I/CSVExporter: Wrote <N> bytes → <path>` via H5's
Logger and returns the resulting `File` so call sites can show a
"Saved to ..." toast.

The constructor is private (the class is utility-only).

#### Verification (run 2026-05-13)
| Gate                                                          | Result |
|---------------------------------------------------------------|--------|
| `mvn -Pdesktop compile`                                       | BUILD SUCCESS |
| `mvn -Pandroid compile`                                       | BUILD SUCCESS |
| `target/h8-smoke/H8Smoke.java`:                               |        |
| &nbsp;&nbsp;`exportSensorData` header + row well-formed       | OK     |
| &nbsp;&nbsp;`exportHarvestData` header + row well-formed      | OK     |
| &nbsp;&nbsp;`saveCsv` wrote 69 bytes to `~/Downloads/`        | OK     |
| &nbsp;&nbsp;Logger output `I/CSVExporter: Wrote 69 bytes → ...` | OK   |
| &nbsp;&nbsp;`Platform.isAndroid()` returns false on host      | OK     |

**Deferred:** "manual Android test writes a real `.csv` users can
find" requires an emulator/device — same gate as the rest of the
Android runtime checks.

#### TODOs for the merge phase
- `// TODO(phase-2)`: B4 wires `LogsController` and
  `ReportsController` (and DashboardController's CSV export) to
  call `CSVExporter.saveCsv(content, name)` instead of
  `javafx.stage.FileChooser` so the same code path works on Android.
- `// TODO(phase-2)`: optional polish — show a Share intent on
  Android after `saveCsv` so the user can email / drive-upload the
  file directly (Gluon Attach `ShareService.share(...)`).

### H9. FarmServer — desktop-only profile guard
> Status: **done (Hagag side)**, 2026-05-13. Final gate is gated on
> 3bdelbary's B1 — see deferred note below.

Three things landed:

1. **`util/Constants.java`** — single static `IS_ANDROID` boolean
   resolved at class-load time via
   `com.gluonhq.attach.util.Platform.isAndroid()`. The lookup is
   wrapped in a `try { ... } catch (Throwable t) { return false; }`
   so the same flag works on a pre-GluonFX desktop dev environment
   (where Attach reports DESKTOP) and on Android (where it reports
   ANDROID).

   The class lives in `smartfarm.util` so callers don't need to
   import Gluon Attach directly — they just read `Constants.IS_ANDROID`.

2. **One-line "desktop build only" header** on each of:
   - `server/FarmServer.java`
   - `server/SensorHandler.java`
   - `server/notification/NotificationService.java`

3. **maven-compiler-plugin `<excludes>`** already in place from H7's
   pom change for `**/smartfarm/server/**/*.java`.

#### Verification (run 2026-05-13)

Cleaned and rebuilt against the Android profile from scratch:

| Class                                   | On Android `target/classes`? | On desktop? |
|-----------------------------------------|------------------------------|-------------|
| `smartfarm/util/Constants.class`        | yes (H9)                     | yes |
| `smartfarm/service/desktop/FingerprintServiceDesktop.class` | **absent** (H7 success) | yes |
| `smartfarm/server/notification/NotificationService.class`   | **absent** (no static refs) | yes |
| `smartfarm/server/FarmServer.class`     | **still present** (javac implicit compile) | yes |
| `smartfarm/server/SensorHandler.class`  | **still present** (javac implicit compile) | yes |

| Smoke                                                       | Result |
|-------------------------------------------------------------|--------|
| `target/h9-smoke/H9Smoke.java` → `Constants.IS_ANDROID`     | `false` on host JVM (desktop), correct. |
| `mvn -Pdesktop clean compile`                               | BUILD SUCCESS (72 sources). |
| `mvn -Pandroid clean compile`                               | BUILD SUCCESS (68 sources). |

**Deferred (depends on 3bdelbary's B1):** the strict gate
"Android profile build does not include `FarmServer.class`" cannot
fully close in this branch alone. `Main.java` still has the line
`import smartfarm.server.FarmServer;` plus `Thread serverThread =
new Thread(() -> new FarmServer().start());` — javac then implicit-
compiles `FarmServer.java` and `SensorHandler.java` from sourcepath
despite the exclude. 3bdelbary's B1 rewrites `Main.java` into a
Gluon `MobileApplication` whose entry point doesn't reference the
TCP server. After that merge:

* the `**/smartfarm/server/**/*.java` exclude becomes structurally
  effective end-to-end, and
* the runtime path in any remaining `Main.start()` caller goes
  through `if (!Constants.IS_ANDROID) new FarmServer().start();`,
  so even an accidental import doesn't fire the listener on Android.

#### TODOs for the merge phase

- `// TODO(phase-2)`: in `Main.java`, gate the
  `new FarmServer().start()` call (and its `import`) on
  `!Constants.IS_ANDROID`. Owned by 3bdelbary's B1 since `Main.java`
  is in their lane.
- `// TODO(phase-2)`: confirm on a clean Android AOT build that the
  `server.*` classes are absent from the APK — once B1 lands, run
  `unzip -l target/.../app.apk | grep server` and check.

### H10. Logger / ThresholdConfig housekeeping
> Status: **done**, 2026-05-13.

#### 1. Logger — Android routing via reflection

`util/Logger.java` now ships the H5 API (`d / i / w / e` with
optional `Throwable`) backed by a runtime-switched writer:

- **Android**: each call routes through `android.util.Log` via
  reflection (`Class.forName("android.util.Log")`,
  `Method.invoke(...)`). Logs surface in `adb logcat` just like
  native Android calls.
- **Desktop**: `android.util.Log` is absent, the static lookup
  returns null, and we fall back to printing
  `"<LEVEL>/<tag>: <msg>"` to {@link System#out} (D/I) or
  {@link System#err} (W/E). Throwables are stack-traced under the
  message — identical to logcat's two-line shape.

Reflection (not a direct {@code import android.util.Log}) is used on
purpose so the desktop Maven build keeps compiling without an Android
SDK on the classpath.

Added an entry to `META-INF/native-image/smartfarm/reflect-config.json`
so GraalVM AOT keeps the methods reachable during the Android build.

#### 2. ThresholdConfig — properties-backed

`util/ThresholdConfig.java` keeps the eight `public static final
float` constants the existing `AlertService` already reads
(`TEMP_CRITICAL_HIGH`, `TEMP_WARNING_HIGH`, `TEMP_CRITICAL_LOW`,
`HUM_WARNING_LOW`, `HUM_WARNING_HIGH`, `SOIL_CRITICAL_DRY`,
`SOIL_WARNING_DRY`, `SOIL_WARNING_WET`). The values are now loaded
from `thresholds.properties` (added under `src/main/resources/`) at
class-load time, with the previous hard-coded defaults as fallback.

Same resource on both targets: classpath on desktop, the APK
classpath / `assets/` on Android. No platform-specific lookup needed.

#### Verification (run 2026-05-13)
| Gate                                                              | Result |
|-------------------------------------------------------------------|--------|
| JSON validation of `reflect-config.json` (after adding android.util.Log) | OK |
| `mvn -Pdesktop clean compile`                                     | BUILD SUCCESS |
| `mvn -Pandroid compile`                                           | BUILD SUCCESS |
| `target/h10-smoke/H10Smoke.java`:                                 |        |
| &nbsp;&nbsp;`I/ThresholdConfig: Loaded thresholds.properties (8 keys)` | OK |
| &nbsp;&nbsp;All eight thresholds match the file values            | OK     |
| &nbsp;&nbsp;`Logger.{i,w,e}` produce `<LEVEL>/<tag>: <msg>` on desktop | OK |
| &nbsp;&nbsp;`Logger.e(tag, msg, throwable)` includes stack trace  | OK     |

**Deferred:** "logs reach `adb logcat`" requires running on an Android
emulator — gated by the full GluonFX Android build as before. The
reflection routing is mechanical, has unit-test-style coverage on
desktop, and shouldn't introduce surprises.

#### TODOs for the merge phase

- `// TODO(phase-2)`: spot-check that `I/ThresholdConfig` and
  `W/SessionManager` appear in `adb logcat -s "*" :I` after a real
  device install.
- `// TODO(phase-2)`: optional polish — once 3bdelbary's controllers
  start using `Logger.i/w/e`, route any sensitive content (e.g. JWTs,
  email PII) through a redacting helper.

### H11. Final notes (this file's TODO log)
> Status: **done**, 2026-05-13.

This file is the H11 deliverable. The plan asks for:
- Final pom profile names → see §H1 + §1.1.
- GraalVM agent regen command → see §H2 ("Regenerating reflect-config with the GraalVM agent").
- Install / run on real device → see §1.2 (machine prerequisites) + §4 (cheat sheet).
- Phase-2 cross-track TODOs → consolidated in §3 below.

---

## 3. Cross-track TODOs (for Phase 2 merge)

> Things Hagag's track stops short of because the other track owns
> the file. Each `// TODO(phase-2): ...` comment in code gets a
> corresponding bullet here. The Phase-2 merger should `grep -rn
> 'TODO(phase-2)' src/` and walk the list — every item below maps to
> one or more in-code bullets.

### 3.1 Things 3bdelbary owns

These need their `Main.java` / controller / FXML edits before they
fully close on the merged tree:

- **`Main.java` → `MobileApplication` (B1).** Drop the
  `import smartfarm.server.FarmServer;` and gate the
  `new FarmServer().start()` line on `!smartfarm.util.Constants.IS_ANDROID`.
  Closes §H7 (`server/**` exclude becomes structurally effective end-to-end)
  and §H9 (no `FarmServer.class` in the APK).
- **Wire `Main#stop()` (and a Gluon `LifecycleService.PAUSE` listener)
  to call `DBConnection.closeQuietly()`.** Closes the §H4 lifecycle TODO.
- **B4 controllers — replace `javafx.stage.FileChooser` with
  `CSVExporter.saveCsv(...)`** in `LogsController`, `ReportsController`,
  and DashboardController's CSV export. Closes the §H8 controller TODO.
- **B7 / Android icons** — drop `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`
  drawables into `src/android/res/mipmap-*/` so the manifest refs in §H3 resolve.
- **B-anywhere** — once the controllers wrap DAO calls in
  `DBConnection.runAsync(...)`, do the per-DAO field refactor
  (`private final Connection conn = ...` → `private Connection conn() { ... }`)
  in one mechanical commit. Closes the §H5 per-DAO TODOs (11 files).

### 3.2 Things either track can fold in during the merge

- **Run the GraalVM agent on a desktop session** and merge the
  captured config into `src/main/resources/META-INF/native-image/smartfarm/`
  — closes the §H2 mysql-connector-j follow-up.
- **Confirm Ikonli Feather font path** matches the version we ship
  (regex in `resource-config.json` assumes `META-INF/resources/feather/<ver>/Feather.ttf`).
- **Add the Plant.id "Settings" admin screen** (3bdelbary's UI lane,
  but Hagag's `SettingsService` keys `db.url`/`db.user`/`db.password`
  back it). Closes the §H4 first-run-credentials TODO.
- **Optional polish — `ShareService.share(...)` after `CSVExporter.saveCsv`** so
  users can email / drive-upload the file. §H8 TODO.
- **Optional polish — redacting helper for `Logger.*`** so JWTs /
  email PII don't end up in logcat. §H10 TODO.

### 3.3 Things deferred until release/hardening

- **Release-build manifest hygiene**: flip
  `android:usesCleartextTraffic="false"` and ship a
  `network_security_config.xml` if any production MySQL host genuinely
  needs cleartext. Bump `versionCode` per Play Console upload. §H3 TODOs.
- **`SECRET_KEY` constant relocation** out of source (currently inlined
  in `SessionManager` as in the pre-migration code). §H6 TODO.
- **Android USB-OTG fingerprint impl** as a third `FingerprintService.Backend`.
  Plan §M9 "Known follow-ups".
- **`adb logcat -s "*" :I` verification** that `Logger` lines surface
  once a real Android install is on a device. §H10 TODO.
- **APK content audit** (`unzip -l ...apk | grep server`) once B1 lands
  to confirm `server.*` is absent. §H9 TODO.
- **Strict gate `mvn -Pandroid gluonfx:compile`** end-to-end —
  depends on the full toolchain + B1.

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
