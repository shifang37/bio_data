package com.example.bio_data.service;

import com.example.bio_data.entity.User;
import com.example.bio_data.entity.Role;
import com.example.bio_data.entity.UserStatus;
import com.example.bio_data.entity.ActivationToken;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    @Qualifier("loginJdbcTemplate")
    private JdbcTemplate loginJdbcTemplate;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private CaptchaService captchaService;

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
            // 添加状态字段映射
            String statusValue = rs.getString("status");
            if (statusValue != null) {
                user.setStatus(statusValue);
            } else {
                user.setStatus(UserStatus.PENDING); // 默认待激活
            }
            return user;
        }
    };
    
    // ActivationToken RowMapper
    private final RowMapper<ActivationToken> activationTokenRowMapper = new RowMapper<ActivationToken>() {
        @Override
        public ActivationToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            ActivationToken token = new ActivationToken();
            token.setId(rs.getLong("id"));
            token.setUserId(rs.getLong("user_id"));
            token.setToken(rs.getString("token"));
            token.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
            token.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return token;
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
                // 检查用户状态
                if (!user.isActive()) {
                    if (user.isPending()) {
                        result.put("success", false);
                        result.put("message", "账户尚未激活，请检查邮箱并点击激活链接");
                        result.put("needActivation", true);
                    } else if (user.isBanned()) {
                        result.put("success", false);
                        result.put("message", "账户已被封禁，请联系管理员");
                        result.put("isBanned", true);
                    } else {
                        result.put("success", false);
                        result.put("message", "账户状态异常，请联系管理员");
                    }
                    logger.warn("用户登录失败，账户状态: {}, 用户: {}", user.getStatusValue(), username);
                    return result;
                }
                
                result.put("success", true);
                result.put("userType", getUserType(user.getRole()));
                result.put("userId", user.getId());
                result.put("username", user.getName());
                result.put("email", user.getEmail());
                result.put("role", user.getRoleValue());
                result.put("status", user.getStatusValue());
                result.put("permission", user.getRoleValue()); // 兼容旧版本
                result.put("message", "登录成功");
                logger.info("用户登录成功: {}, 角色: {}, 状态: {}", username, user.getRoleValue(), user.getStatusValue());
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
     * 用户注册（新版本 - 需要邮箱激活）
     * 只能注册普通用户，角色固定为"guest"，状态为"pending"
     */
    public Map<String, Object> registerUser(String username, String password, String email, String captchaToken, String captchaInput) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 验证图形验证码
            if (!captchaService.verifyCaptcha(captchaToken, captchaInput)) {
                result.put("success", false);
                result.put("message", "验证码错误或已过期");
                return result;
            }
            
            // 检查用户名是否已存在
            String checkUsernameSql = "SELECT COUNT(*) FROM users WHERE name = ?";
            int usernameCount = loginJdbcTemplate.queryForObject(checkUsernameSql, Integer.class, username);
            
            if (usernameCount > 0) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return result;
            }
            
            // 检查邮箱是否已存在
            String checkEmailSql = "SELECT COUNT(*) FROM users WHERE email = ?";
            int emailCount = loginJdbcTemplate.queryForObject(checkEmailSql, Integer.class, email);
            
            if (emailCount > 0) {
                result.put("success", false);
                result.put("message", "邮箱已被注册");
                return result;
            }

            // 强制角色为"guest"，状态为"pending"
            Role actualRole = Role.GUEST;
            UserStatus actualStatus = UserStatus.PENDING;
            
            // 插入新用户
            String insertSql = "INSERT INTO users (name, password, email, role, status) VALUES (?, ?, ?, ?, ?)";
            int rows = loginJdbcTemplate.update(insertSql, username, password, email, actualRole.getValue(), actualStatus.getValue());
            
            if (rows > 0) {
                // 获取新创建用户的ID
                String getUserIdSql = "SELECT id FROM users WHERE name = ? AND email = ?";
                Long userId = loginJdbcTemplate.queryForObject(getUserIdSql, Long.class, username, email);
                
                // 生成激活令牌
                String activationToken = generateActivationToken(userId);
                
                // 发送激活邮件
                boolean emailSent = emailService.sendActivationEmail(email, username, activationToken);
                
                if (emailSent) {
                    result.put("success", true);
                    result.put("message", "注册成功！激活邮件已发送到您的邮箱，请在24小时内完成激活");
                    result.put("needActivation", true);
                    logger.info("用户注册成功: {}, 邮箱: {}, 角色: {}, 状态: {}", username, email, actualRole.getValue(), actualStatus.getValue());
                } else {
                    result.put("success", false);
                    result.put("message", "注册成功，但激活邮件发送失败，请联系管理员");
                    logger.warn("用户注册成功但邮件发送失败: {}, 邮箱: {}", username, email);
                }
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
     * 旧版本注册方法（保持兼容性）
     */
    public Map<String, Object> registerUser(String username, String password, String permission) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "请使用新版本注册接口，需要提供邮箱和验证码");
        logger.warn("使用了已废弃的注册接口: {}", username);
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
     * 生成激活令牌
     */
    private String generateActivationToken(Long userId) {
        try {
            // 生成UUID作为令牌
            String token = UUID.randomUUID().toString().replace("-", "");
            
            // 设置过期时间（24小时后）
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            
            // 插入激活令牌
            String insertTokenSql = "INSERT INTO activation_tokens (user_id, token, expires_at) VALUES (?, ?, ?)";
            loginJdbcTemplate.update(insertTokenSql, userId, token, Timestamp.valueOf(expiresAt));
            
            logger.info("生成激活令牌成功，用户ID: {}, 过期时间: {}", userId, expiresAt);
            return token;
            
        } catch (Exception e) {
            logger.error("生成激活令牌失败，用户ID: {}, 错误: {}", userId, e.getMessage());
            throw new RuntimeException("生成激活令牌失败", e);
        }
    }
    
    /**
     * 激活用户账户
     */
    public Map<String, Object> activateAccount(String token) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 查找激活令牌
            String findTokenSql = "SELECT * FROM activation_tokens WHERE token = ?";
            ActivationToken activationToken = loginJdbcTemplate.queryForObject(findTokenSql, activationTokenRowMapper, token);
            
            if (activationToken == null) {
                result.put("success", false);
                result.put("message", "激活链接无效");
                return result;
            }
            
            // 检查令牌是否过期
            if (activationToken.isExpired()) {
                // 删除过期令牌
                deleteActivationToken(token);
                result.put("success", false);
                result.put("message", "激活链接已过期，请重新注册");
                result.put("expired", true);
                return result;
            }
            
            // 激活用户账户
            String updateUserSql = "UPDATE users SET status = ? WHERE id = ?";
            int rows = loginJdbcTemplate.update(updateUserSql, UserStatus.ACTIVE.getValue(), activationToken.getUserId());
            
            if (rows > 0) {
                // 删除激活令牌（一次性使用）
                deleteActivationToken(token);
                
                result.put("success", true);
                result.put("message", "账户激活成功！您现在可以登录系统了");
                logger.info("用户账户激活成功，用户ID: {}", activationToken.getUserId());
            } else {
                result.put("success", false);
                result.put("message", "账户激活失败，请联系管理员");
                logger.error("账户激活失败，更新用户状态失败，用户ID: {}", activationToken.getUserId());
            }
            
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("message", "激活链接无效");
            logger.warn("激活失败，找不到令牌: {}", token);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "激活异常: " + e.getMessage());
            logger.error("账户激活异常，令牌: {}, 错误: {}", token, e.getMessage());
        }
        return result;
    }
    
    /**
     * 删除激活令牌
     */
    private void deleteActivationToken(String token) {
        try {
            String deleteSql = "DELETE FROM activation_tokens WHERE token = ?";
            int rows = loginJdbcTemplate.update(deleteSql, token);
            logger.info("删除激活令牌，令牌: {}, 删除行数: {}", token, rows);
        } catch (Exception e) {
            logger.error("删除激活令牌失败，令牌: {}, 错误: {}", token, e.getMessage());
        }
    }
    
    /**
     * 重新发送激活邮件
     */
    public Map<String, Object> resendActivationEmail(String email) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 查找用户
            String findUserSql = "SELECT * FROM users WHERE email = ? AND status = ?";
            User user = loginJdbcTemplate.queryForObject(findUserSql, userRowMapper, email, UserStatus.PENDING.getValue());
            
            if (user == null) {
                result.put("success", false);
                result.put("message", "找不到待激活的账户");
                return result;
            }
            
            // 删除旧的激活令牌
            String deleteOldTokensSql = "DELETE FROM activation_tokens WHERE user_id = ?";
            loginJdbcTemplate.update(deleteOldTokensSql, user.getId());
            
            // 生成新的激活令牌
            String newToken = generateActivationToken(user.getId());
            
            // 发送激活邮件
            boolean emailSent = emailService.sendActivationEmail(email, user.getName(), newToken);
            
            if (emailSent) {
                result.put("success", true);
                result.put("message", "激活邮件已重新发送，请检查您的邮箱");
                logger.info("重新发送激活邮件成功，用户: {}, 邮箱: {}", user.getName(), email);
            } else {
                result.put("success", false);
                result.put("message", "激活邮件发送失败，请稍后重试");
                logger.error("重新发送激活邮件失败，用户: {}, 邮箱: {}", user.getName(), email);
            }
            
        } catch (EmptyResultDataAccessException e) {
            result.put("success", false);
            result.put("message", "找不到待激活的账户");
            logger.warn("重新发送激活邮件失败，找不到用户: {}", email);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重新发送激活邮件异常: " + e.getMessage());
            logger.error("重新发送激活邮件异常，邮箱: {}, 错误: {}", email, e.getMessage());
        }
        return result;
    }
    
    /**
     * 清理过期的激活令牌
     */
    public void cleanExpiredActivationTokens() {
        try {
            String deleteSql = "DELETE FROM activation_tokens WHERE expires_at < ?";
            int rows = loginJdbcTemplate.update(deleteSql, Timestamp.valueOf(LocalDateTime.now()));
            if (rows > 0) {
                logger.info("清理过期激活令牌 {} 个", rows);
            }
        } catch (Exception e) {
            logger.error("清理过期激活令牌失败: {}", e.getMessage());
        }
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