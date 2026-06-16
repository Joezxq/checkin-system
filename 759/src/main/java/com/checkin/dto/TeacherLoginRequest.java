package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 教师登录请求
 */
@Data
public class TeacherLoginRequest {

    @NotBlank(message = "工号不能为空")
    private String teacherNo;

    @NotBlank(message = "密码不能为空")
    private String password;
}
