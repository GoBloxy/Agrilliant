# Material You UI Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply a full light-theme Material You / Android 12+ UI revamp across Agrilliant's JavaFX/Gluon mobile-first app.
**Architecture:** Keep the existing JavaFX/Gluon/FXML/controller architecture. Implement a global design system in CSS first, then make targeted FXML and shell refinements only where the existing desktop-like layout prevents a neat Android-native result.
**Tech Stack:** Java 17, Maven, JavaFX 21, Gluon Charm/Glisten 6.2.3, AtlantaFX PrimerLight, FXML, CSS.

---

## Files and Responsibilities

- `src/main/resources/css/farm-theme.css`: Primary visual design system. Define Material You-inspired light colors, cards, app shell, drawer, buttons, badges, tables, forms, dashboard, auth, and page component styling.
- `src/main/resources/css/mobile.css`: Mobile-first sizing/layout overrides. Ensure Android touch targets, phone-width spacing, table/list sizing, and auth/mobile form sizing win after the base theme.
- `src/main/java/smartfarm/ui/views/ShellView.java`: Gluon app shell. Apply style classes to app bar user label, drawer header/footer nodes, drawer items, status dots, and ensure existing navigation remains unchanged.
- `src/main/resources/fxml/signin.fxml`: Auth screen structure. Remove hard inline divider styles and add style classes that can be themed consistently.
- `src/main/resources/fxml/signup.fxml`: Sign-up screen structure. Keep field/controller IDs unchanged and align auth card spacing with sign-in.
- `src/main/resources/fxml/dashboard.fxml`: Main dashboard layout. Add/normalize style classes for dashboard cards, metric cards, sensor sections, and action clusters without renaming `fx:id` values.
- `src/main/resources/fxml/monitoring.fxml`: Monitoring layout. Normalize search/filter/action/card style classes and mobile-friendly spacing.
- `src/main/resources/fxml/alerts.fxml`: Alerts layout. Normalize alert filter chips, tables/lists, severity badges, and action controls.
- `src/main/resources/fxml/crops.fxml`: Crops layout. Normalize summary cards, filters, buttons, tables/lists, and detail cards.
- `src/main/resources/fxml/plots.fxml`: Plots layout. Replace key inline styles with reusable summary/card/icon classes.
- `src/main/resources/fxml/workers.fxml`: Workers layout. Normalize summary cards, filters, worker detail cards, and action controls.
- `src/main/resources/fxml/tasks.fxml`: Tasks layout. Normalize task status chips, cards, table/list rows, and filters.
- `src/main/resources/fxml/harvest.fxml`: Harvest layout. Normalize harvest form, summary cards, export actions, and tables.
- `src/main/resources/fxml/reports.fxml`: Reports layout. Normalize report cards, chart containers, and export actions.
- `src/main/resources/fxml/logs.fxml`: Logs layout. Normalize log filters, table/list presentation, and status chips.
- `docs/superpowers/specs/2026-05-14--material-you-ui-revamp.md`: Approved design source of truth.

## Verification Commands

Run these commands from `/mnt/data/code/Agrilliant` during implementation:

```bash
mvn -q -DskipTests compile
```

```bash
python3 - <<'PY'
from pathlib import Path
root = Path('/mnt/data/code/Agrilliant')
required = [
    'src/main/resources/css/farm-theme.css',
    'src/main/resources/css/mobile.css',
    'src/main/resources/fxml/signin.fxml',
    'src/main/resources/fxml/signup.fxml',
    'src/main/resources/fxml/dashboard.fxml',
    'src/main/resources/fxml/monitoring.fxml',
    'src/main/resources/fxml/alerts.fxml',
    'src/main/resources/fxml/crops.fxml',
    'src/main/resources/fxml/plots.fxml',
    'src/main/resources/fxml/workers.fxml',
    'src/main/resources/fxml/tasks.fxml',
    'src/main/resources/fxml/harvest.fxml',
    'src/main/resources/fxml/reports.fxml',
    'src/main/resources/fxml/logs.fxml',
]
missing = [p for p in required if not (root / p).exists()]
if missing:
    raise SystemExit('Missing expected UI files: ' + ', '.join(missing))
css = (root / 'src/main/resources/css/farm-theme.css').read_text()
for token in ['-ag-primary', '-ag-surface', '.appbar-user-label', '.drawer-header', '.btn-primary', '.auth-card', '.table-view']:
    if token not in css:
        raise SystemExit(f'Missing expected theme token/class: {token}')
print('UI resource smoke check passed')
PY
```

If the desktop runtime is available, run:

```bash
mvn -q javafx:run
```

Manually inspect sign-in, sign-up, dashboard, drawer, monitoring, alerts, crops, plots, workers, tasks, harvests, reports, and logs at the default 390x844 window size.

---

## Task 1: Establish Material You CSS Tokens and Base Surfaces

- [ ] Read `docs/superpowers/specs/2026-05-14--material-you-ui-revamp.md`, `src/main/resources/css/farm-theme.css`, and `src/main/resources/css/mobile.css`.
- [ ] In `src/main/resources/css/farm-theme.css`, replace the current `.root` token block with this Material You-inspired light token set:

```css
.root {
    -ag-primary: #2f6f3e;
    -ag-on-primary: #ffffff;
    -ag-primary-container: #d8f0d4;
    -ag-on-primary-container: #102116;
    -ag-secondary: #53634f;
    -ag-secondary-container: #d6e8cf;
    -ag-tertiary: #38656a;
    -ag-tertiary-container: #bcebf0;
    -ag-error: #ba1a1a;
    -ag-error-container: #ffdad6;
    -ag-background: #fbfdf7;
    -ag-surface: #ffffff;
    -ag-surface-dim: #eef3ea;
    -ag-surface-container-low: #f7faf2;
    -ag-surface-container: #f1f5ed;
    -ag-surface-container-high: #eaf0e5;
    -ag-surface-variant: #dde6d8;
    -ag-on-surface: #191d17;
    -ag-on-surface-variant: #41493d;
    -ag-outline: #c2ccbd;
    -ag-outline-variant: #dce5d8;
    -ag-warning: #8a5100;
    -ag-warning-container: #ffddb0;
    -ag-info: #0061a4;
    -ag-info-container: #d1e4ff;

    -color-accent-fg: -ag-primary;
    -color-accent-emphasis: -ag-primary;
    -color-accent-muted: -ag-primary-container;
    -color-accent-subtle: -ag-primary-container;
}
```

- [ ] In `farm-theme.css`, update global backgrounds so `.dashboard-root`, `.content-area`, `.content-pane`, `.placeholder-page`, and `.page-scroll > .viewport` use `-ag-background` or transparent surfaces consistently.
- [ ] Preserve all existing class names used by FXML/controllers; add new rules rather than deleting selectors unless a selector is replaced in the same edit.
- [ ] Run `mvn -q -DskipTests compile` and confirm compilation succeeds.
- [ ] Commit:

```bash
git add src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Introduce Material You UI tokens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 2: Restyle Core Components Globally

- [ ] In `farm-theme.css`, update reusable components using the new tokens:
  - `.card`: white or low container surface, 20px radius, soft shadow, low/no border.
  - `.card-title`: `-ag-on-surface`, semibold, 15-16px.
  - `.btn-primary`: primary filled pill with `-ag-primary` and `-ag-on-primary`.
  - `.btn-secondary`: tonal pill with `-ag-secondary-container` and `-ag-on-surface`.
  - `.action-btn`, `.export-btn`, `.filter-btn`: rounded touch-friendly Material-like controls.
  - `.search-box`, `.filter-combo`, `.text-field`, `.password-field`, `.combo-box`, `.date-picker`: filled/tonal rounded fields.
  - `.badge-*`: tonal chips for normal, low, medium, high, info, success, warning, error.
  - `.table-view`: calmer headers, subtle separators, no heavy grid look.
  - `.ghost-list`, `.list-tile`, `.charm-list-cell`: card/list row presentation with rounded/touchable rows.
- [ ] In `mobile.css`, keep 48dp minimum tap targets but adjust padding/radii to match the new global style. Use 12px page padding, 16-20px card radii, and compact table cell heights that still read well on 390px width.
- [ ] Run the Python UI resource smoke check from the Verification Commands section and confirm it prints `UI resource smoke check passed`.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] Commit:

```bash
git add src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Restyle shared UI components for Android.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 3: Polish Gluon AppBar and NavigationDrawer Shell

- [ ] Read `src/main/java/smartfarm/ui/views/ShellView.java` lines 199-448.
- [ ] Keep all drawer destinations and `NavTarget` dispatch unchanged.
- [ ] Ensure the app bar title remains `Agrilliant`, sign-out remains available, and `appbar-user-label` stays applied to the user label.
- [ ] In `ShellView.java`, add/confirm style classes for drawer nodes:
  - header root: `drawer-header`
  - app name label: `drawer-title`
  - user/context labels: `drawer-subtitle`
  - footer root/card: `drawer-footer` and `drawer-status-card`
  - status value labels: `drawer-status-label`
  - status dots: existing `status-dot-online` / `status-dot-offline` retained
- [ ] In `farm-theme.css`, style the shell:
  - `.app-bar` / Glisten app bar selectors if present through CSS lookup, plus `.appbar-user-label`.
  - `.drawer-header`: tonal green rounded/clean header.
  - `.drawer-title`, `.drawer-subtitle`: readable hierarchy.
  - navigation drawer rows: 56dp min height, rounded active pill, calm icon/text colors.
  - `.drawer-footer`, `.drawer-status-card`, `.drawer-status-label`: softer tonal footer.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] If `mvn -q javafx:run` works locally, open the drawer and verify every item is visible and selectable.
- [ ] Commit:

```bash
git add src/main/java/smartfarm/ui/views/ShellView.java src/main/resources/css/farm-theme.css
git commit -m "$(cat <<'MSG'
Polish Android navigation shell styling.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 4: Revamp Sign-In and Sign-Up Screens

- [ ] Read `src/main/resources/fxml/signin.fxml`, `src/main/resources/fxml/signup.fxml`, and the auth-related rules in `farm-theme.css`.
- [ ] In `signin.fxml`, replace the two inline divider `Region` styles with a reusable class by changing each divider region to include `styleClass="auth-divider-line"` and removing the inline `style` attribute.
- [ ] Keep `fx:id` values unchanged: `txtEmail`, `txtPassword`, `lblError`, `btnSignIn`, `btnFingerprint`, `lblFpStatus`.
- [ ] In `signup.fxml`, keep `fx:id` values unchanged: `txtFullName`, `txtUsername`, `txtEmail`, `txtPhone`, `txtPassword`, `txtConfirm`, `cmbRole`, `lblError`, `btnSignUp`.
- [ ] In `farm-theme.css`, update auth styles:
  - `.auth-root`: `-ag-background`, 20-24px padding.
  - `.auth-card`: white/tonal card, 24px radius, subtle shadow, 20px padding.
  - `.auth-app-name`: 28px semibold, `-ag-on-surface`.
  - `.auth-tagline`, `.auth-subtitle`, `.auth-footer`: `-ag-on-surface-variant`.
  - `.auth-field`, `.auth-combo`: filled rounded fields with 56px preferred height.
  - `.auth-btn`: primary pill, 52-56px height.
  - `.auth-btn-secondary`: tonal pill.
  - `.auth-divider-line`: low-contrast outline line.
  - `.auth-error`: red tonal container or clear red text using `-ag-error`.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] If runtime is available, launch and inspect sign-in/sign-up navigation.
- [ ] Commit:

```bash
git add src/main/resources/fxml/signin.fxml src/main/resources/fxml/signup.fxml src/main/resources/css/farm-theme.css
git commit -m "$(cat <<'MSG'
Revamp authentication screens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 5: Normalize Dashboard and Monitoring Screens

- [ ] Read `src/main/resources/fxml/dashboard.fxml`, `src/main/resources/fxml/monitoring.fxml`, and `src/main/java/smartfarm/ui/DashboardController.java` to understand which nodes are controlled by code.
- [ ] In `dashboard.fxml`, keep every `fx:id` unchanged.
- [ ] Add or normalize existing style classes rather than changing controller wiring:
  - page root: `content-pane`
  - page title: `page-title`
  - high-level cards: `card`
  - metric cards: `summary-card` plus existing value/title classes where present
  - sensor cards: `sensor-card`, `sensor-label`, `sensor-value`, `sensor-plot`
  - field map: `field-map`
- [ ] In `monitoring.fxml`, keep every `fx:id` unchanged and normalize search/filter/action classes to `search-box`, `filter-combo`, `filter-btn`, `btn-primary`, `btn-secondary`, `card`, and `badge` where applicable.
- [ ] In `farm-theme.css`, update dashboard/monitoring-specific classes to use Material You cards, tonal icon circles, clear live sensor hierarchy, and calm section spacing.
- [ ] In `mobile.css`, ensure dashboard cards remain readable on 390px width and sensor cards keep compact but not cramped row layout.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] If runtime is available, inspect dashboard and monitoring screens and verify no FXML load errors occur.
- [ ] Commit:

```bash
git add src/main/resources/fxml/dashboard.fxml src/main/resources/fxml/monitoring.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Normalize dashboard and monitoring UI.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 6: Normalize Management Screens

- [ ] Read these files before editing: `crops.fxml`, `plots.fxml`, `workers.fxml`, `tasks.fxml`, `harvest.fxml`.
- [ ] Keep all `fx:id` values and `onAction` handlers unchanged.
- [ ] Replace obvious inline color/font/padding styles with reusable classes when the same visual role already exists or is introduced in `farm-theme.css`:
  - summary card titles: `summary-card-title`
  - summary values: `summary-card-value`
  - summary subtitles: `summary-card-subtitle`
  - icon circles: `metric-icon`, plus optional semantic modifiers such as `metric-icon-primary`, `metric-icon-warning`, `metric-icon-info`
  - primary actions: `btn-primary`
  - secondary actions: `btn-secondary`
  - status chips: `badge` plus semantic badge class
- [ ] In `farm-theme.css`, ensure those classes exist and do not rely on page-specific inline colors.
- [ ] Verify tables, filters, and buttons on each management screen use consistent Android-like spacing/radii.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] If runtime is available, inspect Crops, Plots, Workers, Tasks, and Harvests screens.
- [ ] Commit:

```bash
git add src/main/resources/fxml/crops.fxml src/main/resources/fxml/plots.fxml src/main/resources/fxml/workers.fxml src/main/resources/fxml/tasks.fxml src/main/resources/fxml/harvest.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Normalize management screen layouts.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 7: Normalize Alerts, Reports, and Logs Screens

- [ ] Read `alerts.fxml`, `reports.fxml`, and `logs.fxml`.
- [ ] Keep all `fx:id` values and handlers unchanged.
- [ ] Normalize severity/status visuals:
  - alert severity: `badge-low`, `badge-medium`, `badge-high`, `badge-error` as applicable.
  - report/export actions: `export-btn`, `btn-primary`, `btn-secondary`.
  - logs status/type chips: `badge-info`, `badge-warning`, `badge-error`, `badge-normal`.
- [ ] Ensure charts/report containers use `card` or a report-specific class styled as a Material You surface.
- [ ] In `farm-theme.css`, add any missing report/log/alert classes using existing tokens.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] If runtime is available, inspect Alerts, Reports, and Logs screens.
- [ ] Commit:

```bash
git add src/main/resources/fxml/alerts.fxml src/main/resources/fxml/reports.fxml src/main/resources/fxml/logs.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Normalize alerts reports and logs UI.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 8: Final Verification and Visual Regression Sweep

- [ ] Run:

```bash
mvn -q -DskipTests compile
```

- [ ] Run the Python UI resource smoke check from the Verification Commands section.
- [ ] Run:

```bash
git diff --check
```

- [ ] If desktop runtime works, run:

```bash
mvn -q javafx:run
```

- [ ] Manually inspect these screens in the mobile-sized window: sign-in, sign-up, dashboard, drawer, monitoring, alerts, crops, plots, workers, tasks, harvests, reports, logs.
- [ ] Confirm these acceptance criteria:
  - The app still compiles.
  - CSS smoke check passes.
  - No obvious FXML load errors occur.
  - Existing navigation destinations remain intact.
  - Auth fields and buttons remain functional.
  - The UI reads as light Material You / Android-native rather than desktop-dashboard styling.
- [ ] Commit final cleanup if any files changed:

```bash
git add src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css src/main/resources/fxml src/main/java/smartfarm/ui/views/ShellView.java
git commit -m "$(cat <<'MSG'
Complete Material You UI revamp verification.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

If no files changed during final verification, do not create an empty commit.
