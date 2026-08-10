package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;

import java.util.List;
import java.util.Map;

public class CreateIssueTool implements ITool {

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "create_issue",
            "Create a new Jira issue.",
            JsonSchema.object(
                    Map.of(
                            "projectKey", JsonSchema.string("Project key the issue belongs to, e.g. SDD."),
                            "issueType", JsonSchema.string("Issue type name, e.g. Task, Bug, Story."),
                            "summary", JsonSchema.string("Short summary / title of the issue."),
                            "description", JsonSchema.string("Optional longer description (plain text).")),
                    List.of("projectKey", "issueType", "summary")),
            JsonSchema.object(
                    Map.of(
                            "key", JsonSchema.string("Key of the newly created issue, e.g. SDD-1."),
                            "id", JsonSchema.string("Numeric id of the newly created issue, as a string.")),
                    List.of("key", "id"))
    );

    private final JiraClient jiraClient;

    public CreateIssueTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        JiraClient.CreatedIssue created = jiraClient.createIssueDetail(
                ITool.getString(args, "projectKey"),
                ITool.getString(args, "issueType"),
                ITool.getString(args, "summary"),
                ITool.getString(args, "description", null));
        // Unlike the read tools, the text block confirms what happened rather than
        // restating the payload: {"key":"SDD-5"} alone doesn't say an issue was created.
        return ITool.structuredContentResult("Created issue " + created.key(), created);
    }

}
