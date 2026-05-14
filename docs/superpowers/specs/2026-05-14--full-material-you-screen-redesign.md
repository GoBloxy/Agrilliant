# Full Material You Screen Redesign Design

## Goal
Deepen the existing Agrilliant Material You / Android-native UI direction with a full screen redesign pass. The app should feel less like a styled desktop JavaFX dashboard and more like a cohesive light-theme Android farm management app, while preserving the current JavaFX/Gluon/FXML foundation and existing business behavior.

## Current Project Context
Agrilliant is a Java 17+ Maven application using JavaFX, Gluon Charm/Glisten, AtlantaFX PrimerLight, FXML screens, and CSS stylesheets. Global UI styling currently lives in `src/main/resources/css/farm-theme.css`, with mobile-first sizing overrides in `src/main/resources/css/mobile.css`. The app shell is implemented through `smartfarm.ui.views.ShellView`, which provides the Gluon `AppBar` and `NavigationDrawer` around the FXML screen content.

A previous Material You revamp introduced light green design tokens, Android-style shared components, drawer/app bar polish, and normalization across the existing FXML screens. This redesign continues that work but allows more aggressive screen-level restructuring where the current layouts still feel dense, table-heavy, or desktop-oriented.

## Approved Direction
Use a full screen redesign pass, continuing the existing light Material You / Android-native design direction.

This means:
- Keep the app architecture, controllers, services, DAOs, models, and navigation destinations intact.
- Preserve existing controller-critical `fx:id` values and `onAction` handlers.
- Redesign FXML screen layouts more aggressively where necessary.
- Prefer card/list/mobile section layouts over desktop dashboard panels where practical.
- Use the existing CSS token direction as the design foundation, expanding it where needed.
- Avoid changing authentication, sensor, crop, task, alert, report, database, or service behavior.

## Visual System

### Theme
- Light theme only.
- Android 12+ Material You influence.
- Farm-focused green primary palette with expressive tonal containers.
- Warm off-white app background, white/tonal cards, soft outlines, and subtle shadows.
- No dark mode, dynamic system color extraction, or platform theme switching in this pass.

### Shape and Elevation
- Large rounded cards for primary sections and dashboard hero content.
- Medium rounded cards for lists, forms, summary panels, and data containers.
- Pill-shaped primary actions, secondary actions, chips, filters, and search controls.
- Low-contrast outlines only where they improve separation.
- Softer layered elevation instead of heavy desktop borders.

### Typography and Density
- Strong page titles and concise subtitles.
- Clear section headings inside cards.
- Larger key metrics and live sensor values.
- Lower-contrast metadata and supporting text.
- Touch-friendly density suitable for a 390px-wide mobile window.

### Interaction Styling
- Primary actions should be visually obvious and consistent across screens.
- Secondary actions should use tonal buttons, not heavy bordered desktop controls.
- Filters should look like chips or filled controls.
- Forms should use filled rounded fields.
- Severity/status states should use tonal chips and clear semantic colors.

## App Shell

### App Bar
Keep the existing Gluon app bar, title, user context, and sign-out access. Polish should make it feel like a native Android top app bar:
- Tonal or surface background.
- Reduced visual weight.
- Clear title hierarchy.
- Consistent icon/action spacing.

### Navigation Drawer
Keep the existing drawer destination model. Redesign the drawer as a polished Android navigation drawer:
- Tonal green brand header.
- Larger touch targets.
- Rounded selected destination pill.
- Calm icon and label colors.
- Softer footer/status cards.

Do not add bottom navigation in this pass because the app has many destinations and the drawer already matches the app architecture.

## Screen Redesign Strategy

### General Rules
- Preserve every controller-required `fx:id`.
- Preserve every existing `onAction` handler.
- Do not remove controls that controllers read or update.
- Keep FXML readable and avoid introducing unnecessary custom controls.
- Move repeated visual styling into CSS classes instead of inline styles.
- Use small Java controller adjustments only when layout restructuring makes them necessary.

### Auth Screens
The sign-in and sign-up screens should feel like native mobile onboarding/account screens:
- Strong brand/logo area.
- Centered or vertically balanced auth card.
- Large rounded card surfaces.
- Filled rounded inputs.
- Prominent primary button.
- Tonal secondary/fingerprint actions.
- Clear but calm error presentation.

### Dashboard
The dashboard should become the app's visual home hub:
- Hero farm status card at the top.
- Key metrics in compact, high-readability cards.
- Live sensor section with distinct temperature/humidity/soil cards.
- Field health/map summary as a polished card.
- Alerts/tasks preview cards that guide the user to next actions.
- Less table-like density and more mobile scan rhythm.

### Monitoring
Monitoring should prioritize live operational state:
- Current sensor status cards first.
- Search/filter/action controls grouped into a compact mobile section.
- Data table/list retained where controllers require it, but styled as secondary detail.
- Clear status chips for normal/warning/critical readings.

### Alerts
Alerts should be severity-first:
- Summary cards for critical/warning/normal counts.
- Alert rows/cards with severity chip, short title, timestamp, and action affordance.
- Critical states should be obvious but not visually harsh.
- Tables may remain as fallback/detail views if controller bindings require them.

### Crops and Plots
Crop and plot management should feel like mobile management screens:
- Summary cards at the top.
- Search/filter controls as filled fields/chips.
- Primary add/manage action clearly placed.
- Crop/plot details presented with rounded cards and semantic icons where possible.
- Existing tables retained only where needed and restyled to match the design system.

### Workers and Tasks
Worker/task screens should emphasize operational actions:
- Summary/status cards for staffing and task progress.
- Mobile-friendly action clusters.
- Status chips for task state, attendance, and priority.
- Detail areas should use cards rather than dense panels.

### Harvest
Harvest should read as a production/records workflow:
- High-level harvest totals/summary cards.
- Rounded form sections.
- Clear primary save/add action.
- Export/report actions as tonal or outlined-pill controls.
- Data tables styled as secondary history/detail views.

### Reports
Reports should feel like a clean analytics area:
- Report option cards with icon/title/subtitle/action.
- Chart containers as rounded surfaces.
- Export actions grouped consistently.
- Reduce visual noise around tables/charts.

### Logs
Logs should remain information-dense but calmer:
- Search/filter controls grouped at top.
- Type/status chips for log categories.
- Table/list rows with improved spacing and softer separators.
- Error/warning log states should be semantic and easy to scan.

## CSS and Component System
Expand the existing Material You component system in `farm-theme.css` and `mobile.css` rather than creating isolated one-off styles. Expected reusable classes include:
- Page containers and page headers.
- Hero cards.
- Summary/metric cards.
- Sensor cards.
- Section cards.
- List rows/list cards.
- Filled fields.
- Search fields.
- Filter chips/buttons.
- Primary/secondary/tonal/destructive actions.
- Status/severity badges.
- Chart/report containers.
- Empty states.

`mobile.css` should remain responsible for mobile-first sizing and touch targets, while `farm-theme.css` owns palette, shape, elevation, and component visual identity.

## Error Handling and Behavior
No behavior changes are intended.

Existing validation labels, dialogs, alert flows, service calls, data refreshes, navigation actions, and database interactions should continue to work. Styling may make errors clearer through tonal containers and semantic colors, but no new validation rules or data flows should be introduced.

## Verification
Implementation should be verified by:
- Running Maven compile/build commands available in the project.
- Running a CSS/FXML resource smoke check.
- Running `git diff --check`.
- Launching the app in a mobile-sized desktop window if available.
- Navigating sign-in, sign-up, dashboard, drawer, monitoring, alerts, crops, plots, workers, tasks, harvest, reports, and logs.
- Confirming no FXML load errors, missing `fx:id` bindings, stylesheet errors, or navigation regressions occur.
- Confirming no service, DAO, model, database, authentication, sensor, task, crop, alert, report, or log behavior changed.

## Non-Goals
- No dark theme.
- No dynamic Android system color extraction.
- No bottom navigation.
- No replacement of the JavaFX/Gluon/FXML architecture.
- No database or schema changes.
- No service/DAO/model rewrites.
- No changes to authentication rules, sensor processing, crop/task/alert/report business logic, or persistence behavior.
