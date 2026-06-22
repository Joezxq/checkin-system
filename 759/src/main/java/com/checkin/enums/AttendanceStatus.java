package com.checkin.enums;

/**
 * 考勤状态枚举
 */
public enum AttendanceStatus {
    NORMAL("正常签到"),
    LEAVE("请假"),
    ABSENT("缺勤"),
    ABNORMAL("异常签到");

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
