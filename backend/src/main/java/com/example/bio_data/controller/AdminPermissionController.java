package com.example.bio_data.controller;

import com.example.bio_data.service.AdminPermissionService;
import com.example.bio_data.service.DatabaseService;
import com.example.bio_data.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * 管理员权限管理控制器
 * 提供管理员对内部用户权限管理的API接口
 */
@RestController
@RequestMapping("/api/admin/permissions")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081", "http://localhost:5173"})
public class AdminPermissionController {

    private static final Logger logger = LoggerFactory.getLogger(AdminPermissionController.class);

    @Autowired
    private AdminPermissionService adminPermissionService;
    
    @Autowired
    private DatabaseService databaseService;
    
    @Autowired
    private PermissionService permissionService;

    /**
     * 为内部用户授权表写权限
     */
    @PostMapping("/grant")
    public ResponseEntity<Map<String, Object>> grantTableWriteAccess(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            Long internalUserId = Long.valueOf(request.get("internalUserId").toString());
            String databaseName = request.get("databaseName").toString();
            String tableName = request.get("tableName").toString();
            
            // 处理过期时间（可选）
            LocalDateTime expiresAt = null;
            if (request.containsKey("expiresAt") && request.get("expiresAt") != null) {
                String expiresAtStr = request.get("expiresAt").toString();
                if (!expiresAtStr.isEmpty()) {
                    expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
            
            Map<String, Object> result = adminPermissionService.grantTableWriteAccess(adminId, internalUserId, databaseName, tableName, expiresAt);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("授权表写权限失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "授权失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 撤销用户的表写权限
     */
    @PostMapping("/revoke")
    public ResponseEntity<Map<String, Object>> revokeTableWriteAccess(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            Long internalUserId = Long.valueOf(request.get("internalUserId").toString());
            String databaseName = request.get("databaseName").toString();
            String tableName = request.get("tableName").toString();
            
            Map<String, Object> result = adminPermissionService.revokeTableWriteAccess(adminId, internalUserId, databaseName, tableName);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("撤销表写权限失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "撤销失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取所有权限授权记录
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllPermissions(@RequestParam Long adminId) {
        try {
            Map<String, Object> result = adminPermissionService.getAllPermissions(adminId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取权限信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "获取权限信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取特定用户的权限记录
     */
    @GetMapping("/user/{internalUserId}")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @PathVariable Long internalUserId,
            @RequestParam Long adminId) {
        try {
            Map<String, Object> result = adminPermissionService.getUserPermissions(adminId, internalUserId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取用户权限信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "获取用户权限信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 创建内部用户
     */
    @PostMapping("/create-internal-user")
    public ResponseEntity<Map<String, Object>> createInternalUser(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            String username = request.get("username").toString();
            String password = request.get("password").toString();
            
            Map<String, Object> result = adminPermissionService.createInternalUser(adminId, username, password);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("创建内部用户失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "创建内部用户失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取所有内部用户列表
     */
    @GetMapping("/internal-users")
    public ResponseEntity<Map<String, Object>> getAllInternalUsers(@RequestParam Long adminId) {
        try {
            Map<String, Object> result = adminPermissionService.getAllInternalUsers(adminId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取内部用户列表失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "获取内部用户列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量授权权限
     */
    @PostMapping("/grant-batch")
    public ResponseEntity<Map<String, Object>> grantBatchTableWriteAccess(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            Long internalUserId = Long.valueOf(request.get("internalUserId").toString());
            
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, String>> tables = (java.util.List<Map<String, String>>) request.get("tables");
            
            // 处理过期时间（可选）
            LocalDateTime expiresAt = null;
            if (request.containsKey("expiresAt") && request.get("expiresAt") != null) {
                String expiresAtStr = request.get("expiresAt").toString();
                if (!expiresAtStr.isEmpty()) {
                    expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
            
            int successCount = 0;
            int failCount = 0;
            StringBuilder errors = new StringBuilder();
            
            for (Map<String, String> table : tables) {
                String databaseName = table.get("databaseName");
                String tableName = table.get("tableName");
                
                Map<String, Object> result = adminPermissionService.grantTableWriteAccess(adminId, internalUserId, databaseName, tableName, expiresAt);
                if ((Boolean) result.get("success")) {
                    successCount++;
                } else {
                    failCount++;
                    errors.append(databaseName).append(".").append(tableName).append(": ").append(result.get("error")).append("; ");
                }
            }
            
            Map<String, Object> response = Map.of(
                "success", failCount == 0,
                "successCount", successCount,
                "failCount", failCount,
                "message", String.format("成功授权 %d 个表，失败 %d 个", successCount, failCount),
                "errors", failCount > 0 ? errors.toString() : ""
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("批量授权表写权限失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "批量授权失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 检查权限状态
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPermission(
            @RequestParam Long userId,
            @RequestParam String databaseName,
            @RequestParam String tableName) {
        try {
            // 这个接口可以被任何用户使用来检查自己的权限状态
            // 不需要管理员权限验证
            
            // 调用PermissionService检查权限
            boolean hasAccess = permissionService.hasPermissionToModifyTable(userId, databaseName, tableName);
            
            Map<String, Object> result = Map.of(
                "success", true,
                "hasAccess", hasAccess,
                "databaseName", databaseName,
                "tableName", tableName
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("检查权限状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "检查权限状态失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取所有可用数据库列表（仅管理员）
     */
    @GetMapping("/databases")
    public ResponseEntity<Map<String, Object>> getAllDatabases(@RequestParam Long adminId) {
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "权限不足，只有管理员可以获取数据库列表"
                ));
            }
            
            List<Map<String, Object>> databases = databaseService.getAllDatabases("login");
            
            // 过滤掉login数据库，因为它不需要授权
            List<Map<String, Object>> filteredDatabases = databases.stream()
                .filter(db -> !"login".equals(db.get("name")))
                .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> result = Map.of(
                "success", true,
                "databases", filteredDatabases
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取数据库列表失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "获取数据库列表失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 获取指定数据库的所有表列表（仅管理员）
     */
    @GetMapping("/databases/{databaseName}/tables")
    public ResponseEntity<Map<String, Object>> getTablesInDatabase(
            @PathVariable String databaseName,
            @RequestParam Long adminId) {
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "权限不足，只有管理员可以获取表列表"
                ));
            }
            
            List<Map<String, Object>> tables = databaseService.getAllTables(databaseName);
            
            // 简化表信息，只返回必要的字段
            List<Map<String, Object>> simplifiedTables = tables.stream()
                .map(table -> {
                    Map<String, Object> simplifiedTable = new HashMap<>();
                    simplifiedTable.put("name", table.get("TABLE_NAME"));
                    simplifiedTable.put("tableName", table.get("TABLE_NAME"));
                    simplifiedTable.put("comment", table.get("TABLE_COMMENT"));
                    simplifiedTable.put("rows", table.get("TABLE_ROWS"));
                    return simplifiedTable;
                })
                .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> result = Map.of(
                "success", true,
                "databaseName", databaseName,
                "tables", simplifiedTables
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("获取表列表失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "获取表列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 为内部用户授权数据库级写权限
     */
    @PostMapping("/grant-database")
    public ResponseEntity<Map<String, Object>> grantDatabaseWriteAccess(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            Long internalUserId = Long.valueOf(request.get("internalUserId").toString());
            String databaseName = request.get("databaseName").toString();
            
            // 处理过期时间（可选）
            LocalDateTime expiresAt = null;
            if (request.containsKey("expiresAt") && request.get("expiresAt") != null) {
                String expiresAtStr = request.get("expiresAt").toString();
                if (!expiresAtStr.isEmpty()) {
                    expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
            
            Map<String, Object> result = adminPermissionService.grantDatabaseWriteAccess(adminId, internalUserId, databaseName, expiresAt);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("授权数据库级权限失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "授权数据库级权限失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 撤销内部用户的数据库级写权限
     */
    @DeleteMapping("/revoke-database")
    public ResponseEntity<Map<String, Object>> revokeDatabaseWriteAccess(@RequestBody Map<String, Object> request) {
        try {
            Long adminId = Long.valueOf(request.get("adminId").toString());
            Long internalUserId = Long.valueOf(request.get("internalUserId").toString());
            String databaseName = request.get("databaseName").toString();
            
            Map<String, Object> result = adminPermissionService.revokeDatabaseWriteAccess(adminId, internalUserId, databaseName);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("撤销数据库级权限失败", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "撤销数据库级权限失败: " + e.getMessage()
            ));
        }
    }
}
