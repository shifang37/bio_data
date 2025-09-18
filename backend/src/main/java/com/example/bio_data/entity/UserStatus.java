package com.example.bio_data.entity;

/**
 * 用户状态枚举
 * pending - 待激活（注册后默认状态）
 * active - 已激活（邮箱激活后）
 * banned - 已封禁（管理员操作）
 */
public enum UserStatus {
    PENDING("pending", "待激活"),
    ACTIVE("active", "已激活"),
    BANNED("banned", "已封禁");

    private final String value;
    private final String description;

    UserStatus(String value, String description) {
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
    public static UserStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        
        for (UserStatus status : UserStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown user status value: " + value);
    }

    /**
     * 检查是否为激活状态
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 检查是否为待激活状态
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 检查是否为封禁状态
     */
    public boolean isBanned() {
        return this == BANNED;
    }

    @Override
    public String toString() {
        return value;
    }
}
