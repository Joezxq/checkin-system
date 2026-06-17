package com.checkin.service;

import com.checkin.entity.AttendanceRecord;
import com.checkin.entity.AttendanceSession;
import com.checkin.entity.Course;
import com.checkin.entity.Student;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.AttendanceSessionRepository;
import com.checkin.repository.CourseRepository;
import com.checkin.util.GeoUtil;
import com.checkin.util.IPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 签到服务
 */
@Service
public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private IPUtil ipUtil;

    /**
     * 发起签到活动
     */
    @Transactional
    public AttendanceSession openSession(Long courseId, Integer durationMinutes, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        // 检查是否已有开放的签到活动（自动关闭过期的）
        sessionRepository.findByCourseIdAndStatus(courseId, "OPEN")
            .ifPresent(s -> {
                if (s.getEndTime() != null && LocalDateTime.now().isAfter(s.getEndTime())) {
                    logger.info("自动关闭过期签到活动: sessionId={}", s.getId());
                    s.setStatus("CLOSED");
                    sessionRepository.save(s);
                } else {
                    throw new BusinessException("该课程已有进行中的签到活动，请先关闭");
                }
            });

        AttendanceSession session = new AttendanceSession();
        session.setCourseId(courseId);
        session.setStartTime(LocalDateTime.now());
        session.setEndTime(LocalDateTime.now().plusMinutes(durationMinutes));
        session.setStatus("OPEN");
        // 生成 QR 签到令牌
        session.setQrToken(UUID.randomUUID().toString().replace("-", ""));

        return sessionRepository.save(session);
    }

    /**
     * 关闭签到活动（返回签到统计数据）
     */
    @Transactional
    public Map<String, Object> closeSession(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 验证权限
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此签到活动");
        }

        session.setStatus("CLOSED");
        sessionRepository.save(session);

        // 返回签到统计
        return getSessionStatistics(sessionId, teacherId);
    }

    /**
     * 学生签到
     */
    @Transactional
    public AttendanceRecord signIn(Long sessionId, Long studentId, String clientIp, String clientDeviceId) {
        // 1. 验证签到活动是否存在
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 2. 验证活动状态
        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException("签到活动已关闭");
        }

        // 3. 验证活动是否过期（自动关闭）
        if (session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime())) {
            session.setStatus("CLOSED");
            sessionRepository.save(session);
            throw new BusinessException("签到活动已过期");
        }

        // 4. 验证学生是否选了该课程
        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        java.util.Set<Long> enrolledStudentIdSet = new java.util.HashSet<>(enrolledStudentIds);
        if (!enrolledStudentIdSet.contains(studentId)) {
            throw new BusinessException("您未选修此课程，无法签到");
        }

        // 5. 验证是否重复签到
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(409, "您已签到，请勿重复签到");
        }

        // 6. 验证单设备限制（仅用 sessionId + deviceId，与 DB 约束一致）
        if (recordRepository.existsBySessionIdAndClientDeviceId(sessionId, clientDeviceId)) {
            throw new BusinessException(409, "该设备已被使用签到，请使用其他设备");
        }

        // 7. 验证局域网（已关闭，允许所有设备签到）
        // String serverIp = ipUtil.getServerIP();
        // if (!ipUtil.isInSameSubnet(clientIp, serverIp)) {
        //     logger.warn("非局域网访问: clientIp={}, serverIp={}", clientIp, serverIp);
        //     throw new BusinessException(403, "非局域网访问，禁止签到");
        // }

        // 8. 生成模拟地理位置
        BigDecimal[] location = GeoUtil.generateMockLocation();

        // 9. 创建签到记录
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setCourseId(session.getCourseId());
        record.setStudentId(studentId);
        record.setSignTime(LocalDateTime.now());
        record.setClientIp(clientIp);
        record.setClientDeviceId(clientDeviceId);
        record.setGeoLat(location[0]);
        record.setGeoLng(location[1]);

        return recordRepository.save(record);
    }

    /**
     * 扫码签到（通过 QR 令牌验证，跳过局域网检查）
     */
    @Transactional
    public AttendanceRecord qrSignIn(Long sessionId, Long studentId, String token, String clientIp, String clientDeviceId) {
        // 1. 验证签到活动是否存在
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 2. 验证活动状态
        if (!"OPEN".equals(session.getStatus())) {
            throw new BusinessException("签到活动已关闭");
        }

        // 3. 验证活动是否过期（自动关闭）
        if (session.getEndTime() != null && LocalDateTime.now().isAfter(session.getEndTime())) {
            session.setStatus("CLOSED");
            sessionRepository.save(session);
            throw new BusinessException("签到活动已过期");
        }

        // 4. 验证 QR 令牌
        if (session.getQrToken() == null || !session.getQrToken().equals(token)) {
            throw new BusinessException("无效的QR令牌");
        }

        // 5. 验证学生是否选了该课程
        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        java.util.Set<Long> enrolledStudentIdSet = new java.util.HashSet<>(enrolledStudentIds);
        if (!enrolledStudentIdSet.contains(studentId)) {
            throw new BusinessException("您未选修此课程，无法签到");
        }

        // 6. 验证是否重复签到
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(409, "您已签到，请勿重复签到");
        }

        // 7. 验证单设备限制（仅用 sessionId + deviceId）
        if (recordRepository.existsBySessionIdAndClientDeviceId(sessionId, clientDeviceId)) {
            throw new BusinessException(409, "该设备已被使用签到，请使用其他设备");
        }

        // 8. 生成模拟地理位置
        BigDecimal[] location = GeoUtil.generateMockLocation();

        // 9. 创建签到记录
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setCourseId(session.getCourseId());
        record.setStudentId(studentId);
        record.setSignTime(LocalDateTime.now());
        record.setClientIp(clientIp);
        record.setClientDeviceId(clientDeviceId);
        record.setGeoLat(location[0]);
        record.setGeoLng(location[1]);

        return recordRepository.save(record);
    }

    /**
     * 获取单次签到活动的统计数据
     */
    public Map<String, Object> getSessionStatistics(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 验证权限
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此签到统计数据");
        }

        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();
        long signedCount = recordRepository.countBySessionId(sessionId);
        int unsignedCount = totalCount - (int) signedCount;
        double attendanceRate = totalCount > 0 ? Math.round(signedCount * 1000.0 / totalCount) / 10.0 : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("courseId", session.getCourseId());
        result.put("courseName", course.getCourseName());
        result.put("startTime", formatDateTime(session.getStartTime()));
        result.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
        result.put("status", session.getStatus());
        result.put("totalCount", totalCount);
        result.put("signedCount", signedCount);
        result.put("unsignedCount", unsignedCount);
        result.put("attendanceRate", attendanceRate);

        return result;
    }

    /**
     * 获取课程维度的签到统计
     */
    public Map<String, Object> getCourseStatistics(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程统计数据");
        }

        List<AttendanceSession> sessions = sessionRepository.findByCourseId(courseId);

        int totalSessions = sessions.size();
        List<Map<String, Object>> sessionStats = new java.util.ArrayList<>();

        double totalRate = 0;
        int sessionsWithRecords = 0;

        for (AttendanceSession session : sessions) {
            List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
            int totalCount = allStudentIds.size();
            long signedCount = recordRepository.countBySessionId(session.getId());
            double rate = totalCount > 0 ? Math.round(signedCount * 1000.0 / totalCount) / 10.0 : 0.0;

            Map<String, Object> stat = new HashMap<>();
            stat.put("sessionId", session.getId());
            stat.put("startTime", formatDateTime(session.getStartTime()));
            stat.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
            stat.put("status", session.getStatus());
            stat.put("totalCount", totalCount);
            stat.put("signedCount", signedCount);
            stat.put("attendanceRate", rate);
            sessionStats.add(stat);

            // 仅计入有签到记录的场次到平均率
            if (totalCount > 0) {
                totalRate += rate;
                sessionsWithRecords++;
            }
        }

        double averageRate = sessionsWithRecords > 0
            ? Math.round(totalRate * 10.0 / sessionsWithRecords) / 10.0
            : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course.getCourseName());
        result.put("totalSessions", totalSessions);
        result.put("averageRate", averageRate);
        result.put("sessions", sessionStats);

        return result;
    }

    /**
     * 填充历史签到记录的 @Transient 统计字段
     */
    private void populateSessionStats(AttendanceSession session) {
        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();
        long signedCount = recordRepository.countBySessionId(session.getId());
        double rate = totalCount > 0 ? Math.round(signedCount * 1000.0 / totalCount) / 10.0 : 0.0;

        session.setTotalCount(totalCount);
        session.setSignedCount((int) signedCount);
        session.setAttendanceRate(rate);
    }

    /**
     * 获取课程的活动签到活动
     */
    public AttendanceSession getActiveSession(Long courseId) {
        return sessionRepository.findByCourseIdAndStatus(courseId, "OPEN").orElse(null);
    }

    /**
     * 检查学生是否已签到
     */
    public boolean hasStudentSigned(Long sessionId, Long studentId) {
        return recordRepository.existsBySessionIdAndStudentId(sessionId, studentId);
    }

    /**
     * 获取实时签到数据
     */
    public Map<String, Object> getRealtimeData(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 验证权限
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此签到活动数据");
        }

        // 获取课程的所有学生
        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();

        // 获取已签到的学生ID
        List<Long> signedStudentIds = recordRepository.findStudentIdsBySessionId(sessionId);
        int signedCount = signedStudentIds.size();
        int unsignedCount = totalCount - signedCount;

        // 获取已签到的学生详情
        List<Student> signedStudents = studentService.getStudentsByIds(signedStudentIds);

        // 获取签到记录（包含时间）
        List<AttendanceRecord> records = recordRepository.findBySessionId(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("courseId", session.getCourseId());
        result.put("status", session.getStatus());
        result.put("startTime", formatDateTime(session.getStartTime()));
        result.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
        result.put("totalCount", totalCount);
        result.put("signedCount", signedCount);
        result.put("unsignedCount", unsignedCount);
        result.put("signedStudents", signedStudents);
        result.put("records", records);
        result.put("qrToken", session.getQrToken());

        return result;
    }

    /**
     * 获取历史签到记录
     */
    public Page<AttendanceSession> getHistorySessions(Long courseId, LocalDateTime dateFrom, LocalDateTime dateTo, Pageable pageable, Long teacherId) {
        Page<AttendanceSession> page;

        // 如果指定了课程，验证权限
        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException("课程不存在"));

            if (!course.getTeacherId().equals(teacherId)) {
                throw new BusinessException(403, "无权限查看此课程的签到记录");
            }

            page = sessionRepository.findHistorySessions(courseId, dateFrom, dateTo, pageable);
        } else {
            // 如果没有指定课程，只返回该教师的课程的签到记录
            List<Course> teacherCourses = courseRepository.findByTeacherId(teacherId);
            if (teacherCourses.isEmpty()) {
                // 教师没有课程，返回空页面
                return Page.empty(pageable);
            }

            // 获取该教师所有课程的ID列表
            List<Long> courseIds = teacherCourses.stream()
                .map(Course::getId)
                .collect(Collectors.toList());

            page = sessionRepository.findHistorySessionsByCourseIds(courseIds, dateFrom, dateTo, pageable);
        }

        // 批量填充签到率（优化 N+1 查询）
        if (!page.getContent().isEmpty()) {
            List<Long> sessionIds = page.getContent().stream().map(AttendanceSession::getId).collect(Collectors.toList());
            // 批量获取所有课程的选课人数（按 courseId 去重）
            java.util.Set<Long> courseIds = page.getContent().stream().map(AttendanceSession::getCourseId).collect(Collectors.toSet());
            Map<Long, Integer> enrollmentCounts = new HashMap<>();
            for (Long cid : courseIds) {
                enrollmentCounts.put(cid, enrollmentService.getCourseStudentIds(cid).size());
            }
            // 批量获取签到计数
            for (AttendanceSession s : page.getContent()) {
                int total = enrollmentCounts.getOrDefault(s.getCourseId(), 0);
                long signed = recordRepository.countBySessionId(s.getId());
                s.setTotalCount(total);
                s.setSignedCount((int) signed);
                s.setAttendanceRate(total > 0 ? Math.round(signed * 1000.0 / total) / 10.0 : 0.0);
            }
        }

        return page;
    }

    /**
     * 格式化时间
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
