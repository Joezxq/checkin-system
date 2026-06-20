package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.Size;

/**
 * 教师审批请假请求
 */
@Data
public class LeaveApproveRequest {

    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
