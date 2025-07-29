package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DatabaseService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 获取数据库中所有表的信息
     */
    public List<Map<String, Object>> getAllTables() {
        String sql = "SELECT " +
                "TABLE_NAME, " +
                "TABLE_ROWS, " +
                "DATA_LENGTH, " +
                "CREATE_TIME, " +
                "TABLE_COMMENT " +
                "FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "ORDER BY TABLE_NAME";
        
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 获取指定表的列信息
     */
    public List<Map<String, Object>> getTableColumns(String tableName) {
        String sql = "SELECT " +
                "COLUMN_NAME, " +
                "DATA_TYPE, " +
                "IS_NULLABLE, " +
                "COLUMN_DEFAULT, " +
                "COLUMN_KEY, " +
                "EXTRA, " +
                "COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";
        
        return jdbcTemplate.queryForList(sql, tableName);
    }

    /**
     * 获取表的前N行数据
     */
    public List<Map<String, Object>> getTableData(String tableName, int limit) {
        String sql = String.format("SELECT * FROM %s LIMIT ?", tableName);
        return jdbcTemplate.queryForList(sql, limit);
    }

    /**
     * 获取数据库统计信息
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 获取表总数
        String tableCountSql = "SELECT COUNT(*) as table_count FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        Integer tableCount = jdbcTemplate.queryForObject(tableCountSql, Integer.class);
        stats.put("tableCount", tableCount);
        
        // 获取数据库大小
        String sizeSql = "SELECT ROUND(SUM(DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) as size_mb " +
                "FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        Double sizeInMB = jdbcTemplate.queryForObject(sizeSql, Double.class);
        stats.put("databaseSizeMB", sizeInMB);
        
        // 获取数据库名称
        String dbNameSql = "SELECT DATABASE() as db_name";
        String dbName = jdbcTemplate.queryForObject(dbNameSql, String.class);
        stats.put("databaseName", dbName);
        
        return stats;
    }

    /**
     * 执行自定义SQL查询
     */
    public List<Map<String, Object>> executeQuery(String sql, int limit) {
        // 为安全起见，只允许SELECT查询
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("只允许SELECT查询");
        }
        
        // 添加LIMIT限制
        String limitedSql = sql + " LIMIT " + limit;
        return jdbcTemplate.queryForList(limitedSql);
    }

    /**
     * 获取表的索引信息
     */
    public List<Map<String, Object>> getTableIndexes(String tableName) {
        String sql = "SHOW INDEX FROM " + tableName;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 向表中插入数据
     */
    public int insertTableData(String tableName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("插入数据不能为空");
        }

        // 构建插入SQL
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append("`").append(entry.getKey()).append("`");
            values.append("?");
            params.add(entry.getValue());
        }

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", 
                tableName, columns.toString(), values.toString());
        
        return jdbcTemplate.update(sql, params.toArray());
    }
} 