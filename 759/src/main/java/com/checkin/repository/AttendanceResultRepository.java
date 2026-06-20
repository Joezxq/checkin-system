package com.checkin.repository;

import com.checkin.entity.AttendanceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceResultRepository extends JpaRepository<AttendanceResult, Long> {

    List<AttendanceResult> findBySessionId(Long sessionId);

    List<AttendanceResult> findByCourseIdAndStudentId(Long courseId, Long studentId);

    Optional<AttendanceResult> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    @Query("SELECT COUNT(ar) FROM AttendanceResult ar WHERE ar.courseId = :courseId AND ar.studentId = :studentId AND ar.status = :status")
    long countByCourseIdAndStudentIdAndStatus(@Param("courseId") Long courseId,
                                              @Param("studentId") Long studentId,
                                              @Param("status") String status);

    @Query("SELECT COUNT(ar) FROM AttendanceResult ar WHERE ar.courseId = :courseId AND ar.studentId = :studentId")
    long countTotalByCourseIdAndStudentId(@Param("courseId") Long courseId,
                                          @Param("studentId") Long studentId);

    @Query("SELECT COUNT(ar) FROM AttendanceResult ar WHERE ar.sessionId = :sessionId AND ar.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") Long sessionId,
                                   @Param("status") String status);

    void deleteBySessionId(Long sessionId);

    void deleteByStudentId(Long studentId);

    @Query("SELECT ar FROM AttendanceResult ar WHERE ar.sessionId IN :sessionIds")
    List<AttendanceResult> findBySessionIdIn(@Param("sessionIds") List<Long> sessionIds);
}
