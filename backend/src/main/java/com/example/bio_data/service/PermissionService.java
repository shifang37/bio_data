package com.example.bio_data.service;

import com.example.bio_data.entity.User;
import com.example.bio_data.entity.Role;
import com.example.bio_data.entity.UserTableWriteAccess;
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
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    @Autowired
    @Qualifier("loginJdbcTemplate")
    private JdbcTemplate loginJdbcTemplate;

    // User RowMapper
    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setPassword(rs.getString("password"));
            String roleValue = rs.getString("role");
            user.setRole(roleValue);
            return user;
        }
    };

    // UserTableWriteAccess RowMapper
    private final RowMapper<UserTableWriteAccess> writeAccessRowMapper = new RowMapper<UserTableWriteAccess>() {
        @Override
        public UserTableWriteAccess mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserTableWriteAccess access = new UserTableWriteAccess();
            access.setId(rs.getLong("id"));
            access.setUserId(rs.getLong("user_id"));
            access.setDatabaseName(rs.getString("database_name"));
            access.setTableName(rs.getString("table_name"));
            access.setGrantedBy(rs.getLong("granted_by"));
            
            // 处理时间字段
            java.sql.Timestamp createdTimestamp = rs.getTimestamp("created_at");
            if (createdTimestamp != null) {
                access.setCreatedAt(createdTimestamp.toLocalDateTime());
            }
            
            java.sql.Timestamp expiresTimestamp = rs.getTimestamp("expires_at");
            if (expiresTimestamp != null) {
                access.setExpiresAt(expiresTimestamp.toLocalDateTime());
            }
            
            return access;
        }
    };

    /**
     * 检查用户是否为管理员
     * @param userId 用户ID
     * @param userType 用户类型（"admin", "internal", "guest"）
     * @return 是否为管理员
     */
    public boolean isAdmin(Long userId, String userType) {
        if (userId == null) {
            return false;
        }

        try {
            String sql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, userId);
            return user != null && user.getRole() == Role.ADMIN;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}, userType={}", userId, userType);
            return false;
        } catch (Exception e) {
            logger.error("检查管理员权限时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查用户是否为内部用户或管理员
     * @param userId 用户ID
     * @return 是否为内部用户或管理员
     */
    public boolean isInternal(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            String sql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, userId);
            return user != null && (user.getRole() == Role.INTERNAL || user.getRole() == Role.ADMIN);
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        } catch (Exception e) {
            logger.error("检查内部用户权限时发生异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查用户是否有权限访问指定数据库
     * @param userId 用户ID
     * @param userType 用户类型（"admin" 或 "user"）
     * @param databaseName 数据库名称
     * @return 是否有权限访问
     */
    public boolean hasPermissionToAccessDatabase(Long userId, String userType, String databaseName) {
        if (userId == null || userType == null || databaseName == null) {
            return false;
        }

        // 对于login数据库，只有管理员可以访问
        if ("login".equalsIgnoreCase(databaseName)) {
            return isAdmin(userId, userType);
        }

        // 对于其他数据库，已登录用户都可以访问
        return true;
    }

    /**
     * 检查用户是否有权限修改指定数据库
     * @param userId 用户ID
     * @param userType 用户类型（"admin", "internal", "guest"）
     * @param databaseName 数据库名称
     * @return 是否有权限修改
     */
    public boolean hasPermissionToModifyDatabase(Long userId, String userType, String databaseName) {
        if (userId == null || databaseName == null) {
            return false;
        }

        // 对于login数据库，只有管理员可以修改
        if ("login".equalsIgnoreCase(databaseName)) {
            return isAdmin(userId, userType);
        }

        // 获取用户角色
        try {
            String sql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, userId);
            if (user == null) {
                return false;
            }

            Role userRole = user.getRole();
            
            // 管理员有所有权限
            if (userRole == Role.ADMIN) {
                return true;
            }
            
            // 普通用户没有修改权限
            if (userRole == Role.GUEST) {
                return false;
            }
            
            // 内部用户需要检查具体的表权限（这里只检查数据库级别，表级别权限由hasPermissionToModifyTable检查）
            return userRole == Role.INTERNAL;
            
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}, userType={}", userId, userType);
            return false;
        } catch (Exception e) {
            logger.error("检查修改权限时发生异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查用户是否有权限修改指定表
     * @param userId 用户ID
     * @param databaseName 数据库名称
     * @param tableName 表名称
     * @return 是否有权限修改
     */
    public boolean hasPermissionToModifyTable(Long userId, String databaseName, String tableName) {
        if (userId == null || databaseName == null || tableName == null) {
            logger.warn("权限检查参数无效: userId={}, databaseName={}, tableName={}", userId, databaseName, tableName);
            return false;
        }

        logger.info("检查用户{}对表{}.{}的修改权限", userId, databaseName, tableName);

        // 对于login数据库，只有管理员可以修改
        if ("login".equalsIgnoreCase(databaseName)) {
            boolean isAdminResult = isAdmin(userId, null);
            logger.info("login数据库修改权限检查结果: isAdmin={}", isAdminResult);
            return isAdminResult;
        }

        try {
            // 获取用户角色
            String userSql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(userSql, userRowMapper, userId);
            if (user == null) {
                logger.warn("用户不存在: userId={}", userId);
                return false;
            }

            Role userRole = user.getRole();
            logger.info("用户{}的角色: {}", userId, userRole);
            
            // 管理员有所有权限
            if (userRole == Role.ADMIN) {
                logger.info("管理员用户，自动通过权限检查");
                return true;
            }
            
            // 普通用户没有修改权限
            if (userRole == Role.GUEST) {
                logger.info("普通用户，没有修改权限");
                return false;
            }
            
            // 内部用户需要检查user_table_write_access表
            if (userRole == Role.INTERNAL) {
                logger.info("内部用户，检查具体表权限");
                return hasTableWriteAccess(userId, databaseName, tableName);
            }
            
            logger.warn("未知角色: {}", userRole);
            return false;
            
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        } catch (Exception e) {
            logger.error("检查表修改权限时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查用户是否有数据库级的写权限
     * @param userId 用户ID
     * @param databaseName 数据库名称
     * @return 是否有数据库级写权限
     */
    public boolean hasDatabaseWriteAccess(Long userId, String databaseName) {
        if (userId == null || databaseName == null) {
            logger.warn("数据库权限检查参数无效: userId={}, databaseName={}", userId, databaseName);
            return false;
        }

        try {
            logger.info("检查用户{}对数据库{}的写权限", userId, databaseName);
            // 检查table_name为NULL的记录，表示数据库级权限
            String sql = "SELECT * FROM user_table_write_access WHERE user_id = ? AND database_name = ? AND table_name IS NULL";
            List<UserTableWriteAccess> accessList = loginJdbcTemplate.query(sql, writeAccessRowMapper, userId, databaseName);
            
            logger.info("找到{}条数据库级权限记录", accessList.size());
            
            // 检查是否有有效的权限记录
            for (UserTableWriteAccess access : accessList) {
                logger.info("数据库级权限记录: id={}, expires_at={}, isValid={}", access.getId(), access.getExpiresAt(), access.isValid());
                if (access.isValid()) {
                    logger.info("用户{}对数据库{}有有效的写权限", userId, databaseName);
                    return true;
                }
            }
            
            logger.info("用户{}对数据库{}没有有效的写权限", userId, databaseName);
            return false;
            
        } catch (Exception e) {
            logger.error("检查数据库写权限时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查用户是否有指定表的写权限
     * @param userId 用户ID
     * @param databaseName 数据库名称
     * @param tableName 表名称
     * @return 是否有写权限
     */
    public boolean hasTableWriteAccess(Long userId, String databaseName, String tableName) {
        if (userId == null || databaseName == null || tableName == null) {
            logger.warn("权限检查参数无效: userId={}, databaseName={}, tableName={}", userId, databaseName, tableName);
            return false;
        }

        logger.info("检查用户{}对表{}.{}的写权限", userId, databaseName, tableName);

        // 首先检查是否有数据库级权限
        if (hasDatabaseWriteAccess(userId, databaseName)) {
            logger.info("用户{}有数据库{}的整体写权限，自动通过表权限检查", userId, databaseName);
            return true;
        }

        try {
            // 检查具体表的权限
            String sql = "SELECT * FROM user_table_write_access WHERE user_id = ? AND database_name = ? AND table_name = ?";
            List<UserTableWriteAccess> accessList = loginJdbcTemplate.query(sql, writeAccessRowMapper, userId, databaseName, tableName);
            
            logger.info("找到{}条表级权限记录", accessList.size());
            
            // 检查是否有有效的权限记录
            for (UserTableWriteAccess access : accessList) {
                logger.info("表级权限记录: id={}, expires_at={}, isValid={}", access.getId(), access.getExpiresAt(), access.isValid());
                if (access.isValid()) {
                    logger.info("用户{}对表{}.{}有有效的写权限", userId, databaseName, tableName);
                    return true;
                }
            }
            
            logger.warn("用户{}对表{}.{}没有有效的写权限", userId, databaseName, tableName);
            return false;
            
        } catch (Exception e) {
            logger.error("检查表写权限时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查用户是否有在指定数据库中创建表的权限
     * @param userId 用户ID  
     * @param databaseName 数据库名称
     * @return 是否有创建表权限
     */
    public boolean hasPermissionToCreateTable(Long userId, String databaseName) {
        if (userId == null || databaseName == null) {
            logger.warn("创建表权限检查参数无效: userId={}, databaseName={}", userId, databaseName);
            return false;
        }

        logger.info("检查用户{}在数据库{}中创建表的权限", userId, databaseName);

        // 对于login数据库，只有管理员可以创建表
        if ("login".equalsIgnoreCase(databaseName)) {
            boolean isAdminResult = isAdmin(userId, null);
            logger.info("login数据库创建表权限检查结果: isAdmin={}", isAdminResult);
            return isAdminResult;
        }

        try {
            // 获取用户角色
            String userSql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(userSql, userRowMapper, userId);
            if (user == null) {
                logger.warn("用户不存在: userId={}", userId);
                return false;
            }

            Role userRole = user.getRole();
            logger.info("用户{}的角色: {}", userId, userRole);
            
            // 管理员有所有权限
            if (userRole == Role.ADMIN) {
                logger.info("管理员用户，自动通过创建表权限检查");
                return true;
            }
            
            // 普通用户没有创建表权限
            if (userRole == Role.GUEST) {
                logger.info("普通用户，没有创建表权限");
                return false;
            }
            
            // 内部用户需要检查是否有数据库级权限
            if (userRole == Role.INTERNAL) {
                logger.info("内部用户，检查数据库级权限");
                return hasDatabaseWriteAccess(userId, databaseName);
            }
            
            logger.warn("未知角色: {}", userRole);
            return false;
            
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}", userId);
            return false;
        } catch (Exception e) {
            logger.error("检查创建表权限时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用户权限信息
     * @param userId 用户ID
     * @param userType 用户类型（"admin", "internal", "guest"）
     * @return 用户权限信息
     */
    public Map<String, Object> getUserPermissionInfo(Long userId, String userType) {
        Map<String, Object> result = new HashMap<>();
        
        if (userId == null) {
            result.put("success", false);
            result.put("error", "用户信息无效");
            return result;
        }

        try {
            String sql = "SELECT * FROM users WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, userId);
            if (user != null) {
                result.put("success", true);
                result.put("userType", getUserTypeFromRole(user.getRole()));
                result.put("role", user.getRoleValue());
                result.put("permission", user.getRoleValue()); // 兼容旧版本
                result.put("canAccessLogin", user.getRole() == Role.ADMIN);
                result.put("canModifyLogin", user.getRole() == Role.ADMIN);
                result.put("isAdmin", user.getRole() == Role.ADMIN);
                result.put("isInternal", user.getRole() == Role.INTERNAL || user.getRole() == Role.ADMIN);
            } else {
                result.put("success", false);
                result.put("error", "用户不存在");
            }
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("error", "用户不存在");
            logger.warn("用户不存在: userId={}, userType={}", userId, userType);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "获取用户权限信息失败: " + e.getMessage());
            logger.error("获取用户权限信息时发生异常: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 根据Role枚举获取用户类型字符串
     */
    private String getUserTypeFromRole(Role role) {
        if (role == null) {
            return "guest";
        }
        return switch (role) {
            case ADMIN -> "admin";
            case INTERNAL -> "internal";
            case GUEST -> "guest";
        };
    }

    /**
     * 验证请求权限
     * @param userId 用户ID
     * @param userType 用户类型
     * @param databaseName 数据库名称
     * @param operation 操作类型（"read" 或 "write"）
     * @return 权限验证结果
     */
    public Map<String, Object> validatePermission(Long userId, String userType, String databaseName, String operation) {
        Map<String, Object> result = new HashMap<>();
        
        if (userId == null || userType == null) {
            result.put("success", false);
            result.put("error", "用户信息无效");
            return result;
        }

        try {
            boolean hasPermission = false;
            
            if ("read".equalsIgnoreCase(operation)) {
                hasPermission = hasPermissionToAccessDatabase(userId, userType, databaseName);
            } else if ("write".equalsIgnoreCase(operation)) {
                hasPermission = hasPermissionToModifyDatabase(userId, userType, databaseName);
            }

            if (hasPermission) {
                result.put("success", true);
                result.put("message", "权限验证通过");
            } else {
                result.put("success", false);
                result.put("error", "权限不足，无法执行此操作");
                if ("login".equalsIgnoreCase(databaseName)) {
                    result.put("error", "只有管理员可以访问login数据库");
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "权限验证失败: " + e.getMessage());
            logger.error("权限验证时发生异常: {}", e.getMessage());
        }

        return result;
    }
} 