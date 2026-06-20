package com.checkin.controller;

import com.checkin.dto.LeaveSubmitRequest;
import com.checkin.dto.Result;
import com.checkin.entity.AttendanceRecord;
import com.checkin.entity.AttendanceSession;
import com.checkin.entity.Course;
import com.checkin.entity.Enrollment;
import com.checkin.entity.LeaveRequest;
import com.checkin.service.AttendanceService;
import com.checkin.service.CourseService;
import com.checkin.service.EnrollmentService;
import com.checkin.service.LeaveService;
import com.checkin.util.DeviceUtil;
import com.checkin.util.IPUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生控制器
 */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private IPUtil ipUtil;

    @Autowired
    private DeviceUtil deviceUtil;

    @Autowired
    private LeaveService leaveService;

    /**
     * 获取学生的课程列表
     */
    @GetMapping("/courses")
    public Result<List<Course>> getCourses(HttpSession session) {
        Long studentId = (Long) session.getAttribute("userId");
        List<Enrollment> enrollments = enrollmentService.getStudentCourses(studentId);

        // 批量查询课程，避免 N+1 问题
        List<Long> courseIds = enrollments.stream()
            .map(Enrollment::getCourseId)
            .collect(java.util.stream.Collectors.toList());

        if (courseIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<Course> courses = courseService.getCoursesByIds(courseIds);
        return Result.success(courses);
    }

    /**
     * 获取课程的活动签到活动
     */
    @GetMapping("/course/{courseId}/active-session")
    public Result<AttendanceSession> getActiveSession(@PathVariable Long courseId) {
        AttendanceSession session = attendanceService.getActiveSession(courseId);
        return Result.success(session);
    }

    /**
     * 检查学生是否已签到
     */
    @GetMapping("/session/{sessionId}/check")
    public Result<Map<String, Object>> checkSignStatus(
        @PathVariable Long sessionId,
        HttpSession session
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        boolean hasSigned = attendanceService.hasStudentSigned(sessionId, studentId);

        Map<String, Object> result = new HashMap<>();
        result.put("hasSigned", hasSigned);

        return Result.success(result);
    }

    /**
     * 学生签到
     */
    @PostMapping("/session/{sessionId}/sign")
    public Result<Map<String, Object>> signIn(
        @PathVariable Long sessionId,
        HttpSession session,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Long studentId = (Long) session.getAttribute("userId");

        // 获取客户端 IP
        String clientIp = ipUtil.getClientIP(request);

        // 验证 IP 格式
        if (!ipUtil.isValidIPv4(clientIp)) {
            return Result.error(400, "IP地址格式非法");
        }

        // 获取或生成设备 ID
        String deviceId = deviceUtil.getOrGenerateDeviceId(request, response);

        // 执行签到
        AttendanceRecord record = attendanceService.signIn(sessionId, studentId, clientIp, deviceId);

        Map<String, Object> result = new HashMap<>();
        result.put("record", record);
        result.put("message", "签到成功");

        return Result.success(result);
    }

    /**
     * 扫码签到（通过 QR 令牌验证）
     */
    @PostMapping("/session/{sessionId}/qrsign")
    public Result<Map<String, Object>> qrSignIn(
        @PathVariable Long sessionId,
        @RequestBody Map<String, String> body,
        HttpSession session,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        String token = body.get("token");

        if (token == null || token.isEmpty()) {
            return Result.error(400, "QR令牌不能为空");
        }

        // 获取或生成设备 ID（App 可传入自定义 deviceId）
        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = deviceUtil.getOrGenerateDeviceId(request, response);
        }

        // 获取客户端 IP（QR 签到不要求局域网）
        String clientIp = ipUtil.getClientIP(request);

        // 执行扫码签到
        AttendanceRecord record = attendanceService.qrSignIn(sessionId, studentId, token, clientIp, deviceId);

        Map<String, Object> result = new HashMap<>();
        result.put("record", record);
        result.put("message", "扫码签到成功");

        return Result.success(result);
    }

    // ==================== 请假管理 ====================

    /**
     * 学生提交请假申请
     */
    @PostMapping("/leave")
    public Result<LeaveRequest> submitLeave(
        @Valid @RequestBody LeaveSubmitRequest request,
        HttpSession session
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        LeaveRequest leave = leaveService.submitLeave(
            request.getCourseId(),
            request.getSessionId(),
            studentId,
            request.getLeaveType(),
            request.getReason()
        );
        return Result.success(leave);
    }

    /**
     * 获取学生所有请假记录
     */
    @GetMapping("/leaves")
    public Result<List<LeaveRequest>> getLeaves(HttpSession session) {
        Long studentId = (Long) session.getAttribute("userId");
        List<LeaveRequest> leaves = leaveService.getStudentLeaves(studentId);
        return Result.success(leaves);
    }

    /**
     * 获取学生在某课程下的请假记录
     */
    @GetMapping("/courses/{courseId}/leaves")
    public Result<List<LeaveRequest>> getCourseLeaves(
        @PathVariable Long courseId,
        HttpSession session
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        List<LeaveRequest> leaves = leaveService.getStudentLeavesForCourse(studentId, courseId);
        return Result.success(leaves);
    }

    // ==================== 考勤记录 ====================

    /**
     * 获取学生在某课程下的考勤历史
     */
    @GetMapping("/courses/{courseId}/attendance")
    public Result<List<Map<String, Object>>> getAttendanceHistory(
        @PathVariable Long courseId,
        HttpSession session
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        List<Map<String, Object>> history = attendanceService.getStudentAttendanceHistory(studentId, courseId);
        return Result.success(history);
    }

    /**
     * 获取学生在某课程下的考勤统计摘要
     */
    @GetMapping("/courses/{courseId}/attendance/summary")
    public Result<Map<String, Object>> getAttendanceSummary(
        @PathVariable Long courseId,
        HttpSession session
    ) {
        Long studentId = (Long) session.getAttribute("userId");
        Map<String, Object> summary = attendanceService.getStudentCourseStatistics(studentId, courseId);
        return Result.success(summary);
    }
}
