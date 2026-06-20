-- 签到系统功能升级 — 第一阶段：核心考勤闭环
-- 执行前确保已有 schema.sql → seed.sql → migration_v2.sql → migration_v3.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 修改 attendance_session 表，新增签到活动相关字段
-- ============================================================
ALTER TABLE attendance_session
  ADD COLUMN title                VARCHAR(200)  DEFAULT NULL   COMMENT '签到活动标题'       AFTER course_id,
  ADD COLUMN normal_end_time      DATETIME      DEFAULT NULL   COMMENT '正常签到截止时间'     AFTER end_time,
  ADD COLUMN late_end_time        DATETIME      DEFAULT NULL   COMMENT '迟到签到截止时间'     AFTER normal_end_time,
  ADD COLUMN allow_late           TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否允许迟到签到'  AFTER late_end_time,
  ADD COLUMN sign_method          VARCHAR(20)   NOT NULL DEFAULT 'WEB' COMMENT '签到方式: WEB/QR' AFTER allow_late,
  ADD COLUMN allow_qr_code        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否允许扫码签到'  AFTER sign_method,
  ADD COLUMN allow_device_check   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否开启设备校验'   AFTER allow_qr_code;

-- 更新 status 字段注释以反映新状态值
ALTER TABLE attendance_session
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'
    COMMENT '状态: NOT_STARTED/IN_PROGRESS/CLOSED/EXPIRED/CANCELLED';

-- 将已有 CLOSED 记录的 normal_end_time 设为 end_time（数据修复）
UPDATE attendance_session
SET normal_end_time = end_time
WHERE status = 'CLOSED' AND normal_end_time IS NULL;

-- 将已有 OPEN 记录迁移到 IN_PROGRESS（状态值升级）
UPDATE attendance_session
SET status = 'IN_PROGRESS', normal_end_time = end_time
WHERE status = 'OPEN';

-- ============================================================
-- 2. 新建 attendance_result（考勤结果表）
-- ============================================================
DROP TABLE IF EXISTS attendance_result;
CREATE TABLE attendance_result (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  session_id        BIGINT       NOT NULL                COMMENT '签到活动ID',
  course_id         BIGINT       NOT NULL                COMMENT '课程ID',
  student_id        BIGINT       NOT NULL                COMMENT '学生ID',
  status            VARCHAR(20)  NOT NULL                COMMENT '考勤状态: NORMAL/LATE/LEAVE/ABSENT/ABNORMAL',
  sign_time         DATETIME     DEFAULT NULL            COMMENT '签到时间',
  client_ip         VARCHAR(50)  DEFAULT NULL            COMMENT '客户端IP',
  client_device_id  VARCHAR(100) DEFAULT NULL            COMMENT '设备ID',
  leave_request_id  BIGINT       DEFAULT NULL            COMMENT '关联的请假申请ID',
  abnormal_reason   VARCHAR(500) DEFAULT NULL            COMMENT '异常原因说明',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_student (session_id, student_id),
  KEY idx_session_id (session_id),
  KEY idx_course_id (course_id),
  KEY idx_student_id (student_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考勤结果表';

-- ============================================================
-- 3. 新建 leave_request（请假申请表）
-- ============================================================
DROP TABLE IF EXISTS leave_request;
CREATE TABLE leave_request (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  course_id       BIGINT       NOT NULL                COMMENT '课程ID',
  session_id      BIGINT       DEFAULT NULL            COMMENT '关联签到活动，可为空',
  student_id      BIGINT       NOT NULL                COMMENT '学生ID',
  leave_type      VARCHAR(20)  NOT NULL                COMMENT '请假类型: PERSONAL/SICK/OTHER',
  reason          TEXT         NOT NULL                COMMENT '请假原因',
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  teacher_comment VARCHAR(500) DEFAULT NULL            COMMENT '教师审批意见',
  reviewed_by     BIGINT       DEFAULT NULL            COMMENT '审批教师ID',
  reviewed_at     DATETIME     DEFAULT NULL            COMMENT '审批时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_course_id (course_id),
  KEY idx_session_id (session_id),
  KEY idx_student_id (student_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假申请表';

-- ============================================================
-- 4. 修改 student 和 teacher 表，新增 status 列
-- ============================================================
ALTER TABLE student
  ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE/DISABLED' AFTER class_name;

ALTER TABLE teacher
  ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态: ACTIVE/DISABLED' AFTER name;

-- ============================================================
-- 外键约束
-- ============================================================
ALTER TABLE attendance_result ADD CONSTRAINT fk_result_session  FOREIGN KEY (session_id) REFERENCES attendance_session(id) ON DELETE CASCADE;
ALTER TABLE attendance_result ADD CONSTRAINT fk_result_student  FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;
ALTER TABLE leave_request      ADD CONSTRAINT fk_leave_course   FOREIGN KEY (course_id)  REFERENCES course(id)  ON DELETE CASCADE;
ALTER TABLE leave_request      ADD CONSTRAINT fk_leave_student  FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;
ALTER TABLE leave_request      ADD CONSTRAINT fk_leave_session  FOREIGN KEY (session_id) REFERENCES attendance_session(id) ON DELETE SET NULL;

SET FOREIGN_KEY_CHECKS = 1;
