# Agrilliant — Android Migration Plan

> Migrate the existing JavaFX desktop app (`smartfarm.*`) to a native Android APK
> using **Gluon Mobile (GluonFX)** while keeping the same package layout, the
> same MVC + layered architecture, and the same MySQL backend. Two developers
> work in parallel on disjoint files; an AI agent picks **one** track and stays
> in that lane until both branches are merged.

---

## 0. How to use this file

This file has three audiences:

1. **Hagag** — owns the *Build / Data / IoT* track (server-side and plumbing).
2. **3bdelbary** — owns the *UI / Navigation / Resources* track.
3. **The AI agent** working in either developer's branch.

### Rules for the AI agent (read this first, every session)

1. Ask the developer **which track they own** (Hagag or 3bdelbary). If they
   already told you, confirm it once and remember.
2. **Only modify files inside that track's "Owned files" list** in §6.
   Files outside that list are read-only for context — never edit them, even
   if it looks like the obvious fix.
3. **Never touch §9 (Phase 2 — Merge)** until the developer explicitly says
   *"both branches are merged, start Phase 2"*. Phase 2 work depends on the
   other track's output existing in the same tree.
4. Before every edit, re-check the "DO NOT TOUCH" list in §7. If a fix
   genuinely requires editing a file the other track owns, **stop and ask
   the developer** — do not silently cross the line.
5. After every task, run the matching verification gate in §10.

---

## 1. Project snapshot (current state)

### Stack today
| Layer       | Today                                              |
|-------------|----------------------------------------------------|
| Language    | Java 17                                            |
| UI          | JavaFX 21 + FXML + AtlantaFX theme + Ikonli icons  |
| Charts      | `javafx.scene.chart` + JFreeChart-FX               |
| Persistence | MySQL 8 via `mysql-connector-j` (JDBC, singleton)  |
| Auth        | jBCrypt, HMAC-signed local session file            |
| IoT ingress | `ServerSocket` on port 8080 + ESP32 firmware       |
| Hardware    | R307 fingerprint over USB serial via `jSerialComm` |
| External    | Plant.id REST API for disease detection            |
| Build       | Maven (`pom.xml`) + `javafx-maven-plugin` + shade  |

### Layered architecture (must be preserved on Android)
```
Presentation (JavaFX/FXML controllers, ui/)
        ↓
Service (smartfarm.service)            ← business logic
        ↓
DAO     (smartfarm.dao)                ← JDBC only
        ↓
Infra   (DBConnection, FarmServer, SerialPort, HTTP)
```

### File inventory (current)
```
src/main/java/smartfarm/
├── Main.java, Launcher.java
├── model/        14 POJOs           — pure Java, no JavaFX, fully portable
├── dao/          13 JDBC DAOs       — SQL only, portable if JDBC works on Android
├── service/      14 services        — mostly portable; FingerprintService is desktop-only
├── server/       FarmServer + SensorHandler + notification/
└── util/         DBConnection, CSVExporter, ThresholdConfig, Logger

src/main/resources/
├── fxml/         12 layouts         — must be Gluon-friendly (no Stage, no FileChooser)
├── css/          farm-theme.css
└── images/       logos, farm map
```

### Dependencies and how each survives on Android (Gluon)
| Dependency             | Verdict on Gluon Android                                   |
|------------------------|------------------------------------------------------------|
| `javafx-controls/fxml` | ✅ Provided by GluonFX                                      |
| `atlantafx-base`       | ⚠️ CSS works, but desktop-tuned — add mobile overrides     |
| `mysql-connector-j`    | ⚠️ Works as a Java lib, but see §3 risk note                |
| `jbcrypt`              | ✅ Pure Java, fine                                          |
| `ikonli-javafx` + feather | ⚠️ Needs GraalVM reflection config to load fonts        |
| `jSerialComm`          | ❌ Native desktop library — does not work on Android        |
| `gson`                 | ⚠️ Works with reflection config for serialized types       |
| `jfreechart` / `-fx`   | ❌ Drop on Android — replace with `javafx.scene.chart`      |

---

## 2. Migration target

- **Runtime:** Gluon Mobile (GluonFX) producing a native Android APK via
  GraalVM Substrate AOT compilation.
- **Architecture:** unchanged. `model → dao → service → ui` stays. Same
  package names. Same controller-per-FXML pattern.
- **Data path:** the phone connects **directly to MySQL via JDBC** (chosen
  by the team). See §3 for the risks the team has acknowledged.
- **IoT ingress:** the `FarmServer` TCP listener is **removed from the
  Android build**. ESP32 devices keep talking to the desktop variant or to
  a separate always-on host. The Android app is a *consumer* of the data
  via `sensor_readings`, not a producer.
- **Hardware:** the R307 fingerprint flow is **stubbed/disabled** in the
  first Android release; if needed later it goes via Android USB OTG using
  Gluon Attach, not `jSerialComm`.
- **Result:** one Maven module that produces both the desktop JAR (for
  graders / dev) and the Android APK (for end users).

---

## 3. Risks the team has acknowledged

The team chose **JDBC direct from the phone**. Documenting the trade-offs
so neither developer is surprised later:

1. **Network reachability.** A phone over 4G/5G cannot reach a MySQL on
   `localhost` or a private LAN IP. The DB needs a public host or the phone
   must be on the same Wi-Fi. Plan a VPN/tunnel or a public MySQL host.
2. **Credentials in the APK.** `db.properties` ends up bundled or asked at
   first run. Anyone with the APK can extract them. Treat the DB user as
   read-mostly and lock down its grants.
3. **Driver size & startup cost.** `mysql-connector-j` adds ~2 MB and
   non-trivial reflection — must be in the GraalVM reflection list.
4. **Single connection on a singleton.** Current `DBConnection` is a static
   singleton. Android can put the process to sleep at any moment; queries
   must run off the JavaFX UI thread and tolerate the connection being
   killed.
5. **No phone-side TCP server.** Android cannot keep a listening socket
   alive in the background, so `FarmServer` *must* be removed from the
   Android build.

These are known, accepted, and not part of the migration's success bar —
but the AI agent should not silently "fix" them without being asked.

---

## 4. Branch strategy

```
main
 ├── android/hagag        ← Hagag branches here, ships Phase 1 work
 └── android/3bdelbary    ← 3bdelbary branches here, ships Phase 1 work

(after both Phase 1 trees are green)

main ← merge android/hagag → main ← merge android/3bdelbary → main
                                     └── conflict resolution + Phase 2 here
```

- Both branches start from the same commit on `main`.
- Neither branch may delete a file the other branch owns.
- Neither branch may rename or move a file outside its lane.
- Commits within a branch are free-form; commit messages should prefix
  `[hagag]` or `[3bdelbary]` so the merge log reads cleanly.

---

## 5. Phase 0 — Joint setup (done together, BEFORE either branch starts)

Do this once, on `main`, with both developers present. The AI agent **does
not** start Phase 1 work until Phase 0 is merged.

- [ ] Decide GluonFX version (recommended: latest stable `gluonfx-maven-plugin`).
- [ ] Decide minimum Android API (recommended: API 26 / Android 8.0).
- [ ] Install GraalVM (Liberica NIK or GraalVM CE 22+) on both machines.
- [ ] Install Android SDK + NDK on both machines, set `ANDROID_SDK` /
      `ANDROID_NDK` env vars.
- [ ] Verify the desktop build still works (`mvn clean javafx:run`) so we
      have a baseline.
- [ ] Create the two branches `android/hagag` and `android/3bdelbary` from
      the same commit on `main`.
- [ ] Commit *this* file (`ANDROID_MIGRATION.md`) on `main` so both
      branches inherit it.
- [ ] Both developers read §6 (file ownership) and confirm it.

After Phase 0, each developer opens this file in their branch and starts
their Phase 1 list.

---

## 6. File ownership map (the contract)

This is the **single source of truth** for which files each track may
modify in Phase 1. The AI agent must enforce this.

### Hagag's owned paths (Phase 1 write access)
```
pom.xml
src/main/java/smartfarm/dao/**           (all DAOs)
src/main/java/smartfarm/service/**       (all services)
src/main/java/smartfarm/server/**        (FarmServer removal)
src/main/java/smartfarm/util/DBConnection.java
src/main/java/smartfarm/util/CSVExporter.java
src/main/java/smartfarm/util/Logger.java
src/main/java/smartfarm/util/ThresholdConfig.java
src/main/resources/db.properties.example   (new file)
src/main/resources/META-INF/native-image/**  (new dir, GraalVM config)
src/android/AndroidManifest.xml             (new file)
src/android/assets/**                       (new dir)
docs/MIGRATION_HAGAG.md                     (optional task notes)
```

### 3bdelbary's owned paths (Phase 1 write access)
```
src/main/java/smartfarm/Main.java
src/main/java/smartfarm/Launcher.java
src/main/java/smartfarm/ui/**               (every controller)
src/main/resources/fxml/**                  (every layout)
src/main/resources/css/**                   (theme + new mobile.css)
src/main/resources/images/**                (icons, splash)
src/main/resources/views/**                 (new dir, Gluon-style View classes)
docs/MIGRATION_3BDELBARY.md                 (optional task notes)
```

### Read-only for both tracks in Phase 1
```
src/main/java/smartfarm/model/**            (touch ONLY in Phase 2)
sql/**                                      (schema does not change)
firmware/**                                 (ESP32 firmware unchanged)
README.md                                   (update in Phase 2)
ANDROID_MIGRATION.md                        (this file — only fix typos)
```

> **Why `model/` is frozen in Phase 1:** every other layer imports it.
> A change to a model in either branch creates a guaranteed merge
> conflict in the other branch. Save model changes for Phase 2.

---

## 7. DO NOT TOUCH lists (one per track)

### Hagag must NOT touch
- Any file under `src/main/java/smartfarm/ui/`
- Any `.fxml`, `.css`, or `images/` resource
- `Main.java`, `Launcher.java`
- `model/` (frozen)

### 3bdelbary must NOT touch
- Any file under `dao/`, `service/`, or `server/`
- `pom.xml` (build config is Hagag's lane)
- `util/DBConnection.java`, `util/CSVExporter.java`, `util/Logger.java`
- `META-INF/native-image/**` (GraalVM config is Hagag's lane)
- `model/` (frozen)

If either developer hits a *blocker* that genuinely requires the other
track's file — **comment a TODO referencing the blocker** in their own
file and move on. Phase 2 is where those TODOs get reconciled.

---

## 8. Phase 1 — Parallel work

Both developers run Phase 1 at the same time, on their own branch, in any
order they like inside their own list.

---

### 8.A — Hagag's Phase 1 tasks (Build / Data / IoT)

**Goal:** the project builds an Android APK that links cleanly, exposes a
working `DBConnection` against MySQL, and has zero references to
desktop-only native libraries. UI does not need to render yet — that's
3bdelbary's lane — but the app must *start* without crashing in
`Main.start`.

#### H1. Add the GluonFX Maven plugin
- Edit `pom.xml`: add `gluonfx-maven-plugin`, configure `mainClass` to
  `smartfarm.Main`, set `target` to `android`.
- Keep the existing `javafx-maven-plugin` so the desktop build still
  works (`mvn javafx:run`). Both targets should coexist.
- Add Maven profiles `desktop` (default) and `android` so a single repo
  builds both.
- Add the GluonFX target steps as Maven goals that the team will use:
  - `mvn -Pandroid gluonfx:build` — AOT compile
  - `mvn -Pandroid gluonfx:package` — produce APK
  - `mvn -Pandroid gluonfx:install` — install on connected device
  - `mvn -Pandroid gluonfx:run` — run on connected device
- **Verify:** `mvn -Pdesktop javafx:run` still launches the desktop app.
- **Verify:** `mvn -Pandroid gluonfx:compile` succeeds (full APK build
  may need 3bdelbary's UI work — compile gate is enough for H1).

#### H2. GraalVM reflection / resource / JNI config
- Create `src/main/resources/META-INF/native-image/smartfarm/` and add:
  - `reflect-config.json` — list every class loaded by FXML, Ikonli, and
    `mysql-connector-j` (start from GluonFX templates, extend as needed).
  - `resource-config.json` — include `**/*.fxml`, `**/*.css`,
    `images/**`, `db.properties`.
  - `jni-config.json` — empty for now unless USB OTG is added later.
- Document how to regenerate these with GraalVM agent in
  `docs/MIGRATION_HAGAG.md`.
- **Verify:** APK assembly does not warn about missing reflection
  entries for any class in `smartfarm.*`.

#### H3. Android manifest + permissions
- Create `src/android/AndroidManifest.xml` with:
  - `INTERNET` (MySQL JDBC traffic).
  - `ACCESS_NETWORK_STATE`.
  - `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (Plant.id picker — for
    3bdelbary's UI side).
  - `WRITE_EXTERNAL_STORAGE` only if `CSVExporter` writes to scoped
    storage; otherwise rely on Storage Access Framework.
- Set `android:usesCleartextTraffic="true"` only if a non-TLS MySQL is
  used during development; flip off for release.
- App label, icon, splash hooks (icons themselves are 3bdelbary's lane —
  reference paths only).
- **Verify:** manifest merger emits no warnings during APK packaging.

#### H4. Rewrite `util/DBConnection.java` for Android
- Keep the same public surface (`Connection getInstance()`).
- Load credentials from, in order:
  1. environment variable (`DB_URL`/`DB_USER`/`DB_PASSWORD`) — desktop dev.
  2. Gluon Attach `Settings` plugin (Android `SharedPreferences`) — runtime
     configurable.
  3. `db.properties` packaged in `assets/` — fallback default.
- Make the connection lazy and re-creatable: any `SQLException` clears the
  cached `Connection` so the next call retries.
- Add a `closeQuietly()` helper for app shutdown.
- Make sure no DB call ever runs on the JavaFX UI thread:
  - Add `DBConnection.runAsync(Callable<T>) → CompletableFuture<T>`
    backed by a small `ExecutorService`.
- **Verify:** unit test (or a simple `main` harness) connects, runs
  `SELECT 1`, and disconnects without throwing.

#### H5. Sweep the DAO layer for UI-thread safety
- Every public method on every DAO must be safe to call from a
  background thread. They already are (JDBC is blocking) — the change is
  to **document** this and to make sure none of them log to `System.out`
  in a hot loop (Android shows logcat noise).
- No SQL changes. No method signature changes.
- Replace `e.printStackTrace()` with `Logger.e(tag, msg, e)` so logs go
  through Android's logcat layer.
- **Verify:** all 13 DAOs compile against the rewritten `DBConnection`.

#### H6. Rewrite `service/SessionManager.java` for Android
- Replace the file-on-disk implementation in `~/.agrilliant/` with a
  Gluon Attach `Settings` (Android `SharedPreferences`) implementation.
- Keep the public API identical: `saveSession(email)`, `loadSession()`,
  `clearSession()`. The HMAC token logic stays — only the storage layer
  changes.
- On desktop the same file should still work (use a `Settings` impl that
  falls back to file-based when `com.gluonhq.attach.settings.Settings`
  is unavailable).
- **Verify:** session round-trips on desktop; on Android emulator a
  saved email survives an app restart.

#### H7. Replace `service/FingerprintService.java` with a stub
- `jSerialComm` will not link on Android. Move the existing class to
  `service/desktop/FingerprintServiceDesktop.java` (still part of the
  desktop build via Maven profile).
- Create a new `service/FingerprintService.java` that returns
  `connected = false` for all calls and logs a single warning. The UI
  controllers must keep compiling against this name.
- Keep the public API identical so 3bdelbary's controllers don't break.
- **Verify:** Android profile build does not pull `jSerialComm`; desktop
  profile build still does.

#### H8. Rewrite `util/CSVExporter.java` for scoped storage
- Existing version returns a CSV `String` — keep that pure function.
- Add a new `saveCsv(String content, String suggestedName)` that on
  Android uses Gluon Attach `Storage` to write to a user-chosen folder
  (Storage Access Framework). On desktop it falls back to writing into
  `user.home/Downloads`.
- 3bdelbary's controllers already call `exportSensorData(...)` /
  `exportHarvestData(...)` — those signatures stay.
- **Verify:** unit test on the pure functions still passes; manual
  Android test writes a real `.csv` users can find.

#### H9. Decide what happens to `server/`
- The phone cannot host the TCP listener.
- **Action:** wrap `FarmServer` and `SensorHandler` in a profile guard so
  they are *only* compiled in the `desktop` profile. The Android profile
  has `Main` not start the server thread at all — controlled by a
  `Constants.IS_ANDROID` flag (or `com.gluonhq.attach.lifecycle.Lifecycle`
  presence check) read by `Main`.
- Add a one-line comment at the top of `FarmServer.java` saying
  "desktop build only".
- **Verify:** Android profile build does not include `FarmServer.class`;
  desktop profile still does.

#### H10. Logger + ThresholdConfig housekeeping
- `Logger`: route to `android.util.Log` when running on Android (use
  reflection or a Gluon Attach polyfill — don't import `android.*`
  directly so the desktop build still compiles).
- `ThresholdConfig`: read defaults from `assets/thresholds.properties`
  on Android and from the classpath on desktop. Same API.
- **Verify:** both targets log without throwing at startup.

#### H11. Document Hagag's notes
- Create `docs/MIGRATION_HAGAG.md` listing:
  - Final `pom.xml` profile names
  - GraalVM agent command used to regenerate reflection config
  - Steps to install/run on a real device
  - All TODOs left for Phase 2 (cross-track)

---

### 8.B — 3bdelbary's Phase 1 tasks (UI / Navigation / Resources)

**Goal:** every screen renders correctly in Gluon's `MobileApplication`
shell, navigation works via a Gluon `View`/`AppManager` pattern instead of
direct `Stage` switches, all FXML is touch-friendly, and the app boots
into the sign-in flow on Android. The DAO/service layer can stay stubbed
or hard-coded with sample data during 3bdelbary's branch — wiring is
Phase 2.

#### B1. Convert `Main.java` to a Gluon `MobileApplication`
- Replace the JavaFX `Application` extension with
  `com.gluonhq.charm.glisten.application.MobileApplication`.
- Define each screen as a named `View` factory:
  - `SIGNIN_VIEW`, `SIGNUP_VIEW`, `DASHBOARD_VIEW`, `CROPS_VIEW`,
    `WORKERS_VIEW`, `TASKS_VIEW`, `ALERTS_VIEW`, `MONITORING_VIEW`,
    `HARVEST_VIEW`, `REPORTS_VIEW`, `LOGS_VIEW`, `PLOTS_VIEW`,
    `DISEASE_VIEW`, `ATTENDANCE_VIEW`, `ABOUT_VIEW`, `SETTINGS_VIEW`.
- Keep `Launcher.java` as the desktop bootstrap; on Android it is unused.
- Replace `primaryStage.setMaximized(true)` etc. with mobile-friendly
  defaults.
- **Verify:** Gluon `MobileApplication` starts and shows the sign-in view
  in Scene Builder / desktop preview.

#### B2. Replace stage navigation with `AppManager` switch
- Find every `loader.load()` followed by `stage.setScene(...)` in every
  controller (`DashboardController`, `SignInController`, etc.).
- Replace with `AppManager.getInstance().switchView(VIEW_NAME)`.
- Pass cross-screen data via a small `NavContext` singleton in
  `smartfarm.ui.nav` — do not pass it through static fields on
  controllers.
- **Verify:** clicking every nav button in the dashboard reaches the
  correct view without any `Stage` reference.

#### B3. Make every FXML mobile-friendly
For each of the 12 FXML files, do the following pass:
- Remove fixed pixel widths/heights — use `HBox.hgrow` / `VBox.vgrow`
  and percentage widths so layouts adapt to portrait phones.
- Wrap any non-trivial layout in a `ScrollPane` so content is reachable
  on small screens.
- Touch targets: every `Button`/`ToggleButton` minimum 48 dp tall (use
  `-fx-min-height: 48` in CSS, not inline).
- Replace any `Tooltip`, `ContextMenu`, or right-click action with a
  long-press handler (`setOnLongPressed` via Gluon's gestures) or a
  trailing icon button.
- Replace `TableView` with `CharmListView` (Gluon) where the screen is
  a list — `dashboard.fxml`, `alerts.fxml`, `crops.fxml`, `workers.fxml`,
  `tasks.fxml`, `harvest.fxml`, `logs.fxml`, `reports.fxml`. Keep
  `TableView` only where columns truly matter (e.g. comparison reports).
- Replace `MenuBar` / `MenuItem` with a Gluon `NavigationDrawer`.
- Files to walk through (one commit per file is fine):
  - [ ] `signin.fxml`
  - [ ] `signup.fxml`
  - [ ] `dashboard.fxml`
  - [ ] `crops.fxml`
  - [ ] `plots.fxml`
  - [ ] `workers.fxml`
  - [ ] `tasks.fxml`
  - [ ] `alerts.fxml`
  - [ ] `monitoring.fxml`
  - [ ] `harvest.fxml`
  - [ ] `reports.fxml`
  - [ ] `logs.fxml`
- **Verify:** each screen loads in Scene Builder without binding errors
  and looks usable at 360×640 dp.

#### B4. Adapt every controller class
For each of the 17 controllers, do the following pass:
- Replace any `import javafx.stage.*` and `import javafx.scene.input.*`
  desktop-only references (right-click, drag-and-drop, FileChooser).
- Remove direct `Stage` / `Window` field references; navigation goes
  through `AppManager` (B2).
- Replace `FileChooser` (in `DashboardController` CSV export and
  `DiseaseDetectionPage` image picker) with Gluon Attach `Pictures` /
  `Storage` calls — a small `PlatformPickers` helper class in
  `smartfarm.ui.platform` keeps both desktop and Android working.
- Make sure no controller does its own threading — wrap any blocking
  call in `Task<T>` / `CompletableFuture` and update UI fields back on
  the FX thread (`Platform.runLater` if needed).
- Files to walk through:
  - [ ] `SignInController`
  - [ ] `SignUpController`
  - [ ] `DashboardController`
  - [ ] `CropController`, `CropsPage`
  - [ ] `PlotController`
  - [ ] `WorkerController`
  - [ ] `TaskController`
  - [ ] `AlertController`
  - [ ] `MonitoringController`
  - [ ] `HarvestController`
  - [ ] `ReportsController`
  - [ ] `LogsController`
  - [ ] `DiseaseDetectionPage`
  - [ ] `AttendancePage`
  - [ ] `SettingsPage`
  - [ ] `AboutPage`
- **Verify:** every controller compiles in the `desktop` profile and
  loads its FXML without throwing. Wiring to live DAOs happens in Phase 2.

#### B5. Theme + CSS for mobile
- Keep AtlantaFX `PrimerLight` as the base.
- Add `src/main/resources/css/mobile.css` with:
  - Larger base font (`-fx-font-size: 14sp` equivalent → 16 px).
  - Min-height on inputs/buttons.
  - Larger row height for `CharmListView`.
  - Reduced padding where it would push content off-screen.
- Load `mobile.css` after the AtlantaFX stylesheet in `Main`.
- **Verify:** all 12 screens still look intentional, not like the desktop
  with text shrunk.

#### B6. Charts on mobile
- Keep `javafx.scene.chart.LineChart` (already used in dashboard).
- **Remove** any `JFreeChart` / `JFreeChart-FX` use — Gluon-friendly
  charts only. The dashboard already mostly uses native JavaFX charts;
  audit and remove leftover JFreeChart imports if any.
- For monitoring (`MonitoringController`), make sure live updates use
  `Platform.runLater` and bound the data series to the last N points so
  memory does not grow unbounded.
- **Verify:** scrolling the dashboard while live data ticks does not
  freeze the UI on a real device.

#### B7. Splash + icons
- Create launcher icons at the standard Android densities (mdpi → xxxhdpi)
  derived from `images/logo.png`. Place under
  `src/main/resources/icons/android/` (path TBD with Gluon docs — file in
  `MIGRATION_3BDELBARY.md`).
- Add a splash screen image referencing the existing logo.
- **Verify:** launcher icon shows up on emulator home screen.

#### B8. Image picker + camera for disease detection
- `DiseaseDetectionPage` currently relies on JavaFX `FileChooser`.
- Add a `PlatformPickers.pickImage()` static using Gluon Attach
  `Pictures` plugin — returns a `File` on both targets.
- Wire `DiseaseDetectionPage` to use it. The existing Plant.id HTTP
  call in `service/PlantIdService` is unchanged (Hagag owns service
  layer, but the *call site* is in 3bdelbary's controller — only call
  site changes here).
- **Verify:** picking an image in the Android emulator returns a real
  `File` and the existing service call accepts it.

#### B9. Lifecycle hooks
- For each controller, replace any "set up on stage shown" logic with
  Gluon's `View#setOnShown` / `View#setOnHidden`.
- Stop background timers / animations on `setOnHidden` so a paused app
  does not keep ticking.
- **Verify:** rotating the emulator does not duplicate timers.

#### B10. Document 3bdelbary's notes
- Create `docs/MIGRATION_3BDELBARY.md` listing:
  - Every FXML's status (touch-pass done? scroll-wrap done?)
  - Every controller's status (lifecycle done? FileChooser replaced?)
  - All TODOs left for Phase 2 (cross-track), e.g. "DAO call here is
    still synchronous — Phase 2 must wrap in async".

---

## 9. Phase 2 — Merge & Integration (AI agent: do NOT start until both branches are merged)

This phase begins **after** `android/hagag` and `android/3bdelbary` are
both merged into `main`. The AI agent should refuse to do Phase 2 work in
either Phase 1 branch — it requires both trees in the same commit.

### Entry condition
- `git log main` shows both Phase 1 merge commits.
- `mvn -Pdesktop javafx:run` still launches the desktop app on `main`.
- `mvn -Pandroid gluonfx:compile` succeeds on `main`.

### M1. Reconcile `model/` if needed
- If either branch left a TODO requesting a model field, add it now.
- Re-run a build to confirm DAOs and controllers still compile.

### M2. Wire UI to async DAO calls
- 3bdelbary's controllers may still call DAOs synchronously. Hagag added
  `DBConnection.runAsync(...)` in H4. Wrap every DAO call site that runs
  on the FX thread with `runAsync(...)` and update UI in `thenAccept` on
  the FX thread.
- This is the bulk of Phase 2 work — go controller by controller.

### M3. Wire `PlatformPickers` to Hagag's storage helpers
- `CSVExporter.saveCsv` (H8) and `PlatformPickers` (B8) both touch
  storage. Make sure 3bdelbary's call sites use Hagag's helpers, not
  duplicate logic.

### M4. Remove `FarmServer` startup from `Main.java`
- 3bdelbary already replaced `Main.java` with the Gluon entry. Confirm
  it does **not** call `new FarmServer().start()`. Hagag's H9 made the
  class desktop-only — the Android entry simply must not reference it.
- Desktop `Launcher.java` keeps starting `FarmServer` for parity with
  the existing setup.

### M5. Resolve `FingerprintService` references
- `AttendancePage` still calls the fingerprint API. Confirm it now uses
  the Android-safe stub (H7) and shows a friendly "Fingerprint hardware
  not available on this device" message instead of crashing.

### M6. Build the APK end-to-end
- `mvn -Pandroid gluonfx:build` → `gluonfx:package` → `gluonfx:install`.
- First runs almost always crash on missing GraalVM reflection entries.
  Run with the GraalVM tracing agent on desktop to capture missing
  classes, then add to `reflect-config.json` (Hagag-owned file but in
  Phase 2 either developer can add entries).
- Common offenders: FXML root types, Ikonli icon classes, Gson model
  types, `mysql-connector-j` internals.

### M7. End-to-end smoke test on an Android emulator
Walk through these flows on a Pixel 5 emulator (API 34):
- [ ] Launch app → sign-in screen renders.
- [ ] Sign up a new manager account → returns to sign-in.
- [ ] Sign in → dashboard renders with empty state.
- [ ] Create a plot, then a crop.
- [ ] View a sensor reading (insert one via desktop client first).
- [ ] Trigger a fake alert (insert via SQL).
- [ ] Acknowledge the alert.
- [ ] Create a worker, assign a task.
- [ ] Pick an image for disease detection → Plant.id call succeeds.
- [ ] Export a CSV → file lands in user-visible storage.
- [ ] Sign out → session cleared → restart returns to sign-in.

### M8. Polish + README
- Update `README.md` with the Android build/run instructions and a
  caveat section that mirrors §3 (JDBC-from-phone notes).
- Add screenshots from the emulator to `docs/`.
- Tag a release: `v2.0-android-mvp`.

### M9. Known follow-ups (not part of the migration)
Document, do not fix:
- Migrating to a REST backend in front of MySQL.
- Real fingerprint integration via Android USB OTG.
- Push notifications via FCM.
- Offline-first SQLite + sync.

---

## 10. Verification gates

### After Phase 0
- `mvn -Pdesktop javafx:run` works on `main`.
- Both branches exist and start from the same commit.

### After each Phase 1 task (per developer)
- The owning developer's profile builds:
  - Hagag: `mvn -Pdesktop test-compile` and `mvn -Pandroid gluonfx:compile`.
  - 3bdelbary: `mvn -Pdesktop javafx:run` reaches the changed screen.
- No file outside §6 ownership has been modified — `git diff --name-only main`.

### After Phase 1 (both branches done, before merge)
- Both branches green on their own.
- Spot-check: `git diff --name-only main..android/hagag` and
  `git diff --name-only main..android/3bdelbary` produce **non-overlapping**
  file lists, except possibly `ANDROID_MIGRATION.md` typo fixes.

### After Phase 2
- `mvn -Pandroid gluonfx:package` produces an APK.
- All ten flows in §M7 pass on the emulator.
- `mvn -Pdesktop javafx:run` still launches the desktop app (no
  desktop regression).

---

## 11. Conflict-avoidance rules (cheat sheet)

1. **Stay in your lane.** §6 is the law; §7 is the explicit "no" list.
2. **`model/` is frozen** until Phase 2.
3. **No file renames** across lane boundaries in Phase 1.
4. **No `pom.xml` edits by 3bdelbary** — request via TODO and Hagag adds.
5. **No `ui/` edits by Hagag** — request via TODO and 3bdelbary adds.
6. **Comment TODOs with `// TODO(phase-2): <reason>`** so Phase 2 can
   grep them.
7. **Never silently rewrite** the other track's stub. If the stub needs
   to grow, drop a TODO and let the owner do it.
8. **Commit small.** One task per commit makes the merge readable.
9. **The AI agent never crosses the line on its own.** It must stop and
   ask when a fix would require touching the other track's files.

---

## 12. Quick reference for the AI agent

When you start a session, run through this checklist:

1. Which developer am I working with? (Hagag or 3bdelbary)
2. Which branch am I on? (`android/hagag` or `android/3bdelbary` or
   `main`)
3. Is this Phase 0, Phase 1, or Phase 2?
4. Which task in §8.A or §8.B am I on?
5. Confirm the file I'm about to edit is in §6 for this track.
6. Confirm the file I'm about to edit is **not** in §7 (DO NOT TOUCH).
7. Make the edit, run the matching gate in §10, commit.
8. If blocked by something in the other track: write a
   `// TODO(phase-2): ...` comment and move on.

If any of (1)–(6) is unclear, **stop and ask the developer**. Do not
guess.
