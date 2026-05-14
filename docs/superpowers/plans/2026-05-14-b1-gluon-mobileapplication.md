# B1 — Convert Main.java to Gluon MobileApplication — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace JavaFX `Application`-based entry with a Gluon `MobileApplication` that boots cleanly on desktop, shows splash → signin (or splash → welcome stub if session restored), and is structurally ready for Android once Hagag's tracks (H1, H2, H9) land.

**Architecture:** 4 AppManager-level Views (`SPLASH`, `SIGNIN`, `SIGNUP`, `SHELL`) registered lazily in `Main.init()`. Splash is `HOME_VIEW`, does async session restore, then routes to `SHELL` or `SIGNIN`. Shared state lives in a `NavContext` singleton. `Main.postInit()` applies AtlantaFX styles to both targets and desktop-only window defaults + reflective `FarmServer` launch behind a `Platform.isDesktop()` guard.

**Tech Stack:** Java 17, JavaFX 21.0.2, AtlantaFX 2.0.1, Gluon Glisten + Attach Util (added by Hagag's H1), MySQL JDBC, Maven.

**Branch:** `android/3bdelbary` (already created, branched from `mobile-app`).

**Spec:** `docs/superpowers/specs/2026-05-14-b1-gluon-mobileapplication-design.md`

---

## Notes on TDD pattern

The project has **no JUnit/Surefire/TestFX in pom.xml** as of this writing. TDD with automated tests is therefore not viable for B1 — Hagag owns `pom.xml` per §6 of the migration doc, so 3bdelbary cannot add a test framework. Each task in this plan instead uses:

- **Syntactic verification** during creation (IDE shows no red squiggles)
- **Compile verification** via `mvn -Pdesktop test-compile` after each task that adds testable code (only works post-H1)
- **Manual runtime verification** at the end (Task 14, 15)

A `// TODO(phase-2): add unit tests` marker is included in `NavContext` so the testing gap is not forgotten.

---

## Task 0: Verify cross-track prerequisite (Hagag's H1)

**Files:** _(read-only)_ `pom.xml`

This is a precondition gate. **Do not start Task 1 until this passes.**

- [ ] **Step 1: Confirm H1 has merged to `main` and your branch is rebased**

Run:
```powershell
cd c:\Users\moham\Agrilliant-1
git fetch origin
git log --oneline origin/main | Select-Object -First 10
```
Look for a commit message containing `[hagag]` and one of: `gluonfx`, `Gluon`, `H1`, `MobileApplication`. If you see it, H1 is on main.

- [ ] **Step 2: Rebase `android/3bdelbary` onto `main`**

Run:
```powershell
git checkout android/3bdelbary
git rebase origin/main
```
Resolve any conflicts (there should be none if you haven't started B1 yet).

- [ ] **Step 3: Verify Gluon deps are visible in pom.xml**

Run:
```powershell
Select-String -Path pom.xml -Pattern "gluonfx|charm-glisten|attach"
```
Expected output: at least 3 matches (gluonfx-maven-plugin, charm-glisten dependency, attach-util dependency).

- [ ] **Step 4: Verify desktop profile compiles**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0, no errors.

**If any of Steps 1–4 fail:** STOP. H1 is not ready. Wait for Hagag, or proceed with code-only tasks (1–11) but skip all `mvn` commands until H1 lands.

---

## Task 1: Create `NavContext` singleton

**Files:**
- Create: `src/main/java/smartfarm/ui/nav/NavContext.java`

- [ ] **Step 1: Create the new directory**

Run:
```powershell
New-Item -ItemType Directory -Force -Path src\main\java\smartfarm\ui\nav | Out-Null
```

- [ ] **Step 2: Write `NavContext.java`**

Create file `src/main/java/smartfarm/ui/nav/NavContext.java` with these exact contents:

```java
package smartfarm.ui.nav;

import smartfarm.model.User;

/**
 * Process-wide navigation and session state shared between Views.
 * <p>
 * Reads and writes are thread-safe via {@code volatile}. The class holds
 * read-once-on-show data only; for live UI updates use
 * {@code Platform.runLater} after writes from background threads.
 * <p>
 * Phase 2 is expected to add more shared state here (selected plot,
 * selected crop, navigation hints, etc.) as controllers are refactored.
 *
 * TODO(phase-2): add unit tests once Hagag adds JUnit/Surefire to pom.xml.
 */
public final class NavContext {

    private static final NavContext INSTANCE = new NavContext();

    private volatile User currentUser;

    private NavContext() { }

    public static NavContext get() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /** Clear all session-bound state. Call on logout. */
    public void clear() {
        this.currentUser = null;
    }
}
```

- [ ] **Step 3: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0. (If H1 has not landed: skip this step and rely on IDE syntax check.)

- [ ] **Step 4: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/nav/NavContext.java
git commit -m "[3bdelbary] B1.1 add NavContext singleton for cross-View session state"
```

---

## Task 2: Create `AppView` enum

**Files:**
- Create: `src/main/java/smartfarm/ui/nav/AppView.java`

- [ ] **Step 1: Write `AppView.java`**

Create file `src/main/java/smartfarm/ui/nav/AppView.java` with these exact contents:

```java
package smartfarm.ui.nav;

import com.gluonhq.charm.glisten.application.AppManager;

/**
 * Type-safe identifiers for the 4 top-level Views registered with Gluon's
 * {@link com.gluonhq.charm.glisten.application.AppManager}.
 * <p>
 * Use {@link #switchTo()} as the idiomatic way to navigate:
 * <pre>AppView.SHELL.switchTo();</pre>
 */
public enum AppView {
    SPLASH,
    SIGNIN,
    SIGNUP,
    SHELL;

    /** Switch the current AppManager view to this one. */
    public void switchTo() {
        AppManager.getInstance().switchView(name());
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/nav/AppView.java
git commit -m "[3bdelbary] B1.2 add AppView enum (4 top-level Gluon Views)"
```

---

## Task 3: Create `ShellContent` enum

**Files:**
- Create: `src/main/java/smartfarm/ui/nav/ShellContent.java`

- [ ] **Step 1: Write `ShellContent.java`**

Create file `src/main/java/smartfarm/ui/nav/ShellContent.java` with these exact contents:

```java
package smartfarm.ui.nav;

/**
 * Type-safe identifiers for the inner content panes rendered inside
 * {@code SHELL_VIEW}.
 * <p>
 * These are NOT Gluon Views — they are content slots that B2's
 * {@code ShellController} will wire to FXML pages via content swap.
 * <p>
 * B1 only declares the names; no code consumes this enum yet.
 */
public enum ShellContent {
    HOME,
    CROPS,
    PLOTS,
    WORKERS,
    TASKS,
    ALERTS,
    MONITORING,
    HARVEST,
    REPORTS,
    LOGS,
    DISEASE,
    ATTENDANCE,
    ABOUT,
    SETTINGS
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/nav/ShellContent.java
git commit -m "[3bdelbary] B1.3 add ShellContent enum (14 inner content slots for B2)"
```

---

## Task 4: Create `SignInView`

**Files:**
- Create: `src/main/java/smartfarm/ui/views/SignInView.java`

- [ ] **Step 1: Create the new directory**

Run:
```powershell
New-Item -ItemType Directory -Force -Path src\main\java\smartfarm\ui\views | Out-Null
```

- [ ] **Step 2: Write `SignInView.java`**

Create file `src/main/java/smartfarm/ui/views/SignInView.java` with these exact contents:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * Gluon {@link View} wrapping the existing {@code signin.fxml}.
 * <p>
 * The existing {@code SignInController} (referenced via {@code fx:controller}
 * in the FXML) keeps working unchanged. B2 will rewrite the controller's
 * navigation calls to use {@link smartfarm.ui.nav.AppView#switchTo()}.
 */
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

- [ ] **Step 3: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 4: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/views/SignInView.java
git commit -m "[3bdelbary] B1.4 add SignInView (wraps existing signin.fxml)"
```

---

## Task 5: Create `SignUpView`

**Files:**
- Create: `src/main/java/smartfarm/ui/views/SignUpView.java`

- [ ] **Step 1: Write `SignUpView.java`**

Create file `src/main/java/smartfarm/ui/views/SignUpView.java` with these exact contents:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * Gluon {@link View} wrapping the existing {@code signup.fxml}.
 * <p>
 * The existing {@code SignUpController} keeps working unchanged. B2 will
 * rewrite the controller's navigation calls.
 */
public class SignUpView extends View {

    public SignUpView() {
        try {
            Parent content = FXMLLoader.load(
                    getClass().getResource("/fxml/signup.fxml"));
            setCenter(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load signup.fxml", e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/views/SignUpView.java
git commit -m "[3bdelbary] B1.5 add SignUpView (wraps existing signup.fxml)"
```

---

## Task 6: Create `ShellView` (B1 stub)

**Files:**
- Create: `src/main/java/smartfarm/ui/views/ShellView.java`

This is intentionally a stub — B2 replaces it with the real NavigationDrawer + content area.

- [ ] **Step 1: Write `ShellView.java`**

Create file `src/main/java/smartfarm/ui/views/ShellView.java` with these exact contents:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.mvc.View;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import smartfarm.model.User;
import smartfarm.ui.nav.NavContext;

/**
 * B1 STUB: Welcome screen that proves the splash → shell pipeline works.
 * <p>
 * Reads the current user from {@link NavContext} on each show and displays
 * a "Welcome, &lt;name&gt;" message.
 * <p>
 * TODO(B2): replace with a Gluon NavigationDrawer + content area driven by
 * {@link smartfarm.ui.nav.ShellContent}.
 */
public class ShellView extends View {

    private final VBox box;

    public ShellView() {
        this.box = new VBox(12);
        this.box.setAlignment(Pos.CENTER);
        setCenter(this.box);

        setOnShowing(e -> refreshContent());
    }

    private void refreshContent() {
        this.box.getChildren().clear();
        User u = NavContext.get().getCurrentUser();
        if (u != null) {
            this.box.getChildren().add(new Label("Welcome, " + u.getFullName()));
        }
        this.box.getChildren().add(new Label("Shell — full UI coming in B2"));
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/views/ShellView.java
git commit -m "[3bdelbary] B1.6 add ShellView stub (B2 will replace with NavigationDrawer)"
```

---

## Task 7: Create `SplashView` — UI layout only

**Files:**
- Create: `src/main/java/smartfarm/ui/views/SplashView.java`

Task 7 creates only the UI layout. Task 8 adds the session-restore logic so the file is built in two clean commits.

- [ ] **Step 1: Write `SplashView.java`**

Create file `src/main/java/smartfarm/ui/views/SplashView.java` with these exact contents:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

/**
 * Splash screen + session-restore router. Set as HOME_VIEW.
 * <p>
 * On show: hides the Gluon AppBar, then kicks off the async session restore.
 * On hide: restores the AppBar.
 * <p>
 * Routes to {@link smartfarm.ui.nav.AppView#SHELL} if a session was
 * restored, otherwise to {@link smartfarm.ui.nav.AppView#SIGNIN}.
 * <p>
 * Honours a minimum 800 ms visible time so the brand is visible even on
 * a hot-DB cold start.
 */
public class SplashView extends View {

    static final long MIN_VISIBLE_MS = 800;

    private volatile boolean restoreStarted = false;

    public SplashView() {
        ImageView logo = new ImageView(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        logo.setFitWidth(200);
        logo.setPreserveRatio(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        VBox box = new VBox(24, logo, spinner, new Label("Loading…"));
        box.setAlignment(Pos.CENTER);
        setCenter(box);

        setOnShowing(e -> {
            AppManager.getInstance().getAppBar().setVisible(false);
            // Session restore logic added in Task 8
        });
        setOnHiding(e -> AppManager.getInstance().getAppBar().setVisible(true));
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/views/SplashView.java
git commit -m "[3bdelbary] B1.7 add SplashView UI layout (logo + spinner, no logic yet)"
```

---

## Task 8: Add session-restore logic to `SplashView`

**Files:**
- Modify: `src/main/java/smartfarm/ui/views/SplashView.java`

- [ ] **Step 1: Add imports and the `kickOffSessionRestore()` method**

Replace the **entire** file `src/main/java/smartfarm/ui/views/SplashView.java` with:

```java
package smartfarm.ui.views;

import com.gluonhq.charm.glisten.application.AppManager;
import com.gluonhq.charm.glisten.mvc.View;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import smartfarm.model.User;
import smartfarm.service.AuthService;
import smartfarm.service.SessionManager;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.nav.NavContext;

/**
 * Splash screen + session-restore router. Set as HOME_VIEW.
 * <p>
 * On show: hides the Gluon AppBar, then kicks off the async session restore.
 * On hide: restores the AppBar.
 * <p>
 * Routes to {@link AppView#SHELL} if a session was restored, otherwise to
 * {@link AppView#SIGNIN}.
 * <p>
 * Honours a minimum 800 ms visible time so the brand is visible even on
 * a hot-DB cold start.
 */
public class SplashView extends View {

    static final long MIN_VISIBLE_MS = 800;

    private volatile boolean restoreStarted = false;

    public SplashView() {
        ImageView logo = new ImageView(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        logo.setFitWidth(200);
        logo.setPreserveRatio(true);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        VBox box = new VBox(24, logo, spinner, new Label("Loading…"));
        box.setAlignment(Pos.CENTER);
        setCenter(box);

        setOnShowing(e -> {
            AppManager.getInstance().getAppBar().setVisible(false);
            kickOffSessionRestore();
        });
        setOnHiding(e -> AppManager.getInstance().getAppBar().setVisible(true));
    }

    private void kickOffSessionRestore() {
        if (restoreStarted) {
            return; // guard: only run once per JVM
        }
        restoreStarted = true;

        long start = System.currentTimeMillis();

        Thread t = new Thread(() -> {
            User restored = null;
            String savedEmail = SessionManager.loadSession();
            if (savedEmail != null) {
                try {
                    restored = new AuthService().restoreSession(savedEmail);
                } catch (Exception ex) {
                    SessionManager.clearSession();
                }
            }

            // Honour minimum splash visible time
            long elapsed = System.currentTimeMillis() - start;
            long remaining = MIN_VISIBLE_MS - elapsed;
            if (remaining > 0) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            final User finalUser = restored;
            Platform.runLater(() -> {
                if (finalUser != null) {
                    NavContext.get().setCurrentUser(finalUser);
                    AppView.SHELL.switchTo();
                } else {
                    AppView.SIGNIN.switchTo();
                }
            });
        }, "splash-session-restore");
        t.setDaemon(true);
        t.start();
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/ui/views/SplashView.java
git commit -m "[3bdelbary] B1.8 wire SplashView session restore with 800ms min-time guard"
```

---

## Task 9: Rewrite `Main.java` — Gluon entry + view factories

**Files:**
- Modify: `src/main/java/smartfarm/Main.java`

Task 9 introduces the new `Main` with `init()` only. Task 10 adds `postInit()` window setup. Task 11 adds reflective FarmServer. Three commits, three clean concerns.

- [ ] **Step 1: Back up current `Main.java`**

(Skip — `git` is the backup. Just proceed.)

- [ ] **Step 2: Replace `Main.java` with the Gluon entry skeleton**

Replace the **entire** file `src/main/java/smartfarm/Main.java` with:

```java
package smartfarm;

import com.gluonhq.charm.glisten.application.MobileApplication;
import smartfarm.ui.nav.AppView;
import smartfarm.ui.views.ShellView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 * <p>
 * Registers 4 lazy View factories in {@link #init()}. {@code postInit(Scene)}
 * applies stylesheets and, on desktop only, configures window defaults and
 * starts the {@code FarmServer} TCP ingress (Tasks 10–11).
 *
 * <p>The desktop entry path is {@code smartfarm.Launcher} → {@code Main.main()};
 * the Android entry path is {@code smartfarm.Main} directly via GluonFX.
 */
public class Main extends MobileApplication {

    @Override
    public void init() {
        addViewFactory(AppView.SPLASH.name(), SplashView::new);
        addViewFactory(AppView.SIGNIN.name(), SignInView::new);
        addViewFactory(AppView.SIGNUP.name(), SignUpView::new);
        addViewFactory(AppView.SHELL.name(),  ShellView::new);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 3: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 4: Commit**

Run:
```powershell
git add src/main/java/smartfarm/Main.java
git commit -m "[3bdelbary] B1.9 convert Main to Gluon MobileApplication with 4 view factories"
```

---

## Task 10: Add `postInit()` — stylesheets + desktop window defaults

**Files:**
- Modify: `src/main/java/smartfarm/Main.java`

- [ ] **Step 1: Add `postInit()` and `applyDesktopWindowDefaults()` to `Main.java`**

Replace the **entire** file `src/main/java/smartfarm/Main.java` with:

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
import smartfarm.ui.views.ShellView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 * <p>
 * Registers 4 lazy View factories in {@link #init()}. {@link #postInit(Scene)}
 * applies stylesheets and, on desktop only, configures window defaults.
 * Task 11 adds reflective {@code FarmServer} launch.
 */
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
        // Stylesheets — both targets
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().add(
                getClass().getResource("/css/farm-theme.css").toExternalForm());
        // mobile.css added in B5

        // Desktop-only: window setup
        if (Platform.isDesktop()) {
            applyDesktopWindowDefaults(scene);
        }
    }

    private void applyDesktopWindowDefaults(Scene scene) {
        Stage stage = (Stage) scene.getWindow();
        stage.setTitle("Agrilliant — Smart Farm Management System");
        stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        stage.setMinWidth(1300);
        stage.setMinHeight(820);
        stage.setMaximized(true);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/Main.java
git commit -m "[3bdelbary] B1.10 add Main.postInit stylesheets + desktop window defaults"
```

---

## Task 11: Add reflective `FarmServer` launch to `Main.postInit()`

**Files:**
- Modify: `src/main/java/smartfarm/Main.java`

- [ ] **Step 1: Add `startFarmServerReflectively()` and call it from `postInit()`**

Replace the **entire** file `src/main/java/smartfarm/Main.java` with:

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
import smartfarm.ui.views.ShellView;
import smartfarm.ui.views.SignInView;
import smartfarm.ui.views.SignUpView;
import smartfarm.ui.views.SplashView;

/**
 * Gluon {@link MobileApplication} entry for both desktop and Android.
 * <p>
 * Registers 4 lazy View factories in {@link #init()}. {@link #postInit(Scene)}
 * applies stylesheets and, on desktop only, configures window defaults and
 * starts the {@code FarmServer} TCP ingress via reflection.
 *
 * <p>The reflective load avoids a compile-time dependency on
 * {@code smartfarm.server.FarmServer}, which Hagag's H9 excludes from the
 * Android Maven profile.
 */
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
        // Stylesheets — both targets
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scene.getStylesheets().add(
                getClass().getResource("/css/farm-theme.css").toExternalForm());
        // mobile.css added in B5

        // Desktop-only: window setup + FarmServer
        if (Platform.isDesktop()) {
            applyDesktopWindowDefaults(scene);
            startFarmServerReflectively();
        }
    }

    private void applyDesktopWindowDefaults(Scene scene) {
        Stage stage = (Stage) scene.getWindow();
        stage.setTitle("Agrilliant — Smart Farm Management System");
        stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/images/logo-dark.png")));
        stage.setMinWidth(1300);
        stage.setMinHeight(820);
        stage.setMaximized(true);
    }

    /**
     * Starts the ESP32 ingress server on desktop builds only.
     * <p>
     * Reflective load avoids a compile-time dependency on
     * {@code smartfarm.server.FarmServer}, which Hagag's H9 excludes from
     * the Android Maven profile.
     *
     * TODO(phase-2): Replace {@code Platform.isDesktop()} with Hagag's
     * {@code Constants.IS_ANDROID} flag (negated) once H9 lands.
     */
    private void startFarmServerReflectively() {
        try {
            Class<?> cls = Class.forName("smartfarm.server.FarmServer");
            Object server = cls.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method start = cls.getMethod("start");

            Thread t = new Thread(() -> {
                try {
                    start.invoke(server);
                } catch (Exception e) {
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

- [ ] **Step 2: Compile check**

Run:
```powershell
mvn -Pdesktop test-compile -q
```
Expected: exit code 0.

- [ ] **Step 3: Commit**

Run:
```powershell
git add src/main/java/smartfarm/Main.java
git commit -m "[3bdelbary] B1.11 start FarmServer reflectively on desktop (skips on Android)"
```

---

## Task 12: Full project compile gate

**Files:** _(no edits)_

- [ ] **Step 1: Clean and recompile the whole project**

Run:
```powershell
mvn -Pdesktop clean test-compile
```
Expected: `BUILD SUCCESS`, exit code 0. No warnings about missing classes.

- [ ] **Step 2: If errors appear, halt and fix before continuing**

Common errors and fixes:

| Error | Cause | Fix |
|-------|-------|-----|
| `package com.gluonhq.charm.glisten... does not exist` | H1 not on `main` or not rebased | Run Task 0 again |
| `cannot find symbol class MobileApplication` | Same as above | Same as above |
| `cannot find symbol class User` | Path issue or `model/` was modified | `git diff main..HEAD -- src/main/java/smartfarm/model/` should be empty |
| `package smartfarm.ui.views does not exist` | Files in wrong directory | `Get-ChildItem src/main/java/smartfarm/ui/views -Recurse` should list 4 .java files |

- [ ] **Step 3: No commit needed for this task** — verification gate only.

---

## Task 13: Manual run — no saved session

**Files:** _(no edits)_

- [ ] **Step 1: Delete any existing session file**

Run:
```powershell
Remove-Item "$env:USERPROFILE\.agrilliant\session" -ErrorAction SilentlyContinue
```
(The session file path comes from `SessionManager`. Adjust path if `SessionManager` uses a different location.)

- [ ] **Step 2: Run the desktop build**

Run:
```powershell
mvn -Pdesktop javafx:run
```

- [ ] **Step 3: Observe**

Expected sequence:
1. Window opens, maximized.
2. **Splash visible**: dark logo + spinner + "Loading…" — for **at least 800 ms**.
3. Splash hides; AppBar appears.
4. **Sign-in screen renders** (`signin.fxml` content with email/password fields).
5. No JavaFX or Gluon stacktrace printed to stderr.
6. The window title reads "Agrilliant — Smart Farm Management System".

- [ ] **Step 4: Verify FarmServer thread started**

In another terminal while the app is still running:
```powershell
jcmd $(jps -l | Select-String "smart-farm|Launcher|Main" | ForEach-Object { ($_ -split " ")[0] }) Thread.print | Select-String "FarmServer-TCP"
```
Expected: one matching line showing the daemon thread is alive.

- [ ] **Step 5: Close the app cleanly** (window close button — DO NOT sign in yet).

- [ ] **Step 6: No commit needed** — manual verification only.

---

## Task 14: Manual run — saved session restored

**Files:** _(no edits)_

- [ ] **Step 1: Generate a saved session**

Restart the app:
```powershell
mvn -Pdesktop javafx:run
```
Wait for sign-in screen. Sign in with valid credentials. The existing `SessionManager.saveSession(email)` call inside `SignInController.onSignIn` writes the session file.

Close the app cleanly via the window close button.

- [ ] **Step 2: Restart the app**

```powershell
mvn -Pdesktop javafx:run
```

- [ ] **Step 3: Observe**

Expected sequence:
1. Window opens, maximized.
2. Splash visible for ≥ 800 ms.
3. Splash hides.
4. **"Welcome, <your name>"** label appears (from the `ShellView` stub).
5. Below it: "Shell — full UI coming in B2".

If you see the sign-in screen instead, the session was not restored. Check that `SessionManager.loadSession()` returns the saved email — this is `service/` code (Hagag's lane); do not fix here, file a TODO.

- [ ] **Step 4: Close the app.**

- [ ] **Step 5: No commit needed** — manual verification only.

---

## Task 15: Lane diff check

**Files:** _(no edits)_

- [ ] **Step 1: Run the lane diff**

Run:
```powershell
git fetch origin
git diff --name-only origin/main..HEAD
```

Expected output (8 source files + 0–2 docs files, depending on if Task 16 has been done yet):
```
src/main/java/smartfarm/Main.java
src/main/java/smartfarm/ui/nav/AppView.java
src/main/java/smartfarm/ui/nav/NavContext.java
src/main/java/smartfarm/ui/nav/ShellContent.java
src/main/java/smartfarm/ui/views/ShellView.java
src/main/java/smartfarm/ui/views/SignInView.java
src/main/java/smartfarm/ui/views/SignUpView.java
src/main/java/smartfarm/ui/views/SplashView.java
```

- [ ] **Step 2: Confirm no forbidden paths**

Run:
```powershell
git diff --name-only origin/main..HEAD | Select-String -Pattern "^(pom\.xml|src/main/java/smartfarm/(dao|service|server|model)/|src/main/java/smartfarm/util/(DBConnection|CSVExporter|Logger|ThresholdConfig)|src/main/resources/META-INF/)"
```
Expected: **no output** (empty match list).

If anything matches, you've crossed the lane — revert those changes before proceeding.

- [ ] **Step 3: No commit needed** — verification only.

---

## Task 16: Create `MIGRATION_3BDELBARY.md`

**Files:**
- Create: `docs/MIGRATION_3BDELBARY.md`

- [ ] **Step 1: Write the migration notes**

Create file `docs/MIGRATION_3BDELBARY.md` with these exact contents:

```markdown
# 3bdelbary — Phase 1 Migration Notes

> Track: UI / Navigation / Resources (§8.B of `ANDROID_MIGRATION.md`)
> Branch: `android/3bdelbary`

## B1: Convert Main.java to Gluon MobileApplication — DONE ✅

**Spec:** `docs/superpowers/specs/2026-05-14-b1-gluon-mobileapplication-design.md`
**Plan:** `docs/superpowers/plans/2026-05-14-b1-gluon-mobileapplication.md`

### Files created
- `src/main/java/smartfarm/ui/nav/AppView.java` — 4-value enum for AppManager Views
- `src/main/java/smartfarm/ui/nav/ShellContent.java` — 14-value enum for inner content (B2 consumes)
- `src/main/java/smartfarm/ui/nav/NavContext.java` — singleton session state
- `src/main/java/smartfarm/ui/views/SplashView.java` — logo + async session restore router
- `src/main/java/smartfarm/ui/views/SignInView.java` — wraps `signin.fxml`
- `src/main/java/smartfarm/ui/views/SignUpView.java` — wraps `signup.fxml`
- `src/main/java/smartfarm/ui/views/ShellView.java` — Welcome stub (B2 replaces)

### Files modified
- `src/main/java/smartfarm/Main.java` — extends Gluon `MobileApplication`, lazy view factories, `postInit` with desktop window defaults + reflective `FarmServer`.

### Cross-track interactions / TODOs
- **TODO(phase-2):** Replace `com.gluonhq.attach.util.Platform.isDesktop()` in `Main.startFarmServerReflectively` with `!Constants.IS_ANDROID` once Hagag's H9 lands.
- **NOTE:** The migration doc §6 lists `src/main/resources/views/**` as a path for "Gluon-style View classes". Java classes belong in `src/main/java/`, so I placed them in `src/main/java/smartfarm/ui/views/` instead (covered by `src/main/java/smartfarm/ui/**` in §6). No `src/main/resources/views/` directory was created.
- **NOTE:** No JUnit/Surefire is in `pom.xml`. `NavContext` carries a `TODO(phase-2)` marker for unit tests once Hagag adds the test framework.
- **NOTE:** The desktop profile's Maven plugin mainClass should remain `smartfarm.Launcher` (no change needed — Hagag's H1 configures this).
- **KNOWN DEGRADATION:** `AuthService.restoreSession()` is synchronous and could hang the splash on a slow MySQL. Phase 2 / Hagag's H4 will introduce async wrappers.

### Status of B2–B10
- [ ] B2 — Stage navigation → AppManager
- [ ] B3 — Mobile-friendly FXMLs
- [ ] B4 — Adapt controllers
- [ ] B5 — `mobile.css`
- [ ] B6 — Charts on mobile
- [ ] B7 — Splash + icons (Android densities)
- [ ] B8 — Image picker (Gluon Attach `Pictures`)
- [ ] B9 — Lifecycle hooks
- [ ] B10 — These notes (this file)
```

- [ ] **Step 2: Commit**

Run:
```powershell
git add docs/MIGRATION_3BDELBARY.md
git commit -m "[3bdelbary] B1.16 add migration notes for B1 completion"
```

---

## Task 17: Final push

**Files:** _(no edits)_

- [ ] **Step 1: Confirm everything is committed**

Run:
```powershell
git status
```
Expected: `working tree clean` (or only `docs/superpowers/` untracked — that's intentional per user preference).

- [ ] **Step 2: Push to origin**

Run:
```powershell
git push origin android/3bdelbary
```
Expected: a few new commits pushed (B1.1 through B1.16, ~12 commits total).

- [ ] **Step 3: Confirm on GitHub**

Open https://github.com/GoBloxy/Agrilliant/tree/android/3bdelbary in a browser; verify the 8 source files exist under the right paths.

- [ ] **Step 4: B1 is complete.** Update the project tracker / Notion / wherever you note completion. Next task: B2 (stage navigation rewrite).

---

## Plan self-review

**Spec coverage:** All 13 sections of the spec map to tasks:

| Spec § | Task |
|--------|------|
| §4.1 AppView | Task 2 |
| §4.2 ShellContent | Task 3 |
| §4.3 NavContext | Task 1 |
| §4.4 SplashView | Task 7 + 8 |
| §4.5 SignInView | Task 4 |
| §4.6 SignUpView | Task 5 |
| §4.7 ShellView | Task 6 |
| §4.8 Main | Tasks 9, 10, 11 |
| §4.9 Launcher (unchanged) | (no task — confirmed unchanged) |
| §5 Data flow | Demonstrated end-to-end in Task 13–14 |
| §6 Files touched | All 8 paths covered + §7 lane verified in Task 15 |
| §8 Cross-track deps | Task 0 (precondition gate) |
| §9 Verification gate | Tasks 12, 13, 14, 15 |
| §10 Out of scope | Documented in MIGRATION_3BDELBARY.md (Task 16) |
| §11 Risks | `restoreStarted` guard (Task 8) addresses the re-entry risk explicitly |

**Placeholder scan:** Zero TBDs in steps. The `TODO(phase-2)` markers inside source code are intentional cross-track coordination notes, not plan placeholders.

**Type consistency:**
- `AppView.SPLASH/SIGNIN/SIGNUP/SHELL` — used consistently across Tasks 2, 7, 8, 9
- `NavContext.get()/setCurrentUser()/getCurrentUser()/clear()` — consistent across Tasks 1, 6, 8
- `ShellContent` values — declared in Task 3, consumed in B2 only (correct: not used in B1)
- `Main.startFarmServerReflectively()` — declared in Task 11, no other references
- `MIN_VISIBLE_MS = 800` — used only inside SplashView; package-private for test access if added later

All identifier names are stable across tasks.
