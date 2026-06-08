package com.archive.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM Provider 工厂 — 根据配置 {@code app.llm.provider} 选择对应实现。
 * <p>
 * 用法：
 * <pre>{@code
 *     @Autowired
 *     private LLMProviderFactory providerFactory;
 *
 *     LLMProvider provider = providerFactory.getProvider();
 *     String result = provider.chat("你是个助手", "你好");
 * }</pre>
 *
 * @author Mavis
 */
@Slf4j
@Service
public class LLMProviderFactory {

    private final Map<String, LLMProvider> providerMap = new HashMap<>();

    @Value("${app.llm.provider:glm}")
    private String providerName;

    public LLMProviderFactory(List<LLMProvider> providers) {
        for (LLMProvider p : providers) {
            providerMap.put(p.name(), p);
            log.debug("Registered LLM provider: {}", p.name());
        }
    }

    /**
     * 获取当前配置的 LLM Provider。
     * <p>
     * 按 {@code app.llm.provider} 值查找对应实现，未匹配时默认使用 {@code glm}。
     *
     * @return LLMProvider 实例
     * @throws IllegalStateException 当找不到任何可用的 provider 时
     */
    public LLMProvider getProvider() {
        LLMProvider provider = providerMap.get(providerName);
        if (provider != null) {
            return provider;
        }
        // 尝试 fallback 到 glm
        provider = providerMap.get("glm");
        if (provider == null) {
            throw new IllegalStateException(
                    "未找到 LLM provider '" + providerName + "'，且无 glm 兜底。"
                            + "请检查 app.llm.provider 配置或确保至少有一个 LLMProvider bean 可用。");
        }
        log.warn("LLM provider '{}' not found, falling back to 'glm'", providerName);
        return provider;
    }

    /**
     * 获取当前配置的 provider 名称。
     */
    public String getProviderName() {
        return providerName;
    }
}
