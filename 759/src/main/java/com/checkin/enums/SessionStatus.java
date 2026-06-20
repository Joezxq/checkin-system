package com.checkin.enums;

/**
 * 签到活动状态枚举
 */
public enum SessionStatus {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    CLOSED("已关闭"),
    EXPIRED("已过期"),
    CANCELLED("已取消");

    private final String description;

    SessionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isActive(String status) {
        return IN_PROGRESS.name().equals(status);
    }

    public static boolean canModify(String status) {
        return NOT_STARTED.name().equals(status) || IN_PROGRESS.name().equals(status);
    }

    public static boolean canExtend(String status) {
        return IN_PROGRESS.name().equals(status);
    }

    public static boolean canClose(String status) {
        return IN_PROGRESS.name().equals(status);
    }

    public static boolean canCancel(String status) {
        return NOT_STARTED.name().equals(status) || IN_PROGRESS.name().equals(status);
    }
}
