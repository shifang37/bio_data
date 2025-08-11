package com.example.bio_data.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MultiDataSourceService {

    private static final Logger logger = LoggerFactory.getLogger(MultiDataSourceService.class);

    // 存储所有数据源
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    
    // 存储所有JdbcTemplate
    private final Map<String, JdbcTemplate> jdbcTemplateMap = new ConcurrentHashMap<>();

    public MultiDataSourceService(@Qualifier("loginDataSource") DataSource loginDataSource,
                                  @Qualifier("loginJdbcTemplate") JdbcTemplate loginJdbcTemplate) {
        
        // 初始化数据源映射 - 只保留login数据源
        dataSourceMap.put("login", loginDataSource);
        
        // 初始化JdbcTemplate映射
        jdbcTemplateMap.put("login", loginJdbcTemplate);
        
        logger.info("初始化数据源服务，已加载 {} 个数据源", dataSourceMap.size());
        logger.info("可用的数据源名称: {}", dataSourceMap.keySet());
    }

    /**
     * 获取所有数据源名称
     */
    public Set<String> getAllDataSourceNames() {
        return new HashSet<>(dataSourceMap.keySet());
    }

    /**
     * 获取指定数据源的JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String dataSourceName) {
        JdbcTemplate jdbcTemplate = jdbcTemplateMap.get(dataSourceName);
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
        }
        return jdbcTemplate;
    }

    /**
     * 获取指定数据源
     */
    public DataSource getDataSource(String dataSourceName) {
        DataSource dataSource = dataSourceMap.get(dataSourceName);
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
        }
        return dataSource;
    }

    /**
     * 动态添加新的数据源
     */
    public boolean addDataSource(String name, String url, String username, String password) {
        try {
            if (dataSourceMap.containsKey(name)) {
                logger.warn("数据源已存在: {}", name);
                return false;
            }

            // 创建HikariCP数据源配置
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // 连接池配置
            config.setConnectionTimeout(60000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setMaximumPoolSize(15);
            config.setMinimumIdle(3);
            config.setConnectionTestQuery("SELECT 1");

            // 创建数据源和JdbcTemplate
            HikariDataSource dataSource = new HikariDataSource(config);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

            // 测试连接
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // 添加到管理器
            dataSourceMap.put(name, dataSource);
            jdbcTemplateMap.put(name, jdbcTemplate);

            logger.info("成功添加数据源: {}", name);
            return true;

        } catch (Exception e) {
            logger.error("添加数据源失败: {} - {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 移除数据源
     */
    public boolean removeDataSource(String name) {
        try {
            // 不允许移除login数据源
            if ("login".equals(name)) {
                logger.warn("不允许移除login数据源: {}", name);
                return false;
            }
            
            DataSource dataSource = dataSourceMap.remove(name);
            jdbcTemplateMap.remove(name);

            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
            }

            logger.info("成功移除数据源: {}", name);
            return true;

        } catch (Exception e) {
            logger.error("移除数据源失败: {} - {}", name, e.getMessage());
            return false;
        }
    }

    /**
     * 测试数据源连接
     */
    public boolean testConnection(String dataSourceName) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            logger.error("数据源连接测试失败: {} - {}", dataSourceName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取数据源统计信息
     */
    public Map<String, Object> getDataSourceStats(String dataSourceName) {
        try {
            DataSource dataSource = getDataSource(dataSourceName);
            Map<String, Object> stats = new HashMap<>();
            
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource hikariDS = (HikariDataSource) dataSource;
                stats.put("jdbcUrl", hikariDS.getJdbcUrl());
                stats.put("maximumPoolSize", hikariDS.getMaximumPoolSize());
                stats.put("minimumIdle", hikariDS.getMinimumIdle());
                stats.put("connectionTimeout", hikariDS.getConnectionTimeout());
                stats.put("poolName", hikariDS.getPoolName());
                
                // 获取连接池状态
                if (hikariDS.getHikariPoolMXBean() != null) {
                    stats.put("activeConnections", hikariDS.getHikariPoolMXBean().getActiveConnections());
                    stats.put("idleConnections", hikariDS.getHikariPoolMXBean().getIdleConnections());
                    stats.put("totalConnections", hikariDS.getHikariPoolMXBean().getTotalConnections());
                }
            }
            
            stats.put("dataSourceName", dataSourceName);
            stats.put("connectionValid", testConnection(dataSourceName));
            
            return stats;
            
        } catch (Exception e) {
            logger.error("获取数据源统计信息失败: {} - {}", dataSourceName, e.getMessage());
            return Map.of("error", "获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有数据源的概览信息
     */
    public List<Map<String, Object>> getAllDataSourcesOverview() {
        List<Map<String, Object>> overview = new ArrayList<>();
        
        for (String name : dataSourceMap.keySet()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", name);
            info.put("connected", testConnection(name));
            
            try {
                DataSource dataSource = getDataSource(name);
                if (dataSource instanceof HikariDataSource) {
                    HikariDataSource hikariDS = (HikariDataSource) dataSource;
                    info.put("jdbcUrl", hikariDS.getJdbcUrl());
                    info.put("username", hikariDS.getUsername());
                }
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
            
            overview.add(info);
        }
        
        return overview;
    }
} 