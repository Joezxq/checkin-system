package com.checkin.enums;

/**
 * 用户角色枚举
 */
public enum UserRole {
    ADMIN("管理员"),
    TEACHER("教师"),
    STUDENT("学生");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
