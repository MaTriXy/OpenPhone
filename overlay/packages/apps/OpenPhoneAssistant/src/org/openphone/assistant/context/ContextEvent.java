package org.openphone.assistant.context;

public final class ContextEvent {
    public final long id;
    public final String sourceType;
    public final String sourceApp;
    public final String sourceRecordId;
    public final long observedAtMillis;
    public final String title;
    public final String text;
    public final String payloadJson;

    ContextEvent(long id, String sourceType, String sourceApp, String sourceRecordId,
            long observedAtMillis, String title, String text, String payloadJson) {
        this.id = id;
        this.sourceType = sourceType == null ? "" : sourceType;
        this.sourceApp = sourceApp == null ? "" : sourceApp;
        this.sourceRecordId = sourceRecordId == null ? "" : sourceRecordId;
        this.observedAtMillis = observedAtMillis;
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
        this.payloadJson = payloadJson == null ? "" : payloadJson;
    }
}
