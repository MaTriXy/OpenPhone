package org.openphone.assistant.commitments;

public final class CommitmentRecord {
    public final long id;
    public final String title;
    public final String description;
    public final String triggerType;
    public final String triggerSpecJson;
    public final long dueAtMillis;
    public final long expiresAtMillis;
    public final String status;
    public final float confidence;
    public final String evidenceJson;
    public final long createdAtMillis;
    public final long updatedAtMillis;

    CommitmentRecord(long id, String title, String description, String triggerType,
            String triggerSpecJson, long dueAtMillis, long expiresAtMillis, String status,
            float confidence, String evidenceJson, long createdAtMillis, long updatedAtMillis) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.triggerType = triggerType == null ? "" : triggerType;
        this.triggerSpecJson = triggerSpecJson == null ? "{}" : triggerSpecJson;
        this.dueAtMillis = dueAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.status = status == null ? "" : status;
        this.confidence = confidence;
        this.evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
    }
}
