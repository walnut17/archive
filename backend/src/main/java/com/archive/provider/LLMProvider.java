package com.archive.provider;

/**
 * LLM 提供方抽象。所有业务代码只调这个接口，不直接调具体厂商 API。
 * <p>
 * 切换实现：application.yml 设 {@code app.llm.provider=glm|openai|mock}。
 *
 * @author Mavis
 */
public interface LLMProvider {

    /**
     * 同步对话，返回纯文本（摘要/抽取/重排序都走这个）。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 返回的文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 同步对话，带 JSON Schema 约束，返回指定类型的对象。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param type         返回类型 Class
     * @param <T>          返回类型
     * @return 解析后的对象
     */
    <T> T chatJson(String systemPrompt, String userPrompt, Class<T> type);

    /**
     * 多模态：图片 → 文本。
     *
     * @param prompt     提示词
     * @param imageBytes 图片字节数组
     * @param mimeType   图片 MIME 类型（如 image/png、image/jpeg）
     * @return LLM 返回的文本描述
     */
    String vision(String prompt, byte[] imageBytes, String mimeType);

    /**
     * 当前 provider 名（用于 audit_log）。
     *
     * @return provider 名称标识
     */
    String name();
}
