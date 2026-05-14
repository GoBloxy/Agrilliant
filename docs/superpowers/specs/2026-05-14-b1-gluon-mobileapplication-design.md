# B1 — Convert `Main.java` to Gluon `MobileApplication` (Design Spec)

> **Track:** 3bdelbary (UI / Navigation / Resources)
> **Branch:** `android/3bdelbary`
> **Phase:** 1
> **Task:** §8.B1 of `ANDROID_MIGRATION.md`
> **Status:** Draft — pending user review before `writing-plans` invocation

---

## 1. Goal

Replace the existing JavaFX `Application`-based entry (`smartfarm.Main`) with a
Gluon `MobileApplication` that:

- Boots cleanly on the desktop profile (`mvn -Pdesktop javafx:run`) and shows
  the sign-in screen on a fresh start, or a Welcome stub on a session-restored
  start.
- Will boot cleanly on the Android profile (`mvn -Pandroid gluonfx:run`) once
  Hagag's H1 + H2 land. B1 does not itself prove the Android boot — only that
  the code structure supports it.
- Preserves the existing desktop UX surface (AtlantaFX PrimerLight theme,
  window title, icon, maximize, min size) for the desktop profile only.
- Has zero direct references to `smartfarm.server.FarmServer` that would break
  the Android compile when Hagag's H9 excludes that class from the Android
  Maven profile.

## 2. Success criteria (one line)

> The desktop build launches a Gluon `MobileApplication`, shows a logo splash
> for ≥ 800 ms, then displays either `signin.fxml` (no saved session) or a
> "Welcome, _name_" stub (saved session). `git diff --name-only main` shows
> only files in 3bdelbary's §6 lane.

## 3. Architecture overview

```
                            +-------------+
   AppManager (Gluon)  ---> | SPLASH_VIEW |  HOME — session restore router
                            +-------------+
                                  |
                       +----------+----------+
                       v                     v
                  +----------+         +-----------+
                  | SIGNIN   |  <----> | SIGNUP    |
                  +----------+         +-----------+
                       |
                       v (on successful login)
                  +-----------+
                  | SHELL     |  B2 will add NavigationDrawer + content area
                  +-----------+   that swaps 14 ShellContent enum values:
                                  HOME, CROPS, PLOTS, WORKERS, TASKS,
                                  ALERTS, MONITORING, HARVEST, REPORTS,
                                  LOGS, DISEASE, ATTENDANCE, ABOUT, SETTINGS
```

- **4 AppManager-level Views**: `SPLASH`, `SIGNIN`, `SIGNUP`, `SHELL`.
- **14 ShellContent identifiers** (enum, not Gluon Views): consumed by B2's
  `ShellController` for inner content swap.
- **Shared state**: a `NavContext` singleton holds the current `User` for
  cross-view hand-off.

## 4. Components

### 4.1 `smartfarm.ui.nav.AppView` (new enum)

Type-safe identifier for the 4 top-level Views.

```java
package smartfarm.ui.nav;

import com.gluonhq.charm.glisten.application.AppManager;

public enum AppView {
    SPLASH,
    SIGNIN,
    SIGNUP,
    SHELL;

    public void switchTo() {
        AppManager.getInstance().switchView(name());
    }
}
```

### 4.2 `smartfarm.ui.nav.ShellContent` (new enum)

Type-safe identifier for the 14 inner content panes. **No behaviour in B1** —
this is just the registry of names that B2 will wire to FXML pages.

```java
package smartfarm.ui.nav;

public enum ShellContent {
    HOME, CROPS, PLOTS, WORKERS, TASKS, ALERTS, MONITORING,
    HARVEST, REPORTS, LOGS, DISEASE, ATTENDANCE, ABOUT, SETTINGS
}
```

### 4.3 `smartfarm.ui.nav.NavContext` (new singleton)

Process-wide session state. Read-once-on-show pattern (not reactive bindings).

```java
package smartfarm.ui.nav;

import smartfarm.model.User;

public final class NavContext {
    private static final NavContext INSTANCE = new NavContext();
    private volatile User currentUser;

    private NavContext() { }

    public static NavContext get() { return INSTANCE; }
    public User getCurrentUser() { return currentUser; }
    public void setCurrentUser(User user) { this.currentUser = user; }
    public void clear() { this.currentUser = null; }
}
```

`volatile` ensures cross-thread visibility for the splash thread → FX thread
handoff. No `synchronized` block needed because no compound operations exist.

### 4.4 `smartfarm.ui.views.SplashView` (new)

Logo + spinner + async session restore. Routes to `SHELL` if a session was
restored, otherwise to `SIGNIN`. Honours a minimum 800 ms visible time so
users see the brand on a cold start with a hot DB connection.

Key behaviours:

- UI built programmatically (no FXML): logo `ImageView` + `ProgressIndicator`
  + "Loading…" label, centred in a `VBox`.
- `setOnShowing` hides the Gluon `AppBar`; `setOnHiding` restores it.
- Session restore runs on a single daemon `Thread` (not `runAsync`/Executor —
  Hagag's H4 introduces the async layer later; Phase 2 may refactor).
- On the FX thread, `NavContext.get().setCurrentUser(restored)` is set before
  the `switchTo(SHELL)` call so the Shell can read it in `setOnShowing`.

### 4.5 `smartfarm.ui.views.SignInView` (new)

Thin wrapper around the existing `signin.fxml`:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;

public class SignInView extends View {
    public SignInView() {
        try {
            Parent content = FXMLLoader.load(
                    getClass().getResource("/fxml/signin.fxml"));
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load signin.fxml", e);
        }
    }
}
```

The existing `SignInController` (referenced via `fx:controller` in
`signin.fxml`) keeps working unchanged. **B2 will rewrite its navigation
calls**; B1 does not modify any controller.

### 4.6 `smartfarm.ui.views.SignUpView` (new)

Identical pattern to `SignInView`, wraps `signup.fxml`. Existing
`SignUpController` works untouched until B2.

### 4.7 `smartfarm.ui.views.ShellView` (new — stub for B1)

A placeholder so the splash→shell route renders something. Shows
"Welcome, _name_" when a user is present in `NavContext`, plus a "Shell —
full UI coming in B2" label.

```java
public class ShellView extends View {
    public ShellView() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        setOnShowing(e -> {
            box.getChildren().clear();
            User u = NavContext.get().getCurrentUser();
            if (u != null) {
                box.getChildren().add(new Label("Welcome, " + u.getFullName()));
            }
            box.getChildren().add(new Label("Shell — full UI coming in B2"));
        });
        setCenter(box);
    }
}
```

This is intentionally minimal. **B2 will replace it** with a Gluon
`NavigationDrawer` + content area driven by `ShellContent`.

### 4.8 `smartfarm.Main` (rewritten)

```java
package smartfarm;

import atlantafx.base.theme.PrimerLight;
import com.gluonhq.attach.util.Platform;
import com.gluonhq.charm.glisten.application.MobileApplication;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;
import smartfarm.ui.views.ShellView;

public class Main extends MobileApplication {

    @Override
    public void init() {
        addViewFactory(AppView.SPLASH.name(), SplashView::new);
        addViewFactory(AppView.SIGNIN.name(), SignInView::new);
        addViewFactory(AppView.SIGNUP.name(), SignUpView::new);
        addViewFactory(AppView.SHELL.name(),  ShellView::new);
    }

    @Override
    public void postInit(Scene scene) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().add(getClass().getResource("/css/farm-theme.css").toExternalForm());

        if (Platform.isDesktop()) {
            applyDesktopWindowDefaults(scene);
            startFarmServerReflectively();
        }
    }

    private void applyDesktopWindowDefaults(Scene scene) {
        Stage stage = (Stage) scene.getWindow();
        stage.setTitle("Agrilliant — Smart Farm Management System");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        stage.setMinWidth(1300);
        stage.setMinHeight(820);
        stage.setMaximized(true);
    }

    // TODO(phase-2): replace Platform.isDesktop() with !Constants.IS_ANDROID
    // once Hagag's H9 lands. Reflective load avoids a compile-time dependency
    // on smartfarm.server.FarmServer, which H9 excludes from the Android profile.
    private void startFarmServerReflectively() {
        try {
            Class<?> cls = Class.forName("smartfarm.server.FarmServer");
            Object server = cls.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method start = cls.getMethod("start");
            Thread t = new Thread(() -> {
                try { start.invoke(server); }
                catch (Exception e) {
                    System.err.println("FarmServer crashed: " + e.getMessage());
                }
            }, "FarmServer-TCP");
            t.setDaemon(true);
            t.start();
        } catch (ClassNotFoundException e) {
            // Expected on Android build — class excluded from compile.
        } catch (Exception e) {
            System.err.println("Could not start FarmServer: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

### 4.9 `smartfarm.Launcher` (unchanged)

The desktop shaded-JAR module-system workaround. No changes for B1. Hagag's
`pom.xml` keeps `smartfarm.Launcher` as the `mainClass` for the desktop profile;
the Android profile uses `smartfarm.Main` directly so Launcher is never loaded
on Android.

## 5. Data flow

```
Cold start
    |
    v
Main.main()
    |
    v
Main.init()        --> register 4 View factories (lazy)
    |
    v
Main.postInit()    --> apply stylesheets, [desktop] window setup + FarmServer
    |
    v
SPLASH_VIEW shown  --> SplashView.setOnShowing
                          --> hide AppBar
                          --> kick off background session restore thread
                                  |
                                  v
                            SessionManager.loadSession()
                            AuthService.restoreSession()
                            [enforce min 800 ms visible]
                                  |
                                  v
                            Platform.runLater {
                              if (restored)  NavContext.set(user); SHELL.switchTo()
                              else           SIGNIN.switchTo()
                            }
```

## 6. Files touched

### New (7)

| Path | Lines |
|------|------:|
| `src/main/java/smartfarm/ui/nav/AppView.java` | ~20 |
| `src/main/java/smartfarm/ui/nav/ShellContent.java` | ~25 |
| `src/main/java/smartfarm/ui/nav/NavContext.java` | ~35 |
| `src/main/java/smartfarm/ui/views/SplashView.java` | ~70 |
| `src/main/java/smartfarm/ui/views/SignInView.java` | ~20 |
| `src/main/java/smartfarm/ui/views/SignUpView.java` | ~20 |
| `src/main/java/smartfarm/ui/views/ShellView.java` | ~30 |

### Modified (1)

| Path | Lines |
|------|------:|
| `src/main/java/smartfarm/Main.java` | ~95 (was 70) |

### Unchanged

- `src/main/java/smartfarm/Launcher.java`
- All 12 FXMLs (touched by B3)
- All 17 controllers (touched by B2)

## 7. Lane verification (§6 / §7 of `ANDROID_MIGRATION.md`)

| Check | Pass |
|-------|------|
| All paths under `Main.java`, `Launcher.java`, or `ui/**` | ✅ |
| No edits to `dao/`, `service/`, `server/` | ✅ |
| No edits to `pom.xml` | ✅ |
| No edits to `util/DBConnection.java`, `util/CSVExporter.java`, `util/Logger.java`, `util/ThresholdConfig.java` | ✅ |
| No edits to `META-INF/native-image/**` | ✅ |
| No edits to `model/` | ✅ (read-only `User` import) |
| `FarmServer` referenced reflectively only — no `import` statement | ✅ |

Read-only imports used in B1's new code (no source modifications):
- `smartfarm.service.SessionManager`
- `smartfarm.service.AuthService`
- `smartfarm.model.User`

## 8. Cross-track dependencies

**B1 cannot fully verify until Hagag's H1 lands on `main`.**

B1 imports `com.gluonhq.charm.glisten.application.MobileApplication`,
`com.gluonhq.charm.glisten.mvc.View`,
`com.gluonhq.charm.glisten.application.AppManager`, and
`com.gluonhq.attach.util.Platform`. These come from `gluonfx-maven-plugin`
+ Glisten + Attach Util dependencies that Hagag's H1 adds to `pom.xml`.

### Coordination plan

1. 3bdelbary writes all B1 code on `android/3bdelbary`.
2. Compile checks (`mvn test-compile`) fail until H1 lands — expected.
3. Once Hagag merges H1 to `main`, 3bdelbary rebases `android/3bdelbary`.
4. Run the full verification gate (§9).
5. Mark B1 complete in `MIGRATION_3BDELBARY.md`.

### Items that depend on Hagag (do NOT fix in B1)

- `Constants.IS_ANDROID` flag (H9) — B1 uses `Platform.isDesktop()` instead
  with a `TODO(phase-2)` marker for the eventual swap.
- GraalVM reflection config for `FarmServer` (H2) — not needed for desktop
  reflective load; only matters if Android ever needs FarmServer (it does not).
- `pom.xml` profiles `desktop` and `android` (H1) — B1 assumes their existence
  for the verification commands.

## 9. Verification gate

```powershell
# After Hagag's H1 lands on main and android/3bdelbary is rebased:
cd c:\Users\moham\Agrilliant-1
git checkout android/3bdelbary

# 1. Compile only
mvn -Pdesktop test-compile                       # MUST exit 0

# 2. Lane diff — expected: 8 source paths + 2 docs paths
git diff --name-only main..HEAD
#   Source files (8):
#     src/main/java/smartfarm/Main.java                         (modified)
#     src/main/java/smartfarm/ui/nav/AppView.java               (new)
#     src/main/java/smartfarm/ui/nav/NavContext.java            (new)
#     src/main/java/smartfarm/ui/nav/ShellContent.java          (new)
#     src/main/java/smartfarm/ui/views/SignInView.java          (new)
#     src/main/java/smartfarm/ui/views/SignUpView.java          (new)
#     src/main/java/smartfarm/ui/views/SplashView.java          (new)
#     src/main/java/smartfarm/ui/views/ShellView.java           (new)
#   Docs (2):
#     docs/MIGRATION_3BDELBARY.md
#     docs/superpowers/specs/2026-05-14-b1-gluon-mobileapplication-design.md

# 3. Desktop run — no saved session
mvn -Pdesktop javafx:run
#   Expected: splash visible ≥ 800 ms → signin.fxml renders

# 4. Desktop run — with saved session
#    (Sign in first, close app cleanly, run again)
mvn -Pdesktop javafx:run
#   Expected: splash visible ≥ 800 ms → "Welcome, <name>" stub renders
```

Visual checks during run:

- AppBar (Gluon top bar) hidden during splash, visible after.
- Splash visible for at least 800 ms even on a fast DB connection.
- No JavaFX or Gluon stacktrace printed to stderr.
- Existing `farm-theme.css` styles still apply to the sign-in screen.
- `FarmServer-TCP` daemon thread shows up in `jstack` (proves the reflective
  load worked on desktop).

## 10. Out of scope (deferred)

| Concern | Deferred to |
|---------|-------------|
| Controller navigation rewrites | B2 |
| Mobile-friendly FXMLs | B3 |
| Controller adaptation (FileChooser, Stage refs) | B4 |
| `mobile.css` | B5 |
| Charts on mobile | B6 |
| Launcher icons + splash drawable | B7 |
| Image picker via Gluon Attach `Pictures` | B8 |
| Lifecycle hooks across all views | B9 |
| `ShellView` `NavigationDrawer` + content swap | B2 |
| 4 missing FXMLs (Disease, Attendance, About, Settings) | B3 |
| Hiding fingerprint button on Android | B4 |
| Async DAO wrapping | Phase 2 |
| GraalVM reflection config | Hagag H2 |

## 11. Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Hagag's H1 not landed when B1 code is ready | High | Plan rebase after H1 merges to `main` (§8) |
| `SplashView.setOnShowing` re-firing if SPLASH is re-entered later | Low (SPLASH is HOME, never explicitly switched-to in B1) | Add `private volatile boolean restoreStarted` guard during implementation (writing-plans will list this) |
| `AuthService.restoreSession()` blocks on slow MySQL | Medium | Out of scope for B1; document in `MIGRATION_3BDELBARY.md` as known degradation |
| Reflective `FarmServer` load hidden by GraalVM AOT | Zero | Guarded by `Platform.isDesktop()` — reflection only runs on desktop JRE, not on AOT'd Android binary |
| AtlantaFX `setUserAgentStylesheet` conflicts with Glisten's own stylesheet | Medium | Confirm during verification; if conflict, move stylesheet application earlier in init() |
| `postInit(Scene)` not called as expected on older Gluon versions | Low | Pin Gluon version with Hagag in H1; fall back to listening on `scene.windowProperty()` if needed |

## 12. Decision log

| # | Decision | Chosen | Rationale |
|---|----------|--------|-----------|
| Q1 | Navigation architecture | **C. Hybrid** (3 top-level Views + Shell with nested content via NavigationDrawer in B2) | Idiomatic mobile UX without rewriting every controller's API |
| Q2 | Where to do session restore | **A. Splash view decides** | Cleanest separation; Main stays small; no flicker |
| Q3 | Cross-view state passing | **A. NavContext singleton** | Matches migration doc §B2; pure Java; testable |
| Approach | Overall structure | **Approach 1 with tweaks** (lazy registration, Splash with min visible time, FarmServer in Main with reflection) | User-selected; documented trade-offs vs Launcher-owns variant |
| Tweak 1 | View constants form | **Enum** (`AppView`, `ShellContent`) with `.name()` at API boundary | Type-safe; pays the cost once per call site |
| Tweak 2 | FarmServer location | **In `Main.postInit` via reflection** | User preferred over moving to Launcher; reflection necessary because Hagag's H9 excludes FarmServer from Android compile |
| Tweak 3 | Splash UX | **Minimum 800 ms visible** | Avoids flicker on hot-DB cold starts |

## 13. Next step

After this spec is reviewed and approved, invoke the `writing-plans` skill to
produce the implementation plan: a checklist of 2–5-minute tasks with exact
file paths and acceptance criteria per task.
