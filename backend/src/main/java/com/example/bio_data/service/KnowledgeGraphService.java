package com.example.bio_data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析JSON格式的知识图谱
     */
    public Map<String, Object> parseJsonKnowledgeGraph(String jsonContent, String fileName) throws Exception {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("dataType", "json");
            result.put("parseTime", System.currentTimeMillis());
            
            // 检查是否是标准的图谱格式（包含nodes和links/edges）
            if (jsonNode.has("nodes") && (jsonNode.has("links") || jsonNode.has("edges"))) {
                result.put("format", "standard_graph");
                result.put("nodes", parseNodes(jsonNode.get("nodes")));
                
                JsonNode linksNode = jsonNode.has("links") ? jsonNode.get("links") : jsonNode.get("edges");
                result.put("links", parseLinks(linksNode));
            } else {
                // 尝试自动检测其他格式
                result.put("format", "custom");
                @SuppressWarnings("unchecked")
                Map<String, Object> rawData = objectMapper.convertValue(jsonNode, Map.class);
                result.put("rawData", rawData);
            }
            
            // 计算基本统计信息
            Map<String, Object> stats = calculateBasicStats(result);
            result.put("statistics", stats);
            
            return result;
            
        } catch (Exception e) {
            throw new Exception("JSON解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析CSV格式的知识图谱
     */
    public Map<String, Object> parseCsvKnowledgeGraph(List<Map<String, Object>> csvData, String fileName) throws Exception {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("dataType", "csv");
            result.put("parseTime", System.currentTimeMillis());
            
            if (csvData.isEmpty()) {
                throw new Exception("CSV数据为空");
            }
            
            // 检测CSV格式类型
            Map<String, Object> firstRow = csvData.get(0);
            Set<String> columns = firstRow.keySet();
            
            if (isEdgeListFormat(columns)) {
                // 边列表格式（source, target, relation等）
                result.put("format", "edge_list");
                Map<String, Object> graphData = convertEdgeListToGraph(csvData);
                result.put("nodes", graphData.get("nodes"));
                result.put("links", graphData.get("links"));
            } else if (isNodeListFormat(columns)) {
                // 节点列表格式
                result.put("format", "node_list");
                result.put("nodes", convertToNodes(csvData));
                result.put("links", new ArrayList<>());
            } else {
                // 通用表格格式
                result.put("format", "table");
                result.put("data", csvData);
            }
            
            // 计算基本统计信息
            Map<String, Object> stats = calculateBasicStats(result);
            result.put("statistics", stats);
            
            return result;
            
        } catch (Exception e) {
            throw new Exception("CSV解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算知识图谱统计信息
     */
    public Map<String, Object> calculateStatistics(Map<String, Object> graphData) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
            
            if (nodes != null) {
                stats.put("nodeCount", nodes.size());
                
                // 统计节点类型
                Map<String, Integer> nodeTypes = new HashMap<>();
                Map<String, Integer> nodeLabelCounts = new HashMap<>();
                
                for (Map<String, Object> node : nodes) {
                    String type = (String) node.get("type");
                    String label = (String) node.get("label");
                    
                    if (type != null) {
                        nodeTypes.put(type, nodeTypes.getOrDefault(type, 0) + 1);
                    }
                    if (label != null) {
                        nodeLabelCounts.put(label, nodeLabelCounts.getOrDefault(label, 0) + 1);
                    }
                }
                
                stats.put("nodeTypes", nodeTypes);
                stats.put("nodeLabelCounts", nodeLabelCounts);
            } else {
                stats.put("nodeCount", 0);
            }
            
            if (links != null) {
                stats.put("linkCount", links.size());
                
                // 统计关系类型
                Map<String, Integer> relationTypes = new HashMap<>();
                Map<String, Integer> linkTypes = new HashMap<>();
                
                for (Map<String, Object> link : links) {
                    String relation = (String) link.get("relation");
                    String type = (String) link.get("type");
                    
                    if (relation != null) {
                        relationTypes.put(relation, relationTypes.getOrDefault(relation, 0) + 1);
                    }
                    if (type != null) {
                        linkTypes.put(type, linkTypes.getOrDefault(type, 0) + 1);
                    }
                }
                
                stats.put("relationTypes", relationTypes);
                stats.put("linkTypes", linkTypes);
                
                // 计算网络密度（如果有节点数据）
                if (nodes != null && !nodes.isEmpty()) {
                    int nodeCount = nodes.size();
                    int linkCount = links.size();
                    double maxPossibleLinks = nodeCount * (nodeCount - 1.0) / 2.0;
                    double density = maxPossibleLinks > 0 ? linkCount / maxPossibleLinks : 0.0;
                    stats.put("networkDensity", Math.round(density * 10000.0) / 10000.0);
                }
            } else {
                stats.put("linkCount", 0);
            }
            
            // 计算度分布（如果有链接数据）
            if (links != null && nodes != null) {
                Map<String, Integer> degreeMap = calculateDegreeDistribution(nodes, links);
                stats.put("degreeDistribution", degreeMap);
                
                // 找出度最高的节点
                if (!degreeMap.isEmpty()) {
                    String maxDegreeNode = degreeMap.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                    stats.put("maxDegreeNode", maxDegreeNode);
                    stats.put("maxDegree", degreeMap.getOrDefault(maxDegreeNode, 0));
                }
            }
            
            stats.put("calculatedAt", System.currentTimeMillis());
            
        } catch (Exception e) {
            stats.put("error", "统计计算失败: " + e.getMessage());
        }
        
        return stats;
    }

    /**
     * 过滤知识图谱数据
     */
    public Map<String, Object> filterKnowledgeGraph(Map<String, Object> graphData, Map<String, Object> filters) {
        Map<String, Object> filteredData = new HashMap<>(graphData);
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
            
            Set<String> filteredNodeIds = new HashSet<>();
            
            // 过滤节点
            if (nodes != null) {
                List<Map<String, Object>> filteredNodes = nodes.stream()
                    .filter(node -> matchesNodeFilter(node, filters))
                    .collect(Collectors.toList());
                
                filteredNodes.forEach(node -> {
                    String nodeId = (String) node.get("id");
                    if (nodeId != null) {
                        filteredNodeIds.add(nodeId);
                    }
                });
                
                filteredData.put("nodes", filteredNodes);
            }
            
            // 过滤链接（只保留两端节点都存在的链接）
            if (links != null) {
                List<Map<String, Object>> filteredLinks = links.stream()
                    .filter(link -> {
                        String source = (String) link.get("source");
                        String target = (String) link.get("target");
                        return filteredNodeIds.contains(source) && filteredNodeIds.contains(target) 
                               && matchesLinkFilter(link, filters);
                    })
                    .collect(Collectors.toList());
                
                filteredData.put("links", filteredLinks);
            }
            
            filteredData.put("filterApplied", true);
            filteredData.put("filteredAt", System.currentTimeMillis());
            
        } catch (Exception e) {
            filteredData.put("filterError", "过滤失败: " + e.getMessage());
        }
        
        return filteredData;
    }

    /**
     * 搜索节点
     */
    public List<Map<String, Object>> searchNodes(Map<String, Object> graphData, String searchTerm, String searchType) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
            
            if (nodes == null) {
                return results;
            }
            
            String lowerSearchTerm = searchTerm.toLowerCase();
            
            for (Map<String, Object> node : nodes) {
                boolean matches = false;
                
                switch (searchType != null ? searchType : "all") {
                    case "name":
                        String name = (String) node.get("name");
                        matches = name != null && name.toLowerCase().contains(lowerSearchTerm);
                        break;
                    case "id":
                        String id = (String) node.get("id");
                        matches = id != null && id.toLowerCase().contains(lowerSearchTerm);
                        break;
                    case "properties":
                        matches = searchInProperties(node, lowerSearchTerm);
                        break;
                    case "all":
                    default:
                        String nodeName = (String) node.get("name");
                        String nodeId = (String) node.get("id");
                        matches = (nodeName != null && nodeName.toLowerCase().contains(lowerSearchTerm)) ||
                                 (nodeId != null && nodeId.toLowerCase().contains(lowerSearchTerm)) ||
                                 searchInProperties(node, lowerSearchTerm);
                        break;
                }
                
                if (matches) {
                    results.add(node);
                }
            }
            
        } catch (Exception e) {
            // 记录错误但不抛出异常，返回空结果
        }
        
        return results;
    }

    /**
     * 获取节点的邻居节点
     */
    public Map<String, Object> getNodeNeighbors(Map<String, Object> graphData, String nodeId, int depth) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
            
            if (nodes == null || links == null) {
                result.put("nodes", new ArrayList<>());
                result.put("links", new ArrayList<>());
                return result;
            }
            
            Set<String> neighborIds = new HashSet<>();
            Set<String> currentLevel = new HashSet<>();
            currentLevel.add(nodeId);
            
            // 递归查找指定深度的邻居
            for (int level = 0; level < depth; level++) {
                Set<String> nextLevel = new HashSet<>();
                
                for (String currentNodeId : currentLevel) {
                    for (Map<String, Object> link : links) {
                        String source = (String) link.get("source");
                        String target = (String) link.get("target");
                        
                        if (currentNodeId.equals(source)) {
                            nextLevel.add(target);
                            neighborIds.add(target);
                        } else if (currentNodeId.equals(target)) {
                            nextLevel.add(source);
                            neighborIds.add(source);
                        }
                    }
                }
                
                currentLevel = nextLevel;
            }
            
            // 添加原始节点
            neighborIds.add(nodeId);
            
            // 过滤节点
            List<Map<String, Object>> neighborNodes = nodes.stream()
                .filter(node -> neighborIds.contains(node.get("id")))
                .collect(Collectors.toList());
            
            // 过滤链接
            List<Map<String, Object>> neighborLinks = links.stream()
                .filter(link -> {
                    String source = (String) link.get("source");
                    String target = (String) link.get("target");
                    return neighborIds.contains(source) && neighborIds.contains(target);
                })
                .collect(Collectors.toList());
            
            result.put("nodes", neighborNodes);
            result.put("links", neighborLinks);
            result.put("centerNodeId", nodeId);
            result.put("depth", depth);
            result.put("neighborCount", neighborIds.size() - 1); // 排除中心节点
            
        } catch (Exception e) {
            result.put("error", "获取邻居节点失败: " + e.getMessage());
            result.put("nodes", new ArrayList<>());
            result.put("links", new ArrayList<>());
        }
        
        return result;
    }

    /**
     * 验证知识图谱数据格式
     */
    public Map<String, Object> validateKnowledgeGraph(Map<String, Object> graphData, String dataType) {
        Map<String, Object> validation = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            if ("json".equals(dataType)) {
                validateJsonFormat(graphData, errors, warnings);
            } else if ("csv".equals(dataType)) {
                validateCsvFormat(graphData, errors, warnings);
            }
            
            validation.put("isValid", errors.isEmpty());
            validation.put("errors", errors);
            validation.put("warnings", warnings);
            validation.put("errorCount", errors.size());
            validation.put("warningCount", warnings.size());
            validation.put("validatedAt", System.currentTimeMillis());
            
        } catch (Exception e) {
            errors.add("验证过程中发生错误: " + e.getMessage());
            validation.put("isValid", false);
            validation.put("errors", errors);
            validation.put("warnings", warnings);
        }
        
        return validation;
    }

    /**
     * 导出知识图谱数据
     */
    public Map<String, Object> exportKnowledgeGraph(Map<String, Object> graphData, String exportFormat) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        try {
            switch (exportFormat.toLowerCase()) {
                case "json":
                    result.put("content", objectMapper.writeValueAsString(graphData));
                    result.put("mimeType", "application/json");
                    result.put("extension", ".json");
                    break;
                case "csv":
                    result.put("content", convertToCSV(graphData));
                    result.put("mimeType", "text/csv");
                    result.put("extension", ".csv");
                    break;
                case "graphml":
                    result.put("content", convertToGraphML(graphData));
                    result.put("mimeType", "application/xml");
                    result.put("extension", ".graphml");
                    break;
                default:
                    throw new Exception("不支持的导出格式: " + exportFormat);
            }
            
            result.put("format", exportFormat);
            result.put("exportedAt", System.currentTimeMillis());
            
        } catch (Exception e) {
            throw new Exception("导出失败: " + e.getMessage(), e);
        }
        
        return result;
    }

    // ========== 私有辅助方法 ==========

    private List<Map<String, Object>> parseNodes(JsonNode nodesNode) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        
        if (nodesNode.isArray()) {
            for (JsonNode node : nodesNode) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nodeMap = objectMapper.convertValue(node, Map.class);
                nodes.add(nodeMap);
            }
        }
        
        return nodes;
    }

    private List<Map<String, Object>> parseLinks(JsonNode linksNode) {
        List<Map<String, Object>> links = new ArrayList<>();
        
        if (linksNode.isArray()) {
            for (JsonNode link : linksNode) {
                @SuppressWarnings("unchecked")
                Map<String, Object> linkMap = objectMapper.convertValue(link, Map.class);
                links.add(linkMap);
            }
        }
        
        return links;
    }

    private Map<String, Object> calculateBasicStats(Map<String, Object> result) {
        Map<String, Object> stats = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) result.get("links");
        
        stats.put("nodeCount", nodes != null ? nodes.size() : 0);
        stats.put("linkCount", links != null ? links.size() : 0);
        
        return stats;
    }

    private boolean isEdgeListFormat(Set<String> columns) {
        return columns.contains("source") && columns.contains("target") ||
               columns.contains("nodes_a") && columns.contains("nodes_b") ||
               columns.contains("from") && columns.contains("to");
    }

    private boolean isNodeListFormat(Set<String> columns) {
        return columns.contains("id") || columns.contains("node_id") || columns.contains("name");
    }

    private Map<String, Object> convertEdgeListToGraph(List<Map<String, Object>> csvData) {
        Map<String, Object> result = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();
        List<Map<String, Object>> links = new ArrayList<>();
        
        // 确定列名映射
        Map<String, Object> firstRow = csvData.get(0);
        String sourceCol = getColumnName(firstRow, Arrays.asList("source", "nodes_a", "from"));
        String targetCol = getColumnName(firstRow, Arrays.asList("target", "nodes_b", "to"));
        String relationCol = getColumnName(firstRow, Arrays.asList("relation", "type", "edge_type"));
        
        for (Map<String, Object> row : csvData) {
            String source = (String) row.get(sourceCol);
            String target = (String) row.get(targetCol);
            
            if (source != null && target != null) {
                nodeIds.add(source);
                nodeIds.add(target);
                
                Map<String, Object> link = new HashMap<>();
                link.put("source", source);
                link.put("target", target);
                
                if (relationCol != null) {
                    link.put("relation", row.get(relationCol));
                    link.put("type", row.get(relationCol));
                }
                
                // 添加其他属性
                Map<String, Object> properties = new HashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals(sourceCol) && !key.equals(targetCol) && !key.equals(relationCol)) {
                        properties.put(key, entry.getValue());
                    }
                }
                if (!properties.isEmpty()) {
                    link.put("properties", properties);
                }
                
                links.add(link);
            }
        }
        
        // 创建节点列表
        List<Map<String, Object>> nodes = nodeIds.stream()
            .map(nodeId -> {
                Map<String, Object> node = new HashMap<>();
                node.put("id", nodeId);
                node.put("name", nodeId);
                return node;
            })
            .collect(Collectors.toList());
        
        result.put("nodes", nodes);
        result.put("links", links);
        
        return result;
    }

    private List<Map<String, Object>> convertToNodes(List<Map<String, Object>> csvData) {
        return csvData.stream()
            .map(row -> {
                Map<String, Object> node = new HashMap<>(row);
                
                // 确保有id字段
                if (!node.containsKey("id")) {
                    if (node.containsKey("node_id")) {
                        node.put("id", node.get("node_id"));
                    } else if (node.containsKey("name")) {
                        node.put("id", node.get("name"));
                    }
                }
                
                return node;
            })
            .collect(Collectors.toList());
    }

    private String getColumnName(Map<String, Object> row, List<String> candidates) {
        for (String candidate : candidates) {
            if (row.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Integer> calculateDegreeDistribution(List<Map<String, Object>> nodes, List<Map<String, Object>> links) {
        Map<String, Integer> degreeMap = new HashMap<>();
        
        // 初始化所有节点的度为0
        for (Map<String, Object> node : nodes) {
            String nodeId = (String) node.get("id");
            if (nodeId != null) {
                degreeMap.put(nodeId, 0);
            }
        }
        
        // 计算每个节点的度
        for (Map<String, Object> link : links) {
            String source = (String) link.get("source");
            String target = (String) link.get("target");
            
            if (source != null) {
                degreeMap.put(source, degreeMap.getOrDefault(source, 0) + 1);
            }
            if (target != null) {
                degreeMap.put(target, degreeMap.getOrDefault(target, 0) + 1);
            }
        }
        
        return degreeMap;
    }

    private boolean matchesNodeFilter(Map<String, Object> node, Map<String, Object> filters) {
        // 实现节点过滤逻辑
        @SuppressWarnings("unchecked")
        List<String> nodeTypes = (List<String>) filters.get("nodeTypes");
        @SuppressWarnings("unchecked")
        List<String> nodeLabels = (List<String>) filters.get("nodeLabels");
        String searchText = (String) filters.get("searchText");
        
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            String nodeType = (String) node.get("type");
            String nodeLabel = (String) node.get("label");
            if (nodeType == null || (!nodeTypes.contains(nodeType) && !nodeTypes.contains(nodeLabel))) {
                return false;
            }
        }
        
        if (nodeLabels != null && !nodeLabels.isEmpty()) {
            String nodeLabel = (String) node.get("label");
            if (nodeLabel == null || !nodeLabels.contains(nodeLabel)) {
                return false;
            }
        }
        
        if (searchText != null && !searchText.trim().isEmpty()) {
            String lowerSearchText = searchText.toLowerCase();
            String nodeName = (String) node.get("name");
            String nodeId = (String) node.get("id");
            
            boolean matches = (nodeName != null && nodeName.toLowerCase().contains(lowerSearchText)) ||
                             (nodeId != null && nodeId.toLowerCase().contains(lowerSearchText)) ||
                             searchInProperties(node, lowerSearchText);
            
            if (!matches) {
                return false;
            }
        }
        
        return true;
    }

    private boolean matchesLinkFilter(Map<String, Object> link, Map<String, Object> filters) {
        // 实现链接过滤逻辑
        @SuppressWarnings("unchecked")
        List<String> relationTypes = (List<String>) filters.get("relationTypes");
        
        if (relationTypes != null && !relationTypes.isEmpty()) {
            String relation = (String) link.get("relation");
            String type = (String) link.get("type");
            if ((relation == null || !relationTypes.contains(relation)) && 
                (type == null || !relationTypes.contains(type))) {
                return false;
            }
        }
        
        return true;
    }

    private boolean searchInProperties(Map<String, Object> node, String searchTerm) {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) node.get("properties");
        
        if (properties != null) {
            for (Object value : properties.values()) {
                if (value != null && value.toString().toLowerCase().contains(searchTerm)) {
                    return true;
                }
            }
        }
        
        // 也搜索节点的其他字段
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if (!"properties".equals(entry.getKey()) && entry.getValue() != null) {
                if (entry.getValue().toString().toLowerCase().contains(searchTerm)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private void validateJsonFormat(Map<String, Object> graphData, List<String> errors, List<String> warnings) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
        
        if (nodes == null) {
            errors.add("缺少nodes字段");
        } else {
            // 验证节点
            Set<String> nodeIds = new HashSet<>();
            for (int i = 0; i < nodes.size(); i++) {
                Map<String, Object> node = nodes.get(i);
                String nodeId = (String) node.get("id");
                
                if (nodeId == null || nodeId.trim().isEmpty()) {
                    errors.add("节点 " + i + " 缺少id字段");
                } else if (nodeIds.contains(nodeId)) {
                    errors.add("节点ID重复: " + nodeId);
                } else {
                    nodeIds.add(nodeId);
                }
                
                if (node.get("name") == null) {
                    warnings.add("节点 " + nodeId + " 缺少name字段");
                }
            }
        }
        
        if (links != null) {
            // 验证链接
            Set<String> nodeIds = new HashSet<>();
            if (nodes != null) {
                nodes.forEach(node -> {
                    String id = (String) node.get("id");
                    if (id != null) nodeIds.add(id);
                });
            }
            
            for (int i = 0; i < links.size(); i++) {
                Map<String, Object> link = links.get(i);
                String source = (String) link.get("source");
                String target = (String) link.get("target");
                
                if (source == null || source.trim().isEmpty()) {
                    errors.add("链接 " + i + " 缺少source字段");
                } else if (!nodeIds.isEmpty() && !nodeIds.contains(source)) {
                    warnings.add("链接 " + i + " 的source节点不存在: " + source);
                }
                
                if (target == null || target.trim().isEmpty()) {
                    errors.add("链接 " + i + " 缺少target字段");
                } else if (!nodeIds.isEmpty() && !nodeIds.contains(target)) {
                    warnings.add("链接 " + i + " 的target节点不存在: " + target);
                }
            }
        }
    }

    private void validateCsvFormat(Map<String, Object> graphData, List<String> errors, List<String> warnings) {
        // 实现CSV格式验证逻辑
        String format = (String) graphData.get("format");
        
        if ("edge_list".equals(format)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
            if (links == null || links.isEmpty()) {
                errors.add("边列表格式但没有找到有效的链接数据");
            }
        } else if ("node_list".equals(format)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
            if (nodes == null || nodes.isEmpty()) {
                errors.add("节点列表格式但没有找到有效的节点数据");
            }
        }
    }

    private String convertToCSV(Map<String, Object> graphData) {
        StringBuilder csv = new StringBuilder();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
        
        if (links != null && !links.isEmpty()) {
            // 导出为边列表格式
            csv.append("source,target,relation,properties\n");
            
            for (Map<String, Object> link : links) {
                csv.append(escapeCSV((String) link.get("source"))).append(",");
                csv.append(escapeCSV((String) link.get("target"))).append(",");
                csv.append(escapeCSV((String) link.get("relation"))).append(",");
                
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) link.get("properties");
                if (properties != null) {
                    try {
                        csv.append(escapeCSV(objectMapper.writeValueAsString(properties)));
                    } catch (Exception e) {
                        csv.append("");
                    }
                } else {
                    csv.append("");
                }
                csv.append("\n");
            }
        }
        
        return csv.toString();
    }

    private String convertToGraphML(Map<String, Object> graphData) {
        StringBuilder graphml = new StringBuilder();
        
        graphml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        graphml.append("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"\n");
        graphml.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        graphml.append("         xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns\n");
        graphml.append("         http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">\n");
        
        // 定义属性
        graphml.append("  <key id=\"name\" for=\"node\" attr.name=\"name\" attr.type=\"string\"/>\n");
        graphml.append("  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n");
        graphml.append("  <key id=\"relation\" for=\"edge\" attr.name=\"relation\" attr.type=\"string\"/>\n");
        
        graphml.append("  <graph id=\"KnowledgeGraph\" edgedefault=\"directed\">\n");
        
        // 添加节点
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");
                String nodeName = (String) node.get("name");
                String nodeType = (String) node.get("type");
                
                graphml.append("    <node id=\"").append(escapeXML(nodeId)).append("\">\n");
                if (nodeName != null) {
                    graphml.append("      <data key=\"name\">").append(escapeXML(nodeName)).append("</data>\n");
                }
                if (nodeType != null) {
                    graphml.append("      <data key=\"type\">").append(escapeXML(nodeType)).append("</data>\n");
                }
                graphml.append("    </node>\n");
            }
        }
        
        // 添加边
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> links = (List<Map<String, Object>>) graphData.get("links");
        if (links != null) {
            int edgeId = 0;
            for (Map<String, Object> link : links) {
                String source = (String) link.get("source");
                String target = (String) link.get("target");
                String relation = (String) link.get("relation");
                
                graphml.append("    <edge id=\"e").append(edgeId++).append("\"");
                graphml.append(" source=\"").append(escapeXML(source)).append("\"");
                graphml.append(" target=\"").append(escapeXML(target)).append("\">\n");
                
                if (relation != null) {
                    graphml.append("      <data key=\"relation\">").append(escapeXML(relation)).append("</data>\n");
                }
                
                graphml.append("    </edge>\n");
            }
        }
        
        graphml.append("  </graph>\n");
        graphml.append("</graphml>\n");
        
        return graphml.toString();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXML(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}