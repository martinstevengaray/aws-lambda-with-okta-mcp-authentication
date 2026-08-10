package com.mgaray.oktaapp.mcp.tools.implementations;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.tools.ITool;

import java.util.List;
import java.util.Map;

public class AddCommentTool implements ITool {

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "add_comment",
            "Add a comment to a Jira issue.",
            JsonSchema.object(
                    Map.of(
                            "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                            "body", JsonSchema.string("Comment text (plain text).")),
                    List.of("key", "body")),
            JsonSchema.object(
                    Map.of(
                            "issueKey", JsonSchema.string("Key of the issue that was commented on, e.g. SDD-1."),
                            "commentId", JsonSchema.string("Id Jira assigned to the new comment, as a string.")),
                    List.of("issueKey", "commentId"))
    );

    private final JiraClient jiraClient;

    public AddCommentTool(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    @Override
    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }

    @Override
    public Map<String, Object> callTool(Map<String, Object> args) {
        JiraClient.AddedComment added = jiraClient.addCommentDetail(
                ITool.getString(args, "key"),
                ITool.getString(args, "body"));
        return ITool.structuredContentResult("Added comment to " + added.issueKey(), added);
    }

}
