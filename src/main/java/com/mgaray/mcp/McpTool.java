package com.mgaray.mcp;

import java.util.Map;

public interface McpTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    // Write tools can be gated on an Okta scope (see McpHandler's requiredWriteScope).
    boolean isWrite();

    String call(Map<String, Object> arguments) throws ToolCallException;

}
