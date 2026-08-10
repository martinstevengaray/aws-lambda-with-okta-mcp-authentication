package com.mgaray.oktaapp.mcp.jira.tools;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.ITool;
import com.mgaray.oktaapp.mcp.jira.JiraIssueSchemas;
import com.mgaray.oktaapp.mcp.jira.JiraModels.IssueSummary;

import java.util.List;
import java.util.Map;

public class ListMyIssuesTool implements ITool {

    private static final int DEFAULT_MAX_RESULTS = 50;

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "list_my_issues",
            "List Jira issues assigned to you, most recently updated first.",
            JsonSchema.object(
                    Map.of("maxResults", JsonSchema.integer("Maximum number of issues to return (default 50).")),
                    List.of()),
            JiraIssueSchemas.jiraIssueSummaryListSchema("The issues assigned to you, most recently updated first."));

    private final JiraClient jiraClient;

    public ListMyIssuesTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        int maxResults = ITool.getInt(args, "maxResults", DEFAULT_MAX_RESULTS);
        List<IssueSummary> issues = jiraClient.myIssueSummaries(maxResults);
        return ITool.structuredContentResult(Map.of("issues", issues));
    }

}
