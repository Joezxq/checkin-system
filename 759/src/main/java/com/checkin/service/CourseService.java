package com.checkin.service;

import com.checkin.entity.Course;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.AttendanceSessionRepository;
import com.checkin.repository.CourseRepository;
import com.checkin.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程服务
 */
@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    /**
     * 创建课程
     */
    @Transactional
    public Course createCourse(String courseName, Long teacherId) {
        Course course = new Course();
        course.setCourseName(courseName);
        course.setTeacherId(teacherId);
        return courseRepository.save(course);
    }

    /**
     * 更新课程
     */
    @Transactional
    public Course updateCourse(Long courseId, String courseName, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        // 验证权限
        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限修改此课程");
        }

        course.setCourseName(courseName);
        return courseRepository.save(course);
    }

    /**
     * 删除课程
     */
    @Transactional
    public void deleteCourse(Long courseId, Long teacherId) {
        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));

        // 验证权限
        if (!course.getTeacherId().equals(teacherId)) {
            throw new BusinessException(403, "无权限删除此课程");
        }

        // 级联清理签到记录、签到活动、选课记录
        recordRepository.findBySessionIdIn(
            sessionRepository.findByCourseId(courseId).stream().map(s -> s.getId()).collect(java.util.stream.Collectors.toList())
        );
        // 直接用 repository 删除关联数据
        List<com.checkin.entity.AttendanceSession> sessions = sessionRepository.findByCourseId(courseId);
        for (com.checkin.entity.AttendanceSession s : sessions) {
            recordRepository.deleteBySessionId(s.getId());
        }
        sessionRepository.deleteByCourseId(courseId);
        enrollmentRepository.deleteByCourseId(courseId);
        courseRepository.deleteById(courseId);
    }

    /**
     * 获取教师的所有课程
     */
    public List<Course> getTeacherCourses(Long teacherId) {
        return courseRepository.findByTeacherId(teacherId);
    }

    /**
     * 获取课程详情
     */
    public Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
            .orElseThrow(() -> new BusinessException("课程不存在"));
    }

    /**
     * 批量获取课程
     */
    public List<Course> getCoursesByIds(List<Long> courseIds) {
        return courseRepository.findAllById(courseIds);
    }
}
