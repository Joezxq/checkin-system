package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 发起签到活动请求
 */
@Data
public class OpenSessionRequest {

    @Min(value = 1, message = "持续时间必须大于0")
    @Max(value = 180, message = "持续时间不能超过180分钟")
    private Integer durationMinutes = 10; // 默认10分钟
}
