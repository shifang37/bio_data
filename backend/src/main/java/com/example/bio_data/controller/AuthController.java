package com.example.bio_data.controller;

import com.example.bio_data.entity.User;
import com.example.bio_data.entity.Admin;
import com.example.bio_data.service.AuthService;
import com.example.bio_data.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081"})
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private PermissionService permissionService;

    /**
     * 用户登录
     */
    @PostMapping("/login/user")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和密码不能为空"
                ));
            }

            Map<String, Object> result = authService.loginUser(username, password);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "登录异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 管理员登录
     */
    @PostMapping("/login/admin")
    public ResponseEntity<Map<String, Object>> loginAdmin(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和密码不能为空"
                ));
            }

            Map<String, Object> result = authService.loginAdmin(username, password);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "登录异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 用户注册
     * 只能注册普通用户，权限固定为"user"
     */
    @PostMapping("/register/user")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> registerRequest) {
        try {
            String username = registerRequest.get("username");
            String password = registerRequest.get("password");
            // 用户注册时权限固定为"user"，不接受前端传递的权限参数
            String permission = "user";

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和密码不能为空"
                ));
            }

            Map<String, Object> result = authService.registerUser(username, password, permission);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "注册异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 管理员注册 - 已禁用
     * 管理员用户由系统管理员手动添加，不允许通过接口注册
     */
    @PostMapping("/register/admin")
    public ResponseEntity<Map<String, Object>> registerAdmin(@RequestBody Map<String, String> registerRequest) {
        return ResponseEntity.status(403).body(Map.of(
            "success", false,
            "message", "管理员账户由系统管理员添加，不允许通过注册接口创建"
        ));
    }

    /**
     * 获取所有用户（管理员权限）
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = authService.getAllUsers();
            // 隐藏密码字段
            users.forEach(user -> user.setPassword("***"));
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", users,
                "count", users.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取用户列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取所有管理员（超级管理员权限）
     */
    @GetMapping("/admins")
    public ResponseEntity<?> getAllAdmins() {
        try {
            List<Admin> admins = authService.getAllAdmins();
            // 隐藏密码字段
            admins.forEach(admin -> admin.setPassword("***"));
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", admins,
                "count", admins.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取管理员列表失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            User user = authService.getUserById(id);
            if (user != null) {
                user.setPassword("***"); // 隐藏密码
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", user
                ));
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "用户不存在"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取用户信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 根据ID获取管理员信息
     */
    @GetMapping("/admin/{id}")
    public ResponseEntity<?> getAdminById(@PathVariable Long id) {
        try {
            Admin admin = authService.getAdminById(id);
            if (admin != null) {
                admin.setPassword("***"); // 隐藏密码
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", admin
                ));
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "管理员不存在"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "获取管理员信息失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 统一登录接口（自动判断用户类型）
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");
            String userType = loginRequest.getOrDefault("userType", "user"); // 默认为普通用户

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和密码不能为空"
                ));
            }

            Map<String, Object> result;
            if ("admin".equalsIgnoreCase(userType)) {
                result = authService.loginAdmin(username, password);
            } else {
                result = authService.loginUser(username, password);
                // 如果普通用户登录失败，尝试管理员登录
                if (!(Boolean) result.get("success")) {
                    result = authService.loginAdmin(username, password);
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "登录异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 检查用户名是否存在（忘记密码第一步）
     */
    @PostMapping("/check-user")
    public ResponseEntity<Map<String, Object>> checkUserExists(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名不能为空"
                ));
            }

            Map<String, Object> result = authService.checkUserExists(username);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "检查用户失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 重置用户密码（仅普通用户）
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String newPassword = request.get("newPassword");

            if (username == null || newPassword == null || 
                username.trim().isEmpty() || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和新密码不能为空"
                ));
            }

            if (newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "新密码长度至少6位"
                ));
            }

            Map<String, Object> result = authService.resetUserPassword(username, newPassword);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "密码重置失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 测试login数据库连接
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            boolean connected = authService.testConnection();
            return ResponseEntity.ok(Map.of(
                "connected", connected,
                "message", connected ? "Login数据库连接正常" : "Login数据库连接失败",
                "database", "login"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "connected", false,
                "message", "连接测试失败: " + e.getMessage(),
                "database", "login"
            ));
        }
    }

    /**
     * 获取用户权限信息
     */
    @GetMapping("/permission")
    public ResponseEntity<Map<String, Object>> getUserPermission(
            @RequestParam Long userId,
            @RequestParam String userType) {
        try {
            Map<String, Object> permissionInfo = permissionService.getUserPermissionInfo(userId, userType);
            return ResponseEntity.ok(permissionInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "获取用户权限信息失败: " + e.getMessage()
            ));
        }
    }
} 