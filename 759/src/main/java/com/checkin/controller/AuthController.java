package com.checkin.controller;

import com.checkin.dto.Result;
import com.checkin.dto.StudentLoginRequest;
import com.checkin.dto.TeacherLoginRequest;
import com.checkin.dto.TeacherRegisterRequest;
import com.checkin.entity.Student;
import com.checkin.entity.Teacher;
import com.checkin.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 学生登录
     */
    @PostMapping("/student/login")
    public Result<Student> studentLogin(@Valid @RequestBody StudentLoginRequest request, HttpSession session) {
        Student student = authService.studentLogin(request.getStudentNo(), request.getPassword(), session);
        return Result.success(student);
    }

    /**
     * 教师登录
     */
    @PostMapping("/teacher/login")
    public Result<Teacher> teacherLogin(@Valid @RequestBody TeacherLoginRequest request, HttpSession session) {
        Teacher teacher = authService.teacherLogin(request.getTeacherNo(), request.getPassword(), session);
        return Result.success(teacher);
    }

    /**
     * 教师注册
     */
    @PostMapping("/teacher/register")
    public Result<Teacher> teacherRegister(@Valid @RequestBody TeacherRegisterRequest request, HttpSession session) {
        Teacher teacher = authService.teacherRegister(
            request.getTeacherNo(), request.getName(),
            request.getPassword(), request.getConfirmPassword(), session);
        return Result.success(teacher);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        authService.logout(session);
        return Result.success();
    }
}
