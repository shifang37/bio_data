package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;

@Service
public class MultiDataSourceService {

    private static final Logger logger = LoggerFactory.getLogger(MultiDataSourceService.class);

    // 存储login数据源
    private final DataSource loginDataSource;
    private final JdbcTemplate loginJdbcTemplate;

    public MultiDataSourceService(@Qualifier("loginDataSource") DataSource loginDataSource,
                                  @Qualifier("loginJdbcTemplate") JdbcTemplate loginJdbcTemplate) {
        
        this.loginDataSource = loginDataSource;
        this.loginJdbcTemplate = loginJdbcTemplate;
        
        logger.info("初始化数据源服务，已加载login数据源");
    }

    /**
     * 获取所有数据源名称
     */
    public Set<String> getAllDataSourceNames() {
        return Set.of("login");
    }

    /**
     * 获取指定数据源的JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate(String dataSourceName) {
        if ("login".equals(dataSourceName)) {
            return loginJdbcTemplate;
        }
        throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
    }

    /**
     * 获取指定数据源
     */
    public DataSource getDataSource(String dataSourceName) {
        if ("login".equals(dataSourceName)) {
            return loginDataSource;
        }
        throw new IllegalArgumentException("数据源不存在: " + dataSourceName);
    }

    /**
     * 测试数据源连接
     */
    public boolean testConnection(String dataSourceName) {
        try {
            if ("login".equals(dataSourceName)) {
                loginJdbcTemplate.queryForObject("SELECT 1", Integer.class);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("数据源连接测试失败: {} - {}", dataSourceName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取所有数据源的概览信息
     */
    public List<Map<String, Object>> getAllDataSourcesOverview() {
        List<Map<String, Object>> overview = new ArrayList<>();
        
        Map<String, Object> loginInfo = new HashMap<>();
        loginInfo.put("name", "login");
        loginInfo.put("connected", testConnection("login"));
        loginInfo.put("jdbcUrl", "jdbc:mysql://localhost:3306/login");
        loginInfo.put("username", "root");
        overview.add(loginInfo);
        
        return overview;
    }
} 