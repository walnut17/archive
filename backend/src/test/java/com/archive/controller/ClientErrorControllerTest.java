package com.archive.controller;

import com.archive.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClientErrorController 单测.
 */
@WebMvcTest(ClientErrorController.class)
class ClientErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportError_writesAuditLog() throws Exception {
        var body = Map.of(
                "message", "测试错误",
                "stack", "at com.example.Test.method(Test.java:42)",
                "url", "http://localhost:8080/knowledge"
        );

        mockMvc.perform(post("/api/client-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(auditLogService).logClientError(anyString(), eq("测试错误"), anyString(), anyString());
    }
}
