package com.example.bio_data.controller;

import com.example.bio_data.service.AuthService;
import com.example.bio_data.service.PermissionService;
import com.example.bio_data.service.CaptchaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:8081"})
public class AuthController {

    @Autowired
    private AuthService authService;
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private CaptchaService captchaService;

    /**
     * 统一登录接口 - 允许所有角色用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名和密码不能为空"
                ));
            }

            Map<String, Object> result = authService.login(username, password);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "登录异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 用户登录 - 只允许guest角色用户登录
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
     * 管理员登录 - 允许admin和internal角色用户登录
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
     * 用户注册（新版本 - 需要邮箱和验证码）
     * 只能注册普通用户，角色固定为"guest"，状态为"pending"
     */
    @PostMapping("/register/user")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, String> registerRequest) {
        try {
            String username = registerRequest.get("username");
            String password = registerRequest.get("password");
            String email = registerRequest.get("email");
            String captchaToken = registerRequest.get("captchaToken");
            String captchaInput = registerRequest.get("captchaInput");

            // 参数验证
            if (username == null || password == null || email == null || 
                captchaToken == null || captchaInput == null ||
                username.trim().isEmpty() || password.trim().isEmpty() || 
                email.trim().isEmpty() || captchaInput.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "用户名、密码、邮箱和验证码都不能为空"
                ));
            }

            // 简单的邮箱格式验证
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "邮箱格式不正确"
                ));
            }

            Map<String, Object> result = authService.registerUser(username, password, email, captchaToken, captchaInput);
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
     * 获取图形验证码
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> getCaptcha() {
        try {
            String sessionId = UUID.randomUUID().toString();
            String captchaImage = captchaService.generateCaptcha(sessionId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "captchaToken", sessionId,
                "captchaImage", captchaImage
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "生成验证码失败: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 激活用户账户
     */
    @GetMapping("/activate")
    public ResponseEntity<Map<String, Object>> activateAccount(@RequestParam String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "激活令牌不能为空"
                ));
            }

            Map<String, Object> result = authService.activateAccount(token);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "激活异常: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 重新发送激活邮件
     */
    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, Object>> resendActivationEmail(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "邮箱不能为空"
                ));
            }

            // 简单的邮箱格式验证
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "邮箱格式不正确"
                ));
            }

            Map<String, Object> result = authService.resendActivationEmail(email);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "重新发送激活邮件异常: " + e.getMessage()
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