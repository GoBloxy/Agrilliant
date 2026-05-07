package smartfarm.model;

import java.time.LocalDateTime;

/**
 * TODO: Notification delivers messages to users (in-app, push, email, SMS).
 *
 * TRIGGERS:
 * - Alert created (especially CRITICAL) → notify farm owner + assigned workers.
 * - Task assigned → notify the worker.
 * - Task overdue → notify farm owner.
 * - Disease detected with high confidence → notify farm owner.
 * - Drone mission completed → notify requester.
 * - Irrigation completed → notify plot owner.
 *
 * DELIVERY CHANNELS:
 * - IN_APP: shown in the UI notification bell (always).
 * - EMAIL: via JavaMail or SendGrid API.
 * - SMS: via Twilio API (for critical alerts).
 * - PUSH: via Firebase Cloud Messaging (if mobile app exists).
 *
 * Links to: User (recipient), optionally references an Alert, Task, or other entity.
 */
public class Notification {
    public enum Channel { IN_APP, EMAIL, SMS, PUSH }
    public enum Priority { NORMAL, URGENT }

    private int notificationId;
    private int userId;               // FK → users (recipient)
    private String title;
    private String message;
    private Channel channel;
    private Priority priority;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;     // nullable — null until user reads it
    private String referenceType;     // e.g. "ALERT", "TASK", "DISEASE_DETECTION"
    private Integer referenceId;      // FK to the related entity (polymorphic, nullable)

    // TODO: Implement constructors, getters, setters
    // TODO: Implement DAO (NotificationDAO):
    //       - save, getById, getByUser(userId), getUnreadByUser(userId), markAsRead(id)
    // TODO: Implement Service (NotificationService):
    //       - notify(userId, title, message, channel, priority, refType, refId)
    //       - notifyAll(List<userId>, ...) for broadcast
    //       - integrate with AlertService to auto-notify on critical alerts
    //       - integrate with TaskService to notify on assignment/overdue
    // TODO: Create utility classes for each channel:
    //       - EmailSender (JavaMail / SendGrid)
    //       - SmsSender (Twilio)
    //       - PushSender (Firebase)
}
