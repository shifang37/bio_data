package com.example.bio_data.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户表写权限实体类
 * 用于记录内部用户对具体表的写入权限授权
 */
@Entity
@Table(name = "user_table_write_access")
public class UserTableWriteAccess {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;
    
    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;
    
    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    // 构造函数
    public UserTableWriteAccess() {
        this.createdAt = LocalDateTime.now();
    }
    
    public UserTableWriteAccess(Long userId, String databaseName, String tableName, Long grantedBy) {
        this();
        this.userId = userId;
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.grantedBy = grantedBy;
    }
    
    public UserTableWriteAccess(Long userId, String databaseName, String tableName, Long grantedBy, LocalDateTime expiresAt) {
        this(userId, databaseName, tableName, grantedBy);
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
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public Long getGrantedBy() {
        return grantedBy;
    }
    
    public void setGrantedBy(Long grantedBy) {
        this.grantedBy = grantedBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    /**
     * 检查权限是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // 永久有效
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 检查权限是否有效（未过期）
     */
    public boolean isValid() {
        return !isExpired();
    }
    
    @Override
    public String toString() {
        return "UserTableWriteAccess{" +
                "id=" + id +
                ", userId=" + userId +
                ", databaseName='" + databaseName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", grantedBy=" + grantedBy +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
