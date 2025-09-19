package com.example.bio_data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    @Autowired
    private DatabaseService databaseService;

    /**
     * 导出表数据为CSV格式
     */
    public StreamingResponseBody exportTableToCsv(String dataSource, String tableName, 
                                                  Long userId, String userType, 
                                                  Integer limit) {
        return outputStream -> {
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                // 添加BOM以支持Excel正确显示中文
                outputStream.write(0xEF);
                outputStream.write(0xBB);
                outputStream.write(0xBF);
                
                // 获取表列信息
                List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
                
                // 写入CSV头部
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) header.append(",");
                    header.append("\"").append(columns.get(i).get("COLUMN_NAME")).append("\"");
                }
                writer.write(header.toString());
                writer.write("\n");
                writer.flush();
                
                // 获取表数据
                List<Map<String, Object>> data = databaseService.getTableData(dataSource, tableName, limit);
                
                // 写入数据行 - 分批处理避免内存问题
                int batchSize = 1000; // 每批处理1000行
                for (int i = 0; i < data.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, data.size());
                    List<Map<String, Object>> batch = data.subList(i, endIndex);
                    
                    for (Map<String, Object> row : batch) {
                        StringBuilder rowData = new StringBuilder();
                        for (int j = 0; j < columns.size(); j++) {
                            if (j > 0) rowData.append(",");
                            String columnName = (String) columns.get(j).get("COLUMN_NAME");
                            Object value = row.get(columnName);
                            String cellValue = (value != null) ? value.toString() : "";
                            // 转义CSV特殊字符
                            cellValue = cellValue.replace("\"", "\"\"");
                            rowData.append("\"").append(cellValue).append("\"");
                        }
                        writer.write(rowData.toString());
                        writer.write("\n");
                    }
                    
                    // 每处理一批后刷新输出流
                    writer.flush();
                }
                
            } catch (Exception e) {
                throw new RuntimeException("CSV导出失败: " + e.getMessage(), e);
            } finally {
                if (writer != null) {
                    try {
                        writer.flush();
                    } catch (Exception e) {
                        // 忽略刷新时的异常
                    }
                }
            }
        };
    }

    /**
     * 导出表数据为Excel格式
     */
    public StreamingResponseBody exportTableToExcel(String dataSource, String tableName, 
                                                    Long userId, String userType, 
                                                    Integer limit) {
        return outputStream -> {
            Workbook workbook = null;
            try {
                // 使用SXSSFWorkbook支持大数据量导出，在内存中只保留100行
                workbook = new SXSSFWorkbook(100);
                Sheet sheet = workbook.createSheet(tableName);
                
                // 对于SXSSFWorkbook，需要跟踪列才能自动调整列宽
                if (sheet instanceof SXSSFSheet) {
                    ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
                }
                
                // 创建样式
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                
                // 获取表列信息
                List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
                
                // 创建表头
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue((String) columns.get(i).get("COLUMN_NAME"));
                    cell.setCellStyle(headerStyle);
                }
                
                // 获取表数据
                List<Map<String, Object>> data = databaseService.getTableData(dataSource, tableName, limit);
                
                // 写入数据行 - SXSSFWorkbook自动管理内存
                int rowIndex = 1;
                
                for (Map<String, Object> row : data) {
                    Row excelRow = sheet.createRow(rowIndex++);
                    for (int j = 0; j < columns.size(); j++) {
                        Cell cell = excelRow.createCell(j);
                        String columnName = (String) columns.get(j).get("COLUMN_NAME");
                        Object value = row.get(columnName);
                        
                        if (value != null) {
                            if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else if (value instanceof java.util.Date) {
                                cell.setCellValue((java.util.Date) value);
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                    }
                    
                    // 每1000行刷新一次输出流
                    if (rowIndex % 1000 == 0) {
                        try {
                            outputStream.flush();
                        } catch (Exception e) {
                            // 如果连接已断开，停止处理
                            throw new RuntimeException("连接已断开，导出中断", e);
                        }
                    }
                }
                
                // 自动调整列宽（只对前50列进行调整，避免性能问题）
                int maxColumns = Math.min(columns.size(), 50);
                for (int i = 0; i < maxColumns; i++) {
                    try {
                        sheet.autoSizeColumn(i);
                    } catch (Exception e) {
                        // 如果自动调整列宽失败，设置默认列宽
                        sheet.setColumnWidth(i, 4000); // 默认列宽
                    }
                }
                
                // 写入工作簿
                workbook.write(outputStream);
                outputStream.flush();
                
            } catch (Exception e) {
                throw new RuntimeException("Excel导出失败: " + e.getMessage(), e);
            } finally {
                if (workbook != null) {
                    try {
                        workbook.close();
                    } catch (Exception e) {
                        // 忽略关闭时的异常
                    }
                }
            }
        };
    }

    /**
     * 获取导出文件的基本信息
     */
    public Map<String, Object> getExportInfo(String dataSource, String tableName, 
                                             Long userId, String userType) {
        try {
            // 获取表列信息
            List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
            
            // 获取数据行数
            Integer totalRows = databaseService.getTableRowCount(dataSource, tableName);
            
            return Map.of(
                "tableName", tableName,
                "dataSource", dataSource,
                "columnCount", columns.size(),
                "totalRows", totalRows,
                "columns", columns.stream().map(col -> Map.of(
                    "name", col.get("COLUMN_NAME"),
                    "type", col.get("DATA_TYPE"),
                    "nullable", col.get("IS_NULLABLE")
                )).toList()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取导出信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出搜索结果为CSV格式
     */
    public StreamingResponseBody exportSearchResultToCsv(String dataSource, String tableName, 
                                                         Long userId, String userType, 
                                                         String searchValue, String searchType, Integer limit) {
        return outputStream -> {
            OutputStreamWriter writer = null;
            try {
                writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                // 添加BOM以支持Excel正确显示中文
                outputStream.write(0xEF);
                outputStream.write(0xBB);
                outputStream.write(0xBF);
                
                // 获取表列信息
                List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
                
                // 写入CSV头部
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) header.append(",");
                    header.append("\"").append(columns.get(i).get("COLUMN_NAME")).append("\"");
                }
                writer.write(header.toString());
                writer.write("\n");
                writer.flush();
                
                // 获取搜索结果数据
                Map<String, Object> searchResult = databaseService.getTableDataByValueWithPagination(
                    dataSource, tableName, searchValue, 1, limit, "auto", searchType);
                List<Map<String, Object>> data = (List<Map<String, Object>>) searchResult.get("data");
                
                // 写入数据行 - 分批处理避免内存问题
                int batchSize = 1000; // 每批处理1000行
                for (int i = 0; i < data.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, data.size());
                    List<Map<String, Object>> batch = data.subList(i, endIndex);
                    
                    for (Map<String, Object> row : batch) {
                        StringBuilder rowData = new StringBuilder();
                        for (int j = 0; j < columns.size(); j++) {
                            if (j > 0) rowData.append(",");
                            String columnName = (String) columns.get(j).get("COLUMN_NAME");
                            Object value = row.get(columnName);
                            String cellValue = (value != null) ? value.toString() : "";
                            // 转义CSV特殊字符
                            cellValue = cellValue.replace("\"", "\"\"");
                            rowData.append("\"").append(cellValue).append("\"");
                        }
                        writer.write(rowData.toString());
                        writer.write("\n");
                    }
                    
                    // 每处理一批后刷新输出流
                    writer.flush();
                }
                
            } catch (Exception e) {
                throw new RuntimeException("搜索结果CSV导出失败: " + e.getMessage(), e);
            } finally {
                if (writer != null) {
                    try {
                        writer.flush();
                    } catch (Exception e) {
                        // 忽略刷新时的异常
                    }
                }
            }
        };
    }

    /**
     * 导出搜索结果为Excel格式
     */
    public StreamingResponseBody exportSearchResultToExcel(String dataSource, String tableName, 
                                                           Long userId, String userType, 
                                                           String searchValue, String searchType, Integer limit) {
        return outputStream -> {
            Workbook workbook = null;
            try {
                // 使用SXSSFWorkbook支持大数据量导出，在内存中只保留100行
                workbook = new SXSSFWorkbook(100);
                Sheet sheet = workbook.createSheet(tableName);
                
                // 对于SXSSFWorkbook，需要跟踪列才能自动调整列宽
                if (sheet instanceof SXSSFSheet) {
                    ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
                }
                
                // 创建样式
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                
                // 获取表列信息
                List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
                
                // 创建表头
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue((String) columns.get(i).get("COLUMN_NAME"));
                    cell.setCellStyle(headerStyle);
                }
                
                // 获取搜索结果数据
                Map<String, Object> searchResult = databaseService.getTableDataByValueWithPagination(
                    dataSource, tableName, searchValue, 1, limit, "auto", searchType);
                List<Map<String, Object>> data = (List<Map<String, Object>>) searchResult.get("data");
                
                // 写入数据行 - SXSSFWorkbook自动管理内存
                int rowIndex = 1;
                
                for (Map<String, Object> row : data) {
                    Row excelRow = sheet.createRow(rowIndex++);
                    for (int j = 0; j < columns.size(); j++) {
                        Cell cell = excelRow.createCell(j);
                        String columnName = (String) columns.get(j).get("COLUMN_NAME");
                        Object value = row.get(columnName);
                        
                        if (value != null) {
                            if (value instanceof Number) {
                                cell.setCellValue(((Number) value).doubleValue());
                            } else if (value instanceof java.util.Date) {
                                cell.setCellValue((java.util.Date) value);
                            } else {
                                cell.setCellValue(value.toString());
                            }
                        }
                    }
                    
                    // 每1000行刷新一次输出流
                    if (rowIndex % 1000 == 0) {
                        try {
                            outputStream.flush();
                        } catch (Exception e) {
                            // 如果连接已断开，停止处理
                            throw new RuntimeException("连接已断开，导出中断", e);
                        }
                    }
                }
                
                // 自动调整列宽（只对前50列进行调整，避免性能问题）
                int maxColumns = Math.min(columns.size(), 50);
                for (int i = 0; i < maxColumns; i++) {
                    try {
                        sheet.autoSizeColumn(i);
                    } catch (Exception e) {
                        // 如果自动调整列宽失败，设置默认列宽
                        sheet.setColumnWidth(i, 4000); // 默认列宽
                    }
                }
                
                // 写入工作簿
                workbook.write(outputStream);
                outputStream.flush();
                
            } catch (Exception e) {
                throw new RuntimeException("搜索结果Excel导出失败: " + e.getMessage(), e);
            } finally {
                if (workbook != null) {
                    try {
                        workbook.close();
                    } catch (Exception e) {
                        // 忽略关闭时的异常
                    }
                }
            }
        };
    }

    /**
     * 获取搜索结果导出信息
     */
    public Map<String, Object> getSearchResultExportInfo(String dataSource, String tableName, 
                                                         Long userId, String userType, 
                                                         String searchValue, String searchType) {
        try {
            // 获取表列信息
            List<Map<String, Object>> columns = databaseService.getTableColumns(dataSource, tableName);
            
            // 获取搜索结果数据行数
            Map<String, Object> searchResult = databaseService.getTableDataByValueWithPagination(
                dataSource, tableName, searchValue, 1, 1, "auto", searchType);
            Integer totalRows = (Integer) searchResult.get("totalCount");
            
            return Map.of(
                "tableName", tableName,
                "dataSource", dataSource,
                "columnCount", columns.size(),
                "totalRows", totalRows != null ? totalRows : 0,
                "columns", columns.stream().map(col -> Map.of(
                    "name", col.get("COLUMN_NAME"),
                    "type", col.get("DATA_TYPE"),
                    "nullable", col.get("IS_NULLABLE")
                )).toList()
            );
        } catch (Exception e) {
            throw new RuntimeException("获取搜索结果导出信息失败: " + e.getMessage(), e);
        }
    }
}
