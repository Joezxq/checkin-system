package com.checkin.service;

import com.checkin.entity.*;
import com.checkin.repository.*;
import com.checkin.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据导出服务
 */
@Service
public class ExportService {

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
    private LeaveRequestRepository leaveRequestRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 导出单次签到记录为 CSV
     */
    public byte[] exportSessionCSV(Long sessionId, Long teacherId) {
        AttendanceSession session = getSession(sessionId, teacherId);
        Course course = courseRepository.findById(session.getCourseId()).orElse(null);
        List<AttendanceResult> results = resultRepository.findBySessionId(sessionId);
        Map<Long, Student> studentMap = getStudentMap(results);

        StringBuilder sb = new StringBuilder("﻿"); // BOM for Chinese in Excel
        sb.append("学号,姓名,班级,签到时间,考勤状态,IP地址,异常说明\n");

        for (AttendanceResult r : results) {
            Student s = studentMap.get(r.getStudentId());
            sb.append(escapeCsv(s != null ? s.getStudentNo() : "")).append(",");
            sb.append(escapeCsv(s != null ? s.getName() : "")).append(",");
            sb.append(escapeCsv(s != null ? s.getClassName() : "")).append(",");
            sb.append(escapeCsv(r.getSignTime() != null ? r.getSignTime().format(DTF) : "")).append(",");
            sb.append(r.getStatus()).append(",");
            sb.append(escapeCsv(r.getClientIp() != null ? r.getClientIp() : "")).append(",");
            sb.append(escapeCsv(r.getAbnormalReason() != null ? r.getAbnormalReason() : "")).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导出单次签到记录为 Excel (XLSX)
     */
    public byte[] exportSessionExcel(Long sessionId, Long teacherId) {
        AttendanceSession session = getSession(sessionId, teacherId);
        Course course = courseRepository.findById(session.getCourseId()).orElse(null);
        List<AttendanceResult> results = resultRepository.findBySessionId(sessionId);
        Map<Long, Student> studentMap = getStudentMap(results);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("签到记录");
            Row header = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "班级", "签到时间", "考勤状态", "IP地址", "异常说明"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            int rowIdx = 1;
            for (AttendanceResult r : results) {
                Row row = sheet.createRow(rowIdx++);
                Student s = studentMap.get(r.getStudentId());
                row.createCell(0).setCellValue(s != null ? s.getStudentNo() : "");
                row.createCell(1).setCellValue(s != null ? s.getName() : "");
                row.createCell(2).setCellValue(s != null ? s.getClassName() : "");
                row.createCell(3).setCellValue(r.getSignTime() != null ? r.getSignTime().format(DTF) : "");
                row.createCell(4).setCellValue(r.getStatus());
                row.createCell(5).setCellValue(r.getClientIp() != null ? r.getClientIp() : "");
                row.createCell(6).setCellValue(r.getAbnormalReason() != null ? r.getAbnormalReason() : "");
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 导出课程考勤汇总为 CSV
     */
    public byte[] exportCourseSummaryCSV(Long courseId, Long teacherId) {
        Course course = getCourse(courseId, teacherId);
        List<AttendanceResult> results = getCourseResults(courseId);
        Map<Long, Student> studentMap = getStudentMap(results);

        // 按学生聚合
        Map<Long, Map<String, Long>> studentAgg = new LinkedHashMap<>();
        Map<Long, Long> studentTotalMap = new LinkedHashMap<>();
        for (AttendanceResult r : results) {
            studentAgg.computeIfAbsent(r.getStudentId(), k -> new HashMap<>());
            studentAgg.get(r.getStudentId()).merge(r.getStatus(), 1L, Long::sum);
            studentTotalMap.merge(r.getStudentId(), 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder("﻿");
        sb.append("学号,姓名,班级,总次数,正常,迟到,请假,缺勤,异常,出勤率\n");

        for (Map.Entry<Long, Map<String, Long>> entry : studentAgg.entrySet()) {
            Long sid = entry.getKey();
            Map<String, Long> cnt = entry.getValue();
            Student s = studentMap.get(sid);
            long total = studentTotalMap.getOrDefault(sid, 0L);
            long normal = cnt.getOrDefault("NORMAL", 0L);
            double rate = total > 0 ? Math.round(normal * 1000.0 / total) / 10.0 : 0.0;

            sb.append(escapeCsv(s != null ? s.getStudentNo() : "")).append(",");
            sb.append(escapeCsv(s != null ? s.getName() : "")).append(",");
            sb.append(escapeCsv(s != null ? s.getClassName() : "")).append(",");
            sb.append(total).append(",");
            sb.append(normal).append(",");
            sb.append(cnt.getOrDefault("LATE", 0L)).append(",");
            sb.append(cnt.getOrDefault("LEAVE", 0L)).append(",");
            sb.append(cnt.getOrDefault("ABSENT", 0L)).append(",");
            sb.append(cnt.getOrDefault("ABNORMAL", 0L)).append(",");
            sb.append(rate).append("%\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导出请假记录为 CSV
     */
    public byte[] exportLeavesCSV(Long courseId, Long teacherId) {
        getCourse(courseId, teacherId);
        List<LeaveRequest> leaves = leaveRequestRepository.findByCourseId(courseId);

        StringBuilder sb = new StringBuilder("﻿");
        sb.append("学生ID,请假类型,原因,状态,审批意见,申请时间\n");

        for (LeaveRequest lr : leaves) {
            sb.append(lr.getStudentId()).append(",");
            sb.append(lr.getLeaveType()).append(",");
            sb.append(escapeCsv(lr.getReason())).append(",");
            sb.append(lr.getStatus()).append(",");
            sb.append(escapeCsv(lr.getTeacherComment() != null ? lr.getTeacherComment() : "")).append(",");
            sb.append(lr.getCreatedAt() != null ? lr.getCreatedAt().format(DTF) : "").append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // --- Helper methods ---

    private AttendanceSession getSession(Long sessionId, Long teacherId) {
        AttendanceSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("签到活动不存在"));
        Course course = courseRepository.findById(session.getCourseId())
            .orElseThrow(() -> new BusinessException("课程不存在"));
        if (!course.getTeacherId().equals(teacherId))
            throw new BusinessException(403, "无权限导出");
        return session;
    }

    private Course getCourse(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));
        if (!course.getTeacherId().equals(teacherId))
            throw new BusinessException(403, "无权限导出");
        return course;
    }

    private List<AttendanceResult> getCourseResults(Long courseId) {
        List<AttendanceSession> sessions = sessionRepository.findByCourseId(courseId);
        List<Long> sessionIds = sessions.stream().map(AttendanceSession::getId).collect(Collectors.toList());
        if (sessionIds.isEmpty()) return Collections.emptyList();
        return resultRepository.findBySessionIdIn(sessionIds);
    }

    private Map<Long, Student> getStudentMap(List<AttendanceResult> results) {
        Set<Long> ids = results.stream().map(AttendanceResult::getStudentId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return studentService.getStudentsByIds(new ArrayList<>(ids)).stream()
            .collect(Collectors.toMap(Student::getId, s -> s));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
