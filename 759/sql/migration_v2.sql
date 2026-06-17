-- 签到系统 v2 数据库迁移
-- 添加二维码签到令牌字段
use checkin_system;
ALTER TABLE attendance_session ADD COLUMN qr_token VARCHAR(64) DEFAULT NULL COMMENT 'QR签到令牌' AFTER status;
ALTER TABLE attendance_session ADD UNIQUE KEY uk_qr_token (qr_token);
