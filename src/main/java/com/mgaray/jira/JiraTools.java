package com.mgaray.jira;

import com.mgaray.mcp.McpTool;
import com.mgaray.mcp.ToolCallException;
import com.mgaray.oktaapp.common.HttpUtils;
import com.mgaray.oktaapp.common.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// The six JIRA tools exposed over MCP, mirroring (and extending) the claude-skills jira scripts.
public class JiraTools {

    public static List<McpTool> all(JiraClient jira) {
        return List.of(
                tool("jira_whoami",
                        "Return the JIRA account the server is acting as (service account). Useful as an auth check.",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                        false,
                        arguments -> whoami(jira)),
                tool("jira_search",
                        "Search JIRA issues with a JQL query. Returns key, status and requested fields per issue, "
                                + "plus nextPageToken when more results exist.",
                        Map.of("type", "object", "required", List.of("jql"), "properties", Map.of(
                                "jql", Map.of("type", "string",
                                        "description", "JQL query, e.g. \"assignee = currentUser() ORDER BY updated DESC\""),
                                "fields", Map.of("type", "string",
                                        "description", "Comma-separated field list",
                                        "default", "summary,status,priority,issuetype,assignee"),
                                "maxResults", Map.of("type", "integer", "minimum", 1, "maximum", 100, "default", 50))),
                        false,
                        arguments -> search(jira, arguments)),
                tool("jira_get_issue",
                        "Read a single JIRA issue by key. Descriptions are returned as plain text.",
                        Map.of("type", "object", "required", List.of("issueKey"), "properties", Map.of(
                                "issueKey", Map.of("type", "string", "description", "Issue key, e.g. SDD-1"),
                                "fields", Map.of("type", "string",
                                        "description", "Comma-separated field list",
                                        "default", "summary,status,assignee,priority,issuetype,description"))),
                        false,
                        arguments -> getIssue(jira, arguments)),
                tool("jira_create_issue",
                        "Create a JIRA issue. Description is plain text (converted to Atlassian Document Format).",
                        Map.of("type", "object", "required", List.of("projectKey", "summary"), "properties", Map.of(
                                "projectKey", Map.of("type", "string", "description", "Project key, e.g. SDD"),
                                "summary", Map.of("type", "string"),
                                "issueType", Map.of("type", "string", "default", "Task"),
                                "description", Map.of("type", "string",
                                        "description", "Plain text; converted to ADF"),
                                "additionalFields", Map.of("type", "object",
                                        "description", "Extra raw JIRA fields merged into the create payload"))),
                        true,
                        arguments -> createIssue(jira, arguments)),
                tool("jira_add_comment",
                        "Add a comment to a JIRA issue. Body is plain text (converted to Atlassian Document Format).",
                        Map.of("type", "object", "required", List.of("issueKey", "body"), "properties", Map.of(
                                "issueKey", Map.of("type", "string"),
                                "body", Map.of("type", "string", "description", "Plain text; converted to ADF"))),
                        true,
                        arguments -> addComment(jira, arguments)),
                tool("jira_transition_issue",
                        "Transition a JIRA issue to another status. Omit 'transition' to list the available transitions.",
                        Map.of("type", "object", "required", List.of("issueKey"), "properties", Map.of(
                                "issueKey", Map.of("type", "string"),
                                "transition", Map.of("type", "string",
                                        "description", "Transition id or name. Omit to list available transitions."))),
                        true,
                        arguments -> transitionIssue(jira, arguments)));
    }

    private static String whoami(JiraClient jira) {
        Map<String, Object> me = jira.get("/myself");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", me.get("accountId"));
        result.put("displayName", me.get("displayName"));
        result.put("emailAddress", me.get("emailAddress"));
        return JsonUtils.toString(result);
    }

    private static String search(JiraClient jira, Map<String, Object> arguments) {
        String jql = requireString(arguments, "jql");
        String fields = stringArg(arguments, "fields", "summary,status,priority,issuetype,assignee");
        int maxResults = Math.clamp(intArg(arguments, "maxResults", 50), 1, 100);
        String query = "/search/jql?jql=" + HttpUtils.urlEncode(jql)
                + "&fields=" + HttpUtils.urlEncode(fields)
                + "&maxResults=" + maxResults;
        if (arguments.get("nextPageToken") instanceof String token && !token.isEmpty()) {
            query += "&nextPageToken=" + HttpUtils.urlEncode(token);
        }
        Map<String, Object> response = jira.get(query);
        List<Map<String, Object>> issues = new ArrayList<>();
        if (response.get("issues") instanceof List<?> rawIssues) {
            for (Object rawIssue : rawIssues) {
                if (rawIssue instanceof Map<?, ?> issue) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> issueMap = (Map<String, Object>) issue;
                    issues.add(summarizeIssue(issueMap));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issues", issues);
        // /search/jql pages via nextPageToken and reports no total.
        if (response.get("nextPageToken") != null) {
            result.put("nextPageToken", response.get("nextPageToken"));
        }
        return JsonUtils.toString(result);
    }

    private static String getIssue(JiraClient jira, Map<String, Object> arguments) {
        String issueKey = requireString(arguments, "issueKey");
        String fields = stringArg(arguments, "fields", "summary,status,assignee,priority,issuetype,description");
        Map<String, Object> issue = jira.get("/issue/" + issueKey + "?fields=" + HttpUtils.urlEncode(fields));
        return JsonUtils.toString(summarizeIssue(issue));
    }

    private static String createIssue(JiraClient jira, Map<String, Object> arguments) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", requireString(arguments, "projectKey")));
        fields.put("issuetype", Map.of("name", stringArg(arguments, "issueType", "Task")));
        fields.put("summary", requireString(arguments, "summary"));
        if (arguments.get("description") instanceof String description && !description.isEmpty()) {
            fields.put("description", Adf.fromText(description));
        }
        if (arguments.get("additionalFields") instanceof Map<?, ?> additionalFields) {
            for (Map.Entry<?, ?> entry : additionalFields.entrySet()) {
                fields.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Map<String, Object> created = jira.post("/issue", Map.of("fields", fields));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", created.get("key"));
        result.put("id", created.get("id"));
        result.put("self", created.get("self"));
        return JsonUtils.toString(result);
    }

    private static String addComment(JiraClient jira, Map<String, Object> arguments) {
        String issueKey = requireString(arguments, "issueKey");
        String body = requireString(arguments, "body");
        Map<String, Object> comment = jira.post("/issue/" + issueKey + "/comment",
                Map.of("body", Adf.fromText(body)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.get("id"));
        result.put("created", comment.get("created"));
        return JsonUtils.toString(result);
    }

    private static String transitionIssue(JiraClient jira, Map<String, Object> arguments) {
        String issueKey = requireString(arguments, "issueKey");
        Map<String, Object> response = jira.get("/issue/" + issueKey + "/transitions");
        List<Map<String, Object>> available = new ArrayList<>();
        if (response.get("transitions") instanceof List<?> transitions) {
            for (Object transition : transitions) {
                if (transition instanceof Map<?, ?> t) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", t.get("id"));
                    entry.put("name", t.get("name"));
                    if (t.get("to") instanceof Map<?, ?> to) {
                        entry.put("toStatus", to.get("name"));
                    }
                    available.add(entry);
                }
            }
        }
        String requested = stringArg(arguments, "transition", "");
        if (requested.isEmpty()) {
            return JsonUtils.toString(Map.of("transitions", available));
        }
        for (Map<String, Object> transition : available) {
            if (requested.equals(transition.get("id"))
                    || requested.equalsIgnoreCase(String.valueOf(transition.get("name")))) {
                jira.post("/issue/" + issueKey + "/transitions",
                        Map.of("transition", Map.of("id", transition.get("id"))));
                return "Transitioned " + issueKey + " to " + transition.get("toStatus");
            }
        }
        throw new ToolCallException("no transition '" + requested + "' on " + issueKey
                + ", available: " + JsonUtils.toString(available));
    }

    // key + fields, with object fields collapsed to their name and ADF descriptions flattened to text.
    private static Map<String, Object> summarizeIssue(Map<String, Object> issue) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", issue.get("key"));
        Map<String, Object> fields = JsonUtils.getNestedMap(issue, "fields");
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            summary.put(field.getKey(), simplifyField(field.getKey(), field.getValue()));
        }
        return summary;
    }

    private static Object simplifyField(String name, Object value) {
        if ("description".equals(name) || "environment".equals(name)) {
            return (value == null) ? null : Adf.toText(value);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.get("displayName") != null) {
                return map.get("displayName");
            }
            if (map.get("name") != null) {
                return map.get("name");
            }
        }
        return value;
    }

    private static String requireString(Map<String, Object> arguments, String name) {
        if (arguments.get(name) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new ToolCallException("missing required argument: " + name);
    }

    private static String stringArg(Map<String, Object> arguments, String name, String defaultValue) {
        return (arguments.get(name) instanceof String value && !value.isBlank()) ? value : defaultValue;
    }

    private static int intArg(Map<String, Object> arguments, String name, int defaultValue) {
        return (arguments.get(name) instanceof Number value) ? value.intValue() : defaultValue;
    }

    private static McpTool tool(String name, String description, Map<String, Object> inputSchema,
                                boolean isWrite, Function<Map<String, Object>, String> impl) {
        return new McpTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return inputSchema;
            }

            @Override
            public boolean isWrite() {
                return isWrite;
            }

            @Override
            public String call(Map<String, Object> arguments) {
                return impl.apply(arguments);
            }
        };
    }

}
