package smartfarm.model;

public class Notification {
    public enum RecipientRole { ADMIN, MANAGER, WORKER }
    public enum Channel { PUSH, SMS, WHATSAPP, EMAIL }
    public enum Status { QUEUED, SENT, FAILED }

    private int notificationId;
    private int recipientId;          // polymorphic FK (admin|manager|worker) — enforced in app
    private RecipientRole recipientRole;
    private Channel channel;
    private String subject;
    private String body;
    private Status status;
    private String relatedEntityType;
    private Integer relatedEntityId;  // nullable

    public Notification(int notificationId, int recipientId, RecipientRole recipientRole, Channel channel,
                        String subject, String body, Status status, String relatedEntityType, Integer relatedEntityId) {
        this.notificationId = notificationId;
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }

    public Notification(int recipientId, RecipientRole recipientRole, Channel channel,
                        String subject, String body, String relatedEntityType, Integer relatedEntityId) {
        this.notificationId = -1;
        this.recipientId = recipientId;
        this.recipientRole = recipientRole;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.status = Status.QUEUED;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }

    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }
    public int getRecipientId() { return recipientId; }
    public void setRecipientId(int recipientId) { this.recipientId = recipientId; }
    public RecipientRole getRecipientRole() { return recipientRole; }
    public void setRecipientRole(RecipientRole recipientRole) { this.recipientRole = recipientRole; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }
    public Integer getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(Integer relatedEntityId) { this.relatedEntityId = relatedEntityId; }
}
