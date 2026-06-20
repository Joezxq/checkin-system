package com.checkin.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Entity
@Table(name = "attendance_result", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"session_id", "student_id"})
})
public class AttendanceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // NORMAL, LATE, LEAVE, ABSENT, ABNORMAL

    @Column(name = "sign_time")
    private LocalDateTime signTime;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "client_device_id", length = 100)
    private String clientDeviceId;

    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @Column(name = "abnormal_reason", length = 500)
    private String abnormalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Transient fields for frontend display
    @Transient
    private String studentName;

    @Transient
    private String studentNo;

    @Transient
    private String className;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        }
    }
}
