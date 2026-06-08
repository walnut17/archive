package com.archive.service;

import com.archive.entity.DictItem;
import com.archive.entity.DictType;
import com.archive.repository.DictItemRepository;
import com.archive.repository.DictTypeRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 字典业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictService {

    private final DictTypeRepository dictTypeRepository;
    private final DictItemRepository dictItemRepository;

    /** 简单本地缓存: typeCode → (items, 过期时间戳). */
    private final ConcurrentHashMap<String, CacheEntry<List<DictItem>>> itemCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cacheCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dict-cache-cleaner");
        t.setDaemon(true);
        return t;
    });

    /** 缓存 TTL 5 秒. */
    private static final long CACHE_TTL_MS = 5000L;

    @PostConstruct
    public void init() {
        cacheCleaner.scheduleAtFixedRate(this::evictExpiredEntries, 5, 5, TimeUnit.SECONDS);
        log.info("DictService 缓存清理任务已启动");
    }

    @PreDestroy
    public void destroy() {
        cacheCleaner.shutdown();
        log.info("DictService 缓存清理任务已关闭");
    }

    /**
     * 获取所有已启用的字典分类.
     */
    public List<DictType> getDictTypes() {
        return dictTypeRepository.findByEnabledOrderBySortOrderAsc(true);
    }

    /**
     * 获取指定字典类型的所有条目.
     */
    public List<DictItem> getDictItems(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("字典类型代码不能为空");
        }
        return dictItemRepository.findByTypeCodeAndEnabledOrderBySortOrderAsc(typeCode, true);
    }

    /**
     * 获取字典条目(带 5 秒本地缓存).
     */
    public List<DictItem> getDictItemsWithCache(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("字典类型代码不能为空");
        }
        long now = System.currentTimeMillis();
        CacheEntry<List<DictItem>> entry = itemCache.get(typeCode);
        if (entry != null && now < entry.expireAt()) {
            return entry.value();
        }
        List<DictItem> items = dictItemRepository.findByTypeCodeAndEnabledOrderBySortOrderAsc(typeCode, true);
        itemCache.put(typeCode, new CacheEntry<>(items, now + CACHE_TTL_MS));
        return items;
    }

    /**
     * 创建字典分类.
     */
    @Transactional
    public DictType createDictType(DictType dictType) {
        if (dictType.getTypeCode() == null || dictType.getTypeCode().isBlank()) {
            throw new IllegalArgumentException("字典类型代码不能为空");
        }
        if (dictTypeRepository.existsByTypeCode(dictType.getTypeCode())) {
            throw new IllegalArgumentException("字典类型代码已存在: " + dictType.getTypeCode());
        }
        if (dictType.getTypeName() == null || dictType.getTypeName().isBlank()) {
            throw new IllegalArgumentException("字典类型名称不能为空");
        }
        return dictTypeRepository.save(dictType);
    }

    /**
     * 创建字典条目.
     */
    @Transactional
    public DictItem createDictItem(DictItem dictItem) {
        if (dictItem.getTypeCode() == null || dictItem.getTypeCode().isBlank()) {
            throw new IllegalArgumentException("字典类型代码不能为空");
        }
        if (dictItem.getItemKey() == null || dictItem.getItemKey().isBlank()) {
            throw new IllegalArgumentException("字典项键值不能为空");
        }
        if (dictItem.getItemValue() == null || dictItem.getItemValue().isBlank()) {
            throw new IllegalArgumentException("字典项显示值不能为空");
        }
        // 使缓存失效
        itemCache.remove(dictItem.getTypeCode());
        return dictItemRepository.save(dictItem);
    }

    /**
     * 删除字典条目.
     */
    @Transactional
    public void deleteDictItem(Long id) {
        DictItem existing = dictItemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("字典条目不存在: id=" + id));
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new IllegalStateException("系统内置字典条目不可删除: id=" + id);
        }
        // 使缓存失效
        itemCache.remove(existing.getTypeCode());
        dictItemRepository.delete(existing);
        log.info("已删除字典条目 id={}", id);
    }

    /** 清理过期缓存项. */
    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        itemCache.values().removeIf(entry -> now >= entry.expireAt());
    }

    /** 缓存条目. */
    private record CacheEntry<V>(V value, long expireAt) {}
}
