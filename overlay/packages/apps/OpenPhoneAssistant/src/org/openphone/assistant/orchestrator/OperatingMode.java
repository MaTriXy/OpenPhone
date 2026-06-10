package org.openphone.assistant.orchestrator;

public enum OperatingMode {
    REVIEWED("reviewed"),
    TRUSTED_AUTOPILOT("trusted_autopilot"),
    YOLO("yolo"),
    DRY_RUN("dry_run");

    private final String mWireName;

    OperatingMode(String wireName) {
        mWireName = wireName;
    }

    public String wireName() {
        return mWireName;
    }
}
