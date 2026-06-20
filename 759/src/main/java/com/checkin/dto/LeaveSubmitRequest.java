package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 学生提交请假请求
 */
@Data
public class LeaveSubmitRequest {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** 关联签到活动ID，可为空 */
    private Long sessionId;

    @NotBlank(message = "请假类型不能为空")
    @Size(max = 20)
    private String leaveType; // PERSONAL, SICK, OTHER

    @NotBlank(message = "请假原因不能为空")
    @Size(max = 1000, message = "请假原因不能超过1000字")
    private String reason;
}
