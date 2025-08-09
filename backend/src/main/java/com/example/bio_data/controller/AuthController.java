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
     * 检查用户是否存在
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
                "message", "检查用户存在性异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String newPassword = request.get("newPassword");

            if (username == null || newPassword == null || username.trim().isEmpty() || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和新密码不能为空"
                ));
            }

            Map<String, Object> result = authService.resetUserPassword(username, newPassword);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "重置密码异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 测试数据库连接
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