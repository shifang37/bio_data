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
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

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
            user.setEmail(rs.getString("email"));
            String roleValue = rs.getString("role");
            user.setRole(roleValue);
            return user;
        }
    };

    /**
     * 统一用户登录验证
     */
    public Map<String, Object> login(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sql = "SELECT * FROM users WHERE name = ? AND password = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, username, password);
            
            if (user != null) {
                result.put("success", true);
                result.put("userType", getUserType(user.getRole()));
                result.put("userId", user.getId());
                result.put("username", user.getName());
                result.put("email", user.getEmail());
                result.put("role", user.getRoleValue());
                result.put("permission", user.getRoleValue()); // 兼容旧版本
                result.put("message", "登录成功");
                logger.info("用户登录成功: {}, 角色: {}", username, user.getRoleValue());
            } else {
                result.put("success", false);
                result.put("message", "用户名或密码错误");
            }
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            logger.warn("用户登录失败，用户不存在: {}", username);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "登录异常: " + e.getMessage());
            logger.error("用户登录异常: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 用户登录方法 - 只允许guest角色用户登录
     */
    public Map<String, Object> loginUser(String username, String password) {
        Map<String, Object> result = login(username, password);
        
        // 检查登录结果
        if ((Boolean) result.get("success")) {
            String role = (String) result.get("role");
            Role userRole = Role.fromValue(role);
            
            // 只允许guest角色用户通过用户登录接口登录
            if (userRole == Role.ADMIN || userRole == Role.INTERNAL) {
                result.put("success", false);
                result.put("message", "管理员和内部用户请使用管理员登录界面");
                logger.warn("管理员/内部用户尝试通过用户登录接口登录: {}, 角色: {}", username, role);
            }
        }
        
        return result;
    }

    /**
     * 管理员登录方法 - 允许admin和internal角色用户登录
     */
    public Map<String, Object> loginAdmin(String username, String password) {
        Map<String, Object> result = login(username, password);
        
        // 检查登录结果
        if ((Boolean) result.get("success")) {
            String role = (String) result.get("role");
            Role userRole = Role.fromValue(role);
            
            // 只允许admin和internal角色用户通过管理员登录接口登录
            if (userRole != Role.ADMIN && userRole != Role.INTERNAL) {
                result.put("success", false);
                result.put("message", "只有管理员和内部用户可以使用管理员登录界面");
                logger.warn("普通用户尝试通过管理员登录接口登录: {}, 角色: {}", username, role);
            }
        }
        
        return result;
    }

    /**
     * 根据Role枚举获取用户类型字符串
     */
    private String getUserType(Role role) {
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
     * 用户注册
     * 只能注册普通用户，角色固定为"guest"
     */
    public Map<String, Object> registerUser(String username, String password, String permission) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查用户名是否已存在
            String checkSql = "SELECT COUNT(*) FROM users WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count > 0) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return result;
            }

            // 强制角色为"guest"，确保只能注册普通用户
            Role actualRole = Role.GUEST;
            
            // 插入新用户
            String insertSql = "INSERT INTO users (name, password, role) VALUES (?, ?, ?)";
            int rows = loginJdbcTemplate.update(insertSql, username, password, actualRole.getValue());
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "用户注册成功");
                logger.info("用户注册成功: {}, 角色: {}", username, actualRole.getValue());
            } else {
                result.put("success", false);
                result.put("message", "注册失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "注册异常: " + e.getMessage());
            logger.error("用户注册异常: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 管理员注册 - 已禁用
     * 管理员用户由系统管理员手动添加，不允许通过注册接口创建
     */
    public Map<String, Object> registerAdmin(String username, String password, String permission) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "管理员账户由系统管理员添加，不允许通过注册接口创建");
        logger.warn("尝试通过注册接口创建管理员账户: {}", username);
        return result;
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        String sql = "SELECT * FROM users ORDER BY id";
        return loginJdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * 根据角色获取用户列表
     */
    public List<User> getUsersByRole(Role role) {
        String sql = "SELECT * FROM users WHERE role = ? ORDER BY id";
        return loginJdbcTemplate.query(sql, userRowMapper, role.getValue());
    }

    /**
     * 获取管理员用户列表（兼容旧版本）
     */
    public List<User> getAllAdmins() {
        return getUsersByRole(Role.ADMIN);
    }

    /**
     * 根据ID查找用户
     */
    public User getUserById(Long id) {
        try {
            String sql = "SELECT * FROM users WHERE id = ?";
            return loginJdbcTemplate.queryForObject(sql, userRowMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据用户名查找用户
     */
    public User getUserByName(String name) {
        try {
            String sql = "SELECT * FROM users WHERE name = ?";
            return loginJdbcTemplate.queryForObject(sql, userRowMapper, name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 兼容旧版本的根据ID查找管理员方法
     */
    public User getAdminById(Long id) {
        User user = getUserById(id);
        return (user != null && user.getRole() == Role.ADMIN) ? user : null;
    }

    /**
     * 用户密码重置（仅针对普通用户）
     */
    public Map<String, Object> resetUserPassword(String username, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查用户是否存在
            String checkSql = "SELECT COUNT(*) FROM users WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count == 0) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }

            // 更新用户密码
            String updateSql = "UPDATE users SET password = ? WHERE name = ?";
            int rows = loginJdbcTemplate.update(updateSql, newPassword, username);
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "密码重置成功");
                logger.info("用户密码重置成功: {}", username);
            } else {
                result.put("success", false);
                result.put("message", "密码重置失败");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "密码重置异常: " + e.getMessage());
            logger.error("用户密码重置异常: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 验证用户名是否存在（用于密码重置前的验证）
     */
    public Map<String, Object> checkUserExists(String username) {
        Map<String, Object> result = new HashMap<>();
        try {
            String checkSql = "SELECT COUNT(*) FROM users WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count > 0) {
                result.put("success", true);
                result.put("exists", true);
                result.put("message", "用户存在");
            } else {
                result.put("success", true);
                result.put("exists", false);
                result.put("message", "用户不存在");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("exists", false);
            result.put("message", "查询失败: " + e.getMessage());
            logger.error("检查用户是否存在时发生异常: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 测试login数据库连接
     */
    public boolean testConnection() {
        try {
            loginJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            logger.error("Login数据库连接测试失败: {}", e.getMessage());
            return false;
        }
    }
} 