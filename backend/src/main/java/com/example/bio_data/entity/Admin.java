package com.example.bio_data.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "admin")
public class Admin {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    @Column(name = "password", nullable = false, length = 100)
    private String password;
    
    @Column(name = "permission", length = 50)
    private String permission;
    
    // 默认构造函数
    public Admin() {}
    
    // 带参构造函数
    public Admin(String name, String password, String permission) {
        this.name = name;
        this.password = password;
        this.permission = permission;
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
    
    public String getPermission() {
        return permission;
    }
    
    public void setPermission(String permission) {
        this.permission = permission;
    }
    
    @Override
    public String toString() {
        return "Admin{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", permission='" + permission + '\'' +
                '}';
    }
} 