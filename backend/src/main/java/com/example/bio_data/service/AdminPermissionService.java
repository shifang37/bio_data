package com.example.bio_data.service;

import com.example.bio_data.entity.User;
import com.example.bio_data.entity.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 管理员权限管理服务
 * 提供管理员对内部用户权限的管理功能
 */
@Service
public class AdminPermissionService {

    private static final Logger logger = LoggerFactory.getLogger(AdminPermissionService.class);

    @Autowired
    @Qualifier("loginJdbcTemplate")
    private JdbcTemplate loginJdbcTemplate;

    @Autowired
    private PermissionService permissionService;

    // User RowMapper
    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setPassword(rs.getString("password"));
            user.setEmail(rs.getString("email"));
            String roleValue = rs.getString("role");
            user.setRole(roleValue);
            return user;
        }
    };


    /**
     * 为内部用户授权数据库级写权限
     * @param adminId 管理员ID
     * @param internalUserId 内部用户ID
     * @param databaseName 数据库名称
     * @param expiresAt 过期时间（可为空，表示永久有效）
     * @return 操作结果
     */
    public Map<String, Object> grantDatabaseWriteAccess(Long adminId, Long internalUserId, String databaseName, LocalDateTime expiresAt) {
        return grantTableWriteAccess(adminId, internalUserId, databaseName, null, expiresAt);
    }

    /**
     * 为内部用户授权表写权限
     * @param adminId 管理员ID
     * @param internalUserId 内部用户ID
     * @param databaseName 数据库名称
     * @param tableName 表名称（为null时表示数据库级权限）
     * @param expiresAt 过期时间（可为空，表示永久有效）
     * @return 操作结果
     */
    public Map<String, Object> grantTableWriteAccess(Long adminId, Long internalUserId, String databaseName, String tableName, LocalDateTime expiresAt) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以授权");
                return result;
            }
            
            // 验证目标用户是内部用户
            String checkUserSql = "SELECT * FROM users WHERE id = ?";
            User targetUser = loginJdbcTemplate.queryForObject(checkUserSql, userRowMapper, internalUserId);
            if (targetUser == null || targetUser.getRole() != Role.INTERNAL) {
                result.put("success", false);
                result.put("error", "目标用户不存在或不是内部用户");
                return result;
            }
            
            // 检查是否已存在相同的授权记录
            String checkExistingSql = "SELECT COUNT(*) FROM user_table_write_access WHERE user_id = ? AND database_name = ? AND table_name = ?";
            int count = loginJdbcTemplate.queryForObject(checkExistingSql, Integer.class, internalUserId, databaseName, tableName);
            
            if (count > 0) {
                // 更新现有记录
                String updateSql = "UPDATE user_table_write_access SET granted_by = ?, created_at = ?, expires_at = ? WHERE user_id = ? AND database_name = ? AND table_name = ?";
                loginJdbcTemplate.update(updateSql, adminId, LocalDateTime.now(), expiresAt, internalUserId, databaseName, tableName);
                result.put("success", true);
                result.put("message", "权限更新成功");
            } else {
                // 插入新记录
                String insertSql = "INSERT INTO user_table_write_access (user_id, database_name, table_name, granted_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
                loginJdbcTemplate.update(insertSql, internalUserId, databaseName, tableName, adminId, LocalDateTime.now(), expiresAt);
                result.put("success", true);
                result.put("message", "权限授权成功");
            }
            
            if (tableName == null) {
                logger.info("管理员{}为用户{}授权数据库{}级写权限", adminId, internalUserId, databaseName);
            } else {
                logger.info("管理员{}为用户{}授权表{}写权限", adminId, internalUserId, databaseName + "." + tableName);
            }
            
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("error", "目标用户不存在");
            logger.warn("授权失败，目标用户不存在: internalUserId={}", internalUserId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "授权失败: " + e.getMessage());
            logger.error("授权表写权限时发生异常: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 撤销用户的数据库级写权限
     * @param adminId 管理员ID
     * @param internalUserId 内部用户ID
     * @param databaseName 数据库名称
     * @return 操作结果
     */
    public Map<String, Object> revokeDatabaseWriteAccess(Long adminId, Long internalUserId, String databaseName) {
        return revokeTableWriteAccess(adminId, internalUserId, databaseName, null);
    }

    /**
     * 撤销用户的表写权限
     * @param adminId 管理员ID
     * @param internalUserId 内部用户ID
     * @param databaseName 数据库名称
     * @param tableName 表名称（为null时表示数据库级权限）
     * @return 操作结果
     */
    public Map<String, Object> revokeTableWriteAccess(Long adminId, Long internalUserId, String databaseName, String tableName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以撤销权限");
                return result;
            }
            
            // 删除授权记录
            String deleteSql = "DELETE FROM user_table_write_access WHERE user_id = ? AND database_name = ? AND table_name " + 
                              (tableName == null ? "IS NULL" : "= ?");
            int rowsAffected;
            if (tableName == null) {
                rowsAffected = loginJdbcTemplate.update(deleteSql, internalUserId, databaseName);
            } else {
                rowsAffected = loginJdbcTemplate.update(deleteSql, internalUserId, databaseName, tableName);
            }
            
            if (rowsAffected > 0) {
                result.put("success", true);
                result.put("message", "权限撤销成功");
                if (tableName == null) {
                    logger.info("管理员{}撤销用户{}的数据库{}级写权限", adminId, internalUserId, databaseName);
                } else {
                    logger.info("管理员{}撤销用户{}的表{}写权限", adminId, internalUserId, databaseName + "." + tableName);
                }
            } else {
                result.put("success", false);
                result.put("error", "未找到相应的权限记录");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "撤销权限失败: " + e.getMessage());
            logger.error("撤销表写权限时发生异常: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取所有权限授权记录
     * @param adminId 管理员ID
     * @return 所有授权记录
     */
    public Map<String, Object> getAllPermissions(Long adminId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以查看权限信息");
                return result;
            }
            
            String sql = "SELECT wa.*, u.name as user_name, a.name as granted_by_name " +
                        "FROM user_table_write_access wa " +
                        "LEFT JOIN users u ON wa.user_id = u.id " +
                        "LEFT JOIN users a ON wa.granted_by = a.id " +
                        "ORDER BY wa.created_at DESC";
            
            List<Map<String, Object>> permissions = loginJdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> permission = new HashMap<>();
                permission.put("id", rs.getLong("id"));
                permission.put("userId", rs.getLong("user_id"));
                permission.put("userName", rs.getString("user_name"));
                permission.put("databaseName", rs.getString("database_name"));
                permission.put("tableName", rs.getString("table_name"));
                permission.put("grantedBy", rs.getLong("granted_by"));
                permission.put("grantedByName", rs.getString("granted_by_name"));
                permission.put("createdAt", rs.getTimestamp("created_at"));
                permission.put("expiresAt", rs.getTimestamp("expires_at"));
                
                // 权限类型：数据库级权限还是表级权限
                String tableName = rs.getString("table_name");
                if (tableName == null) {
                    permission.put("permissionType", "database");
                    permission.put("displayName", rs.getString("database_name") + " (数据库级权限)");
                } else {
                    permission.put("permissionType", "table");
                    permission.put("displayName", rs.getString("database_name") + "." + tableName);
                }
                
                // 检查是否过期
                java.sql.Timestamp expiresTimestamp = rs.getTimestamp("expires_at");
                if (expiresTimestamp != null) {
                    permission.put("isExpired", LocalDateTime.now().isAfter(expiresTimestamp.toLocalDateTime()));
                } else {
                    permission.put("isExpired", false);
                }
                
                return permission;
            });
            
            result.put("success", true);
            result.put("permissions", permissions);
            result.put("total", permissions.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取权限信息失败: " + e.getMessage());
            logger.error("获取权限信息时发生异常: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取特定用户的权限记录
     * @param adminId 管理员ID
     * @param internalUserId 内部用户ID
     * @return 用户的权限记录
     */
    public Map<String, Object> getUserPermissions(Long adminId, Long internalUserId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以查看权限信息");
                return result;
            }
            
            String sql = "SELECT wa.*, u.name as user_name, a.name as granted_by_name " +
                        "FROM user_table_write_access wa " +
                        "LEFT JOIN users u ON wa.user_id = u.id " +
                        "LEFT JOIN users a ON wa.granted_by = a.id " +
                        "WHERE wa.user_id = ? " +
                        "ORDER BY wa.created_at DESC";
            
            List<Map<String, Object>> permissions = loginJdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> permission = new HashMap<>();
                permission.put("id", rs.getLong("id"));
                permission.put("userId", rs.getLong("user_id"));
                permission.put("userName", rs.getString("user_name"));
                permission.put("databaseName", rs.getString("database_name"));
                permission.put("tableName", rs.getString("table_name"));
                permission.put("grantedBy", rs.getLong("granted_by"));
                permission.put("grantedByName", rs.getString("granted_by_name"));
                permission.put("createdAt", rs.getTimestamp("created_at"));
                permission.put("expiresAt", rs.getTimestamp("expires_at"));
                
                // 权限类型：数据库级权限还是表级权限
                String tableName = rs.getString("table_name");
                if (tableName == null) {
                    permission.put("permissionType", "database");
                    permission.put("displayName", rs.getString("database_name") + " (数据库级权限)");
                } else {
                    permission.put("permissionType", "table");
                    permission.put("displayName", rs.getString("database_name") + "." + tableName);
                }
                
                // 检查是否过期
                java.sql.Timestamp expiresTimestamp = rs.getTimestamp("expires_at");
                if (expiresTimestamp != null) {
                    permission.put("isExpired", LocalDateTime.now().isAfter(expiresTimestamp.toLocalDateTime()));
                } else {
                    permission.put("isExpired", false);
                }
                
                return permission;
            }, internalUserId);
            
            result.put("success", true);
            result.put("permissions", permissions);
            result.put("total", permissions.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取用户权限信息失败: " + e.getMessage());
            logger.error("获取用户权限信息时发生异常: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 创建内部用户（仅管理员可用）
     * @param adminId 管理员ID
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱（可选）
     * @return 操作结果
     */
    public Map<String, Object> createInternalUser(Long adminId, String username, String password, String email) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以创建内部用户");
                return result;
            }
            
            // 检查用户名是否已存在
            String checkSql = "SELECT COUNT(*) FROM users WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count > 0) {
                result.put("success", false);
                result.put("error", "用户名已存在");
                return result;
            }
            
            // 插入新的内部用户
            String insertSql = "INSERT INTO users (name, password, email, role) VALUES (?, ?, ?, ?)";
            int rows = loginJdbcTemplate.update(insertSql, username, password, email, Role.INTERNAL.getValue());
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "内部用户创建成功");
                logger.info("管理员{}创建内部用户: {}", adminId, username);
            } else {
                result.put("success", false);
                result.put("error", "创建用户失败");
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "创建内部用户失败: " + e.getMessage());
            logger.error("创建内部用户时发生异常: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取所有内部用户列表
     * @param adminId 管理员ID
     * @return 内部用户列表
     */
    public Map<String, Object> getAllInternalUsers(Long adminId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 验证管理员权限
            if (!permissionService.isAdmin(adminId, "admin")) {
                result.put("success", false);
                result.put("error", "权限不足，只有管理员可以查看用户信息");
                return result;
            }
            
            String sql = "SELECT id, name, email, role FROM users WHERE role = ? ORDER BY id";
            List<Map<String, Object>> users = loginJdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getLong("id"));
                user.put("name", rs.getString("name"));
                user.put("email", rs.getString("email"));
                user.put("role", rs.getString("role"));
                return user;
            }, Role.INTERNAL.getValue());
            
            result.put("success", true);
            result.put("users", users);
            result.put("total", users.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取内部用户列表失败: " + e.getMessage());
            logger.error("获取内部用户列表时发生异常: {}", e.getMessage());
        }
        
        return result;
    }
}
