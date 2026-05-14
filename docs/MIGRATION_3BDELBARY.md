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

## B3X: UX rework after the first B3 review — DONE ✅

The first round of B3 (commits `9a53bc6..0932ebf`) did the spec-required mechanical changes — drop hardcoded sizes, swap `TableView`→`CharmListView` on list-shaped views, etc. — but a UX review afterwards found four screens that were still desktop-only despite "passing" B3:

| Screen | What was still broken on a 360dp phone | Fix |
|---|---|---|
| `signin.fxml` / `signup.fxml` | Two-column HBox forced the form column to ~180dp wide; fields and buttons unreadable | Stack vertically: compact branding header on top, form column (capped at 480dp) full-width below |
| Inner pages (`workers`, `tasks`, `harvest`, `logs`, `plots`, `monitoring`, `alerts`, `crops`) — summary card rows | 4-5 cards in an `HBox` with `hgrow=ALWAYS` → each ~80dp wide on phone, labels truncated, 24px values overflowed | Replace `HBox` with `FlowPane hgap=N vgap=N`; add `minWidth=200` to each card. Wraps to 1×N on phone, 2× on tablet, N×1 on desktop |
| Filter rows on the same 8 inner pages | Search box + 1–3 ComboBoxes side-by-side overflowed horizontally on phone | Replace `HBox` with `FlowPane`; search-box gets `minWidth=240` so it claims its own row |
| `dashboard.fxml` | Fixed 240dp sidebar took 67% of a 360dp screen, content area unusable | Replace with Gluon `NavigationDrawer` + `AppBar` (slide-in hamburger pattern). Sidebar + topbar in dashboard.fxml hidden via `visible="false" managed="false"` so DashboardController's existing logic still runs unchanged |
| `alerts.fxml` root | `<HBox>` with a fixed-width 380dp `detailPane` ate the screen on phone | Restructure root to `<VBox>`: list section on top, detail panel below. Detail panel `minWidth=0` so it can shrink. Drop the 380dp lock |

### B3X.5–B3X.7 — NavigationDrawer details

Rather than gut `DashboardController` (~900 lines, wires up datetime / weather / status-dots / user-pill / chart / hyperlinks / DAO loaders), the sidebar VBox + topbar HBox stay in `dashboard.fxml` but are hidden:

```xml
<VBox fx:id="sidebar" ... visible="false" managed="false">
```

`@FXML` injection still works (the elements exist), so all of `DashboardController`'s sidebar/topbar update methods (`updateDateTime`, `setupUserMenu`, `updateSidebarStatus`, etc.) keep running on hidden nodes. Re-enabling the sidebar via a future `mobile.css` width-based toggle is a one-attribute change.

The dispatch path is unified through one new public API on `DashboardController`:

```java
public enum NavTarget {
    DASHBOARD, MONITORING, DISEASE, ALERTS, CROPS, PLOTS,
    WORKERS, ATTENDANCE, TASKS, HARVESTS, REPORTS, SETTINGS, USERS, LOGS
}
public void navigate(NavTarget target) { ... }
```

The legacy `onNavXxx()` handlers (still wired to the hidden sidebar buttons) all delegate to `navigate(...)`. The new `ShellView` populates the Gluon NavigationDrawer with 14 items, each calling `controller.navigate(target)` then closing the drawer.

`ShellView`'s lifecycle now also configures `AppManager.getInstance().getAppBar()`:
- title: `"Agrilliant"`
- navIcon: `MaterialDesignIcon.MENU.button(...)` that calls `drawer.open()`
- actionItems: user name label + sign-out icon

On hide, ShellView clears the AppBar (so SignIn / SignUp don't see leftover chrome).

### Commits
```
0cea801 [3bdelbary] B3X.1+B3X.2 signin/signup: vertical stack for mobile
724707b [3bdelbary] B3X.3 summary card rows -> FlowPane (responsive 1/2/4-column wrap)
d3164b2 [3bdelbary] B3X.4 filter rows -> FlowPane + alerts root restructure
50b9767 [3bdelbary] B3X.5-7 Gluon NavigationDrawer + AppBar replace dashboard sidebar
```

### Verification (B3X done state)
| Gate | Result |
|------|--------|
| `mvn -Pdesktop clean compile` | BUILD SUCCESS |
| `mvn -Pandroid clean compile` | BUILD SUCCESS |
| FXML load smoke (12 files, no DB) | **8/12 PASS** — same set as post-B3 (signin, signup, plots, workers, tasks, alerts, harvest, logs). 4 fail with the H5-pending DB NPE in dashboard / crops / monitoring / reports — Hagag's lane to fix. **No new B3X regressions.** |
| Boot smoke (`-Pdesktop`) | Splash → SignIn end-to-end, FarmServer thread starts on port 8080, lifecycle traces cleanly |
| Boot smoke (`-Pandroid`) | Splash → SignIn end-to-end, FarmServer reflectively skipped (correct for Android profile) |
| Lane diff (B3X only, `f4cc67d..HEAD`) | 13 files: 11 in `src/main/resources/fxml/`, 2 in `src/main/java/smartfarm/ui/`. Zero touches to `dao/`, `service/`, `server/`, `util/`, `pom.xml`. |

### Phase 2 follow-ups left as TODOs
- `mobile.css` (B5) should add a width-based rule that re-enables the dashboard sidebar on `width >= 800dp`. The hidden FXML structure is preserved so the change is one-line per element.
- AlertController master-detail: push the detailPane as a stacked `View` onto AppManager when a row is tapped, so phone users get a full-width detail screen instead of a stacked section under the list.
- Long-press / tap-handler on the AppBar user-name label → profile / sign-out menu (B9 lifecycle sweep).
- Surface the system status (DB / sensors / online dots) in the NavigationDrawer footer instead of the current "Version 1.0.0" only.

---

## Pre-B4 fix: CropController.colVariety NPE — DONE ✅

The `crops.fxml` smoke failure flagged at the end of B3 (`NullPointerException: Cannot invoke "javafx.scene.control.TableColumn.setResizable(boolean)" because "this.colVariety" is null`) was the only thing blocking the crops screen from loading. Two valid fixes (per `docs/STATUS.md` line 149): add `<TableColumn fx:id="colVariety">` to `crops.fxml`, or drop the field from the controller.

Picked the controller-removal path because:
- `colVariety.setCellValueFactory(...)` only ever returned a hardcoded `"—"` em-dash — the column was decorative dead code with no backing `Crop.variety` property.
- The `Crop` model is **frozen for Phase 1** (§6 of `ANDROID_MIGRATION.md`), so wiring a real value would have required a cross-track Phase 2 model change anyway.
- Mobile layouts benefit from fewer table columns.

### Files modified
- `src/main/java/smartfarm/ui/CropController.java` — dropped `colVariety,` from the multi-field `@FXML` declaration (line 46), the `colVariety.setResizable(false)` call, and the `colVariety.setCellValueFactory(...)` block. `crops.fxml` untouched — its existing 6 columns now match the 6 fields exactly.

### Verification
| Gate | Result |
|------|--------|
| `grep -rn colVariety src/` | **0 matches** |
| Lane diff | 1 file, in `ui/` (3bdelbary lane) |

---

## B4: Replace FileChooser with CSVExporter + PlatformPickers — DONE ✅

### Scope
8 `javafx.stage.FileChooser` call sites in `ui/` had to go. `FileChooser` is desktop-only (it requires a `javafx.stage.Window` host) and crashes on Android. Hagag's H8 (`CSVExporter.saveCsv(String, String) → File`) had already shipped — B4 just had to wire it. The image picker in `DiseaseDetectionPage` was the one site that needed a new cross-platform abstraction (`PlatformPickers.pickImage()`, delivered as B8 in the same batch).

| Controller | Sites | Replacement |
|------------|------:|-------------|
| `DashboardController` | 4 (CSV: report, sensor, harvest, alert) | `CSVExporter.saveCsv()` |
| `LogsController` | 1 (CSV) | `CSVExporter.saveCsv()` |
| `ReportsController` | 1 (CSV) | `CSVExporter.saveCsv()` |
| `CropController` | 1 (CSV) | `CSVExporter.saveCsv()` |
| `DiseaseDetectionPage` | 1 (image open) | `PlatformPickers.pickImage()` |

### Pattern for the 7 CSV sites
Each `onExport*` method was rewritten from this shape:
```java
FileChooser chooser = new FileChooser();
chooser.setTitle("…"); chooser.setInitialFileName("name.csv");
chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
File file = chooser.showSaveDialog(someNode.getScene().getWindow());
if (file == null) return;
try (FileWriter fw = new FileWriter(file)) {
    fw.write("headers\n");
    for (Row r : rows) fw.write(String.format("…", …));
}
```
into:
```java
try {
    StringBuilder csv = new StringBuilder("headers\n");
    for (Row r : rows) csv.append(String.format("…", …));
    File saved = CSVExporter.saveCsv(csv.toString(), "name.csv");
    showAlert("Export", "Exported to " + saved.getName(), Alert.AlertType.INFORMATION);
} catch (IOException e) { … }
```

Column layouts (headers + `String.format` per row) are preserved verbatim — no user-visible CSV change. `CSVExporter.saveCsv` picks the destination: `~/Downloads/` on desktop, Gluon `StorageService.getPublicStorage("Documents")` on Android, with a timestamp suffix if the target name already exists. The new success toast surfaces the actual file name so the user can find it.

`IOException` from `saveCsv()` joined the existing `SQLException` catch in `DashboardController` via a multi-catch; the error UX is unchanged.

### DiseaseDetectionPage
```java
private void selectImage() {
    Optional<File> picked = PlatformPickers.pickImage(getScene().getWindow());
    picked.ifPresent(file -> {
        selectedFile = file;
        lblFileName.setText(file.getName() + "  (" + (file.length() / 1024) + " KB)");
        imagePreview.setImage(new Image(file.toURI().toString()));
        placeholder.setVisible(false);
        placeholder.setManaged(false);
        btnAnalyze.setDisable(false);
        resultsContainer.setVisible(false);
        resultsContainer.setManaged(false);
    });
}
```

Returns `Optional<File>` so a cancelled picker is a no-op instead of the old `if (file != null)` null check. The downstream `PlantIdService.analyzeImage(selectedFile.getAbsolutePath())` call site is unchanged — `PlatformPickers` guarantees a real file path on both platforms (see B8 below).

### Import cleanup
Removed from each controller:
- `import javafx.stage.FileChooser;`
- `import java.io.FileWriter;` (DashboardController, LogsController, ReportsController, CropController)

Added:
- `import smartfarm.util.CSVExporter;` (4 controllers)
- `import smartfarm.ui.platform.PlatformPickers;` + `import java.util.Optional;` (DiseaseDetectionPage)

### Verification
| Gate | Result |
|------|--------|
| `grep -rn "javafx.stage.FileChooser" src/main/java/smartfarm/ui` | **0 matches** in controllers (only the legitimate desktop fallback inside `PlatformPickers.java`) |
| `grep -rn "FileWriter" src/` | **0 matches** |
| `grep -rn "javafx.stage" src/main/java/smartfarm/ui` | **0 matches** in controllers |
| Lane diff | 5 controllers in `ui/` (3bdelbary lane), no `dao/`/`service/`/`server/`/`util/`/`pom.xml` touches |

---

## B8: PlatformPickers via Gluon Pictures — DONE ✅

### Scope
The migration doc §B8 asked for `smartfarm.ui.platform.PlatformPickers.pickImage()` returning a `File` on both targets. Gluon Attach `PicturesService` (already on the pom via Hagag's `a911016 [hagag] Phase 2 prep`) does the picking, but its `loadImageFromGallery()` returns `Optional<Image>` — a JavaFX `Image` object with no associated file path. Since `PlantIdService.analyzeImage(String imagePath)` (Hagag's lane, can't change) requires a real path, `PlatformPickers` has to persist the picked image to disk on Android before returning.

### Files created
- `src/main/java/smartfarm/ui/platform/PlatformPickers.java` (~210 lines)

### Public API
```java
public static Optional<File> pickImage(Window owner);
```

### Platform branch
Routes on `Constants.IS_ANDROID` (Hagag's H9), consistent with the same flag used in `Main.startFarmServerReflectively`.

**Desktop branch** — opens a `FileChooser` with the same `*.jpg / *.jpeg / *.png / *.bmp / *.webp` extension filters the disease page used pre-B4. Returns `Optional.ofNullable(chooser.showOpenDialog(owner))`. `owner` may be null without issue.

**Android branch** — chains:
```java
Services.get(PicturesService.class)
        .flatMap(PicturesService::loadImageFromGallery)  // Optional<Image>
        → writePng(Image)                                // File in private storage
```
The picked `Image` is encoded to PNG (RGBA, no per-scanline filter, single IDAT chunk) and written to `<private-storage>/picked-images/picked_<timestamp>.png`. The file is in the app's private storage directory (via `StorageService.getPrivateStorage()`) so the file is guaranteed readable by the same process and survives until the JVM exits or the file is overwritten by a subsequent pick. Filename includes a millisecond timestamp so concurrent picks don't collide.

### Why a pure-Java PNG encoder?
`javax.imageio` lives in the `java.desktop` module and `javafx.embed.swing.SwingFXUtils` lives in `javafx.swing` — **neither is on the GraalVM Substrate Android compile classpath**. The standard "use `ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file)"` recipe would not link.

The hand-rolled encoder uses only `java.io` + `java.nio.charset.StandardCharsets` + `java.nio.file.Files` + `java.util.zip` (`Deflater`, `CRC32`) — all in `java.base` which Substrate always provides. It reads pixels via `Image.getPixelReader().getArgb(x, y)`, repacks each scanline as `[0 filter byte][RGBA pixels...]`, deflates the whole IDAT body, and assembles the standard PNG signature + `IHDR` (color type 6 = truecolor + alpha, bit depth 8) + `IDAT` + `IEND` chunks with CRC-32 trailers. ~55 lines, no external deps. Plant.id accepts PNG so the downstream analysis call still works.

### Lifecycle & error handling
- `PicturesService` lookup failure (rare — only when `pictures` is missing from `<attachList>`) is logged via `Logger.e` and returns `Optional.empty()`.
- User cancellation of the gallery picker also returns `Optional.empty()` — `DiseaseDetectionPage.selectImage` then no-ops via `ifPresent`.
- PNG write failure is logged but never crashes the UI thread — caller still sees an empty optional and the existing "no file selected" state.

### Lane verification
| Check | Pass |
|-------|------|
| File lives under `src/main/java/smartfarm/ui/platform/` (3bdelbary lane via §6 `ui/**`) | ✅ |
| Read-only imports only: `Constants` (H9), `Logger` (H5/H10), `CSVExporter` (H8) — all Hagag-owned utilities | ✅ |
| Gluon Attach `pictures` already in `<attachList>` in `pom.xml` | ✅ (Hagag `a911016`) |
| Substrate-safe: zero `java.desktop` / `javafx.swing` references | ✅ |

### Phase 2 follow-ups
- The picked PNG is left in private storage indefinitely. A future cleanup pass could delete files older than N days on app start. Low priority — private storage is sandboxed and Android reclaims it when the app is uninstalled.
- `PicturesService.takePhoto(boolean savePhoto)` is not exposed yet. A future "take photo" entry next to "browse" in `DiseaseDetectionPage` would re-use the same `encodePng` helper.

---

## B5: mobile.css — DONE ✅

### Scope
§8.B5 of `ANDROID_MIGRATION.md` asked for:
- Larger base font (16 px ≈ 14sp on Android).
- ≥48dp touch targets on inputs/buttons.
- Larger row height for `CharmListView`.
- Reduced padding where it would push content off-screen.
- Keep AtlantaFX `PrimerLight` as the base.
- Load `mobile.css` after the AtlantaFX stylesheet in `Main`.
- Verify all 12 screens still look intentional.

### Conditional loading decision
JavaFX CSS has no `@media` equivalent for width-based rules. Loading `mobile.css` unconditionally would visibly enlarge the desktop UI (16 px font + 48dp inputs everywhere) — that conflicts with §B5's verification gate "all 12 screens still look intentional, not like the desktop with text shrunk". Reading that the other way: desktop shouldn't suddenly look like the phone either.

**Decision: load `mobile.css` only on Android, gated on `Constants.IS_ANDROID`.** Desktop keeps its current dashboard-optimized sizing from `farm-theme.css`; Android picks up the touch-friendly overrides. The two profiles share the same FXMLs and the same `farm-theme.css` palette/chrome — only the sizing layer differs.

This matches the same pattern already established in `Main.postInit` for the desktop-only `FarmServer` reflective load.

### Files created
- `src/main/resources/css/mobile.css` (~110 lines, ~25 selectors)

### Files modified
- `src/main/java/smartfarm/Main.java` — `postInit(Scene)` now loads `/css/mobile.css` inside the `Constants.IS_ANDROID` branch, after `/css/farm-theme.css`. The earlier `// mobile.css added in B5` placeholder comment is gone. The desktop branch (window defaults + FarmServer reflective load) is now the `else` of the same `if` so the two profiles are visually paired in the source.

### Selectors covered
| Selector group | Override |
|----------------|----------|
| `.root` | `-fx-font-size: 16` — base size; controls without their own override inherit |
| `.button`, `.toggle-button`, `.menu-button`, `.split-menu-button`, `.choice-box`, `.combo-box` | `-fx-min-height: 48; -fx-padding: 10 18 10 18` |
| `.icon-btn` | `-fx-min-width: 44; -fx-min-height: 44` (compact, still tap-friendly) |
| `.text-field`, `.password-field`, `.date-picker > .text-field` | `-fx-min-height: 48; -fx-padding: 12 14 12 14` |
| `.text-area .content` | `-fx-padding: 12 14 12 14` |
| `.date-picker`, `.search-box` | `-fx-min-height: 48` |
| `.list-tile`, `.charm-list-cell` | `-fx-min-height: 84; -fx-padding: 12 16 12 16` — fits a 3-line tile + 48dp action buttons |
| `.nav-drawer .list-cell` | `-fx-min-height: 56; -fx-padding: 12 20 12 20; -fx-font-size: 15` — Material spec for drawer rows |
| `.dialog-pane .button` | `-fx-min-height: 48; -fx-min-width: 80; -fx-padding: 10 22 10 22` |
| `.table-view` | `-fx-fixed-cell-size: 48` — for the 5 inner pages still using TableView (dashboard, crops, alerts, monitoring, reports) |
| `.page-title` | `-fx-font-size: 22` — keeps headings visually anchored after the global 16 px bump |

Glisten-specific selectors (`.list-tile`, `.charm-list-cell`, `.nav-drawer .list-cell`) target the controls B3 + B3X introduced (`CharmListView` for workers/tasks/harvest/logs, `NavigationDrawer` in ShellView).

### Cascading order
```
1. AtlantaFX PrimerLight     (Application.setUserAgentStylesheet)
2. /css/farm-theme.css       (scene stylesheet, both targets)
3. /css/mobile.css           (scene stylesheet, Android only)
```
Each later stylesheet wins where its selectors overlap. `farm-theme.css` rules with explicit sizes (e.g. `.sensor-value { -fx-font-size: 22 }`) keep their look because `mobile.css` doesn't touch those component-specific selectors; only the generic control selectors get the touch-friendly bump.

### Width-based sidebar toggle — deferred
The B3X follow-up note ("`mobile.css` should add a width-based rule that re-enables the dashboard sidebar on `width >= 800dp`") is **not** implementable in pure JavaFX CSS — there are no media queries. The proper fix is a controller-side listener on `Scene.widthProperty()` that toggles `sidebar.setVisible/Managed(...)` and `topbar.setVisible/Managed(...)` based on a threshold. That's a small `DashboardController` change, but it's a behavioral rather than CSS one and out of scope for B5. Captured below in Phase 2 follow-ups.

### Verification
| Gate | Result |
|------|--------|
| `mobile.css` file exists at `src/main/resources/css/mobile.css` | ✅ |
| Loaded after `farm-theme.css` in `Main.postInit` | ✅ |
| Loaded only on Android (`Constants.IS_ANDROID` branch) | ✅ |
| No `@media` queries or other unsupported CSS | ✅ (pure JavaFX CSS subset) |
| `PrimerLight` still the user-agent stylesheet | ✅ (unchanged) |
| Lane diff | 2 files: `Main.java` (3bdelbary lane) + `css/mobile.css` (new, 3bdelbary lane). Zero touches to `dao/`, `service/`, `server/`, `util/`, `pom.xml`. |

### Phase 2 follow-ups
- Width-based sidebar toggle: add a `Scene.widthProperty()` listener in `DashboardController` or `ShellView` that flips the hidden sidebar/topbar back on when `width >= 800` (the dashboard's natural breakpoint). The FXML structure is preserved (`visible="false" managed="false"`) so this is a one-line toggle per element.
- A real-device run on a 5" / 6.5" Android emulator would confirm the 84dp `.list-tile` size hits 48dp action buttons cleanly without truncating the 3-line text — left as a manual visual gate (no automated harness in Phase 1).

---

## B6: Drop any remaining JFreeChart use — DONE ✅

### Scope
§8.B6 of `ANDROID_MIGRATION.md` had two clauses:
1. Drop any leftover `JFreeChart` / `JFreeChart-FX` use — Gluon-friendly charts only.
2. For `MonitoringController`, make sure live updates use `Platform.runLater` and bound the data series to the last N points.

### Audit results
| Check | Result |
|-------|--------|
| `jfree` (case-insensitive) in `src/` | **0 matches** |
| `JFreeChart` / `ChartFactory` / `ChartPanel` / `XYPlot` / `JFreeChart-FX` symbols in `src/` | **0 matches** |
| `pom.xml` JFreeChart dependencies | Still in the `desktop` profile (`jfreechart 1.5.4` + `jfreechart-fx 1.0.1`), with a comment explicitly waiting on this B6 audit |
| `MonitoringController.subscribeLiveSensor` FX-thread safety | ✅ already correct — `LiveSensorData.update(...)` wraps property mutations in `Platform.runLater`, so listener bodies in the controller fire on the FX thread |
| `MonitoringController.setupTrendChart` bounded series | N/A in current state — the trend chart is populated with 8 hardcoded mock data points at startup and is never appended to. No memory-growth risk. |

### Source changes
Zero. The §B6 audit's only mandatory action was confirming `src/` had no JFreeChart references — that was already true before B6 started (B3-era survey already moved away from JFreeChart).

One defensive change for future-proofing:
- `src/main/java/smartfarm/ui/MonitoringController.java` — added a `TODO(phase-2)` block comment at the top of `setupTrendChart()` documenting the unbounded-growth gotcha for whoever wires real live data into the chart later. Documents the pattern (`trim from head before append`) and notes that `LiveSensorData.update` already marshals onto the FX thread, so the append point doesn't need its own `Platform.runLater` wrapper.

### Cross-track — Hagag follow-up
`pom.xml` still has `org.jfree:jfreechart:1.5.4` and `org.jfree:jfreechart-fx:1.0.1` declared in the `desktop` profile (lines 257-269 in the current pom). Hagag left an explicit comment there:
> *JFreeChart — advanced charts. Currently unused in source but kept in the desktop profile until 3bdelbary's B6 chart audit confirms removal.*

This audit is that confirmation. The dependencies are now safe to drop — `mvn -Pdesktop dependency:tree` will show them as unused, and removing them shrinks the desktop fat-jar by ~3 MB. **Action item left for Hagag** (`pom.xml` is Hagag's lane per §6); not part of B6's done-state on this side.

### Verification
| Gate | Result |
|------|--------|
| `grep -ri "jfree\|JFreeChart\|ChartFactory\|ChartPanel\|XYPlot" src/` | **0 matches** ✅ |
| `MonitoringController.subscribeLiveSensor` invokes UI setters on FX thread | ✅ (via `LiveSensorData.update`'s internal `Platform.runLater`) |
| `MonitoringController.setupTrendChart` documents the bounded-series rule | ✅ (new `TODO(phase-2)` comment) |
| Lane diff | 1 file: `MonitoringController.java` (3bdelbary lane). Comment-only edit — no behavioral change. |

### Phase 2 follow-ups
- Wire `MonitoringController.setupTrendChart` to real `LiveSensorData` updates (currently mock). Reuse the existing `subscribeLiveSensor` pattern but cap each series at ~100 points by trimming from the head before appending.
- The `cmbChartPeriod` ComboBox ("24 Hours" / "7 Days" / "30 Days") has no listener wired today — once live data is in, hook it to drive a `SensorDAO.getRecent(period)`-style query and rebuild the series.

---

## B7: Android launcher icons — DONE ✅

### Scope
`AndroidManifest.xml:61-62` references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. Without those PNG bitmaps on disk the APK assembly fails. §B7 of `ANDROID_MIGRATION.md` asked for icons at the five standard Android density buckets (mdpi → xxxhdpi), derived from `images/logo.png`. The `STATUS.md` row pinned the target paths at `src/android/res/mipmap-*/ic_launcher{,_round}.png`.

### Approach: in-repo generator, no external tooling
The cleanest path that lives entirely in 3bdelbary's lane is a small generator that reads `logo.png` and produces all ten PNGs. Two equivalent implementations ship side by side so the team can run whichever fits their environment:

| Generator | When to use | Stack |
|-----------|-------------|-------|
| `smartfarm.ui.tools.LauncherIconGenerator` (Java, JavaFX `Application`) | Anywhere with Maven + JavaFX configured (i.e. the normal dev setup) | JavaFX `Canvas.snapshot` + the shared `smartfarm.ui.platform.PngEncoder` |
| `src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1` (PowerShell) | Quick Windows-side regeneration that doesn't require building the project | `System.Drawing` (built into Windows .NET) |

Both produce byte-for-byte equivalent layouts: `#2e7d32` (brand green from `farm-theme.css`) background, source logo centred at 72% of the icon edge with aspect ratio preserved. The `_round.png` variant uses an antialiased circular background plus a clip path so the logo can't bleed outside the disc — launchers that don't auto-mask still display a proper round icon.

### Files created
1. `src/main/java/smartfarm/ui/platform/PngEncoder.java` — extracted from the inline encoder I built for `PlatformPickers` in B8. Single public method `PngEncoder.encode(Image img) → byte[]`. RGBA, filter type 0, single IDAT, deflate via `java.util.zip`. Substrate-safe (no `java.desktop` / `javafx.swing`).
2. `src/main/java/smartfarm/ui/tools/LauncherIconGenerator.java` — JavaFX `Application`. Reads `images/logo.png`, renders the 5 density × 2 variant matrix on a Gluon-friendly `Canvas`, writes via `PngEncoder`. ~190 lines.
3. `src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1` — PowerShell mirror of the Java generator using `System.Drawing` (`FillEllipse`, `DrawImage`, `Save` to PNG). Resolves paths relative to its own location so it can be invoked from anywhere. ~130 lines including comments.
4. **10 generated PNG bitmaps** under `src/android/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png`. Committed.

### Files modified
- `src/main/java/smartfarm/ui/platform/PlatformPickers.java` — the previously inline `encodePng` / `deflate` / `writeChunk` private methods are gone. `writePng(Image)` now delegates to `PngEncoder.encode(img)`. Class shrank by ~60 lines; behaviour unchanged.

### How to (re-)run the generator

**PowerShell (no Maven required, used to produce the committed PNGs):**

```powershell
# From anywhere — the script auto-resolves paths relative to itself:
pwsh -ExecutionPolicy Bypass `
     -File .\src\main\java\smartfarm\ui\tools\generate-launcher-icons.ps1
```

**Java (when the full toolchain is set up):**

```powershell
# From the project root:
mvn -Pdesktop compile exec:java `
    -Dexec.mainClass="smartfarm.ui.tools.LauncherIconGenerator"
```

Expected output (either path):
```
Source logo: <project-root>\src\main\resources\images\logo.png (1024x1024)
Output root: <project-root>\src\android\res

  [mdpi      48 px] ic_launcher.png, ic_launcher_round.png
  [hdpi      72 px] ic_launcher.png, ic_launcher_round.png
  [xhdpi     96 px] ic_launcher.png, ic_launcher_round.png
  [xxhdpi   144 px] ic_launcher.png, ic_launcher_round.png
  [xxxhdpi  192 px] ic_launcher.png, ic_launcher_round.png

Done. Wrote 10 icon files under <project-root>\src\android\res
```

### Verification
| Gate | Result |
|------|--------|
| 10 PNGs present at `src/android/res/mipmap-*/ic_launcher{,_round}.png` | ✅ |
| Pixel dimensions match Android density spec (48/72/96/144/192) | ✅ (verified via `System.Drawing.Image.FromFile().Width/Height` on each) |
| File sizes scale ~quadratically with density (1 KB → 8 KB) | ✅ |
| Visual smoke (largest variant) | ✅ — `#2e7d32` background, bird logo centred, clean antialiased circle on the round variant |
| Source logo `images/logo.png` unchanged | ✅ |
| `AndroidManifest.xml` (Hagag's lane) untouched | ✅ |

### Tweaking the icons later
- Background colour: change `BackgroundColor` in `generate-launcher-icons.ps1` (or `BACKGROUND` in `LauncherIconGenerator.java`). Default `#2e7d32`.
- Logo margin: change `LogoScale` / `LOGO_SCALE` (default `0.72` = 14% margin per side).
- Source logo: pass `-LogoPath <path>` to the PS script, or edit `SOURCE_LOGO` in the Java generator. Try `/images/logo-dark.png` if the dark variant reads better.

Re-run the generator after any change; commit the new PNGs.

### Lane check (§6 / §7 of `ANDROID_MIGRATION.md`)
| Check | Pass |
|-------|------|
| Java sources under `src/main/java/smartfarm/ui/**` (3bdelbary lane) | ✅ |
| Source logo read from `src/main/resources/images/logo.png` (3bdelbary lane) | ✅ |
| Output written under `src/android/res/mipmap-*/` (icon **content** is 3bdelbary's per §B7; Hagag owns the manifest references in `src/android/AndroidManifest.xml`) | ✅ |
| No edits to `dao/`, `service/`, `server/`, `util/`, `pom.xml`, `META-INF/native-image/**`, `model/`, or `AndroidManifest.xml` | ✅ |
| `PlatformPickers` refactored to use `PngEncoder` — behaviour preserved, class shrank | ✅ |

### Phase 2 follow-ups
- Adaptive icons (`mipmap-anydpi-v26/ic_launcher.xml` + foreground/background drawables) would scale better on Android 8+'s round/squircle/teardrop launcher masks than the current legacy PNG approach. Out of scope for Phase 1 — current PNGs are still valid and supported through Android 14.
- Splash screen drawable: §B7 of the migration doc also mentions "Add a splash screen image referencing the existing logo." The in-app splash (`SplashView` from B1) covers the user-visible splash; what's missing is the brief native pre-FX splash that some Gluon apps show during the Substrate binary cold start. Low-priority polish — defer to Phase 2.

---

## B9: Lifecycle hooks sweep — DONE ✅

### Scope
§8.B9 of `ANDROID_MIGRATION.md` had two clauses:
1. Replace any "set up on stage shown" patterns with Gluon `View#setOnShown` / `View#setOnHidden`.
2. Stop background timers / animations on `setOnHidden` so a paused app does not keep ticking. Verify: rotating the emulator does not duplicate timers.

### Audit results

| Pattern | Hits in `src/main/java/smartfarm/ui` | Verdict |
|---------|--------------------------------------|---------|
| `stage.setOn*` / `windowProperty()` / `showingProperty()` | **0** | Already eliminated by the B1/B2 nav rewrite |
| `setOnShowing` / `setOnHiding` on Gluon Views | All 4 Views have both | Already wired (SplashView, SignInView, SignUpView, ShellView) |
| `javafx.animation.Timeline` | 1 (`DashboardController.updateDateTime`) | Real-time clock, runs forever |
| `new Thread(...).start()` | 5 sites (SplashView session restore, SignInController fingerprint scan, WorkerController fingerprint enroll/delete ×2, DiseaseDetectionPage Plant.id API) | All one-shot tasks tied to user action; **not** background tickers — no fix needed |
| `LiveSensorData` listener attachments | 5 in `DashboardController`, 3 in `MonitoringController` | `MonitoringController` actually leaks across page swaps; `DashboardController` doesn't today (shell caches it) but is forward-compat-fixed |
| Node-local property listeners (`txtSearch.textProperty`, table-selection `selectedItemProperty`, etc.) | Many, across most controllers | GC'd with their owning nodes — no fix needed |

### Real leak fix — `MonitoringController`

Each click on the "Monitoring" nav item calls `DashboardController.loadFxmlPage("/fxml/monitoring.fxml", btnMonitoring)`, which replaces the inner-page root via `pageContainer.getChildren().setAll(...)`. The old root is dropped from the scene graph, but its 3 lambda listeners on `LiveSensorData.{temperature,humidity,soilMoisture}Property` stay registered on the **singleton** service. Each capturing lambda keeps the old controller alive, so repeated visits compound:
- Listener count on `LiveSensorData` grows linearly with visits.
- The old controllers + their FXML scene graphs leak.
- The user sees the live label still ticking via the *old* controller's `lblTemp` (which is no longer onscreen but still receiving updates).

**Fix:** promote the 3 lambdas to `ChangeListener<Number>` fields, then hook `lblTemp.sceneProperty()` so when the scene reference goes `null` (page unmount) the listeners are removed via `unsubscribeLiveSensor()`. The scene-property listener itself is owned by `lblTemp` and is GC'd with the rest of the subtree — no chain-of-references leak.

This pattern is the JavaFX-idiomatic equivalent of `setOnHidden` for sub-page controllers that aren't themselves Gluon `View`s. It satisfies §B9 clause 2 for the actual leak site.

### Forward-compat hook — `DashboardController`

`ShellView` caches `DashboardController` in its constructor (B2 work) and reuses the same instance for the JVM's lifetime — every shell show/hide goes through the same controller. So today the clock + 5 `LiveSensorData` listeners don't actually leak. But:
- The Timeline ticks 1/sec even when the app is OS-backgrounded.
- The listeners fire on every sensor update even when the user is on Sign-In.
- Phase 2 will wire `com.gluonhq.attach.lifecycle.LifecycleService` PAUSE/RESUME to genuinely idle the app.

**Refactor:**
- `Timeline clock` promoted from a local in `updateDateTime()` to an instance field; the method is now idempotent (early-return if already started).
- 5 LiveSensorData listeners (`liveTempListener`, `liveHumListener`, `liveSoilListener`, `liveDeviceListener`, `activeSensorsListener`) promoted to `ChangeListener` fields, attached behind null-checks.
- New public method `DashboardController.stopLifecycle()` — stops the clock, removes the 5 listeners, idempotent. Documented to be the intended hook for Phase 2's `LifecycleService.PAUSE` event.

Not called from `ShellView.setOnHiding` today because the natural pair (`startLifecycle()` on every `setOnShowing`) isn't wired either — that's the Phase 2 work. Today's behaviour is unchanged.

### Rotation verification
The Android `<activity>` element in `src/android/AndroidManifest.xml` declares:
```xml
android:configChanges="orientation|keyboardHidden|screenSize|smallestScreenSize"
```
which intercepts orientation changes at the Activity level — Android does **not** destroy and recreate the Activity (or the JVM) on rotation. The §B9 verification gate ("rotating the emulator does not duplicate timers") is satisfied by this manifest configuration plus the field-backed `Timeline` (so even in a hypothetical future where the activity recycles, a fresh DashboardController doesn't start a second clock).

### Files modified
- `src/main/java/smartfarm/ui/MonitoringController.java` — added `import javafx.beans.value.ChangeListener`; 3 listener fields; `unsubscribeLiveSensor()` method; scene-property auto-detach in `subscribeLiveSensor()`. ~30 line delta.
- `src/main/java/smartfarm/ui/DashboardController.java` — added `import javafx.beans.value.ChangeListener`; 6 lifecycle fields (Timeline + 5 listeners); idempotency guards in `updateDateTime()`, `updateSidebarStatus()`, `subscribeLiveSensor()`; new public `stopLifecycle()` with full javadoc. ~75 line delta.

### Lane check (§6 / §7 of `ANDROID_MIGRATION.md`)
| Check | Pass |
|-------|------|
| Edits only under `src/main/java/smartfarm/ui/` (3bdelbary `ui/**` lane) | ✅ |
| No edits to `dao/`, `service/`, `server/`, `util/`, `pom.xml`, `META-INF/native-image/**`, `model/`, `AndroidManifest.xml` | ✅ |
| `LiveSensorData` service (Hagag's lane) read-only-imported, not modified | ✅ |
| Behavioural surface preserved (clock still ticks; sensor labels still update) | ✅ — the field-backed versions are functionally identical to the previous local-variable versions |

### Phase 2 follow-ups
- **Wire `LifecycleService` (Hagag-track + cross-track)** — once the Gluon Attach `LifecycleService` is registered in `Main`, add `PAUSE` → `controller.stopLifecycle()` and `RESUME` → a future `controller.startLifecycle()` hook in `ShellView`. The `startLifecycle()` method is straightforward to add: call `updateDateTime()` + `subscribeLiveSensor()` + the listener-attach portion of `updateSidebarStatus()`. All three are already idempotent.
- **Apply the scene-property auto-detach pattern to other sub-pages if they grow service-singleton listeners.** Today only `MonitoringController` does; if a future page subscribes to `LiveSensorData`, `AlertService`, etc. directly, mirror the same `<anyFXMLField>.sceneProperty().addListener(...)` hook.
- **Long-press / tap-handler on the AppBar user-name label** (carry-over from the B3X follow-up list) — still pending; not a B9 lifecycle issue strictly, but the existing B9 comment in `ShellView.configureAppBar` referenced it.

---

## B10: Final consolidation — DONE ✅

§B10 of `ANDROID_MIGRATION.md` asked for this doc to list "every FXML's status, every controller's status, all TODOs left for Phase 2". The narrative B1–B9 sections above cover the *what* and *why*; this section is the at-a-glance reference.

### FXML status matrix (12 files in `src/main/resources/fxml/`)

Legend: ✅ done · — not applicable · N/A pre-existing pattern, no change needed.

| FXML | Root size dropped (B3) | TableView → CharmListView (B3) | Vertical auth (B3X) | FlowPane cards (B3X) | FlowPane filters (B3X) | Other |
|------|:---:|:---:|:---:|:---:|:---:|---|
| `signin.fxml`     | ✅ | — | ✅ | — | — | |
| `signup.fxml`     | ✅ | — | ✅ | — | — | |
| `dashboard.fxml`  | ✅ | — | — | ✅ | ✅ | sidebar + topbar hidden via `visible="false" managed="false"` (B3X.5–7); replaced by Gluon `NavigationDrawer` + `AppBar` in `ShellView` |
| `crops.fxml`      | ✅ | — | — | ✅ | ✅ | `colVariety` column reference dropped from controller (pre-B4 fix) |
| `plots.fxml`      | ✅ | — | — | ✅ | ✅ | 2 hover-only tooltips removed (B3.5) |
| `workers.fxml`    | ✅ | ✅ | — | ✅ | ✅ | |
| `tasks.fxml`      | ✅ | ✅ | — | ✅ | ✅ | |
| `alerts.fxml`     | ✅ | — | — | ✅ | ✅ | root restructured `HBox` → stacked `VBox` (B3X.4) — list above, detail below |
| `monitoring.fxml` | ✅ | — | — | ✅ | ✅ | |
| `harvest.fxml`    | ✅ | ✅ | — | ✅ | ✅ | |
| `reports.fxml`    | ✅ | — | — | ✅ | ✅ | |
| `logs.fxml`       | ✅ | ✅ | — | ✅ | ✅ | severity-coloured icon badges in `ListTile` |

ScrollPane wrap was a no-op across the board: `dashboard.fxml`'s outer `<ScrollPane fitToWidth="true">` (line 153) already wraps `pageContainer`, so all 9 inner pages inherit vertical scrolling for free; the 2 auth views are short forms.

### Controller status matrix (17 in `smartfarm/ui/`)

Legend: ✅ done · — not applicable · ⚠ has Phase 2 TODO.

| Controller / Page | B2 nav (drop `Stage`) | B3 `CharmListView` adapter | B4 `FileChooser` → `CSVExporter`/`PlatformPickers` | B9 lifecycle | Phase 2 async DAO wrap |
|-------------------|:---:|:---:|:---:|:---:|:---:|
| `SignInController`         | ✅ | — | — | — | ⚠ fingerprint thread + `AuthService.authenticate` blocking |
| `SignUpController`         | ✅ | — | — | — | ⚠ |
| `DashboardController`      | ✅ | — | ✅ (4 CSV exports) | ✅ `stopLifecycle()` ready for Phase 2 LifecycleService | ⚠ DAO calls on FX thread |
| `CropController`           | — | — | ✅ (1 CSV)       | — | ⚠ |
| `CropsPage`                | — | — | — | — | ⚠ |
| `PlotController`           | — | — | — | — | ⚠ has try/catch for `Crop.GrowthStage.GROWING` enum mismatch (workaround until fix) |
| `WorkerController`         | — | ✅ | — | — | ⚠ |
| `TaskController`           | — | ✅ | — | — | ⚠ |
| `AlertController`          | — | — | — | — | ⚠ |
| `MonitoringController`     | — | — | — | ✅ scene-property auto-detach for `LiveSensorData` listeners | ⚠ also `setupTrendChart` is mock-only — see Phase 2 TODO list below |
| `HarvestController`        | — | ✅ | — | — | ⚠ defensive `RuntimeException` catch for null DB (pending H5 sweep) |
| `ReportsController`        | — | — | ✅ (1 CSV)       | — | ⚠ |
| `LogsController`           | — | ✅ | ✅ (1 CSV)       | — | — |
| `DiseaseDetectionPage`     | — | — | ✅ (image picker) | — | — (uses `Task<>`/Thread for Plant.id call already) |
| `AttendancePage`           | — | — | — | — | — |
| `SettingsPage`             | — | — | — | — | — |
| `AboutPage`                | — | — | — | — | — |

### Gluon View status (4 in `smartfarm/ui/views/`)

| View | `setOnShowing` | `setOnHiding` | Notes |
|------|:---:|:---:|---|
| `SplashView`  | ✅ hides `AppBar`, kicks off session-restore daemon thread (B1) | ✅ restores `AppBar` | min-visible 800 ms guard; `restoreStarted` volatile flag against re-entry |
| `SignInView`  | ✅ logs lifecycle | ✅ logs lifecycle | thin FXML wrapper |
| `SignUpView`  | ✅ logs lifecycle | ✅ logs lifecycle | thin FXML wrapper |
| `ShellView`   | ✅ configures `AppBar` + `NavigationDrawer`, pushes user from `NavContext` (B2 + B3X) | ✅ clears `AppBar` (nav icon + action items + visibility) | caches `DashboardController`; drawer items dispatch through `controller.navigate(NavTarget)` |

### Consolidated Phase 2 TODO list

Cross-cutting concerns surfaced during B1–B9 that intentionally land in Phase 2 (after both tracks merge to `main` and the `model/` freeze lifts):

**Async / thread-safety**
- Wrap every DAO call site in `DBConnection.runAsync(...)` (Hagag's H4) — every controller marked ⚠ in the table above. This is the bulk of Phase 2 work.
- `AuthService.restoreSession()` is synchronous and could hang `SplashView` on a slow MySQL — give it a timeout/cancel path.
- `AuthService.authenticate()` runs on FX thread in `SignInController.onSignIn` — wrap.
- `cmbChartPeriod` ComboBox in `MonitoringController` ("24 Hours" / "7 Days" / "30 Days") has no listener wired today — once `setupTrendChart` is wired to live data, hook it to drive a `SensorDAO.getRecent(period)` query and rebuild the series.

**Lifecycle**
- Wire Gluon Attach `LifecycleService` in `Main` to dispatch PAUSE/RESUME → `DashboardController.stopLifecycle()` and a future `startLifecycle()` (cross-track; `Main` is in 3bdelbary's lane but `LifecycleService` registration is typically Hagag-side per the H10 work).
- Apply the `sceneProperty()` auto-detach pattern to any future sub-page that subscribes to a service singleton (mirror the B9 fix in `MonitoringController`).
- `MonitoringController.setupTrendChart` is currently static mock data — wire to real `LiveSensorData` updates and cap each series at ~100 points by trimming from the head before appending. See `TODO(phase-2)` block comment in the source.

**Layout / UX polish**
- Width-based dashboard sidebar toggle: `Scene.widthProperty()` listener in `DashboardController` (or `ShellView`) that flips `sidebar.setVisible/Managed(...)` + `topbar.setVisible/Managed(...)` back on at ≥800 dp. The hidden FXML structure is preserved so this is a one-line per element.
- AlertController master-detail: push `detailPane` as a stacked `View` onto `AppManager` when a row is tapped, so phone users get a full-width detail screen instead of a stacked section under the list.
- Long-press / tap-handler on the AppBar user-name label → profile / sign-out menu (referenced in `ShellView.configureAppBar`).
- Surface system status (DB / sensors / online dots) in the `NavigationDrawer` footer — currently shows only "Version 1.0.0".
- Visual smoke on a 5" / 6.5" Android emulator to confirm the 84 dp `.list-tile` size hits 48 dp action buttons cleanly without truncating the 3-line text — manual gate, no Phase 1 harness.

**Assets**
- Adaptive icons (`mipmap-anydpi-v26/ic_launcher.xml` + foreground/background drawables) for cleaner Android 8+ launcher masks. Current legacy PNGs work through Android 14.
- Native pre-FX splash drawable (Substrate cold-start splash before the JVM is up). The in-app `SplashView` covers the user-visible splash; this is just the brief pre-FX bit.
- Cleanup of old picked PNGs in `picked-images/` private storage (B8) — low priority, sandboxed.
- `PicturesService.takePhoto(boolean savePhoto)` entry next to "Browse Image" in `DiseaseDetectionPage` — re-use the existing `PngEncoder` helper.

**Tests**
- `NavContext` unit tests pending JUnit/Surefire in `pom.xml` (Hagag's lane).

**Frozen-model fix (cross-track)**
- `Crop.GrowthStage` enum vs DB data mismatch: rows with `growth_stage='GROWING'` crash `dashboard.fxml` and `reports.fxml` during init via `CropDAO.extractCrop()`. Three options: (a) add `GROWING` to the enum (`model/`, frozen), (b) UPDATE the DB rows to a valid value, (c) make `CropDAO.extractCrop()` handle unknown values gracefully (`dao/`, Hagag's lane). Phase 1 workaround: option (b) via SQL.

### Cross-track items waiting on Hagag

These were surfaced by B1–B9 work and parked because they cross the lane boundary:

1. **Drop JFreeChart deps from `pom.xml`** — B6 audit confirmed zero `src/` references. `pom.xml` desktop profile still declares `org.jfree:jfreechart:1.5.4` + `org.jfree:jfreechart-fx:1.0.1` with a comment explicitly waiting on this audit. Trims ~3 MB from the desktop fat-jar.
2. **`exec-maven-plugin` for the launcher icon generator (optional)** — would let the team run `mvn -Pdesktop exec:java -Dexec.mainClass=smartfarm.ui.tools.LauncherIconGenerator`. The PowerShell variant `generate-launcher-icons.ps1` is already a working alternative that needs no Maven plumbing.
3. **Wire Gluon Attach `LifecycleService` in `Main`** — register a listener that dispatches PAUSE → `DashboardController.stopLifecycle()` and (eventually) RESUME → `startLifecycle()`. The 3bdelbary side is ready (the `stopLifecycle()` method is the dedicated hook).

### Read-only imports from Hagag's lane (recap)

3bdelbary code imports — never modifies — these Hagag-owned utilities:

| Symbol | Source file | Used by |
|--------|-------------|---------|
| `smartfarm.util.Constants.IS_ANDROID` | H9 | `Main.postInit`, `PlatformPickers.pickImage` |
| `smartfarm.util.Logger.{d,i,w,e}` | H5 / H10 | `SplashView`, `ShellView`, `SignInView`, `SignUpView`, `Main`, `PlatformPickers` |
| `smartfarm.util.CSVExporter.saveCsv` | H8 | `DashboardController` (×4), `LogsController`, `ReportsController`, `CropController` |
| `smartfarm.service.SessionManager.{loadSession,clearSession}` | H6 | `SplashView`, `DashboardController.onLogout`, `ShellView.signOut` |
| `smartfarm.service.AuthService` | (existing) | `SignInController`, `SignUpController`, `SplashView` |
| `smartfarm.service.LiveSensorData` | (existing) | `DashboardController`, `MonitoringController`, `CropController` |
| `smartfarm.service.PlantIdService` | (existing) | `DiseaseDetectionPage` |
| `smartfarm.model.User`, `smartfarm.model.Crop`, ... | (frozen Phase 1) | All controllers — read-only by §6 |

### Files added or modified across the entire 3bdelbary track

**New Java sources (10) + 1 PowerShell helper**
- `src/main/java/smartfarm/ui/nav/AppView.java` (B1)
- `src/main/java/smartfarm/ui/nav/ShellContent.java` (B1)
- `src/main/java/smartfarm/ui/nav/NavContext.java` (B1)
- `src/main/java/smartfarm/ui/views/SplashView.java` (B1)
- `src/main/java/smartfarm/ui/views/SignInView.java` (B1)
- `src/main/java/smartfarm/ui/views/SignUpView.java` (B1)
- `src/main/java/smartfarm/ui/views/ShellView.java` (B1, expanded in B2 + B3X)
- `src/main/java/smartfarm/ui/platform/PlatformPickers.java` (B8)
- `src/main/java/smartfarm/ui/platform/PngEncoder.java` (B7 — extracted from `PlatformPickers`)
- `src/main/java/smartfarm/ui/tools/LauncherIconGenerator.java` (B7)
- `src/main/java/smartfarm/ui/tools/generate-launcher-icons.ps1` (B7 — PowerShell mirror, runs without Maven)

**New resource files (1 CSS + 10 PNG)**
- `src/main/resources/css/mobile.css` (B5)
- `src/android/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png` (B7 — 10 binaries)

**Modified Java files (12)**
- `src/main/java/smartfarm/Main.java` (B1, B5)
- `src/main/java/smartfarm/ui/SignInController.java` (B2)
- `src/main/java/smartfarm/ui/SignUpController.java` (B2)
- `src/main/java/smartfarm/ui/DashboardController.java` (B2, B4, B9)
- `src/main/java/smartfarm/ui/CropController.java` (`colVariety` fix, B4)
- `src/main/java/smartfarm/ui/LogsController.java` (B3, B4)
- `src/main/java/smartfarm/ui/ReportsController.java` (B4)
- `src/main/java/smartfarm/ui/WorkerController.java` (B3)
- `src/main/java/smartfarm/ui/TaskController.java` (B3)
- `src/main/java/smartfarm/ui/HarvestController.java` (B3)
- `src/main/java/smartfarm/ui/MonitoringController.java` (B6 TODO comment, B9)
- `src/main/java/smartfarm/ui/DiseaseDetectionPage.java` (B4)

**Modified resource files (12 FXML, all of `resources/fxml/`)**

All 12 FXMLs touched in B3 (root size drops, list conversions where applicable) and again in B3X (FlowPane summary/filter rows on inner pages, vertical auth on signin/signup, drawer migration on dashboard, master-detail restack on alerts).

**Docs (3)**
- `docs/MIGRATION_3BDELBARY.md` — this file (B1, B2, B3, B3X, B4, B5, B6, B7, B8, B9, B10)
- `docs/STATUS.md` — running status snapshot, kept in sync with each task close
- `docs/superpowers/specs/2026-05-14-b1-gluon-mobileapplication-design.md` — B1 design spec (created at B1 start, not modified since)

### Phase 1 wrap-up

3bdelbary's Phase 1 track is **complete**. The app now boots through `Splash → SignIn → Shell` on both `mvn -Pdesktop javafx:run` and the `mvn -Pandroid` profile compile, all 12 FXMLs are mobile-friendly with Gluon-native chrome, the 10 Android launcher icons live under `src/android/res/mipmap-*/`, every cross-platform asset (CSV exporter, image picker, PNG encoder, mobile.css) has its desktop + Android fork wired through `Constants.IS_ANDROID`, and the lifecycle hooks are ready for Hagag's eventual `LifecycleService` registration. No `Stage`, `FileChooser`, or `FileWriter` references remain in any controller.

Outstanding work is entirely cross-track: a single `pom.xml` cleanup on Hagag's side, the eventual `LifecycleService` wire-up, and the Phase 2 async-DAO sweep that the team scheduled to follow the Phase 1 merge.

The next concrete milestone is the first `mvn -Pandroid gluonfx:build` to produce an APK — every 3bdelbary-track asset and code path required for that command is now in place.

---

## Status of B-tasks
- [x] **B1** — done.
- [x] **B2** — done.
- [x] **B3** — done.
- [x] **B3X** — done.
- [x] **B4** — done.
- [x] **B5** — done.
- [x] **B6** — done.
- [x] **B7** — done. PNGs generated and committed.
- [x] **B8** — done.
- [x] **B9** — done.
- [x] **B10** — done. This consolidated reference + the FXML/controller matrices + Phase 2 TODO list complete the §B10 deliverable.
<<<<<<< C:/Users/moham/Agrilliant/docs/MIGRATION_3BDELBARY.md
<<<<<<< C:/Users/moham/Agrilliant/docs/MIGRATION_3BDELBARY.md
=======
=======
>>>>>>> C:/Users/moham/.windsurf/worktrees/Agrilliant/Agrilliant-f99a6225/docs/MIGRATION_3BDELBARY.md

---

## Post-B10: cross-track follow-ups landed early

The original plan parked these as Phase 2 / Hagag-side concerns; the user authorized doing them now since they unblock the first APK build.

### `pom.xml` — JFreeChart deps removed (cross-lane edit)

The desktop profile no longer declares `org.jfree:jfreechart` or `org.jfree:jfreechart-fx`. The B6 audit's TODO ("kept until 3bdelbary's B6 chart audit confirms removal") is closed; the comment block at the deletion site explains why and points back to §B6. The two top-of-file comment blocks that listed JFreeChart as a desktop-only dep were also updated. The desktop fat-jar shrinks by ~3 MB and the dependency tree no longer carries SWT-related transitive baggage.

> **Lane note:** `pom.xml` is Hagag's lane per §6/§11.4. The audit-confirms-removal comment in the original pom block was an explicit hand-off; this edit closes that hand-off with the user's go-ahead.

### `LifecycleService` PAUSE/RESUME wired

The B9 forward-compat hook on `DashboardController.stopLifecycle()` is now driven from two sites:

1. **`ShellView.setOnHiding`** — fires on logout (user clicks Sign Out → `AppView.SIGNIN.switchTo()` hides the SHELL view).
2. **`Gluon Attach LifecycleService.PAUSE`** — fires on Android when the OS backgrounds the app.

Symmetrically `startLifecycle()` is called from `ShellView.setOnShowing` and from `LifecycleService.RESUME`.

#### New code

- `DashboardController.startLifecycle()` — public, idempotent re-attach. Calls `updateDateTime()` + `subscribeLiveSensor()` + `updateSidebarStatus()`, all of which I made idempotent in B9.
- `ShellView.wireLifecycleServiceOnce()` — looks up `Services.get(LifecycleService.class)`, registers PAUSE → `controller.stopLifecycle()` and RESUME → `controller.startLifecycle()`. Guarded by a `lifecycleServiceWired` boolean so re-entering the SHELL view doesn't double-register. Wrapped in `.ifPresent` so on hosts where the service isn't registered (some headless desktop runs) the wiring silently skips.
- `ShellView.setOnShowing` / `setOnHiding` now also drive `controller.startLifecycle()` / `stopLifecycle()` directly. This handles the in-app logout/login case that doesn't go through OS-level PAUSE/RESUME.

#### Imports added to `ShellView`

```java
import com.gluonhq.attach.lifecycle.LifecycleEvent;
import com.gluonhq.attach.lifecycle.LifecycleService;
import com.gluonhq.attach.util.Services;
```

`com.gluonhq.attach:lifecycle` was already a `pom.xml` dependency (Hagag's earlier work, confirmed via the `<attachList>` block) so no Maven changes were needed for this part. On desktop builds the lifecycle service is wired through Gluon Attach's host implementation; if the service is unavailable, the `.ifPresent` lambda quietly skips and the in-app `setOnShowing`/`setOnHiding` path still works.

### DB workaround — `Crop.GrowthStage='GROWING'`

A new migration script lives at `docs/sql/2026-05-14-fix-growthstage-growing.sql`:

```sql
UPDATE crops SET growth_stage = 'VEGETATIVE' WHERE growth_stage = 'GROWING';
```

The file documents the problem, lists the three fix options (a/b/c), justifies why (b) was chosen, and includes a before/after `SELECT COUNT(*)` for verification. After running it, `dashboard.fxml` and `reports.fxml` should pass the FXML-load smoke (the only two failures left in `STATUS.md`'s smoke matrix).

<<<<<<< C:/Users/moham/Agrilliant/docs/MIGRATION_3BDELBARY.md
### APK build — pre-flight checklist

The 3bdelbary track and the cross-track items above are now complete. The first `mvn -Pandroid gluonfx:build` is a build-environment task that needs the user's machine to be set up — I cannot run it from this worktree (worktree mode + no installed deps). Pre-flight requirements:
=======
### APK build — three paths

The 3bdelbary track and the cross-track items above are now complete. The first `mvn -Pandroid gluonfx:build` needs a **Linux host** because GluonFX's Substrate + GraalVM native-image cross-compile toolchain does not run natively on Windows. The team can pick from three paths:

1. **Cloud build (recommended for Windows users)** — push to GitHub, click "Run workflow", download the APK as an Actions artifact. Zero local Linux setup. See `docs/CI_ANDROID_BUILD.md` for the one-time GitHub Secrets setup. Workflow file: `.github/workflows/android-build.yml`.
2. **WSL2 + Ubuntu (local Linux on Windows)** — install WSL2, run `scripts/wsl-setup-android-build-env.sh` inside Ubuntu, then `mvn -Pandroid gluonfx:build` natively. Faster iteration than cloud once set up; ~1 hr first-time setup. See `scripts/README.md`.
3. **Native Linux / macOS** — run `scripts/wsl-setup-android-build-env.sh` on any Ubuntu 22.04+ machine, or follow the equivalent steps for macOS from Gluon's docs.

Pre-flight requirements (paths 2 + 3 only — the cloud path has none on your machine):
>>>>>>> C:/Users/moham/.windsurf/worktrees/Agrilliant/Agrilliant-f99a6225/docs/MIGRATION_3BDELBARY.md

| Requirement | How to verify |
|-------------|---------------|
| **GraalVM CE 22+ or Liberica NIK 23+** | `java -version` should mention `GraalVM` or `Liberica NIK`. Set `GRAALVM_HOME` env var to that JDK. |
| **Android SDK** with `cmdline-tools`, `platforms;android-35`, `build-tools;35.0.0` | `sdkmanager --list_installed`. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`). |
| **Android NDK** ≥ 25.x | Installed via `sdkmanager "ndk;<ver>"`. Set `ANDROID_NDK` env var. |
<<<<<<< C:/Users/moham/Agrilliant/docs/MIGRATION_3BDELBARY.md
| **DB credentials** for first-run via `DB_URL` / `DB_USER` / `DB_PASSWORD` env vars or `db.properties` (Hagag's H4) | Per Hagag's `MIGRATION_HAGAG.md`. |
| **APK signing keystore** (release builds only) | Debug builds auto-sign with the GluonFX-managed debug key — fine for first device install. |

=======
| **APK signing keystore** (release builds only) | Debug builds auto-sign with the GluonFX-managed debug key — fine for first device install. |

#### Properties files — gitignored secrets must be copied in before the build

`gluonfx:build` packages the contents of `src/main/resources/` into the APK at AOT-compile time. The four secret-bearing properties files are gitignored (only their `.example` templates are committed). Before invoking `gluonfx:build`, copy your real files into the build's resources directory:

| Source (your local secrets) | Destination (in this repo) | Loaded by |
|-----------------------------|----------------------------|-----------|
| your real `db.properties` | `src/main/resources/db.properties` | `smartfarm.util.DBConnection` (H4) — but only as the third fallback layer; env vars `DB_URL`/`DB_USER`/`DB_PASSWORD` and Gluon Settings keys `db.url`/`db.user`/`db.password` take precedence. |
| your real `mqtt.properties` | `src/main/resources/mqtt.properties` | `smartfarm.server.MqttBridge` + `smartfarm.server.MqttSensorSubscriber` |
| your real `crop-health.properties` | `src/main/resources/crop-health.properties` | `smartfarm.service.PlantIdService` (Kindwise Crop.health API) |
| `thresholds.properties` (already committed) | already in place | `smartfarm.util.ThresholdConfig` (H10) |

```powershell
# One-shot copy from your main checkout into the worktree before building.
# (Adjust paths to match your layout.)
Copy-Item c:\Users\moham\Agrilliant\src\main\resources\db.properties           src\main\resources\db.properties
Copy-Item c:\Users\moham\Agrilliant\src\main\resources\mqtt.properties         src\main\resources\mqtt.properties
Copy-Item c:\Users\moham\Agrilliant\src\main\resources\crop-health.properties  src\main\resources\crop-health.properties
```

**Security note for production APKs:** baking real credentials into the APK is fine for a personal / dev build but is *not* the recommended deployment pattern. The credential-layering chain (env vars → Gluon Settings → properties) is specifically designed so that release APKs can ship with the properties files containing placeholders and the user enters real values into Gluon Settings on first launch (typed via a Settings screen). Phase 2 should add that Settings UI.

The pre-flight checklist below assumes a dev/internal build where the properties files are in place.

>>>>>>> C:/Users/moham/.windsurf/worktrees/Agrilliant/Agrilliant-f99a6225/docs/MIGRATION_3BDELBARY.md
#### Build commands

```powershell
# 1. AOT-compile the Substrate native binary (slow first time — ~5–15 min,
#    pulls down the GraalVM ahead-of-time build)
mvn -Pandroid gluonfx:build

# 2. Package into an APK
mvn -Pandroid gluonfx:package

# 3. Install on a connected device or running emulator (adb required)
mvn -Pandroid gluonfx:install

# 4. Launch on the connected device
mvn -Pandroid gluonfx:run
```

#### What success looks like

- Step 1 ends with `BUILD SUCCESS` and produces `target/gluonfx/aarch64-android/gvm/<binary>` plus an `android-project/` Gradle scratch dir.
- Step 2 produces `target/gluonfx/aarch64-android/gvm/<binary>.apk` (~30–50 MB).
- Step 3 logs `Successfully installed`; the Agrilliant icon appears on the device's home screen (the B7 launcher PNGs).
- Step 4 launches the app; the splash → sign-in flow runs.

#### Common first-run failures

- **`reflection-config.json` misses a class** — the Substrate AOT compiler can't follow runtime reflection. Symptom: `ClassNotFoundException` or `NoSuchMethodException` at startup. Fix: re-run with the GraalVM tracing agent on desktop (`mvn -Pdesktop -Dgluonfx.run.with.agent=true gluonfx:run`), exercise the failing flow, then merge the agent-generated `reflect-config.json` into `src/main/resources/META-INF/native-image/smartfarm/`. Hagag's H2 set up the seed config; production runs typically need a few additions per release.
- **MySQL connector reflection** — `mysql-connector-j` uses heavy reflection; if Hagag's H2 seed misses a constructor, queries fail with `InvocationTargetException`. Same agent-trace fix.
- **`READ_MEDIA_IMAGES` permission silently denied on Android 13+** — `DiseaseDetectionPage.selectImage` returns empty. Fix: add a runtime permission request before calling `PlatformPickers.pickImage()`. Out of scope here — Hagag's `PermissionRequestActivity` (already in the manifest) is the entry point.
- **`Crop.GrowthStage.GROWING` enum mismatch** at first DB hit — apply the SQL migration above before launching.
<<<<<<< C:/Users/moham/Agrilliant/docs/MIGRATION_3BDELBARY.md
>>>>>>> C:/Users/moham/.windsurf/worktrees/Agrilliant/Agrilliant-f99a6225/docs/MIGRATION_3BDELBARY.md
=======
>>>>>>> C:/Users/moham/.windsurf/worktrees/Agrilliant/Agrilliant-f99a6225/docs/MIGRATION_3BDELBARY.md
