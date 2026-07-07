package com.mgaray.jira;

import java.util.Map;

public interface JiraClient {

    // pathAndQuery is relative to the API base, e.g. "/myself" or "/search/jql?jql=..."
    Map<String, Object> get(String pathAndQuery) throws JiraApiException;

    Map<String, Object> post(String path, Map<String, Object> body) throws JiraApiException;

}
