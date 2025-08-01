package com.example.bio_data.controller;

import com.example.bio_data.service.DatabaseService;
import com.example.bio_data.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081"})
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;
    
    @Autowired
    private PermissionService permissionService;
    
    /**
     * 安全地从请求中获取userId
     */
    private Long extractUserId(Map<String, Object> request) {
        Object userIdObj = request.get("userId");
        if (userIdObj == null) {
            return null;
        }
        
        try {
            if (userIdObj instanceof String) {
                String userIdStr = (String) userIdObj;
                return userIdStr.trim().isEmpty() ? null : Long.parseLong(userIdStr);
            } else if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            } else {
                String userIdStr = userIdObj.toString();
                return userIdStr.trim().isEmpty() ? null : Long.parseLong(userIdStr);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 权限验证辅助方法
     */
    private ResponseEntity<?> validatePermission(Long userId, String userType, String dataSource, String operation) {
        if (userId == null || userType == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
        }
        
        // 确定数据库名称 - 将dataSource映射到实际的数据库名称
        String databaseName = dataSource;
        if (databaseName == null || databaseName.trim().isEmpty()) {
            databaseName = "default"; // 默认数据库
        }
        // 如果dataSource直接是"login"，则使用"login"作为数据库名称
        // 其他数据源（如chembl33等）不是login数据库，使用"default"
        if (!"login".equals(databaseName)) {
            databaseName = "default";
        }
        
        Map<String, Object> permissionResult = permissionService.validatePermission(userId, userType, databaseName, operation);
        
        if (!(Boolean) permissionResult.get("success")) {
            return ResponseEntity.status(403).body(Map.of("error", permissionResult.get("error")));
        }
        
        return null; // 权限验证通过
    }

    // =============================================================================
    // 数据源管理相关接口
    // =============================================================================

    /**
     * 获取所有可用数据源
     */
    @GetMapping("/datasources")
    public ResponseEntity<?> getAvailableDataSources() {
        try {
            List<Map<String, Object>> dataSources = databaseService.getAvailableDataSources();
            return ResponseEntity.ok(dataSources);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据源列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有可用数据库（用于CSV导入等功能）
     */
    @GetMapping("/datasources/databases")
    public ResponseEntity<?> getAvailableDatabasesForImport(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            if (userId == null || userType == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
            }
            
            // 获取所有数据库（包括用户创建的）
            List<Map<String, Object>> databases = databaseService.getAllDatabases("chembl33");
            
            // 过滤掉系统数据库，只保留可用于导入的数据库
            List<Map<String, Object>> availableDatabases = new ArrayList<>();
            for (Map<String, Object> db : databases) {
                String dbName = (String) db.get("name");
                
                // 检查权限：对于login数据库，只有管理员可以访问
                if ("login".equals(dbName) && !"admin".equals(userType)) {
                    continue;
                }
                
                // 添加到可用数据库列表
                Map<String, Object> availableDb = new HashMap<>();
                availableDb.put("name", dbName);
                availableDb.put("displayName", db.get("displayName"));
                availableDb.put("description", db.get("description"));
                availableDb.put("type", "database");
                availableDb.put("connected", true);
                
                availableDatabases.add(availableDb);
            }
            
            return ResponseEntity.ok(availableDatabases);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库列表失败: " + e.getMessage()));
        }
    }

    /**
     * 添加新的数据源
     */
    @PostMapping("/datasources")
    public ResponseEntity<?> addDataSource(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String url = request.get("url");
            String username = request.get("username");
            String password = request.get("password");

            if (name == null || url == null || username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "缺少必需的参数"));
            }

            boolean success = databaseService.addDataSource(name, url, username, password);
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "数据源添加成功",
                    "dataSourceName", name
                ));
            } else {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", "数据源添加失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "添加数据源失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/datasources/{dataSourceName}")
    public ResponseEntity<?> removeDataSource(@PathVariable String dataSourceName) {
        try {
            boolean success = databaseService.removeDataSource(dataSourceName);
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "数据源删除成功"
                ));
            } else {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", "数据源删除失败"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "删除数据源失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 测试数据源连接
     */
    @GetMapping("/datasources/{dataSourceName}/test")
    public ResponseEntity<?> testDataSourceConnection(@PathVariable String dataSourceName) {
        try {
            boolean connected = databaseService.testDataSourceConnection(dataSourceName);
            return ResponseEntity.ok(Map.of(
                "dataSourceName", dataSourceName,
                "connected", connected,
                "message", connected ? "连接成功" : "连接失败"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "dataSourceName", dataSourceName,
                "connected", false,
                "error", "连接测试失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取数据源统计信息
     */
    @GetMapping("/datasources/{dataSourceName}/stats")
    public ResponseEntity<?> getDataSourceStats(@PathVariable String dataSourceName) {
        try {
            Map<String, Object> stats = databaseService.getDataSourceStats(dataSourceName);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据源统计失败: " + e.getMessage()));
        }
    }

    // =============================================================================
    // 数据库操作相关接口（支持多数据源）
    // =============================================================================

    /**
     * 获取数据库统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats(
            @RequestParam(required = false) String dataSource) {
        try {
            Map<String, Object> stats;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                stats = databaseService.getDatabaseStats(dataSource);
            } else {
                stats = databaseService.getDatabaseStats();
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有表信息
     */
    @GetMapping("/tables")
    public ResponseEntity<List<Map<String, Object>>> getAllTables(
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return ResponseEntity.status(permissionCheck.getStatusCode()).body(null);
            }
            
            List<Map<String, Object>> tables;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                tables = databaseService.getAllTables(dataSource);
            } else {
                tables = databaseService.getAllTables();
            }
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取指定表的列信息
     */
    @GetMapping("/tables/{tableName}/columns")
    public ResponseEntity<List<Map<String, Object>>> getTableColumns(
            @PathVariable String tableName,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return ResponseEntity.status(permissionCheck.getStatusCode()).body(null);
            }
            
            List<Map<String, Object>> columns;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                columns = databaseService.getTableColumns(dataSource, tableName);
            } else {
                columns = databaseService.getTableColumns(tableName);
            }
            return ResponseEntity.ok(columns);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取表数据
     */
    @GetMapping("/tables/{tableName}/data")
    public ResponseEntity<List<Map<String, Object>>> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return ResponseEntity.status(permissionCheck.getStatusCode()).body(null);
            }
            
            if (limit > 1000) limit = 1000; // 限制最大返回行数
            List<Map<String, Object>> data;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                data = databaseService.getTableData(dataSource, tableName, limit);
            } else {
                data = databaseService.getTableData(tableName, limit);
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 分页获取表数据
     */
    @GetMapping("/tables/{tableName}/data/page")
    public ResponseEntity<Map<String, Object>> getTableDataWithPagination(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return ResponseEntity.status(permissionCheck.getStatusCode()).body(Map.of("error", "权限不足"));
            }
            
            if (size > 100) size = 100; // 限制每页最大100条
            if (page < 1) page = 1; // 页码最小为1
            
            Map<String, Object> result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.getTableDataWithPagination(dataSource, tableName, page, size);
            } else {
                result = databaseService.getTableDataWithPagination(tableName, page, size);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据失败: " + e.getMessage()));
        }
    }

    /**
     * 获取表的索引信息
     */
    @GetMapping("/tables/{tableName}/indexes")
    public ResponseEntity<List<Map<String, Object>>> getTableIndexes(
            @PathVariable String tableName,
            @RequestParam(required = false) String dataSource) {
        try {
            List<Map<String, Object>> indexes;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                indexes = databaseService.getTableIndexes(dataSource, tableName);
            } else {
                indexes = databaseService.getTableIndexes(tableName);
            }
            return ResponseEntity.ok(indexes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 执行自定义SQL查询
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody Map<String, Object> request) {
        try {
            String sql = (String) request.get("sql");
            Object limitObj = request.get("limit");
            int limit = 100;
            if (limitObj != null) {
                if (limitObj instanceof Number) {
                    limit = ((Number) limitObj).intValue();
                } else {
                    limit = Integer.parseInt(limitObj.toString());
                }
            }
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
            }
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            if (limit > 1000) limit = 1000; // 限制最大返回行数
            
            List<Map<String, Object>> result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.executeQuery(dataSource, sql, limit);
            } else {
                result = databaseService.executeQuery(sql, limit);
            }
            
            return ResponseEntity.ok(Map.of(
                "data", result,
                "count", result.size(),
                "sql", sql,
                "dataSource", dataSource != null ? dataSource : "default"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "查询执行失败: " + e.getMessage()));
        }
    }

    /**
     * 根据字段名搜索包含该字段的表
     */
    @GetMapping("/search/tables-by-column")
    public ResponseEntity<?> findTablesByColumn(
            @RequestParam String columnName,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 基本参数验证
            if (columnName == null || columnName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "字段名不能为空"));
            }
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            List<Map<String, Object>> tables;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                tables = databaseService.findTablesByColumn(dataSource, columnName);
            } else {
                tables = databaseService.findTablesByColumn(columnName);
            }
            
            // 统计结果
            Map<String, Object> result = new HashMap<>();
            result.put("tables", tables);
            result.put("totalCount", tables.size());
            result.put("columnName", columnName);
            result.put("dataSource", dataSource != null ? dataSource : "default");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "字段搜索失败: " + e.getMessage()));
        }
    }

    /**
     * 根据字段名获取表中包含该字段的数据
     */
    @GetMapping("/search/data-by-column")
    public ResponseEntity<?> getTableDataByColumn(
            @RequestParam String tableName,
            @RequestParam String columnName,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 基本参数验证
            if (tableName == null || tableName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
            }
            if (columnName == null || columnName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "字段名不能为空"));
            }
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            // 限制返回行数
            if (limit > 1000) limit = 1000;
            if (limit < 1) limit = 100;
            
            Map<String, Object> result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.getTableDataByColumn(dataSource, tableName, columnName, limit);
            } else {
                result = databaseService.getTableDataByColumn(tableName, columnName, limit);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "数据查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据字段值搜索包含该值的所有表（类似Navicat的字段查找功能）
     */
    @GetMapping("/search/tables-by-value")
    public ResponseEntity<?> findTablesByValue(
            @RequestParam String searchValue,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false, defaultValue = "auto") String searchMode) {
        try {
            // 基本参数验证
            if (searchValue == null || searchValue.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "搜索值不能为空"));
            }
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            List<Map<String, Object>> tables;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                tables = databaseService.findTablesByValue(dataSource, searchValue, searchMode);
            } else {
                tables = databaseService.findTablesByValue(null, searchValue, searchMode);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("tables", tables);
            response.put("totalCount", tables.size());
            response.put("searchValue", searchValue);
            response.put("dataSource", dataSource);
            response.put("searchMode", searchMode);
            
            // 根据搜索模式设置响应信息
            if ("text_only".equals(searchMode)) {
                response.put("searchInfo", "仅搜索文本字段");
                response.put("searchType", "文本字段搜索");
            } else if ("numeric_only".equals(searchMode)) {
                response.put("searchInfo", "仅搜索数字字段");
                response.put("searchType", "数字字段搜索");
            } else if ("auto".equals(searchMode)) {
                // 智能判断搜索模式
                boolean isNumeric = isSearchValueNumeric(searchValue);
                if (isNumeric) {
                    response.put("searchInfo", "智能搜索：检测到数字，搜索数字字段");
                    response.put("searchType", "智能数字搜索");
                } else {
                    response.put("searchInfo", "智能搜索：检测到文本，仅搜索文本字段");
                    response.put("searchType", "智能文本搜索");
                }
            } else {
                response.put("searchInfo", "完整数据搜索");
                response.put("searchType", "全表全字段搜索");
            }
            
            response.put("isCompleteSearch", "all".equals(searchMode));
            response.put("timeoutMinutes", 5);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "字段值搜索失败: " + e.getMessage()));
        }
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
     * 根据字段值搜索包含该值的所有表（实时进度版本，使用SSE）
     */
    @GetMapping(value = "/search/tables-by-value-progress", produces = "text/event-stream")
    public SseEmitter searchTablesByValueWithProgress(
            @RequestParam String searchValue,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false, defaultValue = "auto") String searchMode) {
        
        // 基本参数验证
        if (searchValue == null || searchValue.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索值不能为空");
        }
        
        // 权限验证
        ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
        if (permissionCheck != null) {
            throw new RuntimeException("权限验证失败");
        }
        
        // 创建SSE发射器，设置30分钟超时
        SseEmitter emitter = new SseEmitter(1800000L);
        
        // 异步执行搜索
        CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null && !dataSource.trim().isEmpty()) {
                    databaseService.findTablesByValueWithProgress(dataSource, searchValue, searchMode, emitter);
                } else {
                    databaseService.findTablesByValueWithProgress(null, searchValue, searchMode, emitter);
                }
            } catch (Exception e) {
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
        });
        
        return emitter;
    }

    /**
     * 根据字段值获取表中包含该值的数据记录
     */
    @GetMapping("/search/data-by-value")
    public ResponseEntity<?> getTableDataByValue(
            @RequestParam String tableName,
            @RequestParam String searchValue,
            @RequestParam(defaultValue = "10000") int limit,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 基本参数验证
            if (tableName == null || tableName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
            }
            if (searchValue == null || searchValue.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "搜索值不能为空"));
            }
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            // 如果传入了page和size参数，使用分页模式；否则使用limit模式保持向后兼容
            Map<String, Object> result;
            if (page > 1 || size != 50) {
                // 分页模式
                if (size > 1000) size = 1000;
                if (size < 1) size = 50;
                if (page < 1) page = 1;
                
                if (dataSource != null && !dataSource.trim().isEmpty()) {
                    result = databaseService.getTableDataByValueWithPagination(dataSource, tableName, searchValue, page, size);
                } else {
                    result = databaseService.getTableDataByValueWithPagination(null, tableName, searchValue, page, size);
                }
            } else {
                // 传统limit模式，保持向后兼容
                if (limit > 10000) limit = 10000;
                if (limit < 1) limit = 100;
                
                if (dataSource != null && !dataSource.trim().isEmpty()) {
                    result = databaseService.getTableDataByValue(dataSource, tableName, searchValue, limit);
                } else {
                    result = databaseService.getTableDataByValue(null, tableName, searchValue, limit);
                }
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "数据查询失败: " + e.getMessage()));
        }
    }

    /**
     * 向表中插入数据
     */
    @PostMapping("/tables/{tableName}/data")
    public ResponseEntity<?> insertTableData(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> request) {
        try {
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.get("data");
            
            if (data == null) {
                data = request; // 向后兼容，如果没有data字段，则整个请求体就是数据
                data.remove("dataSource"); // 移除dataSource字段
                data.remove("userId"); // 移除userId字段
                data.remove("userType"); // 移除userType字段
            }
            
            int result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.insertTableData(dataSource, tableName, data);
            } else {
                result = databaseService.insertTableData(tableName, data);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "数据插入成功",
                "affectedRows", result,
                "dataSource", dataSource != null ? dataSource : "default"
            ));
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            String friendlyMessage = translateDataInsertError(errorMessage);
            
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", friendlyMessage
            ));
        }
    }

    /**
     * 删除表中的数据
     */
    @DeleteMapping("/tables/{tableName}/data")
    public ResponseEntity<?> deleteTableData(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> request) {
        try {
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> whereConditions = (Map<String, Object>) request.get("whereConditions");
            
            if (whereConditions == null) {
                whereConditions = request; // 向后兼容
                whereConditions.remove("dataSource");
                whereConditions.remove("userId");
                whereConditions.remove("userType");
            }
            
            int result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.deleteTableData(dataSource, tableName, whereConditions);
            } else {
                result = databaseService.deleteTableData(tableName, whereConditions);
            }
            
            if (result > 0) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "数据删除成功",
                    "affectedRows", result,
                    "dataSource", dataSource != null ? dataSource : "default"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "未找到符合条件的数据",
                    "affectedRows", 0
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据删除失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 更新表中的数据
     */
    @PutMapping("/tables/{tableName}/data")
    public ResponseEntity<?> updateTableData(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> request) {
        try {
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> updateData = (Map<String, Object>) request.get("updateData");
            @SuppressWarnings("unchecked")
            Map<String, Object> whereConditions = (Map<String, Object>) request.get("whereConditions");
            
            int result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.updateTableData(dataSource, tableName, updateData, whereConditions);
            } else {
                result = databaseService.updateTableData(tableName, updateData, whereConditions);
            }
            
            if (result > 0) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "数据更新成功",
                    "affectedRows", result,
                    "dataSource", dataSource != null ? dataSource : "default"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "未找到符合条件的数据",
                    "affectedRows", 0
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据更新失败: " + e.getMessage()
            ));
        }
    }

    // =============================================================================
    // 兼容性接口
    // =============================================================================

    /**
     * 获取数据库信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getDatabaseInfo(@RequestParam(required = false) String database) {
        try {
            Map<String, Object> info = databaseService.getDatabaseInfo(database);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取可用数据库列表（兼容性接口，返回数据源列表）
     */
    @GetMapping("/databases")
    public ResponseEntity<?> getAvailableDatabases() {
        try {
            List<Map<String, Object>> databases = databaseService.getAvailableDatabases();
            return ResponseEntity.ok(databases);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建新数据库
     */
    @PostMapping("/create")
    public ResponseEntity<?> createDatabase(@RequestBody Map<String, Object> request) {
        try {
            // 获取用户信息
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 验证管理员权限
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "login", "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            // 只允许管理员创建数据库
            if (!"admin".equals(userType)) {
                return ResponseEntity.status(403).body(Map.of("error", "只有管理员才能创建数据库"));
            }
            
            String databaseName = (String) request.get("databaseName");
            String charset = (String) request.get("charset");
            String collation = (String) request.get("collation");
            String description = (String) request.get("description");
            
            if (databaseName == null || databaseName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据库名称不能为空"));
            }
            
            // 使用默认数据源连接创建数据库
            String dataSourceName = "chembl33"; // 使用默认连接
            
            boolean success = databaseService.createDatabase(dataSourceName, databaseName, charset, collation);
            
            if (success) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "数据库创建成功");
                result.put("databaseName", databaseName);
                result.put("charset", charset != null ? charset : "utf8");
                result.put("collation", collation != null ? collation : "utf8_general_ci");
                result.put("description", description != null ? description : "用户创建的数据库");
                result.put("type", "mysql");
                result.put("status", "active");
                
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "数据库创建失败"
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "创建数据库失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 删除数据库
     */
    @DeleteMapping("/drop")
    public ResponseEntity<?> dropDatabase(@RequestBody Map<String, Object> request) {
        try {
            // 获取用户信息
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 验证管理员权限
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "login", "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            // 只允许管理员删除数据库
            if (!"admin".equals(userType)) {
                return ResponseEntity.status(403).body(Map.of("error", "只有管理员才能删除数据库"));
            }
            
            String dataSourceName = (String) request.get("dataSource");
            String databaseName = (String) request.get("databaseName");
            
            if (databaseName == null || databaseName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据库名称不能为空"));
            }
            
            // 使用默认数据源如果未指定
            if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
                dataSourceName = "chembl33";
            }
            
            boolean success = databaseService.dropDatabase(dataSourceName, databaseName);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "数据库删除成功",
                    "databaseName", databaseName
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "数据库删除失败"
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "删除数据库失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取所有数据库（包括用户创建的）
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllDatabases(
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "default", "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            // 使用默认数据源如果未指定
            if (dataSource == null || dataSource.trim().isEmpty()) {
                dataSource = "chembl33";
            }
            
            List<Map<String, Object>> databases = databaseService.getAllDatabases(dataSource);
            return ResponseEntity.ok(databases);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库列表失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(@RequestParam(required = false) String dataSource) {
        try {
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                databaseService.getDatabaseStats(dataSource);
                return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "Connected",
                    "dataSource", dataSource,
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                databaseService.getDatabaseStats();
                return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "Connected",
                    "dataSource", "default",
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "database", "Disconnected",
                "error", e.getMessage(),
                "dataSource", dataSource != null ? dataSource : "default",
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    // =============================================================================
    // 表管理相关接口
    // =============================================================================
    
    /**
     * 创建新表
     */
    @PostMapping("/tables/create")
    public ResponseEntity<?> createTable(@RequestBody Map<String, Object> request) {
        try {
            // 获取用户信息
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            String dataSourceName = (String) request.get("dataSource");
            String databaseName = (String) request.get("databaseName");
            
            // 验证权限
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, databaseName, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            String tableName = (String) request.get("tableName");
            String tableComment = (String) request.get("tableComment");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) request.get("columns");
            
            if (tableName == null || tableName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
            }
            
            if (databaseName == null || databaseName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据库名称不能为空"));
            }
            
            if (columns == null || columns.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "至少需要定义一个列"));
            }
            
            // 检查表是否已存在
            if (databaseService.tableExists(dataSourceName, databaseName, tableName)) {
                return ResponseEntity.badRequest().body(Map.of("error", "表 '" + tableName + "' 已存在"));
            }
            
            // 使用默认数据源如果未指定
            if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
                dataSourceName = "chembl33";
            }
            
            boolean success = databaseService.createTable(dataSourceName, databaseName, tableName, columns, tableComment);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "表创建成功",
                    "tableName", tableName,
                    "databaseName", databaseName
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "表创建失败"
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "创建表失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 删除表
     */
    @DeleteMapping("/tables/drop")
    public ResponseEntity<?> dropTable(@RequestBody Map<String, Object> request) {
        try {
            // 获取用户信息
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            String dataSourceName = (String) request.get("dataSource");
            String databaseName = (String) request.get("databaseName");
            String tableName = (String) request.get("tableName");
            
            // 验证权限
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, databaseName, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            if (tableName == null || tableName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "表名不能为空"));
            }
            
            if (databaseName == null || databaseName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据库名称不能为空"));
            }
            
            // 使用默认数据源如果未指定
            if (dataSourceName == null || dataSourceName.trim().isEmpty()) {
                dataSourceName = "chembl33";
            }
            
            boolean success = databaseService.dropTable(dataSourceName, databaseName, tableName);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "表删除成功",
                    "tableName", tableName,
                    "databaseName", databaseName
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "表删除失败"
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "删除表失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取支持的数据类型列表
     */
    @GetMapping("/data-types")
    public ResponseEntity<?> getSupportedDataTypes() {
        try {
            List<Map<String, Object>> dataTypes = databaseService.getSupportedDataTypes();
            return ResponseEntity.ok(dataTypes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据类型列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 检查表是否存在
     */
    @GetMapping("/tables/exists")
    public ResponseEntity<?> checkTableExists(
            @RequestParam String dataSource,
            @RequestParam String databaseName,
            @RequestParam String tableName) {
        try {
            boolean exists = databaseService.tableExists(dataSource, databaseName, tableName);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "tableName", tableName,
                "databaseName", databaseName
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "检查表是否存在失败: " + e.getMessage()));
        }
    }

    // =============================================================================
    // 缓存管理相关接口
    // =============================================================================

    /**
     * 清理搜索缓存
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<?> clearSearchCache(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证 - 只有登录用户可以清理缓存
            if (userId == null || userType == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
            }
            
            String dataSource = (String) request.get("dataSource");
            String tableName = (String) request.get("tableName");
            
            if (tableName != null && !tableName.trim().isEmpty()) {
                // 清理特定表的缓存
                databaseService.clearTableSearchCache(dataSource, tableName);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "表 " + tableName + " 的搜索缓存已清理"
                ));
            } else {
                // 清理所有缓存
                databaseService.clearAllSearchCache();
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "所有搜索缓存已清理"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "清理缓存失败: " + e.getMessage()));
        }
    }

    /**
     * 获取搜索缓存统计信息
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<?> getSearchCacheStats(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证 - 只有登录用户可以查看缓存统计
            if (userId == null || userType == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
            }
            
            Map<String, Object> stats = databaseService.getSearchCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取缓存统计失败: " + e.getMessage()));
        }
    }

    /**
     * 批量插入表数据
     */
    @PostMapping("/tables/{tableName}/batch-insert")
    public ResponseEntity<?> batchInsertTableData(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> request) {
        try {
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            Boolean useTransaction = (Boolean) request.get("useTransaction");
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "write");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) request.get("dataList");
            
            if (dataList == null || dataList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据列表不能为空"));
            }
            
            // 限制单次批量插入的数据量（防止内存溢出）
            if (dataList.size() > 100000) {
                return ResponseEntity.badRequest().body(Map.of("error", "单次批量插入数据量不能超过10万条"));
            }
            
            Map<String, Object> result;
            if (useTransaction != null && useTransaction) {
                // 使用事务性批量插入
                if (dataSource != null && !dataSource.trim().isEmpty()) {
                    result = databaseService.batchInsertTableDataTransaction(dataSource, tableName, dataList);
                } else {
                    result = databaseService.batchInsertTableDataTransaction("chembl33", tableName, dataList);
                }
            } else {
                // 使用非事务性批量插入
                if (dataSource != null && !dataSource.trim().isEmpty()) {
                    result = databaseService.batchInsertTableData(dataSource, tableName, dataList);
                } else {
                    result = databaseService.batchInsertTableData("chembl33", tableName, dataList);
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量插入完成",
                "result", result
            ));
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            String friendlyMessage = translateDataInsertError(errorMessage);
            
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", friendlyMessage
            ));
        }
    }

    /**
     * 验证CSV数据格式
     */
    @PostMapping("/tables/{tableName}/validate-csv")
    public ResponseEntity<?> validateCsvData(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> request) {
        try {
            String dataSource = (String) request.get("dataSource");
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户ID无效"));
            }
            
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) request.get("dataList");
            
            if (dataList == null || dataList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "数据列表不能为空"));
            }
            
            Map<String, Object> result;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                result = databaseService.validateCsvData(dataSource, tableName, dataList);
            } else {
                result = databaseService.validateCsvData("chembl33", tableName, dataList);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "validation", result
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据验证失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取表的列信息（用于CSV导入时的列映射）
     */
    @GetMapping("/tables/{tableName}/columns-info")
    public ResponseEntity<?> getTableColumnsInfo(
            @PathVariable String tableName,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, dataSource, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            List<Map<String, Object>> columns;
            if (dataSource != null && !dataSource.trim().isEmpty()) {
                columns = databaseService.getTableColumns(dataSource, tableName);
            } else {
                columns = databaseService.getTableColumns("chembl33", tableName);
            }
            
            // 处理列信息，添加更多有用的信息
            List<Map<String, Object>> processedColumns = new ArrayList<>();
            for (Map<String, Object> column : columns) {
                Map<String, Object> processedColumn = new HashMap<>(column);
                
                String dataType = (String) column.get("DATA_TYPE");
                String isNullable = (String) column.get("IS_NULLABLE");
                String columnDefault = (String) column.get("COLUMN_DEFAULT");
                String extra = (String) column.get("EXTRA");
                
                processedColumn.put("isRequired", "NO".equals(isNullable) && columnDefault == null && 
                        (extra == null || !extra.contains("auto_increment")));
                processedColumn.put("isAutoIncrement", extra != null && extra.contains("auto_increment"));
                processedColumn.put("allowNull", "YES".equals(isNullable));
                processedColumn.put("hasDefault", columnDefault != null);
                
                processedColumns.add(processedColumn);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "columns", processedColumns,
                "tableName", tableName,
                "dataSource", dataSource != null ? dataSource : "chembl33"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "获取表列信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 诊断数据库和表状态（用于排查导入问题）
     */
    @GetMapping("/tables/{tableName}/diagnose")
    public ResponseEntity<?> diagnoseTableForImport(
            @PathVariable String tableName,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String userType) {
        try {
            // 权限验证
            if (userId == null || userType == null) {
                return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
            }
            
            String actualDataSource = (dataSource == null || dataSource.trim().isEmpty()) ? "chembl33" : dataSource;
            
            Map<String, Object> diagnosis = new HashMap<>();
            diagnosis.put("dataSource", actualDataSource);
            diagnosis.put("tableName", tableName);
            diagnosis.put("timestamp", System.currentTimeMillis());
            
            try {
                // 检查数据库是否存在
                boolean databaseExists = checkDatabaseExists(actualDataSource);
                diagnosis.put("databaseExists", databaseExists);
                
                if (!databaseExists) {
                    diagnosis.put("error", "数据库 '" + actualDataSource + "' 不存在");
                    diagnosis.put("suggestion", "请检查数据库名称是否正确，或者先创建该数据库");
                    return ResponseEntity.ok(diagnosis);
                }
                
                // 检查表是否存在
                boolean tableExists = checkTableExists(actualDataSource, tableName);
                diagnosis.put("tableExists", tableExists);
                
                if (!tableExists) {
                    diagnosis.put("error", "表 '" + tableName + "' 在数据库 '" + actualDataSource + "' 中不存在");
                    diagnosis.put("suggestion", "请检查表名是否正确，或者先创建该表");
                    return ResponseEntity.ok(diagnosis);
                }
                
                // 获取表结构
                List<Map<String, Object>> columns = databaseService.getTableColumns(actualDataSource, tableName);
                diagnosis.put("columns", columns);
                diagnosis.put("columnCount", columns.size());
                
                // 获取表的基本信息
                List<Map<String, Object>> tableInfo = databaseService.getAllTables(actualDataSource);
                Map<String, Object> currentTable = tableInfo.stream()
                    .filter(table -> tableName.equals(table.get("TABLE_NAME")))
                    .findFirst()
                    .orElse(null);
                
                if (currentTable != null) {
                    diagnosis.put("tableInfo", currentTable);
                    diagnosis.put("tableRows", currentTable.get("TABLE_ROWS"));
                    diagnosis.put("dataLength", currentTable.get("DATA_LENGTH"));
                }
                
                // 检查数据源类型
                boolean isUserCreated = databaseService.isUserCreatedDatabase(actualDataSource);
                diagnosis.put("isUserCreatedDatabase", isUserCreated);
                
                // 测试连接
                try {
                    JdbcTemplate jdbcTemplate = databaseService.getJdbcTemplate(actualDataSource);
                    if (isUserCreated) {
                        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    } else {
                        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    }
                    diagnosis.put("connectionTest", "成功");
                } catch (Exception e) {
                    diagnosis.put("connectionTest", "失败: " + e.getMessage());
                }
                
                diagnosis.put("status", "success");
                diagnosis.put("message", "诊断完成");
                
            } catch (Exception e) {
                diagnosis.put("error", "诊断过程中发生错误: " + e.getMessage());
                diagnosis.put("status", "error");
            }
            
            return ResponseEntity.ok(diagnosis);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "诊断失败: " + e.getMessage(),
                "dataSource", dataSource,
                "tableName", tableName
            ));
        }
    }
    
    /**
     * 检查数据库是否存在
     */
    private boolean checkDatabaseExists(String databaseName) {
        try {
            JdbcTemplate jdbcTemplate = databaseService.getJdbcTemplate("chembl33");
            String sql = "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查表是否存在
     */
    private boolean checkTableExists(String databaseName, String tableName) {
        try {
            JdbcTemplate jdbcTemplate = databaseService.getJdbcTemplate("chembl33");
            String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, databaseName, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 翻译数据插入错误信息为用户友好的提示
     */
    private String translateDataInsertError(String errorMessage) {
        if (errorMessage == null) {
            return "数据插入失败：未知错误";
        }
        
        String lowerError = errorMessage.toLowerCase();
        
        // 重复键错误
        if (lowerError.contains("duplicate entry") || lowerError.contains("duplicate key")) {
            if (errorMessage.contains("for key 'PRIMARY'")) {
                return "主键重复：该记录的主键值已存在，请使用不同的主键值";
            } else if (errorMessage.contains("for key")) {
                // 提取键名
                String keyName = extractKeyName(errorMessage);
                return "数据重复：字段 '" + keyName + "' 的值已存在，请使用不同的值";
            } else {
                return "数据重复：该记录已存在，请检查主键或唯一字段的值";
            }
        }
        
        // 外键约束错误
        if (lowerError.contains("foreign key constraint") || lowerError.contains("cannot add or update")) {
            return "外键约束失败：引用的数据不存在，请先确保被引用的记录存在";
        }
        
        // 非空约束错误
        if (lowerError.contains("cannot be null") || lowerError.contains("not null")) {
            String columnName = extractColumnName(errorMessage);
            if (columnName != null) {
                return "必填字段缺失：字段 '" + columnName + "' 不能为空，请填写该字段";
            } else {
                return "必填字段缺失：某些必填字段未填写，请检查表单";
            }
        }
        
        // 数据过长错误
        if (lowerError.contains("data too long") || lowerError.contains("too long")) {
            String columnName = extractColumnName(errorMessage);
            if (columnName != null) {
                return "数据长度超限：字段 '" + columnName + "' 的数据过长，请缩短内容";
            } else {
                return "数据长度超限：某些字段的数据过长，请缩短内容";
            }
        }
        
        // 数据类型错误
        if (lowerError.contains("incorrect") && (lowerError.contains("value") || lowerError.contains("type"))) {
            return "数据格式错误：请检查数据类型是否正确（如数字、日期格式等）";
        }
        
        // 超出范围错误
        if (lowerError.contains("out of range") || lowerError.contains("value out of range")) {
            return "数值超出范围：请检查数字字段的值是否在允许的范围内";
        }
        
        // 权限错误
        if (lowerError.contains("access denied") || lowerError.contains("permission")) {
            return "权限不足：您没有插入数据的权限";
        }
        
        // 表不存在错误
        if (lowerError.contains("table") && lowerError.contains("doesn't exist")) {
            return "表不存在：目标数据表不存在或已被删除";
        }
        
        // 连接错误
        if (lowerError.contains("connection") || lowerError.contains("timeout")) {
            return "数据库连接失败：请稍后重试";
        }
        
        // 默认情况
        return "数据插入失败：" + errorMessage;
    }
    
    /**
     * 从错误信息中提取键名
     */
    private String extractKeyName(String errorMessage) {
        try {
            if (errorMessage.contains("for key '")) {
                int start = errorMessage.indexOf("for key '") + 9;
                int end = errorMessage.indexOf("'", start);
                if (end > start) {
                    return errorMessage.substring(start, end);
                }
            }
        } catch (Exception e) {
            // 忽略提取错误
        }
        return "唯一约束";
    }
    
    /**
     * 从错误信息中提取列名
     */
    private String extractColumnName(String errorMessage) {
        try {
            if (errorMessage.contains("Column '")) {
                int start = errorMessage.indexOf("Column '") + 8;
                int end = errorMessage.indexOf("'", start);
                if (end > start) {
                    return errorMessage.substring(start, end);
                }
            }
            // 尝试其他模式
            if (errorMessage.contains("column '")) {
                int start = errorMessage.indexOf("column '") + 8;
                int end = errorMessage.indexOf("'", start);
                if (end > start) {
                    return errorMessage.substring(start, end);
                }
            }
        } catch (Exception e) {
            // 忽略提取错误
        }
        return null;
    }
} 