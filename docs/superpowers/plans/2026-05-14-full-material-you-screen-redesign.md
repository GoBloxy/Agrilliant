# Full Material You Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign Agrilliant's JavaFX/Gluon UI into a cohesive light Material You / Android-native farm management app.
**Architecture:** Keep the JavaFX/Gluon/FXML/controller structure intact, preserving all controller-bound `fx:id` values and `onAction` handlers. Implement the redesign through expanded shared CSS components, mobile-first sizing overrides, and targeted FXML restructuring by screen group.
**Tech Stack:** Java 17, Maven, JavaFX 21, Gluon Charm/Glisten 6.2.3, AtlantaFX PrimerLight, FXML, CSS.

---

## Scope Check

The redesign touches multiple screen groups. Keep this as one branch because the shared component system must remain consistent, but execute it as independently verifiable screen-group tasks:

1. UI resource verification harness.
2. Shared Material You component system.
3. App shell and auth screens.
4. Dashboard and monitoring.
5. Crops, plots, workers, tasks, and harvest.
6. Alerts, reports, and logs.
7. Final visual/compile sweep.

Each task should leave the app compiling and should be committed before moving to the next task.

## Files and Responsibilities

- `docs/superpowers/specs/2026-05-14--full-material-you-screen-redesign.md`: approved source of truth for the redesign.
- `scripts/verify_ui_resources.py`: reusable smoke check for CSS/FXML resources, required style classes, and controller-bound identifiers.
- `src/main/resources/css/farm-theme.css`: primary visual design system for palette, shape, elevation, typography, cards, buttons, fields, badges, shell, screen-specific components, and table/list styling.
- `src/main/resources/css/mobile.css`: mobile-first sizing and layout overrides for 390px-wide Android-style windows, touch targets, card density, tables, forms, and screen spacing.
- `src/main/java/smartfarm/ui/views/ShellView.java`: Gluon app shell, app bar, navigation drawer, drawer header/footer, status nodes, and destination styling hooks.
- `src/main/resources/fxml/signin.fxml`: sign-in screen layout and auth style classes.
- `src/main/resources/fxml/signup.fxml`: sign-up screen layout and auth style classes.
- `src/main/resources/fxml/dashboard.fxml`: home hub layout, hero status, metrics, live sensors, field overview, alerts/tasks previews.
- `src/main/resources/fxml/monitoring.fxml`: live operational state, sensor cards, filters, and secondary readings table.
- `src/main/resources/fxml/alerts.fxml`: severity-first alert summary, alert table/detail card styling, filters, and actions.
- `src/main/resources/fxml/crops.fxml`: crop management summary, filters, actions, table/detail card styling.
- `src/main/resources/fxml/plots.fxml`: plot management summary, filters, map/detail card styling.
- `src/main/resources/fxml/workers.fxml`: worker operations summary, filters, table/detail card styling.
- `src/main/resources/fxml/tasks.fxml`: task progress summary, status/priority chips, filters, and detail card styling.
- `src/main/resources/fxml/harvest.fxml`: harvest summary, rounded form sections, actions, and history table styling.
- `src/main/resources/fxml/reports.fxml`: analytics/report cards, chart containers, export action styling.
- `src/main/resources/fxml/logs.fxml`: log filters, type/status chips, and calmer table/list presentation.
- Controllers under `src/main/java/smartfarm/ui/*Controller.java`: read only unless a layout restructuring exposes a binding issue that requires a minimal safe controller adjustment.

## Verification Commands

Run from `/mnt/data/code/Agrilliant`.

```bash
mvn -q -DskipTests compile
```

```bash
python3 scripts/verify_ui_resources.py
```

```bash
git diff --check
```

If JavaFX runtime launches in the environment, run:

```bash
mvn -q javafx:run
```

Manual runtime sweep target: sign-in, sign-up, dashboard, drawer, monitoring, alerts, crops, plots, workers, tasks, harvest, reports, and logs in the default mobile-sized app window.

---

## Task 1: Add UI Resource Verification Harness

### Files to create/modify/test

- Create: `scripts/verify_ui_resources.py`
- Read: `src/main/resources/css/farm-theme.css`
- Read: `src/main/resources/css/mobile.css`
- Read: all files in `src/main/resources/fxml/`

### Steps

- [ ] Create `scripts/verify_ui_resources.py` with this content:

```python
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CSS_FILES = [
    ROOT / "src/main/resources/css/farm-theme.css",
    ROOT / "src/main/resources/css/mobile.css",
]
FXML_DIR = ROOT / "src/main/resources/fxml"

REQUIRED_FILES = [
    *CSS_FILES,
    FXML_DIR / "signin.fxml",
    FXML_DIR / "signup.fxml",
    FXML_DIR / "dashboard.fxml",
    FXML_DIR / "monitoring.fxml",
    FXML_DIR / "alerts.fxml",
    FXML_DIR / "crops.fxml",
    FXML_DIR / "plots.fxml",
    FXML_DIR / "workers.fxml",
    FXML_DIR / "tasks.fxml",
    FXML_DIR / "harvest.fxml",
    FXML_DIR / "reports.fxml",
    FXML_DIR / "logs.fxml",
]

REQUIRED_CSS_TOKENS = [
    "-ag-primary",
    "-ag-on-primary",
    "-ag-primary-container",
    "-ag-background",
    "-ag-surface",
    "-ag-surface-container",
    "-ag-on-surface",
    "-ag-outline",
    "-ag-error",
    "-ag-warning-container",
    "-ag-info-container",
]

REQUIRED_STYLE_CLASSES = [
    "page-header",
    "page-kicker",
    "page-title",
    "page-subtitle",
    "hero-card",
    "hero-title",
    "hero-subtitle",
    "section-card",
    "section-header",
    "summary-card",
    "metric-icon",
    "sensor-card",
    "list-card",
    "list-row-card",
    "btn-primary",
    "btn-secondary",
    "btn-tonal",
    "btn-danger",
    "search-box",
    "filter-combo",
    "filter-chip",
    "badge",
    "badge-high",
    "badge-warning",
    "badge-error",
    "chart-card",
    "empty-state",
    "auth-card",
    "drawer-header",
    "appbar-user-label",
]

REQUIRED_FX_IDS = {
    "signin.fxml": ["txtEmail", "txtPassword", "lblError", "btnSignIn", "btnFingerprint", "lblFpStatus"],
    "signup.fxml": ["txtFullName", "txtUsername", "txtEmail", "txtPhone", "txtPassword", "txtConfirm", "cmbRole", "lblError", "btnSignUp"],
    "alerts.fxml": ["rootPane", "listSection", "cmbSeverity", "cmbStatus", "txtSearch", "btnMarkAllRead", "alertTable", "detailPane", "btnCloseDetails"],
    "crops.fxml": ["listView", "btnExport", "btnAddCrop", "cropTable"],
    "dashboard.fxml": ["temperatureLabel", "humidityLabel", "soilMoistureLabel"],
    "harvest.fxml": ["harvestTable"],
    "logs.fxml": ["logsTable"],
    "monitoring.fxml": ["sensorTable"],
    "plots.fxml": ["plotTable"],
    "reports.fxml": ["reportTypeCombo"],
    "tasks.fxml": ["taskTable"],
    "workers.fxml": ["workerTable"],
}

missing_files = [str(path.relative_to(ROOT)) for path in REQUIRED_FILES if not path.exists()]
if missing_files:
    print("Missing required files:")
    for item in missing_files:
        print(f"  - {item}")
    sys.exit(1)

css = "\n".join(path.read_text(encoding="utf-8") for path in CSS_FILES)
missing_tokens = [token for token in REQUIRED_CSS_TOKENS if token not in css]
missing_classes = [klass for klass in REQUIRED_STYLE_CLASSES if f".{klass}" not in css]

errors = []
if missing_tokens:
    errors.append("Missing CSS tokens: " + ", ".join(missing_tokens))
if missing_classes:
    errors.append("Missing CSS classes: " + ", ".join(missing_classes))

fx_id_pattern = re.compile(r'fx:id="([^"]+)"')
for filename, ids in REQUIRED_FX_IDS.items():
    path = FXML_DIR / filename
    text = path.read_text(encoding="utf-8")
    present = set(fx_id_pattern.findall(text))
    missing = [fx_id for fx_id in ids if fx_id not in present]
    if missing:
        errors.append(f"{filename} missing fx:id values: " + ", ".join(missing))
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        errors.append(f"{filename} is not well-formed XML: {exc}")

if errors:
    for error in errors:
        print(error)
    sys.exit(1)

print("UI resource verification passed")
```

- [ ] Run the verifier and confirm it fails before the new full redesign classes exist:

```bash
python3 scripts/verify_ui_resources.py
```

Expected failure contains at least one missing class such as `page-header`, `hero-card`, `section-card`, or `list-row-card`.

- [ ] Run compile to confirm the new script did not affect the Java build:

```bash
mvn -q -DskipTests compile
```

- [ ] Commit:

```bash
git add scripts/verify_ui_resources.py
git commit -m "$(cat <<'MSG'
Add UI resource verification harness.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 2: Expand the Shared Material You Component System

### Files to create/modify/test

- Modify: `src/main/resources/css/farm-theme.css`
- Modify: `src/main/resources/css/mobile.css`
- Test: `scripts/verify_ui_resources.py`

### Steps

- [ ] Read the approved spec and current stylesheets:

```bash
sed -n '1,220p' docs/superpowers/specs/2026-05-14--full-material-you-screen-redesign.md
sed -n '1,260p' src/main/resources/css/farm-theme.css
sed -n '1,260p' src/main/resources/css/mobile.css
```

- [ ] In `src/main/resources/css/farm-theme.css`, add or normalize these reusable classes without deleting existing screen-specific selectors that FXML still uses:

```css
.page-header {
    -fx-spacing: 6;
    -fx-padding: 2 2 4 2;
}

.page-kicker {
    -fx-text-fill: -ag-primary;
    -fx-font-size: 12;
    -fx-font-weight: bold;
}

.page-subtitle {
    -fx-text-fill: -ag-on-surface-variant;
    -fx-font-size: 13;
}

.hero-card {
    -fx-background-color: linear-gradient(to bottom right, -ag-primary-container, -ag-surface-container-low);
    -fx-background-radius: 28;
    -fx-border-radius: 28;
    -fx-border-color: -ag-outline-variant;
    -fx-padding: 20;
    -fx-effect: dropshadow(gaussian, rgba(47, 111, 62, 0.14), 18, 0.18, 0, 7);
}

.hero-title {
    -fx-text-fill: -ag-on-primary-container;
    -fx-font-size: 24;
    -fx-font-weight: bold;
}

.hero-subtitle {
    -fx-text-fill: -ag-on-surface-variant;
    -fx-font-size: 13;
}

.section-card,
.list-card,
.chart-card,
.form-card {
    -fx-background-color: -ag-surface;
    -fx-background-radius: 22;
    -fx-border-radius: 22;
    -fx-border-color: -ag-outline-variant;
    -fx-border-width: 1;
    -fx-padding: 16;
    -fx-effect: dropshadow(gaussian, rgba(25, 29, 23, 0.08), 14, 0.12, 0, 5);
}

.section-header {
    -fx-spacing: 8;
    -fx-alignment: CENTER_LEFT;
}

.list-row-card {
    -fx-background-color: -ag-surface-container-low;
    -fx-background-radius: 18;
    -fx-border-radius: 18;
    -fx-border-color: transparent;
    -fx-padding: 12 14 12 14;
}

.btn-tonal {
    -fx-background-color: -ag-secondary-container;
    -fx-text-fill: -ag-on-surface;
    -fx-background-radius: 999;
    -fx-border-radius: 999;
    -fx-border-color: transparent;
    -fx-font-weight: bold;
    -fx-cursor: hand;
}

.btn-danger {
    -fx-background-color: -ag-error-container;
    -fx-text-fill: -ag-error;
    -fx-background-radius: 999;
    -fx-border-radius: 999;
    -fx-border-color: transparent;
    -fx-font-weight: bold;
    -fx-cursor: hand;
}

.filter-chip {
    -fx-background-color: -ag-surface-container;
    -fx-text-fill: -ag-on-surface-variant;
    -fx-background-radius: 999;
    -fx-border-radius: 999;
    -fx-border-color: -ag-outline-variant;
    -fx-padding: 7 12 7 12;
    -fx-font-size: 12;
    -fx-font-weight: bold;
}

.empty-state {
    -fx-background-color: -ag-surface-container-low;
    -fx-background-radius: 22;
    -fx-border-radius: 22;
    -fx-border-color: -ag-outline-variant;
    -fx-padding: 24;
    -fx-alignment: CENTER;
}
```

- [ ] In `farm-theme.css`, ensure `.badge-warning` and `.badge-error` exist alongside the current badge classes:

```css
.badge-warning {
    -fx-background-color: -ag-warning-container;
    -fx-text-fill: -ag-warning;
}

.badge-error {
    -fx-background-color: -ag-error-container;
    -fx-text-fill: -ag-error;
}
```

- [ ] In `src/main/resources/css/mobile.css`, add mobile sizing for the new components:

```css
.page-header {
    -fx-spacing: 4;
}

.hero-card {
    -fx-background-radius: 24;
    -fx-border-radius: 24;
    -fx-padding: 16;
}

.hero-title {
    -fx-font-size: 22;
}

.section-card,
.list-card,
.chart-card,
.form-card {
    -fx-background-radius: 20;
    -fx-border-radius: 20;
    -fx-padding: 14;
}

.list-row-card {
    -fx-padding: 12;
    -fx-min-height: 64;
}

.btn-tonal,
.btn-danger {
    -fx-min-height: 48;
    -fx-padding: 10 16 10 16;
}
```

- [ ] Run verification and compile:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] Commit:

```bash
git add src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Expand Material You component system.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 3: Redesign App Shell and Auth Screens

### Files to create/modify/test

- Modify: `src/main/java/smartfarm/ui/views/ShellView.java`
- Modify: `src/main/resources/fxml/signin.fxml`
- Modify: `src/main/resources/fxml/signup.fxml`
- Modify: `src/main/resources/css/farm-theme.css`
- Modify: `src/main/resources/css/mobile.css`
- Test: `scripts/verify_ui_resources.py`

### Steps

- [ ] Read shell and auth files:

```bash
sed -n '1,520p' src/main/java/smartfarm/ui/views/ShellView.java
sed -n '1,240p' src/main/resources/fxml/signin.fxml
sed -n '1,280p' src/main/resources/fxml/signup.fxml
```

- [ ] In `ShellView.java`, preserve destination routing and ensure drawer/header/footer nodes keep or receive these style classes: `drawer-header`, `drawer-title`, `drawer-subtitle`, `drawer-footer`, `drawer-status-card`, `drawer-status-label`, `status-dot-online`, `status-dot-offline`, and `appbar-user-label`.

- [ ] In `signin.fxml`, keep these identifiers unchanged: `txtEmail`, `txtPassword`, `lblError`, `btnSignIn`, `btnFingerprint`, `lblFpStatus`.

- [ ] In `signin.fxml`, restructure only layout containers so the screen has this hierarchy: `auth-root` outer container, `auth-brand-panel` brand/logo block, `auth-card` form surface, `auth-field` inputs, `auth-btn` primary action, `auth-btn-secondary` fingerprint action, and `auth-error` error label.

- [ ] In `signup.fxml`, keep these identifiers unchanged: `txtFullName`, `txtUsername`, `txtEmail`, `txtPhone`, `txtPassword`, `txtConfirm`, `cmbRole`, `lblError`, `btnSignUp`.

- [ ] In `signup.fxml`, apply the same auth hierarchy and spacing as sign-in, using `auth-card`, `auth-field`, `auth-combo`, `auth-btn`, and `auth-error`.

- [ ] In `farm-theme.css`, refine auth and shell styles so auth cards use 24-28px radius, inputs use filled tonal fields, and drawer rows use rounded selected pills with 56px touch targets.

- [ ] Run verification and compile:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] If runtime launches, inspect sign-in, sign-up, drawer open/close, destination switching, and sign-out visibility:

```bash
mvn -q javafx:run
```

- [ ] Commit:

```bash
git add src/main/java/smartfarm/ui/views/ShellView.java src/main/resources/fxml/signin.fxml src/main/resources/fxml/signup.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Redesign app shell and auth screens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 4: Redesign Dashboard and Monitoring Screens

### Files to create/modify/test

- Modify: `src/main/resources/fxml/dashboard.fxml`
- Modify: `src/main/resources/fxml/monitoring.fxml`
- Modify: `src/main/resources/css/farm-theme.css`
- Modify: `src/main/resources/css/mobile.css`
- Read: `src/main/java/smartfarm/ui/DashboardController.java`
- Read: `src/main/java/smartfarm/ui/MonitoringController.java`
- Test: `scripts/verify_ui_resources.py`

### Steps

- [ ] Read controller bindings before editing FXML:

```bash
rg -n "@FXML|lookup|setText|setItems|setCell|setVisible|setManaged" src/main/java/smartfarm/ui/DashboardController.java src/main/java/smartfarm/ui/MonitoringController.java
sed -n '1,340p' src/main/resources/fxml/dashboard.fxml
sed -n '1,340p' src/main/resources/fxml/monitoring.fxml
```

- [ ] In `dashboard.fxml`, preserve all existing `fx:id` values. Do not rename `temperatureLabel`, `humidityLabel`, or `soilMoistureLabel`.

- [ ] Redesign `dashboard.fxml` into a home hub using: `page-header`, `hero-card`, `summary-card`, `sensor-card`, `section-card`, `chart-card`, and `list-row-card`.

- [ ] Keep existing charts, labels, and data nodes present so `DashboardController` can still populate live values.

- [ ] In `monitoring.fxml`, preserve `sensorTable` and every existing filter/action `fx:id`.

- [ ] Redesign `monitoring.fxml` so live sensor status cards appear before table detail, filters are grouped in a `section-card`, and the table is wrapped in a `chart-card` or `list-card` surface.

- [ ] In `farm-theme.css`, add dashboard/monitoring-specific polish for hero sensor values, metric icon variants, chart containers, and live status chips.

- [ ] In `mobile.css`, ensure dashboard/monitoring cards fit the mobile window without clipping: 12px page padding, 14px section card padding, compact sensor values, and table max height no greater than 260px.

- [ ] Run verification and compile:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] If runtime launches, navigate to Dashboard and Monitoring, open drawer after visiting both screens, and confirm no FXML load error appears:

```bash
mvn -q javafx:run
```

- [ ] Commit:

```bash
git add src/main/resources/fxml/dashboard.fxml src/main/resources/fxml/monitoring.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Redesign dashboard and monitoring screens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 5: Redesign Management Screens

### Files to create/modify/test

- Modify: `src/main/resources/fxml/crops.fxml`
- Modify: `src/main/resources/fxml/plots.fxml`
- Modify: `src/main/resources/fxml/workers.fxml`
- Modify: `src/main/resources/fxml/tasks.fxml`
- Modify: `src/main/resources/fxml/harvest.fxml`
- Modify: `src/main/resources/css/farm-theme.css`
- Modify: `src/main/resources/css/mobile.css`
- Read: `src/main/java/smartfarm/ui/CropController.java`
- Read: `src/main/java/smartfarm/ui/PlotController.java`
- Read: `src/main/java/smartfarm/ui/WorkerController.java`
- Read: `src/main/java/smartfarm/ui/TaskController.java`
- Read: `src/main/java/smartfarm/ui/HarvestController.java`
- Test: `scripts/verify_ui_resources.py`

### Steps

- [ ] Read bindings and FXML:

```bash
rg -n "@FXML|on[A-Z]|setText|setItems|setCell|setVisible|setManaged" src/main/java/smartfarm/ui/CropController.java src/main/java/smartfarm/ui/PlotController.java src/main/java/smartfarm/ui/WorkerController.java src/main/java/smartfarm/ui/TaskController.java src/main/java/smartfarm/ui/HarvestController.java
sed -n '1,360p' src/main/resources/fxml/crops.fxml
sed -n '1,360p' src/main/resources/fxml/plots.fxml
sed -n '1,360p' src/main/resources/fxml/workers.fxml
sed -n '1,360p' src/main/resources/fxml/tasks.fxml
sed -n '1,360p' src/main/resources/fxml/harvest.fxml
```

- [ ] Preserve all `fx:id` values and `onAction` handlers in the five FXML files.

- [ ] In `crops.fxml`, redesign top content as `page-header`, summary `summary-card` row/stack, filters in `section-card`, crop table in `list-card`, and detail pane in `section-card`.

- [ ] In `plots.fxml`, redesign top content as `page-header`, plot summary cards, map/detail card, filters in `section-card`, and table/history content in `list-card`.

- [ ] In `workers.fxml`, redesign top content as staffing summary cards, mobile action cluster, worker table in `list-card`, and detail pane in `section-card`.

- [ ] In `tasks.fxml`, redesign task progress as summary cards, filters as chips/filled controls, task table in `list-card`, and detail pane in `section-card` with status/priority badges.

- [ ] In `harvest.fxml`, redesign harvest totals as summary cards, form fields in `form-card`, export/actions as tonal pills, and history table in `list-card`.

- [ ] In `farm-theme.css`, add or refine management-specific selectors: `.management-summary-grid`, `.detail-pane`, `.detail-section-title`, `.detail-grid-label`, `.detail-grid-value`, `.metric-icon-primary`, `.metric-icon-info`, `.metric-icon-warning`, `.metric-icon-error`, `.form-card`, and `.status-chip`.

- [ ] In `mobile.css`, ensure management screens use stacked spacing, full-width action buttons when narrow, and readable table row heights.

- [ ] Run verification and compile:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] If runtime launches, navigate Crops, Plots, Workers, Tasks, and Harvest. Confirm add/export/detail buttons remain visible and no screen fails to load:

```bash
mvn -q javafx:run
```

- [ ] Commit:

```bash
git add src/main/resources/fxml/crops.fxml src/main/resources/fxml/plots.fxml src/main/resources/fxml/workers.fxml src/main/resources/fxml/tasks.fxml src/main/resources/fxml/harvest.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Redesign management screens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 6: Redesign Alerts, Reports, and Logs Screens

### Files to create/modify/test

- Modify: `src/main/resources/fxml/alerts.fxml`
- Modify: `src/main/resources/fxml/reports.fxml`
- Modify: `src/main/resources/fxml/logs.fxml`
- Modify: `src/main/resources/css/farm-theme.css`
- Modify: `src/main/resources/css/mobile.css`
- Read: `src/main/java/smartfarm/ui/AlertController.java`
- Read: `src/main/java/smartfarm/ui/ReportsController.java`
- Read: `src/main/java/smartfarm/ui/LogsController.java`
- Test: `scripts/verify_ui_resources.py`

### Steps

- [ ] Read bindings and FXML:

```bash
rg -n "@FXML|on[A-Z]|setText|setItems|setCell|setVisible|setManaged" src/main/java/smartfarm/ui/AlertController.java src/main/java/smartfarm/ui/ReportsController.java src/main/java/smartfarm/ui/LogsController.java
sed -n '1,380p' src/main/resources/fxml/alerts.fxml
sed -n '1,380p' src/main/resources/fxml/reports.fxml
sed -n '1,360p' src/main/resources/fxml/logs.fxml
```

- [ ] Preserve all `fx:id` values and `onAction` handlers in the three FXML files.

- [ ] In `alerts.fxml`, redesign as severity-first: page header, severity summary cards, filters in `section-card`, alert table in `list-card`, detail pane in `section-card`, and suggested action as a tonal card.

- [ ] In `reports.fxml`, redesign report options as `chart-card` or `section-card` surfaces, chart containers as `chart-card`, and export controls as `btn-primary`, `btn-secondary`, or `btn-tonal`.

- [ ] In `logs.fxml`, redesign filters as a compact `section-card`, log table as `list-card`, and type/status states as `badge-info`, `badge-warning`, `badge-error`, or `badge-normal`.

- [ ] In `farm-theme.css`, add or refine selectors for alert severity cards, report cards, chart cards, export action rows, log rows, and semantic badge variants.

- [ ] In `mobile.css`, keep alert/report/log cards compact enough for phone width and table max heights balanced with the rest of the screen.

- [ ] Run verification and compile:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] If runtime launches, navigate Alerts, Reports, and Logs. Confirm tables render, filters remain visible, and detail panes do not cover the full screen unexpectedly:

```bash
mvn -q javafx:run
```

- [ ] Commit:

```bash
git add src/main/resources/fxml/alerts.fxml src/main/resources/fxml/reports.fxml src/main/resources/fxml/logs.fxml src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css
git commit -m "$(cat <<'MSG'
Redesign alerts reports and logs screens.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

## Task 7: Final Verification and Cleanup

### Files to create/modify/test

- Modify if needed: `src/main/resources/css/farm-theme.css`
- Modify if needed: `src/main/resources/css/mobile.css`
- Modify if needed: touched FXML files from previous tasks
- Test: full compile and UI resource verification

### Steps

- [ ] Run full verification:

```bash
python3 scripts/verify_ui_resources.py
mvn -q -DskipTests compile
git diff --check
```

- [ ] Inspect changed files for accidental controller/service/data changes:

```bash
git diff --name-only HEAD~6..HEAD
```

Expected implementation files are CSS, FXML, `ShellView.java`, and `scripts/verify_ui_resources.py`. If a controller was changed, inspect that diff and confirm the change is limited to safe UI binding support.

- [ ] If runtime launches, perform the mobile-sized visual sweep:

```bash
mvn -q javafx:run
```

Screens to inspect in order: sign-in, sign-up, dashboard, drawer, monitoring, alerts, crops, plots, workers, tasks, harvest, reports, logs.

- [ ] Confirm acceptance criteria:
  - App compiles.
  - UI resource verifier passes.
  - `git diff --check` passes.
  - Drawer navigation destinations remain intact.
  - Auth fields and buttons remain visible.
  - Tables/lists render without FXML errors.
  - The UI reads as full light Material You / Android-native, not desktop-dashboard styling.
  - No service, DAO, model, database, authentication, sensor, crop, task, alert, report, or log behavior changed.

- [ ] Commit cleanup only if files changed during this final pass:

```bash
git add src/main/resources/css/farm-theme.css src/main/resources/css/mobile.css src/main/resources/fxml src/main/java/smartfarm/ui/views/ShellView.java scripts/verify_ui_resources.py
git commit -m "$(cat <<'MSG'
Complete full Material You redesign verification.

Generated with [Devin](https://cli.devin.ai/docs)

Co-Authored-By: Devin <158243242+devin-ai-integration[bot]@users.noreply.github.com>
MSG
)"
```

If no files changed during final verification, do not create an empty commit.
