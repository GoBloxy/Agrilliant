# Material You UI Revamp Design

## Goal
Revamp the full Agrilliant JavaFX/Gluon user interface so it feels neat, modern, and Android-native using a light-only Material You / Android 12+ direction, while preserving the current app architecture, controllers, navigation targets, data access, and business behavior.

## Current Project Context
Agrilliant is a Java 17+ Maven app using JavaFX, Gluon Charm/Glisten, AtlantaFX PrimerLight, FXML screens, and CSS stylesheets. The mobile-first shell is implemented through `smartfarm.ui.views.ShellView`, which wraps `dashboard.fxml` in a Gluon `AppBar` and `NavigationDrawer`. Global styling currently lives in `src/main/resources/css/farm-theme.css` and mobile overrides in `src/main/resources/css/mobile.css`.

The revamp should preserve existing controller bindings and FXML `fx:id` values. It should prefer theme and targeted layout cleanup over a structural rewrite.

## Approved Direction
Use Approach B: global Material You design system plus targeted layout cleanup.

This means:
- Restyle the full app globally with a Material You-inspired light palette.
- Improve dense desktop-like screen layouts where they hurt mobile neatness.
- Keep controllers and model/service/data layers unchanged unless UI code requires a small safe adjustment.
- Avoid a full UI rebuild or replacing the FXML/controller architecture.

## Visual System

### Theme
- Light theme only for this pass.
- Android 12+ Material You influence.
- Farm-friendly green primary palette with soft tonal surfaces.
- Avoid dark theme, system-following theme, and dynamic color extraction in this pass.

### Color Palette
Use a consistent CSS-token-like palette in stylesheets:
- Primary: fresh green for main actions and selected states.
- Primary container: pale green for active navigation and selected surfaces.
- On primary: white or very light text.
- Background: warm off-white / very light green-gray.
- Surface: white and subtly tinted green-white cards.
- Surface variant: pale neutral/green for grouped panels and inputs.
- Outline: soft low-contrast gray-green lines.
- Error: Android-like red for errors and critical alerts.
- Warning: amber/orange tonal chips.
- Info: soft blue tonal chips.

### Shape
Use rounder Android-native forms:
- 24px radius for auth cards and large hero cards.
- 18-20px radius for main content cards.
- 14-16px radius for smaller cards and fields.
- Full pill radius for chips, search fields, filter buttons, and compact primary/secondary action buttons where appropriate.

### Elevation
Use softer Material-like elevation:
- Prefer subtle layered shadows and tonal surfaces over heavy borders.
- Keep borders low contrast when used.
- Avoid strong desktop-style boxed panels.

### Typography
Use a cleaner hierarchy:
- Page titles should be larger and semibold.
- Section titles should be medium-weight and easy to scan.
- Secondary labels should be lower contrast.
- Dense table/list text should remain readable on 390px-wide mobile windows.

## Navigation Shell

### App Bar
Keep the existing Gluon `AppBar` in `ShellView`.

Restyle to feel like Android native:
- Clean tonal surface background.
- Clear app title.
- Hamburger menu retained.
- User label and sign-out action retained.
- Avoid visually heavy chrome.

### Navigation Drawer
Keep the existing `NavigationDrawer` destinations.

Restyle drawer:
- Tonal green header with app identity and user/session context.
- More spacious rows with 48-56dp touch targets.
- Selected row appears as a rounded tonal pill.
- Icons and labels use consistent primary/on-surface colors.
- Footer status cards become softer and cleaner.

Do not add bottom navigation in this pass because the app has many destinations and the drawer is already implemented.

## Screen Scope
Apply the revamp across all visible screens:
- Splash/auth shell as applicable.
- Sign in.
- Sign up.
- Dashboard.
- Monitoring.
- Disease detection.
- Alerts.
- Crops.
- Plots.
- Workers.
- Attendance.
- Tasks.
- Harvests.
- Reports.
- Settings.
- Users/about-style placeholder pages if present.
- Logs.

## Layout Strategy

### General
- Preserve controller IDs and event handlers.
- Prefer CSS class improvements and small FXML layout adjustments.
- Avoid rewiring business logic.
- Keep mobile-first 390x844 desktop testing target.

### Dense Action Rows
Where pages have crowded top rows with titles, search, filters, and actions:
- Stack or wrap controls for mobile width.
- Make primary actions prominent but not oversized.
- Use full-width search/filter controls where useful.
- Use tonal secondary buttons instead of bordered desktop buttons.

### Cards
- Make cards more spacious, rounded, and tonal.
- Use consistent card padding.
- Reduce unnecessary borders.
- Create a unified look for metric cards, status cards, summary cards, and detail cards.

### Tables and Lists
- Keep tables if controllers depend on them.
- Make table headers calmer.
- Reduce heavy grid lines.
- Improve row height and font sizing for mobile.
- Preserve horizontal scrolling for wide data.
- Prefer list/card styling where a screen already uses lists.

### Forms
- Inputs should use Android-like filled/tonal fields.
- Buttons should be pill-like and touch-friendly.
- Error labels should be clear but not visually harsh.
- Auth forms should feel like native mobile account screens.

## Dashboard Direction
The dashboard should become the visual anchor for the app:
- Strong but clean page hierarchy.
- Rounded metric cards using tonal surfaces.
- Live sensor cards with clearer icon/value/plot/status hierarchy.
- Field overview and alerts/status sections visually separated by consistent cards.
- Less crowded spacing and more native mobile rhythm.

## Error Handling and Behavior
No behavior changes are intended.

UI error handling should continue using existing labels, dialogs, alerts, and controller flows. Styling may make errors more Android-like through colors, rounded containers, and typography, but no new validation rules or data flows should be introduced.

## Verification
Implementation should be verified by:
- Running Maven compile/build commands available in the project.
- Launching the desktop app in mobile-sized mode if possible.
- Navigating key screens to catch FXML/CSS/controller binding issues.
- Checking that the stylesheets load without resource errors.
- Confirming no database, service, or DAO behavior changed.

## Non-Goals
- No dark theme.
- No dynamic Android system color extraction.
- No bottom navigation.
- No replacement of Gluon/JavaFX/FXML architecture.
- No database/schema changes.
- No behavior changes to authentication, sensors, crops, tasks, alerts, or reports.
