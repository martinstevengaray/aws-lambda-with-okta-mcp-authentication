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

    /**
     * One issue in {@code structuredContent.issues}. Every field is required
     * because the client substitutes a placeholder ("?", "-", "") rather than
     * omitting a field Jira didn't return.
     */
    static final JsonSchema ISSUE = JsonSchema.object(
            "A single Jira issue.",
            Map.of(
                    "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                    "status", JsonSchema.string("Current workflow status, e.g. In Progress."),
                    "priority", JsonSchema.string("Priority name, or \"-\" if unset."),
                    "summary", JsonSchema.string("Short summary / title of the issue.")),
            List.of("key", "status", "priority", "summary"));

    /**
     * The outputSchema for a tool returning issue rows. An outputSchema root must
     * be an object, so the array is wrapped in an "issues" property.
     */
    public static JsonSchema createIssuesOutputSchema(String description) {
        return JsonSchema.object(
                Map.of("issues", JsonSchema.array(description, ISSUE)),
                List.of("issues"));
    }
}
