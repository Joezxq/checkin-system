package com.checkin.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 创建/更新学生请求
 */
@Data
public class StudentRequest {

    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String password;

    private String className;
}
