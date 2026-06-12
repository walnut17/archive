package com.archive.agent.tool;

import com.archive.dto.Source;

import java.util.ArrayList;
import java.util.List;

public class ToolResult {
    private boolean ok;
    private Object data;
    private String error;
    private List<Source> sources = new ArrayList<>();
    
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
    
    /** 快捷创建 ok 结果并附带来源列表. */
    public static ToolResult ok(Object data, List<Source> sources) {
        ToolResult r = ok(data);
        if (sources != null) {
            r.sources = sources;
        }
        return r;
    }
    
    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }
}
