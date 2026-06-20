package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * 发起签到活动请求
 */
@Data
public class OpenSessionRequest {

    /** 签到活动标题 */
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    /** 总的签到持续时间（分钟），向后兼容 */
    @Min(value = 1, message = "持续时间必须大于0")
    @Max(value = 180, message = "持续时间不能超过180分钟")
    private Integer durationMinutes = 10;

    /** 正常签到截止时间（相对开始时间的分钟数），若不传则等于 durationMinutes */
    @Min(value = 1, message = "正常签到时间必须大于0")
    @Max(value = 180, message = "正常签到时间不能超过180分钟")
    private Integer normalEndTimeMinutes;

    /** 迟到宽限时间（相对 normalEndTime 的分钟数） */
    @Min(value = 0, message = "迟到宽限时间不能为负数")
    @Max(value = 60, message = "迟到宽限时间不能超过60分钟")
    private Integer lateEndTimeMinutes;

    /** 是否允许迟到签到 */
    private Boolean allowLate = true;

    /** 签到方式: WEB / QR */
    @Size(max = 20)
    private String signMethod = "WEB";

    /** 是否允许扫码签到 */
    private Boolean allowQrCode = true;

    /** 是否开启设备校验 */
    private Boolean allowDeviceCheck = false;
}
