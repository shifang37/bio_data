package com.example.bio_data.entity;

import jakarta.persistence.*;


@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    @Column(name = "password", nullable = false, length = 100)
    private String password;
    
    @Column(name = "email", nullable = false, length = 100)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;
    
    // 默认构造函数
    public User() {}
    
    // 带参构造函数
    public User(String name, String password, String email, Role role) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.role = role;
        this.status = UserStatus.PENDING; // 默认为待激活状态
    }
    
    // 带参构造函数（支持字符串角色）
    public User(String name, String password, String email, String roleValue) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.role = Role.fromValue(roleValue);
        this.status = UserStatus.PENDING; // 默认为待激活状态
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public void setRole(String roleValue) {
        this.role = Role.fromValue(roleValue);
    }
    
    public UserStatus getStatus() {
        return status;
    }
    
    public void setStatus(UserStatus status) {
        this.status = status;
    }
    
    public void setStatus(String statusValue) {
        this.status = UserStatus.fromValue(statusValue);
    }
    
    /**
     * 获取角色的字符串值
     */
    public String getRoleValue() {
        return role != null ? role.getValue() : null;
    }
    
    /**
     * 获取状态的字符串值
     */
    public String getStatusValue() {
        return status != null ? status.getValue() : null;
    }
    
    /**
     * 检查用户是否已激活
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }
    
    /**
     * 检查用户是否待激活
     */
    public boolean isPending() {
        return status != null && status.isPending();
    }
    
    /**
     * 检查用户是否被封禁
     */
    public boolean isBanned() {
        return status != null && status.isBanned();
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role=" + (role != null ? role.getValue() : null) +
                ", status=" + (status != null ? status.getValue() : null) +
                '}';
    }
} 