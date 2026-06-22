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
import java.util.HashMap;
import java.util.Map;

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
     * 管理员登录
     */
    @PostMapping("/admin/login")
    public Result<?> adminLogin(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.error(400, "用户名和密码不能为空");
        }
        return Result.success(authService.adminLogin(username, password, session));
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(HttpSession session) {
        Object userType = session.getAttribute("userType");
        if (userType == null) {
            return Result.error(401, "未登录");
        }
        Map<String, Object> user = new HashMap<>();
        user.put("userType", userType);
        user.put("userId", session.getAttribute("userId"));
        user.put("userName", session.getAttribute("userName"));
        return Result.success(user);
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
