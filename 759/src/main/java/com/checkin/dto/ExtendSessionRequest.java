package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 延长签到活动请求
 */
@Data
public class ExtendSessionRequest {

    @Min(value = 1, message = "延长分钟数必须大于0")
    @Max(value = 60, message = "延长分钟数不能超过60分钟")
    private Integer extendMinutes;
}
