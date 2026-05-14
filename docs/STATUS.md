# Agrilliant Android Migration — Current Status

> Branch: `mobile-app` | Last updated: 2026-05-14
> Tracks: **Hagag** (Build / Data / IoT — §8.A) + **3bdelbary** (UI / Navigation / Resources — §8.B)

---

## Summary

The Agrilliant desktop JavaFX app is being migrated to Android via Gluon Mobile (Glisten + Attach + GluonFX native-image). Both Phase 1 tracks have made substantial progress:

- **Hagag's track (H1–H11):** Complete. All build, data, and infrastructure tasks are done.
- **3bdelbary's track (B1–B3X):** Complete through B3X. B4–B10 remain.

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

### 3bdelbary Track (UI / Navigation / Resources) — B1 through B3X DONE

| Task | Description | Status |
|------|-------------|--------|
| B1 | Convert `Main.java` to Gluon `MobileApplication` with 4 view factories (SPLASH, SIGNIN, SIGNUP, SHELL) | Done |
| B2 | Replace `Stage` navigation with `AppManager.switchView()` across SignIn/SignUp/Dashboard/Shell | Done |
| B3 | Make every FXML mobile-friendly: drop hardcoded sizes, TableView → CharmListView, remove hover-only tooltips | Done |
| B3X | UX rework: vertical auth forms, FlowPane summary/filters, NavigationDrawer + AppBar, alerts stacked layout | Done |

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

### Build Verification

| Gate | Result |
|------|--------|
| `mvn -Pdesktop clean compile` | BUILD SUCCESS (79 sources) |
| `mvn -Pandroid clean compile` | BUILD SUCCESS (75 sources) |
| FXML load smoke (with DB) | **9/12 pass** (retested 2026-05-14 with real MySQL at 139.59.153.80) |
| Boot smoke (desktop) | Splash → SignIn end-to-end, FarmServer starts |
| Boot smoke (android profile) | Splash → SignIn end-to-end, FarmServer skipped (correct) |

---

## What Is Missing (B4–B10)

These are 3bdelbary's remaining Phase 1 tasks, none started yet:

| Task | Description | Scope | Impact |
|------|-------------|-------|--------|
| **B4** | Replace `FileChooser` with `CSVExporter.saveCsv()` + `PlatformPickers.pickImage()` | 5 controllers: `DashboardController` (4 CSV exports), `LogsController`, `ReportsController`, `CropController`, `DiseaseDetectionPage` (image picker) | **High** — `FileChooser` uses `javafx.stage.Stage` which doesn't exist on Android; these features crash on mobile |
| **B5** | Add `css/mobile.css` with touch-target enforcement (≥48dp), width-based sidebar toggle, phone-specific overrides | New CSS file + `Main.postInit` stylesheet registration | **Medium** — app works without it but touch targets may be small; sidebar won't auto-show on desktop-width screens |
| **B6** | Drop any remaining JFreeChart use | Dashboard/monitoring controllers — verify no leftover imports | **Low** — JFreeChart already excluded from Android profile; just cleanup |
| **B7** | Android launcher icons (mdpi → xxxhdpi) | `src/android/res/mipmap-*/ic_launcher*.png` | **High** — manifest references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`; APK build fails without them |
| **B8** | Image picker via Gluon Attach `Pictures` | New `smartfarm.ui.platform.PlatformPickers` class with `pickImage()` returning `File` | **Medium** — DiseaseDetectionPage needs this for Android; B4 wires it in |
| **B9** | Lifecycle hooks sweep | Replace any remaining "set up on stage shown" patterns with `View#setOnShown`/`setOnHidden` | **Low** — SplashView and ShellView already follow this; other views likely fine |
| **B10** | Keep migration docs current | Ongoing bookkeeping | **Low** — documentation maintenance |

### FileChooser Usage (B4 must replace these)

| Controller | Lines | Type |
|------------|-------|------|
| `DashboardController` | 4 instances (lines 536, 584, 608, 632) | CSV export |
| `LogsController` | 1 instance (line 139) | CSV export |
| `ReportsController` | 1 instance (line 142) | CSV export |
| `CropController` | 1 instance (line 768) | CSV export |
| `DiseaseDetectionPage` | 1 instance (line 259) | Image open |

---

## Known Errors and Issues

### 1. FXML Load Failures (3/12) — Retested 2026-05-14 with real DB

Retested with `db.properties` pointing to the real MySQL at `139.59.153.80`. DB connection now succeeds, which changed the failure profile: `monitoring.fxml` now passes (was failing with DB NPE before). Three FXMLs still fail, but with different root causes than the previous "no DB" NPEs.

**Full smoke results:**

| FXML | Status | Details |
|------|--------|---------|
| `signin.fxml` | PASS | |
| `signup.fxml` | PASS | |
| `dashboard.fxml` | **FAIL** | `IllegalArgumentException: No enum constant smartfarm.model.Crop.GrowthStage.GROWING` — DB has `growth_stage='GROWING'` but the Java enum only defines `SEED, SEEDLING, VEGETATIVE, FLOWERING, FRUITING, HARVESTED` |
| `crops.fxml` | **FAIL** | `NullPointerException: Cannot invoke "javafx.scene.control.TableColumn.setResizable(boolean)" because "this.colVariety" is null` — `CropController` declares `@FXML colVariety` but `crops.fxml` has no `<TableColumn fx:id="colVariety">` |
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

**Fix options:** (a) Add `GROWING` to the enum (if the DB value is intentional), (b) UPDATE the DB rows to use a valid enum value like `VEGETATIVE`, or (c) Make `CropDAO.extractCrop()` handle unknown enum values gracefully (e.g. default to `VEGETATIVE`).

**Root cause #2 — `CropController.colVariety` FXML mismatch:**

`CropController` declares `@FXML private TableColumn<Crop, String> colVariety` and calls `colVariety.setResizable(false)` in `initialize()`, but `crops.fxml` has never had a `<TableColumn fx:id="colVariety">` element. The `@FXML` injection leaves the field `null`, and the subsequent method call NPEs.

**Fix:** Either add the column to `crops.fxml` or remove the `colVariety` field + its references from `CropController`.

**Previous (no-DB) smoke for comparison:**

Before the env files were added (no DB credentials), the smoke was 8/12 pass. The 4 failures were all `NullPointerException: Cannot invoke "...Connection.createStatement()" because "this.conn" is null` in `dashboard`, `crops`, `monitoring`, `reports`. With the real DB connected, `monitoring` now passes and the 3 remaining failures surface their real root causes (enum mismatch + FXML field mismatch) instead of the generic "no DB" NPE.

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

## Phase 2 TODOs (12 in-code markers)

| Category | Count | Description |
|----------|-------|-------------|
| DAO cached-connection field → per-method call | 11 | One per DAO: `AdminDAO`, `AlertDAO`, `AttendanceDAO`, `CropDAO`, `DeviceDAO`, `HarvestDAO`, `ManagerDAO`, `PlotDAO`, `SensorDAO`, `TaskDAO`, `WorkerDAO` |
| NavContext unit tests | 1 | Awaiting JUnit/Surefire in pom.xml |

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

1. **Fix `Crop.GrowthStage` enum mismatch** — Add `GROWING` to the enum or UPDATE DB rows (blocks dashboard + reports FXML loading)
2. **Fix `CropController.colVariety` NPE** — Add column to `crops.fxml` or remove field from controller (blocks crops FXML loading)
3. **B4** — Replace `FileChooser` with `CSVExporter.saveCsv()` (highest migration impact; these features crash on Android)
4. **B7** — Add launcher icons (required for APK build)
5. **B8** — Create `PlatformPickers` for image picking (needed by DiseaseDetectionPage)
6. **B5** — Add `mobile.css` for touch targets and responsive sidebar
7. Run first `mvn -Pandroid gluonfx:build` to produce an APK
