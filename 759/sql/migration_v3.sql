-- 签到系统 v3 数据库迁移
-- 添加外键约束、索引

-- 1. class_name 索引（支持班级筛选）
CREATE INDEX idx_student_class_name ON student(class_name);

-- 2. 复合索引（签到活动按课程+状态查询）
CREATE INDEX idx_session_course_status ON attendance_session(course_id, status);

-- 3. 外键约束
ALTER TABLE course ADD CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE CASCADE;
ALTER TABLE enrollment ADD CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE;
ALTER TABLE enrollment ADD CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;
ALTER TABLE attendance_session ADD CONSTRAINT fk_session_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE;
ALTER TABLE attendance_record ADD CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES attendance_session(id) ON DELETE CASCADE;
ALTER TABLE attendance_record ADD CONSTRAINT fk_record_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;
