-- 签到系统数据库表结构
-- 字符集：utf8mb4

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 表：教师
-- ----------------------------
DROP TABLE IF EXISTS `teacher`;
CREATE TABLE `teacher` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `teacher_no` VARCHAR(50) NOT NULL COMMENT '工号',
  `name` VARCHAR(100) NOT NULL COMMENT '姓名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_no` (`teacher_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师表';

-- ----------------------------
-- 表：学生
-- ----------------------------
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_no` VARCHAR(50) NOT NULL COMMENT '学号',
  `name` VARCHAR(100) NOT NULL COMMENT '姓名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `class_name` VARCHAR(100) DEFAULT NULL COMMENT '班级',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_class_name` (`class_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生表';

-- ----------------------------
-- 表：课程
-- ----------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_name` VARCHAR(200) NOT NULL COMMENT '课程名称',
  `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- ----------------------------
-- 表：选课记录
-- ----------------------------
DROP TABLE IF EXISTS `enrollment`;
CREATE TABLE `enrollment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_student` (`course_id`, `student_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选课记录表';

-- ----------------------------
-- 表：签到活动
-- ----------------------------
DROP TABLE IF EXISTS `attendance_session`;
CREATE TABLE `attendance_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN-开放，CLOSED-关闭',
  `qr_token` VARCHAR(64) DEFAULT NULL COMMENT 'QR签到令牌',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_status` (`status`),
  UNIQUE KEY `uk_qr_token` (`qr_token`),
  KEY `idx_course_status` (`course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到活动表';

-- ----------------------------
-- 表：签到记录
-- ----------------------------
DROP TABLE IF EXISTS `attendance_record`;
CREATE TABLE `attendance_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT NOT NULL COMMENT '签到活动ID',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `student_id` BIGINT NOT NULL COMMENT '学生ID',
  `sign_time` DATETIME NOT NULL COMMENT '签到时间',
  `client_ip` VARCHAR(50) NOT NULL COMMENT '客户端IP',
  `client_device_id` VARCHAR(100) NOT NULL COMMENT '客户端设备ID',
  `geo_lat` DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度',
  `geo_lng` DECIMAL(10, 6) DEFAULT NULL COMMENT '经度',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_student` (`session_id`, `student_id`),
  UNIQUE KEY `uk_session_device` (`session_id`, `client_device_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_course_sign_time` (`course_id`, `sign_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';

-- ----------------------------
-- 外键约束
-- ----------------------------
ALTER TABLE course ADD CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE CASCADE;
ALTER TABLE enrollment ADD CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE;
ALTER TABLE enrollment ADD CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;
ALTER TABLE attendance_session ADD CONSTRAINT fk_session_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE;
ALTER TABLE attendance_record ADD CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES attendance_session(id) ON DELETE CASCADE;
ALTER TABLE attendance_record ADD CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS = 1;
