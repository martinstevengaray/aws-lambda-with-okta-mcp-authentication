package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;
import com.mgaray.oktaapp.mcp.tools.JiraIssueSchemas;

import java.util.List;
import java.util.Map;

public class GetIssueTool implements ITool {

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "get_issue",
            "Get a single Jira issue by key, including its description.",
            JsonSchema.object(
                    Map.of("key", JsonSchema.string("Issue key, e.g. SDD-1.")),
                    List.of("key")),
            JiraIssueSchemas.jiraIssueDetailSchema());

    private final JiraClient jiraClient;

    public GetIssueTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        String key = ITool.getString(args, "key");
        JiraClient.IssueDetail issue = jiraClient.issueDetail(key);
        return ITool.structuredContentResult(JiraClient.formatIssue(issue), issue);
    }

}
