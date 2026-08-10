package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;

import java.util.List;
import java.util.Map;

public class TransitionIssueTool implements ITool {

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "transition_issue",
            "Move a Jira issue to a new status (e.g. In Progress, Done).",
            JsonSchema.object(
                    Map.of(
                            "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                            "status", JsonSchema.string("Target status or transition name, e.g. \"In Progress\".")),
                    List.of("key", "status")),JsonSchema.object(
            Map.of(
                    "key", JsonSchema.string("Key of the issue that was moved, e.g. SDD-1."),
                    "status", JsonSchema.string("Status the issue is in after the move, e.g. In Progress."),
                    "transition", JsonSchema.string("Name of the transition that was applied, e.g. Start Progress.")),
            List.of("key", "status", "transition"))
    );

    private final JiraClient jiraClient;

    public TransitionIssueTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        JiraClient.TransitionedIssue moved = jiraClient.transitionIssueDetail(
                ITool.getString(args, "key"),
                ITool.getString(args, "status"));
        // Report the status the issue actually landed in, not the caller's wording.
        return ITool.structuredContentResult(
                "Transitioned " + moved.key() + " -> " + moved.status(), moved);
    }

}
