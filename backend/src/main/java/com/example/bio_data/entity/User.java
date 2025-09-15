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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;
    
    // 默认构造函数
    public User() {}
    
    // 带参构造函数
    public User(String name, String password, Role role) {
        this.name = name;
        this.password = password;
        this.role = role;
    }
    
    // 带参构造函数（支持字符串角色）
    public User(String name, String password, String roleValue) {
        this.name = name;
        this.password = password;
        this.role = Role.fromValue(roleValue);
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
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public void setRole(String roleValue) {
        this.role = Role.fromValue(roleValue);
    }
    
    /**
     * 获取角色的字符串值
     */
    public String getRoleValue() {
        return role != null ? role.getValue() : null;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", role=" + (role != null ? role.getValue() : null) +
                '}';
    }
} 