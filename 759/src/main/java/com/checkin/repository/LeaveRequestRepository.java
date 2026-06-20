package com.checkin.repository;

import com.checkin.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByStudentId(Long studentId);

    List<LeaveRequest> findByCourseIdAndStudentId(Long courseId, Long studentId);

    List<LeaveRequest> findBySessionId(Long sessionId);

    List<LeaveRequest> findByCourseIdAndStatus(Long courseId, String status);

    List<LeaveRequest> findByCourseId(Long courseId);

    Optional<LeaveRequest> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentIdAndStatus(Long sessionId, Long studentId, String status);

    Page<LeaveRequest> findByCourseId(Long courseId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        "SELECT lr FROM LeaveRequest lr WHERE lr.courseId IN :courseIds AND lr.status = :status"
    )
    Page<LeaveRequest> findByCourseIdInAndStatus(
        @org.springframework.data.repository.query.Param("courseIds") List<Long> courseIds,
        @org.springframework.data.repository.query.Param("status") String status,
        Pageable pageable
    );
}
