-- ============================================
-- 阶段2 秒杀相关 DDL/种子数据
-- ============================================
USE car_care;

-- 号段模式发号器表（双缓冲版，实体 com.carcare.entity.SeqAlloc）
CREATE TABLE IF NOT EXISTS t_seq_alloc (
    biz_type    VARCHAR(32) PRIMARY KEY COMMENT '业务标识',
    max_id      BIGINT NOT NULL COMMENT '当前已批发到的号段上界',
    step        INT    NOT NULL COMMENT '号段步长',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '号段模式发号器';

INSERT INTO t_seq_alloc (biz_type, max_id, step) VALUES ('coupon_order', 1000, 100)
ON DUPLICATE KEY UPDATE step = VALUES(step);

-- 订单号发号段（order_no = CC+日期+号段id）
INSERT INTO t_seq_alloc (biz_type, max_id, step) VALUES ('order', 0, 1000)
ON DUPLICATE KEY UPDATE step = VALUES(step);

-- 清理早期试验用的旧发号表（如存在）
DROP TABLE IF EXISTS t_sequence;

-- 压测用户 user01~user30（密码同为 123456 的 MD5）
INSERT INTO t_user (username, password, phone, name, role)
SELECT CONCAT('user', LPAD(n, 2, '0')),
       'e10adc3949ba59abbe56e057f20f883e',
       CONCAT('13700000', LPAD(n, 3, '0')),
       CONCAT('压测用户', n), 1
FROM (
  SELECT a.d + b.d * 10 + 1 AS n
  FROM (SELECT 0 d UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
       (SELECT 0 d UNION SELECT 1 UNION SELECT 2) b
) t
WHERE n <= 30
ON DUPLICATE KEY UPDATE username = VALUES(username);
