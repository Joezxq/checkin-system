package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 更新学生请求
 */
@Data
public class UpdateStudentRequest {

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String password;

    private String className;
}
