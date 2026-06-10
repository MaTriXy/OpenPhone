package org.openphone.assistant.watchers;

public final class WatcherRecord {
    public final long id;
    public final String type;
    public final String title;
    public final String conditionJson;
    public final String scheduleJson;
    public final String sessionTarget;
    public final String deliveryJson;
    public final String status;
    public final long createdAtMillis;
    public final long updatedAtMillis;
    public final long nextRunAtMillis;
    public final long runningAtMillis;
    public final long lastRunAtMillis;
    public final String lastResultHash;
    public final int failureCount;
    public final long failureAlertAtMillis;

    WatcherRecord(long id, String type, String title, String conditionJson,
            String scheduleJson, String sessionTarget, String deliveryJson,
            String status, long createdAtMillis, long updatedAtMillis,
            long nextRunAtMillis, long runningAtMillis, long lastRunAtMillis,
            String lastResultHash, int failureCount, long failureAlertAtMillis) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.conditionJson = conditionJson;
        this.scheduleJson = scheduleJson;
        this.sessionTarget = sessionTarget;
        this.deliveryJson = deliveryJson;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.nextRunAtMillis = nextRunAtMillis;
        this.runningAtMillis = runningAtMillis;
        this.lastRunAtMillis = lastRunAtMillis;
        this.lastResultHash = lastResultHash;
        this.failureCount = failureCount;
        this.failureAlertAtMillis = failureAlertAtMillis;
    }
}
