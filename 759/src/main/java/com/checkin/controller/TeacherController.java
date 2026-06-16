package com.checkin.controller;

import com.checkin.dto.*;
import com.checkin.entity.*;
import com.checkin.exception.BusinessException;
import com.checkin.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师控制器
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private AttendanceService attendanceService;

    // ==================== 课程管理 ====================

    /**
     * 获取教师的所有课程
     */
    @GetMapping("/courses")
    public Result<List<Course>> getCourses(HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        List<Course> courses = courseService.getTeacherCourses(teacherId);
        return Result.success(courses);
    }

    /**
     * 创建课程
     */
    @PostMapping("/courses")
    public Result<Course> createCourse(@Valid @RequestBody CourseRequest request, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Course course = courseService.createCourse(request.getCourseName(), teacherId);
        return Result.success(course);
    }

    /**
     * 更新课程
     */
    @PutMapping("/courses/{id}")
    public Result<Course> updateCourse(
        @PathVariable Long id,
        @Valid @RequestBody CourseRequest request,
        HttpSession session
    ) {
        Long teacherId = (Long) session.getAttribute("userId");
        Course course = courseService.updateCourse(id, request.getCourseName(), teacherId);
        return Result.success(course);
    }

    /**
     * 删除课程
     */
    @DeleteMapping("/courses/{id}")
    public Result<Void> deleteCourse(@PathVariable Long id, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        courseService.deleteCourse(id, teacherId);
        return Result.success();
    }

    // ==================== 学生管理 ====================

    /**
     * 分页查询学生
     */
    @GetMapping("/students")
    public Result<Map<String, Object>> getStudents(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String className,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;
        if (className != null && className.isEmpty()) className = null;
        if (keyword != null && keyword.isEmpty()) keyword = null;

        Pageable pageable = PageRequest.of(page, size);
        Page<Student> studentPage = studentService.searchStudents(keyword, className, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("content", studentPage.getContent());
        result.put("totalElements", studentPage.getTotalElements());
        result.put("totalPages", studentPage.getTotalPages());
        result.put("currentPage", page);

        return Result.success(result);
    }

    /**
     * 获取所有班级名
     */
    @GetMapping("/students/classes")
    public Result<List<String>> getStudentClasses() {
        return Result.success(studentService.getDistinctClassNames());
    }

    /**
     * 批量导入学生
     */
    @PostMapping("/students/batch")
    public Result<Map<String, Object>> batchCreateStudents(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> students = (List<Map<String, String>>) body.get("students");
        Map<String, Object> result = studentService.createStudents(students);
        return Result.success(result);
    }

    /**
     * 创建学生
     */
    @PostMapping("/students")
    public Result<Student> createStudent(@Valid @RequestBody StudentRequest request) {
        Student student = studentService.createStudent(
            request.getStudentNo(),
            request.getName(),
            request.getPassword() != null ? request.getPassword() : "123456",
            request.getClassName()
        );
        return Result.success(student);
    }

    /**
     * 获取单个学生信息
     */
    @GetMapping("/students/{id}")
    public Result<Student> getStudent(@PathVariable Long id) {
        Student student = studentService.getStudent(id);
        return Result.success(student);
    }

    /**
     * 更新学生
     */
    @PutMapping("/students/{id}")
    public Result<Student> updateStudent(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        // 先获取学生信息以保留学号
        Student existingStudent = studentService.getStudent(id);

        Student student = studentService.updateStudent(
            id,
            existingStudent.getStudentNo(), // 保持学号不变
            request.getName(),
            request.getPassword(),
            request.getClassName()
        );
        return Result.success(student);
    }

    /**
     * 删除学生
     */
    @DeleteMapping("/students/{id}")
    public Result<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return Result.success();
    }

    // ==================== 选课管理 ====================

    /**
     * 将学生加入课程
     */
    @PostMapping("/course/{courseId}/enroll")
    public Result<Enrollment> enrollStudent(@PathVariable Long courseId, @RequestBody Map<String, Long> body, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Long studentId = body.get("studentId");
        if (studentId == null) {
            throw new BusinessException(400, "学生ID不能为空");
        }
        Enrollment enrollment = enrollmentService.enrollStudent(courseId, studentId, teacherId);
        return Result.success(enrollment);
    }

    /**
     * 批量将学生加入课程
     */
    @PostMapping("/course/{courseId}/enroll/batch")
    public Result<Map<String, Object>> batchEnrollStudents(
        @PathVariable Long courseId,
        @RequestBody Map<String, Object> body,
        HttpSession session
    ) {
        Long teacherId = (Long) session.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Integer> idList = (List<Integer>) body.get("studentIds");
        List<Long> studentIds = idList.stream().map(Long::valueOf).collect(java.util.stream.Collectors.toList());
        return Result.success(enrollmentService.enrollStudents(courseId, studentIds, teacherId));
    }

    /**
     * 移除学生
     */
    @DeleteMapping("/course/{courseId}/enroll/{studentId}")
    public Result<Void> removeStudent(@PathVariable Long courseId, @PathVariable Long studentId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        enrollmentService.removeStudent(courseId, studentId, teacherId);
        return Result.success();
    }

    /**
     * 批量移除学生
     */
    @DeleteMapping("/course/{courseId}/enroll/batch")
    public Result<Map<String, Object>> batchRemoveStudents(
        @PathVariable Long courseId,
        @RequestBody Map<String, Object> body,
        HttpSession session
    ) {
        Long teacherId = (Long) session.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Integer> idList = (List<Integer>) body.get("studentIds");
        List<Long> studentIds = idList.stream().map(Long::valueOf).collect(java.util.stream.Collectors.toList());
        return Result.success(enrollmentService.removeStudents(courseId, studentIds, teacherId));
    }

    /**
     * 获取课程的所有学生
     */
    @GetMapping("/course/{courseId}/students")
    public Result<List<Student>> getCourseStudents(@PathVariable Long courseId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        List<Long> studentIds = enrollmentService.getCourseStudentIds(courseId, teacherId);
        List<Student> students = studentService.getStudentsByIds(studentIds);
        return Result.success(students);
    }

    // ==================== 签到管理 ====================

    /**
     * 获取课程的活跃签到活动（教师端）
     */
    @GetMapping("/course/{courseId}/active-session")
    public Result<AttendanceSession> getActiveSession(@PathVariable Long courseId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        // 验证课程属于该教师
        Course course = courseService.getCourse(courseId);
        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程");
        }
        return Result.success(attendanceService.getActiveSession(courseId));
    }

    /**
     * 发起签到活动
     */
    @PostMapping("/course/{courseId}/sessions/open")
    public Result<AttendanceSession> openSession(
        @PathVariable Long courseId,
        @Valid @RequestBody(required = false) OpenSessionRequest request,
        HttpSession session
    ) {
        Long teacherId = (Long) session.getAttribute("userId");
        Integer durationMinutes = (request != null && request.getDurationMinutes() != null)
            ? request.getDurationMinutes() : 10;
        AttendanceSession attendanceSession = attendanceService.openSession(courseId, durationMinutes, teacherId);
        return Result.success(attendanceSession);
    }

    /**
     * 关闭签到活动（返回签到统计数据）
     */
    @PostMapping("/sessions/{sessionId}/close")
    public Result<Map<String, Object>> closeSession(@PathVariable Long sessionId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Map<String, Object> stats = attendanceService.closeSession(sessionId, teacherId);
        return Result.success(stats);
    }

    /**
     * 获取实时签到数据
     */
    @GetMapping("/sessions/{sessionId}/realtime")
    public Result<Map<String, Object>> getRealtimeData(@PathVariable Long sessionId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Map<String, Object> data = attendanceService.getRealtimeData(sessionId, teacherId);
        return Result.success(data);
    }

    /**
     * 获取单次签到活动的统计数据
     */
    @GetMapping("/sessions/{sessionId}/statistics")
    public Result<Map<String, Object>> getSessionStatistics(@PathVariable Long sessionId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Map<String, Object> stats = attendanceService.getSessionStatistics(sessionId, teacherId);
        return Result.success(stats);
    }

    /**
     * 获取课程维度的签到统计
     */
    @GetMapping("/courses/{courseId}/statistics")
    public Result<Map<String, Object>> getCourseStatistics(@PathVariable Long courseId, HttpSession session) {
        Long teacherId = (Long) session.getAttribute("userId");
        Map<String, Object> stats = attendanceService.getCourseStatistics(courseId, teacherId);
        return Result.success(stats);
    }

    /**
     * 获取历史签到记录
     */
    @GetMapping("/sessions/history")
    public Result<Map<String, Object>> getHistorySessions(
        @RequestParam(required = false) Long courseId,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        HttpSession session
    ) {
        Long teacherId = (Long) session.getAttribute("userId");

        // 参数校验和修正
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size);

        LocalDateTime dateFromParsed = null;
        LocalDateTime dateToParsed = null;

        if (dateFrom != null && !dateFrom.isEmpty()) {
            dateFromParsed = LocalDateTime.parse(dateFrom + " 00:00:00",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            dateToParsed = LocalDateTime.parse(dateTo + " 23:59:59",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        Page<AttendanceSession> sessionPage = attendanceService.getHistorySessions(
            courseId, dateFromParsed, dateToParsed, pageable, teacherId
        );

        Map<String, Object> result = new HashMap<>();
        result.put("content", sessionPage.getContent());
        result.put("totalElements", sessionPage.getTotalElements());
        result.put("totalPages", sessionPage.getTotalPages());
        result.put("currentPage", page);

        return Result.success(result);
    }
}
