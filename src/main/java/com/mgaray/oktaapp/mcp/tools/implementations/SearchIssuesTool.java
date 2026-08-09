package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;
import com.mgaray.oktaapp.mcp.tools.JiraSchemas;

import java.util.List;
import java.util.Map;

public class SearchIssuesTool implements ITool {

    private static final int DEFAULT_MAX_RESULTS = 50;

    public static final ToolDefinition toolDefinition = new ToolDefinition(
            "search_issues",
            "Search Jira issues with a JQL query.",
            JsonSchema.object(
                    Map.of(
                            "jql", JsonSchema.string("A JQL query, e.g. \"project = SDD AND status = 'To Do'\"."),
                            "maxResults", JsonSchema.integer("Maximum number of issues to return (default 50).")),
                    List.of("jql")),
            JiraSchemas.createIssuesOutputSchema("The issues matching the query."));

    private final JiraClient jiraClient;

    public SearchIssuesTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        String jql = ITool.getString(args, "jql");
        int maxResults = ITool.getInt(args, "maxResults", DEFAULT_MAX_RESULTS);
        List<JiraClient.IssueSummary> issues = jiraClient.searchIssueSummaries(jql, maxResults);
        return ITool.structuredContentResult(Map.of("issues", issues));
    }

}
