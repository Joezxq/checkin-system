package com.checkin.enums;

/**
 * 请假类型枚举
 */
public enum LeaveType {
    PERSONAL("事假"),
    SICK("病假"),
    OTHER("其他");

    private final String description;

    LeaveType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isValid(String type) {
        try {
            valueOf(type);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
