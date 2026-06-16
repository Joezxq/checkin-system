package com.checkin.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "attendance_session")
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // OPEN, CLOSED

    @Column(name = "qr_token", unique = true, length = 64)
    private String qrToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 以下字段为 @Transient，仅用于前端展示，不持久化到数据库
    @Transient
    private Integer signedCount;

    @Transient
    private Integer totalCount;

    @Transient
    private Double attendanceRate;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
