package com.checkin.controller;

import com.checkin.dto.*;
import com.checkin.entity.*;
import com.checkin.repository.*;
import com.checkin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private OperationLogService logService;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== Dashboard ====================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        LocalDateTime todayStart = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).with(LocalTime.MIN);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        Map<String, Object> data = new HashMap<>();
        data.put("teacherCount", teacherRepository.count());
        data.put("studentCount", studentRepository.count());
        data.put("courseCount", courseRepository.count());
        data.put("activeSessionCount", sessionRepository.countByStatus("IN_PROGRESS"));
        data.put("todaySignCount", recordRepository.countBySignTimeBetween(todayStart, tomorrowStart));
        return Result.success(data);
    }

    // ==================== 教师管理 ====================

    @GetMapping("/teachers")
    public Result<Map<String, Object>> getTeachers(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) page = 0; if (size <= 0) size = 10; if (size > 100) size = 100;
        if (keyword != null && keyword.isEmpty()) keyword = null;
        Page<Teacher> teacherPage = teacherRepository.searchByKeyword(keyword, PageRequest.of(page, size));
        Map<String, Object> result = new HashMap<>();
        result.put("content", teacherPage.getContent());
        result.put("totalElements", teacherPage.getTotalElements());
        result.put("totalPages", teacherPage.getTotalPages());
        result.put("currentPage", page);
        return Result.success(result);
    }

    @PostMapping("/teachers")
    public Result<Teacher> createTeacher(@RequestBody Map<String, String> body) {
        String teacherNo = body.get("teacherNo");
        String name = body.get("name");
        String password = body.getOrDefault("password", "123456");
        if (teacherNo == null || teacherNo.isEmpty()) return Result.error(400, "工号不能为空");
        if (name == null || name.isEmpty()) return Result.error(400, "姓名不能为空");
        if (password.length() < 6) return Result.error(400, "密码长度不能少于6位");
        if (teacherRepository.existsByTeacherNo(teacherNo)) return Result.error(400, "工号已存在");

        Teacher teacher = new Teacher();
        teacher.setTeacherNo(teacherNo);
        teacher.setName(name);
        teacher.setPasswordHash(passwordEncoder.encode(password));
        teacher.setStatus("ACTIVE");
        return Result.success(teacherRepository.save(teacher));
    }

    @PutMapping("/teachers/{id}")
    public Result<Teacher> updateTeacher(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new com.checkin.exception.BusinessException("教师不存在"));
        if (body.containsKey("name")) teacher.setName(body.get("name"));
        if (body.containsKey("teacherNo")) {
            String newNo = body.get("teacherNo");
            if (!newNo.equals(teacher.getTeacherNo()) && teacherRepository.existsByTeacherNo(newNo))
                return Result.error(400, "工号已存在");
            teacher.setTeacherNo(newNo);
        }
        return Result.success(teacherRepository.save(teacher));
    }

    @DeleteMapping("/teachers/{id}")
    public Result<Void> deleteTeacher(@PathVariable Long id) {
        if (!teacherRepository.existsById(id))
            throw new com.checkin.exception.BusinessException("教师不存在");
        // 检查教师是否还有课程
        List<Course> courses = courseRepository.findByTeacherId(id);
        if (!courses.isEmpty())
            return Result.error(400, "该教师下还有 " + courses.size() + " 门课程，请先删除课程");
        teacherRepository.deleteById(id);
        return Result.success();
    }

    @PostMapping("/teachers/{id}/reset-password")
    public Result<Void> resetTeacherPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new com.checkin.exception.BusinessException("教师不存在"));
        String newPwd = body.getOrDefault("password", "123456");
        if (newPwd.length() < 6) return Result.error(400, "密码长度不能少于6位");
        teacher.setPasswordHash(passwordEncoder.encode(newPwd));
        teacherRepository.save(teacher);
        return Result.success();
    }

    @PostMapping("/teachers/{id}/toggle-status")
    public Result<Teacher> toggleTeacherStatus(@PathVariable Long id) {
        Teacher teacher = teacherRepository.findById(id)
            .orElseThrow(() -> new com.checkin.exception.BusinessException("教师不存在"));
        teacher.setStatus("ACTIVE".equals(teacher.getStatus()) ? "DISABLED" : "ACTIVE");
        return Result.success(teacherRepository.save(teacher));
    }

    // ==================== 学生管理 ====================

    @GetMapping("/students")
    public Result<Map<String, Object>> getStudents(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String className,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) page = 0; if (size <= 0) size = 10; if (size > 100) size = 100;
        Page<Student> studentPage = studentService.searchStudents(keyword, className, PageRequest.of(page, size));
        Map<String, Object> result = new HashMap<>();
        result.put("content", studentPage.getContent());
        result.put("totalElements", studentPage.getTotalElements());
        result.put("totalPages", studentPage.getTotalPages());
        result.put("currentPage", page);
        return Result.success(result);
    }

    @PostMapping("/students")
    public Result<Student> createStudent(@Valid @RequestBody StudentRequest request) {
        Student student = studentService.createStudent(
            request.getStudentNo(), request.getName(),
            request.getPassword() != null ? request.getPassword() : "123456",
            request.getClassName());
        return Result.success(student);
    }

    @PutMapping("/students/{id}")
    public Result<Student> updateStudent(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        Student existing = studentService.getStudent(id);
        return Result.success(studentService.updateStudent(
            id, existing.getStudentNo(), request.getName(), request.getPassword(), request.getClassName()));
    }

    @DeleteMapping("/students/{id}")
    public Result<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    @PostMapping("/students/{id}/reset-password")
    public Result<Void> resetStudentPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        studentService.updateStudentPassword(id, body.getOrDefault("password", "123456"));
        return Result.success();
    }

    @PostMapping("/students/{id}/toggle-status")
    public Result<Student> toggleStudentStatus(@PathVariable Long id) {
        return Result.success(studentService.toggleStudentStatus(id));
    }

    // ==================== 课程查看 ====================

    @GetMapping("/courses")
    public Result<Map<String, Object>> getCourses(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) page = 0; if (size <= 0) size = 10; if (size > 100) size = 100;
        Page<Course> coursePage = courseRepository.findAll(PageRequest.of(page, size));
        // 构建包含教师名的返回数据
        List<Map<String, Object>> enrichedCourses = coursePage.getContent().stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", c.getId());
            item.put("courseName", c.getCourseName());
            item.put("teacherId", c.getTeacherId());
            item.put("createdAt", c.getCreatedAt());
            teacherRepository.findById(c.getTeacherId()).ifPresent(t -> item.put("teacherName", t.getName()));
            return item;
        }).collect(java.util.stream.Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", enrichedCourses);
        result.put("totalElements", coursePage.getTotalElements());
        result.put("totalPages", coursePage.getTotalPages());
        result.put("currentPage", page);
        return Result.success(result);
    }

    // ==================== 系统配置 ====================

    @GetMapping("/configs")
    public Result<List<SystemConfig>> getConfigs() {
        return Result.success(configService.getAllConfigs());
    }

    @PutMapping("/configs")
    public Result<SystemConfig> updateConfig(@RequestBody Map<String, String> body, HttpSession session) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || key.isEmpty()) return Result.error(400, "配置键不能为空");
        if (value == null) return Result.error(400, "配置值不能为空");
        Long adminId = (Long) session.getAttribute("userId");
        return Result.success(configService.updateConfig(key, value, adminId));
    }

    // ==================== 操作日志 ====================

    @GetMapping("/logs")
    public Result<Map<String, Object>> getLogs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) page = 0; if (size <= 0) size = 20; if (size > 100) size = 100;
        Page<OperationLog> logPage = logService.getLogs(PageRequest.of(page, size));
        Map<String, Object> result = new HashMap<>();
        result.put("content", logPage.getContent());
        result.put("totalElements", logPage.getTotalElements());
        result.put("totalPages", logPage.getTotalPages());
        result.put("currentPage", page);
        return Result.success(result);
    }
}
