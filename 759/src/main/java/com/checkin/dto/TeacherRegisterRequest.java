package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 教师注册请求
 */
@Data
public class TeacherRegisterRequest {

    @NotBlank(message = "工号不能为空")
    private String teacherNo;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
