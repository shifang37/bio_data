package com.example.bio_data.controller;

import com.example.bio_data.service.KnowledgeGraphService;
import com.example.bio_data.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-graph")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081"})
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    
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
    private ResponseEntity<?> validatePermission(Long userId, String userType, String operation) {
        if (userId == null || userType == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户未登录"));
        }
        
        Map<String, Object> permissionResult = permissionService.validatePermission(userId, userType, "default", operation);
        
        if (!(Boolean) permissionResult.get("success")) {
            return ResponseEntity.status(403).body(Map.of("error", permissionResult.get("error")));
        }
        
        return null; // 权限验证通过
    }

    /**
     * 解析JSON格式的知识图谱文件
     */
    @PostMapping("/parse/json")
    public ResponseEntity<?> parseJsonFile(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            String jsonContent = (String) request.get("jsonContent");
            String fileName = (String) request.get("fileName");
            
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "JSON内容不能为空"));
            }
            
            Map<String, Object> result = knowledgeGraphService.parseJsonKnowledgeGraph(jsonContent, fileName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "JSON文件解析成功",
                "data", result
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "JSON文件解析失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 解析CSV格式的知识图谱文件
     */
    @PostMapping("/parse/csv")
    public ResponseEntity<?> parseCsvFile(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> csvData = (List<Map<String, Object>>) request.get("csvData");
            String fileName = (String) request.get("fileName");
            
            if (csvData == null || csvData.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "CSV数据不能为空"));
            }
            
            Map<String, Object> result = knowledgeGraphService.parseCsvKnowledgeGraph(csvData, fileName);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "CSV文件解析成功",
                "data", result
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "CSV文件解析失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取知识图谱统计信息
     */
    @PostMapping("/statistics")
    public ResponseEntity<?> getKnowledgeGraphStatistics(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            Map<String, Object> statistics = knowledgeGraphService.calculateStatistics(graphData);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "statistics", statistics
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "统计信息计算失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 过滤知识图谱数据
     */
    @PostMapping("/filter")
    public ResponseEntity<?> filterKnowledgeGraph(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) request.get("filters");
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            if (filters == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "过滤条件不能为空"));
            }
            
            Map<String, Object> filteredData = knowledgeGraphService.filterKnowledgeGraph(graphData, filters);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", filteredData
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据过滤失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 搜索知识图谱中的节点
     */
    @PostMapping("/search")
    public ResponseEntity<?> searchNodes(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            String searchTerm = (String) request.get("searchTerm");
            String searchType = (String) request.get("searchType"); // "name", "id", "properties", "all"
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "搜索关键词不能为空"));
            }
            
            List<Map<String, Object>> searchResults = knowledgeGraphService.searchNodes(graphData, searchTerm, searchType);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "results", searchResults,
                "count", searchResults.size(),
                "searchTerm", searchTerm,
                "searchType", searchType != null ? searchType : "all"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "节点搜索失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取节点的邻居节点
     */
    @PostMapping("/neighbors")
    public ResponseEntity<?> getNodeNeighbors(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            String nodeId = (String) request.get("nodeId");
            Integer depth = (Integer) request.get("depth"); // 邻居深度，默认为1
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            if (nodeId == null || nodeId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "节点ID不能为空"));
            }
            
            if (depth == null || depth < 1) {
                depth = 1;
            }
            
            Map<String, Object> neighbors = knowledgeGraphService.getNodeNeighbors(graphData, nodeId, depth);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", neighbors,
                "nodeId", nodeId,
                "depth", depth
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "获取邻居节点失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 验证知识图谱数据格式
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateKnowledgeGraph(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            String dataType = (String) request.get("dataType"); // "json" or "csv"
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            Map<String, Object> validation = knowledgeGraphService.validateKnowledgeGraph(graphData, dataType);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "validation", validation
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据验证失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 导出知识图谱数据
     */
    @PostMapping("/export")
    public ResponseEntity<?> exportKnowledgeGraph(@RequestBody Map<String, Object> request) {
        try {
            Long userId = extractUserId(request);
            String userType = (String) request.get("userType");
            
            // 权限验证
            ResponseEntity<?> permissionCheck = validatePermission(userId, userType, "read");
            if (permissionCheck != null) {
                return permissionCheck;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphData = (Map<String, Object>) request.get("graphData");
            String exportFormat = (String) request.get("exportFormat"); // "json", "csv", "graphml"
            
            if (graphData == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "图谱数据不能为空"));
            }
            
            if (exportFormat == null || exportFormat.trim().isEmpty()) {
                exportFormat = "json";
            }
            
            Map<String, Object> exportResult = knowledgeGraphService.exportKnowledgeGraph(graphData, exportFormat);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", exportResult,
                "format", exportFormat
            ));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "数据导出失败: " + e.getMessage()
            ));
        }
    }
}