-- ============================================================
-- 茶饮店进销存系统 - 建表脚本（精简版 12 张表）
-- MySQL 5.7+ / 8.0+，字符集 utf8mb4
-- 执行前请确认已创建数据库，例如：
--   CREATE DATABASE teadrink_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE teadrink_system;
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS t_stock_warn;
DROP TABLE IF EXISTS t_inventory_log;
DROP TABLE IF EXISTS t_member_account_log;
DROP TABLE IF EXISTS t_sale_order_item;
DROP TABLE IF EXISTS t_sale_order;
DROP TABLE IF EXISTS t_purchase_order_item;
DROP TABLE IF EXISTS t_purchase_order;
DROP TABLE IF EXISTS t_product_material;
DROP TABLE IF EXISTS t_member;
DROP TABLE IF EXISTS t_material;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_user;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 1. 登录用户（收银员 / 店长）
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone         VARCHAR(20)  NOT NULL COMMENT '登录手机号',
    password      VARCHAR(255) NOT NULL COMMENT '密码（BCrypt 等密文）',
    name          VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '姓名',
    role          VARCHAR(32)  NOT NULL DEFAULT 'CASHIER' COMMENT '角色：ADMIN/CASHIER/WAREHOUSE',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 停用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_role (role),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

-- ------------------------------------------------------------
-- 2. 在售商品
-- ------------------------------------------------------------
CREATE TABLE t_product (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '商品名',
    category    VARCHAR(32)  NOT NULL DEFAULT '奶茶' COMMENT '分类：奶茶/果茶/咖啡/甜品',
    product_tag VARCHAR(32)  NOT NULL DEFAULT '常规商品' COMMENT '商品标签：常规商品/当季限定/地区限定/自定义',
    sale_price  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    image_url   VARCHAR(512) NULL COMMENT '图片 URL',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_category (category),
    KEY idx_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品';

-- ------------------------------------------------------------
-- 3. 原料（含当前库存与安全库存）
-- ------------------------------------------------------------
CREATE TABLE t_material (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100) NOT NULL COMMENT '原料名称',
    unit            VARCHAR(16)  NOT NULL DEFAULT 'g' COMMENT '单位',
    stock_quantity  DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '当前库存',
    safety_stock    DECIMAL(12,3) NOT NULL DEFAULT 100.000 COMMENT '安全库存',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近变更时间',
    PRIMARY KEY (id),
    KEY idx_material_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原料与库存';

-- ------------------------------------------------------------
-- 4. 商品-原料配方（每份商品消耗原料数量）
-- ------------------------------------------------------------
CREATE TABLE t_product_material (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id  BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    material_id BIGINT UNSIGNED NOT NULL COMMENT '原料 ID',
    consume_qty DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '每份商品消耗量',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_material (product_id, material_id),
    KEY idx_pm_material (material_id),
    CONSTRAINT fk_pm_product  FOREIGN KEY (product_id)  REFERENCES t_product (id)  ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_pm_material FOREIGN KEY (material_id) REFERENCES t_material (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品原料配方';

-- ------------------------------------------------------------
-- 5. 会员
-- ------------------------------------------------------------
CREATE TABLE t_member (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone          VARCHAR(20)  NOT NULL COMMENT '手机号',
    name           VARCHAR(50)  NULL COMMENT '姓名',
    level          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 普通 2 银卡 3 金卡',
    balance        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '储值余额',
    points         INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '积分',
    total_consume  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 冻结',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_phone (phone),
    KEY idx_member_level (level),
    KEY idx_member_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员';

-- ------------------------------------------------------------
-- 6. 采购单主表
-- ------------------------------------------------------------
CREATE TABLE t_purchase_order (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no       VARCHAR(32)  NOT NULL COMMENT '采购单号',
    supplier_name  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '供应商名称（文本）',
    total_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待入库 1 已入库 2 已取消',
    operator_id    BIGINT UNSIGNED NULL COMMENT '操作人',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inbound_at     DATETIME     NULL COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_order_no (order_no),
    KEY idx_purchase_status (status),
    KEY idx_purchase_created (created_at),
    CONSTRAINT fk_purchase_operator FOREIGN KEY (operator_id) REFERENCES t_user (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购单';

-- ------------------------------------------------------------
-- 7. 采购明细
-- ------------------------------------------------------------
CREATE TABLE t_purchase_order_item (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    purchase_order_id BIGINT UNSIGNED NOT NULL COMMENT '采购单 ID',
    material_id       BIGINT UNSIGNED NOT NULL COMMENT '原料 ID',
    quantity          DECIMAL(12,3) NOT NULL DEFAULT 0.000 COMMENT '数量',
    unit_price        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
    subtotal          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '小计',
    PRIMARY KEY (id),
    KEY idx_poi_order (purchase_order_id),
    KEY idx_poi_material (material_id),
    CONSTRAINT fk_poi_order    FOREIGN KEY (purchase_order_id) REFERENCES t_purchase_order (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_poi_material FOREIGN KEY (material_id)       REFERENCES t_material (id)       ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购明细';

-- ------------------------------------------------------------
-- 8. 销售订单主表
-- ------------------------------------------------------------
CREATE TABLE t_sale_order (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)  NOT NULL COMMENT '订单号',
    member_id       BIGINT UNSIGNED NULL COMMENT '会员 ID，散客为空',
    total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '商品原价合计',
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    pay_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    pay_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1现金 2微信 3支付宝 4余额',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 已完成 0 已取消',
    cashier_id      BIGINT UNSIGNED NOT NULL COMMENT '收银员',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sale_order_no (order_no),
    KEY idx_sale_member (member_id),
    KEY idx_sale_cashier (cashier_id),
    KEY idx_sale_created (created_at),
    KEY idx_sale_status (status),
    CONSTRAINT fk_sale_member  FOREIGN KEY (member_id)  REFERENCES t_member (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_sale_cashier FOREIGN KEY (cashier_id) REFERENCES t_user (id)    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售订单';

-- ------------------------------------------------------------
-- 9. 销售明细
-- ------------------------------------------------------------
CREATE TABLE t_sale_order_item (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    sale_order_id BIGINT UNSIGNED NOT NULL COMMENT '销售订单 ID',
    product_id    BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    product_name  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '商品名快照',
    unit_price    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价快照',
    quantity      INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '数量',
    subtotal      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '小计',
    PRIMARY KEY (id),
    KEY idx_soi_order (sale_order_id),
    KEY idx_soi_product (product_id),
    CONSTRAINT fk_soi_order   FOREIGN KEY (sale_order_id) REFERENCES t_sale_order (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_soi_product FOREIGN KEY (product_id)    REFERENCES t_product (id)   ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售明细';

-- ------------------------------------------------------------
-- 10. 会员账务流水（余额 + 积分）
-- ------------------------------------------------------------
CREATE TABLE t_member_account_log (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    member_id          BIGINT UNSIGNED NOT NULL COMMENT '会员 ID',
    biz_type           VARCHAR(32)  NOT NULL COMMENT '业务类型：RECHARGE/ORDER_PAY/ORDER_POINTS 等',
    delta_balance      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '余额变动',
    delta_points       INT           NOT NULL DEFAULT 0 COMMENT '积分变动',
    balance_after      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '变动后余额',
    points_after       INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '变动后积分',
    ref_sale_order_id  BIGINT UNSIGNED NULL COMMENT '关联销售订单',
    remark             VARCHAR(255) NULL COMMENT '备注',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mal_member (member_id),
    KEY idx_mal_created (created_at),
    CONSTRAINT fk_mal_member FOREIGN KEY (member_id) REFERENCES t_member (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_mal_order  FOREIGN KEY (ref_sale_order_id) REFERENCES t_sale_order (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员账务流水';

-- ------------------------------------------------------------
-- 11. 库存流水
-- ------------------------------------------------------------
CREATE TABLE t_inventory_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    material_id BIGINT UNSIGNED NOT NULL COMMENT '原料 ID',
    change_qty  DECIMAL(12,3) NOT NULL COMMENT '变动数量（正入库负出库）',
    after_stock DECIMAL(12,3) NOT NULL COMMENT '变动后库存',
    biz_type    VARCHAR(32)  NOT NULL COMMENT 'PURCHASE_IN/SALE_OUT/ADJUST 等',
    ref_id      BIGINT UNSIGNED NULL COMMENT '关联业务主键（采购单/销售单等）',
    type_name   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '前端展示用说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_inv_material (material_id),
    KEY idx_inv_created (created_at),
    CONSTRAINT fk_inv_material FOREIGN KEY (material_id) REFERENCES t_material (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水';

-- ------------------------------------------------------------
-- 12. 库存预警（可由触发器写入）
-- ------------------------------------------------------------
CREATE TABLE t_stock_warn (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    material_id BIGINT UNSIGNED NOT NULL COMMENT '原料 ID',
    current_qty DECIMAL(12,3) NOT NULL COMMENT '触发时库存',
    safe_qty    DECIMAL(12,3) NOT NULL COMMENT '触发时安全库存',
    warn_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    handled     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未处理 1 已处理',
    PRIMARY KEY (id),
    KEY idx_warn_material (material_id),
    KEY idx_warn_handled (handled),
    CONSTRAINT fk_warn_material FOREIGN KEY (material_id) REFERENCES t_material (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预警记录';
