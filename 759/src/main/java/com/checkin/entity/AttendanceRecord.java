package com.checkin.entity;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "attendance_record", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"session_id", "student_id"}),
    @UniqueConstraint(columnNames = {"session_id", "client_device_id"})
})
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "sign_time", nullable = false)
    private LocalDateTime signTime;

    @Column(name = "client_ip", nullable = false, length = 50)
    private String clientIp;

    @Column(name = "client_device_id", nullable = false, length = 100)
    private String clientDeviceId;

    @Column(name = "geo_lat", precision = 10, scale = 6)
    private BigDecimal geoLat;

    @Column(name = "geo_lng", precision = 10, scale = 6)
    private BigDecimal geoLng;
}
