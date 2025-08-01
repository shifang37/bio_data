package com.example.bio_data.service;

import com.example.bio_data.entity.User;
import com.example.bio_data.entity.Admin;
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
            user.setPermission(rs.getString("permission"));
            return user;
        }
    };

    // Admin RowMapper
    private final RowMapper<Admin> adminRowMapper = new RowMapper<Admin>() {
        @Override
        public Admin mapRow(ResultSet rs, int rowNum) throws SQLException {
            Admin admin = new Admin();
            admin.setId(rs.getLong("id"));
            admin.setName(rs.getString("name"));
            admin.setPassword(rs.getString("password"));
            admin.setPermission(rs.getString("permission"));
            return admin;
        }
    };

    /**
     * 用户登录验证
     */
    public Map<String, Object> loginUser(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sql = "SELECT * FROM user WHERE name = ? AND password = ?";
            User user = loginJdbcTemplate.queryForObject(sql, userRowMapper, username, password);
            
            if (user != null) {
                result.put("success", true);
                result.put("userType", "user");
                result.put("userId", user.getId());
                result.put("username", user.getName());
                result.put("permission", user.getPermission());
                result.put("message", "用户登录成功");
                logger.info("用户登录成功: {}", username);
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
     * 管理员登录验证
     */
    public Map<String, Object> loginAdmin(String username, String password) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sql = "SELECT * FROM admin WHERE name = ? AND password = ?";
            Admin admin = loginJdbcTemplate.queryForObject(sql, adminRowMapper, username, password);
            
            if (admin != null) {
                result.put("success", true);
                result.put("userType", "admin");
                result.put("userId", admin.getId());
                result.put("username", admin.getName());
                result.put("permission", admin.getPermission());
                result.put("message", "管理员登录成功");
                logger.info("管理员登录成功: {}", username);
            } else {
                result.put("success", false);
                result.put("message", "用户名或密码错误");
            }
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            logger.warn("管理员登录失败，用户不存在: {}", username);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "登录异常: " + e.getMessage());
            logger.error("管理员登录异常: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 用户注册
     * 只能注册普通用户，权限固定为"user"
     */
    public Map<String, Object> registerUser(String username, String password, String permission) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查用户名是否已存在
            String checkSql = "SELECT COUNT(*) FROM user WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count > 0) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return result;
            }

            // 强制权限为"user"，确保只能注册普通用户
            String actualPermission = "user";
            
            // 插入新用户
            String insertSql = "INSERT INTO user (name, password, permission) VALUES (?, ?, ?)";
            int rows = loginJdbcTemplate.update(insertSql, username, password, actualPermission);
            
            if (rows > 0) {
                result.put("success", true);
                result.put("message", "用户注册成功");
                logger.info("用户注册成功: {}, 权限: {}", username, actualPermission);
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
        String sql = "SELECT * FROM user ORDER BY id";
        return loginJdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * 获取所有管理员
     */
    public List<Admin> getAllAdmins() {
        String sql = "SELECT * FROM admin ORDER BY id";
        return loginJdbcTemplate.query(sql, adminRowMapper);
    }

    /**
     * 根据ID查找用户
     */
    public User getUserById(Long id) {
        try {
            String sql = "SELECT * FROM user WHERE id = ?";
            return loginJdbcTemplate.queryForObject(sql, userRowMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 根据ID查找管理员
     */
    public Admin getAdminById(Long id) {
        try {
            String sql = "SELECT * FROM admin WHERE id = ?";
            return loginJdbcTemplate.queryForObject(sql, adminRowMapper, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 用户密码重置（仅针对普通用户）
     */
    public Map<String, Object> resetUserPassword(String username, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查用户是否存在
            String checkSql = "SELECT COUNT(*) FROM user WHERE name = ?";
            int count = loginJdbcTemplate.queryForObject(checkSql, Integer.class, username);
            
            if (count == 0) {
                result.put("success", false);
                result.put("message", "用户不存在");
                return result;
            }

            // 更新用户密码
            String updateSql = "UPDATE user SET password = ? WHERE name = ?";
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
            String checkSql = "SELECT COUNT(*) FROM user WHERE name = ?";
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