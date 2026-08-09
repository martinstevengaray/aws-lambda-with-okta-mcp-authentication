package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;

import java.util.List;
import java.util.Map;

/**
 * Schema fragments shared by the tools that return Jira issue rows. Both
 * {@code list_my_issues} and {@code search_issues} return the same
 * {@link com.mgaray.oktaapp.mcp.jira.JiraClient.IssueSummary} shape, so the
 * schema describing it lives in one place rather than once per tool.
 */
public final class JiraSchemas {

    private JiraSchemas() {}

    private static final JsonSchema JIRA_ISSUE_SCHEMA = JsonSchema.object(
            "A single Jira issue.",
            Map.of(
                    "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                    "status", JsonSchema.string("Current workflow status, e.g. In Progress."),
                    "priority", JsonSchema.string("Priority name, or \"-\" if unset."),
                    "summary", JsonSchema.string("Short summary / title of the issue.")),
            List.of("key", "status", "priority", "summary"));

    public static JsonSchema createJiraIssuesOutputSchema(String description) {
        return JsonSchema.object(
                Map.of("issues", JsonSchema.array(description, JIRA_ISSUE_SCHEMA)),
                List.of("issues"));
    }
}
