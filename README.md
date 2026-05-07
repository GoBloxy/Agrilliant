# Smart Farm Management System

A full-stack Java & IoT college project for the **OOP II** module.

Physical sensors measure field conditions → a Java server processes the data → the system manages crops, workers, tasks, and harvests in MySQL → the manager monitors everything through a live JavaFX dashboard.

---

## Tech Stack

| Component       | Technology                | Purpose                          |
|-----------------|---------------------------|----------------------------------|
| Language        | Java 17+                  | Main application language        |
| GUI             | JavaFX 21                 | Dashboard and all UI screens     |
| Database        | MySQL 8                   | Persistent storage               |
| DB Driver       | JDBC (mysql-connector-j)  | Java ↔ MySQL communication       |
| Server          | Java ServerSocket          | Receives ESP32 TCP connections   |
| Concurrency     | ExecutorService            | Thread pool for device handlers  |
| Microcontroller | ESP32                     | Field device with WiFi           |
| Sensor          | DHT11                     | Temperature & humidity sensor    |
| Firmware        | Arduino C++               | ESP32 programming                |
| Build Tool      | Maven                     | Dependency management            |

---

## Project Structure

```
Agrilliant/
├── pom.xml                          # Maven build file (dependencies & plugins)
├── sql/
│   └── schema.sql                   # MySQL schema — run this FIRST to create the DB
├── firmware/
│   └── esp32_dht11.ino              # ESP32 + DHT11 Arduino firmware
│
└── src/main/
    ├── java/smartfarm/
    │   ├── model/                   # Plain Java classes (data only — no logic)
    │   │   ├── Plot.java            #   A physical piece of farm land
    │   │   ├── Crop.java            #   A crop planted on a plot (lifecycle: PLANTED→GROWING→READY→HARVESTED)
    │   │   ├── Worker.java          #   A farm employee
    │   │   ├── Task.java            #   A job assigned to a worker on a plot (PENDING→IN_PROGRESS→DONE)
    │   │   ├── Alert.java           #   A threshold-breach alert (INFO / WARNING / CRITICAL)
    │   │   ├── SensorReading.java   #   One temperature+humidity reading from an ESP32
    │   │   └── HarvestRecord.java   #   A harvest event with quantity and grade (A/B/C)
    │   │
    │   ├── dao/                     # Data Access Objects — JDBC queries only, no business logic
    │   │   ├── PlotDAO.java         #   CRUD for plots
    │   │   ├── CropDAO.java         #   CRUD for crops + overdue query
    │   │   ├── WorkerDAO.java       #   CRUD for workers
    │   │   ├── TaskDAO.java         #   CRUD for tasks + active-count query
    │   │   ├── AlertDAO.java        #   Save alerts, get unresolved, mark resolved
    │   │   ├── SensorDAO.java       #   Save readings, get last 50 by device
    │   │   └── HarvestDAO.java      #   Save harvest records, get by plot/crop
    │   │
    │   ├── service/                 # Business logic — the "brain" of the system
    │   │   ├── SensorService.java   #   Coordinates: save reading → check alerts → notify UI
    │   │   ├── AlertService.java    #   Threshold detection → fire alerts → auto-create tasks
    │   │   ├── CropService.java     #   Plant crops, advance stages, detect overdue harvests
    │   │   ├── TaskService.java     #   Manual + auto task creation, least-busy worker assignment
    │   │   ├── HarvestService.java  #   Record harvests, yield analytics, efficiency calculation
    │   │   └── WorkerService.java   #   Worker management and availability checks
    │   │
    │   ├── server/                  # TCP networking — ESP32 ↔ Java connection
    │   │   ├── FarmServer.java      #   ServerSocket on port 8080, accepts ESP32 connections
    │   │   └── SensorHandler.java   #   Runnable — one thread per connected device
    │   │
    │   ├── ui/                      # JavaFX controllers (paired with FXML views)
    │   │   ├── DashboardController.java  # Main screen: live charts, alerts, farm overview
    │   │   ├── CropController.java       # Crop & plot CRUD screen
    │   │   ├── WorkerController.java     # Worker management screen
    │   │   ├── AlertController.java      # Alert table with resolve action
    │   │   └── HarvestController.java    # Harvest logging and yield analytics
    │   │
    │   ├── util/                    # Shared utilities
    │   │   ├── DBConnection.java    #   Singleton MySQL connection (READY — update credentials)
    │   │   ├── CSVExporter.java     #   Export sensor/harvest data to CSV files
    │   │   └── ThresholdConfig.java #   Configurable alert thresholds
    │   │
    │   └── Main.java               # Entry point — launches JavaFX + starts FarmServer
    │
    └── resources/fxml/              # JavaFX layout files (no logic — view only)
        ├── dashboard.fxml           #   Main dashboard layout
        ├── crops.fxml               #   Crop management layout
        ├── workers.fxml             #   Worker management layout
        ├── alerts.fxml              #   Alert panel layout
        └── harvest.fxml             #   Harvest logging layout
```

---

## Architecture

The project follows a **strict layered architecture** — each layer only talks to the layer directly below it:

```
┌─────────────────────────────────────────┐
│  Presentation Layer (JavaFX + FXML)     │  ← What the farmer sees
├─────────────────────────────────────────┤
│  Service Layer (business logic)         │  ← Where decisions are made
├─────────────────────────────────────────┤
│  Data Access Layer (DAO + JDBC)         │  ← Where SQL lives
├─────────────────────────────────────────┤
│  Infrastructure (Sockets, DB conn)      │  ← Plumbing
└─────────────────────────────────────────┘
```

**Rule:** The Presentation layer never writes SQL. The DAO layer never decides whether to fire an alert. Each layer has one job.

Inside the JavaFX layer, **MVC** is used:
- **Model** — `model/` package (data classes)
- **View** — `.fxml` files (layout, no logic)
- **Controller** — `ui/` package (connects views to services)

---

## Getting Started

### 1. Database Setup
```bash
mysql -u root -p < sql/schema.sql
```
Then update the credentials in `src/main/java/smartfarm/util/DBConnection.java`:
```java
private static final String URL      = "jdbc:mysql://localhost:3306/smart_farm";
private static final String USER     = "root";
private static final String PASSWORD = "";   // ← your MySQL password
```

### 2. Build & Run
```bash
mvn clean javafx:run
```

### 3. ESP32 (later)
1. Wire DHT11 to ESP32: VCC→3.3V, Data→GPIO4, GND→GND
2. Update WiFi credentials and server IP in `firmware/esp32_dht11.ino`
3. Upload via Arduino IDE

---

## Build Roadmap

| Stage | Focus                        | What to Build                                                  |
|-------|------------------------------|----------------------------------------------------------------|
| 1     | Foundation                   | DB setup, DBConnection, all model classes, all DAO classes     |
| 2     | Farm Management Core         | CropService, TaskService, Crop/Worker/Task UI screens          |
| 3     | Networking & IoT Pipeline    | FarmServer, SensorHandler, test with simulated client          |
| 4     | ESP32 Integration            | Wire DHT11, upload firmware, verify data in DB                 |
| 5     | Alert System & Auto-Tasks    | AlertService, threshold detection, auto-task creation          |
| 6     | Full Dashboard               | Live sensor chart, alert panel, farm overview cards, nav       |
| 7     | Harvest, Export & Polish     | Harvest screen, CSV export, error handling, documentation      |

---

## OOP II Concepts Covered

| Concept            | Where It Appears                                        |
|--------------------|---------------------------------------------------------|
| OOP Design         | 7 model classes with enums, lifecycle methods           |
| Interfaces         | `Runnable` (SensorHandler), DAO pattern                 |
| Multithreading     | One thread per ESP32 device via ExecutorService          |
| Networking         | TCP socket between ESP32 and Java ServerSocket           |
| Database (JDBC)    | 7 tables, full CRUD via DAO classes                      |
| JavaFX GUI         | Live dashboard with charts, tables, forms                |
| File I/O           | CSV export for sensor and harvest data                   |
| Collections        | List, Map for managing data throughout                   |
| Exception Handling | Network drops, DB errors, invalid sensor data            |
