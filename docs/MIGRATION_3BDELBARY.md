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

## Status of B2–B10
- [ ] **B2** — Replace `Stage` navigation with `AppManager.switchView(...)`. ~17 controller touches. `NavContext` already in place (B1.1).
- [ ] **B3** — 12 FXML files made mobile-friendly. Touch targets, `ScrollPane` wrap, `TableView` → `CharmListView` where appropriate.
- [ ] **B4** — 17 controllers updated. Remove `Stage`, replace `FileChooser` with `CSVExporter.saveCsv(...)` (already exists from H8) + a new `PlatformPickers.pickImage()` helper (delivered in B8).
- [ ] **B5** — Add `css/mobile.css`. Keep AtlantaFX PrimerLight as base.
- [ ] **B6** — Drop any remaining JFreeChart use (already absent from `src/main/java`; double-check leftover imports in dashboard/monitoring).
- [ ] **B7** — Launcher icons mdpi → xxxhdpi under `src/android/res/mipmap-*/` to satisfy Hagag's manifest refs (`@mipmap/ic_launcher`, `@mipmap/ic_launcher_round`).
- [ ] **B8** — Image picker via Gluon Attach `Pictures` (Hagag added the dep in `a911016`). Create `smartfarm.ui.platform.PlatformPickers` with `pickImage()` returning `File`.
- [ ] **B9** — Lifecycle hooks: `View#setOnShown`/`setOnHidden` instead of "set up on stage shown" patterns. (`SplashView` already follows this; sweep the rest.)
- [ ] **B10** — Keep this file current as we go.
