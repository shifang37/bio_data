package com.example.bio_data.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户邮箱激活令牌实体
 */
@Entity
@Table(name = "activation_tokens")
public class ActivationToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    // 默认构造函数
    public ActivationToken() {
        this.createdAt = LocalDateTime.now();
    }
    
    // 带参构造函数
    public ActivationToken(Long userId, String token, LocalDateTime expiresAt) {
        this();
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * 检查令牌是否已过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 检查令牌是否有效（未过期）
     */
    public boolean isValid() {
        return !isExpired();
    }
    
    @Override
    public String toString() {
        return "ActivationToken{" +
                "id=" + id +
                ", userId=" + userId +
                ", token='" + token.substring(0, 8) + "...'" +
                ", expiresAt=" + expiresAt +
                ", createdAt=" + createdAt +
                '}';
    }
}
