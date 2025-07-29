package com.example.bio_data.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SearchCacheService {
    
    // 搜索缓存数据结构
    public static class SearchCacheEntry {
        private String whereClause;
        private List<Object> params;
        private Integer totalCount;
        private long createTime;
        private long lastAccessTime;
        
        public SearchCacheEntry(String whereClause, List<Object> params, Integer totalCount) {
            this.whereClause = whereClause;
            this.params = new ArrayList<>(params);
            this.totalCount = totalCount;
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = this.createTime;
        }
        
        public String getWhereClause() { return whereClause; }
        public List<Object> getParams() { return new ArrayList<>(params); }
        public Integer getTotalCount() { return totalCount; }
        public long getCreateTime() { return createTime; }
        public long getLastAccessTime() { return lastAccessTime; }
        
        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - createTime > timeoutMs;
        }
        
        public boolean isIdle(long idleTimeoutMs) {
            return System.currentTimeMillis() - lastAccessTime > idleTimeoutMs;
        }
    }
    
    // 缓存存储：key = "dataSource:tableName:searchValue", value = SearchCacheEntry
    private final Map<String, SearchCacheEntry> searchCache = new ConcurrentHashMap<>();
    
    // 缓存配置
    private static final long CACHE_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟过期
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000; // 10分钟空闲过期
    private static final int MAX_CACHE_SIZE = 1000; // 最大缓存条数
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String dataSource, String tableName, String searchValue) {
        String safeDataSource = dataSource != null ? dataSource : "default";
        return String.format("%s:%s:%s", safeDataSource, tableName, searchValue);
    }
    
    /**
     * 获取缓存的搜索条件
     */
    public SearchCacheEntry getSearchCache(String dataSource, String tableName, String searchValue) {
        String cacheKey = generateCacheKey(dataSource, tableName, searchValue);
        SearchCacheEntry entry = searchCache.get(cacheKey);
        
        if (entry != null) {
            // 检查是否过期
            if (entry.isExpired(CACHE_TIMEOUT_MS) || entry.isIdle(IDLE_TIMEOUT_MS)) {
                searchCache.remove(cacheKey);
                return null;
            }
            
            // 更新访问时间
            entry.updateLastAccessTime();
            return entry;
        }
        
        return null;
    }
    
    /**
     * 缓存搜索条件
     */
    public void putSearchCache(String dataSource, String tableName, String searchValue, 
                              String whereClause, List<Object> params, Integer totalCount) {
        // 检查缓存大小，如果超过限制则清理
        if (searchCache.size() >= MAX_CACHE_SIZE) {
            cleanupExpiredEntries();
            
            // 如果清理后还是超过限制，清理最老的条目
            if (searchCache.size() >= MAX_CACHE_SIZE) {
                cleanupOldestEntries();
            }
        }
        
        String cacheKey = generateCacheKey(dataSource, tableName, searchValue);
        SearchCacheEntry entry = new SearchCacheEntry(whereClause, params, totalCount);
        searchCache.put(cacheKey, entry);
    }
    
    /**
     * 清理过期的缓存条目
     */
    public void cleanupExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        searchCache.entrySet().removeIf(entry -> 
            entry.getValue().isExpired(CACHE_TIMEOUT_MS) || 
            entry.getValue().isIdle(IDLE_TIMEOUT_MS)
        );
    }
    
    /**
     * 清理最老的缓存条目（当缓存满时）
     */
    private void cleanupOldestEntries() {
        // 移除最老的25%的条目
        int removeCount = Math.max(1, searchCache.size() / 4);
        
        List<Map.Entry<String, SearchCacheEntry>> entries = new ArrayList<>(searchCache.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().getCreateTime()));
        
        for (int i = 0; i < removeCount && i < entries.size(); i++) {
            searchCache.remove(entries.get(i).getKey());
        }
    }
    
    /**
     * 清空特定表的缓存（当表结构发生变化时）
     */
    public void clearTableCache(String dataSource, String tableName) {
        String prefix = generateCacheKey(dataSource, tableName, "");
        searchCache.entrySet().removeIf(entry -> 
            entry.getKey().startsWith(prefix.substring(0, prefix.length() - 1))
        );
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllCache() {
        searchCache.clear();
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", searchCache.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("cacheTimeoutMinutes", CACHE_TIMEOUT_MS / (60 * 1000));
        stats.put("idleTimeoutMinutes", IDLE_TIMEOUT_MS / (60 * 1000));
        
        long currentTime = System.currentTimeMillis();
        long expiredCount = searchCache.values().stream()
            .mapToLong(entry -> entry.isExpired(CACHE_TIMEOUT_MS) || entry.isIdle(IDLE_TIMEOUT_MS) ? 1 : 0)
            .sum();
        stats.put("expiredEntries", expiredCount);
        
        return stats;
    }
} 