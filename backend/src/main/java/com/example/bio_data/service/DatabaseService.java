package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

import java.util.*;

@Service
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    @Autowired
    private MultiDataSourceService multiDataSourceService;
    
    @Autowired
    private SearchCacheService searchCacheService;

    // 默认数据源名称
    private static final String DEFAULT_DATASOURCE = "login";

    /**
     * 获取JdbcTemplate，如果数据源名称为空则使用默认数据源
     */
    public JdbcTemplate getJdbcTemplate(String dataSourceName) {
        String actualDataSourceName = (dataSourceName == null || dataSourceName.trim().isEmpty()) 
            ? DEFAULT_DATASOURCE : dataSourceName;
        return multiDataSourceService.getJdbcTemplate(actualDataSourceName);
    }

    // =============================================================================
    // 多数据源版本的方法（新版本）
    // =============================================================================

    /**
     * 获取指定数据源中所有表的信息
     */
    public List<Map<String, Object>> getAllTables(String dataSourceName) {
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接，但查询指定数据库
            String sql = "SELECT " +
                    "TABLE_NAME, " +
                    "TABLE_ROWS, " +
                    "DATA_LENGTH, " +
                    "CREATE_TIME, " +
                    "TABLE_COMMENT " +
                    "FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = ? " +
                    "ORDER BY TABLE_NAME";
            
            return getJdbcTemplate(DEFAULT_DATASOURCE).queryForList(sql, dataSourceName);
        } else {
            // 配置的数据源，使用原来的逻辑
            String sql = "SELECT " +
                    "TABLE_NAME, " +
                    "TABLE_ROWS, " +
                    "DATA_LENGTH, " +
                    "CREATE_TIME, " +
                    "TABLE_COMMENT " +
                    "FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "ORDER BY TABLE_NAME";
            
            return getJdbcTemplate(dataSourceName).queryForList(sql);
        }
    }
    
    /**
     * 获取指定表的准确行数
     */
    public Integer getTableRowCount(String dataSourceName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate;
            String countSql;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s`", dataSourceName, tableName);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                countSql = String.format("SELECT COUNT(*) FROM `%s`", tableName);
            }
            
            Integer rowCount = jdbcTemplate.queryForObject(countSql, Integer.class);
            return rowCount != null ? rowCount : 0;
        } catch (Exception e) {
            logger.warn("无法获取表 {} 的准确行数: {}", tableName, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取指定数据源中指定表的列信息
     */
    public List<Map<String, Object>> getTableColumns(String dataSourceName, String tableName) {
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接，但查询指定数据库
            String sql = "SELECT " +
                    "COLUMN_NAME, " +
                    "DATA_TYPE, " +
                    "IS_NULLABLE, " +
                    "COLUMN_DEFAULT, " +
                    "COLUMN_KEY, " +
                    "EXTRA, " +
                    "COLUMN_COMMENT " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                    "ORDER BY ORDINAL_POSITION";
            
            return getJdbcTemplate(DEFAULT_DATASOURCE).queryForList(sql, dataSourceName, tableName);
        } else {
            // 配置的数据源，使用原来的逻辑
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
            
            return getJdbcTemplate(dataSourceName).queryForList(sql, tableName);
        }
    }

    /**
     * 获取指定数据源中表的前N行数据
     */
    public List<Map<String, Object>> getTableData(String dataSourceName, String tableName, int limit) {
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接，但查询指定数据库的表
            String sql = String.format("SELECT * FROM `%s`.`%s` LIMIT ?", dataSourceName, tableName);
            return getJdbcTemplate(DEFAULT_DATASOURCE).queryForList(sql, limit);
        } else {
            // 配置的数据源，使用原来的逻辑
            String sql = String.format("SELECT * FROM `%s` LIMIT ?", tableName);
            return getJdbcTemplate(dataSourceName).queryForList(sql, limit);
        }
    }

    /**
     * 分页获取指定数据源中表数据
     */
    public Map<String, Object> getTableDataWithPagination(String dataSourceName, String tableName, int page, int size) {
        // 检查是否为用户创建的数据库
        JdbcTemplate jdbcTemplate;
        String dataSql;
        String approxCountSql;
        String countSql;
        
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            dataSql = String.format("SELECT * FROM `%s`.`%s` LIMIT ?, ?", dataSourceName, tableName);
            approxCountSql = "SELECT TABLE_ROWS FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
            countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s`", dataSourceName, tableName);
        } else {
            // 配置的数据源，使用原来的逻辑
            jdbcTemplate = getJdbcTemplate(dataSourceName);
            dataSql = String.format("SELECT * FROM `%s` LIMIT ?, ?", tableName);
            approxCountSql = "SELECT TABLE_ROWS FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            countSql = String.format("SELECT COUNT(*) FROM `%s`", tableName);
        }
        
        // 限制每页最大数据量，防止内存溢出
        if (size > 1000) {
            size = 1000;
        }
        
        // 计算偏移量
        int offset = (page - 1) * size;
        
        try {
            // 获取分页数据
            List<Map<String, Object>> data = jdbcTemplate.queryForList(dataSql, offset, size);
            
            // 获取总记录数 - 总是使用准确的COUNT查询
            Integer totalCount;
            try {
                // 直接使用COUNT(*)查询获取准确的行数，而不是依赖information_schema的估算值
                totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
                if (totalCount == null) {
                    totalCount = 0;
                }
            } catch (Exception e) {
                // 如果COUNT查询失败，尝试使用information_schema的估算值作为备选
                try {
                    if (isUserCreatedDatabase(dataSourceName)) {
                        totalCount = jdbcTemplate.queryForObject(approxCountSql, Integer.class, dataSourceName, tableName);
                    } else {
                        totalCount = jdbcTemplate.queryForObject(approxCountSql, Integer.class, tableName);
                    }
                    
                    if (totalCount == null || totalCount == 0) {
                        totalCount = (page * size) + (data.size() == size ? size : 0);
                    }
                } catch (Exception fallbackException) {
                    totalCount = (page * size) + (data.size() == size ? size : 0);
                    logger.warn("无法获取表 {} 的行数，使用估算值: {}", tableName, totalCount);
                }
            }
            
            // 计算总页数
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("totalCount", totalCount);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("dataSource", dataSourceName);
            result.put("isApproximate", false); // 现在总是尝试获取准确的行数
            
            return result;
            
        } catch (Exception e) {
            logger.error("查询表 {} 的分页数据时发生错误: {}", tableName, e.getMessage());
            throw new RuntimeException("查询大数据表失败，请尝试减少每页显示数量或使用筛选条件", e);
        }
    }





    /**
     * 根据字段名搜索包含该字段的表
     */
    public List<Map<String, Object>> findTablesByColumn(String dataSourceName, String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        
        String sql;
        JdbcTemplate jdbcTemplate;
        
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            sql = "SELECT DISTINCT " +
                    "c.TABLE_NAME, " +
                    "t.TABLE_ROWS, " +
                    "t.DATA_LENGTH, " +
                    "t.CREATE_TIME, " +
                    "t.TABLE_COMMENT, " +
                    "c.COLUMN_NAME, " +
                    "c.DATA_TYPE, " +
                    "c.COLUMN_COMMENT, " +
                    "c.ORDINAL_POSITION " +
                    "FROM information_schema.COLUMNS c " +
                    "LEFT JOIN information_schema.TABLES t ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME " +
                    "WHERE c.TABLE_SCHEMA = ? AND c.COLUMN_NAME LIKE ? " +
                    "ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION";
            jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            return jdbcTemplate.queryForList(sql, dataSourceName, "%" + columnName + "%");
        } else {
            // 配置的数据源，使用原来的逻辑
            sql = "SELECT DISTINCT " +
                    "c.TABLE_NAME, " +
                    "t.TABLE_ROWS, " +
                    "t.DATA_LENGTH, " +
                    "t.CREATE_TIME, " +
                    "t.TABLE_COMMENT, " +
                    "c.COLUMN_NAME, " +
                    "c.DATA_TYPE, " +
                    "c.COLUMN_COMMENT, " +
                    "c.ORDINAL_POSITION " +
                    "FROM information_schema.COLUMNS c " +
                    "LEFT JOIN information_schema.TABLES t ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME " +
                    "WHERE c.TABLE_SCHEMA = DATABASE() AND c.COLUMN_NAME LIKE ? " +
                    "ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION";
            jdbcTemplate = getJdbcTemplate(dataSourceName);
            return jdbcTemplate.queryForList(sql, "%" + columnName + "%");
        }
    }

    /**
     * 按字段值搜索表（完整搜索版本，支持搜索模式优化）
     * 搜索全部表的全部数据，提供完整的搜索结果
     */
    public List<Map<String, Object>> findTablesByValue(String dataSourceName, String searchValue, String searchMode) {
        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索值不能为空");
        }
        
        // 向后兼容：如果没有指定searchMode，使用auto模式
        if (searchMode == null || searchMode.trim().isEmpty()) {
            searchMode = "auto";
        }
        
        List<Map<String, Object>> resultTables = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        final long TIMEOUT_MS = 600000; // 10分钟超时
        
        try {
            // 获取数据库中的所有表
            List<Map<String, Object>> allTables = getAllTables(dataSourceName);
            logger.info("开始字段值搜索，数据库：{}，搜索值：{}，搜索模式：{}，总表数：{}", 
                    dataSourceName, searchValue, searchMode, allTables.size());
            
            int searchedCount = 0;
            int foundCount = 0;
            
            for (Map<String, Object> table : allTables) {
                // 检查超时
                if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                    logger.warn("字段值搜索超时，已搜索 {} 个表，找到 {} 个匹配表", searchedCount, foundCount);
                    break;
                }
                
                String tableName = (String) table.get("TABLE_NAME");
                searchedCount++;
                
                logger.info("正在搜索表 {} ({}/{})", tableName, searchedCount, allTables.size());
                
                try {
                    // 检查表是否包含匹配值（支持搜索模式）
                    boolean hasMatch = checkTableForValueWithMode(dataSourceName, tableName, searchValue, searchMode);
                    
                    if (hasMatch) {
                        foundCount++;
                        // 获取匹配记录的准确数量
                        int actualCount = getActualMatchCountWithMode(dataSourceName, tableName, searchValue, searchMode);
                        
                        Map<String, Object> resultTable = new HashMap<>();
                        resultTable.put("TABLE_NAME", tableName);
                        resultTable.put("TABLE_ROWS", table.get("TABLE_ROWS"));
                        resultTable.put("TABLE_COMMENT", table.get("TABLE_COMMENT"));
                        resultTable.put("MATCH_COUNT", actualCount);
                        resultTable.put("SEARCH_VALUE", searchValue);
                        resultTable.put("DATA_SOURCE", dataSourceName);
                        resultTable.put("SEARCH_MODE", searchMode);
                        resultTable.put("IS_COMPLETE", true);
                        
                        // 根据搜索模式设置搜索类型信息
                        String searchTypeInfo = getSearchTypeInfo(searchMode, searchValue);
                        resultTable.put("SEARCH_TYPE", searchTypeInfo);
                        
                        resultTables.add(resultTable);
                        
                        logger.info("在表 {} 中找到 {} 条匹配记录", tableName, actualCount);
                    }
                    
                } catch (Exception e) {
                    // 某个表查询失败，记录日志但继续处理其他表
                    logger.warn("搜索表 {} 时出现错误: {}", tableName, e.getMessage());
                }
            }
            
            long endTime = System.currentTimeMillis();
            logger.info("字段值搜索完成，搜索模式：{}，用时：{}ms，搜索了 {} 个表，找到 {} 个匹配表", 
                    searchMode, (endTime - startTime), searchedCount, foundCount);
            
        } catch (Exception e) {
            logger.error("执行字段值搜索时发生错误: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
        
        return resultTables;
    }
    
    /**
     * 保持向后兼容的方法
     */
    public List<Map<String, Object>> findTablesByValue(String dataSourceName, String searchValue) {
        return findTablesByValue(dataSourceName, searchValue, "auto");
    }
    
    /**
     * 检查表中是否包含指定值（完整搜索）
     */
    //private boolean checkTableForValueComplete(String dataSourceName, String tableName, String searchValue) {
        //return checkTableForValueWithMode(dataSourceName, tableName, searchValue, "all");
    //}
    
    /**
     * 检查表中是否包含指定值（支持搜索模式）
     */
    private boolean checkTableForValueWithMode(String dataSourceName, String tableName, String searchValue, String searchMode) {
        try {
            // 获取表的所有字段信息
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            
            if (columns.isEmpty()) {
                return false;
            }
            
            // 根据搜索模式过滤字段
            List<Map<String, Object>> filteredColumns = filterColumnsBySearchMode(columns, searchValue, searchMode);
            
            if (filteredColumns.isEmpty()) {
                logger.debug("表 {} 没有符合搜索模式 {} 的字段", tableName, searchMode);
                return false;
            }
            
            // 构建搜索SQL，只搜索过滤后的字段
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();
            
            for (Map<String, Object> column : filteredColumns) {
                String columnName = (String) column.get("COLUMN_NAME");
                String dataType = (String) column.get("DATA_TYPE");
                
                if (whereClause.length() > 0) {
                    whereClause.append(" OR ");
                }
                
                // 根据数据类型构建不同的搜索条件
                if (isNumericType(dataType)) {
                    // 数值类型：尝试等值匹配和字符串匹配
                    try {
                        if (isInteger(searchValue)) {
                            whereClause.append("(`").append(columnName).append("` = ? OR CAST(`").append(columnName).append("` AS CHAR) LIKE ?)");
                            params.add(Long.parseLong(searchValue));
                            params.add("%" + searchValue + "%");
                        } else if (isDecimal(searchValue)) {
                            whereClause.append("(`").append(columnName).append("` = ? OR CAST(`").append(columnName).append("` AS CHAR) LIKE ?)");
                            params.add(Double.parseDouble(searchValue));
                            params.add("%" + searchValue + "%");
                        } else {
                            whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                            params.add("%" + searchValue + "%");
                        }
                    } catch (NumberFormatException e) {
                        whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                        params.add("%" + searchValue + "%");
                    }
                } else {
                    // 字符串类型：使用LIKE模糊匹配，确保字符集兼容性
                    whereClause.append("CAST(`").append(columnName).append("` AS CHAR CHARACTER SET utf8) COLLATE utf8_general_ci LIKE ?");
                    params.add("%" + searchValue + "%");
                }
            }
            
            if (whereClause.length() == 0) {
                return false;
            }
            
            // 构建EXISTS查询，检查是否存在匹配记录
            String existsSql;
            JdbcTemplate jdbcTemplate;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                existsSql = String.format("SELECT EXISTS(SELECT 1 FROM `%s`.`%s` WHERE %s)", 
                        dataSourceName, tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                existsSql = String.format("SELECT EXISTS(SELECT 1 FROM `%s` WHERE %s)", 
                        tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            Boolean exists = jdbcTemplate.queryForObject(existsSql, Boolean.class, params.toArray());
            
            if (exists != null && exists) {
                logger.debug("表 {} 在搜索模式 {} 下找到匹配，过滤了 {} 个字段，实际搜索 {} 个字段", 
                        tableName, searchMode, columns.size() - filteredColumns.size(), filteredColumns.size());
            }
            
            return exists != null && exists;
            
        } catch (Exception e) {
            logger.warn("检查表 {} 是否包含值时出错: {}", tableName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 根据搜索模式过滤字段
     */
    private List<Map<String, Object>> filterColumnsBySearchMode(List<Map<String, Object>> columns, String searchValue, String searchMode) {
        List<Map<String, Object>> filteredColumns = new ArrayList<>();
        
        for (Map<String, Object> column : columns) {
            String dataType = (String) column.get("DATA_TYPE");
            boolean isNumeric = isNumericType(dataType);
            
            boolean shouldInclude = false;
            
            switch (searchMode.toLowerCase()) {
                case "text_only":
                    // 只搜索文本类型字段
                    shouldInclude = !isNumeric;
                    break;
                case "numeric_only":
                    // 只搜索数字类型字段
                    shouldInclude = isNumeric;
                    break;
                case "auto":
                    // 智能模式：根据搜索值的特征判断
                    boolean searchValueIsNumeric = isSearchValueNumeric(searchValue);
                    if (searchValueIsNumeric) {
                        // 搜索值是数字，优先搜索数字字段，但也搜索文本字段（数字可能被存储为文本）
                        shouldInclude = true;
                    } else {
                        // 搜索值是文本，只搜索文本字段（蛋白质名称等）
                        shouldInclude = !isNumeric;
                    }
                    break;
                case "all":
                default:
                    // 搜索所有字段
                    shouldInclude = true;
                    break;
            }
            
            if (shouldInclude) {
                filteredColumns.add(column);
            }
        }
        
        return filteredColumns;
    }
    
    /**
     * 判断搜索值是否为数字
     */
    private boolean isSearchValueNumeric(String searchValue) {
        if (searchValue == null || searchValue.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = searchValue.trim();
        
        // 检查是否为整数
        try {
            Long.parseLong(trimmed);
            return true;
        } catch (NumberFormatException e) {
            // 继续检查小数
        }
        
        // 检查是否为小数
        try {
            Double.parseDouble(trimmed);
            return trimmed.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 获取搜索类型信息
     */
    private String getSearchTypeInfo(String searchMode, String searchValue) {
        switch (searchMode.toLowerCase()) {
            case "text_only":
                return "文本字段搜索";
            case "numeric_only":
                return "数字字段搜索";
            case "auto":
                boolean isNumeric = isSearchValueNumeric(searchValue);
                if (isNumeric) {
                    return "智能数字搜索";
                } else {
                    return "智能文本搜索（蛋白质名称等）";
                }
            case "all":
            default:
                return "完整搜索";
        }
    }
    
    /**
     * 获取匹配记录的准确数量（完整统计）并同时建立搜索缓存
     */
    //private int getActualMatchCount(String dataSourceName, String tableName, String searchValue) {
    //    return getActualMatchCountWithMode(dataSourceName, tableName, searchValue, "all");
    //}
    
    /**
     * 获取匹配记录的准确数量（支持搜索模式）并同时建立搜索缓存
     */
    private int getActualMatchCountWithMode(String dataSourceName, String tableName, String searchValue, String searchMode) {
        try {
            // 缓存键包含搜索模式，以区分不同模式的搜索结果
            String cacheKey = searchValue + "_mode_" + searchMode;
            SearchCacheService.SearchCacheEntry cacheEntry = searchCacheService.getSearchCache(dataSourceName, tableName, cacheKey);
            if (cacheEntry != null) {
                logger.debug("从缓存中获取表 {} 的匹配记录数 (模式: {}): {}", tableName, searchMode, cacheEntry.getTotalCount());
                return cacheEntry.getTotalCount();
            }
            
            // 获取表的字段信息
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            
            if (columns.isEmpty()) {
                return 0;
            }
            
            // 根据搜索模式过滤字段
            List<Map<String, Object>> filteredColumns = filterColumnsBySearchMode(columns, searchValue, searchMode);
            
            if (filteredColumns.isEmpty()) {
                logger.debug("表 {} 在搜索模式 {} 下没有符合条件的字段", tableName, searchMode);
                return 0;
            }
            
            // 构建计数查询，只统计过滤后的字段
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();
            
            for (Map<String, Object> column : filteredColumns) {
                String columnName = (String) column.get("COLUMN_NAME");
                String dataType = (String) column.get("DATA_TYPE");
                
                if (whereClause.length() > 0) {
                    whereClause.append(" OR ");
                }
                
                // 根据数据类型构建不同的搜索条件，与getTableDataByValueWithPagination保持一致
                if (isNumericType(dataType)) {
                    try {
                        if (isInteger(searchValue)) {
                            whereClause.append("`").append(columnName).append("` = ?");
                            params.add(Long.parseLong(searchValue));
                        } else if (isDecimal(searchValue)) {
                            whereClause.append("`").append(columnName).append("` = ?");
                            params.add(Double.parseDouble(searchValue));
                        } else {
                            whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                            params.add("%" + searchValue + "%");
                        }
                    } catch (NumberFormatException e) {
                        whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                        params.add("%" + searchValue + "%");
                    }
                } else {
                    whereClause.append("CAST(`").append(columnName).append("` AS CHAR CHARACTER SET utf8) COLLATE utf8_general_ci LIKE ?");
                    params.add("%" + searchValue + "%");
                }
            }
            
            String countSql;
            JdbcTemplate jdbcTemplate;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE %s", 
                        dataSourceName, tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                countSql = String.format("SELECT COUNT(*) FROM `%s` WHERE %s", 
                        tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
            int finalCount = count != null ? count : 0;
            
            // 将搜索条件和结果缓存起来，以便后续快速分页
            searchCacheService.putSearchCache(dataSourceName, tableName, cacheKey, whereClause.toString(), params, finalCount);
            logger.debug("为表 {} 建立搜索缓存 (模式: {})，搜索值: {}, 过滤字段数: {}, 匹配记录数: {}", 
                    tableName, searchMode, searchValue, filteredColumns.size(), finalCount);
            
            return finalCount;
            
        } catch (Exception e) {
            logger.warn("获取表 {} 的匹配记录数时出错 (模式: {}): {}", tableName, searchMode, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 获取指定表中包含特定值的数据记录
     */
    public Map<String, Object> getTableDataByValue(String dataSourceName, String tableName, String searchValue, int limit) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索值不能为空");
        }
        
        // 限制返回行数
        if (limit > 10000) {
            limit = 10000;
        }
        
        try {
            // 获取表的所有字段信息
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            
            // 构建动态搜索SQL
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();
            
            for (int i = 0; i < columns.size(); i++) {
                Map<String, Object> column = columns.get(i);
                String columnName = (String) column.get("COLUMN_NAME");
                String dataType = (String) column.get("DATA_TYPE");
                
                if (whereClause.length() > 0) {
                    whereClause.append(" OR ");
                }
                
                // 根据数据类型构建不同的搜索条件
                if (isNumericType(dataType)) {
                    // 数值类型：尝试等值匹配
                    try {
                        if (isInteger(searchValue)) {
                            whereClause.append("`").append(columnName).append("` = ?");
                            params.add(Long.parseLong(searchValue));
                        } else if (isDecimal(searchValue)) {
                            whereClause.append("`").append(columnName).append("` = ?");
                            params.add(Double.parseDouble(searchValue));
                        } else {
                            whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                            params.add("%" + searchValue + "%");
                        }
                    } catch (NumberFormatException e) {
                        whereClause.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                        params.add("%" + searchValue + "%");
                    }
                } else {
                    // 字符串类型：使用LIKE模糊匹配
                    whereClause.append("CAST(`").append(columnName).append("` AS CHAR CHARACTER SET utf8) COLLATE utf8_general_ci LIKE ?");
                    params.add("%" + searchValue + "%");
                }
            }
            
            String sql;
            String countSql;
            JdbcTemplate jdbcTemplate;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                sql = String.format("SELECT * FROM `%s`.`%s` WHERE %s LIMIT ?", 
                        dataSourceName, tableName, whereClause.toString());
                countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE %s", 
                        dataSourceName, tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                sql = String.format("SELECT * FROM `%s` WHERE %s LIMIT ?", 
                        tableName, whereClause.toString());
                countSql = String.format("SELECT COUNT(*) FROM `%s` WHERE %s", 
                        tableName, whereClause.toString());
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            // 添加limit参数
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(limit);
            
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, sqlParams.toArray());
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("tableName", tableName);
            result.put("searchValue", searchValue);
            result.put("totalCount", totalCount != null ? totalCount : 0);
            result.put("returnedCount", data.size());
            result.put("dataSource", dataSourceName);
            result.put("limit", limit);
            
            return result;
            
        } catch (Exception e) {
            logger.error("根据值 {} 查询表 {} 的数据时发生错误: {}", searchValue, tableName, e.getMessage());
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据字段值获取表中包含该值的数据记录（分页版本，带缓存优化）
     */
    public Map<String, Object> getTableDataByValueWithPagination(String dataSourceName, String tableName, String searchValue, int page, int size) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索值不能为空");
        }
        
        // 限制每页大小
        if (size > 1000) {
            size = 1000;
        }
        if (size < 1) {
            size = 50;
        }
        
        // 计算偏移量
        int offset = (page - 1) * size;
        
        try {
            // 尝试从缓存获取搜索条件和总记录数
            SearchCacheService.SearchCacheEntry cacheEntry = searchCacheService.getSearchCache(dataSourceName, tableName, searchValue);
            
            String whereClause;
            List<Object> params;
            Integer totalCount;
            
            if (cacheEntry != null) {
                // 使用缓存的搜索条件
                whereClause = cacheEntry.getWhereClause();
                params = cacheEntry.getParams();
                totalCount = cacheEntry.getTotalCount();
                logger.debug("使用缓存的搜索条件进行分页查询: {}:{}", tableName, searchValue);
            } else {
                // 缓存中没有，需要构建搜索条件
                logger.debug("构建新的搜索条件并缓存: {}:{}", tableName, searchValue);
                
                // 获取表的所有字段信息
                List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
                
                // 构建动态搜索SQL
                StringBuilder whereClauseBuilder = new StringBuilder();
                params = new ArrayList<>();
                
                for (int i = 0; i < columns.size(); i++) {
                    Map<String, Object> column = columns.get(i);
                    String columnName = (String) column.get("COLUMN_NAME");
                    String dataType = (String) column.get("DATA_TYPE");
                    
                    if (whereClauseBuilder.length() > 0) {
                        whereClauseBuilder.append(" OR ");
                    }
                    
                    // 根据数据类型构建不同的搜索条件
                    if (isNumericType(dataType)) {
                        // 数值类型：尝试等值匹配
                        try {
                            if (isInteger(searchValue)) {
                                whereClauseBuilder.append("`").append(columnName).append("` = ?");
                                params.add(Long.parseLong(searchValue));
                            } else if (isDecimal(searchValue)) {
                                whereClauseBuilder.append("`").append(columnName).append("` = ?");
                                params.add(Double.parseDouble(searchValue));
                            } else {
                                whereClauseBuilder.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                                params.add("%" + searchValue + "%");
                            }
                        } catch (NumberFormatException e) {
                            whereClauseBuilder.append("CAST(`").append(columnName).append("` AS CHAR) LIKE ?");
                            params.add("%" + searchValue + "%");
                        }
                    } else {
                        // 字符串类型：使用LIKE模糊匹配
                        whereClauseBuilder.append("CAST(`").append(columnName).append("` AS CHAR CHARACTER SET utf8) COLLATE utf8_general_ci LIKE ?");
                        params.add("%" + searchValue + "%");
                    }
                }
                
                whereClause = whereClauseBuilder.toString();
                
                // 执行COUNT查询获取总记录数
                String countSql;
                JdbcTemplate jdbcTemplate;
                
                if (isUserCreatedDatabase(dataSourceName)) {
                    countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE %s", 
                            dataSourceName, tableName, whereClause);
                    jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                } else {
                    countSql = String.format("SELECT COUNT(*) FROM `%s` WHERE %s", 
                            tableName, whereClause);
                    jdbcTemplate = getJdbcTemplate(dataSourceName);
                }
                
                totalCount = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
                
                // 缓存搜索条件和总记录数
                searchCacheService.putSearchCache(dataSourceName, tableName, searchValue, whereClause, params, totalCount);
            }
            
            // 执行分页查询
            String sql;
            JdbcTemplate jdbcTemplate;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                sql = String.format("SELECT * FROM `%s`.`%s` WHERE %s LIMIT ?, ?", 
                        dataSourceName, tableName, whereClause);
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                sql = String.format("SELECT * FROM `%s` WHERE %s LIMIT ?, ?", 
                        tableName, whereClause);
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            // 添加分页参数
            List<Object> sqlParams = new ArrayList<>(params);
            sqlParams.add(offset);
            sqlParams.add(size);
            
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, sqlParams.toArray());
            
            // 计算总页数
            int totalPages = (int) Math.ceil((double) (totalCount != null ? totalCount : 0) / size);
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("tableName", tableName);
            result.put("searchValue", searchValue);
            result.put("totalCount", totalCount != null ? totalCount : 0);
            result.put("returnedCount", data.size());
            result.put("dataSource", dataSourceName);
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("totalPages", totalPages);
            result.put("cached", cacheEntry != null); // 标记是否使用了缓存
            
            return result;
            
        } catch (Exception e) {
            logger.error("根据值 {} 分页查询表 {} 的数据时发生错误: {}", searchValue, tableName, e.getMessage());
            throw new RuntimeException("分页查询失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 判断数据类型是否为数值类型
     */
    private boolean isNumericType(String dataType) {
        if (dataType == null) return false;
        String type = dataType.toLowerCase();
        return type.contains("int") || type.contains("decimal") || type.contains("float") || 
               type.contains("double") || type.contains("numeric") || type.contains("bigint") ||
               type.contains("smallint") || type.contains("tinyint") || type.contains("mediumint");
    }
    
    /**
     * 判断字符串是否为整数
     */
    private boolean isInteger(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 判断字符串是否为小数
     */
    private boolean isDecimal(String str) {
        try {
            Double.parseDouble(str);
            return str.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 根据字段名获取表中包含该字段的数据
     */
    public Map<String, Object> getTableDataByColumn(String dataSourceName, String tableName, String columnName, int limit) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        
        // 限制返回行数
        if (limit > 10000) {
            limit = 10000;
        }
        
        try {
            // 首先检查字段是否存在于表中
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            boolean columnExists = columns.stream()
                    .anyMatch(col -> columnName.equalsIgnoreCase((String) col.get("COLUMN_NAME")));
            
            if (!columnExists) {
                throw new IllegalArgumentException("字段 '" + columnName + "' 不存在于表 '" + tableName + "' 中");
            }
            
            String sql;
            JdbcTemplate jdbcTemplate;
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(dataSourceName)) {
                // 用户创建的数据库，使用默认数据源连接
                sql = String.format("SELECT * FROM `%s`.`%s` WHERE `%s` IS NOT NULL ORDER BY `%s` LIMIT ?", 
                        dataSourceName, tableName, columnName, columnName);
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                // 配置的数据源，使用原来的逻辑
                sql = String.format("SELECT * FROM `%s` WHERE `%s` IS NOT NULL ORDER BY `%s` LIMIT ?", 
                        tableName, columnName, columnName);
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            List<Map<String, Object>> data = jdbcTemplate.queryForList(sql, limit);
            
            // 获取总记录数（包含该字段且不为NULL的记录）
            String countSql;
            Integer totalCount;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                countSql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE `%s` IS NOT NULL", 
                        dataSourceName, tableName, columnName);
                totalCount = getJdbcTemplate(DEFAULT_DATASOURCE).queryForObject(countSql, Integer.class);
            } else {
                countSql = String.format("SELECT COUNT(*) FROM `%s` WHERE `%s` IS NOT NULL", tableName, columnName);
                totalCount = getJdbcTemplate(dataSourceName).queryForObject(countSql, Integer.class);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("tableName", tableName);
            result.put("columnName", columnName);
            result.put("totalCount", totalCount != null ? totalCount : 0);
            result.put("returnedCount", data.size());
            result.put("dataSource", dataSourceName);
            result.put("limit", limit);
            
            return result;
            
        } catch (Exception e) {
            logger.error("根据字段 {} 查询表 {} 的数据时发生错误: {}", columnName, tableName, e.getMessage());
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定数据源中表的索引信息
     */
    public List<Map<String, Object>> getTableIndexes(String dataSourceName, String tableName) {
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            String sql = String.format("SHOW INDEX FROM `%s`.`%s`", dataSourceName, tableName);
            return getJdbcTemplate(DEFAULT_DATASOURCE).queryForList(sql);
        } else {
            // 配置的数据源，使用原来的逻辑
            String sql = "SHOW INDEX FROM " + tableName;
            return getJdbcTemplate(dataSourceName).queryForList(sql);
        }
    }

    /**
     * 向指定数据源的表中插入数据
     */
    public int insertTableData(String dataSourceName, String tableName, Map<String, Object> data) {
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

        String sql;
        JdbcTemplate jdbcTemplate;
        
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            sql = String.format("INSERT INTO `%s`.`%s` (%s) VALUES (%s)", 
                    dataSourceName, tableName, columns.toString(), values.toString());
            jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
        } else {
            // 配置的数据源，使用原来的逻辑
            sql = String.format("INSERT INTO `%s` (%s) VALUES (%s)", 
                    tableName, columns.toString(), values.toString());
            jdbcTemplate = getJdbcTemplate(dataSourceName);
        }
        
        try {
            int result = jdbcTemplate.update(sql, params.toArray());
            
            // 清除该表的搜索缓存，确保搜索结果反映最新数据
            clearTableSearchCache(dataSourceName, tableName);
            
            return result;
        } catch (Exception e) {
            logger.error("插入数据失败 - 表: {}, SQL: {}, 错误: {}", tableName, sql, e.getMessage());
            
            // 提供更具体的错误信息
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("Duplicate entry")) {
                    throw new RuntimeException("数据重复：该记录的某些字段值已存在，请检查主键或唯一字段。", e);
                } else if (errorMessage.contains("cannot be null")) {
                    throw new RuntimeException("必填字段缺失：某些必填字段未填写或为空值。", e);
                } else if (errorMessage.contains("Data too long")) {
                    throw new RuntimeException("数据长度超限：某些字段的数据长度超过了定义的最大长度。", e);
                } else if (errorMessage.contains("Out of range value")) {
                    throw new RuntimeException("数值超出范围：某些整数字段的值超出了定义的数据类型范围。建议使用BIGINT类型或检查数据。", e);
                } else if (errorMessage.contains("Incorrect integer value")) {
                    throw new RuntimeException("整数格式错误：某些字段包含非法的整数值。请检查数据格式。", e);
                } else if (errorMessage.contains("foreign key constraint")) {
                    throw new RuntimeException("外键约束失败：引用的数据不存在，请确保相关联的记录已存在。", e);
                } else if (errorMessage.contains("Incorrect") && errorMessage.contains("value")) {
                    throw new RuntimeException("数据格式错误：某些字段的数据格式不正确，请检查数据类型。", e);
                }
            }
            
            throw new RuntimeException("插入数据失败: " + errorMessage, e);
        }
    }

    /**
     * 删除指定数据源表中的数据
     */
    public int deleteTableData(String dataSourceName, String tableName, Map<String, Object> whereConditions) {
        if (whereConditions == null || whereConditions.isEmpty()) {
            throw new IllegalArgumentException("删除条件不能为空，不允许删除整个表");
        }

        // 构建删除SQL
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("`").append(entry.getKey()).append("` = ?");
            params.add(entry.getValue());
        }

        String sql;
        JdbcTemplate jdbcTemplate;
        
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            sql = String.format("DELETE FROM `%s`.`%s` WHERE %s", dataSourceName, tableName, whereClause.toString());
            jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
        } else {
            // 配置的数据源，使用原来的逻辑
            sql = String.format("DELETE FROM `%s` WHERE %s", tableName, whereClause.toString());
            jdbcTemplate = getJdbcTemplate(dataSourceName);
        }
        
        int result = jdbcTemplate.update(sql, params.toArray());
        
        // 清除该表的搜索缓存，确保搜索结果反映最新数据
        clearTableSearchCache(dataSourceName, tableName);
        
        return result;
    }

    /**
     * 更新指定数据源表中的数据
     */
    public int updateTableData(String dataSourceName, String tableName, Map<String, Object> updateData, Map<String, Object> whereConditions) {
        if (updateData == null || updateData.isEmpty()) {
            throw new IllegalArgumentException("更新数据不能为空");
        }
        if (whereConditions == null || whereConditions.isEmpty()) {
            throw new IllegalArgumentException("更新条件不能为空，不允许更新整个表");
        }

        // 构建更新SQL
        StringBuilder setClause = new StringBuilder();
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // 构建SET子句
        for (Map.Entry<String, Object> entry : updateData.entrySet()) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append("`").append(entry.getKey()).append("` = ?");
            params.add(entry.getValue());
        }

        // 构建WHERE子句
        for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("`").append(entry.getKey()).append("` = ?");
            params.add(entry.getValue());
        }

        String sql;
        JdbcTemplate jdbcTemplate;
        
        // 检查是否为用户创建的数据库
        if (isUserCreatedDatabase(dataSourceName)) {
            // 用户创建的数据库，使用默认数据源连接
            sql = String.format("UPDATE `%s`.`%s` SET %s WHERE %s", 
                    dataSourceName, tableName, setClause.toString(), whereClause.toString());
            jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
        } else {
            // 配置的数据源，使用原来的逻辑
            sql = String.format("UPDATE `%s` SET %s WHERE %s", 
                    tableName, setClause.toString(), whereClause.toString());
            jdbcTemplate = getJdbcTemplate(dataSourceName);
        }
        
        int result = jdbcTemplate.update(sql, params.toArray());
        
        // 清除该表的搜索缓存，确保搜索结果反映最新数据
        clearTableSearchCache(dataSourceName, tableName);
        
        return result;
    }

    // =============================================================================
    // 兼容性方法（保持向后兼容，使用默认数据源）
    // =============================================================================

    /**
     * 获取数据库中所有表的信息（使用默认数据源）
     */
    public List<Map<String, Object>> getAllTables() {
        return getAllTables(DEFAULT_DATASOURCE);
    }

    /**
     * 获取指定表的列信息（使用默认数据源）
     */
    public List<Map<String, Object>> getTableColumns(String tableName) {
        return getTableColumns(DEFAULT_DATASOURCE, tableName);
    }

    /**
     * 获取表的前N行数据（使用默认数据源）
     */
    public List<Map<String, Object>> getTableData(String tableName, int limit) {
        return getTableData(DEFAULT_DATASOURCE, tableName, limit);
    }

    /**
     * 分页获取表数据（使用默认数据源）
     */
    public Map<String, Object> getTableDataWithPagination(String tableName, int page, int size) {
        return getTableDataWithPagination(DEFAULT_DATASOURCE, tableName, page, size);
    }





    /**
     * 获取表的索引信息（使用默认数据源）
     */
    public List<Map<String, Object>> getTableIndexes(String tableName) {
        return getTableIndexes(DEFAULT_DATASOURCE, tableName);
    }

    /**
     * 向表中插入数据（使用默认数据源）
     */
    public int insertTableData(String tableName, Map<String, Object> data) {
        return insertTableData(DEFAULT_DATASOURCE, tableName, data);
    }

    /**
     * 删除表中的数据（使用默认数据源）
     */
    public int deleteTableData(String tableName, Map<String, Object> whereConditions) {
        return deleteTableData(DEFAULT_DATASOURCE, tableName, whereConditions);
    }

    /**
     * 更新表中的数据（使用默认数据源）
     */
    public int updateTableData(String tableName, Map<String, Object> updateData, Map<String, Object> whereConditions) {
        return updateTableData(DEFAULT_DATASOURCE, tableName, updateData, whereConditions);
    }

    /**
     * 根据字段名搜索包含该字段的表（使用默认数据源）
     */
    public List<Map<String, Object>> findTablesByColumn(String columnName) {
        return findTablesByColumn(DEFAULT_DATASOURCE, columnName);
    }

    /**
     * 根据字段名获取表中包含该字段的数据（使用默认数据源）
     */
    public Map<String, Object> getTableDataByColumn(String tableName, String columnName, int limit) {
        return getTableDataByColumn(DEFAULT_DATASOURCE, tableName, columnName, limit);
    }



    /**
     * 获取数据库信息（兼容性方法）
     */
    public Map<String, Object> getDatabaseInfo(String database) {
        // 始终使用默认数据源连接，但查询指定的数据库信息
        String dataSourceName = DEFAULT_DATASOURCE;
        String targetDatabase = database != null ? database : "login";
        
        Map<String, Object> info = new HashMap<>();
        
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            
            // 如果传入的是数据库名，需要检查该数据库是否存在
            if (database != null && !database.equals("login")) {
                // 检查用户创建的数据库是否存在
                if (!databaseExists(dataSourceName, database)) {
                    throw new IllegalArgumentException("数据库不存在: " + database);
                }
            }
            
            // 获取表总数
            String tableCountSql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?";
            Integer tableCount = jdbcTemplate.queryForObject(tableCountSql, Integer.class, targetDatabase);
            
            // 获取数据库总大小
            String sizeSql = "SELECT SUM(DATA_LENGTH + INDEX_LENGTH) as total_size " +
                    "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?";
            Long totalSize = jdbcTemplate.queryForObject(sizeSql, Long.class, targetDatabase);
            
            info.put("name", targetDatabase);
            info.put("tableCount", tableCount != null ? tableCount : 0);
            info.put("totalSize", totalSize != null ? totalSize : 0L);
            info.put("dataSource", dataSourceName);
            info.put("displayName", getDatabaseDisplayName(targetDatabase));
            info.put("description", getDatabaseDescription(targetDatabase));
            
        } catch (Exception e) {
            logger.error("获取数据库信息失败: {}", e.getMessage());
            info.put("name", targetDatabase);
            info.put("tableCount", 0);
            info.put("totalSize", 0L);
            info.put("dataSource", dataSourceName);
            info.put("error", e.getMessage());
        }
        
        return info;
    }

    /**
     * 获取可用数据库列表（现在返回所有数据源）
     */
    public List<Map<String, Object>> getAvailableDatabases() {
        List<Map<String, Object>> databases = new ArrayList<>();
        
        try {
            // 只返回login数据库
            Map<String, Object> db = new HashMap<>();
            db.put("name", "login");
            db.put("displayName", "Login Database");
            db.put("description", "用户登录数据库");
            db.put("connected", true);
            databases.add(db);
            
        } catch (Exception e) {
            logger.error("获取数据库列表失败: {}", e.getMessage());
        }
        
        return databases;
    }
    
    // =============================================================================
    // 数据库管理方法（新建、删除数据库）
    // =============================================================================
    
    /**
     * 创建新数据库
     */
    public boolean createDatabase(String dataSourceName, String databaseName, String charset, String collation) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            
            // 验证数据库名称的合法性
            if (!isValidDatabaseName(databaseName)) {
                throw new IllegalArgumentException("数据库名称不符合规范");
            }
            
            // 检查数据库是否已存在
            if (databaseExists(dataSourceName, databaseName)) {
                throw new IllegalArgumentException("数据库已存在");
            }
            
            // 构建创建数据库的SQL
            StringBuilder sql = new StringBuilder();
            sql.append("CREATE DATABASE `").append(databaseName).append("`");
            
            // 添加字符集和排序规则
            if (charset != null && !charset.trim().isEmpty()) {
                sql.append(" CHARACTER SET ").append(charset);
            } else {
                sql.append(" CHARACTER SET utf8");
            }
            
            if (collation != null && !collation.trim().isEmpty()) {
                sql.append(" COLLATE ").append(collation);
            } else {
                sql.append(" COLLATE utf8_general_ci");
            }
            
            // 执行创建数据库的SQL
            jdbcTemplate.execute(sql.toString());
            
            logger.info("成功创建数据库: {}", databaseName);
            return true;
            
        } catch (Exception e) {
            logger.error("创建数据库失败 - 数据库名: {}, 错误: {}", databaseName, e.getMessage());
            throw new RuntimeException("创建数据库失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除数据库
     */
    public boolean dropDatabase(String dataSourceName, String databaseName) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            
            // 验证数据库名称的合法性
            if (!isValidDatabaseName(databaseName)) {
                throw new IllegalArgumentException("数据库名称不符合规范");
            }
            
            // 检查数据库是否存在
            if (!databaseExists(dataSourceName, databaseName)) {
                throw new IllegalArgumentException("数据库不存在");
            }
            
            // 禁止删除系统数据库
            if (isSystemDatabase(databaseName)) {
                throw new IllegalArgumentException("不能删除系统数据库");
            }
            
            // 执行删除数据库的SQL
            String sql = "DROP DATABASE `" + databaseName + "`";
            jdbcTemplate.execute(sql);
            
            logger.info("成功删除数据库: {}", databaseName);
            return true;
            
        } catch (Exception e) {
            logger.error("删除数据库失败 - 数据库名: {}, 错误: {}", databaseName, e.getMessage());
            throw new RuntimeException("删除数据库失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查数据库是否存在
     */
    private boolean databaseExists(String dataSourceName, String databaseName) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            String sql = "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName);
            return count != null && count > 0;
        } catch (Exception e) {
            logger.error("检查数据库是否存在时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证数据库名称的合法性
     */
    private boolean isValidDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.trim().isEmpty()) {
            return false;
        }
        
        // 数据库名称长度限制
        if (databaseName.length() > 64) {
            return false;
        }
        
        // 数据库名称只能包含字母、数字、下划线
        return databaseName.matches("^[a-zA-Z0-9_]+$");
    }
    
    /**
     * 检查是否为系统数据库
     */
    private boolean isSystemDatabase(String databaseName) {
        Set<String> systemDatabases = Set.of(
            "information_schema", 
            "mysql", 
            "performance_schema", 
            "sys",
            "login"
        );
        return systemDatabases.contains(databaseName.toLowerCase());
    }
    
    /**
     * 获取所有数据库列表（包括用户创建的数据库）
     */
    public List<Map<String, Object>> getAllDatabases(String dataSourceName) {
        try {
            JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
            String sql = "SELECT SCHEMA_NAME as database_name, " +
                        "DEFAULT_CHARACTER_SET_NAME as charset, " +
                        "DEFAULT_COLLATION_NAME as collation " +
                        "FROM information_schema.SCHEMATA " +
                        "WHERE SCHEMA_NAME NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys') " +
                        "ORDER BY SCHEMA_NAME";
            
            List<Map<String, Object>> databases = jdbcTemplate.queryForList(sql);
            
            // 为每个数据库添加显示名称和描述
            for (Map<String, Object> db : databases) {
                String dbName = (String) db.get("database_name");
                db.put("name", dbName);
                db.put("displayName", getDatabaseDisplayName(dbName));
                db.put("description", getDatabaseDescription(dbName));
                db.put("type", "mysql");
                db.put("status", "active");
            }
            
            return databases;
            
        } catch (Exception e) {
            logger.error("获取数据库列表失败: {}", e.getMessage());
            // 返回默认数据库列表
            return getAvailableDatabases();
        }
    }
    
    /**
     * 获取数据库的显示名称
     */
    private String getDatabaseDisplayName(String databaseName) {
        switch (databaseName.toLowerCase()) {
            case "login":
                return "Login Database";
            default:
                return databaseName;
        }
    }
    
    /**
     * 获取数据库的描述
     */
    private String getDatabaseDescription(String databaseName) {
        switch (databaseName.toLowerCase()) {
            case "login":
                return "用户登录数据库（仅管理员）";
            default:
                // 检查是否为用户创建的数据库
                if (isUserCreatedDatabase(databaseName)) {
                    return "用户创建的数据库 - " + databaseName;
                }
                return "数据库";
        }
    }
    
    /**
     * 检查是否为用户创建的数据库（公共方法）
     */
    public boolean isUserCreatedDatabase(String databaseName) {
        Set<String> systemDatabases = Set.of(
            "login",
            "information_schema", 
            "mysql", 
            "performance_schema", 
            "sys"
        );
        return !systemDatabases.contains(databaseName.toLowerCase());
    }
    
    // =============================================================================
    // 表管理相关方法
    // =============================================================================
    
    /**
     * 创建新表
     */
    public boolean createTable(String dataSourceName, String databaseName, String tableName, 
                              List<Map<String, Object>> columns, String tableComment) {
        try {
            // 验证参数
            if (tableName == null || tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("表名不能为空");
            }
            
            if (!isValidTableName(tableName)) {
                throw new IllegalArgumentException("表名不符合规范");
            }
            
            if (columns == null || columns.isEmpty()) {
                throw new IllegalArgumentException("至少需要定义一个列");
            }
            
            // 验证外键约束
            validateForeignKeyConstraints(dataSourceName, databaseName, columns);
            
            JdbcTemplate jdbcTemplate;
            StringBuilder sql = new StringBuilder();
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(databaseName)) {
                // 用户创建的数据库，使用默认数据源连接
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql.append("CREATE TABLE `").append(databaseName).append("`.`").append(tableName).append("` (");
            } else {
                // 配置的数据源
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql.append("CREATE TABLE `").append(tableName).append("` (");
            }
            
            // 构建列定义
            List<String> columnDefinitions = new ArrayList<>();
            List<String> primaryKeys = new ArrayList<>();
            
            for (Map<String, Object> column : columns) {
                String columnDef = buildColumnDefinition(column);
                columnDefinitions.add(columnDef);
                
                // 收集主键列
                Boolean isPrimary = (Boolean) column.get("isPrimary");
                if (isPrimary != null && isPrimary) {
                    primaryKeys.add((String) column.get("name"));
                }
            }
            
            sql.append(String.join(", ", columnDefinitions));
            
            // 添加主键约束
            if (!primaryKeys.isEmpty()) {
                sql.append(", PRIMARY KEY (");
                sql.append(primaryKeys.stream()
                    .map(key -> "`" + key + "`")
                    .collect(java.util.stream.Collectors.joining(", ")));
                sql.append(")");
            }
            
            // 添加外键约束
            List<String> foreignKeyConstraints = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                Boolean isForeignKey = (Boolean) column.get("isForeignKey");
                if (isForeignKey != null && isForeignKey) {
                    String referenceTable = (String) column.get("referenceTable");
                    String referenceColumn = (String) column.get("referenceColumn");
                    String onUpdate = (String) column.get("onUpdate");
                    String onDelete = (String) column.get("onDelete");
                    String columnName = (String) column.get("name");
                    
                    if (referenceTable != null && !referenceTable.trim().isEmpty() && 
                        referenceColumn != null && !referenceColumn.trim().isEmpty()) {
                        
                        String constraintName = "fk_" + tableName + "_" + columnName;
                        StringBuilder fkConstraint = new StringBuilder();
                        fkConstraint.append("CONSTRAINT `").append(constraintName).append("` ");
                        fkConstraint.append("FOREIGN KEY (`").append(columnName).append("`) ");
                        fkConstraint.append("REFERENCES ");
                        
                        if (isUserCreatedDatabase(databaseName)) {
                            fkConstraint.append("`").append(databaseName).append("`.");
                        }
                        fkConstraint.append("`").append(referenceTable).append("` (`").append(referenceColumn).append("`)");
                        
                        // 添加ON UPDATE和ON DELETE子句
                        if (onUpdate != null && !onUpdate.trim().isEmpty() && !"RESTRICT".equals(onUpdate)) {
                            fkConstraint.append(" ON UPDATE ").append(onUpdate);
                        }
                        if (onDelete != null && !onDelete.trim().isEmpty() && !"RESTRICT".equals(onDelete)) {
                            fkConstraint.append(" ON DELETE ").append(onDelete);
                        } else if (onDelete == null || onDelete.trim().isEmpty()) {
                            // 如果没有指定删除动作，默认使用CASCADE
                            fkConstraint.append(" ON DELETE CASCADE");
                        }
                        
                        foreignKeyConstraints.add(fkConstraint.toString());
                    }
                }
            }
            
            // 添加外键约束到SQL
            if (!foreignKeyConstraints.isEmpty()) {
                sql.append(", ");
                sql.append(foreignKeyConstraints.stream()
                    .collect(java.util.stream.Collectors.joining(", ")));
            }
            
            sql.append(")");
            
            // 添加表选项
            sql.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci");
            
            // 添加表注释
            if (tableComment != null && !tableComment.trim().isEmpty()) {
                sql.append(" COMMENT='").append(tableComment.replace("'", "\\'")).append("'");
            }
            
            // 执行创建表的SQL
            jdbcTemplate.execute(sql.toString());
            
            logger.info("成功创建表: {}.{}", databaseName, tableName);
            return true;
            
        } catch (Exception e) {
            logger.error("创建表失败 - 数据库: {}, 表名: {}, 错误: {}", databaseName, tableName, e.getMessage());
            
            // 处理外键约束错误
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("error code [1215]") || errorMessage.contains("Cannot add foreign key constraint")) {
                    throw new RuntimeException("外键约束创建失败：请检查引用的表和列是否存在，以及数据类型是否匹配。外键列的数据类型必须与被引用列完全一致。", e);
                } else if (errorMessage.contains("error code [1005]")) {
                    throw new RuntimeException("表创建失败：可能存在表名冲突或权限问题。", e);
                } else if (errorMessage.contains("error code [1050]")) {
                    throw new RuntimeException("表已存在：该表名已被使用，请选择其他表名。", e);
                } else if (errorMessage.contains("Duplicate column name")) {
                    throw new RuntimeException("列名重复：表中存在重复的列名，请检查列定义。", e);
                } else if (errorMessage.contains("Data too long")) {
                    throw new RuntimeException("数据长度超限：某些字段的长度设置可能过大。", e);
                }
            }
            
            throw new RuntimeException("创建表失败: " + errorMessage, e);
        }
    }
    
    /**
     * 构建列定义SQL
     */
    private String buildColumnDefinition(Map<String, Object> column) {
        StringBuilder columnDef = new StringBuilder();
        
        String name = (String) column.get("name");
        String dataType = (String) column.get("dataType");
        Integer length = parseIntegerSafely(column.get("length"));
        Integer decimals = parseIntegerSafely(column.get("decimals"));
        Boolean isNotNull = (Boolean) column.get("isNotNull");
        Boolean isAutoIncrement = (Boolean) column.get("isAutoIncrement");
        Object defaultValue = column.get("defaultValue");
        String comment = (String) column.get("comment");
        
        // 列名
        columnDef.append("`").append(name).append("` ");
        
        // 数据类型
        columnDef.append(dataType.toUpperCase());
        
        // 长度和精度
        if (needsLength(dataType)) {
            if (length != null && length > 0) {
                columnDef.append("(").append(length);
                if (needsDecimals(dataType) && decimals != null && decimals > 0) {
                    columnDef.append(",").append(decimals);
                }
                columnDef.append(")");
            }
        }
        
        // NOT NULL约束
        if (isNotNull != null && isNotNull) {
            columnDef.append(" NOT NULL");
        }
        
        // AUTO_INCREMENT
        if (isAutoIncrement != null && isAutoIncrement) {
            columnDef.append(" AUTO_INCREMENT");
        }
        
        // 默认值
        if (defaultValue != null) {
            String defaultStr = defaultValue.toString().trim();
            if (!defaultStr.isEmpty() && !"NULL".equalsIgnoreCase(defaultStr)) {
                if (isStringType(dataType)) {
                    columnDef.append(" DEFAULT '").append(defaultStr.replace("'", "\\'")).append("'");
                } else {
                    columnDef.append(" DEFAULT ").append(defaultStr);
                }
            }
        }
        
        // 列注释
        if (comment != null && !comment.trim().isEmpty()) {
            columnDef.append(" COMMENT '").append(comment.replace("'", "\\'")).append("'");
        }
        
        return columnDef.toString();
    }
    
    /**
     * 检查数据类型是否需要长度参数
     */
    private boolean needsLength(String dataType) {
        Set<String> typesWithLength = Set.of(
            "varchar", "char", "varbinary", "binary",
            "decimal", "numeric", "float", "double",
            "bit", "tinyint", "smallint", "mediumint", "int", "bigint"
        );
        return typesWithLength.contains(dataType.toLowerCase());
    }
    
    /**
     * 检查数据类型是否需要小数位参数
     */
    private boolean needsDecimals(String dataType) {
        Set<String> typesWithDecimals = Set.of("decimal", "numeric", "float", "double");
        return typesWithDecimals.contains(dataType.toLowerCase());
    }
    
    /**
     * 检查是否为字符串类型
     */
    private boolean isStringType(String dataType) {
        Set<String> stringTypes = Set.of(
            "char", "varchar", "text", "tinytext", "mediumtext", "longtext",
            "enum", "set", "json"
        );
        return stringTypes.contains(dataType.toLowerCase());
    }
    
    /**
     * 安全地解析整数值，支持字符串和整数输入
     */
    private Integer parseIntegerSafely(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        }
        
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                logger.warn("无法解析整数值: {}", strValue);
                return null;
            }
        }
        
        // 尝试转换其他数字类型
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        logger.warn("不支持的整数类型: {}", value.getClass().getName());
        return null;
    }
    
    /**
     * 验证表名的合法性
     */
    private boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return false;
        }
        
        // 表名长度限制
        if (tableName.length() > 64) {
            return false;
        }
        
        // 表名只能包含字母、数字、下划线，不能以数字开头
        return tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
    
    /**
     * 检查表是否存在
     */
    public boolean tableExists(String dataSourceName, String databaseName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(databaseName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName, tableName);
                return count != null && count > 0;
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            logger.error("检查表是否存在时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 删除表
     */
    public boolean dropTable(String dataSourceName, String databaseName, String tableName) {
        try {
            if (!isValidTableName(tableName)) {
                throw new IllegalArgumentException("表名不符合规范");
            }
            
            if (!tableExists(dataSourceName, databaseName, tableName)) {
                throw new IllegalArgumentException("表不存在");
            }
            
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(databaseName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "DROP TABLE `" + databaseName + "`.`" + tableName + "`";
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "DROP TABLE `" + tableName + "`";
            }
            
            jdbcTemplate.execute(sql);
            
            logger.info("成功删除表: {}.{}", databaseName, tableName);
            return true;
            
        } catch (Exception e) {
            logger.error("删除表失败 - 数据库: {}, 表名: {}, 错误: {}", databaseName, tableName, e.getMessage());
            throw new RuntimeException("删除表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取支持的数据类型列表
     */
    public List<Map<String, Object>> getSupportedDataTypes() {
        List<Map<String, Object>> dataTypes = new ArrayList<>();
        
        // 数值类型
        dataTypes.add(createDataType("TINYINT", "整数", "数值", true, false, "1字节整数 (-128 到 127)"));
        dataTypes.add(createDataType("SMALLINT", "整数", "数值", true, false, "2字节整数 (-32,768 到 32,767)"));
        dataTypes.add(createDataType("MEDIUMINT", "整数", "数值", true, false, "3字节整数 (-8,388,608 到 8,388,607)"));
        dataTypes.add(createDataType("INT", "整数", "数值", true, false, "4字节整数 (-2,147,483,648 到 2,147,483,647)"));
        dataTypes.add(createDataType("BIGINT", "整数", "数值", true, false, "8字节整数"));
        dataTypes.add(createDataType("DECIMAL", "小数", "数值", true, true, "精确小数，适合金额等"));
        dataTypes.add(createDataType("FLOAT", "小数", "数值", true, true, "单精度浮点数"));
        dataTypes.add(createDataType("DOUBLE", "小数", "数值", true, true, "双精度浮点数"));
        
        // 字符串类型
        dataTypes.add(createDataType("CHAR", "字符", "字符串", true, false, "定长字符串 (0-255)"));
        dataTypes.add(createDataType("VARCHAR", "字符", "字符串", true, false, "变长字符串 (0-65,535)"));
        dataTypes.add(createDataType("TEXT", "文本", "字符串", false, false, "长文本 (0-65,535)"));
        dataTypes.add(createDataType("MEDIUMTEXT", "文本", "字符串", false, false, "中等长度文本 (0-16,777,215)"));
        dataTypes.add(createDataType("LONGTEXT", "文本", "字符串", false, false, "长文本 (0-4,294,967,295)"));
        
        // 日期时间类型
        dataTypes.add(createDataType("DATE", "日期", "日期时间", false, false, "日期 (YYYY-MM-DD)"));
        dataTypes.add(createDataType("TIME", "时间", "日期时间", false, false, "时间 (HH:MM:SS)"));
        dataTypes.add(createDataType("DATETIME", "日期时间", "日期时间", false, false, "日期时间 (YYYY-MM-DD HH:MM:SS)"));
        dataTypes.add(createDataType("TIMESTAMP", "时间戳", "日期时间", false, false, "时间戳"));
        dataTypes.add(createDataType("YEAR", "年份", "日期时间", false, false, "年份 (YYYY)"));
        
        // 二进制类型
        dataTypes.add(createDataType("BINARY", "二进制", "二进制", true, false, "定长二进制数据"));
        dataTypes.add(createDataType("VARBINARY", "二进制", "二进制", true, false, "变长二进制数据"));
        dataTypes.add(createDataType("BLOB", "二进制", "二进制", false, false, "二进制大对象"));
        
        // 其他类型
        dataTypes.add(createDataType("JSON", "JSON", "其他", false, false, "JSON数据"));
        dataTypes.add(createDataType("ENUM", "枚举", "其他", false, false, "枚举类型"));
        
        return dataTypes;
    }
    
    /**
     * 创建数据类型定义
     */
    private Map<String, Object> createDataType(String name, String displayName, String category, 
                                              boolean needsLength, boolean needsDecimals, String description) {
        Map<String, Object> dataType = new HashMap<>();
        dataType.put("name", name);
        dataType.put("displayName", displayName);
        dataType.put("category", category);
        dataType.put("needsLength", needsLength);
        dataType.put("needsDecimals", needsDecimals);
        dataType.put("description", description);
        return dataType;
    }
    
    /**
     * 验证外键约束
     */
    private void validateForeignKeyConstraints(String dataSourceName, String databaseName, 
                                             List<Map<String, Object>> columns) {
        for (Map<String, Object> column : columns) {
            Boolean isForeignKey = (Boolean) column.get("isForeignKey");
            if (isForeignKey != null && isForeignKey) {
                String referenceTable = (String) column.get("referenceTable");
                String referenceColumn = (String) column.get("referenceColumn");
                String columnName = (String) column.get("name");
                
                if (referenceTable == null || referenceTable.trim().isEmpty()) {
                    throw new IllegalArgumentException("外键列 '" + columnName + "' 必须指定引用表");
                }
                
                if (referenceColumn == null || referenceColumn.trim().isEmpty()) {
                    throw new IllegalArgumentException("外键列 '" + columnName + "' 必须指定引用列");
                }
                
                // 验证引用表是否存在
                if (!tableExists(dataSourceName, databaseName, referenceTable)) {
                    throw new IllegalArgumentException("外键引用的表 '" + referenceTable + "' 不存在");
                }
                
                // 验证引用列是否存在
                if (!columnExists(dataSourceName, databaseName, referenceTable, referenceColumn)) {
                    throw new IllegalArgumentException("外键引用的列 '" + referenceTable + "." + referenceColumn + "' 不存在");
                }
                
                // 验证数据类型是否匹配
                validateForeignKeyDataTypeMatch(dataSourceName, databaseName, column, referenceTable, referenceColumn);
                
                // 验证ON UPDATE和ON DELETE动作的有效性
                String onUpdate = (String) column.get("onUpdate");
                String onDelete = (String) column.get("onDelete");
                validateForeignKeyAction(onUpdate, "ON UPDATE");
                validateForeignKeyAction(onDelete, "ON DELETE");
            }
        }
    }
    
    /**
     * 验证外键动作的有效性
     */
    private void validateForeignKeyAction(String action, String actionType) {
        if (action != null && !action.trim().isEmpty()) {
            Set<String> validActions = Set.of("RESTRICT", "CASCADE", "SET NULL", "NO ACTION");
            if (!validActions.contains(action.toUpperCase())) {
                throw new IllegalArgumentException(actionType + " 动作 '" + action + "' 无效，有效值为: " + validActions);
            }
        }
    }
    
    /**
     * 检查列是否存在
     */
    private boolean columnExists(String dataSourceName, String databaseName, String tableName, String columnName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(databaseName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName, tableName, columnName);
                return count != null && count > 0;
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            logger.error("检查列是否存在时发生错误: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 验证外键数据类型匹配
     */
    private void validateForeignKeyDataTypeMatch(String dataSourceName, String databaseName, 
                                               Map<String, Object> column, String referenceTable, String referenceColumn) {
        try {
            String columnName = (String) column.get("name");
            String columnDataType = (String) column.get("dataType");
            Integer columnLength = parseIntegerSafely(column.get("length"));
            
            // 获取引用列的数据类型信息
            Map<String, Object> refColumnInfo = getColumnInfo(dataSourceName, databaseName, referenceTable, referenceColumn);
            if (refColumnInfo == null) {
                throw new IllegalArgumentException("无法获取引用列 '" + referenceTable + "." + referenceColumn + "' 的信息");
            }
            
            String refDataType = (String) refColumnInfo.get("DATA_TYPE");
            String refColumnType = (String) refColumnInfo.get("COLUMN_TYPE");
            
            // 标准化数据类型进行比较
            String normalizedColumnType = normalizeDataType(columnDataType, columnLength);
            String normalizedRefType = normalizeDataType(refDataType, refColumnType);
            
            if (!normalizedColumnType.equalsIgnoreCase(normalizedRefType)) {
                throw new IllegalArgumentException(
                    String.format("外键列 '%s' 的数据类型 '%s' 与引用列 '%s.%s' 的数据类型 '%s' 不匹配。" +
                        "解决方案：请将外键列 '%s' 的数据类型修改为 '%s'，确保与被引用列完全一致。", 
                        columnName, normalizedColumnType, referenceTable, referenceColumn, normalizedRefType, columnName, normalizedRefType)
                );
            }
            
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            logger.error("验证外键数据类型匹配时发生错误: {}", e.getMessage());
            throw new IllegalArgumentException("无法验证外键数据类型匹配: " + e.getMessage());
        }
    }
    
    /**
     * 获取列的详细信息
     */
    private Map<String, Object> getColumnInfo(String dataSourceName, String databaseName, String tableName, String columnName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(databaseName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE " +
                      "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, databaseName, tableName, columnName);
                return results.isEmpty() ? null : results.get(0);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT DATA_TYPE, COLUMN_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE " +
                      "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, tableName, columnName);
                return results.isEmpty() ? null : results.get(0);
            }
        } catch (Exception e) {
            logger.error("获取列信息时发生错误: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 标准化数据类型用于比较
     */
    private String normalizeDataType(String dataType, Object lengthOrColumnType) {
        if (dataType == null) return "";
        
        String normalized = dataType.toUpperCase();
        
        // 如果传入的是完整的列类型定义（如 "varchar(255)"），处理后返回
        if (lengthOrColumnType instanceof String) {
            String columnType = (String) lengthOrColumnType;
            if (columnType != null) {
                String upperColumnType = columnType.toUpperCase();
                
                // 对于数值类型，移除显示宽度
                if (isNumericTypeWithDisplayWidth(upperColumnType)) {
                    return removeDisplayWidth(upperColumnType);
                }
                
                // 对于需要长度的类型，保留长度信息
                if (upperColumnType.contains("(") && needsLengthForComparison(extractBaseType(upperColumnType))) {
                    return upperColumnType;
                }
                
                // 其他情况返回基础类型
                return extractBaseType(upperColumnType);
            }
        }
        
        // 如果传入的是长度，构建完整类型
        if (lengthOrColumnType instanceof Integer) {
            Integer length = (Integer) lengthOrColumnType;
            if (length != null && length > 0 && needsLengthForComparison(normalized)) {
                return normalized + "(" + length + ")";
            }
        }
        
        return normalized;
    }
    
    /**
     * 判断是否为带显示宽度的数值类型
     */
    private boolean isNumericTypeWithDisplayWidth(String columnType) {
        if (columnType == null) return false;
        String baseType = extractBaseType(columnType);
        Set<String> numericTypes = Set.of("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT");
        return numericTypes.contains(baseType) && columnType.contains("(");
    }
    
    /**
     * 移除数值类型的显示宽度
     */
    private String removeDisplayWidth(String columnType) {
        if (columnType == null) return "";
        return extractBaseType(columnType);
    }
    
    /**
     * 提取基础数据类型（移除括号及其内容）
     */
    private String extractBaseType(String columnType) {
        if (columnType == null) return "";
        int index = columnType.indexOf('(');
        return index > 0 ? columnType.substring(0, index) : columnType;
    }
    
    /**
     * 判断数据类型是否需要长度参数进行比较
     */
    private boolean needsLengthForComparison(String dataType) {
        Set<String> typesNeedingLength = Set.of("VARCHAR", "CHAR", "VARBINARY", "BINARY", "DECIMAL", "NUMERIC");
        return typesNeedingLength.contains(dataType.toUpperCase());
    }

    // =============================================================================
    // 缓存管理方法
    // =============================================================================

    /**
     * 清理特定表的搜索缓存
     */
    public void clearTableSearchCache(String dataSource, String tableName) {
        searchCacheService.clearTableCache(dataSource, tableName);
    }

    /**
     * 清理所有搜索缓存
     */
    public void clearAllSearchCache() {
        searchCacheService.clearAllCache();
    }

    /**
     * 获取搜索缓存统计信息
     */
    public Map<String, Object> getSearchCacheStats() {
        return searchCacheService.getCacheStats();
    }

    /**
     * 验证数据库和表是否存在
     */
    private void validateDatabaseAndTable(String dataSourceName, String tableName) {
        try {
            // 如果是用户创建的数据库，验证数据库是否存在
            if (isUserCreatedDatabase(dataSourceName)) {
                JdbcTemplate jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                String checkDbSql = "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
                Integer dbCount = jdbcTemplate.queryForObject(checkDbSql, Integer.class, dataSourceName);
                
                if (dbCount == null || dbCount == 0) {
                    throw new IllegalArgumentException("数据库 '" + dataSourceName + "' 不存在");
                }
                
                // 检查表是否存在
                String checkTableSql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                Integer tableCount = jdbcTemplate.queryForObject(checkTableSql, Integer.class, dataSourceName, tableName);
                
                if (tableCount == null || tableCount == 0) {
                    throw new IllegalArgumentException("表 '" + tableName + "' 在数据库 '" + dataSourceName + "' 中不存在");
                }
                
            } else {
                // 配置的数据源，检查表是否存在
                JdbcTemplate jdbcTemplate = getJdbcTemplate(dataSourceName);
                String checkTableSql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
                Integer tableCount = jdbcTemplate.queryForObject(checkTableSql, Integer.class, tableName);
                
                if (tableCount == null || tableCount == 0) {
                    throw new IllegalArgumentException("表 '" + tableName + "' 在数据源 '" + dataSourceName + "' 中不存在");
                }
            }
            
        } catch (Exception e) {
            logger.error("验证数据库和表失败: {}", e.getMessage());
            throw new RuntimeException("验证数据库和表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量插入表数据（支持百万级数据优化）
     */
    public Map<String, Object> batchInsertTableData(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("插入数据不能为空");
        }

        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        int successCount = 0;
        int failureCount = 0;
        List<String> errors = new ArrayList<>();
        
        // 批处理大小，避免内存溢出
        int batchSize = 5000;
        
        logger.info("开始批量插入 - 数据源: {}, 表名: {}, 记录数: {}", dataSourceName, tableName, totalRecords);
        
        try {
            // 验证数据库和表是否存在
            validateDatabaseAndTable(dataSourceName, tableName);
            
            JdbcTemplate jdbcTemplate;
            String sqlTemplate;
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(dataSourceName)) {
                // 用户创建的数据库，使用默认数据源连接
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sqlTemplate = "INSERT INTO `%s`.`%s` (%s) VALUES %s";
                logger.info("使用用户创建的数据库模式 - 数据源: {}, 表名: {}", dataSourceName, tableName);
            } else {
                // 配置的数据源，使用原来的逻辑
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sqlTemplate = "INSERT INTO `%s` (%s) VALUES %s";
                logger.info("使用配置的数据源模式 - 数据源: {}, 表名: {}", dataSourceName, tableName);
            }
            
            // 获取第一条记录的列名，用于构建SQL
            Map<String, Object> firstRecord = dataList.get(0);
            List<String> columnNames = new ArrayList<>(firstRecord.keySet());
            String columnsStr = columnNames.stream()
                    .map(col -> "`" + col + "`")
                    .collect(java.util.stream.Collectors.joining(", "));
            
            logger.info("列名映射: {}", columnNames);
            
            // 获取表的列信息用于数据类型检查
            Map<String, String> columnTypes = new HashMap<>();
            try {
                String getColumnTypesSql;
                if (isUserCreatedDatabase(dataSourceName)) {
                    getColumnTypesSql = "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                    List<Map<String, Object>> columnInfo = jdbcTemplate.queryForList(getColumnTypesSql, dataSourceName, tableName);
                    for (Map<String, Object> row : columnInfo) {
                        columnTypes.put((String) row.get("COLUMN_NAME"), (String) row.get("DATA_TYPE"));
                    }
                } else {
                    getColumnTypesSql = "SELECT COLUMN_NAME, DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
                    List<Map<String, Object>> columnInfo = jdbcTemplate.queryForList(getColumnTypesSql, tableName);
                    for (Map<String, Object> row : columnInfo) {
                        columnTypes.put((String) row.get("COLUMN_NAME"), (String) row.get("DATA_TYPE"));
                    }
                }
                logger.info("获取到的列类型信息: {}", columnTypes);
            } catch (Exception e) {
                logger.warn("获取列类型信息失败，将使用默认处理: {}", e.getMessage());
            }
            
            // 分批处理数据
            for (int i = 0; i < totalRecords; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalRecords);
                List<Map<String, Object>> batch = dataList.subList(i, endIndex);
                
                try {
                    // 构建批量插入SQL
                    StringBuilder valuesBuilder = new StringBuilder();
                    List<Object> params = new ArrayList<>();
                    
                    for (int j = 0; j < batch.size(); j++) {
                        if (j > 0) {
                            valuesBuilder.append(", ");
                        }
                        valuesBuilder.append("(");
                        
                        Map<String, Object> record = batch.get(j);
                        for (int k = 0; k < columnNames.size(); k++) {
                            if (k > 0) {
                                valuesBuilder.append(", ");
                            }
                            valuesBuilder.append("?");
                            
                            // 处理空字符串问题 - 对于数字类型列，将空字符串转换为null
                            String columnName = columnNames.get(k);
                            Object value = record.get(columnName);
                            
                            // 检查是否是空字符串并且是数字类型
                            if (value != null && "".equals(value.toString().trim())) {
                                String columnType = columnTypes.get(columnName);
                                if (columnType != null && isNumericType(columnType)) {
                                    // 数字类型的空字符串转换为null
                                    value = null;
                                    logger.debug("将列 {} ({}) 的空字符串转换为null", columnName, columnType);
                                }
                            }
                            
                            params.add(value);
                        }
                        valuesBuilder.append(")");
                    }
                    
                    String sql;
                    if (isUserCreatedDatabase(dataSourceName)) {
                        sql = String.format(sqlTemplate, dataSourceName, tableName, columnsStr, valuesBuilder.toString());
                    } else {
                        sql = String.format(sqlTemplate, tableName, columnsStr, valuesBuilder.toString());
                    }
                    
                    // 记录即将执行的SQL（仅前200个字符用于调试）
                    logger.info("执行批量插入SQL（前200字符）: {}", sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
                    
                    // 记录参数类型和前几个参数的值
                    if (i == 0) {
                        logger.info("参数总数: {}", params.size());
                        logger.info("列名数量: {}", columnNames.size());
                        logger.info("批次大小: {}", batch.size());
                        
                        // 记录第一行数据的参数
                        if (!params.isEmpty()) {
                            logger.info("第一行数据参数类型和值:");
                            for (int p = 0; p < Math.min(columnNames.size(), params.size()); p++) {
                                Object paramValue = params.get(p);
                                String paramType = paramValue != null ? paramValue.getClass().getSimpleName() : "null";
                                logger.info("  {}: {} ({})", columnNames.get(p), paramValue, paramType);
                            }
                        }
                    }
                    
                    // 执行批量插入
                    int affectedRows = jdbcTemplate.update(sql, params.toArray());
                    successCount += affectedRows;
                    
                    logger.info("批量插入进度: {}/{} 条记录已处理，本批次成功: {}", i + batch.size(), totalRecords, affectedRows);
                    
                } catch (Exception e) {
                    failureCount += batch.size();
                    
                    // 详细的错误信息记录
                    String errorDetail = "";
                    if (e instanceof org.springframework.dao.DataAccessException) {
                        Throwable rootCause = e;
                        while (rootCause.getCause() != null) {
                            rootCause = rootCause.getCause();
                        }
                        errorDetail = rootCause.getMessage();
                    } else {
                        errorDetail = e.getMessage();
                    }
                    
                    String error = String.format("批次 %d-%d 插入失败: %s", i + 1, endIndex, errorDetail);
                    errors.add(error);
                    logger.error(error, e);
                    
                    // 记录第一批失败的详细信息
                    if (i == 0) {
                        logger.error("第一批数据示例: {}", batch.get(0));
                        logger.error("失败的SQL模板: {}", sqlTemplate);
                        logger.error("参数类型: dataSourceName={}, tableName={}", dataSourceName, tableName);
                        logger.error("isUserCreatedDatabase({}) = {}", dataSourceName, isUserCreatedDatabase(dataSourceName));
                        logger.error("错误的根本原因: {}", errorDetail);
                        
                        // 如果是SQL异常，尝试提供更多信息
                        if (e instanceof org.springframework.dao.DataAccessException) {
                            logger.error("数据访问异常详情:", e);
                            
                            // 检查是否是表不存在的问题
                            if (errorDetail != null && errorDetail.toLowerCase().contains("doesn't exist")) {
                                logger.error("表可能不存在，请检查数据库 '{}' 中是否存在表 '{}'", dataSourceName, tableName);
                            }
                            
                            // 检查是否是字段不匹配的问题
                            if (errorDetail != null && errorDetail.toLowerCase().contains("unknown column")) {
                                logger.error("字段不匹配，请检查表 '{}' 的字段定义", tableName);
                                logger.error("CSV文件列名: {}", columnNames);
                            }
                            
                            // 检查是否是数据类型错误
                            if (errorDetail != null && (errorDetail.toLowerCase().contains("data too long") || 
                                                       errorDetail.toLowerCase().contains("incorrect") ||
                                                       errorDetail.toLowerCase().contains("invalid"))) {
                                logger.error("数据类型或长度错误，请检查数据格式");
                            }
                            
                            // 检查是否是整数范围错误
                            if (errorDetail != null && (errorDetail.toLowerCase().contains("out of range value") ||
                                                       errorDetail.toLowerCase().contains("incorrect integer value"))) {
                                logger.error("整数范围错误：某些整数字段的值超出了定义的数据类型范围。建议使用BIGINT类型或检查数据。");
                            }
                            
                            // 检查是否是约束冲突
                            if (errorDetail != null && (errorDetail.toLowerCase().contains("duplicate") || 
                                                       errorDetail.toLowerCase().contains("constraint"))) {
                                logger.error("约束冲突，可能是主键重复或其他约束问题");
                            }
                        }
                    }
                }
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalRecords", totalRecords);
            result.put("successCount", successCount);
            result.put("failureCount", failureCount);
            result.put("duration", duration);
            result.put("errors", errors);
            result.put("tableName", tableName);
            result.put("dataSource", dataSourceName);
            
            // 清除该表的搜索缓存，确保搜索结果反映最新数据
            clearTableSearchCache(dataSourceName, tableName);
            
            logger.info("批量插入完成 - 表: {}, 总记录数: {}, 成功: {}, 失败: {}, 耗时: {}ms", 
                    tableName, totalRecords, successCount, failureCount, duration);
            
            return result;
            
        } catch (Exception e) {
            logger.error("批量插入失败 - 表: {}, 错误: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("批量插入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 事务性批量插入表数据（确保数据一致性）
     */
    @Transactional
    public Map<String, Object> batchInsertTableDataTransaction(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("插入数据不能为空");
        }

        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        
        try {
            JdbcTemplate jdbcTemplate;
            String sqlTemplate;
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(dataSourceName)) {
                // 用户创建的数据库，使用默认数据源连接
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sqlTemplate = "INSERT INTO `%s`.`%s` (%s) VALUES (%s)";
            } else {
                // 配置的数据源，使用原来的逻辑
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sqlTemplate = "INSERT INTO `%s` (%s) VALUES (%s)";
            }
            
            // 获取第一条记录的列名，用于构建SQL
            Map<String, Object> firstRecord = dataList.get(0);
            List<String> columnNames = new ArrayList<>(firstRecord.keySet());
            String columnsStr = columnNames.stream()
                    .map(col -> "`" + col + "`")
                    .collect(java.util.stream.Collectors.joining(", "));
            
            String placeholders = columnNames.stream()
                    .map(col -> "?")
                    .collect(java.util.stream.Collectors.joining(", "));
            
            String sql;
            if (isUserCreatedDatabase(dataSourceName)) {
                sql = String.format(sqlTemplate, dataSourceName, tableName, columnsStr, placeholders);
            } else {
                sql = String.format(sqlTemplate, tableName, columnsStr, placeholders);
            }
            
            // 获取表列信息，用于数据类型处理
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            Map<String, String> columnTypeMap = new HashMap<>();
            for (Map<String, Object> column : columns) {
                String columnName = (String) column.get("COLUMN_NAME");
                String dataType = (String) column.get("DATA_TYPE");
                columnTypeMap.put(columnName, dataType);
            }
            
            // 准备批量参数
            List<Object[]> batchArgs = new ArrayList<>();
            for (Map<String, Object> record : dataList) {
                Object[] args = new Object[columnNames.size()];
                for (int i = 0; i < columnNames.size(); i++) {
                    String columnName = columnNames.get(i);
                    Object value = record.get(columnName);
                    
                    // 处理空字符串值
                    if (value != null && value.toString().trim().isEmpty()) {
                        String dataType = columnTypeMap.get(columnName);
                        if (dataType != null && isNumericType(dataType)) {
                            // 数字类型的空字符串转换为null
                            args[i] = null;
                        } else {
                            // 非数字类型保持原值
                            args[i] = value;
                        }
                    } else {
                        args[i] = value;
                    }
                }
                batchArgs.add(args);
            }
            
            // 执行批量插入
            int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
            int successCount = java.util.Arrays.stream(updateCounts).sum();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalRecords", totalRecords);
            result.put("successCount", successCount);
            result.put("failureCount", totalRecords - successCount);
            result.put("duration", duration);
            result.put("errors", new ArrayList<>());
            result.put("tableName", tableName);
            result.put("dataSource", dataSourceName);
            
            // 清除该表的搜索缓存，确保搜索结果反映最新数据
            clearTableSearchCache(dataSourceName, tableName);
            
            logger.info("事务性批量插入完成 - 表: {}, 总记录数: {}, 成功: {}, 耗时: {}ms", 
                    tableName, totalRecords, successCount, duration);
            
            return result;
            
        } catch (Exception e) {
            logger.error("事务性批量插入失败 - 表: {}, 错误: {}", tableName, e.getMessage(), e);
            throw new RuntimeException("事务性批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 带策略的批量插入表数据（支持追加和覆盖模式）
     */
    public Map<String, Object> batchInsertTableDataWithStrategy(String dataSourceName, String tableName, List<Map<String, Object>> dataList, String importStrategy) {
        logger.info("开始带策略的批量插入 - 数据源: {}, 表名: {}, 记录数: {}, 策略: {}", dataSourceName, tableName, dataList.size(), importStrategy);
        
        if ("overwrite".equals(importStrategy)) {
            return batchInsertTableDataWithOverwrite(dataSourceName, tableName, dataList);
        } else {
            // 默认为追加模式
            return batchInsertTableDataWithAppend(dataSourceName, tableName, dataList);
        }
    }
    
    /**
     * 带策略的事务性批量插入表数据（支持追加和覆盖模式）
     */
    @Transactional
    public Map<String, Object> batchInsertTableDataTransactionWithStrategy(String dataSourceName, String tableName, List<Map<String, Object>> dataList, String importStrategy) {
        logger.info("开始带策略的事务性批量插入 - 数据源: {}, 表名: {}, 记录数: {}, 策略: {}", dataSourceName, tableName, dataList.size(), importStrategy);
        
        if ("overwrite".equals(importStrategy)) {
            return batchInsertTableDataTransactionWithOverwrite(dataSourceName, tableName, dataList);
        } else {
            // 默认为追加模式
            return batchInsertTableDataTransactionWithAppend(dataSourceName, tableName, dataList);
        }
    }
    
    /**
     * 覆盖模式的批量插入（清空表后重新导入）
     */
    private Map<String, Object> batchInsertTableDataWithOverwrite(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        long startTime = System.currentTimeMillis();
        
        try {
            JdbcTemplate jdbcTemplate;
            String deleteTemplate;
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                deleteTemplate = "DELETE FROM `%s`.`%s`";
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                deleteTemplate = "DELETE FROM `%s`";
            }
            
            // 清空表数据
            String deleteSql;
            if (isUserCreatedDatabase(dataSourceName)) {
                deleteSql = String.format(deleteTemplate, dataSourceName, tableName);
            } else {
                deleteSql = String.format(deleteTemplate, tableName);
            }
            
            logger.info("覆盖模式：清空表数据 - SQL: {}", deleteSql);
            int deletedRows = jdbcTemplate.update(deleteSql);
            logger.info("覆盖模式：已删除 {} 行数据", deletedRows);
            
            // 然后调用正常的批量插入
            Map<String, Object> insertResult = batchInsertTableData(dataSourceName, tableName, dataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("deletedRows", deletedRows);
            insertResult.put("importStrategy", "overwrite");
            
            logger.info("覆盖模式批量插入完成 - 删除行数: {}, 插入结果: {}", deletedRows, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("覆盖模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("覆盖模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 事务性覆盖模式的批量插入
     */
    @Transactional
    private Map<String, Object> batchInsertTableDataTransactionWithOverwrite(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        long startTime = System.currentTimeMillis();
        
        try {
            JdbcTemplate jdbcTemplate;
            String deleteTemplate;
            
            // 检查是否为用户创建的数据库
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                deleteTemplate = "DELETE FROM `%s`.`%s`";
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                deleteTemplate = "DELETE FROM `%s`";
            }
            
            // 清空表数据
            String deleteSql;
            if (isUserCreatedDatabase(dataSourceName)) {
                deleteSql = String.format(deleteTemplate, dataSourceName, tableName);
            } else {
                deleteSql = String.format(deleteTemplate, tableName);
            }
            
            logger.info("事务性覆盖模式：清空表数据 - SQL: {}", deleteSql);
            int deletedRows = jdbcTemplate.update(deleteSql);
            logger.info("事务性覆盖模式：已删除 {} 行数据", deletedRows);
            
            // 然后调用事务性批量插入
            Map<String, Object> insertResult = batchInsertTableDataTransaction(dataSourceName, tableName, dataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("deletedRows", deletedRows);
            insertResult.put("importStrategy", "overwrite");
            
            logger.info("事务性覆盖模式批量插入完成 - 删除行数: {}, 插入结果: {}", deletedRows, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("事务性覆盖模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("事务性覆盖模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 追加模式的批量插入（检测重复数据，只导入不同的数据）
     */
    private Map<String, Object> batchInsertTableDataWithAppend(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        
        try {
            logger.info("追加模式：开始检测重复数据 - 数据源: {}, 表名: {}, 记录数: {}", dataSourceName, tableName, totalRecords);
            
            // 获取表的主键列信息
            List<String> primaryKeys = getTablePrimaryKeys(dataSourceName, tableName);
            
            List<Map<String, Object>> uniqueDataList = new ArrayList<>();
            
            if (primaryKeys.isEmpty()) {
                // 没有主键，使用所有列进行重复检测
                logger.info("追加模式：表没有主键，使用全行比较检测重复");
                uniqueDataList = filterDuplicatesByAllColumns(dataSourceName, tableName, dataList);
                skippedCount = totalRecords - uniqueDataList.size();
            } else {
                // 有主键，使用主键进行重复检测
                logger.info("追加模式：使用主键进行重复检测 - 主键列: {}", primaryKeys);
                uniqueDataList = filterDuplicatesByPrimaryKeys(dataSourceName, tableName, dataList, primaryKeys);
                skippedCount = totalRecords - uniqueDataList.size();
            }
            
            logger.info("追加模式：重复检测完成 - 原始记录: {}, 去重后: {}, 跳过: {}", totalRecords, uniqueDataList.size(), skippedCount);
            
            if (uniqueDataList.isEmpty()) {
                // 所有数据都是重复的
                Map<String, Object> result = new HashMap<>();
                result.put("totalRecords", totalRecords);
                result.put("successCount", 0);
                result.put("failureCount", 0);
                result.put("skippedCount", skippedCount);
                result.put("duration", System.currentTimeMillis() - startTime);
                result.put("errors", errors);
                result.put("importStrategy", "append");
                result.put("message", "所有数据都已存在，无需导入");
                
                logger.info("追加模式：所有数据都是重复的，跳过导入");
                return result;
            }
            
            // 调用正常的批量插入处理去重后的数据
            Map<String, Object> insertResult = batchInsertTableData(dataSourceName, tableName, uniqueDataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("skippedCount", skippedCount);
            insertResult.put("importStrategy", "append");
            
            logger.info("追加模式批量插入完成 - 跳过重复: {}, 插入结果: {}", skippedCount, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("追加模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("追加模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 事务性追加模式的批量插入
     */
    @Transactional
    private Map<String, Object> batchInsertTableDataTransactionWithAppend(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        int skippedCount = 0;
        
        try {
            logger.info("事务性追加模式：开始检测重复数据 - 数据源: {}, 表名: {}, 记录数: {}", dataSourceName, tableName, totalRecords);
            
            // 获取表的主键列信息
            List<String> primaryKeys = getTablePrimaryKeys(dataSourceName, tableName);
            
            List<Map<String, Object>> uniqueDataList = new ArrayList<>();
            
            if (primaryKeys.isEmpty()) {
                // 没有主键，使用所有列进行重复检测
                logger.info("事务性追加模式：表没有主键，使用全行比较检测重复");
                uniqueDataList = filterDuplicatesByAllColumns(dataSourceName, tableName, dataList);
                skippedCount = totalRecords - uniqueDataList.size();
            } else {
                // 有主键，使用主键进行重复检测
                logger.info("事务性追加模式：使用主键进行重复检测 - 主键列: {}", primaryKeys);
                uniqueDataList = filterDuplicatesByPrimaryKeys(dataSourceName, tableName, dataList, primaryKeys);
                skippedCount = totalRecords - uniqueDataList.size();
            }
            
            logger.info("事务性追加模式：重复检测完成 - 原始记录: {}, 去重后: {}, 跳过: {}", totalRecords, uniqueDataList.size(), skippedCount);
            
            if (uniqueDataList.isEmpty()) {
                // 所有数据都是重复的
                Map<String, Object> result = new HashMap<>();
                result.put("totalRecords", totalRecords);
                result.put("successCount", 0);
                result.put("failureCount", 0);
                result.put("skippedCount", skippedCount);
                result.put("duration", System.currentTimeMillis() - startTime);
                result.put("errors", new ArrayList<>());
                result.put("importStrategy", "append");
                result.put("message", "所有数据都已存在，无需导入");
                
                logger.info("事务性追加模式：所有数据都是重复的，跳过导入");
                return result;
            }
            
            // 调用事务性批量插入处理去重后的数据
            Map<String, Object> insertResult = batchInsertTableDataTransaction(dataSourceName, tableName, uniqueDataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("skippedCount", skippedCount);
            insertResult.put("importStrategy", "append");
            
            logger.info("事务性追加模式批量插入完成 - 跳过重复: {}, 插入结果: {}", skippedCount, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("事务性追加模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("事务性追加模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取表的主键列
     */
    private List<String> getTablePrimaryKeys(String dataSourceName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                      "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY' " +
                      "ORDER BY ORDINAL_POSITION";
                return jdbcTemplate.queryForList(sql, String.class, dataSourceName, tableName);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                      "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = 'PRIMARY' " +
                      "ORDER BY ORDINAL_POSITION";
                return jdbcTemplate.queryForList(sql, String.class, tableName);
            }
        } catch (Exception e) {
            logger.warn("获取主键信息失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取用户选择的主键列（用于自动建表的重复检测）
     */
    private List<String> getUserSelectedPrimaryKeys(List<Map<String, Object>> csvColumns) {
        List<String> primaryKeys = new ArrayList<>();
        
        for (Map<String, Object> column : csvColumns) {
            Boolean isPrimaryKey = (Boolean) column.get("isPrimaryKey");
            if (isPrimaryKey != null && isPrimaryKey) {
                String columnName = sanitizeColumnName((String) column.get("columnName"));
                primaryKeys.add(columnName);
            }
        }
        
        return primaryKeys;
    }
    
    /**
     * 基于主键过滤重复数据
     */
    private List<Map<String, Object>> filterDuplicatesByPrimaryKeys(String dataSourceName, String tableName, 
            List<Map<String, Object>> dataList, List<String> primaryKeys) {
        
        if (primaryKeys.isEmpty() || dataList.isEmpty()) {
            return dataList;
        }
        
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            // 构建查询条件
            String whereClause = primaryKeys.stream()
                    .map(pk -> "`" + pk + "` = ?")
                    .collect(java.util.stream.Collectors.joining(" AND "));
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE %s", dataSourceName, tableName, whereClause);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = String.format("SELECT COUNT(*) FROM `%s` WHERE %s", tableName, whereClause);
            }
            
            List<Map<String, Object>> uniqueList = new ArrayList<>();
            
            for (Map<String, Object> record : dataList) {
                // 准备主键值
                Object[] primaryKeyValues = new Object[primaryKeys.size()];
                boolean hasAllPrimaryKeys = true;
                
                for (int i = 0; i < primaryKeys.size(); i++) {
                    Object value = record.get(primaryKeys.get(i));
                    if (value == null) {
                        hasAllPrimaryKeys = false;
                        break;
                    }
                    primaryKeyValues[i] = value;
                }
                
                if (!hasAllPrimaryKeys) {
                    // 主键值不完整，跳过该记录
                    logger.warn("记录的主键值不完整，跳过: {}", record);
                    continue;
                }
                
                // 检查是否已存在
                try {
                    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, primaryKeyValues);
                    if (count == null || count == 0) {
                        uniqueList.add(record);
                    }
                } catch (Exception e) {
                    logger.warn("检查重复数据时出错，将该记录添加到导入列表: {}", e.getMessage());
                    uniqueList.add(record);
                }
            }
            
            return uniqueList;
            
        } catch (Exception e) {
            logger.error("基于主键过滤重复数据失败: {}", e.getMessage());
            return dataList; // 出错时返回原始数据
        }
    }
    
    /**
     * 基于所有列过滤重复数据（当没有主键时）
     */
    private List<Map<String, Object>> filterDuplicatesByAllColumns(String dataSourceName, String tableName, 
            List<Map<String, Object>> dataList) {
        
        if (dataList.isEmpty()) {
            return dataList;
        }
        
        try {
            // 获取表的所有列
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            List<String> columnNames = columns.stream()
                    .map(col -> (String) col.get("COLUMN_NAME"))
                    .collect(java.util.stream.Collectors.toList());
            
            if (columnNames.isEmpty()) {
                return dataList;
            }
            
            JdbcTemplate jdbcTemplate;
            String sql;
            
            // 构建查询条件 - 使用 IS NULL 处理 null 值
            String whereClause = columnNames.stream()
                    .map(col -> String.format("((`%s` = ?) OR (`%s` IS NULL AND ? IS NULL))", col, col))
                    .collect(java.util.stream.Collectors.joining(" AND "));
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = String.format("SELECT COUNT(*) FROM `%s`.`%s` WHERE %s", dataSourceName, tableName, whereClause);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = String.format("SELECT COUNT(*) FROM `%s` WHERE %s", tableName, whereClause);
            }
            
            List<Map<String, Object>> uniqueList = new ArrayList<>();
            
            for (Map<String, Object> record : dataList) {
                // 准备所有列的值（每个值需要重复两次用于 null 检查）
                Object[] allColumnValues = new Object[columnNames.size() * 2];
                for (int i = 0; i < columnNames.size(); i++) {
                    Object value = record.get(columnNames.get(i));
                    allColumnValues[i * 2] = value;
                    allColumnValues[i * 2 + 1] = value;
                }
                
                // 检查是否已存在
                try {
                    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, allColumnValues);
                    if (count == null || count == 0) {
                        uniqueList.add(record);
                    }
                } catch (Exception e) {
                    logger.warn("检查重复数据时出错，将该记录添加到导入列表: {}", e.getMessage());
                    uniqueList.add(record);
                }
            }
            
            return uniqueList;
            
        } catch (Exception e) {
            logger.error("基于所有列过滤重复数据失败: {}", e.getMessage());
            return dataList; // 出错时返回原始数据
        }
    }

    /**
     * 自动建表并导入数据（非事务性）
     */
    public Map<String, Object> autoCreateTableAndImportData(String dataSourceName, String tableName, List<Map<String, Object>> csvData, List<Map<String, Object>> csvColumns, String importStrategy) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("开始自动建表并导入 - 数据源: {}, 表名: {}, 记录数: {}, 策略: {}", dataSourceName, tableName, csvData.size(), importStrategy);
            
            // 1. 检查表是否已存在
            boolean tableExists = checkTableExists(dataSourceName, tableName);
            
            if (!tableExists) {
                // 2. 表不存在，创建表
                createTableFromCsvColumns(dataSourceName, tableName, csvColumns, csvData);
                logger.info("成功创建表: {}", tableName);
            } else {
                logger.info("表已存在: {}，将根据策略进行导入", tableName);
            }
            
            // 3. 导入数据
            Map<String, Object> importResult;
            if ("overwrite".equals(importStrategy)) {
                importResult = batchInsertTableDataWithOverwrite(dataSourceName, tableName, csvData);
            } else {
                // 对于自动建表的追加模式，需要传递CSV列信息以获取用户选择的主键
                importResult = batchInsertTableDataWithAppendForAutoCreate(dataSourceName, tableName, csvData, csvColumns);
            }
            
            // 4. 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            importResult.put("duration", duration);
            importResult.put("tableCreated", !tableExists);
            importResult.put("autoCreateTable", true);
            
            logger.info("自动建表并导入完成 - 表: {}, 是否新建: {}, 导入结果: {}", tableName, !tableExists, importResult);
            
            return importResult;
            
        } catch (Exception e) {
            logger.error("自动建表并导入失败: {}", e.getMessage(), e);
            throw new RuntimeException("自动建表并导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 自动建表并导入数据（事务性）
     */
    @Transactional
    public Map<String, Object> autoCreateTableAndImportDataTransaction(String dataSourceName, String tableName, List<Map<String, Object>> csvData, List<Map<String, Object>> csvColumns, String importStrategy) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("开始事务性自动建表并导入 - 数据源: {}, 表名: {}, 记录数: {}, 策略: {}", dataSourceName, tableName, csvData.size(), importStrategy);
            
            // 1. 检查表是否已存在
            boolean tableExists = checkTableExists(dataSourceName, tableName);
            
            if (!tableExists) {
                // 2. 表不存在，创建表
                createTableFromCsvColumns(dataSourceName, tableName, csvColumns, csvData);
                logger.info("成功创建表: {}", tableName);
            } else {
                logger.info("表已存在: {}，将根据策略进行导入", tableName);
            }
            
            // 3. 导入数据
            Map<String, Object> importResult;
            if ("overwrite".equals(importStrategy)) {
                importResult = batchInsertTableDataTransactionWithOverwrite(dataSourceName, tableName, csvData);
            } else {
                // 对于自动建表的追加模式，需要传递CSV列信息以获取用户选择的主键
                importResult = batchInsertTableDataTransactionWithAppendForAutoCreate(dataSourceName, tableName, csvData, csvColumns);
            }
            
            // 4. 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            importResult.put("duration", duration);
            importResult.put("tableCreated", !tableExists);
            importResult.put("autoCreateTable", true);
            
            logger.info("事务性自动建表并导入完成 - 表: {}, 是否新建: {}, 导入结果: {}", tableName, !tableExists, importResult);
            
            return importResult;
            
        } catch (Exception e) {
            logger.error("事务性自动建表并导入失败: {}", e.getMessage(), e);
            throw new RuntimeException("事务性自动建表并导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查表是否存在（公共方法，用于API调用）
     */
    public boolean checkTableExists(String dataSourceName, String databaseName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName, tableName);
                return count != null && count > 0;
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            logger.warn("检查表是否存在时出错: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查表是否存在（私有方法，用于内部调用）
     */
    private boolean checkTableExists(String dataSourceName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate;
            String sql;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, dataSourceName, tableName);
                return count != null && count > 0;
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
                sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
                return count != null && count > 0;
            }
        } catch (Exception e) {
            logger.warn("检查表是否存在时出错: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 根据CSV列信息创建表
     */
    private void createTableFromCsvColumns(String dataSourceName, String tableName, List<Map<String, Object>> csvColumns, List<Map<String, Object>> csvData) {
        try {
            JdbcTemplate jdbcTemplate;
            
            if (isUserCreatedDatabase(dataSourceName)) {
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            // 构建创建表的SQL
            StringBuilder createTableSql = new StringBuilder();
            if (isUserCreatedDatabase(dataSourceName)) {
                createTableSql.append("CREATE TABLE `").append(dataSourceName).append("`.`").append(tableName).append("` (");
            } else {
                createTableSql.append("CREATE TABLE `").append(tableName).append("` (");
            }
            
            // 添加CSV列
            List<String> primaryKeyColumns = new ArrayList<>();
            
            // 从CSV列信息中获取用户选择的主键
            logger.info("开始解析CSV列信息，列数量: {}", csvColumns.size());
            for (Map<String, Object> column : csvColumns) {
                String columnName = (String) column.get("columnName");
                Boolean isPrimaryKey = (Boolean) column.get("isPrimaryKey");
                
                logger.info("检查列: {}, isPrimaryKey: {}", columnName, isPrimaryKey);
                
                if (isPrimaryKey != null && isPrimaryKey) {
                    String sanitizedColumnName = sanitizeColumnName(columnName);
                    primaryKeyColumns.add(sanitizedColumnName);
                    logger.info("添加主键列: {} (原始: {})", sanitizedColumnName, columnName);
                }
            }
            
            logger.info("用户选择的主键列: {}", primaryKeyColumns);
            
            for (int i = 0; i < csvColumns.size(); i++) {
                Map<String, Object> column = csvColumns.get(i);
                String columnName = (String) column.get("columnName");
                String dataType = (String) column.get("inferredType");
                
                // 清理列名，确保合法
                columnName = sanitizeColumnName(columnName);
                
                // 优化数据类型，确保整数类型有足够的长度
                String optimizedDataType = optimizeDataType(dataType, csvData, columnName);
                
                // 普通列，移除AUTO_INCREMENT关键字
                String cleanDataType = optimizedDataType.replace("AUTO_INCREMENT", "").trim();
                createTableSql.append("`").append(columnName).append("` ").append(cleanDataType);
                
                if (i < csvColumns.size() - 1) {
                    createTableSql.append(", ");
                }
            }
            
            // 添加复合主键约束
            if (!primaryKeyColumns.isEmpty()) {
                logger.info("添加复合主键约束，主键列: {}", primaryKeyColumns);
                createTableSql.append(", PRIMARY KEY (");
                for (int i = 0; i < primaryKeyColumns.size(); i++) {
                    createTableSql.append("`").append(primaryKeyColumns.get(i)).append("`");
                    if (i < primaryKeyColumns.size() - 1) {
                        createTableSql.append(", ");
                    }
                }
                createTableSql.append(")");
            } else {
                logger.info("没有选择主键列，不添加主键约束");
            }
            
            createTableSql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            
            logger.info("创建表SQL: {}", createTableSql.toString());
            
            // 执行创建表SQL
            jdbcTemplate.execute(createTableSql.toString());
            
            logger.info("成功创建表: {} 包含 {} 个字段", tableName, csvColumns.size());
            
        } catch (Exception e) {
            logger.error("创建表失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理列名，确保符合MySQL命名规范
     */
    private String sanitizeColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            return "column_" + System.currentTimeMillis();
        }
        
        // 移除非法字符，只保留字母、数字和下划线
        String sanitized = columnName.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // 确保以字母或下划线开头
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "col_" + sanitized;
        }
        
        // 限制长度（MySQL列名最大64字符）
        if (sanitized.length() > 60) {
            sanitized = sanitized.substring(0, 60);
        }
        
        // 避免MySQL保留字冲突
        if (isReservedWord(sanitized.toUpperCase())) {
            sanitized = "col_" + sanitized;
        }
        
        return sanitized;
    }
    
    /**
     * 自动建表模式的追加导入（基于用户选择的主键进行重复检测）
     */
    private Map<String, Object> batchInsertTableDataWithAppendForAutoCreate(String dataSourceName, String tableName, 
            List<Map<String, Object>> dataList, List<Map<String, Object>> csvColumns) {
        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        
        try {
            logger.info("自动建表追加模式：开始检测重复数据 - 数据源: {}, 表名: {}, 记录数: {}", dataSourceName, tableName, totalRecords);
            
            // 获取用户选择的主键列
            List<String> userSelectedPrimaryKeys = getUserSelectedPrimaryKeys(csvColumns);
            
            List<Map<String, Object>> uniqueDataList = new ArrayList<>();
            
            if (userSelectedPrimaryKeys.isEmpty()) {
                // 没有选择主键，使用所有列进行重复检测
                logger.info("自动建表追加模式：用户未选择主键，使用全行比较检测重复");
                uniqueDataList = filterDuplicatesByAllColumns(dataSourceName, tableName, dataList);
                skippedCount = totalRecords - uniqueDataList.size();
            } else {
                // 有用户选择的主键，使用主键进行重复检测
                logger.info("自动建表追加模式：使用用户选择的主键进行重复检测 - 主键列: {}", userSelectedPrimaryKeys);
                uniqueDataList = filterDuplicatesByPrimaryKeys(dataSourceName, tableName, dataList, userSelectedPrimaryKeys);
                skippedCount = totalRecords - uniqueDataList.size();
            }
            
            logger.info("自动建表追加模式：重复检测完成 - 原始记录: {}, 去重后: {}, 跳过: {}", totalRecords, uniqueDataList.size(), skippedCount);
            
            if (uniqueDataList.isEmpty()) {
                // 所有数据都是重复的
                Map<String, Object> result = new HashMap<>();
                result.put("totalRecords", totalRecords);
                result.put("successCount", 0);
                result.put("failureCount", 0);
                result.put("skippedCount", skippedCount);
                result.put("duration", System.currentTimeMillis() - startTime);
                result.put("errors", errors);
                result.put("importStrategy", "append");
                result.put("message", "所有数据都已存在，无需导入");
                
                logger.info("自动建表追加模式：所有数据都是重复的，跳过导入");
                return result;
            }
            
            // 调用正常的批量插入处理去重后的数据
            Map<String, Object> insertResult = batchInsertTableData(dataSourceName, tableName, uniqueDataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("skippedCount", skippedCount);
            insertResult.put("importStrategy", "append");
            
            logger.info("自动建表追加模式批量插入完成 - 跳过重复: {}, 插入结果: {}", skippedCount, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("自动建表追加模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("自动建表追加模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 自动建表模式的事务性追加导入
     */
    @Transactional
    private Map<String, Object> batchInsertTableDataTransactionWithAppendForAutoCreate(String dataSourceName, String tableName, 
            List<Map<String, Object>> dataList, List<Map<String, Object>> csvColumns) {
        long startTime = System.currentTimeMillis();
        int totalRecords = dataList.size();
        int skippedCount = 0;
        
        try {
            logger.info("事务性自动建表追加模式：开始检测重复数据 - 数据源: {}, 表名: {}, 记录数: {}", dataSourceName, tableName, totalRecords);
            
            // 获取用户选择的主键列
            List<String> userSelectedPrimaryKeys = getUserSelectedPrimaryKeys(csvColumns);
            
            List<Map<String, Object>> uniqueDataList = new ArrayList<>();
            
            if (userSelectedPrimaryKeys.isEmpty()) {
                // 没有选择主键，使用所有列进行重复检测
                logger.info("事务性自动建表追加模式：用户未选择主键，使用全行比较检测重复");
                uniqueDataList = filterDuplicatesByAllColumns(dataSourceName, tableName, dataList);
                skippedCount = totalRecords - uniqueDataList.size();
            } else {
                // 有用户选择的主键，使用主键进行重复检测
                logger.info("事务性自动建表追加模式：使用用户选择的主键进行重复检测 - 主键列: {}", userSelectedPrimaryKeys);
                uniqueDataList = filterDuplicatesByPrimaryKeys(dataSourceName, tableName, dataList, userSelectedPrimaryKeys);
                skippedCount = totalRecords - uniqueDataList.size();
            }
            
            logger.info("事务性自动建表追加模式：重复检测完成 - 原始记录: {}, 去重后: {}, 跳过: {}", totalRecords, uniqueDataList.size(), skippedCount);
            
            if (uniqueDataList.isEmpty()) {
                // 所有数据都是重复的
                Map<String, Object> result = new HashMap<>();
                result.put("totalRecords", totalRecords);
                result.put("successCount", 0);
                result.put("failureCount", 0);
                result.put("skippedCount", skippedCount);
                result.put("duration", System.currentTimeMillis() - startTime);
                result.put("errors", new ArrayList<>());
                result.put("importStrategy", "append");
                result.put("message", "所有数据都已存在，无需导入");
                
                logger.info("事务性自动建表追加模式：所有数据都是重复的，跳过导入");
                return result;
            }
            
            // 调用事务性批量插入处理去重后的数据
            Map<String, Object> insertResult = batchInsertTableDataTransaction(dataSourceName, tableName, uniqueDataList);
            
            // 更新结果信息
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            insertResult.put("duration", duration);
            insertResult.put("skippedCount", skippedCount);
            insertResult.put("importStrategy", "append");
            
            logger.info("事务性自动建表追加模式批量插入完成 - 跳过重复: {}, 插入结果: {}", skippedCount, insertResult);
            
            return insertResult;
            
        } catch (Exception e) {
            logger.error("事务性自动建表追加模式批量插入失败: {}", e.getMessage(), e);
            throw new RuntimeException("事务性自动建表追加模式批量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查是否为MySQL保留字
     */
    private boolean isReservedWord(String word) {
        String[] reservedWords = {
            "SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "ORDER", "GROUP", 
            "BY", "HAVING", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "AS", 
            "AND", "OR", "NOT", "IN", "LIKE", "BETWEEN", "IS", "NULL", "TRUE", "FALSE",
            "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "CONSTRAINT", "UNIQUE", "DEFAULT", "AUTO_INCREMENT"
        };
        
        for (String reserved : reservedWords) {
            if (reserved.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证CSV数据格式
     */
    public Map<String, Object> validateCsvData(String dataSourceName, String tableName, List<Map<String, Object>> dataList) {
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            if (dataList == null || dataList.isEmpty()) {
                errors.add("数据列表不能为空");
                result.put("valid", false);
                result.put("errors", errors);
                return result;
            }
            
            // 获取表的列信息
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            Map<String, Map<String, Object>> columnMap = new HashMap<>();
            Set<String> requiredColumns = new HashSet<>();
            
            for (Map<String, Object> column : columns) {
                String columnName = (String) column.get("COLUMN_NAME");
                columnMap.put(columnName, column);
                
                // 检查是否为必填字段
                String isNullable = (String) column.get("IS_NULLABLE");
                String columnDefault = (String) column.get("COLUMN_DEFAULT");
                String extra = (String) column.get("EXTRA");
                
                if ("NO".equals(isNullable) && columnDefault == null && 
                    (extra == null || !extra.contains("auto_increment"))) {
                    requiredColumns.add(columnName);
                }
            }
            
            // 检查数据完整性
            Map<String, Object> firstRecord = dataList.get(0);
            Set<String> dataColumns = firstRecord.keySet();
            
            // 检查缺失的必填字段
            for (String requiredColumn : requiredColumns) {
                if (!dataColumns.contains(requiredColumn)) {
                    errors.add("缺少必填字段: " + requiredColumn);
                }
            }
            
            // 检查多余的字段
            for (String dataColumn : dataColumns) {
                if (!columnMap.containsKey(dataColumn)) {
                    warnings.add("未知字段: " + dataColumn + "，该字段将被忽略");
                }
            }
            
            // 数据类型检查（抽样检查前100条记录）
            int sampleSize = Math.min(100, dataList.size());
            for (int i = 0; i < sampleSize; i++) {
                Map<String, Object> record = dataList.get(i);
                for (Map.Entry<String, Object> entry : record.entrySet()) {
                    String columnName = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (columnMap.containsKey(columnName) && value != null) {
                        Map<String, Object> columnInfo = columnMap.get(columnName);
                        String dataType = (String) columnInfo.get("DATA_TYPE");
                        
                        // 简单的数据类型检查
                        if (!isValidDataType(value, dataType)) {
                            warnings.add(String.format("第%d行，字段%s的值可能不符合数据类型%s", 
                                    i + 1, columnName, dataType));
                        }
                    }
                }
            }
            
            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
            result.put("totalRecords", dataList.size());
            result.put("validatedColumns", dataColumns);
            result.put("requiredColumns", requiredColumns);
            
        } catch (Exception e) {
            errors.add("数据验证失败: " + e.getMessage());
            result.put("valid", false);
            result.put("errors", errors);
        }
        
        return result;
    }

    /**
     * 简单的数据类型验证
     */
    private boolean isValidDataType(Object value, String dataType) {
        if (value == null) {
            return true;
        }
        
        String valueStr = value.toString().trim();
        if (valueStr.isEmpty()) {
            return true;
        }
        
        switch (dataType.toLowerCase()) {
            case "int":
            case "integer":
            case "tinyint":
            case "smallint":
            case "mediumint":
            case "bigint":
                try {
                    Long.parseLong(valueStr);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "decimal":
            case "float":
            case "double":
            case "numeric":
                try {
                    Double.parseDouble(valueStr);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "date":
                return valueStr.matches("\\d{4}-\\d{2}-\\d{2}");
            case "datetime":
            case "timestamp":
                return valueStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            case "time":
                return valueStr.matches("\\d{2}:\\d{2}:\\d{2}");
            default:
                return true; // 字符串类型默认通过
        }
    }

    /**
     * 按字段值搜索表（带实时进度反馈的版本）
     * 通过SSE实时推送搜索进度给前端
     */
    public void findTablesByValueWithProgress(String dataSourceName, String searchValue, String searchMode, SseEmitter emitter) {
        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索值不能为空");
        }
        
        // 向后兼容：如果没有指定searchMode，使用auto模式
        if (searchMode == null || searchMode.trim().isEmpty()) {
            searchMode = "auto";
        }
        
        List<Map<String, Object>> resultTables = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        final long TIMEOUT_MS = 600000; // 10分钟超时
        
        try {
            // 发送开始事件
            Map<String, Object> startEvent = new HashMap<>();
            startEvent.put("type", "start");
            startEvent.put("message", "开始搜索...");
            startEvent.put("searchValue", searchValue);
            startEvent.put("searchMode", searchMode);
            startEvent.put("dataSource", dataSourceName);
            startEvent.put("searchTypeInfo", getSearchTypeInfo(searchMode, searchValue));
            emitter.send(SseEmitter.event().name("start").data(startEvent));
            
            // 获取数据库中的所有表
            List<Map<String, Object>> allTables = getAllTables(dataSourceName);
            logger.info("开始字段值搜索，数据库：{}，搜索值：{}，搜索模式：{}，总表数：{}", 
                    dataSourceName, searchValue, searchMode, allTables.size());
            
            // 发送总表数信息
            Map<String, Object> totalEvent = new HashMap<>();
            totalEvent.put("type", "total");
            totalEvent.put("totalTables", allTables.size());
            emitter.send(SseEmitter.event().name("total").data(totalEvent));
            
            int searchedCount = 0;
            int foundCount = 0;
            
            for (Map<String, Object> table : allTables) {
                // 检查SSE连接是否还活跃
                if (emitter == null) {
                    logger.warn("SSE连接已断开，停止搜索");
                    break;
                }
                
                // 检查超时
                if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                    logger.warn("字段值搜索超时，已搜索 {} 个表，找到 {} 个匹配表", searchedCount, foundCount);
                    Map<String, Object> timeoutEvent = new HashMap<>();
                    timeoutEvent.put("type", "timeout");
                    timeoutEvent.put("message", "搜索超时");
                    timeoutEvent.put("searchedCount", searchedCount);
                    timeoutEvent.put("foundCount", foundCount);
                    emitter.send(SseEmitter.event().name("timeout").data(timeoutEvent));
                    break;
                }
                
                String tableName = (String) table.get("TABLE_NAME");
                searchedCount++;
                
                logger.info("正在搜索表 {} ({}/{})", tableName, searchedCount, allTables.size());
                
                // 发送当前搜索进度
                Map<String, Object> progressEvent = new HashMap<>();
                progressEvent.put("type", "progress");
                progressEvent.put("currentTable", tableName);
                progressEvent.put("searchedCount", searchedCount);
                progressEvent.put("totalCount", allTables.size());
                progressEvent.put("foundCount", foundCount);
                progressEvent.put("percentage", Math.round((searchedCount * 100.0) / allTables.size()));
                progressEvent.put("elapsedTime", System.currentTimeMillis() - startTime);
                
                try {
                    emitter.send(SseEmitter.event().name("progress").data(progressEvent));
                } catch (IOException e) {
                    logger.warn("发送进度事件失败，客户端可能已断开连接: {}", e.getMessage());
                    break;
                }
                
                try {
                    // 检查表是否包含匹配值（支持搜索模式）
                    boolean hasMatch = checkTableForValueWithMode(dataSourceName, tableName, searchValue, searchMode);
                    
                    if (hasMatch) {
                        foundCount++;
                        // 获取匹配记录的准确数量
                        int actualCount = getActualMatchCountWithMode(dataSourceName, tableName, searchValue, searchMode);
                        
                        Map<String, Object> resultTable = new HashMap<>();
                        resultTable.put("TABLE_NAME", tableName);
                        resultTable.put("TABLE_ROWS", table.get("TABLE_ROWS"));
                        resultTable.put("TABLE_COMMENT", table.get("TABLE_COMMENT"));
                        resultTable.put("DATA_LENGTH", table.get("DATA_LENGTH"));
                        resultTable.put("CREATE_TIME", table.get("CREATE_TIME"));
                        resultTable.put("MATCH_COUNT", actualCount);
                        resultTable.put("SEARCH_VALUE", searchValue);
                        resultTable.put("DATA_SOURCE", dataSourceName);
                        resultTable.put("SEARCH_MODE", searchMode);
                        resultTable.put("IS_COMPLETE", true);
                        resultTable.put("SEARCH_TYPE", getSearchTypeInfo(searchMode, searchValue));
                        
                        resultTables.add(resultTable);
                        
                        // 发送找到匹配表的事件
                        Map<String, Object> foundEvent = new HashMap<>();
                        foundEvent.put("type", "found");
                        foundEvent.put("table", resultTable);
                        foundEvent.put("foundCount", foundCount);
                        
                        try {
                            emitter.send(SseEmitter.event().name("found").data(foundEvent));
                        } catch (IOException e) {
                            logger.warn("发送找到事件失败，客户端可能已断开连接: {}", e.getMessage());
                            break;
                        }
                        
                        logger.info("在表 {} 中找到 {} 条匹配记录", tableName, actualCount);
                    }
                    
                } catch (Exception e) {
                    // 某个表查询失败，记录日志但继续处理其他表
                    logger.warn("搜索表 {} 时出现错误: {}", tableName, e.getMessage());
                    
                    // 发送表搜索错误事件
                    Map<String, Object> tableErrorEvent = new HashMap<>();
                    tableErrorEvent.put("type", "table_error");
                    tableErrorEvent.put("tableName", tableName);
                    tableErrorEvent.put("error", e.getMessage());
                    
                    try {
                        emitter.send(SseEmitter.event().name("table_error").data(tableErrorEvent));
                    } catch (IOException ioException) {
                        logger.warn("发送表错误事件失败: {}", ioException.getMessage());
                        break;
                    }
                }
                
                // 添加小延迟，避免过于频繁的事件发送
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            logger.info("完整字段值搜索完成，用时：{}ms，搜索了 {} 个表，找到 {} 个匹配表", 
                    (endTime - startTime), searchedCount, foundCount);
            
            // 发送完成事件
            Map<String, Object> completeEvent = new HashMap<>();
            completeEvent.put("type", "complete");
            completeEvent.put("message", "搜索完成");
            completeEvent.put("searchedCount", searchedCount);
            completeEvent.put("foundCount", foundCount);
            completeEvent.put("totalCount", foundCount); // 添加totalCount字段，与foundCount保持一致
            completeEvent.put("totalTime", endTime - startTime);
            completeEvent.put("tables", resultTables);
            completeEvent.put("searchValue", searchValue);
            completeEvent.put("dataSource", dataSourceName);
            completeEvent.put("searchInfo", "完整数据搜索");
            completeEvent.put("isCompleteSearch", true);
            completeEvent.put("searchType", "全表全字段搜索");
            
            emitter.send(SseEmitter.event().name("complete").data(completeEvent));
            emitter.complete();
            
        } catch (Exception e) {
            logger.error("执行字段值搜索时发生错误: {}", e.getMessage());
            
            try {
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("type", "error");
                errorEvent.put("message", "搜索失败: " + e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errorEvent));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                emitter.completeWithError(ioException);
            }
        }
    }

    /**
     * 保持向后兼容的方法（不带搜索模式）
     */
    public void findTablesByValueWithProgress(String dataSourceName, String searchValue, SseEmitter emitter) {
        findTablesByValueWithProgress(dataSourceName, searchValue, "auto", emitter);
    }
    
    /**
     * 优化数据类型，确保整数类型有足够的长度
     */
    private String optimizeDataType(String originalDataType, List<Map<String, Object>> csvData, String columnName) {
        if (originalDataType == null) {
            return "VARCHAR(255)";
        }
        
        String upperDataType = originalDataType.toUpperCase();
        
        // 如果是整数类型，进行更详细的验证
        if (upperDataType.contains("INT") && !upperDataType.contains("BIGINT")) {
            // 检查所有数据，确保没有超出范围的值
            long maxValue = Long.MIN_VALUE;
            long minValue = Long.MAX_VALUE;
            boolean hasValidNumbers = false;
            
            for (Map<String, Object> row : csvData) {
                Object value = row.get(columnName);
                if (value != null && !value.toString().trim().isEmpty()) {
                    try {
                        long numValue = Long.parseLong(value.toString().trim());
                        maxValue = Math.max(maxValue, numValue);
                        minValue = Math.min(minValue, numValue);
                        hasValidNumbers = true;
                    } catch (NumberFormatException e) {
                        // 忽略非数字值
                    }
                }
            }
            
            if (hasValidNumbers) {
                // 根据实际数值范围选择最合适的数据类型
                if (maxValue <= 127 && minValue >= -128) {
                    return "TINYINT";
                } else if (maxValue <= 32767 && minValue >= -32768) {
                    return "SMALLINT";
                } else if (maxValue <= 8388607 && minValue >= -8388608) {
                    return "MEDIUMINT";
                } else if (maxValue <= 2147483647L && minValue >= -2147483648L) {
                    return "INT";
                } else {
                    // 如果超出INT范围，升级为BIGINT
                    logger.warn("列 {} 的数值范围超出INT限制，自动升级为BIGINT。最大值: {}, 最小值: {}", 
                               columnName, maxValue, minValue);
                    return "BIGINT";
                }
            }
        }
        
        // 对于其他类型，保持原样
        return originalDataType;
    }

    /**
     * 修改表结构 - 修改列的数据类型
     */
    public boolean modifyTableColumn(String dataSourceName, String databaseName, String tableName, 
                                   String columnName, String newDataType, String newLength, String newDecimals) {
        try {
            if (!isValidTableName(tableName)) {
                throw new IllegalArgumentException("表名不符合规范");
            }
            
            if (!tableExists(dataSourceName, databaseName, tableName)) {
                throw new IllegalArgumentException("表不存在");
            }
            
            // 验证列是否存在
            List<Map<String, Object>> columns = getTableColumns(dataSourceName, tableName);
            boolean columnExists = columns.stream()
                .anyMatch(col -> columnName.equalsIgnoreCase((String) col.get("COLUMN_NAME")));
            
            if (!columnExists) {
                throw new IllegalArgumentException("列 '" + columnName + "' 不存在于表 '" + tableName + "' 中");
            }
            
            JdbcTemplate jdbcTemplate;
            String sql;
            
            // 构建ALTER TABLE语句
            StringBuilder alterSql = new StringBuilder();
            alterSql.append("ALTER TABLE ");
            
            if (isUserCreatedDatabase(databaseName)) {
                alterSql.append("`").append(databaseName).append("`.`").append(tableName).append("` ");
                jdbcTemplate = getJdbcTemplate(DEFAULT_DATASOURCE);
            } else {
                alterSql.append("`").append(tableName).append("` ");
                jdbcTemplate = getJdbcTemplate(dataSourceName);
            }
            
            alterSql.append("MODIFY COLUMN `").append(columnName).append("` ");
            
            // 构建新的数据类型
            String fullDataType = newDataType.toUpperCase();
            if (needsLength(newDataType)) {
                fullDataType += "(";
                if (newLength != null && !newLength.trim().isEmpty()) {
                    fullDataType += newLength;
                    if (needsDecimals(newDataType) && newDecimals != null && !newDecimals.trim().isEmpty()) {
                        fullDataType += "," + newDecimals;
                    }
                }
                fullDataType += ")";
            }
            
            alterSql.append(fullDataType);
            
            logger.info("执行修改表结构SQL: {}", alterSql.toString());
            
            // 执行修改
            jdbcTemplate.execute(alterSql.toString());
            
            logger.info("成功修改表结构: {}.{}.{} -> {}", databaseName, tableName, columnName, fullDataType);
            return true;
            
        } catch (Exception e) {
            logger.error("修改表结构失败 - 数据库: {}, 表: {}, 列: {}, 错误: {}", 
                        databaseName, tableName, columnName, e.getMessage());
            throw new RuntimeException("修改表结构失败: " + e.getMessage(), e);
        }
    }
} 