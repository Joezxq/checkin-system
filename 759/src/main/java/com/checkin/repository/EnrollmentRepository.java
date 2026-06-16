package com.checkin.repository;

import com.checkin.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByCourseId(Long courseId);

    List<Enrollment> findByStudentId(Long studentId);

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    void deleteByCourseIdAndStudentId(Long courseId, Long studentId);

    void deleteByCourseId(Long courseId);

    void deleteByStudentId(Long studentId);

    @Query("SELECT e.studentId FROM Enrollment e WHERE e.courseId = :courseId")
    List<Long> findStudentIdsByCourseId(@Param("courseId") Long courseId);
}
