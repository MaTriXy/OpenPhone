package org.openphone.assistant.model;

public interface ModelAdapter {
    String name();
    String providerDisplayName();
    String modelName();
    boolean usesCloud();
    String privacyDisclosure();
    /**
     * Full orchestrator decision: returns a JSON string with mode
     * (answer|clarify|retrieve|inspect_screen|act|watch|memory|stop), reply,
     * task_goal, proposed_actions, delivery_surface, reason.
     */
    String decideOrchestration(String userMessage, boolean hasActiveTask,
            String recentConversationJson);
    String chat(String userMessage);
    String answerScreenQuestion(String userMessage, String screenJson);
    String runTask(String taskId, String userGoal, ToolExecutor executor);
    void cancel();

    interface ToolExecutor {
        String callTool(String toolName, String argumentsJson);
        boolean isCancelled();
    }
}
