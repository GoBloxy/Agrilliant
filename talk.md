# Agrilliant: From JavaFX Desktop to Native Android

## A Technical Deep-Dive into a Full-Stack Java + IoT Smart Farm System and Its Migration to Mobile

---

## 1. What Is Agrilliant?

Agrilliant is a **full-stack Java + IoT smart farm management system** built as an OOP II college project. The core idea is simple: physical sensors in the field measure environmental conditions, a Java server ingests that data over TCP, business logic fires alerts and auto-creates tasks when thresholds are breached, and a manager monitors everything through a live dashboard.

The project started life as a **JavaFX 21 desktop application** -- a fat JAR launched via `mvn javafx:run`, with a fixed 1300x820 window, `TableView`s, `MenuBar`, `FileChooser`, and all the desktop-centric assumptions that come with them. It has since been migrated to also produce a **native Android APK** via GraalVM AOT compilation through the Gluon Mobile framework -- all from the same Maven module, the same source tree, and the same package layout.

---

## 2. The Stack

### 2.1 Language and Runtime

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Language | Java | 17+ (`--release 17`) | Application language |
| UI Framework | JavaFX | 21.0.2 | Windowing, controls, charts, properties |
| Mobile UI | Gluon Glisten (Charm) | 6.2.3 | Material-style mobile shell, `MobileApplication`, `CharmListView`, `NavigationDrawer` |
| Mobile Bridge | Gluon Attach | 4.0.23 | Platform service abstractions (Settings, Storage, Pictures, Display, Lifecycle, StatusBar) |
| AOT Compiler | GraalVM Substrate (via GluonFX) | CE 23-dev+25.1 | Ahead-of-time native image for Android arm64 |
| Build | Maven | 3.9.6 | Dependency management, profiles, plugins |

### 2.2 Data and Persistence

| Layer | Technology | Purpose |
|---|---|---|
| Database | MySQL 8 | Persistent storage (11 tables) |
| Driver | `mysql-connector-j` 8.3.0 | JDBC connectivity |
| Connection | `DBConnection` singleton | Lazy, re-creatable, credential-layered connection |
| Auth | jBCrypt 0.4 | Password hashing |
| Sessions | HMAC-signed tokens via Gluon Settings | Session persistence across restarts |

### 2.3 IoT and Hardware

| Component | Technology | Purpose |
|---|---|---|
| Microcontroller | ESP32 Dev Module | Wi-Fi-enabled field device |
| Temperature/Humidity | DHT11 | Ambient conditions (0-50C, 20-80% RH) |
| Soil Moisture | FC-28 analog | Soil water content (0-100%) |
| Display | SH1106 1.3" OLED (I2C) | On-device live readings |
| Biometrics | R307 Fingerprint (UART) | Worker attendance check-in/out |
| Firmware | Arduino C++ | ESP32 programming |
| Protocol | TCP over Wi-Fi (port 8080) | ESP32-to-Java data ingress |

### 2.4 External Services

| Service | Purpose |
|---|---|
| Crop.health API (Kindwise) | AI-powered crop disease detection from leaf photos |
| MQTT (planned) | Push notifications / real-time messaging |

### 2.5 UI Libraries

| Library | Purpose |
|---|---|
| AtlantaFX (PrimerLight) | CSS theme engine -- pure CSS, portable to GluonFX |
| Ikonli (Feather pack) | Icon fonts for JavaFX buttons and nav |
| Gson 2.11 | JSON parsing for Crop.health API responses |

---

## 3. Architecture

The project follows a **strict layered architecture** where each layer only talks to the layer directly below it:

```
+-------------------------------------------+
|  Presentation Layer                       |  <- JavaFX/Gluon controllers + FXML
|  (smartfarm.ui / smartfarm.ui.views)      |
+-------------------------------------------+
        |
+-------------------------------------------+
|  Service Layer                            |  <- Business logic, alert rules, auto-tasks
|  (smartfarm.service)                      |
+-------------------------------------------+
        |
+-------------------------------------------+
|  Data Access Layer                        |  <- JDBC-only, no business logic
|  (smartfarm.dao)                          |
+-------------------------------------------+
        |
+-------------------------------------------+
|  Infrastructure                           |  <- DBConnection, FarmServer, serial, HTTP
|  (smartfarm.util / smartfarm.server)      |
+-------------------------------------------+
```

**Rule:** The Presentation layer never writes SQL. The DAO layer never decides whether to fire an alert. Each layer has one job.

Within the Presentation layer, **MVC** is enforced:
- **Model** -- `model/` package (14 POJOs, pure Java, no JavaFX imports)
- **View** -- `.fxml` files (layout only, zero logic)
- **Controller** -- `ui/` package (wires views to services)

### 3.1 Package Layout

```
src/main/java/smartfarm/
  Main.java, Launcher.java          -- Entry points
  model/     (14 POJOs)             -- Crop, Worker, Task, Alert, SensorReading, etc.
  dao/       (13 DAOs)              -- CRUD per entity, JDBC only
  service/   (14 services)          -- Business logic + LiveSensorData bridge
  server/    (FarmServer + Handler) -- TCP ingress (desktop-only)
  ui/        (17 controllers)       -- JavaFX/Gluon controllers
  ui/nav/    (AppView, NavContext)   -- Gluon navigation graph
  ui/views/  (4 View classes)       -- SplashView, SignInView, SignUpView, ShellView
  ui/platform/ (PlatformPickers)    -- Cross-platform file/camera abstractions
  util/      (DBConnection, Logger, Constants, ThresholdConfig, CSVExporter)
```

---

## 4. The IoT Pipeline: Sensors to Dashboard

This is the system's most interesting data flow. Here's how a single temperature reading travels from a physical sensor in a field to a live-updating label on a manager's screen:

### 4.1 The Physical Layer (ESP32)

The ESP32 runs an Arduino sketch (`firmware/esp32_dht11.ino`) that:

1. **Boots** -- connects to Wi-Fi, initializes DHT11 on GPIO 4, FC-28 on GPIO 34 (analog), SH1106 OLED on I2C (SDA=21, SCL=22), and R307 fingerprint on UART2 (RX=16, TX=17)
2. **Auto-detects** the FC-28 soil moisture sensor by sampling the ADC pin 10 times and checking for a stable, non-floating signal
3. **Loops every 2 seconds** -- reads temperature and humidity from the DHT11, reads soil moisture from the FC-28 (if detected), updates the OLED display, and sends the reading over TCP
4. **Formats data** as a simple newline-terminated string:
   ```
   DEVICE:plot1_sensor,TEMP:27.50,HUM:63.20,SOIL:54.30
   ```
5. **Handles fingerprints** -- when a finger is placed on the R307, the ESP32 performs on-device matching (image capture -> feature extraction -> database search), then sends:
   ```
   FINGERPRINT:plot1_sensor,ID:42
   ```
6. **Bridges serial commands** -- the desktop Java app can also send commands over USB serial (`SCAN`, `ENROLL:<slot>`, `DELETE:<slot>`, `TEMPLATE_COUNT`) which the ESP32 relays to the R307, enabling the desktop UI to manage fingerprint enrollment

### 4.2 The TCP Server (Desktop Only)

`FarmServer.java` opens a `ServerSocket` on port 8080 and spawns one `SensorHandler` thread per connected device via a cached `ExecutorService`. Each handler:

1. Reads lines from the socket in a blocking loop
2. Routes `FINGERPRINT:` lines to `AttendanceService.handleFingerprintScan()`
3. Parses sensor lines into a `Parsed` record (deviceCode, temperature, humidity, soilMoisture)
4. Resolves the device code to a database `device_id` via `DeviceDAO.getByCode()`
5. Constructs a `SensorReading` model object and passes it to `SensorService.processReading()`

**Why desktop-only?** Android cannot keep a listening TCP socket alive in the background -- the OS will kill the process. The Android app is a *consumer* of sensor data (reads from the database), not a *producer*.

### 4.3 The Processing Pipeline

`SensorService.processReading()` does two things, in order:

1. **Live UI update (always runs, even if DB is down):**
   - Calls `LiveSensorData.getInstance().update(reading, deviceCode)`
   - This pushes values to JavaFX properties via `Platform.runLater()`, ensuring the dashboard updates in real-time regardless of database state

2. **Database persistence (throttled to once per 60 seconds per device):**
   - Calls `SensorDAO.save(reading)` to INSERT into `sensor_readings`
   - Triggers `AlertService.checkAndAlert(reading, plotId)` for threshold-based alert evaluation
   - Any DB/alert errors are caught and logged without affecting the live UI

### 4.4 The Live Data Bridge

`LiveSensorData` is the critical bridge between the TCP thread and the JavaFX UI thread. It's a singleton that:

- Exposes JavaFX `Property` objects (`SimpleFloatProperty`, `SimpleStringProperty`, `SimpleIntegerProperty`)
- Wraps all property updates in `Platform.runLater()` to safely marshal them onto the FX Application Thread
- Tracks connected devices using a `ConcurrentHashMap`-backed set for thread safety
- The dashboard controller attaches change listeners to these properties, so labels update automatically

### 4.5 The Alert Engine

`AlertService.checkAndAlert()` evaluates every persisted reading against configurable thresholds from `ThresholdConfig`:

| Condition | Severity | Auto-creates Task? |
|---|---|---|
| Temp >= 45C or <= 0C | CRITICAL | Yes -- auto-assigns to least-busy worker |
| Temp >= 38C or soil <= 15% | WARNING | No |
| Humidity >= 85% or <= 25% | WARNING | No |
| Soil <= 10% (critically dry) | CRITICAL | Yes -- "irrigation needed" task |

Critical alerts automatically create tasks via `TaskService.autoCreateTask()`, which uses a least-busy-worker assignment strategy. This means the system doesn't just notify -- it *acts*.

---

## 5. The Crop Disease Detection Pipeline

A separate but equally important flow is AI-powered disease detection:

1. Manager picks a leaf photo via `PlatformPickers.pickImage()` (Gluon Attach `Pictures` on Android, `FileChooser` on desktop)
2. `PlantIdService.analyzeImage()` base64-encodes the image and POSTs it to the Crop.health API (Kindwise)
3. The API returns disease suggestions with probability, severity, and treatment recommendations
4. The result is converted to a `DiseaseDetection` model and persisted to the `disease_detection` table
5. Confidence is classified as HIGH (>=80%), MEDIUM (>=50%), or LOW

This service uses Java 11's `HttpClient` with a 30-second timeout, Gson for JSON parsing, and loads its API key from `crop-health.properties` on the classpath (or the `CROP_HEALTH_API_KEY` environment variable as fallback).

---

## 6. Database Schema

The MySQL schema (`sql/schema.sql`) defines 11 tables across the farm domain:

```
admin / manager          -- Two-role auth system (admin + manager)
worker                   -- Farm employees (managed by a manager, no login)
plots                    -- Physical pieces of farm land
devices                  -- ESP32 sensor nodes (typed: TEMP_HUM, SOIL_MOISTURE, etc.)
crops                    -- Crops with lifecycle: SEED -> SEEDLING -> VEGETATIVE -> FLOWERING -> FRUITING -> HARVESTED
sensor_readings          -- Time-series data from devices
alerts                   -- Threshold-breach alerts (INFO / WARNING / CRITICAL)
tasks                    -- Work items (PENDING -> IN_PROGRESS -> DONE)
worker_task              -- M:N assignment between workers and tasks
harvest_records          -- Harvest events with quantity and grade (A/B/C/REJECT)
disease_detection        -- AI-detected crop diseases
attendance               -- R307 fingerprint check-in/out
```

Key design decisions:
- **Cascading deletes** from manager down to plots, crops, tasks -- deleting a manager cleans up their entire domain
- **Alert-to-task linkage** via `alert_id` (nullable) -- auto-created tasks reference their triggering alert
- **Worker fingerprint mapping** via `fingerprint_id` on the worker table -- links a physical biometric ID to a worker record

---

## 7. The Migration: JavaFX Desktop to Native Android

This is the most technically ambitious part of the project. The team needed to produce a native Android APK from the same codebase that runs as a desktop JavaFX application, without forking into a separate project.

### 7.1 The Migration Target

- **Runtime:** Gluon Mobile (GluonFX) producing a native Android APK via GraalVM Substrate AOT compilation
- **Architecture:** unchanged. `model -> dao -> service -> ui` stays. Same package names, same controller-per-FXML pattern
- **Data path:** the phone connects directly to MySQL via JDBC (the team chose this over building a REST backend)
- **IoT ingress:** the `FarmServer` TCP listener is removed from the Android build -- the phone is a data consumer, not a producer
- **Hardware:** R307 fingerprint is stubbed on Android (returns `connected = false`); future work would use Android USB OTG via Gluon Attach
- **Result:** one Maven module that produces both the desktop JAR and the Android APK

### 7.2 The Toolchain: GluonFX + GraalVM AOT

The migration uses **GluonFX** (`gluonfx-maven-plugin` 1.0.28), which orchestrates:

1. **Compilation** -- standard `javac` with `--release 17`
2. **GraalVM Substrate native-image** -- ahead-of-time compiles the Java bytecode into a native ARM64 binary for Android
3. **Android packaging** -- wraps the native binary in an APK with the Gluon Android bridge activity (`MainActivity`), which boots the GraalVM isolate and launches `smartfarm.Main`

The AOT step is where things get hard. GraalVM's native-image performs **closed-world analysis** -- it only includes classes and methods that are reachable at compile time. Any code loaded via reflection (FXML controllers, JDBC drivers, Ikonli font loaders, Gson serialization) must be explicitly registered in GraalVM configuration files.

### 7.3 GraalVM Reflection Configuration

The project ships seed configuration in `src/main/resources/META-INF/native-image/smartfarm/`:

- **`reflect-config.json`** -- 12 FXML controllers, 18 model POJOs, ~30 JavaFX widget root types, Ikonli's `FontIcon` + `Feather` enum + SPI handler, MySQL JDBC driver classes (`Driver`, `NonRegisteringDriver`, `NativeProtocol`), 5 programmatic UI pages
- **`resource-config.json`** -- pattern includes for `fxml/**`, `css/**`, `images/**`, `*.properties`, `META-INF/services/*`, AtlantaFX theme CSS, Ikonli Feather TTF
- **`jni-config.json`** -- empty (no native JNI calls yet; USB OTG fingerprint would populate this)

These files are auto-discovered by GraalVM at AOT time. The team can regenerate them by running the desktop app with the GraalVM tracing agent attached and clicking through every screen.

### 7.4 Maven Profile Architecture

The `pom.xml` defines two profiles that share the same source tree but diverge at compile and package time:

**Desktop profile** (default, `activeByDefault=true`):
- Includes desktop-only dependencies: `jSerialComm` (R307 fingerprint over USB serial)
- Runs `maven-shade-plugin` to produce a fat JAR for `jpackage`
- Sets `gluonfx.target=host`
- Compiles all sources including `server/` and `service/desktop/`

**Android profile** (`mvn -Pandroid`):
- Sets `gluonfx.target=android`
- **Excludes** `service/desktop/**/*.java` (jSerialComm-dependent fingerprint implementation)
- **Excludes** `server/**/*.java` (FarmServer + SensorHandler -- Android can't host a TCP listener)
- No shade plugin -- GluonFX handles APK packaging via its own AOT pipeline

### 7.5 The Two-Track Parallel Migration

The migration was executed by two developers working in parallel on disjoint file sets:

**Hagag's track (Build / Data / IoT):**
- H1: GluonFX plugin + Maven profiles in `pom.xml`
- H2: GraalVM reflection/resource/JNI config seeds
- H3: `AndroidManifest.xml` with INTERNET, CAMERA, READ_MEDIA_IMAGES permissions
- H4: Rewrote `DBConnection` for Android -- credential layering (env vars -> Gluon Settings -> `db.properties`), lazy re-creatable connection, `runAsync()` for off-UI-thread DB work
- H5: Swept all `printStackTrace()` / `System.out.println` calls to `Logger` API
- H6: Rewrote `SessionManager` -- file-on-disk replaced with Gluon Attach `Settings` (Android `SharedPreferences`) with desktop file fallback
- H7: Split `FingerprintService` -- desktop impl moved to `service/desktop/`, Android-safe stub returns `connected = false`
- H8: Rewrote `CSVExporter` for Android scoped storage via Gluon Attach `Storage` (Storage Access Framework)
- H9: Added `Constants.IS_ANDROID` runtime flag, profile-guarded `server/` package
- H10: Logger routes to `android.util.Log` on Android, `System.out` on desktop; `ThresholdConfig` reads from classpath properties

**3bdelbary's track (UI / Navigation / Resources):**
- B1: Converted `Main.java` from `javafx.application.Application` to `com.gluonhq.charm.glisten.application.MobileApplication` with 4 lazy view factories (SPLASH, SIGNIN, SIGNUP, SHELL)
- B2: Replaced all `Stage.setScene()` / `stage.getScene().setRoot()` navigation with `AppManager.getInstance().switchView()` via an `AppView` enum
- B3: Made all 12 FXML files mobile-friendly -- dropped fixed pixel sizes, replaced 4 `TableView`s with `CharmListView`, added `FlowPane` responsive layouts
- B4: Adapted all 17 controllers -- removed `Stage`/`FileChooser` imports, added `PlatformPickers` abstraction
- B5: Added `mobile.css` with larger base font, touch-target sizing, `CharmListView` row height
- B6: Audited and removed JFreeChart dependency (native `javafx.scene.chart` is Gluon-friendly)
- B7: Launcher icons at standard Android densities
- B8: Image picker via Gluon Attach `Pictures` for disease detection
- B9: Lifecycle hooks -- `View.setOnShown`/`setOnHidden` instead of stage listeners

### 7.6 Key Migration Challenges and Solutions

#### Challenge 1: FarmServer Can't Run on Android

Android kills background processes and can't keep a TCP `ServerSocket` alive. The solution was a **two-layer exclusion**:

1. **Compile-time:** The Android Maven profile excludes `server/**/*.java` from compilation
2. **Runtime:** `Main.startFarmServerReflectively()` uses `Class.forName("smartfarm.server.FarmServer")` behind a `!Constants.IS_ANDROID` guard. If the class is absent (Android), it logs and skips. If present (desktop), it starts the server on a daemon thread.

The reflective load is critical -- without it, `Main.java` would have a compile-time import on `FarmServer`, and javac's implicit compilation would drag `FarmServer` and `SensorHandler` into the Android classpath even though the Maven profile excludes them.

#### Challenge 2: Fingerprint Hardware (jSerialComm)

`jSerialComm` is a native desktop library that talks to the R307 fingerprint scanner over USB serial. It cannot link on Android. The solution:

- Move the real implementation to `service/desktop/FingerprintServiceDesktop.java` (excluded from Android compile)
- Create a stub `service/FingerprintService.java` that returns `connected = false` for all calls
- The stub keeps the same public API so 3bdelbary's controllers compile unchanged

#### Challenge 3: DB Connection Lifecycle on Android

On desktop, the DB connection is a long-lived singleton. On Android, the OS can freeze the process at any time, closing sockets. The rewritten `DBConnection`:

- Uses **credential layering**: env vars -> Gluon Attach `Settings` (SharedPreferences) -> `db.properties` on classpath
- Caches credentials in an `Optional<Creds>` so the "nothing configured" decision isn't re-evaluated per call
- Makes the connection **lazy and re-creatable**: any `SQLException` clears the cached `Connection` via `AtomicReference.compareAndSet`, and the next call opens a fresh one
- Provides `runAsync(Callable<T>)` backed by a single-thread `ExecutorService` so DB work never blocks the FX thread
- `closeQuietly()` should be wired from `LifecycleEvent.DESTROY` (not `PAUSE` -- the user might return)

#### Challenge 4: GraalVM Closed-World Analysis

GraalVM's AOT compiler only includes code that is statically reachable. Anything loaded by reflection is invisible unless explicitly registered. The project's reflection-heavy dependencies (FXML, MySQL JDBC, Ikonli, Gson) required a carefully curated `reflect-config.json`. The seed file was built manually and can be regenerated by running the desktop app with the GraalVM tracing agent:

```bash
mvn -Pdesktop -Dagent gluonfx:run
# Click through every screen, then stop the app
native-image-configure generate \
  --input-dir=target/native-agent-config \
  --output-dir=src/main/resources/META-INF/native-image/smartfarm/
```

#### Challenge 5: Glisten's HOME_VIEW Contract

A subtle bug discovered during smoke testing: Gluon's `AppManager.continueInit()` calls `switchView("home")` to mount the startup view. The original `AppView.SPLASH` was registered under its enum name `"SPLASH"`, which didn't match `"home"`. The fix: `AppView` constants now carry an explicit `registeredName`, with `SPLASH` mapping to `MobileApplication.HOME_VIEW` (`"home"`).

#### Challenge 6: TableView to CharmListView

Four screens (workers, tasks, harvest, logs) were list-shaped -- each row was 5-8 columns that could be collapsed into a Material-style card. These were migrated from `TableView` + `TableColumn` to `CharmListView` with custom `CharmListCell` implementations that build Glisten `ListTile` nodes. The remaining tables (dashboard, crops, plots, alerts, monitoring, reports) stayed as `TableView` because columns truly matter for comparison.

---

## 8. The Navigation Graph

Pre-migration, navigation was raw `Stage` manipulation:

```java
// Old: direct stage swap
Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));
stage.getScene().setRoot(root);
```

Post-migration, navigation uses Gluon's `AppManager` and a typed enum:

```java
// New: Gluon view switch
AppView.SHELL.switchTo();

// Cross-screen state via singleton
NavContext.get().setCurrentUser(user);
```

The graph has 4 top-level views:

```
SPLASH (HOME_VIEW) ──> SIGNIN ──> SHELL (dashboard)
                         ^         |
                         |         v
                       SIGNUP <────+ (logout clears NavContext)
```

Inside `SHELL`, the `DashboardController` still uses `FXMLLoader.load()` for inner content swaps (crops, plots, workers, tasks, alerts, monitoring, harvest, reports, logs, disease, attendance, settings, about) -- these are content panes, not full-screen states, so they don't need to be Gluon Views.

---

## 9. Cross-Platform Abstractions

The migration introduced several abstractions that keep both targets compiling and running:

| Abstraction | Desktop Behavior | Android Behavior |
|---|---|---|
| `Constants.IS_ANDROID` | `false` (Gluon Attach absent or desktop platform) | `true` (Gluon Attach reports Android) |
| `DBConnection` credential layering | env vars -> Settings file -> `db.properties` | env vars -> SharedPreferences -> `db.properties` asset |
| `SessionManager` | File in `~/.agrilliant/` | Gluon Attach `Settings` (SharedPreferences) |
| `FingerprintService` | Real impl via `jSerialComm` + R307 | Stub: `connected = false` |
| `CSVExporter.saveCsv()` | Writes to `~/Downloads/` | Gluon Attach `Storage` (SAF) |
| `PlatformPickers.pickImage()` | JavaFX `FileChooser` | Gluon Attach `Pictures` |
| `Logger` | `System.out` / `System.err` | `android.util.Log` (via reflection, no `android.*` imports) |
| `FarmServer` | Started on daemon thread | Excluded from compile + runtime guard |
| CSS | `farm-theme.css` only | `farm-theme.css` + `mobile.css` (larger fonts, touch targets) |

---

## 10. CI/CD: GitHub Actions for Android APK

The project includes a GitHub Actions workflow (`.github/workflows/android-build.yml`) that:

1. Checks out the repo with submodules
2. Sets up GraalVM (Gluon's distribution)
3. Installs Android SDK + NDK
4. Generates `db.properties`, `mqtt.properties`, `crop-health.properties` from GitHub Secrets
5. Runs a sanity compile for both desktop and Android profiles
6. Executes `mvn -Pandroid gluonfx:build` for the full AOT + APK pipeline
7. Uploads GluonFX logs on failure for debugging

The workflow handles the `CAPCache` build failure that newer GraalVM versions introduce by passing `-H:+NewCAPCache` and `-H:+UnlockExperimentalVMOptions` as native-image args.

---

## 11. What the Migration Preserved

The entire point of the migration was to produce an Android APK *without* rewriting the application. Here's what stayed identical:

- **14 model classes** -- pure Java, no JavaFX imports, fully portable
- **13 DAO classes** -- JDBC-only, no UI dependencies, same SQL
- **Service layer business logic** -- `AlertService.checkAndAlert()`, `TaskService.autoCreateTask()`, `CropService` lifecycle management, `HarvestService` yield analytics -- all unchanged
- **Database schema** -- same 11 tables, same foreign keys, same enums
- **IoT firmware** -- the ESP32 sketch is untouched; it still talks to the desktop variant's TCP server
- **Package structure** -- `smartfarm.model`, `smartfarm.dao`, `smartfarm.service`, `smartfarm.ui` -- same names, same boundaries

The only things that changed were the *edges*: how the UI renders (Glisten vs raw JavaFX), how navigation works (AppManager vs Stage), how storage works (SAF vs filesystem), and how the app boots (MobileApplication vs Application).

---

## 12. Known Trade-Offs and Future Work

The team acknowledged several trade-offs in the migration:

1. **JDBC from the phone** -- A phone on 4G/5G cannot reach a MySQL on a private LAN. The DB needs a public host or VPN. Credentials in the APK are extractable. A REST backend in front of MySQL is the proper fix.
2. **No phone-side TCP server** -- The Android app is a data consumer only. ESP32 devices still talk to the desktop variant or a separate always-on host.
3. **Fingerprint stubbed** -- The R307 flow is disabled on Android. Real integration would go via Android USB OTG using Gluon Attach, not `jSerialComm`.
4. **No push notifications** -- FCM integration is documented as a follow-up.
5. **No offline mode** -- SQLite + sync is documented as future work.
6. **Reflection config is a seed** -- The full APK build will surface missing GraalVM reflection entries; the team plans to run the tracing agent to capture them.

---

## 13. Summary

Agrilliant is a system where:

- **Physical sensors** in farm fields send data over Wi-Fi via TCP
- **A Java server** ingests, persists, and evaluates that data against configurable thresholds
- **Business logic** automatically fires alerts and creates tasks when conditions are dangerous
- **A live dashboard** shows real-time sensor data, active alerts, and farm status
- **AI disease detection** lets managers photograph leaves and get treatment recommendations
- **Biometric attendance** tracks worker check-in/out via fingerprint scanners

And the migration story is:

- **Same codebase**, same architecture, same package layout
- **Two Maven profiles** produce a desktop JAR and a native Android APK
- **GraalVM AOT** compiles Java to native ARM64 for Android
- **Gluon Mobile** provides the Material-style mobile shell
- **Cross-platform abstractions** (Settings, Storage, Pictures, Logger) keep both targets working
- **Parallel two-developer migration** with strict file ownership to avoid merge conflicts

The result is a single Maven module that builds for both desktop and mobile from one source tree -- a practical demonstration that JavaFX + GraalVM AOT is a viable path from desktop to native mobile, even for a project with IoT integration, biometric hardware, and AI-powered external services.
