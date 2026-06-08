package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.ExtractionMethod;
import com.archive.repository.ExtractionMethodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 抽取方法业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionMethodService {

    private final ExtractionMethodRepository extractionMethodRepository;

    /**
     * 创建抽取方法.
     */
    @Transactional
    public ExtractionMethod create(ExtractionMethod method) {
        if (method.getCode() == null || method.getCode().isBlank()) {
            throw new IllegalArgumentException("方法代码不能为空");
        }
        if (method.getName() == null || method.getName().isBlank()) {
            throw new IllegalArgumentException("方法名称不能为空");
        }
        if (method.getPromptTemplate() == null || method.getPromptTemplate().isBlank()) {
            throw new IllegalArgumentException("Prompt 模板不能为空");
        }
        if (method.getOutputSchema() == null || method.getOutputSchema().isBlank()) {
            throw new IllegalArgumentException("输出 Schema 不能为空");
        }
        return extractionMethodRepository.save(method);
    }

    /**
     * 更新抽取方法.
     */
    @Transactional
    public ExtractionMethod update(Long id, ExtractionMethod method) {
        ExtractionMethod existing = extractionMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("抽取方法不存在: id=" + id));

        if (method.getName() != null) {
            existing.setName(method.getName());
        }
        if (method.getDescription() != null) {
            existing.setDescription(method.getDescription());
        }
        if (method.getApplyTo() != null) {
            existing.setApplyTo(method.getApplyTo());
        }
        if (method.getPromptTemplate() != null) {
            existing.setPromptTemplate(method.getPromptTemplate());
        }
        if (method.getOutputSchema() != null) {
            existing.setOutputSchema(method.getOutputSchema());
        }
        if (method.getEnabled() != null) {
            existing.setEnabled(method.getEnabled());
        }
        if (method.getSortOrder() != null) {
            existing.setSortOrder(method.getSortOrder());
        }
        return extractionMethodRepository.save(existing);
    }

    /**
     * 删除抽取方法(内置方法不可删除).
     */
    @Transactional
    public void delete(Long id) {
        ExtractionMethod existing = extractionMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("抽取方法不存在: id=" + id));
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalStateException("系统内置抽取方法不可删除: id=" + id);
        }
        extractionMethodRepository.delete(existing);
        log.info("已删除抽取方法 id={}", id);
    }

    /**
     * 按 ID 查询抽取方法.
     */
    public ExtractionMethod getById(Long id) {
        return extractionMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("抽取方法不存在: id=" + id));
    }

    /**
     * 按代码查询抽取方法.
     */
    public ExtractionMethod getByCode(String code) {
        return extractionMethodRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("抽取方法不存在: code=" + code));
    }

    /**
     * 分页查询所有抽取方法.
     */
    public PageResponse<ExtractionMethod> listAll(int page, int size) {
        Page<ExtractionMethod> result = extractionMethodRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sortOrder", "code")));
        return PageResponse.of(result);
    }

    /**
     * 按应用对象查询已启用的方法.
     */
    public List<ExtractionMethod> getEnabledByApplyTo(String applyTo) {
        if (applyTo == null || applyTo.isBlank()) {
            throw new IllegalArgumentException("应用对象不能为空");
        }
        return extractionMethodRepository.findByApplyToAndEnabled(applyTo, true);
    }
}
