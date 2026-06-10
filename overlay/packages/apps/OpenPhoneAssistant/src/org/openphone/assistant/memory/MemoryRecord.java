package org.openphone.assistant.memory;

public final class MemoryRecord {
    public final long id;
    public final String type;
    public final String subject;
    public final String text;
    public final float confidence;
    public final long createdAtMillis;
    public final long updatedAtMillis;
    public final String evidenceJson;

    MemoryRecord(long id, String type, String subject, String text, float confidence,
            long createdAtMillis, long updatedAtMillis, String evidenceJson) {
        this.id = id;
        this.type = type == null ? "" : type;
        this.subject = subject == null ? "" : subject;
        this.text = text == null ? "" : text;
        this.confidence = confidence;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.evidenceJson = evidenceJson == null ? "{}" : evidenceJson;
    }
}
