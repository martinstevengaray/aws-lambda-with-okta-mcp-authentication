package com.mgaray.mcp;

// Message is shown to the MCP client as an isError tool result, so keep it user-readable.
public class ToolCallException extends RuntimeException {

    public ToolCallException(String message) {
        super(message);
    }

    public ToolCallException(String message, Throwable cause) {
        super(message, cause);
    }

}
