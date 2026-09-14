-- ============================================
-- 汽车维修保养服务系统 建库脚本 v1.0 (阶段0)
-- 执行: mysql -uroot -p < car_care.sql
-- ============================================
DROP DATABASE IF EXISTS car_care;
CREATE DATABASE car_care DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE car_care;

-- 用户表
CREATE TABLE t_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(32)  NOT NULL UNIQUE COMMENT '登录账号',
    password    VARCHAR(64)  NOT NULL COMMENT '密码(MD5)',
    phone       VARCHAR(20)  DEFAULT NULL,
    name        VARCHAR(32)  NOT NULL COMMENT '姓名/昵称',
    role        TINYINT      NOT NULL DEFAULT 1 COMMENT '0管理员 1车主',
    avatar      VARCHAR(255) DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户表';

-- 门店表
CREATE TABLE t_store (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(64)  NOT NULL COMMENT '门店名',
    address        VARCHAR(255) NOT NULL,
    city           VARCHAR(32)  NOT NULL DEFAULT '杭州',
    lng            DECIMAL(10,6) NOT NULL COMMENT '经度',
    lat            DECIMAL(10,6) NOT NULL COMMENT '纬度',
    phone          VARCHAR(20)  DEFAULT NULL,
    score          DECIMAL(2,1) NOT NULL DEFAULT 5.0 COMMENT '综合评分',
    comment_scores VARCHAR(255) DEFAULT NULL COMMENT '技术,服务,环境分项评分',
    cover          VARCHAR(255) DEFAULT NULL,
    business_hours VARCHAR(64)  DEFAULT '08:30-18:00',
    status         TINYINT NOT NULL DEFAULT 1 COMMENT '0休息 1营业',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '维修门店';

-- 服务分类表
CREATE TABLE t_service_category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(32) NOT NULL UNIQUE,
    sort        INT NOT NULL DEFAULT 0,
    status      TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '保养服务分类';

-- 保养项目表
CREATE TABLE t_service_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id    BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    image       VARCHAR(255) DEFAULT NULL,
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '0停售 1起售',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_store (store_id),
    KEY idx_category (category_id)
) COMMENT '保养/维修项目';

-- 套餐表
CREATE TABLE t_package (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id    BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    status      TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_store (store_id)
) COMMENT '保养套餐';

-- 套餐明细表
CREATE TABLE t_package_item (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id  BIGINT NOT NULL,
    item_id     BIGINT NOT NULL,
    item_data   VARCHAR(512) DEFAULT NULL COMMENT '项目数据json快照',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_package (package_id)
) COMMENT '套餐包含项目';

-- 优惠券表
CREATE TABLE t_coupon (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id       BIGINT NOT NULL,
    title          VARCHAR(32) NOT NULL,
    type           TINYINT NOT NULL DEFAULT 2 COMMENT '1满减券 2代金券',
    type_desc      VARCHAR(32) DEFAULT '无门槛代金券',
    stock          INT NOT NULL DEFAULT 0,
    min_price      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛',
    discount_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    cash_price     DECIMAL(10,2) NOT NULL DEFAULT 0,
    description    VARCHAR(500) DEFAULT NULL,
    valid_start_time DATETIME NOT NULL,
    valid_end_time   DATETIME NOT NULL,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_store (store_id)
) COMMENT '优惠券';

-- 领券记录表
CREATE TABLE t_coupon_order (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    coupon_id   BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    type        TINYINT NOT NULL DEFAULT 1 COMMENT '1秒杀领取 2普通领取',
    status      TINYINT NOT NULL DEFAULT 1 COMMENT '1未使用 2已使用 3已过期',
    order_id    BIGINT DEFAULT NULL,
    start_time  DATETIME DEFAULT NULL,
    end_time    DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_coupon_user (coupon_id, user_id, type) COMMENT '防重复领取'
) COMMENT '领券记录';

-- 订单表
CREATE TABLE t_order (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no       VARCHAR(32) NOT NULL UNIQUE,
    user_id        BIGINT NOT NULL,
    store_id       BIGINT NOT NULL,
    vehicle_id     BIGINT DEFAULT NULL,
    package_id     BIGINT DEFAULT NULL,
    coupon_order_id BIGINT DEFAULT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    actual_amount  DECIMAL(10,2) NOT NULL,
    status         TINYINT NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3施工中 4已完工 5已取消 6已评价',
    pay_type       TINYINT DEFAULT NULL COMMENT '1微信 2支付宝',
    pay_status     TINYINT NOT NULL DEFAULT 0,
    remark         VARCHAR(255) DEFAULT NULL,
    appointment_time DATETIME DEFAULT NULL,
    order_time     DATETIME DEFAULT NULL,
    checkout_time  DATETIME DEFAULT NULL,
    cancel_reason  VARCHAR(255) DEFAULT NULL,
    cancel_time    DATETIME DEFAULT NULL,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id),
    KEY idx_store_status (store_id, status)
) COMMENT '服务订单';

-- 订单明细表
CREATE TABLE t_order_detail (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id    BIGINT NOT NULL,
    item_id     BIGINT DEFAULT NULL,
    item_name   VARCHAR(64) NOT NULL,
    image       VARCHAR(255) DEFAULT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    number      INT NOT NULL DEFAULT 1,
    amount      DECIMAL(10,2) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order (order_id)
) COMMENT '订单明细';

-- 维修工单表
CREATE TABLE t_work_order (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id     BIGINT NOT NULL UNIQUE,
    store_id     BIGINT NOT NULL,
    technician   VARCHAR(32) DEFAULT NULL,
    status       TINYINT NOT NULL DEFAULT 1 COMMENT '1待接单 2维修中 3待验收 4已完工',
    progress_desc VARCHAR(500) DEFAULT NULL,
    finish_images VARCHAR(1024) DEFAULT NULL,
    start_time   DATETIME DEFAULT NULL,
    finish_time  DATETIME DEFAULT NULL,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '维修工单';

-- 车辆档案表
CREATE TABLE t_vehicle (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT NOT NULL,
    plate_number     VARCHAR(16) NOT NULL,
    brand            VARCHAR(32) DEFAULT NULL,
    model            VARCHAR(64) DEFAULT NULL,
    color            VARCHAR(16) DEFAULT NULL,
    mileage          INT DEFAULT 0,
    register_date    DATE DEFAULT NULL,
    next_maintain_date DATE DEFAULT NULL COMMENT '下次保养提醒',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) COMMENT '车主车辆档案';

-- 评价表
CREATE TABLE t_review (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id    BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    order_id    BIGINT DEFAULT NULL,
    score       TINYINT NOT NULL DEFAULT 5,
    content     VARCHAR(1000) DEFAULT NULL,
    images      VARCHAR(1024) DEFAULT NULL,
    liked_count INT NOT NULL DEFAULT 0,
    deleted     TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_store_time (store_id, id),
    KEY idx_time (id)
) COMMENT '门店评价/养车笔记';

-- 关注表
CREATE TABLE t_follow (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id        BIGINT NOT NULL COMMENT '粉丝',
    follow_user_id BIGINT NOT NULL COMMENT '被关注人',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follow (user_id, follow_user_id)
) COMMENT '用户关注关系';

-- ============ 种子数据 ============
INSERT INTO t_user (username, password, phone, name, role) VALUES
('admin',  'e10adc3949ba59abbe56e057f20f883e', '13800000000', '系统管理员', 0),
('zhangsan', 'e10adc3949ba59abbe56e057f20f883e', '13900000001', '张三', 1),
('lisi', 'e10adc3949ba59abbe56e057f20f883e', '13900000002', '李四', 1);

INSERT INTO t_store (name, address, city, lng, lat, phone, score, comment_scores, business_hours) VALUES
('车管家·西湖文一店', '杭州市西湖区文一西路 128 号', '杭州', 120.087611, 30.278942, '0571-88886666', 4.8, '9.2,8.8,8.5', '08:30-18:00'),
('车管家·滨江江南大道店', '杭州市滨江区江南大道 450 号', '杭州', 120.210383, 30.208051, '0571-66668888', 4.6, '8.9,9.1,8.2', '09:00-19:00');

INSERT INTO t_service_category (name, sort) VALUES
('发动机与机油', 1), ('轮胎与轮毂', 2), ('制动系统', 3), ('车身与电子', 4);

INSERT INTO t_service_item (store_id, category_id, name, price, description) VALUES
(1, 1, '小保养（更换机油机滤）', 268.00, '含原厂机油4L+机滤一个，约30分钟'),
(1, 1, '大保养（机油三滤+全车检查）', 899.00, '机油机滤空滤汽滤+26项安全检查'),
(1, 1, '发动机深度清洗', 388.00, '内积碳可视化清洗'),
(1, 2, '轮胎更换（单条）含动平衡', 459.00, '常见17寸规格'),
(1, 2, '四轮换位', 80.00, '每1万公里建议一次'),
(1, 3, '前刹车片更换（一对）', 560.00, '原厂品质刹车片'),
(1, 4, '空调滤芯更换', 88.00, '带活性炭'),
(1, 4, '电瓶更换（以旧换新）', 520.00, '瓦尔塔 60Ah'),
(2, 1, '小保养（更换机油机滤）', 258.00, '含半合成机油4L+机滤'),
(2, 1, '大保养（机油三滤+全车检查）', 869.00, '26项安全检查'),
(2, 3, '刹车油更换', 180.00, 'DOT4 全循环更换'),
(2, 4, '全车打蜡', 168.00, '固体蜡养护');

INSERT INTO t_package (store_id, name, price, description) VALUES
(1, '安心小保养套餐', 668.00, '小保养+空调滤芯+26项检查，立省88元'),
(1, '大保养尊享套餐', 1599.00, '大保养+发动机深度清洗+全车打蜡');

INSERT INTO t_package_item (package_id, item_id, item_data) VALUES
(1, 1, '{"name":"小保养（更换机油机滤）","price":268.00}'),
(1, 7, '{"name":"空调滤芯更换","price":88.00}'),
(2, 2, '{"name":"大保养（机油三滤+全车检查）","price":899.00}'),
(2, 3, '{"name":"发动机深度清洗","price":388.00}'),
(2, 12, '{"name":"全车打蜡","price":168.00}');

INSERT INTO t_coupon (store_id, title, type, type_desc, stock, min_price, discount_price, cash_price, description, valid_start_time, valid_end_time) VALUES
(1, '大保养满1000减100', 1, '满减券', 20, 1000, 100, 0, '限大保养套餐使用', '2026-09-01 00:00:00', '2026-12-31 23:59:59'),
(1, '无门槛50元保养代金券', 2, '无门槛代金券', 30, 0, 0, 50, '任意保养项目可用', '2026-09-01 00:00:00', '2026-12-31 23:59:59');

INSERT INTO t_vehicle (user_id, plate_number, brand, model, color, mileage, next_maintain_date) VALUES
(2, '浙A·88888', '大众', '迈腾 380TSI', '黑色', 62000, '2026-10-20'),
(3, '浙A·66666', '特斯拉', 'Model 3', '白色', 31000, '2026-12-01');

INSERT INTO t_order (order_no, user_id, store_id, vehicle_id, total_amount, discount_amount, actual_amount, status, pay_type, pay_status, remark, appointment_time, order_time, checkout_time) VALUES
('CC10001', 2, 1, 1, 268.00, 0, 268.00, 2, 1, 1, '顺便检查下刹车异响', '2026-09-15 10:00:00', '2026-09-14 09:00:00', '2026-09-14 09:01:00'),
('CC10002', 3, 1, 2, 668.00, 0, 668.00, 1, NULL, 0, '提前预约小保养', '2026-09-16 14:00:00', '2026-09-14 09:30:00', NULL);

INSERT INTO t_order_detail (order_id, item_id, item_name, unit_price, number, amount) VALUES
(1, 1, '小保养（更换机油机滤）', 268.00, 1, 268.00),
(2, NULL, '安心小保养套餐', 668.00, 1, 668.00);

INSERT INTO t_work_order (order_id, store_id, status, progress_desc, start_time) VALUES
(1, 1, 2, '车辆已进店，等待工位', '2026-09-14 09:05:00');

INSERT INTO t_review (store_id, user_id, order_id, score, content, liked_count) VALUES
(1, 2, 1, 5, '技师很专业，保养前主动出示了机油保质期，下次还来。', 12),
(1, 3, NULL, 4, '环境不错，就是高峰期要等位。', 5);
