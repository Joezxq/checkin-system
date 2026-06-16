package com.checkin.service;

import com.checkin.entity.Course;
import com.checkin.entity.Enrollment;
import com.checkin.exception.BusinessException;
import com.checkin.repository.CourseRepository;
import com.checkin.repository.EnrollmentRepository;
import com.checkin.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 选课服务
 */
@Service
public class EnrollmentService {

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    /**
     * 学生加入课程
     */
    @Transactional
    public Enrollment enrollStudent(Long courseId, Long studentId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        // 验证学生是否存在
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("学生不存在");
        }

        // 检查是否已选课
        if (enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("学生已选此课程");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        return enrollmentRepository.save(enrollment);
    }

    /**
     * 移除学生
     */
    @Transactional
    public void removeStudent(Long courseId, Long studentId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        if (!enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException("学生未选此课程");
        }
        enrollmentRepository.deleteByCourseIdAndStudentId(courseId, studentId);
    }

    /**
     * 获取课程的所有学生ID（内部使用，不校验权限）
     */
    public List<Long> getCourseStudentIds(Long courseId) {
        return enrollmentRepository.findStudentIdsByCourseId(courseId);
    }

    /**
     * 获取课程的所有学生ID（带权限校验）
     */
    public List<Long> getCourseStudentIds(Long courseId, Long teacherId) {
        // 验证课程是否存在且属于该教师
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限查看此课程学生");
        }

        return enrollmentRepository.findStudentIdsByCourseId(courseId);
    }

    /**
     * 批量添加学生到课程
     */
    @Transactional
    public Map<String, Object> enrollStudents(Long courseId, List<Long> studentIds, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (Long studentId : studentIds) {
            try {
                if (!studentRepository.existsById(studentId)) {
                    errors.add("学生ID " + studentId + " 不存在");
                    continue;
                }
                if (enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
                    continue; // 已选课，跳过
                }
                Enrollment enrollment = new Enrollment();
                enrollment.setCourseId(courseId);
                enrollment.setStudentId(studentId);
                enrollmentRepository.save(enrollment);
                successCount++;
            } catch (Exception e) {
                errors.add("学生ID " + studentId + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", errors.size());
        result.put("errors", errors);
        return result;
    }

    /**
     * 批量移除学生
     */
    @Transactional
    public Map<String, Object> removeStudents(Long courseId, List<Long> studentIds, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限操作此课程");
        }

        int successCount = 0;
        for (Long studentId : studentIds) {
            if (enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
                enrollmentRepository.deleteByCourseIdAndStudentId(courseId, studentId);
                successCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("removed", studentIds.size());
        return result;
    }

    /**
     * 获取学生的所有课程
     */
    public List<Enrollment> getStudentCourses(Long studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }
}
