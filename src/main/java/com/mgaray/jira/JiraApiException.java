package com.mgaray.jira;

import com.mgaray.mcp.ToolCallException;

// Extends ToolCallException so JIRA API failures surface as isError tool results.
public class JiraApiException extends ToolCallException {

    public JiraApiException(String message) {
        super(message);
    }

    public JiraApiException(String message, Throwable cause) {
        super(message, cause);
    }

}
