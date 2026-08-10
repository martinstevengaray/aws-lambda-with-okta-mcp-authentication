package com.mgaray.oktaapp.mcp.jira.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mgaray.oktaapp.common.JsonUtils;
import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.jira.JiraClient;
import com.mgaray.oktaapp.mcp.ITool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddCommentTool implements ITool {

    private static final ToolDefinition toolDefinition = new ToolDefinition(
            "add_comment",
            "Add a comment to a Jira issue.",
            JsonSchema.fromObjectDescription(new AddCommentRequest(
                    "Issue key, e.g. SDD-1.",
                    "Comment text (plain text).")),
            JsonSchema.fromObjectDescription(new AddCommentResponse(
                    "Key of the issue that was commented on, e.g. SDD-1.",
                   "Id Jira assigned to the new comment, as a string."))
    );

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddCommentRequest(String key,
                                     String body) {}

    public record AddCommentResponse(String issueKey,
                                     String commentId) {}

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
        AddCommentRequest addCommentRequest = JsonUtils.convertToPojo(args, AddCommentRequest.class);
        validateRequest(addCommentRequest);
        AddCommentResponse addCommentResponse = jiraClient.addCommentDetail(addCommentRequest);
        return ITool.structuredContentResult("Added comment to " + addCommentResponse.issueKey(), addCommentResponse);
    }

    private void validateRequest(AddCommentRequest addCommentRequest) throws IllegalArgumentException {
        List<String> errors = new ArrayList<>();
        if (addCommentRequest.key() == null || addCommentRequest.key().isBlank()) {
            errors.add("key is required");
        }
        if (addCommentRequest.body() == null || addCommentRequest.body().isBlank()) {
            errors.add("body is required");
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }

}
