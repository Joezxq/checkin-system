package com.checkin.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Entity
@Table(name = "attendance_session")
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "normal_end_time")
    private LocalDateTime normalEndTime;

    @Column(name = "late_end_time")
    private LocalDateTime lateEndTime;

    @Column(name = "allow_late", nullable = false)
    private Boolean allowLate = true;

    @Column(name = "sign_method", nullable = false, length = 20)
    private String signMethod = "WEB";

    @Column(name = "allow_qr_code", nullable = false)
    private Boolean allowQrCode = true;

    @Column(name = "allow_device_check", nullable = false)
    private Boolean allowDeviceCheck = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // NOT_STARTED, IN_PROGRESS, CLOSED, EXPIRED, CANCELLED

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

    @Transient
    private String courseName;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }
}
