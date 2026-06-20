package com.checkin.service;

import com.checkin.entity.Admin;
import com.checkin.entity.Student;
import com.checkin.entity.Teacher;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AdminRepository;
import com.checkin.repository.StudentRepository;
import com.checkin.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * 认证服务
 */
@Service
public class AuthService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OperationLogService logService;

    /**
     * 学生登录
     */
    public Student studentLogin(String studentNo, String password, HttpSession session) {
        Student student = studentRepository.findByStudentNo(studentNo)
            .orElseThrow(() -> new BusinessException("学号或密码错误"));

        if (!passwordEncoder.matches(password, student.getPasswordHash())) {
            throw new BusinessException("学号或密码错误");
        }

        if ("DISABLED".equals(student.getStatus())) {
            throw new BusinessException("此账号已被禁用，请联系管理员");
        }

        // 设置 Session
        session.setAttribute("userType", "STUDENT");
        session.setAttribute("userId", student.getId());
        session.setAttribute("userName", student.getName());

        logService.log("STUDENT", student.getId(), student.getName(), "LOGIN", "Student", student.getId(), null, null);
        return student;
    }

    /**
     * 教师登录
     */
    public Teacher teacherLogin(String teacherNo, String password, HttpSession session) {
        Teacher teacher = teacherRepository.findByTeacherNo(teacherNo)
            .orElseThrow(() -> new BusinessException("工号或密码错误"));

        if (!passwordEncoder.matches(password, teacher.getPasswordHash())) {
            throw new BusinessException("工号或密码错误");
        }

        if ("DISABLED".equals(teacher.getStatus())) {
            throw new BusinessException("此账号已被禁用，请联系管理员");
        }

        // 设置 Session
        session.setAttribute("userType", "TEACHER");
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userName", teacher.getName());

        logService.log("TEACHER", teacher.getId(), teacher.getName(), "LOGIN", "Teacher", teacher.getId(), null, null);
        return teacher;
    }

    /**
     * 教师注册
     */
    public Teacher teacherRegister(String teacherNo, String name, String password, String confirmPassword, HttpSession session) {
        // 校验两次密码是否一致
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("两次密码不一致");
        }

        // 校验密码长度
        if (password.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }

        // 校验工号唯一性
        if (teacherRepository.existsByTeacherNo(teacherNo)) {
            throw new BusinessException("工号已存在");
        }

        // 创建教师
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(teacherNo);
        teacher.setName(name);
        teacher.setPasswordHash(passwordEncoder.encode(password));
        teacherRepository.save(teacher);

        // 自动登录
        session.setAttribute("userType", "TEACHER");
        session.setAttribute("userId", teacher.getId());
        session.setAttribute("userName", teacher.getName());

        return teacher;
    }

    /**
     * 管理员登录
     */
    public Admin adminLogin(String username, String password, HttpSession session) {
        Admin admin = adminRepository.findByUsername(username)
            .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        if ("DISABLED".equals(admin.getStatus())) {
            throw new BusinessException("此账号已被禁用");
        }

        session.setAttribute("userType", "ADMIN");
        session.setAttribute("userId", admin.getId());
        session.setAttribute("userName", admin.getName());

        logService.log("ADMIN", admin.getId(), admin.getName(), "LOGIN", "Admin", admin.getId(), null, null);
        return admin;
    }

    /**
     * 登出
     */
    public void logout(HttpSession session) {
        Object userType = session.getAttribute("userType");
        Object userId = session.getAttribute("userId");
        Object userName = session.getAttribute("userName");
        if (userType != null && userId != null) {
            logService.log(userType.toString(), (Long) userId,
                userName != null ? userName.toString() : null,
                "LOGOUT", null, null, null, null);
        }
        session.invalidate();
    }
}
