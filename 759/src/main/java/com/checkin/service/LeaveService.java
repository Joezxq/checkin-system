package com.checkin.service;

import com.checkin.entity.*;
import com.checkin.enums.LeaveStatus;
import com.checkin.enums.LeaveType;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceResultRepository;
import com.checkin.repository.CourseRepository;
import com.checkin.repository.EnrollmentRepository;
import com.checkin.repository.LeaveRequestRepository;
import com.checkin.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 请假服务
 */
@Service
public class LeaveService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveService.class);

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AttendanceResultRepository attendanceResultRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private OperationLogService logService;

    /**
     * 学生提交请假申请
     */
    @Transactional
    public LeaveRequest submitLeave(Long courseId, Long sessionId, Long studentId, String leaveType, String reason) {
        // 验证请假类型
        if (!LeaveType.isValid(leaveType)) {
            throw new BusinessException(400, "无效的请假类型，可选: PERSONAL/SICK/OTHER");
        }

        // 验证课程存在
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        // 验证学生已选此课程
        if (!enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("您未选修此课程，无法请假");
        }

        // 如果指定了签到活动，防止重复请假
        if (sessionId != null) {
            if (leaveRequestRepository.existsBySessionIdAndStudentIdAndStatus(
                    sessionId, studentId, LeaveStatus.PENDING.name())) {
                throw new BusinessException(409, "该次签到已有待审核的请假申请");
            }
            if (leaveRequestRepository.existsBySessionIdAndStudentIdAndStatus(
                    sessionId, studentId, LeaveStatus.APPROVED.name())) {
                throw new BusinessException(409, "该次签到请假已通过，无需重复申请");
            }
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setCourseId(courseId);
        leaveRequest.setSessionId(sessionId);
        leaveRequest.setStudentId(studentId);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setReason(reason);
        leaveRequest.setStatus(LeaveStatus.PENDING.name());

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        logService.log("STUDENT", studentId, null, "LEAVE_SUBMIT", "LeaveRequest", saved.getId(),
            "课程: " + courseId + ", 类型: " + leaveType, null);
        return saved;
    }

    /**
     * 教师批准请假
     */
    @Transactional
    public LeaveRequest approveLeave(Long leaveId, String comment, Long teacherId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId)
            .orElseThrow(() -> new BusinessException("请假申请不存在"));

        // 验证教师是该课程的负责人
        Course course = courseRepository.findById(leaveRequest.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限审批此请假申请");
        }

        // 验证状态
        if (!LeaveStatus.PENDING.name().equals(leaveRequest.getStatus())) {
            throw new BusinessException("该请假申请已处理，无法重复审批");
        }

        leaveRequest.setStatus(LeaveStatus.APPROVED.name());
        leaveRequest.setTeacherComment(comment);
        leaveRequest.setReviewedBy(teacherId);
        leaveRequest.setReviewedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        // 更新已存在的考勤结果状态为 LEAVE
        if (leaveRequest.getSessionId() != null) {
            // 针对特定签到活动的请假：只更新该场次的结果
            attendanceResultRepository.findBySessionIdAndStudentId(
                    leaveRequest.getSessionId(), leaveRequest.getStudentId())
                .ifPresent(result -> {
                    result.setStatus(com.checkin.enums.AttendanceStatus.LEAVE.name());
                    result.setLeaveRequestId(leaveId);
                    attendanceResultRepository.save(result);
                });
        } else {
            // 课程级请假（sessionId==null）：更新该学生在此课程下所有已有的考勤结果
            List<com.checkin.entity.AttendanceResult> allResults =
                attendanceResultRepository.findByCourseIdAndStudentId(
                    leaveRequest.getCourseId(), leaveRequest.getStudentId());
            for (com.checkin.entity.AttendanceResult result : allResults) {
                // 仅更新非 NORMAL 状态的记录（如 ABSENT -> LEAVE），保护已有正常签到记录
                if (!com.checkin.enums.AttendanceStatus.NORMAL.name().equals(result.getStatus())) {
                    result.setStatus(com.checkin.enums.AttendanceStatus.LEAVE.name());
                    result.setLeaveRequestId(leaveId);
                    attendanceResultRepository.save(result);
                }
            }
        }

        logger.info("教师 {} 批准了学生 {} 的请假申请, leaveId={}", teacherId, leaveRequest.getStudentId(), leaveId);
        logService.log("TEACHER", teacherId, null, "LEAVE_APPROVE", "LeaveRequest", leaveId,
            "批准学生 " + leaveRequest.getStudentId() + " 的请假", null);
        return saved;
    }

    /**
     * 教师驳回请假
     */
    @Transactional
    public LeaveRequest rejectLeave(Long leaveId, String comment, Long teacherId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId)
            .orElseThrow(() -> new BusinessException("请假申请不存在"));

        // 验证教师是该课程的负责人
        Course course = courseRepository.findById(leaveRequest.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限审批此请假申请");
        }

        // 验证状态
        if (!LeaveStatus.PENDING.name().equals(leaveRequest.getStatus())) {
            throw new BusinessException("该请假申请已处理，无法重复审批");
        }

        leaveRequest.setStatus(LeaveStatus.REJECTED.name());
        leaveRequest.setTeacherComment(comment);
        leaveRequest.setReviewedBy(teacherId);
        leaveRequest.setReviewedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        logger.info("教师 {} 驳回了学生 {} 的请假申请, leaveId={}", teacherId, leaveRequest.getStudentId(), leaveId);
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        logService.log("TEACHER", teacherId, null, "LEAVE_REJECT", "LeaveRequest", leaveId,
            "驳回学生 " + leaveRequest.getStudentId() + " 的请假", null);
        return saved;
    }

    /**
     * 获取课程待审核的请假列表
     */
    public List<LeaveRequest> getPendingLeaves(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程的请假申请");
        }

        List<LeaveRequest> leaves = leaveRequestRepository.findByCourseIdAndStatus(courseId, LeaveStatus.PENDING.name());
        populateTransientFields(leaves);
        return leaves;
    }

    /**
     * 获取课程所有请假（所有状态）
     */
    public List<LeaveRequest> getAllCourseLeaves(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程的请假申请");
        }

        List<LeaveRequest> leaves = leaveRequestRepository.findByCourseId(courseId);
        populateTransientFields(leaves);
        return leaves;
    }

    /**
     * 填充请假记录的 transient 字段（学生姓名、学号、班级、课程名）
     */
    private void populateTransientFields(List<LeaveRequest> leaves) {
        if (leaves.isEmpty()) return;
        java.util.Set<Long> studentIds = leaves.stream().map(LeaveRequest::getStudentId)
            .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> courseIds = leaves.stream().map(LeaveRequest::getCourseId)
            .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, Student> studentMap = new java.util.HashMap<>();
        for (Long sid : studentIds) {
            studentRepository.findById(sid).ifPresent(s -> studentMap.put(sid, s));
        }
        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        for (Long cid : courseIds) {
            courseRepository.findById(cid).ifPresent(c -> courseMap.put(cid, c));
        }

        for (LeaveRequest lr : leaves) {
            Student s = studentMap.get(lr.getStudentId());
            if (s != null) {
                lr.setStudentName(s.getName());
                lr.setStudentNo(s.getStudentNo());
                lr.setClassName(s.getClassName());
            }
            Course c = courseMap.get(lr.getCourseId());
            if (c != null) {
                lr.setCourseName(c.getCourseName());
            }
        }
    }

    /**
     * 获取课程的所有请假列表
     */
    public List<LeaveRequest> getCourseLeaves(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程的请假申请");
        }

        return leaveRequestRepository.findByCourseIdAndStatus(courseId, LeaveStatus.PENDING.name());
    }

    /**
     * 获取学生的所有请假记录
     */
    public List<LeaveRequest> getStudentLeaves(Long studentId) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByStudentId(studentId);
        populateTransientFields(leaves);
        return leaves;
    }

    /**
     * 获取学生在某课程下的请假记录
     */
    public List<LeaveRequest> getStudentLeavesForCourse(Long studentId, Long courseId) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByCourseIdAndStudentId(courseId, studentId);
        populateTransientFields(leaves);
        return leaves;
    }

    /**
     * 检查学生对某次签到活动是否有已批准的请假
     */
    public boolean hasApprovedLeave(Long sessionId, Long studentId) {
        return leaveRequestRepository.existsBySessionIdAndStudentIdAndStatus(
                sessionId, studentId, LeaveStatus.APPROVED.name());
    }

    /**
     * 根据ID获取请假记录
     */
    public LeaveRequest getLeaveById(Long leaveId) {
        return leaveRequestRepository.findById(leaveId)
            .orElseThrow(() -> new BusinessException("请假申请不存在"));
    }

    /**
     * 获取学生在某签到活动中已批准的请假记录
     */
    public LeaveRequest getApprovedLeaveForSession(Long sessionId, Long studentId) {
        return leaveRequestRepository.findBySessionIdAndStudentId(sessionId, studentId)
            .filter(lr -> LeaveStatus.APPROVED.name().equals(lr.getStatus()))
            .orElse(null);
    }

    /**
     * 获取课程中所有已批准的请假记录
     */
    public List<LeaveRequest> getApprovedLeavesForCourse(Long courseId) {
        return leaveRequestRepository.findByCourseIdAndStatus(courseId, LeaveStatus.APPROVED.name());
    }
}
