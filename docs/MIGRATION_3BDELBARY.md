# 3bdelbary — Phase 1 Migration Notes

> Track: UI / Navigation / Resources (§8.B of `ANDROID_MIGRATION.md`)
> Branch: `mobile-app` (Hagag's H-tasks + 3bdelbary's B-tasks both
> land here per the team's "no main" rule; the original
> `android/3bdelbary` source branch has been merged in).

## B1: Convert Main.java to Gluon MobileApplication — DONE ✅

**Spec:** `docs/superpowers/specs/2026-05-14-b1-gluon-mobileapplication-design.md`
**Plan:** `docs/superpowers/plans/2026-05-14-b1-gluon-mobileapplication.md`

### Files created
- `src/main/java/smartfarm/ui/nav/AppView.java` — 4-value enum for AppManager Views (`SPLASH`, `SIGNIN`, `SIGNUP`, `SHELL`).
- `src/main/java/smartfarm/ui/nav/ShellContent.java` — 14-value enum for inner content slots (consumed in B2).
- `src/main/java/smartfarm/ui/nav/NavContext.java` — singleton session state (`currentUser`, expandable).
- `src/main/java/smartfarm/ui/views/SplashView.java` — logo + spinner + async session restore router with 800 ms min-visible guard.
- `src/main/java/smartfarm/ui/views/SignInView.java` — wraps `signin.fxml`.
- `src/main/java/smartfarm/ui/views/SignUpView.java` — wraps `signup.fxml`.
- `src/main/java/smartfarm/ui/views/ShellView.java` — Welcome stub (B2 replaces with `NavigationDrawer`).

### Files modified
- `src/main/java/smartfarm/Main.java` — now `extends MobileApplication`, registers 4 lazy view factories in `init()`, applies stylesheets + desktop window defaults in `postInit(Scene)`, and starts `FarmServer` reflectively behind a `!Constants.IS_ANDROID` runtime gate.

### Commits (in order)
```
520e1a7 [3bdelbary] B1.1 add NavContext singleton for cross-View session state
6a7d570 [3bdelbary] B1.2 add AppView enum (4 top-level Gluon Views)
a87004a [3bdelbary] B1.3 add ShellContent enum (14 inner content slots for B2)
30d8ccc [3bdelbary] B1.4 add SignInView (wraps existing signin.fxml)
839110a [3bdelbary] B1.5 add SignUpView (wraps existing signup.fxml)
bfac2f4 [3bdelbary] B1.6 add ShellView stub (B2 will replace with NavigationDrawer)
323b697 [3bdelbary] B1.7 add SplashView UI layout (logo + spinner, no logic yet)
3e84a78 [3bdelbary] B1.8 wire SplashView session restore with 800ms min-time guard
ccb355a [3bdelbary] B1.9 convert Main to Gluon MobileApplication with 4 view factories
b5417fd [3bdelbary] B1.10 add Main.postInit stylesheets + desktop window defaults
e791591 [3bdelbary] B1.11 start FarmServer reflectively, gated on !Constants.IS_ANDROID
```

### Cross-track interactions (resolved by Hagag's foundation)
- **Glisten + Attach Pictures**: added to `pom.xml` in Hagag's commit `a911016 [hagag] Phase 2 prep`. B1 code compiles cleanly against `MobileApplication`, `AppManager`, `View`, `PicturesService`, etc.
- **`Constants.IS_ANDROID`**: already present from Hagag's H9. The B1 plan originally said to use `com.gluonhq.attach.util.Platform.isDesktop()` and flagged a `TODO(phase-2)` to switch — H9 has already landed, so we used the cleaner `!Constants.IS_ANDROID` directly from B1.10/B1.11. Plan's TODO is now closed.
- **`Logger`**: Hagag's H5/H10 Logger is used inside `Main.startFarmServerReflectively` (the plan said `System.err.println`; we use `Logger.e`/`Logger.i` for consistency with H10's `adb logcat` routing).
- **`SessionManager.loadSession()` / `AuthService.restoreSession(String)`**: unchanged public surface from Hagag's H6 — `SplashView` calls both off the FX thread on a one-shot `splash-session-restore` daemon thread.

### Path notes
- The migration doc §6 lists `src/main/resources/views/**` as a path for "Gluon-style View classes". Java classes belong in `src/main/java/`, so I placed them in `src/main/java/smartfarm/ui/views/` (covered by `src/main/java/smartfarm/ui/**` in §6). No `src/main/resources/views/` directory was created.

### Known degradations / Phase 2 TODOs
- `AuthService.restoreSession()` is **synchronous** and could hang the splash if MySQL is slow. The 800 ms min-visible guard masks the latency from the user's perspective only up to a point; a real timeout/cancel path lands in Phase 2. (Hagag's `DBConnection.runAsync` exists but is not wired here yet — controllers would need to be rewritten end-to-end to use it.)
- `NavContext` has `TODO(phase-2): add unit tests once Hagag adds JUnit/Surefire to pom.xml`. The pom still has no test framework.
- `SplashView.restoreStarted` guard is a `volatile` boolean, set on the FX thread (in `setOnShowing`) and read on the same thread, so memory visibility is trivially correct. Adding `compareAndSet` semantics is overkill — left as a Phase 2 polish if anyone genuinely re-enters the view.

### Verification (B1 done state)
| Gate | Result |
|------|--------|
| `mvn -Pdesktop clean compile` | BUILD SUCCESS — 79 sources |
| `mvn -Pandroid clean compile` | BUILD SUCCESS — 75 sources |
| Lane diff `git diff --name-only a911016..HEAD` | 8 files, all under `smartfarm/Main.java` + `smartfarm/ui/{nav,views}/**` |
| Interactive run (splash → signin) | Deferred — needs a graphical session, not runnable from this CLI environment |
| Interactive run (splash → shell w/ saved session) | Deferred — same reason |

The two interactive runtime checks (plan §13, §14) need to be done by you on a desktop with a display. Recipe in the plan.

---

## B2: Replace Stage navigation with AppManager switch — DONE ✅

### Approach
B2 in this codebase splits cleanly into two kinds of navigation:

- **AppManager-level (top-of-stack swaps)** — Sign-In ↔ Sign-Up ↔ Shell ↔ logout. Three controllers had `stage.getScene().setRoot(loader.load(...))` patterns. All four nav paths now go through `AppView.<name>.switchTo()` (B1.2).
- **Inner content swap inside the dashboard** — `DashboardController.loadFxmlPage(fxmlPath, navBtn)` swaps the inner `pageContainer.getChildren()`. This was never Stage-level — it just replaces a Node inside the existing scene. Left as-is for B2; B3/B4 may revisit when porting to mobile.

### Files modified
- `src/main/java/smartfarm/ui/views/ShellView.java` — was a stub Welcome label; now loads `dashboard.fxml`, caches the `DashboardController`, hides the Gluon AppBar while shell is on screen, and pushes the current user from `NavContext` on every `setOnShowing`.
- `src/main/java/smartfarm/ui/SignInController.java` — `navigateToDashboard` is now `NavContext.get().setCurrentUser(user); AppView.SHELL.switchTo();`. `onGoToSignUp` is now `AppView.SIGNUP.switchTo()`. Dropped `javafx.stage.Stage`, `javafx.fxml.FXMLLoader`, `javafx.scene.Parent`, `javafx.scene.Scene` imports. `findWorkerByFingerprint` catch now uses `Logger.e(TAG, ..., e)` for consistency with the H5 sweep style.
- `src/main/java/smartfarm/ui/SignUpController.java` — `onGoToSignIn` is now `AppView.SIGNIN.switchTo()`. Dropped same imports.
- `src/main/java/smartfarm/ui/DashboardController.java` — `onLogout` is now `SessionManager.clearSession(); NavContext.get().clear(); AppView.SIGNIN.switchTo();`. Dropped `javafx.stage.Stage`, `javafx.scene.Parent` imports. (FXMLLoader stays for `loadFxmlPage` inner swap.)

### Commits
```
80de960 [3bdelbary] B2.1 ShellView wraps dashboard.fxml + injects user from NavContext
25e3404 [3bdelbary] B2.2 SignInController nav via AppView + NavContext (drop Stage)
cacccb0 [3bdelbary] B2.3 SignUpController nav via AppView (drop Stage)
aa320f5 [3bdelbary] B2.4 DashboardController logout via NavContext + AppView (drop Stage)
```

### Verification (B2 done state)
| Gate | Result |
|------|--------|
| `grep -rn "import javafx.stage.Stage" src/main/java/smartfarm/ui` | **0 matches** |
| `grep -rnE "setScene\\(|stage\\.getScene\\(\\)\\.setRoot|primaryStage" src/main/java/smartfarm/ui` | **0 matches** |
| `mvn -Pdesktop clean compile` | BUILD SUCCESS — 79 sources |
| `mvn -Pandroid clean compile` | BUILD SUCCESS — 75 sources |
| Lane diff vs B1 final (`00e972b..HEAD`) | 4 files, all in `ui/` (3bdelbary's lane) |

### Cross-track notes
- `FileChooser` is still imported in `DashboardController`, `LogsController`, `ReportsController`, `CropController`, `DiseaseDetectionPage` — that's B4's job to replace with `CSVExporter.saveCsv(...)` (already exists from Hagag's H8) and `PlatformPickers.pickImage()` (delivered in B8).
- `DashboardController.loadFxmlPage` (inner page swap) still uses `FXMLLoader.load(...)` per-click. That's fine for now — the loaded FXMLs have their own controllers wired via `fx:controller`. B3 may collapse this into a `View`-per-page model if the team decides each inner page should also be a Gluon View (it doesn't need to be; the inner pages are content, not full-screen states).

### Phase 2 TODOs added in this batch
- None. Existing TODOs (NavContext unit tests, AuthService async wrap) still stand; no new ones introduced.

---

## Pre-B3 smoke results

Three smoke checks ran on 2026-05-14 against the post-B2 tree (`mobile-app` HEAD `28d4965` at the time; ran again at `62cece4` after the `display`+`statusbar` Attach addendum). All checks ran on Linux headless using `org.testfx:openjfx-monocle:jdk-12.0.1+2` for Monocle headless rendering, since Xvfb has a missing-lib issue on this host (`libnettle.so.9`).

### Smoke 1 — resource presence
Verified every resource the new code references actually exists on the classpath:

| Resource | Status |
|----------|--------|
| `/images/logo-dark.png` (Splash, Main window icon) | ✅ present |
| `/images/logo.png` | ✅ present |
| `/css/farm-theme.css` (Main.postInit) | ✅ present |
| All 12 FXML files (`signin`, `signup`, `dashboard`, + the 9 inner pages DashboardController loads) | ✅ all present |

### Smoke 2 — FXML load via `FXMLLoader.load` (headless JFX)
`target/fxml-smoke/FxmlLoadSmoke.java` invokes `FXMLLoader.load(...)` for every FXML on the FX thread.

**Result: 7/12 load clean, 5/12 fail with NPE in their controllers' `initialize()`.**

| FXML | Status | Reason |
|------|--------|--------|
| signin.fxml | ✅ loads | pure UI |
| signup.fxml | ✅ loads | pure UI |
| alerts.fxml | ✅ loads | controller defers DB |
| logs.fxml | ✅ loads | controller defers DB |
| plots.fxml | ✅ loads | `PlotController.initialize` catches the NPE and logs |
| tasks.fxml | ✅ loads | controller defers DB |
| workers.fxml | ✅ loads | controller defers DB |
| dashboard.fxml | ❌ no-DB NPE | `DashboardController.initialize` eagerly hits DB |
| crops.fxml | ❌ no-DB NPE | same |
| harvest.fxml | ❌ no-DB NPE | same |
| monitoring.fxml | ❌ no-DB NPE | same |
| reports.fxml | ❌ no-DB NPE | same |

**The 5 failures are pre-existing fragility, not a B1/B2 regression.** Stack traces all read the same shape:
```
NullPointerException: Cannot invoke "java.sql.Connection.createStatement()" because "this.conn" is null
    at smartfarm.dao.PlotDAO.getAll(PlotDAO.java:77)
    at smartfarm.ui.PlotController.loadPlotData(PlotController.java:510)
    at smartfarm.ui.PlotController.setupTable(PlotController.java:501)
    at smartfarm.ui.PlotController.initialize(PlotController.java:75)
    at FXMLLoader.loadImpl ...
```

The DAOs cache a `Connection` at construction (Hagag's H5 doc'd this with the `TODO(phase-2): replace cached field with a per-call getInstance()` comment); without a real MySQL the cached connection is `null` and the eager init explodes. The same crash would have happened pre-migration on any sign-in flow that tried to load `dashboard.fxml` without DB access — `SignInController.navigateToDashboard` doing `FXMLLoader.load(dashboard.fxml)` would have hit the same NPE.

**Implications for the B1/B2 nav graph:**
- ✅ Splash → SIGNIN path works end-to-end without a DB (both `signin.fxml` + `signup.fxml` load clean)
- ✅ SIGNIN ↔ SIGNUP nav (`AppView.SIGNUP/SIGNIN.switchTo()`) works
- ❌ SIGNIN → SHELL (post-login) requires a DB because `ShellView` constructor loads `dashboard.fxml`. Same constraint as pre-migration desktop builds — sign-in always required a DB anyway.

### Smoke 3 — `MobileApplication` boot
`target/fxml-smoke/BootSmoke.java` calls `smartfarm.Main.main(...)` under Monocle headless. **Boot got through `Main.init()` + `Main.postInit()` cleanly** — confirmed by:

```
I/FarmServer: Farm Server started on port 8080
```

That line is `Logger.i(TAG, "Farm Server started on port " + PORT)` from `FarmServer.start`, reached via `Main.startFarmServerReflectively` (B1.11). So:
- ✅ Gluon `MobileApplication.launch` reaches our `Main.init`
- ✅ All 4 view factories register
- ✅ Glisten's `AppManager.start` → `AppBar` construction works (verifies the **`display`+`statusbar` Attach addendum** in `[hagag] 62cece4` is correct — the original `Phase 2 prep` commit `a911016` would have crashed here on `NoClassDefFoundError: com/gluonhq/attach/display/DisplayService`)
- ✅ `Main.postInit()` runs: styles applied, FarmServer thread started

The smoke does crash later at the actual window-show step with `AbstractMethodError: MonocleWindow does not define _updateViewSize(long)`. That's the testfx-monocle artifact (built for JDK 12.0.1, 2019) being too old for JavaFX 21.0.2's evolved Window API. **Test-infrastructure limitation, not a code bug.**

Two non-fatal Glisten log lines also appear during boot — `LicenseManager` / `TrackingManager` log `SEVERE: Private storage file not available`. Glisten tries to read a `gluonmobile.license` file via StorageService; the bare-desktop StorageService impl jar isn't on the classpath (only the API), so the read fails and Glisten falls back silently. App continues. Will not appear on `mvn -Pdesktop gluonfx:run` or on Android since GluonFX wires the platform-specific Storage impl via `<attachList>`.

### What's verified, what's deferred

**Verified through smoke:**
- B1 + B2 code paths reach all the way through `Main.init` → `Main.postInit` → Glisten AppBar construction → FarmServer thread launch.
- All resources the new code references exist.
- 7 of 12 FXMLs load clean — including the two on the critical no-DB path (signin/signup).
- The 5 FXML failures reproduce a known pre-existing fragility that the migration plan has slated for Phase 2 (async DAO sweep + per-method `getInstance()` refactor).

**Deferred to a real-desktop smoke:**
- Visual confirmation of splash → signin rendering.
- Visual confirmation of `Main.applyDesktopWindowDefaults` (window title, icon, min size, maximize).
- Post-login visual: sign in with a real DB → ShellView loads → dashboard renders inside Gluon's center pane.
- Inner page navigation (`DashboardController.loadFxmlPage`).

**Recipe for the real-desktop smoke** (on a machine with a display + a reachable MySQL):
```bash
# 1. Configure DB creds (one of):
#    export DB_URL=... DB_USER=... DB_PASSWORD=...
#    OR drop a populated db.properties in src/main/resources/
# 2. Run via GluonFX (NOT javafx:run — that doesn't know Gluon's init/postInit
#    or wire the Storage impl jar):
mvn -Pdesktop gluonfx:run
```

### Real-desktop smoke results (2026-05-14, `DISPLAY=:0` on Linux/X11)

Re-ran `BootSmokeReal` (target/fxml-smoke/BootSmokeReal.java) against the real
X server. **Boot sequence runs end-to-end cleanly.** Logger.d lifecycle hooks
added to all 4 Views captured the full expected trace:

```
D/SplashView: showing
I/FarmServer: Farm Server started on port 8080
D/SplashView: no session → SIGNIN
W/DBConnection: No DB credentials on env/Settings/properties — DB features disabled until configured.
D/SplashView: hiding
D/SignInView: showing
[8003ms] [BootSmokeReal] 8s budget elapsed — forcing JVM exit.
Exit code: 0
```

Confirms:
- Splash view mounts at startup (HOME_VIEW factory hit) — visual splash with logo + spinner
- `Main.postInit` runs: FarmServer TCP listener thread spawns
- `SplashView.kickOffSessionRestore` runs on its daemon thread, finds no session, sleeps 800ms min-time, then `Platform.runLater → AppView.SIGNIN.switchTo()`
- Splash hides, SignIn shows — transition completes without an exception
- DBConnection lazy init also works — no DB creds → "DB features disabled" log line, no crash

**Headless-monocle bonus check:** even under headless Monocle (no display)
the splash now mounts (`D/SplashView: showing`) — but Glisten's NagScreenPresenter
(triggered by the missing license file) calls `enterNestedEventLoop`, which
Monocle's strict event-thread check rejects. That's a pure Glisten+headless+
missing-license issue, not a code bug. On a real display the nag dialog can
fire cleanly without crashing.

### Bug found and fixed during the real-desktop smoke

The first real-desktop smoke run produced no `D/SplashView: showing` line.
Investigation via `javap` of Glisten 6.2.3's `AppManager.class` showed
`continueInit()` literally calls `switchView("home")` to mount the startup
View. The `AppView.SPLASH` factory was registered under `.name() = "SPLASH"`,
which did not match — so no view was ever mounted, and the JFX thread sat
idle for the entire 5-second smoke budget.

**Fix landed as commit `a0c06d5 [3bdelbary] B2.7`:** `AppView` constants now
carry an explicit `registeredName`. `SPLASH` maps to
`MobileApplication.HOME_VIEW` (`"home"`); the others use their uppercase
enum names. `Main.init()` switched to `AppView.X.registeredName()`. This
is the contract Glisten requires.

**Lifecycle logging added as commit `45e4b47 [3bdelbary] B2.8`:**
`Logger.d` calls on `setOnShowing` / `setOnHiding` for all 4 Views, plus
the `SHELL` vs `SIGNIN` branch decision inside `SplashView`. Routes through
the H10 facade → `adb logcat` on Android (verbose level, filtered out in
release builds), `System.out` on desktop. Makes Phase 2 navigation issues
observable without a debugger.

---

## B3: Make every FXML mobile-friendly — DONE ✅

### Approach
The §8.B3 spec asked for 4 things per FXML:
1. drop fixed pixel widths/heights
2. wrap non-trivial layouts in a `ScrollPane`
3. enforce 48dp touch targets
4. replace `TableView` with `CharmListView` where the screen is a list

After surveying all 12 FXMLs (commit `33c0539` notes), the actual scope shook out as:

| Need | Files affected | Why |
|------|----------------|-----|
| Drop oversized root `prefWidth`/`prefHeight` | 11 files (all except `monitoring`, which already had no root size set… or so the survey said — turned out `monitoring.fxml` *did* have `1200×900`, see B3.7) | Forced desktop window sizes don't fit on phones. |
| `ScrollPane` wrap | 0 files | Dashboard's existing `<ScrollPane fitToWidth="true">` (line 153 of dashboard.fxml) already wraps `pageContainer`, so all 9 inner pages inherit vertical scrolling. The 2 auth views are short forms that fit a phone height. |
| Touch target ≥48dp | 0 files | The survey found no fixed-size buttons anywhere — the existing styles already use `maxWidth="Infinity"` or styleClass-based sizing. The CSS-level enforcement lands in B5 `mobile.css`. |
| Tooltip / ContextMenu replacement | 1 file (`plots.fxml`) | Only 2 hover tooltips on map controls. Removed (touch has no hover); a long-press handler is a future polish. |
| `MenuBar` / `MenuItem` → `NavigationDrawer` | 0 files | Survey found no `MenuBar` anywhere — only one `MenuButton` in dashboard, which is mobile-fine (it's a dropdown button, not a top-of-window menu bar). |
| `TableView` → `CharmListView` | 4 list-shaped files: `workers`, `tasks`, `harvest`, `logs` | Each row collapses 5–8 columns into a Glisten `ListTile`. The other 4 tables (`dashboard`, `crops`, `plots`, `alerts`, `monitoring`, `reports`) stay `TableView` — they're either dashboard-shaped (mixed widgets, not list-shaped) or comparison-shaped (columns truly matter). |

### Files modified
- 12 FXMLs: all under `src/main/resources/fxml/` (3bdelbary lane)
- 4 controllers: `WorkerController`, `TaskController`, `HarvestController`, `LogsController`

### CharmListView pattern (for the 4 list-shaped views)
Each rewrite follows the same shape: replace the `<TableView>` + N `<TableColumn>` block with a single `<CharmListView fx:id="...List" VBox.vgrow="ALWAYS"/>`, drop the matching `@FXML TableColumn` fields from the controller, add a `setupCellFactory()` method that installs an inner `XxxListCell extends CharmListCell<T>`. Each cell builds a Glisten `ListTile`:

| Slot                      | Worker                  | Task                       | Harvest                  | SystemLog                       |
|---------------------------|-------------------------|----------------------------|--------------------------|---------------------------------|
| `setPrimaryGraphic` (left)| green avatar with `fth-user` | status-coloured badge with `fth-check-square` (green/blue/amber by `Status.DONE`/`IN_PROGRESS`/`PENDING`) | green badge with `fth-package` | severity-coloured badge with `fth-info`/`fth-alert-triangle`/`fth-x-circle` |
| `setTextLine(0)`          | full name               | description                | crop + plot              | message                         |
| `setTextLine(1)`          | role + status           | assignee + plot            | quantity + date          | timestamp + user                |
| `setTextLine(2)`          | workload + phone + FP id| due + priority + status    | grade + revenue          | source + level                  |
| `setSecondaryGraphic` (right) | edit + delete (`icon-btn`) | advance + revert + delete (advance disabled at `DONE`, revert disabled at `PENDING`) | edit + delete | none (logs read-only) |

The cells reuse the existing `icon-btn` style class from `farm-theme.css`, so they match the rest of the app visually. Cell layouts are built once in the constructor and only the per-row data is set in `updateItem` — no allocations on scroll.

### Defensive DB guard (B3.14)
`HarvestController.loadCropCache()` and `loadRecords()` previously caught only `SQLException`. The H5-pending DAOs (`CropDAO`, `HarvestDAO`) eagerly construct their `Connection` field via `DBConnection.getInstance()`, which returns `null` when no DB creds are configured — so the first `prepareStatement` call NPEs (unchecked, bypasses `catch SQLException`). B3.14 widens the catches to `catch (SQLException | RuntimeException)`, matching the pattern `WorkerController.loadWorkers()` already uses. The TODO is to drop the `RuntimeException` once `CropDAO`/`HarvestDAO` get Hagag's H5 null-guard sweep.

### Commits
```
9a53bc6 [3bdelbary] B3.1 signin.fxml: drop hardcoded root + auth-left widths for mobile
05b5795 [3bdelbary] B3.2 signup.fxml: drop hardcoded root + auth-left widths for mobile
e16fe69 [3bdelbary] B3.3 dashboard.fxml: drop 1440x900 root prefSize
cfb2808 [3bdelbary] B3.4 crops.fxml: drop 1200x900 root prefSize
aeb2ce7 [3bdelbary] B3.5 plots.fxml: drop 1200x900 root + remove 2 hover-only tooltips
a467a53 [3bdelbary] B3.6-B3.8 alerts/monitoring/reports.fxml: drop hardcoded root sizes
5da4b75 [3bdelbary] B3.9 workers.fxml: TableView -> CharmListView + drop 1200x900
33c0539 [3bdelbary] B3.10 WorkerController: adapt to CharmListView cellFactory
2e88cd1 [3bdelbary] B3.11 tasks.fxml: TableView -> CharmListView + drop 1200x900
3bdf5fd [3bdelbary] B3.12 TaskController: adapt to CharmListView cellFactory
f26a33f [3bdelbary] B3.13 harvest.fxml: TableView -> CharmListView + drop 1200x900
67634a3 [3bdelbary] B3.14 HarvestController: adapt to CharmListView + defensive DB guard
71c1b82 [3bdelbary] B3.15 logs.fxml: TableView -> CharmListView + drop 1200x900
0932ebf [3bdelbary] B3.16 LogsController: adapt to CharmListView + severity-coloured cell
```

### Verification (B3 done state)
| Gate | Result |
|------|--------|
| `mvn -Pdesktop clean compile` | BUILD SUCCESS |
| `mvn -Pandroid clean compile` | BUILD SUCCESS |
| FXML load smoke (12 files, no DB) | **8/12 PASS** (signin, signup, plots, workers, tasks, alerts, harvest, logs). 4 fail with the H5-pending DB NPE (dashboard, crops, monitoring, reports) — same shape as before, owned by Hagag's lane. **Net improvement: +1 vs the pre-B3 baseline (which had harvest also failing).** |
| Boot smoke (`-Pdesktop`) | Splash → SignIn end-to-end, FarmServer thread starts on port 8080 |
| Boot smoke (`-Pandroid`) | Splash → SignIn end-to-end, FarmServer reflectively skipped (correct: H9 excludes it from Android profile) |
| Lane diff (`f083ad3..HEAD` for B3 only) | 16 files: 12 in `src/main/resources/fxml/`, 4 in `src/main/java/smartfarm/ui/` (3bdelbary's lane). Zero touches to `dao/`, `service/`, `server/`, `util/`, `pom.xml`. |

### Pre-existing inconsistency surfaced (not a regression)
`crops.fxml` fails the smoke with `NullPointerException: ...this.colVariety is null`. This is not B3 fallout — `CropController` declares `@FXML private TableColumn<Crop, String> colVariety;` and calls `colVariety.setResizable(false)` in `initialize()`, but `crops.fxml` has never had a `<TableColumn fx:id="colVariety">` element (`git log --all -- src/main/java/smartfarm/ui/CropController.java` shows the field reference predates the migration commits). With a real DB the controller would NPE before reaching the no-DB branch; without one it just NPEs earlier. Either way it's a Phase 2 follow-up: either remove `colVariety` from the controller or add it to the FXML.

---

## Status of B4–B10
- [x] **B3** — done. See section above.
- [ ] **B4** — Sweep controllers: replace `FileChooser` (CSV export in Dashboard/Logs/Reports/Crop) with `CSVExporter.saveCsv(...)`. Replace `FileChooser.showOpenDialog` (DiseaseDetectionPage) with `PlatformPickers.pickImage()` (delivered in B8).
- [ ] **B5** — Add `css/mobile.css`. Keep AtlantaFX PrimerLight as base.
- [ ] **B6** — Drop any remaining JFreeChart use (already absent from `src/main/java`; double-check leftover imports in dashboard/monitoring).
- [ ] **B7** — Launcher icons mdpi → xxxhdpi under `src/android/res/mipmap-*/` to satisfy Hagag's manifest refs (`@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`).
- [ ] **B8** — Image picker via Gluon Attach `Pictures` (Hagag added the dep in `a911016`). Create `smartfarm.ui.platform.PlatformPickers` with `pickImage()` returning `File`.
- [ ] **B9** — Lifecycle hooks: `View#setOnShown`/`setOnHidden` instead of "set up on stage shown" patterns. (`SplashView` and `ShellView` already follow this; sweep the rest.)
- [ ] **B10** — Keep this file current as we go.
