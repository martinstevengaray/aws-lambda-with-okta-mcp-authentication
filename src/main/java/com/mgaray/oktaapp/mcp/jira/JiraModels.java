package com.mgaray.oktaapp.mcp.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class JiraModels {

    public record IssueSummary(String key,
                               String status,
                               String priority,
                               String summary) {}

    public record IssueDetail(String key,
                              String summary,
                              String status,
                              String priority,
                              String assignee,
                              String description) {}

    public record CreatedIssue(String key,
                               String id) {}

    public record TransitionedIssue(String key,
                                    String status,  //status that issue landed in, not necessarily what was requested
                                    String transition) {}


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddCommentRequest(String key,
                                    String body) {}

    public record AddCommentResponse(String issueKey,
                                     String commentId) {}

}
