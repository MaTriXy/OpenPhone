package org.openphone.assistant.orchestrator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One structured decision from the orchestrator about how to help with a
 * user message. Modes follow the master-plan orchestrator_decision schema.
 */
public final class OrchestratorDecision {
    public static final String MODE_ANSWER = "answer";
    public static final String MODE_CLARIFY = "clarify";
    public static final String MODE_RETRIEVE = "retrieve";
    public static final String MODE_INSPECT_SCREEN = "inspect_screen";
    public static final String MODE_ACT = "act";
    public static final String MODE_WATCH = "watch";
    public static final String MODE_MEMORY = "memory";
    public static final String MODE_STOP = "stop";

    private final String mMode;
    private final String mReply;
    private final String mTaskGoal;
    private final JSONArray mProposedActions;
    private final String mDeliverySurface;
    private final String mReason;
    private final OperatingMode mOperatingMode;

    private OrchestratorDecision(String mode, String reply, String taskGoal,
            JSONArray proposedActions, String deliverySurface, String reason,
            OperatingMode operatingMode) {
        mMode = isValidMode(mode) ? mode : MODE_ANSWER;
        mReply = reply == null ? "" : reply;
        mTaskGoal = taskGoal == null ? "" : taskGoal;
        mProposedActions = proposedActions == null ? new JSONArray() : proposedActions;
        mDeliverySurface = deliverySurface == null || deliverySurface.isEmpty()
                ? "chat" : deliverySurface;
        mReason = reason == null ? "" : reason;
        mOperatingMode = operatingMode == null ? OperatingMode.REVIEWED : operatingMode;
    }

    public static boolean isValidMode(String mode) {
        return MODE_ANSWER.equals(mode) || MODE_CLARIFY.equals(mode)
                || MODE_RETRIEVE.equals(mode) || MODE_INSPECT_SCREEN.equals(mode)
                || MODE_ACT.equals(mode) || MODE_WATCH.equals(mode)
                || MODE_MEMORY.equals(mode) || MODE_STOP.equals(mode);
    }

    public static OrchestratorDecision answer(String reply, String reason,
            OperatingMode operatingMode) {
        return new OrchestratorDecision(MODE_ANSWER, reply, "", null, "chat", reason,
                operatingMode);
    }

    public static OrchestratorDecision clarify(String question, String reason,
            OperatingMode operatingMode) {
        return new OrchestratorDecision(MODE_CLARIFY, question, "", null, "chat", reason,
                operatingMode);
    }

    public static OrchestratorDecision inspectScreen(String reason,
            OperatingMode operatingMode) {
        return new OrchestratorDecision(MODE_INSPECT_SCREEN, "", "", null, "chat", reason,
                operatingMode);
    }

    public static OrchestratorDecision agentTask(String taskGoal, String reason,
            OperatingMode operatingMode) {
        return new OrchestratorDecision(MODE_ACT, "", taskGoal, null, "chat", reason,
                operatingMode);
    }

    public static OrchestratorDecision oneShot(String mode, JSONArray proposedActions,
            String deliverySurface, String reason, OperatingMode operatingMode) {
        return new OrchestratorDecision(mode, "", "", proposedActions, deliverySurface,
                reason, operatingMode);
    }

    public static OrchestratorDecision stop(String reason, OperatingMode operatingMode) {
        return new OrchestratorDecision(MODE_STOP, "", "", null, "chat", reason,
                operatingMode);
    }

    public String mode() {
        return mMode;
    }

    public String reply() {
        return mReply;
    }

    public String taskGoal() {
        return mTaskGoal;
    }

    /** Array of {"tool": name, "arguments": {...}} entries for one-shot execution. */
    public JSONArray proposedActions() {
        return mProposedActions;
    }

    public boolean hasProposedActions() {
        return mProposedActions.length() > 0;
    }

    public String deliverySurface() {
        return mDeliverySurface;
    }

    public String reason() {
        return mReason;
    }

    public OperatingMode operatingMode() {
        return mOperatingMode;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("mode", mMode)
                .put("reply", mReply)
                .put("task_goal", mTaskGoal)
                .put("proposed_actions", mProposedActions)
                .put("delivery_surface", mDeliverySurface)
                .put("reason", mReason)
                .put("operating_mode", mOperatingMode.wireName());
    }
}
