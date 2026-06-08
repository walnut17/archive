package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.ComparisonMethod;
import com.archive.repository.ComparisonMethodRepository;
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
 * 对比方法业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComparisonMethodService {

    private final ComparisonMethodRepository comparisonMethodRepository;

    /**
     * 创建对比方法.
     */
    @Transactional
    public ComparisonMethod create(ComparisonMethod method) {
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
        return comparisonMethodRepository.save(method);
    }

    /**
     * 更新对比方法.
     */
    @Transactional
    public ComparisonMethod update(Long id, ComparisonMethod method) {
        ComparisonMethod existing = comparisonMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("对比方法不存在: id=" + id));

        if (method.getName() != null) {
            existing.setName(method.getName());
        }
        if (method.getDescription() != null) {
            existing.setDescription(method.getDescription());
        }
        if (method.getFromType() != null) {
            existing.setFromType(method.getFromType());
        }
        if (method.getToType() != null) {
            existing.setToType(method.getToType());
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
        return comparisonMethodRepository.save(existing);
    }

    /**
     * 删除对比方法(内置方法不可删除).
     */
    @Transactional
    public void delete(Long id) {
        ComparisonMethod existing = comparisonMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("对比方法不存在: id=" + id));
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalStateException("系统内置对比方法不可删除: id=" + id);
        }
        comparisonMethodRepository.delete(existing);
        log.info("已删除对比方法 id={}", id);
    }

    /**
     * 按 ID 查询对比方法.
     */
    public ComparisonMethod getById(Long id) {
        return comparisonMethodRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("对比方法不存在: id=" + id));
    }

    /**
     * 按代码查询对比方法.
     */
    public ComparisonMethod getByCode(String code) {
        return comparisonMethodRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("对比方法不存在: code=" + code));
    }

    /**
     * 分页查询所有对比方法.
     */
    public PageResponse<ComparisonMethod> listAll(int page, int size) {
        Page<ComparisonMethod> result = comparisonMethodRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sortOrder", "code")));
        return PageResponse.of(result);
    }
}
