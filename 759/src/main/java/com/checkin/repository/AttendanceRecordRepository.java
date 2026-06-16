package com.checkin.repository;

import com.checkin.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndClientDeviceId(Long sessionId, String clientDeviceId);

    boolean existsBySessionIdAndClientIpAndClientDeviceId(Long sessionId, String clientIp, String clientDeviceId);

    List<AttendanceRecord> findBySessionId(Long sessionId);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT r.studentId FROM AttendanceRecord r WHERE r.sessionId = :sessionId")
    List<Long> findStudentIdsBySessionId(@Param("sessionId") Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteByStudentId(Long studentId);
}
