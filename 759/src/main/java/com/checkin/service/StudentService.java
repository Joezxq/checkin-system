package com.checkin.service;

import com.checkin.entity.Student;
import com.checkin.exception.BusinessException;
import com.checkin.repository.AttendanceRecordRepository;
import com.checkin.repository.EnrollmentRepository;
import com.checkin.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生服务
 */
@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码长度不能少于6位");
        }
    }

    /**
     * 创建学生
     */
    @Transactional
    public Student createStudent(String studentNo, String name, String password, String className) {
        if (studentRepository.existsByStudentNo(studentNo)) {
            throw new BusinessException("学号已存在");
        }
        validatePassword(password);

        Student student = new Student();
        student.setStudentNo(studentNo);
        student.setName(name);
        student.setPasswordHash(passwordEncoder.encode(password));
        student.setClassName(className);
        student.setStatus("ACTIVE");
        return studentRepository.save(student);
    }

    /**
     * 批量创建学生
     */
    @Transactional
    public Map<String, Object> createStudents(List<Map<String, String>> studentList) {
        int successCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < studentList.size(); i++) {
            Map<String, String> s = studentList.get(i);
            try {
                String studentNo = s.get("studentNo");
                String name = s.get("name");
                String password = s.getOrDefault("password", "123456");
                String className = s.getOrDefault("className", "");

                if (studentNo == null || studentNo.isEmpty() || name == null || name.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("row", i + 1);
                    err.put("studentNo", studentNo);
                    err.put("reason", "学号和姓名不能为空");
                    errors.add(err);
                    continue;
                }

                createStudent(studentNo, name, password, className);
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", i + 1);
                err.put("studentNo", s.get("studentNo"));
                err.put("reason", e.getMessage());
                errors.add(err);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", errors.size());
        result.put("errors", errors);
        return result;
    }

    /**
     * 更新学生
     */
    @Transactional
    public Student updateStudent(Long studentId, String studentNo, String name, String password, String className) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));

        // 检查学号是否被其他学生占用
        if (!student.getStudentNo().equals(studentNo) && studentRepository.existsByStudentNo(studentNo)) {
            throw new BusinessException("学号已存在");
        }

        student.setStudentNo(studentNo);
        student.setName(name);
        if (password != null && !password.isEmpty()) {
            student.setPasswordHash(passwordEncoder.encode(password));
        }
        student.setClassName(className);
        return studentRepository.save(student);
    }

    /**
     * 重置学生密码（管理员操作）
     */
    @Transactional
    public void updateStudentPassword(Long studentId, String newPassword) {
        validatePassword(newPassword);
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));
        student.setPasswordHash(passwordEncoder.encode(newPassword));
        studentRepository.save(student);
    }

    /**
     * 切换学生账号启用/禁用状态
     */
    @Transactional
    public Student toggleStudentStatus(Long studentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));
        if ("ACTIVE".equals(student.getStatus())) {
            student.setStatus("DISABLED");
        } else {
            student.setStatus("ACTIVE");
        }
        return studentRepository.save(student);
    }

    /**
     * 删除学生（级联清理选课和签到记录）
     */
    @Transactional
    public void deleteStudent(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new BusinessException("学生不存在");
        }
        enrollmentRepository.deleteByStudentId(studentId);
        studentRepository.deleteById(studentId);
    }

    /**
     * 分页查询学生
     */
    public Page<Student> searchStudents(String keyword, String className, Pageable pageable) {
        boolean hasKeyword = keyword != null && !keyword.isEmpty();
        boolean hasClass = className != null && !className.isEmpty();

        if (hasKeyword || hasClass) {
            return studentRepository.searchByKeyword(
                hasKeyword ? keyword : "",
                hasClass ? className : null,
                pageable);
        }
        return studentRepository.findAll(pageable);
    }

    /**
     * 获取所有班级名列表
     */
    public List<String> getDistinctClassNames() {
        return studentRepository.findDistinctClassNames();
    }

    /**
     * 根据ID列表获取学生
     */
    public List<Student> getStudentsByIds(List<Long> ids) {
        return studentRepository.findByIdIn(ids);
    }

    /**
     * 获取学生详情
     */
    public Student getStudent(Long studentId) {
        return studentRepository.findById(studentId)
            .orElseThrow(() -> new BusinessException("学生不存在"));
    }
}
