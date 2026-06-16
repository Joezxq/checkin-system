package com.checkin.repository;

import com.checkin.entity.AttendanceSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    Optional<AttendanceSession> findByCourseIdAndStatus(Long courseId, String status);

    List<AttendanceSession> findByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);

    @Query("SELECT s FROM AttendanceSession s WHERE " +
           "(:courseId IS NULL OR s.courseId = :courseId) " +
           "AND (:dateFrom IS NULL OR s.startTime >= :dateFrom) " +
           "AND (:dateTo IS NULL OR s.startTime <= :dateTo) " +
           "ORDER BY s.startTime DESC")
    Page<AttendanceSession> findHistorySessions(
        @Param("courseId") Long courseId,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo,
        Pageable pageable
    );

    @Query("SELECT s FROM AttendanceSession s WHERE " +
           "s.courseId IN :courseIds " +
           "AND (:dateFrom IS NULL OR s.startTime >= :dateFrom) " +
           "AND (:dateTo IS NULL OR s.startTime <= :dateTo) " +
           "ORDER BY s.startTime DESC")
    Page<AttendanceSession> findHistorySessionsByCourseIds(
        @Param("courseIds") List<Long> courseIds,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo,
        Pageable pageable
    );
}
