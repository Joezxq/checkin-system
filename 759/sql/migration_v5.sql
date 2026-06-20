-- 签到系统功能升级 — 第三阶段：智能风险预警
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS risk_score;
CREATE TABLE risk_score (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  course_id       BIGINT       NOT NULL                COMMENT '课程ID',
  student_id      BIGINT       NOT NULL                COMMENT '学生ID',
  score           INT          NOT NULL DEFAULT 0      COMMENT '风险评分 0-100',
  risk_level      VARCHAR(20)  NOT NULL                COMMENT 'LOW/MEDIUM/HIGH',
  metrics         JSON         DEFAULT NULL            COMMENT '各项指标详细数据',
  prediction      JSON         DEFAULT NULL            COMMENT '下次缺勤预测及原因',
  calculated_at   DATETIME     NOT NULL                COMMENT '计算时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_student (course_id, student_id),
  KEY idx_course_id (course_id),
  KEY idx_risk_level (risk_level),
  KEY idx_score (score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险评分表';

ALTER TABLE risk_score ADD CONSTRAINT fk_risk_course  FOREIGN KEY (course_id)  REFERENCES course(id)  ON DELETE CASCADE;
ALTER TABLE risk_score ADD CONSTRAINT fk_risk_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE CASCADE;

SET FOREIGN_KEY_CHECKS = 1;
