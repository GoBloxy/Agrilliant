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

---

## Status of B3–B10
- [ ] **B3** — 12 FXML files made mobile-friendly. Touch targets, `ScrollPane` wrap, `TableView` → `CharmListView` where appropriate.
- [ ] **B4** — Sweep controllers: replace `FileChooser` (CSV export in Dashboard/Logs/Reports/Crop) with `CSVExporter.saveCsv(...)`. Replace `FileChooser.showOpenDialog` (DiseaseDetectionPage) with `PlatformPickers.pickImage()` (delivered in B8).
- [ ] **B5** — Add `css/mobile.css`. Keep AtlantaFX PrimerLight as base.
- [ ] **B6** — Drop any remaining JFreeChart use (already absent from `src/main/java`; double-check leftover imports in dashboard/monitoring).
- [ ] **B7** — Launcher icons mdpi → xxxhdpi under `src/android/res/mipmap-*/` to satisfy Hagag's manifest refs (`@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`).
- [ ] **B8** — Image picker via Gluon Attach `Pictures` (Hagag added the dep in `a911016`). Create `smartfarm.ui.platform.PlatformPickers` with `pickImage()` returning `File`.
- [ ] **B9** — Lifecycle hooks: `View#setOnShown`/`setOnHidden` instead of "set up on stage shown" patterns. (`SplashView` and `ShellView` already follow this; sweep the rest.)
- [ ] **B10** — Keep this file current as we go.
