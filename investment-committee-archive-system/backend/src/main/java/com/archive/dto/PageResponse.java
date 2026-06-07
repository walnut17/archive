package com.archive.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页响应 DTO.
 *
 * 把 Spring Data 的 Page 转换成稳定的 JSON 结构,避免暴露 Pageable 实现细节。
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int page;             // 当前页(从 0 开始)
    private int size;             // 每页大小
    private long totalElements;   // 总记录数
    private int totalPages;       // 总页数
    private boolean first;
    private boolean last;

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /** 转换 content 中每个元素到目标类型. */
    public <R> PageResponse<R> mapContent(Function<T, R> mapper) {
        return PageResponse.<R>builder()
                .content(this.content.stream().map(mapper).collect(Collectors.toList()))
                .page(this.page)
                .size(this.size)
                .totalElements(this.totalElements)
                .totalPages(this.totalPages)
                .first(this.first)
                .last(this.last)
                .build();
    }
}
