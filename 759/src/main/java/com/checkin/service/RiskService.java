package com.checkin.service;

import com.checkin.entity.*;
import com.checkin.enums.AttendanceStatus;
import com.checkin.enums.RiskLevel;
import com.checkin.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险评分服务
 */
@Service
public class RiskService {

    private static final Logger logger = LoggerFactory.getLogger(RiskService.class);

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private AttendanceResultRepository resultRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseRepository courseRepository;

    /**
     * 计算单个学生的风险评分
     */
    @Transactional
    public RiskScore calculateRiskScore(Long courseId, Long studentId) {
        List<AttendanceResult> results = resultRepository.findByCourseIdAndStudentId(courseId, studentId);
        int totalSessions = results.size();

        if (totalSessions == 0) {
            RiskScore rs = new RiskScore();
            rs.setCourseId(courseId);
            rs.setStudentId(studentId);
            rs.setScore(0);
            rs.setRiskLevel(RiskLevel.LOW.name());
            rs.setMetrics("{}");
            rs.setPrediction("{}");
            rs.setCalculatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            return riskScoreRepository.save(rs);
        }

        // 计算各项指标
        long absentCount = results.stream().filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus())).count();
        long recentAbsentCount = results.stream()
            .sorted((a, b) -> b.getSessionId().compareTo(a.getSessionId()))
            .limit(5)
            .filter(r -> AttendanceStatus.ABSENT.name().equals(r.getStatus()))
            .count();
        long consecutiveAbsences = countConsecutiveAbsences(results);
        long lateCount = results.stream().filter(r -> AttendanceStatus.LATE.name().equals(r.getStatus())).count();
        long abnormalCount = results.stream().filter(r -> AttendanceStatus.ABNORMAL.name().equals(r.getStatus())).count();

        double historyAbsenceRate = totalSessions > 0 ? (double) absentCount / totalSessions : 0;
        double recentAbsenceRate = Math.min(5, totalSessions) > 0
            ? (double) recentAbsentCount / Math.min(5, totalSessions) : 0;
        double lateRate = totalSessions > 0 ? (double) lateCount / totalSessions : 0;

        // 加权评分公式
        double score = 0;
        score += historyAbsenceRate * 35;
        score += recentAbsenceRate * 25;
        score += Math.min(consecutiveAbsences, 3) * 5;
        score += lateRate * 10;
        score += Math.min(abnormalCount * 5, 10);

        int finalScore = (int) Math.min(Math.round(score), 100);
        RiskLevel level = RiskLevel.fromScore(finalScore);

        // 保存
        RiskScore riskScore = riskScoreRepository.findByCourseIdAndStudentId(courseId, studentId)
            .orElse(new RiskScore());
        riskScore.setCourseId(courseId);
        riskScore.setStudentId(studentId);
        riskScore.setScore(finalScore);
        riskScore.setRiskLevel(level.name());
        riskScore.setMetrics(String.format(
            "{\"historyAbsenceRate\":%.2f,\"recentAbsenceRate\":%.2f,\"consecutiveAbsences\":%d,\"lateRate\":%.2f,\"abnormalCount\":%d}",
            historyAbsenceRate, recentAbsenceRate, consecutiveAbsences, lateRate, abnormalCount));
        riskScore.setPrediction(buildPrediction(level, finalScore, consecutiveAbsences));
        riskScore.setCalculatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        return riskScoreRepository.save(riskScore);
    }

    /**
     * 计算课程所有学生的风险评分
     */
    @Transactional
    public int calculateCourseRiskScores(Long courseId) {
        List<Long> studentIds = enrollmentService.getCourseStudentIds(courseId);
        int count = 0;
        for (Long studentId : studentIds) {
            try {
                calculateRiskScore(courseId, studentId);
                count++;
            } catch (Exception e) {
                logger.error("计算风险评分失败: courseId={}, studentId={}", courseId, studentId, e);
            }
        }
        logger.info("课程 {} 风险评分计算完成, 共 {} 名学生", courseId, count);
        return count;
    }

    /**
     * 获取课程风险评分列表
     */
    public List<RiskScore> getCourseRiskScores(Long courseId) {
        List<RiskScore> scores = riskScoreRepository.findByCourseId(courseId);
        // 填充学生信息
        Set<Long> studentIds = scores.stream().map(RiskScore::getStudentId).collect(Collectors.toSet());
        if (!studentIds.isEmpty()) {
            Map<Long, Student> studentMap = studentService.getStudentsByIds(new ArrayList<>(studentIds))
                .stream().collect(Collectors.toMap(Student::getId, s -> s));
            for (RiskScore rs : scores) {
                Student s = studentMap.get(rs.getStudentId());
                if (s != null) {
                    rs.setStudentName(s.getName());
                    rs.setStudentNo(s.getStudentNo());
                    rs.setClassName(s.getClassName());
                }
            }
        }
        return scores;
    }

    /**
     * 获取高风险学生列表
     */
    public List<RiskScore> getHighRiskStudents(Long courseId) {
        List<RiskScore> highRisk = riskScoreRepository.findByCourseIdAndRiskLevel(courseId, RiskLevel.HIGH.name());
        List<RiskScore> mediumRisk = riskScoreRepository.findByCourseIdAndRiskLevel(courseId, RiskLevel.MEDIUM.name());
        List<RiskScore> all = new ArrayList<>(highRisk);
        all.addAll(mediumRisk);
        all.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        return getCourseRiskScores(courseId).stream()
            .filter(r -> RiskLevel.HIGH.name().equals(r.getRiskLevel()) || RiskLevel.MEDIUM.name().equals(r.getRiskLevel()))
            .sorted((a, b) -> b.getScore().compareTo(a.getScore()))
            .collect(Collectors.toList());
    }

    private long countConsecutiveAbsences(List<AttendanceResult> results) {
        results.sort(Comparator.comparing(AttendanceResult::getSessionId).reversed());
        long consecutive = 0;
        for (AttendanceResult r : results) {
            if (AttendanceStatus.ABSENT.name().equals(r.getStatus())) consecutive++;
            else break;
        }
        return consecutive;
    }

    private String buildPrediction(RiskLevel level, int score, long consecutiveAbsences) {
        if (level == RiskLevel.HIGH) return String.format(
            "{\"level\":\"高\",\"predictedRate\":%d,\"reason\":\"连续缺勤 %d 次，下次课缺勤概率较高\"}", score, consecutiveAbsences);
        if (level == RiskLevel.MEDIUM) return String.format(
            "{\"level\":\"中\",\"predictedRate\":%d,\"reason\":\"近期出勤率下降，需关注\"}", score);
        return "{\"level\":\"低\",\"predictedRate\":0,\"reason\":\"出勤情况良好\"}";
    }
}
