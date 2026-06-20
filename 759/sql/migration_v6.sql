-- 签到系统功能升级 — 第四阶段：系统完善
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 管理员表
DROP TABLE IF EXISTS admin;
CREATE TABLE admin (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  username        VARCHAR(50)  NOT NULL                COMMENT '管理员用户名',
  name            VARCHAR(100) NOT NULL                COMMENT '管理员姓名',
  password_hash   VARCHAR(255) NOT NULL                COMMENT '密码哈希',
  status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- 系统配置表
DROP TABLE IF EXISTS system_config;
CREATE TABLE system_config (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  config_key      VARCHAR(100) NOT NULL                COMMENT '配置键',
  config_value    TEXT         NOT NULL                COMMENT '配置值',
  config_type     VARCHAR(20)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/BOOLEAN',
  description     VARCHAR(200) DEFAULT NULL            COMMENT '配置说明',
  updated_by      BIGINT       DEFAULT NULL            COMMENT '修改人ID',
  updated_at      DATETIME     DEFAULT NULL            COMMENT '修改时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 操作日志表
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  user_type       VARCHAR(20)  NOT NULL                COMMENT 'ADMIN/TEACHER/STUDENT',
  user_id         BIGINT       NOT NULL                COMMENT '操作人ID',
  user_name       VARCHAR(100) DEFAULT NULL            COMMENT '操作人姓名',
  operation       VARCHAR(50)  NOT NULL                COMMENT '操作类型',
  target_type     VARCHAR(50)  DEFAULT NULL            COMMENT '操作对象类型',
  target_id       BIGINT       DEFAULT NULL            COMMENT '操作对象ID',
  detail          TEXT         DEFAULT NULL            COMMENT '操作详情',
  client_ip       VARCHAR(50)  DEFAULT NULL            COMMENT '客户端IP',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_type, user_id),
  KEY idx_operation (operation),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 默认系统配置
INSERT INTO system_config (config_key, config_value, config_type, description) VALUES
('default_duration', '10', 'INT', '默认签到持续时间(分钟)'),
('default_late_time', '5', 'INT', '默认迟到宽限时间(分钟)'),
('qr_validity_seconds', '300', 'INT', '二维码有效期(秒)'),
('device_check_enabled', 'false', 'BOOLEAN', '是否开启设备校验'),
('ip_rate_limit', '5', 'INT', '同IP短时间签到上限'),
('risk_high_threshold', '70', 'INT', '高风险评分阈值'),
('risk_medium_threshold', '40', 'INT', '中风险评分阈值'),
('default_export_format', 'csv', 'STRING', '默认导出格式')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value);

SET FOREIGN_KEY_CHECKS = 1;
