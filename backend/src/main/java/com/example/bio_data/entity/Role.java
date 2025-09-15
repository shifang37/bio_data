package com.example.bio_data.entity;

/**
 * 用户角色枚举
 * guest - 普通用户
 * internal - 内部用户
 * admin - 管理员
 */
public enum Role {
    GUEST("guest", "普通用户"),
    INTERNAL("internal", "内部用户"), 
    ADMIN("admin", "管理员");

    private final String value;
    private final String description;

    Role(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串值获取对应的枚举
     */
    public static Role fromValue(String value) {
        if (value == null) {
            return null;
        }
        
        for (Role role : Role.values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role value: " + value);
    }

    /**
     * 检查是否为管理员角色
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 检查是否为内部用户或管理员
     */
    public boolean isInternal() {
        return this == INTERNAL || this == ADMIN;
    }

    @Override
    public String toString() {
        return value;
    }
}
