package com.mgaray.jira;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mgaray.mcp.McpTool;
import com.mgaray.mcp.ToolCallException;
import com.mgaray.oktaapp.common.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraToolsTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Records calls and serves canned responses keyed by path prefix.
    static class FakeJiraClient implements JiraClient {
        final List<String> getPaths = new ArrayList<>();
        final List<String> postPaths = new ArrayList<>();
        final List<Map<String, Object>> postBodies = new ArrayList<>();
        final Map<String, Map<String, Object>> cannedGets = new LinkedHashMap<>();
        Map<String, Object> postResponse = Map.of();

        @Override
        public Map<String, Object> get(String pathAndQuery) {
            getPaths.add(pathAndQuery);
            for (Map.Entry<String, Map<String, Object>> canned : cannedGets.entrySet()) {
                if (pathAndQuery.startsWith(canned.getKey())) {
                    return canned.getValue();
                }
            }
            return Map.of();
        }

        @Override
        public Map<String, Object> post(String path, Map<String, Object> body) {
            postPaths.add(path);
            postBodies.add(body);
            return postResponse;
        }
    }

    private final FakeJiraClient jira = new FakeJiraClient();

    private McpTool tool(String name) {
        return JiraTools.all(jira).stream()
                .filter(t -> t.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static Map<String, Object> asMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void allExposesSixToolsWithCorrectWriteFlags() {
        List<McpTool> tools = JiraTools.all(jira);
        assertEquals(6, tools.size());
        Map<String, Boolean> writeFlags = new LinkedHashMap<>();
        tools.forEach(t -> writeFlags.put(t.name(), t.isWrite()));
        assertEquals(Map.of(
                "jira_whoami", false,
                "jira_search", false,
                "jira_get_issue", false,
                "jira_create_issue", true,
                "jira_add_comment", true,
                "jira_transition_issue", true), writeFlags);
    }

    @Test
    void whoamiReturnsTrimmedProfile() {
        jira.cannedGets.put("/myself", Map.of(
                "accountId", "abc123", "displayName", "Service Bot",
                "emailAddress", "bot@example.com", "avatarUrls", Map.of("48x48", "http://...")));
        Map<String, Object> result = asMap(tool("jira_whoami").call(Map.of()));
        assertEquals("abc123", result.get("accountId"));
        assertEquals("Service Bot", result.get("displayName"));
        assertNull(result.get("avatarUrls"));
    }

    @Test
    void searchEncodesJqlAndSummarizesIssues() {
        jira.cannedGets.put("/search/jql", Map.of(
                "issues", List.of(Map.of(
                        "key", "SDD-1",
                        "fields", Map.of(
                                "summary", "Fix the thing",
                                "status", Map.of("name", "In Progress"),
                                "priority", Map.of("name", "High"),
                                "assignee", Map.of("displayName", "Marton")))),
                "nextPageToken", "tok123"));
        String output = tool("jira_search").call(Map.of("jql", "assignee = currentUser() ORDER BY updated DESC"));

        assertEquals(1, jira.getPaths.size());
        String path = jira.getPaths.get(0);
        assertTrue(path.startsWith("/search/jql?jql="), path);
        assertTrue(path.contains("assignee+%3D+currentUser%28%29"), path);
        assertTrue(path.contains("&maxResults=50"), path);
        assertTrue(path.contains("&fields=summary%2Cstatus%2Cpriority%2Cissuetype%2Cassignee"), path);

        Map<String, Object> result = asMap(output);
        assertEquals("tok123", result.get("nextPageToken"));
        List<Map<String, Object>> issues = JsonUtils.getNestedField(result, "issues");
        assertEquals("SDD-1", issues.get(0).get("key"));
        assertEquals("In Progress", issues.get(0).get("status"));
        assertEquals("Marton", issues.get(0).get("assignee"));
    }

    @Test
    void searchRequiresJql() {
        assertThrows(ToolCallException.class, () -> tool("jira_search").call(Map.of()));
    }

    @Test
    void getIssueFlattensAdfDescription() {
        jira.cannedGets.put("/issue/SDD-1", Map.of(
                "key", "SDD-1",
                "fields", Map.of(
                        "summary", "Fix the thing",
                        "status", Map.of("name", "To Do"),
                        "description", Map.of("type", "doc", "version", 1, "content", List.of(
                                Map.of("type", "paragraph", "content", List.of(
                                        Map.of("type", "text", "text", "line one"))),
                                Map.of("type", "paragraph", "content", List.of(
                                        Map.of("type", "text", "text", "line two"))))))));
        Map<String, Object> result = asMap(tool("jira_get_issue").call(Map.of("issueKey", "SDD-1")));
        assertEquals("SDD-1", result.get("key"));
        assertEquals("To Do", result.get("status"));
        assertEquals("line one\nline two", result.get("description"));
        assertTrue(jira.getPaths.get(0).startsWith("/issue/SDD-1?fields="), jira.getPaths.get(0));
    }

    @Test
    void createIssueBuildsAdfDescriptionAndMergesAdditionalFields() {
        jira.postResponse = Map.of("key", "SDD-42", "id", "10042", "self", "https://api/issue/10042");
        Map<String, Object> result = asMap(tool("jira_create_issue").call(Map.of(
                "projectKey", "SDD",
                "summary", "New issue",
                "description", "the details",
                "additionalFields", Map.of("labels", List.of("mcp")))));

        assertEquals("SDD-42", result.get("key"));
        assertEquals("/issue", jira.postPaths.get(0));
        Map<String, Object> fields = JsonUtils.getNestedMap(jira.postBodies.get(0), "fields");
        assertEquals(Map.of("key", "SDD"), fields.get("project"));
        assertEquals(Map.of("name", "Task"), fields.get("issuetype"));
        assertEquals("New issue", fields.get("summary"));
        assertEquals(List.of("mcp"), fields.get("labels"));
        assertEquals("doc", JsonUtils.getNestedField(fields, "description", "type"));
    }

    @Test
    void addCommentPostsAdfBody() {
        jira.postResponse = Map.of("id", "5001", "created", "2026-07-06T10:00:00.000+0000");
        Map<String, Object> result = asMap(tool("jira_add_comment").call(Map.of(
                "issueKey", "SDD-1", "body", "looks good")));
        assertEquals("5001", result.get("id"));
        assertEquals("/issue/SDD-1/comment", jira.postPaths.get(0));
        assertEquals("doc", JsonUtils.getNestedField(jira.postBodies.get(0), "body", "type"));
        assertEquals("looks good", Adf.toText(jira.postBodies.get(0).get("body")));
    }

    @Test
    void transitionIssueWithoutArgListsTransitions() {
        jira.cannedGets.put("/issue/SDD-1/transitions", Map.of("transitions", List.of(
                Map.of("id", "11", "name", "Start Progress", "to", Map.of("name", "In Progress")),
                Map.of("id", "21", "name", "Done", "to", Map.of("name", "Done")))));
        Map<String, Object> result = asMap(tool("jira_transition_issue").call(Map.of("issueKey", "SDD-1")));
        List<Map<String, Object>> transitions = JsonUtils.getNestedField(result, "transitions");
        assertEquals(2, transitions.size());
        assertEquals("In Progress", transitions.get(0).get("toStatus"));
        assertTrue(jira.postPaths.isEmpty());
    }

    @Test
    void transitionIssueResolvesNameToIdAndPosts() {
        jira.cannedGets.put("/issue/SDD-1/transitions", Map.of("transitions", List.of(
                Map.of("id", "11", "name", "Start Progress", "to", Map.of("name", "In Progress")))));
        String output = tool("jira_transition_issue").call(Map.of(
                "issueKey", "SDD-1", "transition", "start progress"));
        assertEquals("Transitioned SDD-1 to In Progress", output);
        assertEquals("/issue/SDD-1/transitions", jira.postPaths.get(0));
        assertEquals(Map.of("transition", Map.of("id", "11")), jira.postBodies.get(0));
    }

    @Test
    void transitionIssueRejectsUnknownTransition() {
        jira.cannedGets.put("/issue/SDD-1/transitions", Map.of("transitions", List.of(
                Map.of("id", "11", "name", "Start Progress", "to", Map.of("name", "In Progress")))));
        ToolCallException e = assertThrows(ToolCallException.class,
                () -> tool("jira_transition_issue").call(Map.of("issueKey", "SDD-1", "transition", "Reopen")));
        assertTrue(e.getMessage().contains("Start Progress"), e.getMessage());
    }

}
