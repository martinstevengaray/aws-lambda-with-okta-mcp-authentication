package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.mcp.Models.JsonSchema;

import java.util.List;
import java.util.Map;

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

    public static JsonSchema listAndSearchJiraIssuesOutputSchema(String description) {
        return JsonSchema.object(
                Map.of("issues", JsonSchema.array(description, JIRA_ISSUE_SCHEMA)),
                List.of("issues"));
    }

    /**
     * A single issue with its description. No wrapper property is needed here —
     * unlike the list tools, the result is already an object, which is what an
     * outputSchema root must be.
     */
    public static JsonSchema getJiraIssueDetailOutputSchema() {
        return JsonSchema.object(
                Map.of(
                        "key", JsonSchema.string("Issue key, e.g. SDD-1."),
                        "status", JsonSchema.string("Current workflow status, e.g. In Progress."),
                        "priority", JsonSchema.string("Priority name, or \"-\" if unset."),
                        "summary", JsonSchema.string("Short summary / title of the issue."),
                        "assignee", JsonSchema.string("Display name of the assignee, or \"Unassigned\"."),
                        "description", JsonSchema.string(
                                "Issue description as plain text, flattened from Atlassian Document Format."
                                        + " Empty string when the issue has no description.")),
                List.of("key", "status", "priority", "summary", "assignee", "description"));
    }

    /**
     * What a create returns: just the identifiers Jira assigns. The caller already
     * knows the summary and description it sent, so echoing them back is noise.
     */
    /**
     * What adding a comment returns: which issue was commented on, and the id of the
     * new comment. The comment body is not echoed back — the caller just sent it.
     */
    public static JsonSchema addJiraCommentOutputSchema() {
        return JsonSchema.object(
                Map.of(
                        "issueKey", JsonSchema.string("Key of the issue that was commented on, e.g. SDD-1."),
                        "commentId", JsonSchema.string("Id Jira assigned to the new comment, as a string.")),
                List.of("issueKey", "commentId"));
    }

    /**
     * What a transition returns. {@code status} is the resulting status rather than an
     * echo of the request, since the caller may have named a transition instead.
     */
    public static JsonSchema transitionJiraIssueOutputSchema() {
        return JsonSchema.object(
                Map.of(
                        "key", JsonSchema.string("Key of the issue that was moved, e.g. SDD-1."),
                        "status", JsonSchema.string("Status the issue is in after the move, e.g. In Progress."),
                        "transition", JsonSchema.string("Name of the transition that was applied, e.g. Start Progress.")),
                List.of("key", "status", "transition"));
    }

    public static JsonSchema createJiraIssueOutputSchema() {
        return JsonSchema.object(
                Map.of(
                        "key", JsonSchema.string("Key of the newly created issue, e.g. SDD-1."),
                        "id", JsonSchema.string("Numeric id of the newly created issue, as a string.")),
                List.of("key", "id"));
    }
}
