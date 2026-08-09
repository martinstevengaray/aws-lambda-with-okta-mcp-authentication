package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.mcp.Models;
import com.mgaray.oktaapp.mcp.Models.ToolDefinition;
import com.mgaray.oktaapp.mcp.Models.JsonSchema;
import com.mgaray.oktaapp.mcp.jira.JiraClient;

import java.util.List;
import java.util.Map;

public class ListMyIssuesTool implements ITool {

    /**
     * The shape of one issue in {@code structuredContent.issues}. Every field is
     * required because {@link JiraClient#myIssueSummaries} substitutes a placeholder
     * ("?", "-", "") rather than omitting a field Jira didn't return.
     */
    private static final JsonSchema ISSUE = Models.JsonSchema.object(
            "A single Jira issue.",
            Map.of(
                    "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                    "status", JsonSchema.string("Current workflow status, e.g. In Progress."),
                    "priority", JsonSchema.string("Priority name, or \"-\" if unset."),
                    "summary", JsonSchema.string("Short summary / title of the issue.")),
            List.of("key", "status", "priority", "summary"));

    public static final ToolDefinition toolDefinition = new ToolDefinition(
    "list_my_issues",
"List Jira issues assigned to you, most recently updated first.",
            JsonSchema.object(Map.of(
                "maxResults", Models.JsonSchema.integer("Maximum number of issues to return (default 50).")),
            List.of()),
            // An outputSchema root must be an object, so the array is wrapped in "issues".
            JsonSchema.object(Map.of(
                "issues", Models.JsonSchema.array("The issues assigned to you, most recently updated first.", ISSUE)),
            List.of("issues")));

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
        int maxResults = 50;
        try {
            maxResults = (int)Double.parseDouble(args.get("maxResults").toString()); //parseDouble so 5.0 is acceptable
        } catch(Exception ignored) {} //do nothing keep defaults
        List<JiraClient.IssueSummary> issues = jiraClient.myIssueSummaries(maxResults);
        // The text block stays for clients that ignore structuredContent; the spec asks
        // that a structured result also be readable as text.
        return Map.of(
                "content", List.of(textContent(JiraClient.formatSummaries(issues))),
                "structuredContent", Map.of("issues", issues));
    }
    private static Map<String, Object> textContent(String text) {
        return Map.of("type", "text", "text", text);
    }
}
