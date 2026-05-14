================================================================================
                    AGRILLIANT — SMART FARM MANAGEMENT SYSTEM
================================================================================

1. GROUP MEMBERS
--------------------------------------------------------------------------------
  Name                                  ID
  ------------------------------------  ----------
  Mostafa Mahmoud Aborehab              241008538
  Jouiria Mohammed Hassan               241008818
  Ahmed Hagag Abdelhamed                241002485
  Mohamed Ahmed Mahmoud Abdelbary       241009209
  Mariam Yasser Dahab                   241008888

2. SELECTED BUSINESS DOMAIN
--------------------------------------------------------------------------------
  Smart Agriculture (Farm Management)

  Agrilliant is a full-stack Java desktop application for managing smart farms.
  It integrates real-time IoT sensor monitoring (temperature, humidity, soil
  moisture), crop and plot management, worker attendance via fingerprint
  scanning, disease detection, harvest tracking, automated alerts, and
  task assignment — all through a modern JavaFX dashboard.

3. STEPS TO RUN THE PROJECT
--------------------------------------------------------------------------------
  Prerequisites:
    - Java JDK 17 or higher
    - Apache Maven 3.8+
    - MySQL Server 8.x
    - (Optional) An MQTT broker for remote sensor data (e.g. Mosquitto)

  Steps:
    1. Clone or extract the project folder.
    2. Open a terminal in the project root directory (where pom.xml is located).
    3. Set up the database (see Section 4 below).
    4. Configure the database connection:
       - Create the file:  src/main/resources/db.properties
       - Add the following content (adjust values to your setup):

           db.url=jdbc:mysql://localhost:3306/smartfarm
           db.user=root
           db.password=YOUR_PASSWORD

    5. Build the project:
           mvn clean install

    6. Run the application:
           mvn javafx:run

       Alternatively, run the Launcher class directly:
           smartfarm.Launcher

4. DATABASE SETUP INSTRUCTIONS
--------------------------------------------------------------------------------
    1. Open MySQL Workbench or the MySQL command-line client.
    2. Create the database:

           CREATE DATABASE smartfarm;
           USE smartfarm;

    3. Import the schema from the provided SQL file (if available):

           SOURCE path/to/schema.sql;

       Or create the core tables manually. The application uses the following
       tables: admin, manager, worker, plot, crop, device, sensor_reading,
       alert, task, harvest_record, attendance, notification.

    4. Ensure your MySQL server is running on localhost:3306 (default).
    5. Verify the credentials in db.properties match your MySQL user/password.

5. SOCKET PORT NUMBER
--------------------------------------------------------------------------------
  The built-in TCP Farm Server listens on port:  8080

  This socket server receives live sensor data from IoT devices (e.g. Arduino/
  ESP32). Each connected device sends readings in a text-based protocol over
  TCP. The server parses temperature, humidity, soil moisture values and
  fingerprint scan data from connected hardware.

================================================================================
