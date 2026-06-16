package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建课程请求
 */
@Data
public class CourseRequest {

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 200, message = "课程名称不能超过200字")
    private String courseName;
}
