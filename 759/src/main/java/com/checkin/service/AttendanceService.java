package com.checkin.service;

import com.checkin.entity.AttendanceRecord;
import com.checkin.entity.AttendanceResult;
import com.checkin.entity.AttendanceSession;
import com.checkin.entity.Course;
import com.checkin.entity.LeaveRequest;
import com.checkin.entity.Student;
import com.checkin.enums.AttendanceStatus;
import com.checkin.enums.LeaveStatus;
import com.checkin.enums.SessionStatus;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.AttendanceResultRepository;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private AttendanceResultRepository resultRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private LeaveService leaveService;

    @Autowired
    private OperationLogService logService;

    @Autowired
    private IPUtil ipUtil;

    /**
     * 发起签到活动
     */
    @Transactional
    public AttendanceSession openSession(Long courseId, Integer durationMinutes, String title,
                                          Integer normalEndTimeMinutes, Integer lateEndTimeMinutes,
                                          Boolean allowLate, String signMethod,
                                          Boolean allowQrCode, Boolean allowDeviceCheck,
                                          Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        // 检查是否已有进行中的签到活动（自动关闭过期的）
        sessionRepository.findByCourseIdAndStatus(courseId, SessionStatus.IN_PROGRESS.name())
            .ifPresent(s -> {
                if (s.getEndTime() != null && LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isAfter(s.getEndTime())) {
                    logger.info("自动关闭过期签到活动: sessionId={}", s.getId());
                    s.setStatus(SessionStatus.EXPIRED.name());
                    sessionRepository.save(s);
                    // 自动关闭时也生成考勤结果
                    generateAttendanceResults(s.getId());
                } else {
                    throw new BusinessException("该课程已有进行中的签到活动，请先关闭");
                }
            });

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        // 确定各时间参数
        int normalMinutes = (normalEndTimeMinutes != null && normalEndTimeMinutes > 0) ? normalEndTimeMinutes : durationMinutes;
        int lateMinutes = (lateEndTimeMinutes != null) ? lateEndTimeMinutes : 0;
        boolean lateAllowed = allowLate != null ? allowLate : true;

        LocalDateTime startTime = now;
        LocalDateTime normalEndTime = now.plusMinutes(normalMinutes);
        LocalDateTime endTime = normalEndTime;
        LocalDateTime lateEnd = null;

        if (lateAllowed && lateMinutes > 0) {
            lateEnd = normalEndTime.plusMinutes(lateMinutes);
            endTime = lateEnd;
        }

        AttendanceSession session = new AttendanceSession();
        session.setCourseId(courseId);
        session.setTitle(title);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setNormalEndTime(normalEndTime);
        session.setLateEndTime(lateEnd);
        session.setAllowLate(lateAllowed);
        session.setSignMethod(signMethod != null ? signMethod : "WEB");
        session.setAllowQrCode(allowQrCode != null ? allowQrCode : true);
        session.setAllowDeviceCheck(allowDeviceCheck != null ? allowDeviceCheck : false);
        session.setStatus(SessionStatus.IN_PROGRESS.name());

        // 仅在允许扫码时生成 QR 令牌
        if (session.getAllowQrCode()) {
            session.setQrToken(UUID.randomUUID().toString().replace("-", ""));
        }

        AttendanceSession saved = sessionRepository.save(session);
        logService.log("TEACHER", teacherId, null, "OPEN_SESSION", "AttendanceSession", saved.getId(),
            "课程: " + course.getCourseName() + ", 标题: " + title, null);
        return saved;
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

        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException("签到活动不处于进行中状态，无法关闭");
        }

        session.setStatus(SessionStatus.CLOSED.name());
        sessionRepository.save(session);

        // 生成考勤结果
        generateAttendanceResults(sessionId);

        logService.log("TEACHER", teacherId, null, "CLOSE_SESSION", "AttendanceSession", sessionId,
            "课程: " + course.getCourseName(), null);

        // 返回签到统计
        return getSessionStatistics(sessionId, teacherId);
    }

    /**
     * 生成考勤结果（为每个选课学生生成考勤状态）
     */
    @Transactional
    public void generateAttendanceResults(Long sessionId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        List<AttendanceRecord> records = recordRepository.findBySessionId(sessionId);
        Map<Long, AttendanceRecord> recordMap = records.stream()
            .collect(Collectors.toMap(AttendanceRecord::getStudentId, r -> r, (a, b) -> a));

        // 获取本课程所有已批准的请假（无特定学生ID，使用课程查询）
        List<LeaveRequest> approvedLeaves = leaveService.getApprovedLeavesForCourse(
                session.getCourseId()).stream()
            .filter(lr -> lr.getSessionId() == null || lr.getSessionId().equals(sessionId))
            .collect(Collectors.toList());
        Map<Long, LeaveRequest> leaveMap = approvedLeaves.stream()
            .collect(Collectors.toMap(LeaveRequest::getStudentId, lr -> lr, (a, b) -> a));

        // 异常检测（同名设备签到）
        Map<String, Integer> deviceCount = new HashMap<>();
        for (AttendanceRecord record : records) {
            deviceCount.merge(record.getClientDeviceId(), 1, Integer::sum);
        }

        List<AttendanceResult> results = new ArrayList<>();

        for (Long studentId : enrolledStudentIds) {
            AttendanceResult result = resultRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElse(new AttendanceResult());

            result.setSessionId(sessionId);
            result.setCourseId(session.getCourseId());
            result.setStudentId(studentId);

            boolean hasApprovedLeave = leaveMap.containsKey(studentId);
            LeaveRequest approvedLeave = leaveMap.get(studentId);
            AttendanceRecord record = recordMap.get(studentId);

            if (hasApprovedLeave && approvedLeave != null) {
                // 有已批准的请假 → LEAVE
                result.setStatus(AttendanceStatus.LEAVE.name());
                result.setLeaveRequestId(approvedLeave.getId());
                if (record != null) {
                    result.setSignTime(record.getSignTime());
                    result.setClientIp(record.getClientIp());
                    result.setClientDeviceId(record.getClientDeviceId());
                }
            } else if (record != null) {
                result.setSignTime(record.getSignTime());
                result.setClientIp(record.getClientIp());
                result.setClientDeviceId(record.getClientDeviceId());

                // 异常检测：同设备多学生
                if (session.getAllowDeviceCheck() && deviceCount.getOrDefault(record.getClientDeviceId(), 1) > 1) {
                    result.setStatus(AttendanceStatus.ABNORMAL.name());
                    result.setAbnormalReason("同一设备被多个学生使用签到");
                }
                // 判断正常/迟到
                else if (session.getNormalEndTime() != null
                        && !record.getSignTime().isAfter(session.getNormalEndTime())) {
                    result.setStatus(AttendanceStatus.NORMAL.name());
                } else if (session.getAllowLate() && session.getLateEndTime() != null
                        && !record.getSignTime().isAfter(session.getLateEndTime())) {
                    result.setStatus(AttendanceStatus.LATE.name());
                } else if (session.getNormalEndTime() != null) {
                    // 签到时间晚于截止时间：允许迟到则标记LATE，否则ABSENT
                    if (session.getAllowLate() && session.getLateEndTime() != null) {
                        // 允许迟到且签到在迟到截止之后 -> 缺勤
                        result.setStatus(AttendanceStatus.ABSENT.name());
                    } else if (!session.getAllowLate()) {
                        // 不允许迟到 -> 缺勤
                        result.setStatus(AttendanceStatus.ABSENT.name());
                    } else {
                        result.setStatus(AttendanceStatus.LATE.name());
                    }
                } else {
                    result.setStatus(AttendanceStatus.NORMAL.name());
                }
            } else {
                // 无签到记录且无有效请假 → ABSENT
                result.setStatus(AttendanceStatus.ABSENT.name());
            }

            results.add(result);
        }

        // 批量保存考勤结果
        resultRepository.saveAll(results);

        logger.info("生成考勤结果完成: sessionId={}, count={}", sessionId, results.size());
    }

    /**
     * 修改签到活动时间（仅未开始状态可修改）
     */
    @Transactional
    public AttendanceSession modifySessionTime(Long sessionId, LocalDateTime startTime,
                                                LocalDateTime normalEndTime, LocalDateTime lateEndTime,
                                                Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限修改此签到活动");
        }

        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException("签到活动不处于进行中状态，无法修改时间");
        }

        // 校验时间合理性
        if (startTime != null && normalEndTime != null && !startTime.isBefore(normalEndTime)) {
            throw new BusinessException("开始时间必须早于正常截止时间");
        }
        if (normalEndTime != null && lateEndTime != null && !normalEndTime.isBefore(lateEndTime)) {
            throw new BusinessException("正常截止时间必须早于迟到截止时间");
        }

        if (startTime != null) session.setStartTime(startTime);
        if (normalEndTime != null) session.setNormalEndTime(normalEndTime);
        if (lateEndTime != null) {
            session.setLateEndTime(lateEndTime);
            session.setEndTime(lateEndTime);
        } else if (normalEndTime != null) {
            session.setEndTime(normalEndTime);
        }

        logger.info("教师 {} 修改了签到活动时间, sessionId={}", teacherId, sessionId);
        return sessionRepository.save(session);
    }

    /**
     * 延长签到活动
     */
    @Transactional
    public AttendanceSession extendSession(Long sessionId, Integer extendMinutes, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限延长此签到活动");
        }

        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException("签到活动不处于进行中状态，无法延长");
        }

        if (extendMinutes == null || extendMinutes <= 0) {
            throw new BusinessException("延长分钟数必须大于0");
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime newEndTime;

        if (session.getEndTime() != null && session.getEndTime().isAfter(now)) {
            newEndTime = session.getEndTime().plusMinutes(extendMinutes);
        } else {
            newEndTime = now.plusMinutes(extendMinutes);
        }

        // 延长总结束时间
        session.setEndTime(newEndTime);

        // 如果允许迟到，同时延长迟到截止时间
        if (session.getAllowLate() && session.getLateEndTime() != null) {
            LocalDateTime newLateEnd = session.getLateEndTime().plusMinutes(extendMinutes);
            session.setLateEndTime(newLateEnd);
        }

        // 如果正常截止时间已过，延长正常截止时间
        if (session.getNormalEndTime() != null && session.getNormalEndTime().isBefore(now)) {
            session.setNormalEndTime(session.getNormalEndTime().plusMinutes(extendMinutes));
        }

        logger.info("教师 {} 延长了签到活动, sessionId={}, +{}分钟", teacherId, sessionId, extendMinutes);
        return sessionRepository.save(session);
    }

    /**
     * 取消签到活动
     */
    @Transactional
    public AttendanceSession cancelSession(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限取消此签到活动");
        }

        if (!SessionStatus.canCancel(session.getStatus())) {
            throw new BusinessException("签到活动状态不允许取消");
        }

        session.setStatus(SessionStatus.CANCELLED.name());

        logger.info("教师 {} 取消了签到活动, sessionId={}", teacherId, sessionId);
        AttendanceSession saved = sessionRepository.save(session);
        logService.log("TEACHER", teacherId, null, "CANCEL_SESSION", "AttendanceSession", sessionId, null, null);
        return saved;
    }

    /**
     * 学生签到
     */
    @Transactional
    public AttendanceRecord signIn(Long sessionId, Long studentId, String clientIp, String clientDeviceId) {
        // 1. 验证签到活动
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        // 2. 验证活动状态
        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException("签到活动已关闭");
        }

        // 3. 验证是否过期
        if (session.getEndTime() != null
                && LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isAfter(session.getEndTime())) {
            session.setStatus(SessionStatus.EXPIRED.name());
            sessionRepository.save(session);
            throw new BusinessException("签到活动已过期");
        }

        // 4. 验证学生选课
        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        if (!new HashSet<>(enrolledStudentIds).contains(studentId)) {
            throw new BusinessException("您未选修此课程，无法签到");
        }

        // 5. 验证是否重复签到
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(409, "您已签到，请勿重复签到");
        }

        // 6. 设备校验（始终检查，避免DB约束冲突）
        if (recordRepository.existsBySessionIdAndClientDeviceId(sessionId, clientDeviceId)) {
            throw new BusinessException(409, "该设备已被使用签到，请使用其他设备");
        }

        // 7. 生成模拟地理位置
        BigDecimal[] location = GeoUtil.generateMockLocation();

        // 8. 创建签到记录
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setCourseId(session.getCourseId());
        record.setStudentId(studentId);
        record.setSignTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        record.setClientIp(clientIp);
        record.setClientDeviceId(clientDeviceId);
        record.setGeoLat(location[0]);
        record.setGeoLng(location[1]);

        return recordRepository.save(record);
    }

    /**
     * 扫码签到
     */
    @Transactional
    public AttendanceRecord qrSignIn(Long sessionId, Long studentId, String token, String clientIp, String clientDeviceId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

        if (!SessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BusinessException("签到活动已关闭");
        }

        if (session.getEndTime() != null
                && LocalDateTime.now(ZoneId.of("Asia/Shanghai")).isAfter(session.getEndTime())) {
            session.setStatus(SessionStatus.EXPIRED.name());
            sessionRepository.save(session);
            throw new BusinessException("签到活动已过期");
        }

        // 验证是否允许二维码签到
        if (!session.getAllowQrCode()) {
            throw new BusinessException("该签到活动不允许扫码签到");
        }

        // 验证 QR 令牌
        if (session.getQrToken() == null || !session.getQrToken().equals(token)) {
            throw new BusinessException("无效的QR令牌");
        }

        // 验证学生选课
        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        if (!new HashSet<>(enrolledStudentIds).contains(studentId)) {
            throw new BusinessException("您未选修此课程，无法签到");
        }

        // 验证是否重复签到
        if (recordRepository.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new BusinessException(409, "您已签到，请勿重复签到");
        }

        // 设备校验（始终检查，避免DB约束冲突）
        if (recordRepository.existsBySessionIdAndClientDeviceId(sessionId, clientDeviceId)) {
            throw new BusinessException(409, "该设备已被使用签到，请使用其他设备");
        }

        BigDecimal[] location = GeoUtil.generateMockLocation();

        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setCourseId(session.getCourseId());
        record.setStudentId(studentId);
        record.setSignTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        record.setClientIp(clientIp);
        record.setClientDeviceId(clientDeviceId);
        record.setGeoLat(location[0]);
        record.setGeoLng(location[1]);

        return recordRepository.save(record);
    }

    /**
     * 获取单次签到活动的统计数据（含状态细分）
     */
    public Map<String, Object> getSessionStatistics(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));

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

        // 从考勤结果获取详细统计
        List<AttendanceResult> results = resultRepository.findBySessionId(sessionId);
        long normalCount = results.stream().filter(r -> AttendanceStatus.NORMAL.name().equals(r.getStatus())).count();
        long lateCount = results.stream().filter(r -> AttendanceStatus.LATE.name().equals(r.getStatus())).count();
        long leaveCount = results.stream().filter(r -> AttendanceStatus.LEAVE.name().equals(r.getStatus())).count();
        long absentCount = results.stream().filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus())).count();
        long abnormalCount = results.stream().filter(r -> AttendanceStatus.ABNORMAL.name().equals(r.getStatus())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("courseId", session.getCourseId());
        result.put("courseName", course.getCourseName());
        result.put("title", session.getTitle());
        result.put("startTime", formatDateTime(session.getStartTime()));
        result.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
        result.put("normalEndTime", session.getNormalEndTime() != null ? formatDateTime(session.getNormalEndTime()) : null);
        result.put("lateEndTime", session.getLateEndTime() != null ? formatDateTime(session.getLateEndTime()) : null);
        result.put("status", session.getStatus());
        result.put("totalCount", totalCount);
        result.put("signedCount", signedCount);
        result.put("unsignedCount", unsignedCount);
        result.put("attendanceRate", attendanceRate);
        result.put("normalCount", normalCount);
        result.put("lateCount", lateCount);
        result.put("leaveCount", leaveCount);
        result.put("absentCount", absentCount);
        result.put("abnormalCount", abnormalCount);

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
        List<Map<String, Object>> sessionStats = new ArrayList<>();

        double totalRate = 0;
        int sessionsWithRecords = 0;

        for (AttendanceSession session : sessions) {
            List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
            int totalCount = allStudentIds.size();
            long signedCount = recordRepository.countBySessionId(session.getId());
            double rate = totalCount > 0 ? Math.round(signedCount * 1000.0 / totalCount) / 10.0 : 0.0;

            // 获取状态细分
            List<AttendanceResult> results = resultRepository.findBySessionId(session.getId());
            long normalCount = results.stream().filter(r -> AttendanceStatus.NORMAL.name().equals(r.getStatus())).count();
            long lateCount = results.stream().filter(r -> AttendanceStatus.LATE.name().equals(r.getStatus())).count();
            long leaveCount = results.stream().filter(r -> AttendanceStatus.LEAVE.name().equals(r.getStatus())).count();
            long absentCount = results.stream().filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus())).count();
            long abnormalCount = results.stream().filter(r -> AttendanceStatus.ABNORMAL.name().equals(r.getStatus())).count();

            Map<String, Object> stat = new HashMap<>();
            stat.put("sessionId", session.getId());
            stat.put("title", session.getTitle());
            stat.put("startTime", formatDateTime(session.getStartTime()));
            stat.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
            stat.put("status", session.getStatus());
            stat.put("totalCount", totalCount);
            stat.put("signedCount", signedCount);
            stat.put("attendanceRate", rate);
            stat.put("normalCount", normalCount);
            stat.put("lateCount", lateCount);
            stat.put("leaveCount", leaveCount);
            stat.put("absentCount", absentCount);
            stat.put("abnormalCount", abnormalCount);
            sessionStats.add(stat);

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
     * 获取课程数据看板
     */
    public Map<String, Object> getCourseDashboard(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程数据");
        }

        List<AttendanceSession> sessions = sessionRepository.findByCourseId(courseId);
        int totalSessions = sessions.size();

        // 汇总所有考勤结果
        List<AttendanceResult> allResults = new ArrayList<>();
        for (AttendanceSession session : sessions) {
            allResults.addAll(resultRepository.findBySessionId(session.getId()));
        }

        long normalCount = allResults.stream().filter(r -> AttendanceStatus.NORMAL.name().equals(r.getStatus())).count();
        long lateCount = allResults.stream().filter(r -> AttendanceStatus.LATE.name().equals(r.getStatus())).count();
        long leaveCount = allResults.stream().filter(r -> AttendanceStatus.LEAVE.name().equals(r.getStatus())).count();
        long absentCount = allResults.stream().filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus())).count();
        long abnormalCount = allResults.stream().filter(r -> AttendanceStatus.ABNORMAL.name().equals(r.getStatus())).count();

        List<Long> enrolledStudentIds = enrollmentService.getCourseStudentIds(courseId);
        int totalStudents = enrolledStudentIds.size();

        // 计算平均出勤率
        double averageRate = 0;
        int sessionsWithData = 0;
        for (AttendanceSession session : sessions) {
            long signedCount = recordRepository.countBySessionId(session.getId());
            if (totalStudents > 0) {
                averageRate += Math.round(signedCount * 1000.0 / totalStudents) / 10.0;
                sessionsWithData++;
            }
        }
        averageRate = sessionsWithData > 0
            ? Math.round(averageRate * 10.0 / sessionsWithData) / 10.0
            : 0.0;

        // 最近一次签到率
        double lastRate = 0;
        if (!sessions.isEmpty()) {
            AttendanceSession lastSession = sessions.get(sessions.size() - 1);
            long lastSigned = recordRepository.countBySessionId(lastSession.getId());
            lastRate = totalStudents > 0 ? Math.round(lastSigned * 1000.0 / totalStudents) / 10.0 : 0.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course.getCourseName());
        result.put("totalSessions", totalSessions);
        result.put("totalStudents", totalStudents);
        result.put("averageRate", averageRate);
        result.put("lastRate", lastRate);
        result.put("normalCount", normalCount);
        result.put("lateCount", lateCount);
        result.put("leaveCount", leaveCount);
        result.put("absentCount", absentCount);
        result.put("abnormalCount", abnormalCount);

        return result;
    }

    /**
     * 获取学生个人考勤历史
     */
    public List<Map<String, Object>> getStudentAttendanceHistory(Long studentId, Long courseId) {
        List<AttendanceResult> results = resultRepository.findByCourseIdAndStudentId(courseId, studentId);

        List<Map<String, Object>> history = new ArrayList<>();
        for (AttendanceResult result : results) {
            AttendanceSession session = sessionRepository.findById(result.getSessionId()).orElse(null);
            Map<String, Object> item = new HashMap<>();
            item.put("sessionId", result.getSessionId());
            item.put("sessionTitle", session != null ? session.getTitle() : null);
            item.put("sessionStartTime", session != null ? formatDateTime(session.getStartTime()) : null);
            item.put("status", result.getStatus());
            item.put("signTime", result.getSignTime() != null ? formatDateTime(result.getSignTime()) : null);
            item.put("abnormalReason", result.getAbnormalReason());
            history.add(item);
        }

        return history;
    }

    /**
     * 获取学生个人考勤统计
     */
    public Map<String, Object> getStudentCourseStatistics(Long studentId, Long courseId) {
        List<AttendanceResult> results = resultRepository.findByCourseIdAndStudentId(courseId, studentId);

        long totalCount = results.size();
        long normalCount = results.stream().filter(r -> AttendanceStatus.NORMAL.name().equals(r.getStatus())).count();
        long lateCount = results.stream().filter(r -> AttendanceStatus.LATE.name().equals(r.getStatus())).count();
        long leaveCount = results.stream().filter(r -> AttendanceStatus.LEAVE.name().equals(r.getStatus())).count();
        long absentCount = results.stream().filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus())).count();
        long abnormalCount = results.stream().filter(r -> AttendanceStatus.ABNORMAL.name().equals(r.getStatus())).count();

        double attendanceRate = totalCount > 0
            ? Math.round(normalCount * 1000.0 / totalCount) / 10.0 : 0.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalSessions", totalCount);
        result.put("normalCount", normalCount);
        result.put("lateCount", lateCount);
        result.put("leaveCount", leaveCount);
        result.put("absentCount", absentCount);
        result.put("abnormalCount", abnormalCount);
        result.put("attendanceRate", attendanceRate);

        return result;
    }

    /**
     * 获取出勤趋势数据（用于图表）
     */
    public List<Map<String, Object>> getCourseTrend(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程数据");
        }

        List<AttendanceSession> sessions = sessionRepository.findByCourseId(courseId);
        int totalStudents = enrollmentService.getCourseStudentIds(courseId).size();

        List<Map<String, Object>> trend = new ArrayList<>();
        for (AttendanceSession session : sessions) {
            long signedCount = recordRepository.countBySessionId(session.getId());
            double rate = totalStudents > 0 ? Math.round(signedCount * 1000.0 / totalStudents) / 10.0 : 0.0;

            Map<String, Object> point = new HashMap<>();
            point.put("sessionId", session.getId());
            point.put("title", session.getTitle());
            point.put("date", formatDateTime(session.getStartTime()));
            point.put("attendanceRate", rate);
            trend.add(point);
        }

        return trend;
    }

    /**
     * 获取考勤状态分布（用于饼图）
     */
    public Map<String, Object> getStatusDistribution(Long courseId, Long teacherId) {
        Map<String, Object> dashboard = getCourseDashboard(courseId, teacherId);

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("NORMAL", dashboard.get("normalCount"));
        distribution.put("LATE", dashboard.get("lateCount"));
        distribution.put("LEAVE", dashboard.get("leaveCount"));
        distribution.put("ABSENT", dashboard.get("absentCount"));
        distribution.put("ABNORMAL", dashboard.get("abnormalCount"));

        return distribution;
    }

    /**
     * 填充历史签到记录的统计字段
     */
    private void populateSessionStats(AttendanceSession session) {
        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();
        long signedCount = recordRepository.countBySessionId(session.getId());
        double rate = totalCount > 0 ? Math.round(signedCount * 1000.0 / totalCount) / 10.0 : 0.0;

        session.setTotalCount(totalCount);
        session.setSignedCount((int) signedCount);
        session.setAttendanceRate(rate);

        Course course = courseRepository.findById(session.getCourseId()).orElse(null);
        if (course != null) {
            session.setCourseName(course.getCourseName());
        }
    }

    /**
     * 获取课程的活跃签到活动
     */
    public AttendanceSession getActiveSession(Long courseId) {
        return sessionRepository.findByCourseIdAndStatus(courseId, SessionStatus.IN_PROGRESS.name()).orElse(null);
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

        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此签到活动数据");
        }

        List<Long> allStudentIds = enrollmentService.getCourseStudentIds(session.getCourseId());
        int totalCount = allStudentIds.size();

        List<Long> signedStudentIds = recordRepository.findStudentIdsBySessionId(sessionId);
        int signedCount = signedStudentIds.size();

        // 计算未签到学生ID列表
        Set<Long> signedSet = new HashSet<>(signedStudentIds);
        List<Long> unsignedStudentIds = new ArrayList<>();
        for (Long sid : allStudentIds) {
            if (!signedSet.contains(sid)) unsignedStudentIds.add(sid);
        }
        int unsignedCount = unsignedStudentIds.size();

        List<Student> signedStudents = studentService.getStudentsByIds(signedStudentIds);
        List<Student> unsignedStudents = unsignedStudentIds.isEmpty() ? new ArrayList<>()
            : studentService.getStudentsByIds(unsignedStudentIds);
        List<AttendanceRecord> records = recordRepository.findBySessionId(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("courseId", session.getCourseId());
        result.put("title", session.getTitle());
        result.put("status", session.getStatus());
        result.put("startTime", formatDateTime(session.getStartTime()));
        result.put("endTime", session.getEndTime() != null ? formatDateTime(session.getEndTime()) : null);
        result.put("normalEndTime", session.getNormalEndTime() != null ? formatDateTime(session.getNormalEndTime()) : null);
        result.put("lateEndTime", session.getLateEndTime() != null ? formatDateTime(session.getLateEndTime()) : null);
        result.put("totalCount", totalCount);
        result.put("signedCount", signedCount);
        result.put("unsignedCount", unsignedCount);
        result.put("signedStudents", signedStudents);
        result.put("unsignedStudents", unsignedStudents);
        result.put("records", records);
        result.put("qrToken", session.getQrToken());

        return result;
    }

    /**
     * 获取历史签到记录
     */
    public Page<AttendanceSession> getHistorySessions(Long courseId, LocalDateTime dateFrom, LocalDateTime dateTo,
                                                       Pageable pageable, Long teacherId) {
        Page<AttendanceSession> page;

        if (courseId != null) {
            Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException("课程不存在"));

            if (!course.getTeacherId().equals(teacherId)) {
                throw new BusinessException(403, "无权限查看此课程的签到记录");
            }

            page = sessionRepository.findHistorySessions(courseId, dateFrom, dateTo, pageable);
        } else {
            List<Course> teacherCourses = courseRepository.findByTeacherId(teacherId);
            if (teacherCourses.isEmpty()) {
                return Page.empty(pageable);
            }

            List<Long> courseIds = teacherCourses.stream()
                .map(Course::getId)
                .collect(Collectors.toList());

            page = sessionRepository.findHistorySessionsByCourseIds(courseIds, dateFrom, dateTo, pageable);
        }

        // 批量填充签到率和课程名
        if (!page.getContent().isEmpty()) {
            Set<Long> courseIds = page.getContent().stream().map(AttendanceSession::getCourseId).collect(Collectors.toSet());
            Map<Long, Integer> enrollmentCounts = new HashMap<>();
            Map<Long, String> courseNames = new HashMap<>();
            for (Long cid : courseIds) {
                enrollmentCounts.put(cid, enrollmentService.getCourseStudentIds(cid).size());
                courseRepository.findById(cid).ifPresent(c -> courseNames.put(cid, c.getCourseName()));
            }
            for (AttendanceSession s : page.getContent()) {
                int total = enrollmentCounts.getOrDefault(s.getCourseId(), 0);
                long signed = recordRepository.countBySessionId(s.getId());
                s.setTotalCount(total);
                s.setSignedCount((int) signed);
                s.setAttendanceRate(total > 0 ? Math.round(signed * 1000.0 / total) / 10.0 : 0.0);
                s.setCourseName(courseNames.getOrDefault(s.getCourseId(), ""));
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
