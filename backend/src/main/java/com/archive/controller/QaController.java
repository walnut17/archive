package com.archive.controller;

import com.archive.agent.AgentEngine;
import com.archive.agent.AgentRequest;
import com.archive.agent.AgentResponse;
import com.archive.common.ApiResponse;
import com.archive.dto.QaRequest;
import com.archive.dto.QaResponse;
import com.archive.service.GlmService;
import com.archive.service.KnowledgeSearchService;
import com.archive.service.KnowledgeSearchService.SearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库问答 API.
 *
 * 双路径:
 *   1. Agent 模式(spring.ai.agent.enabled=true):AgentEngine 手写 5 步 ReAct 循环
 *   2. 老路径(降级):KnowledgeSearchService FULLTEXT → GlmService rerank → GlmService generate
 *
 * @author Mavis
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final KnowledgeSearchService searchService;
    private final GlmService glmService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private AgentEngine agentEngine;

    @Value("${spring.ai.agent.enabled:true}")
    private boolean agentEnabled;

    public QaController(KnowledgeSearchService searchService, GlmService glmService) {
        this.searchService = searchService;
        this.glmService = glmService;
    }

    @PostMapping("/ask")
    public ApiResponse<QaResponse> ask(@Valid @RequestBody QaRequest req) {
        long start = System.currentTimeMillis();

        // 路径 1:Agent 模式
        if (agentEnabled && agentEngine != null) {
            try {
                AgentRequest agentReq = AgentRequest.fromQaRequest(req);
                AgentResponse ar = agentEngine.run(agentReq);
                QaResponse qr = QaResponse.fromAgentResponse(ar);
                qr.setQuestion(req.getQuestion());
                qr.setElapsedMs(System.currentTimeMillis() - start);
                return ApiResponse.ok(qr);
            } catch (Exception e) {
                log.warn("Agent 失败,降级到老路径", e);
                // 不返 500,降级走老路径
            }
        }

        // 路径 2:老 FULLTEXT 路径(原 M2)
        return legacyAsk(req, start);
    }

    /**
     * 老路径:search → rerank → generate(原 M2 逻辑,保留降级用).
     */
    private ApiResponse<QaResponse> legacyAsk(QaRequest req, long start) {
        int topN = req.getTopN() != null ? req.getTopN() : 10;
        boolean rerank = req.getRerank() == null || req.getRerank();

        // 1. 全文检索
        List<SearchResult> sources = searchService.search(req.getQuestion(), topN);

        // 2. LLM 重排(可选)
        boolean reranked = false;
        if (rerank && !sources.isEmpty()) {
            try {
                String orderJson = glmService.rerank(req.getQuestion(), sources);
                List<Integer> order = mapper.readValue(orderJson, new TypeReference<List<Integer>>() {});
                if (order != null && !order.isEmpty()) {
                    List<SearchResult> rerankedList = new ArrayList<>();
                    for (Integer idx : order) {
                        if (idx != null && idx >= 1 && idx <= sources.size()) {
                            rerankedList.add(sources.get(idx - 1));
                        }
                    }
                    if (rerankedList.size() == sources.size()) {
                        sources = rerankedList;
                        reranked = true;
                    }
                }
            } catch (Exception e) {
                log.warn("Rerank failed, keep original order: {}", e.getMessage());
            }
        }

        // 3. LLM 生成答案(可选)
        String answer = null;
        if (!sources.isEmpty()) {
            try {
                answer = generateAnswer(req.getQuestion(), sources);
            } catch (IllegalStateException e) {
                log.info("GLM key not configured, skip answer generation");
            } catch (Exception e) {
                log.warn("Generate answer failed", e);
            }
        }

        QaResponse resp = QaResponse.builder()
                .question(req.getQuestion())
                .answer(answer)
                .sources(sources)
                .reranked(reranked)
                .elapsedMs(System.currentTimeMillis() - start)
                .build();
        return ApiResponse.ok(resp);
    }

    /**
     * 让 LLM 拿 top K(前 3)材料片段生成答案.
     */
    private String generateAnswer(String question, List<SearchResult> sources) {
        int top = Math.min(3, sources.size());
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题:").append(question).append("\n\n");
        prompt.append("相关材料(按相关度排序):\n\n");
        for (int i = 0; i < top; i++) {
            SearchResult r = sources.get(i);
            prompt.append("【").append(i + 1).append("】");
            prompt.append("项目 ").append(safe(r.getProjectName())).append(" > ");
            prompt.append("议案 ").append(safe(r.getProposalTitle())).append(" > ");
            prompt.append("材料 ").append(safe(r.getMaterialTitle())).append(" v").append(r.getVersionNo()).append("\n");
            prompt.append("片段:").append(safe(r.getSnippet())).append("\n\n");
        }
        prompt.append("请根据以上材料回答用户问题。如果材料中找不到答案,请明确说明。");
        prompt.append("回答末尾标注引用来源(参考 [1] [2] [3])。");

        String system = "你是投委会档案管理系统的智能助手,根据提供的材料片段回答用户问题。回答要准确、简洁,引用要明确。";
        return glmService.chat(system, prompt.toString());
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\n", " ").trim();
    }
}
