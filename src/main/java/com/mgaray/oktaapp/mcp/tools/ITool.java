package com.mgaray.oktaapp.mcp.tools;

import com.mgaray.oktaapp.mcp.Models.ToolDefinition;

import java.util.Map;

public interface ITool {

    ToolDefinition toolDefinition();
    Map<String, Object> callTool(Map<String, Object> args);

}
