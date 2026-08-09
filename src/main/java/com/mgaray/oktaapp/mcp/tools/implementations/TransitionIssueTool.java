package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;
import com.mgaray.oktaapp.mcp.tools.JiraSchemas;

import java.util.List;
import java.util.Map;

public class TransitionIssueTool implements ITool {

    public static final ToolDefinition toolDefinition = new ToolDefinition(
            "transition_issue",
            "Move a Jira issue to a new status (e.g. In Progress, Done).",
            JsonSchema.object(
                    Map.of(
                            "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                            "status", JsonSchema.string("Target status or transition name, e.g. \"In Progress\".")),
                    List.of("key", "status")),
            JiraSchemas.transitionJiraIssueOutputSchema());

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
