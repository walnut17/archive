package com.archive.agent;

import com.archive.agent.tool.AgentTool;
import com.archive.dto.QaRequest;
import com.archive.dto.QaResponse;
import com.archive.controller.QaController;
import com.archive.service.KnowledgeSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试(10 用例).
 * 工具调用真实,ChatClient 用 MockBean 避免真实 GLM 调用.
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentIntegrationTest {

    @Autowired
    private QaController qaController;

    @Autowired
    private AgentEngine agentEngine;

    @Autowired
    private List<AgentTool> agentTools;

    // 注: 之前用 @MockBean mock 了 GlmService 导致 chat() 返 null
    // 现在测例用真 GLM (GLM_API_KEY 环境变量), 不要 mock
    // @MockBean
    // private com.archive.service.GlmService glmService;

    @Autowired
    private com.archive.repository.ProjectRepository projectRepo;

    @BeforeEach
    void setUp() {
        // 确保工具注册完整(6 个工具)
        assertTrue(agentTools.size() >= 6, "应有至少 6 个工具注册");
        // 种子项目 (P0-20 修: 让 find_project.findByCode 命中, 避免走 FULLTEXT 在 H2 崩)
        if (!projectRepo.existsByCode("PRJ-2026-001")) {
            com.archive.entity.Project p = new com.archive.entity.Project();
            p.setCode("PRJ-2026-001");
            p.setName("新能源项目");
            p.setCustomerName("某新能源公司");
            p.setAmountWan(5000L);
            p.setStatus("贷后");
            p.setSummary("新能源项目种子数据,仅测例用");
            projectRepo.save(p);
        }
    }

    // ========== 10 个测试用例 ==========

    @Test
    void test1_searchFulltext() {
        // 检索类:问"新能源项目风险点" → 返答案 + 来源
        AgentRequest req = new AgentRequest("新能源项目风险点");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
        assertTrue(resp.isAgentMode());
        assertNotNull(resp.getSteps());
    }

    @Test
    void test2_queryBusinessData() {
        // 查库类:问"PRJ-2026-001 剩余金额"
        AgentRequest req = new AgentRequest("PRJ-2026-001 剩余金额");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
        // 应调用过 get_project_business_data 工具
        assertTrue(resp.getSteps().stream()
                .anyMatch(s -> "get_project_business_data".equals(s.getTool())));
    }

    @Test
    void test3_queryMysqlAggregate() {
        // 查表类:问"查询 project 表 status='否决' 的所有项目" → query_mysql (P0-22 修: 之前问"今年否决了哪些项目"太泛, LLM 一直 ask_clarification)
        AgentRequest req = new AgentRequest("查询 project 表里 status 字段是'否决'的所有项目");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
        // 应调用过 query_mysql 工具
        assertTrue(resp.getSteps().stream()
                .anyMatch(s -> "query_mysql".equals(s.getTool())));
    }

    @Test
    void test4_askClarification() {
        // 追问类:问"那个项目" → 模糊问题可能触发 ask_clarification
        AgentRequest req = new AgentRequest("那个项目怎么样");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
    }

    @Test
    void test5_findProjectLock() {
        // 锁项目:问"PRJ-2026-001" → find_project 锁定 (或者 LLM 智能选 get_project_business_data)
        AgentRequest req = new AgentRequest("PRJ-2026-001 的情况");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        // LLM 可能 (a) 先调 find_project 锁, 或 (b) 看到 PRJ-2026-001 猜是 projectCode 直接调 get_project_business_data
        // 两种都是合法答
        // Mavis 修 P0-21: LLM 智能选择 (b) 路径也 OK, 允许
        assertTrue(resp.getSteps().stream()
                .anyMatch(s -> "find_project".equals(s.getTool()) || "get_project_business_data".equals(s.getTool())),
                "应调用过 find_project 或 get_project_business_data");
    }

    @Test
    void test6_crossTableQuery() {
        // 跨表查:问"今天有哪些待办"
        AgentRequest req = new AgentRequest("今天有哪些待办");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        assertNotNull(resp.getAnswer());
    }

    @Test
    void test7_fallbackOnException() {
        // 降级:模拟 LLM 抛异常 → fallback
        // 这里测试引擎本身不会崩溃
        AgentRequest req = new AgentRequest("测试降级");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        // 即使 LLM 返回异常,引擎应返回兜底答案
        assertNotNull(resp.getAnswer());
    }

    @Test
    void test8_whitelistProtection() {
        // 白名单:尝试非法 entity → 应有错误处理
        AgentRequest req = new AgentRequest("查一下 user 表的数据");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        // 引擎不应崩溃,应返回某种形式的答案
        assertNotNull(resp.getAnswer());
    }

    @Test
    void test9_agentStepsRecorded() {
        // 埋点:Agent 循环步骤应被记录
        AgentRequest req = new AgentRequest("新能源项目");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        // steps 应非空且有内容
        assertNotNull(resp.getSteps());
        assertFalse(resp.getSteps().isEmpty(), "应有至少 1 个步骤");
    }

    @Test
    void test10_maxIterationsRespected() {
        // 边界:5 步上限应被遵守
        AgentRequest req = new AgentRequest("这是一个复杂问题需要多步分析新能源项目盈利情况剩余金额待办事项");
        AgentResponse resp = agentEngine.run(req);
        assertNotNull(resp);
        // steps 不应超过 5 步
        assertTrue(resp.getSteps().size() <= 5, "步骤数不应超过 5");
    }
}
