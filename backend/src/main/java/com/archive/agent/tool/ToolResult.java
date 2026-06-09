package com.archive.agent.tool;

public class ToolResult {
    private boolean ok;
    private Object data;
    private String error;
    
    public ToolResult() {}
    
    public static ToolResult ok(Object data) {
        ToolResult r = new ToolResult();
        r.ok = true;
        r.data = data;
        return r;
    }
    
    public static ToolResult error(String message) {
        ToolResult r = new ToolResult();
        r.ok = false;
        r.error = message;
        return r;
    }
    
    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
