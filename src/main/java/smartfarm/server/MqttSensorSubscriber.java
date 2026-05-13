package smartfarm.server;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import smartfarm.model.SensorReading;
import smartfarm.service.LiveSensorData;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

/**
 * Client-side MQTT subscriber.
 * Every JavaFX client instance (on any machine / .exe) subscribes to the
 * sensor topic on the MQTT broker. When a message arrives, it is parsed
 * and pushed into {@link LiveSensorData} — so dashboards, monitoring charts,
 * and alerts update in real time across ALL connected clients.
 *
 * Internally, Paho uses TCP sockets and its own reader/writer threads.
 */
public class MqttSensorSubscriber {

    private static MqttSensorSubscriber instance;

    private MqttAsyncClient client;
    private final Gson gson = new Gson();
    private volatile boolean connected = false;

    private MqttSensorSubscriber() {}

    public static synchronized MqttSensorSubscriber getInstance() {
        if (instance == null) instance = new MqttSensorSubscriber();
        return instance;
    }

    /**
     * Connect to the MQTT broker and subscribe to sensor topics.
     * Runs on a daemon thread so it doesn't block the JavaFX UI thread.
     */
    public void start() {
        Thread subThread = new Thread(() -> {
            try {
                Properties props = loadConfig();
                String broker = props.getProperty("mqtt.broker", "tcp://broker.hivemq.com:1883");
                String topicPrefix = props.getProperty("mqtt.topic.prefix", "agrilliant/sensors");
                int qos = Integer.parseInt(props.getProperty("mqtt.qos", "1"));
                String clientId = props.getProperty("mqtt.client.id.prefix", "agrilliant-")
                        + "client-" + UUID.randomUUID().toString().substring(0, 8);

                client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());

                // Set up callback BEFORE connecting
                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        System.out.println("[MqttSubscriber] " + (reconnect ? "Reconnected" : "Connected") + " to " + serverURI);
                        try {
                            // Subscribe to all device topics: agrilliant/sensors/#
                            client.subscribe(topicPrefix + "/#", qos);
                            System.out.println("[MqttSubscriber] Subscribed to " + topicPrefix + "/#");
                        } catch (MqttException e) {
                            System.err.println("[MqttSubscriber] Subscribe failed: " + e.getMessage());
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        connected = false;
                        System.err.println("[MqttSubscriber] Connection lost: " + cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        handleMessage(topic, message);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // Not used for subscriber
                    }
                });

                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setCleanSession(true);
                opts.setAutomaticReconnect(true);
                opts.setConnectionTimeout(10);
                opts.setKeepAliveInterval(30);

                String user = props.getProperty("mqtt.username");
                String pass = props.getProperty("mqtt.password");
                if (user != null && !user.isBlank()) opts.setUserName(user);
                if (pass != null && !pass.isBlank()) opts.setPassword(pass.toCharArray());

                // TLS support — required for ssl:// brokers like HiveMQ Cloud
                if (broker.startsWith("ssl://")) {
                    opts.setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault());
                }

                client.connect(opts).waitForCompletion(10_000);
                connected = true;

            } catch (Exception e) {
                System.err.println("[MqttSubscriber] Failed to start: " + e.getMessage());
                connected = false;
            }
        }, "MqttSubscriber-Connect");
        subThread.setDaemon(true);
        subThread.start();
    }

    /**
     * Handle incoming MQTT message — parse JSON and push to LiveSensorData.
     * This runs on Paho's internal receiver thread (multi-threading in action).
     */
    private void handleMessage(String topic, MqttMessage message) {
        try {
            String json = new String(message.getPayload());
            MqttBridge.SensorPayload payload = gson.fromJson(json, MqttBridge.SensorPayload.class);

            if (payload == null || payload.deviceCode == null) return;

            SensorReading reading = new SensorReading(
                    0, // device_id resolved later if needed
                    payload.temperature,
                    payload.humidity,
                    payload.soilMoisture,
                    LocalDateTime.now()
            );

            // Push directly into LiveSensorData — updates all UI charts/labels instantly
            LiveSensorData.getInstance().update(reading, payload.deviceCode);

        } catch (Exception e) {
            System.err.println("[MqttSubscriber] Failed to parse message on " + topic + ": " + e.getMessage());
        }
    }

    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                System.out.println("[MqttSubscriber] Disconnected");
            }
        } catch (MqttException e) {
            System.err.println("[MqttSubscriber] Disconnect error: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/mqtt.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) {
            System.err.println("[MqttSubscriber] Could not load mqtt.properties, using defaults");
        }
        return props;
    }
}
