# Agrilliant Android Migration — Current Status

> Branch: `mobile-app` | Last updated: 2026-05-15 (Phase 2 in progress)
> Tracks: **Hagag** (Build / Data / IoT — §8.A) + **3bdelbary** (UI / Navigation / Resources — §8.B)

---

## Summary

The Agrilliant desktop JavaFX app is being migrated to Android via Gluon Mobile (Glisten + Attach + GluonFX native-image). Both Phase 1 tracks have made substantial progress:

- **Hagag's track (H1–H11):** Complete.
- **3bdelbary's track (B1–B10):** Complete.
- **Post-B10 cross-track follow-ups (user-authorized):** JFreeChart deps removed from `pom.xml`; Gluon Attach `LifecycleService` PAUSE/RESUME wired to `DashboardController.startLifecycle()`/`stopLifecycle()`; `Crop.GrowthStage='GROWING'` SQL workaround migration script committed at `docs/sql/2026-05-14-fix-growthstage-growing.sql`. The repo is now ready for the first `mvn -Pandroid gluonfx:build`.
- **Build environment (Windows-friendly, user-authorized):** GitHub Actions workflow at `.github/workflows/android-build.yml` builds the APK on a Linux cloud runner so Windows-host developers don't need WSL2. Local-Linux fallback path is `scripts/wsl-setup-android-build-env.sh` (one-shot installer for WSL2 Ubuntu). Docs at `docs/CI_ANDROID_BUILD.md` (cloud) and `scripts/README.md` (local).
- **Phase 2 (3bdelbary, in progress):** P2.1–P2.5 landed — `AsyncCalls` helper + 44 DAO call sites across 13 controllers refactored off the FX thread, auth flow timeouts (10 s sign-in/up, 5 s splash restore), and the width-driven dashboard sidebar / hamburger toggle at the 900 px breakpoint. P2.6–P2.12 (UX polish + doc sweep) still pending.

The app compiles cleanly on both profiles and boots through Splash → SignIn on desktop. No Android APK has been built yet (needs GraalVM + Android SDK toolchain).

---

## What Is Accomplished

### Hagag Track (Build / Data / IoT) — ALL DONE

| Task | Description | Status |
|------|-------------|--------|
| H1 | GluonFX Maven plugin + desktop/android profiles | Done |
| H2 | GraalVM native-image config seeds (reflect, resource, jni JSON) | Done |
| H3 | AndroidManifest.xml + permissions | Done |
| H4 | DBConnection rewrite: lazy init, layered creds (env → Settings → properties), async helper | Done |
| H5 | Logger API + DAO thread-safety documentation sweep | Done |
| H6 | SessionManager via Gluon Settings + file fallback + HMAC migration | Done |
| H7 | FingerprintService: Android stub + desktop impl split (SPI pattern) | Done |
| H8 | CSVExporter: `saveCsv()` using Gluon Storage / `~/Downloads` | Done |
| H9 | `Constants.IS_ANDROID` + desktop-only headers on server classes | Done |
| H10 | Logger → `android.util.Log` reflection routing + ThresholdConfig from properties | Done |
| H11 | Migration documentation | Done |
| Review | Post-review fixes: race condition, perf caching, doc corrections, RFC-4180 CSV quoting | Done |

### 3bdelbary Track (UI / Navigation / Resources) — ALL DONE

| Task | Description | Status |
|------|-------------|--------|
| B1 | Convert `Main.java` to Gluon `MobileApplication` with 4 view factories (SPLASH, SIGNIN, SIGNUP, SHELL) | Done |
| B2 | Replace `Stage` navigation with `AppManager.switchView()` across SignIn/SignUp/Dashboard/Shell | Done |
| B3 | Make every FXML mobile-friendly: drop hardcoded sizes, TableView → CharmListView, remove hover-only tooltips | Done |
| B3X | UX rework: vertical auth forms, FlowPane summary/filters, NavigationDrawer + AppBar, alerts stacked layout | Done |
| Fix | `CropController.colVariety` NPE — dropped dead `@FXML` field + its 2 references (column had no backing model field) | Done |
| B4 | Replace 8 `FileChooser` call sites: 7 CSV exports → `CSVExporter.saveCsv()`, 1 image picker → `PlatformPickers.pickImage()` | Done |
| B5 | `css/mobile.css` with ≥48dp touch targets, 16 px base font, `CharmListView`/drawer row bumps. Loaded only on Android via `Constants.IS_ANDROID` branch in `Main.postInit` | Done |
| B6 | JFreeChart audit — 0 references in `src/`; FX-thread safety of `MonitoringController.subscribeLiveSensor` confirmed via `LiveSensorData.update` → `Platform.runLater`; defensive `TODO(phase-2)` added in `setupTrendChart` for bounded-series rule | Done |
| B7 | 10 Android launcher PNGs (5 densities mdpi → xxxhdpi × square + round variants) generated from `images/logo.png` and committed under `src/android/res/mipmap-*/`. Two side-by-side generators ship: Java (`smartfarm.ui.tools.LauncherIconGenerator`) and PowerShell (`generate-launcher-icons.ps1`). Both use the shared `smartfarm.ui.platform.PngEncoder` / `System.Drawing` to produce byte-equivalent layouts — `#2e7d32` background, logo at 72% edge, circular clip on round variant. | Done |
| B8 | `smartfarm.ui.platform.PlatformPickers` with Gluon `PicturesService` on Android, `FileChooser` on desktop, pure-Java PNG encoder for Substrate-safe persistence | Done |
| B9 | Lifecycle hooks sweep: zero `stage.setOn*` patterns in `ui/` (B1/B2 already eliminated); `MonitoringController` listener leak across `loadFxmlPage` page swaps fixed via `sceneProperty()` auto-detach; `DashboardController` `Timeline` + 5 `LiveSensorData` listeners promoted to instance fields with new public `stopLifecycle()` method ready for Phase 2 `LifecycleService.PAUSE` wiring | Done |
| B10 | Final consolidation: per-FXML status matrix (12 rows), per-controller status matrix (17 rows), 4 Gluon View status, consolidated Phase 2 TODO list (async / lifecycle / layout / assets / tests / frozen-model), cross-track items waiting on Hagag, read-only-imports recap, complete file inventory across the track. See `docs/MIGRATION_3BDELBARY.md` §B10. | Done |

#### B1 Key Deliverables
- `AppView.java` — 4-value enum mapping to Gluon View names (SPLASH → HOME_VIEW)
- `NavContext.java` — singleton cross-view session state
- `SplashView.java` — logo + spinner + async session restore with 800ms min-visible guard
- `SignInView.java` / `SignUpView.java` — wrap existing FXMLs
- `ShellView.java` — wraps dashboard, configures NavigationDrawer + AppBar
- `Main.java` — extends `MobileApplication`, registers 4 lazy view factories, starts FarmServer reflectively (desktop only)

#### B2 Key Deliverables
- All `Stage`/`Scene`/`setRoot` navigation replaced with `AppView.X.switchTo()`
- Zero `import javafx.stage.Stage` remaining in `smartfarm/ui/`

#### B3 Key Deliverables
- 12 FXMLs: all hardcoded root sizes removed
- 4 list-shaped views converted to `CharmListView` with `CharmListCell` + `ListTile` rows (workers, tasks, harvest, logs)
- Hover-only tooltips removed from `plots.fxml`
- Defensive `RuntimeException` catch added to `HarvestController` for null-DB DAOs

#### B3X Key Deliverables
- `signin.fxml` / `signup.fxml`: two-column HBox → vertical stack (branding header + full-width form)
- Summary card rows on 8 inner pages: `HBox` → `FlowPane` with `minWidth=200` (wraps responsively)
- Filter rows on 8 inner pages: `HBox` → `FlowPane` with `minWidth=240` search box
- `dashboard.fxml`: fixed 240dp sidebar replaced with Gluon `NavigationDrawer` (hamburger slide-in) + `AppBar`
- `alerts.fxml`: side-by-side `HBox` → stacked `VBox` (list on top, detail below)
- `DashboardController`: new `navigate(NavTarget)` public API + `NavTarget` enum (14 targets)
- Sidebar/topbar hidden via `visible="false" managed="false"` (preserves existing controller logic)

#### B4 + B8 Key Deliverables
- `PlatformPickers.pickImage(Window) → Optional<File>` in new package `smartfarm.ui.platform` — Gluon `PicturesService` on Android (with pure-Java PNG encoder for Substrate-safe persistence to private storage), `FileChooser` on desktop
- 7 CSV exports rewritten to build `StringBuilder` content then `CSVExporter.saveCsv(content, name)` — `DashboardController` ×4 (`onExportReport`, `onExportSensorCSV`, `onExportHarvestCSV`, `onExportAlertCSV`), `LogsController.onExport`, `ReportsController.onExport`, `CropController.onExport`. Column layouts preserved verbatim.
- `DiseaseDetectionPage.selectImage` now uses `PlatformPickers.pickImage(getScene().getWindow())` with `Optional.ifPresent`. Downstream `PlantIdService.analyzeImage(String)` unchanged — `PlatformPickers` always returns a real file path on both platforms.
- `javafx.stage.FileChooser` references in `ui/`: **0** (only the legitimate desktop fallback inside `PlatformPickers` itself)
- `FileWriter` references in `src/`: **0** (all replaced with `CSVExporter.saveCsv()`)

#### B5 Key Deliverables
- New stylesheet `src/main/resources/css/mobile.css` (~110 lines, ~25 selectors). Bumps base font to 16 px on `.root`, enforces 48dp min-height on every tappable control (`.button`, `.text-field`, `.combo-box`, `.date-picker`, `.menu-button`, dialog buttons), bumps `.list-tile` / `.charm-list-cell` to 84dp (fits a 3-line tile with 48dp action buttons), drawer rows to 56dp (Material spec).
- **Loaded only on Android** — `Main.postInit` now branches on `Constants.IS_ANDROID`: Android picks up `mobile.css` after `farm-theme.css`; desktop keeps its dashboard-optimized sizing untouched. Avoids the "desktop UI suddenly looks like the phone" failure mode flagged in §8.B5's verification gate.
- The width-based dashboard sidebar toggle mentioned in the B3X follow-ups is deferred to Phase 2 — not implementable in pure JavaFX CSS (no media queries). Needs a `Scene.widthProperty()` listener instead.

#### B6 Key Deliverables
- Verified `src/` has zero JFreeChart references (`jfree`, `JFreeChart`, `ChartFactory`, `ChartPanel`, `XYPlot`, `JFreeChart-FX`). The B3-era survey had already moved away from it; B6 is the formal sign-off.
- Verified `MonitoringController.subscribeLiveSensor` fires UI setters on the FX thread — the indirection runs through `LiveSensorData.update(...)`, which wraps its property mutations in `Platform.runLater`.
- Added defensive `TODO(phase-2)` block comment in `MonitoringController.setupTrendChart()` documenting the bounded-series rule for the next engineer wiring real live data (cap series at N, trim from head before append). The current chart is mock-only so unbounded growth doesn't manifest, but the comment captures the gotcha.
- **Hagag follow-up:** `pom.xml` desktop profile still declares `org.jfree:jfreechart:1.5.4` + `org.jfree:jfreechart-fx:1.0.1` with a comment waiting on exactly this audit. Safe to drop now — trims ~3 MB from the desktop fat-jar.

#### B7 Key Deliverables
- New `smartfarm.ui.platform.PngEncoder` — PNG encoder extracted from `PlatformPickers` (B8) into a reusable utility. RGBA, filter type 0, single IDAT, deflate via `java.util.zip`. Substrate-safe (no `java.desktop`/`javafx.swing`). Single public method `encode(Image) → byte[]`.
- `PlatformPickers` refactored to delegate to `PngEncoder` — ~60 fewer lines, identical behaviour.
- New `smartfarm.ui.tools.LauncherIconGenerator` (JavaFX `Application`) — reads `images/logo.png`, renders 5 densities (mdpi 48 → xxxhdpi 192) × 2 variants (square + round), writes via `PngEncoder` to `src/android/res/mipmap-*/ic_launcher{,_round}.png`. Run via `mvn -Pdesktop compile exec:java`.
- New `src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1` — PowerShell mirror using `System.Drawing` for environments without the full Maven toolchain. Used to produce the committed PNGs.
- **10 launcher PNG bitmaps generated and committed** at:
  ```
  src/android/res/mipmap-mdpi/ic_launcher.png        (48×48)
  src/android/res/mipmap-mdpi/ic_launcher_round.png  (48×48)
  src/android/res/mipmap-hdpi/...                    (72×72)
  src/android/res/mipmap-xhdpi/...                   (96×96)
  src/android/res/mipmap-xxhdpi/...                  (144×144)
  src/android/res/mipmap-xxxhdpi/...                 (192×192)
  ```
  Each icon is the `#2e7d32` brand-green background with the bird logo centred at 72% of the edge. Round variants use an antialiased circular background with a clip path so the logo can't bleed outside the disc.

#### B9 Key Deliverables
- **Audit clean for `stage.setOn*` / `windowProperty` patterns** — zero hits across `src/main/java/smartfarm/ui` (B1/B2 already eliminated them). All 4 Gluon Views (`SplashView`, `SignInView`, `SignUpView`, `ShellView`) use `setOnShowing` / `setOnHiding`.
- **`MonitoringController` listener leak fixed.** Each visit to the Monitoring tab was attaching 3 lambdas to the `LiveSensorData` singleton without ever detaching, keeping the old controller alive. Promoted the lambdas to `ChangeListener<Number>` fields and hook `lblTemp.sceneProperty()` so when the scene goes `null` (page unmounted by `DashboardController.loadFxmlPage`), `unsubscribeLiveSensor()` removes all three from the singleton. Pattern documented for any future sub-page controllers that grow service-singleton subscriptions.
- **`DashboardController` lifecycle handles in place for Phase 2.** Today the shell caches the controller for the JVM lifetime so its `Timeline` clock and 5 `LiveSensorData` listeners don't actually leak — but they tick / fire even when the app is OS-backgrounded. Refactor:
  - `Timeline clock` promoted from a local to an instance field; `updateDateTime()` is idempotent (early-return if already started).
  - 5 listeners (`liveTempListener`, `liveHumListener`, `liveSoilListener`, `liveDeviceListener`, `activeSensorsListener`) promoted to `ChangeListener` fields with null-guarded attach.
  - New public `stopLifecycle()` method stops the clock and removes all 5 listeners. Idempotent. Documented as the intended hook for Phase 2's Gluon Attach `LifecycleService.PAUSE` event.
- **Rotation safety confirmed.** `AndroidManifest.xml` declares `android:configChanges="orientation|keyboardHidden|screenSize|smallestScreenSize"` on the launcher activity, so orientation changes don't tear down and rebuild the Activity / JVM. The field-backed `Timeline` provides defense-in-depth for any future scenario where the JVM does restart.

### Phase 2 — In Progress (3bdelbary)

Phase 2 picks up the async / lifecycle / layout follow-ups parked at the end of Phase 1. Goals: keep the FX thread free during every DB round-trip, harden the auth flow against hung connections, and make the dashboard feel native on both tablet-landscape (sidebar) and phone-portrait (drawer) widths.

| Task | Description | Status |
|------|-------------|--------|
| P2.1 | `AsyncCalls` helper — wraps `DBConnection.runAsync` with FX-thread marshalling + `Duration`-timeout overloads for `runAndApply` / `runWithBusy` | Done |
| P2.2 | `DashboardController` async sweep — 12 DAO call sites + 4 cached-fallback paths | Done |
| P2.3a | `AttendancePage` / `AlertController` / `MonitoringController` / `WorkerController` — 10 sites | Done |
| P2.3b | `PlotController` / `ReportsController` / `HarvestController` — 7 sites | Done |
| P2.3c | `TaskController` + `CropController` — 16 sites (incl. filter-setup duplication bug fix) | Done |
| P2.4 | Auth flow async + timeouts: `SignInController` (10 s) / `SignUpController` (10 s) / `SplashView` (5 s) | Done |
| P2.5 | Width-based dashboard sidebar toggle — 900 px breakpoint listener on `Scene.widthProperty()` | Done |
| P2.6 | `AlertController` master-detail full-width on wide viewports | Pending |
| P2.7 | NavigationDrawer footer status dots (system / DB / sensors) | Pending |
| P2.8 | `DiseaseDetectionPage` "Take Photo" button (Gluon `PicturesService` — capture mode) | Pending |
| P2.9 | `MonitoringController.setupTrendChart` → `LiveSensorData` with bounded series | Pending |
| P2.10 | `cmbChartPeriod` listener wired | Pending |
| P2.11 | Adaptive launcher icons (Android 8.0+ foreground/background layers) | Pending |
| P2.12 | This doc + `MIGRATION_3BDELBARY.md` Phase 2 section | In progress |

#### P2.1–P2.3 Key Deliverables (async sweep)
- New helper `src/main/java/smartfarm/ui/async/AsyncCalls.java` with six entry points: `runAndApply` (3 overloads incl. a `Duration` timeout), `runWithBusy` (3 overloads incl. a `Duration` timeout), `runFireAndForget`, `runFireAndForgetThen`. All marshal the success / error consumer back to the FX thread via `Platform.runLater` and unwrap `CompletionException` / `ExecutionException` so callers see the real `SQLException` / `TimeoutException`.
- **44 DAO call sites** across **13 controller files** converted from synchronous DAO calls on the FX thread to `AsyncCalls.runAndApply` / `runWithBusy`. Count confirmed by `grep "AsyncCalls\.(runAndApply\|runWithBusy\|runFireAndForget)" src/main/java/smartfarm/ui/` (12 in `DashboardController`, 6 each in `CropController` / `TaskController`, 5 each in `HarvestController` / `WorkerController`, 2 each in `AlertController` / `AttendancePage`, 1 each in `MonitoringController` / `PlotController` / `ReportsController` / `SignInController` / `SignUpController` / `SplashView`).
- UX guards preserved across the sweep: per-row in-memory mutation order (advance / revert task status, fingerprint-rollback on worker save failure), filter combo idempotence (`CropController.setupFilters` split into one-time setup + idempotent `refreshPlotFilter`), and stale-async guards (`CropController.onAdvanceStage` / `buildCareHistoryTab` short-circuit if `selectedCrop` changed mid-fetch).
- Documented caveat: `CompletableFuture.orTimeout` does **not** cancel the underlying JDBC call. If the driver hangs, the DB executor's worker stays blocked behind it and subsequent operations queue. The timeout only frees the FX side so the UI can recover (e.g. SplashView falls through to sign-in on a hung restore). See the Javadoc on the `Duration` overloads of `runAndApply` / `runWithBusy`.

#### P2.4 Key Deliverables (auth flow)
- `SignInController.onSignIn` and `SignUpController.onSignUp` refactored to `AsyncCalls.runWithBusy(button, ..., Duration.ofSeconds(10), errorHandler)`. The submit button stays disabled until success / error / timeout; an inline error message renders the right copy for the 10-second cutoff.
- `SplashView` session restore: ad-hoc `Thread` + `Platform.runLater` boilerplate replaced with `AsyncCalls.runAndApply(..., Duration.ofSeconds(5), ...)`. The 800 ms minimum-visible guard is now expressed as a `PauseTransition` on the FX thread, so a fast DB still gets the full splash beat; a hung DB falls through to `SIGNIN` after 5 s.
- Both `runAndApply` and `runWithBusy` gained `Duration` overloads in P2.1 specifically to unblock this work.

#### P2.5 Key Deliverables (responsive sidebar)
- `DashboardController` exposes `setSidebarInline(boolean)` which toggles the legacy sidebar `VBox`'s `visible` + `managed` properties together (collapses layout, not just paint).
- `ShellView` owns a `WIDE_BREAKPOINT = 900.0` constant and a `ChangeListener<Number> widthListener` attached to `Scene.widthProperty()` from `setOnShowing` and detached from `setOnHiding`. Below 900 px: hamburger in AppBar, sidebar collapsed, drawer-driven nav (phone / portrait-tablet). At or above 900 px: sidebar inflates inline, hamburger hidden (tablet-landscape / desktop).
- Listener lifecycle is the correctness win — detaching on hide stops chrome from bleeding into sign-in / sign-up if the user resizes the window during auth.
- 900 px breakpoint catches iPad-portrait-and-up (768 px) while keeping standard phones (≤ 412 px logical width) on the drawer pattern.

### Build Verification

| Gate | Result |
|------|--------|
| `mvn -Pdesktop clean compile` | BUILD SUCCESS (79 sources) |
| `mvn -Pandroid clean compile` | BUILD SUCCESS (75 sources) |
| FXML load smoke (with DB) | **9/12 pass** (retested 2026-05-14 with real MySQL at 139.59.153.80) |
| Boot smoke (desktop) | Splash → SignIn end-to-end, FarmServer starts |
| Boot smoke (android profile) | Splash → SignIn end-to-end, FarmServer skipped (correct) |

---

## What Is Missing

Nothing on 3bdelbary's Phase 1 list. All B-tasks are complete and the post-B10 cross-track items the user pulled forward (JFreeChart pom cleanup, LifecycleService wiring, SQL migration) are also done.

The only remaining work is environmental — running the actual APK build on a host with GraalVM + Android SDK + NDK installed. See `docs/MIGRATION_3BDELBARY.md` *Post-B10 → APK build pre-flight checklist* for the exact command sequence and common first-run failure modes.

### Cross-track items still pending (low priority)
- **`exec-maven-plugin` for the launcher generator (optional)** — would let the team run `mvn exec:java -Dexec.mainClass=smartfarm.ui.tools.LauncherIconGenerator`. The PowerShell variant (`src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1`) is the working alternative that needs no Maven plumbing.
- **Phase 2 async DAO sweep** — **Done in P2.1–P2.3c** (44 sites across 13 controllers via `AsyncCalls`). See the *Phase 2 — In Progress* section above and `docs/MIGRATION_3BDELBARY.md` for details.

---

## Known Errors and Issues

### 1. FXML Load Failures (2/12) — Retested 2026-05-14 with real DB

Retested with `db.properties` pointing to the real MySQL at `139.59.153.80`. DB connection now succeeds, which changed the failure profile: `monitoring.fxml` now passes (was failing with DB NPE before), and `crops.fxml` now passes after the `colVariety` dead-code removal. Two FXMLs still fail — both with the `Crop.GrowthStage.GROWING` enum mismatch, not the previous "no DB" NPEs.

**Full smoke results:**

| FXML | Status | Details |
|------|--------|---------|
| `signin.fxml` | PASS | |
| `signup.fxml` | PASS | |
| `dashboard.fxml` | **FAIL** | `IllegalArgumentException: No enum constant smartfarm.model.Crop.GrowthStage.GROWING` — DB has `growth_stage='GROWING'` but the Java enum only defines `SEED, SEEDLING, VEGETATIVE, FLOWERING, FRUITING, HARVESTED` |
| `crops.fxml` | PASS | (was failing on `colVariety` NPE; fixed by dropping the dead field from `CropController`) |
| `plots.fxml` | PASS | (logs `IllegalArgumentException: No enum constant Crop.GrowthStage.GROWING` but catches it — `PlotController.initialize` has a try/catch guard) |
| `workers.fxml` | PASS | |
| `tasks.fxml` | PASS | |
| `alerts.fxml` | PASS | |
| `monitoring.fxml` | PASS | (was failing without DB; now works with real DB) |
| `harvest.fxml` | PASS | |
| `reports.fxml` | **FAIL** | `IllegalArgumentException: No enum constant smartfarm.model.Crop.GrowthStage.GROWING` — same enum mismatch as dashboard |
| `logs.fxml` | PASS | |

**Root cause #1 — `Crop.GrowthStage` enum vs DB data mismatch:**

The `crops` table contains rows with `growth_stage = 'GROWING'`, but `Crop.GrowthStage` only defines:
```java
public enum GrowthStage { SEED, SEEDLING, VEGETATIVE, FLOWERING, FRUITING, HARVESTED }
```
`CropDAO.extractCrop()` calls `Crop.GrowthStage.valueOf(rs.getString("growth_stage"))` which throws `IllegalArgumentException` for any value not in the enum. This crashes `dashboard.fxml` and `reports.fxml` during `initialize()`. `plots.fxml` survives because `PlotController.initialize()` wraps the call in a try/catch.

**Fix options:** (a) Add `GROWING` to the enum (if the DB value is intentional), (b) UPDATE the DB rows to use a valid enum value like `VEGETATIVE`, or (c) Make `CropDAO.extractCrop()` handle unknown enum values gracefully (e.g. default to `VEGETATIVE`). Options (a) and (c) touch `smartfarm/model/` and `smartfarm/dao/` — Hagag's lane / frozen for Phase 1 — so the immediate workaround is option (b) via SQL.

**Previous (no-DB) smoke for comparison:**

Before the env files were added (no DB credentials), the smoke was 8/12 pass. The 4 failures were all `NullPointerException: Cannot invoke "...Connection.createStatement()" because "this.conn" is null` in `dashboard`, `crops`, `monitoring`, `reports`. With the real DB connected, `monitoring` now passes and (after the `colVariety` dead-code removal) `crops` also passes. The 2 remaining failures (`dashboard` + `reports`) surface their real root cause — the `Crop.GrowthStage.GROWING` enum mismatch — instead of the generic "no DB" NPE.

### 2. Headless Monocle NagScreen Crash — Glisten + headless-only

Under headless Monocle (no display), Glisten's `NagScreenPresenter` (triggered by missing license file) calls `enterNestedEventLoop`, which Monocle's strict event-thread check rejects. Not an app code bug — only affects headless test environments. On real displays the nag dialog renders fine.

### 3. NavigationDrawer Not Visually Tested

The ShellView drawer + AppBar configuration is compile-verified but has not been visually smoke-tested with a real login session (requires DB credentials). The drawer items call `DashboardController.navigate(NavTarget)` which delegates to the existing `loadFxmlPage` path — this should work but hasn't been confirmed end-to-end.

### 4. No Android APK Built Yet

The full `mvn -Pandroid gluonfx:build` → APK pipeline has not been run. Requires:
- GraalVM / Liberica NIK installed
- Android SDK + NDK available
- B7 launcher icons in place (otherwise manifest references fail)

### 5. `FarmServer.class` Still Leaks into Android Build

`Main.java` no longer statically imports `FarmServer`, but javac may still implicit-compile it. The `**/smartfarm/server/**/*.java` exclude in the android profile should now be structurally effective after B1's rewrite, but this hasn't been confirmed by inspecting the Android `target/classes` output.

---

## Phase 2 TODOs (14 in-code markers)

| Category | Count | Description | Tracked by |
|----------|-------|-------------|------------|
| DAO cached-connection field → per-method call | 11 | One per DAO: `AdminDAO`, `AlertDAO`, `AttendanceDAO`, `CropDAO`, `DeviceDAO`, `HarvestDAO`, `ManagerDAO`, `PlotDAO`, `SensorDAO`, `TaskDAO`, `WorkerDAO` | Hagag lane — not in 3bdelbary P2.x |
| `NavContext` unit tests | 1 | Awaiting JUnit/Surefire in pom.xml | Hagag lane (test framework) |
| `MonitoringController.setupTrendChart` bounded series | 1 | Cap each `XYChart.Series` at N data points, trim from head before append | **P2.9** (scheduled) |
| `alerts.fxml` master-detail pattern | 1 | Phone users get full-width detail screen via stacked View | **P2.6** (scheduled) |

Note: the **44 `AsyncCalls.runAsync` / `runWithBusy` call sites added in P2.1–P2.3c are not `TODO(phase-2)` markers** — they're completed refactors. The 14 markers above are the remaining qualitative items the original Phase 1 deliverables flagged for future work.

Additional Phase 2 items from the migration docs (not in-code):
- Wire `DBConnection.closeQuietly()` into `Main#stop()` and a Gluon `LifecycleEvent.DESTROY` listener
- Run GraalVM agent on desktop session to expand reflect-config (especially mysql-connector-j)
- Add "Settings" admin screen for first-run DB credentials
- Flip `usesCleartextTraffic="false"` for release builds
- Relocate `SECRET_KEY` out of source code
- Android USB-OTG fingerprint backend implementation
- Verify `adb logcat` Logger output on real device
- APK content audit (`server.*` classes absent)

---

## Commit History (3bdelbary, recent)

```
425828a [3bdelbary] B3X.8 doc: B3X UX-rework section in MIGRATION_3BDELBARY.md
50b9767 [3bdelbary] B3X.5-7 Gluon NavigationDrawer + AppBar replace dashboard sidebar
d3164b2 [3bdelbary] B3X.4 filter rows -> FlowPane + alerts root restructure
724707b [3bdelbary] B3X.3 summary card rows -> FlowPane (responsive 1/2/4-column wrap)
0cea801 [3bdelbary] B3X.1+B3X.2 signin/signup: vertical stack for mobile
f4cc67d [3bdelbary] B3.18 doc: B3 done section in MIGRATION_3BDELBARY.md
0932ebf [3bdelbary] B3.16 LogsController: adapt to CharmListView + severity-coloured cell
71c1b82 [3bdelbary] B3.15 logs.fxml: TableView -> CharmListView + drop 1200x900
67634a3 [3bdelbary] B3.14 HarvestController: adapt to CharmListView + defensive DB guard
f26a33f [3bdelbary] B3.13 harvest.fxml: TableView -> CharmListView + drop 1200x900
3bdf5fd [3bdelbary] B3.12 TaskController: adapt to CharmListView cellFactory
2e88cd1 [3bdelbary] B3.11 tasks.fxml: TableView -> CharmListView + drop 1200x900
33c0539 [3bdelbary] B3.10 WorkerController: adapt to CharmListView cellFactory
5da4b75 [3bdelbary] B3.9 workers.fxml: TableView -> CharmListView + drop 1200x900
a467a53 [3bdelbary] B3.6-B3.8 alerts/monitoring/reports.fxml: drop hardcoded root sizes
aeb2ce7 [3bdelbary] B3.5 plots.fxml: drop 1200x900 root + remove 2 hover-only tooltips
cfb2808 [3bdelbary] B3.4 crops.fxml: drop 1200x900 root prefSize
e16fe69 [3bdelbary] B3.3 dashboard.fxml: drop 1440x900 root prefSize
05b5795 [3bdelbary] B3.2 signup.fxml: drop hardcoded root + auth-left widths for mobile
9a53bc6 [3bdelbary] B3.1 signin.fxml: drop hardcoded root + auth-left widths for mobile
f083ad3 [3bdelbary] B2.9 doc: real-desktop smoke results + HOME_VIEW bugfix writeup
45e4b47 [3bdelbary] B2.8 add lifecycle logging to the 4 mobile Views
a0c06d5 [3bdelbary] B2.7 fix: SPLASH registers under HOME_VIEW so Glisten mounts it at startup
80de960 [3bdelbary] B2.1 ShellView wraps dashboard.fxml + injects user from NavContext
```

---

## Recommended Next Steps

1. **Push `mobile-app` branch to GitHub** so the Actions workflow becomes available.
2. **Add GitHub Secrets** for DB / MQTT / Crop.health (see the secrets table in `docs/CI_ANDROID_BUILD.md`). Without these the cloud build fails fast in step 5 with a clear error.
3. **Apply the SQL migration** to fix `Crop.GrowthStage='GROWING'` on your MySQL so the app's `dashboard` + `reports` FXMLs load:
   ```
   mysql -h <host> -u <user> -p <db> < docs/sql/2026-05-14-fix-growthstage-growing.sql
   ```
   Idempotent. The script reports row counts before and after.
4. **Trigger the cloud build** — Actions tab → "Android APK Build" → Run workflow → pick `mobile-app` → Run. First run ≈20–40 min.
5. **Download the `agrilliant-apk` artifact** from the completed run, then `adb install` on a device with USB debugging on.
6. **Continue Phase 2 UX polish (P2.6 → P2.11)** — the async sweep (P2.1–P2.3c), auth-flow timeouts (P2.4), and responsive sidebar (P2.5) are landed. The remaining items are layout / asset / chart wiring polish; see the *Phase 2 — In Progress* matrix above and `docs/MIGRATION_3BDELBARY.md` for per-task specifics.
