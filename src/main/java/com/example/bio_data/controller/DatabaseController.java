package com.example.bio_data.controller;

import com.example.bio_data.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081"})
public class DatabaseController {

    @Autowired
    private DatabaseService databaseService;

    /**
     * 获取数据库统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        try {
            Map<String, Object> stats = databaseService.getDatabaseStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取数据库统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有表信息
     */
    @GetMapping("/tables")
    public ResponseEntity<List<Map<String, Object>>> getAllTables() {
        try {
            List<Map<String, Object>> tables = databaseService.getAllTables();
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取指定表的列信息
     */
    @GetMapping("/tables/{tableName}/columns")
    public ResponseEntity<List<Map<String, Object>>> getTableColumns(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> columns = databaseService.getTableColumns(tableName);
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
            @RequestParam(defaultValue = "100") int limit) {
        try {
            if (limit > 1000) limit = 1000; // 限制最大返回行数
            List<Map<String, Object>> data = databaseService.getTableData(tableName, limit);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取表的索引信息
     */
    @GetMapping("/tables/{tableName}/indexes")
    public ResponseEntity<List<Map<String, Object>>> getTableIndexes(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> indexes = databaseService.getTableIndexes(tableName);
            return ResponseEntity.ok(indexes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 执行自定义SQL查询
     */
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody Map<String, String> request) {
        try {
            String sql = request.get("sql");
            int limit = Integer.parseInt(request.getOrDefault("limit", "100"));
            
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "SQL语句不能为空"));
            }
            
            if (limit > 1000) limit = 1000; // 限制最大返回行数
            
            List<Map<String, Object>> result = databaseService.executeQuery(sql, limit);
            return ResponseEntity.ok(Map.of(
                "data", result,
                "count", result.size(),
                "sql", sql
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "查询执行失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            databaseService.getDatabaseStats();
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "database", "Connected",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "database", "Disconnected",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
} 