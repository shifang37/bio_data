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
import java.util.Map;
import java.util.HashMap;

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
            String sql = "SELECT * FROM user WHERE id = ?";
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
            String sql = "SELECT * FROM user WHERE id = ?";
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

        // 对于其他数据库，所有已登录用户都可以修改
        try {
            String sql = "SELECT * FROM user WHERE id = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, userId);
            return user != null;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("用户不存在: userId={}, userType={}", userId, userType);
            return false;
        } catch (Exception e) {
            logger.error("检查修改权限时发生异常: {}", e.getMessage());
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
            String sql = "SELECT * FROM user WHERE id = ?";
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