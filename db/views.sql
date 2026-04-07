-- ============================================================
-- 视图定义（与 front-end/js/index.js 报表字段对齐）
-- 依赖：已执行 schema.sql
-- ============================================================

SET NAMES utf8mb4;

-- 删除旧视图（若存在）
DROP VIEW IF EXISTS v_product_rank;
DROP VIEW IF EXISTS v_daily_sales;

-- ------------------------------------------------------------
-- 销售日报：供 /api/v_daily_sales 与 renderDailyReport
-- 字段：sale_date, order_count, total_amount, discount_amount,
--       real_income, cash_amount, mobile_amount
-- ------------------------------------------------------------
CREATE VIEW v_daily_sales AS
SELECT
    DATE(o.created_at) AS sale_date,
    COUNT(*) AS order_count,
    IFNULL(SUM(o.total_amount), 0)    AS total_amount,
    IFNULL(SUM(o.discount_amount), 0) AS discount_amount,
    IFNULL(SUM(o.pay_amount), 0)      AS real_income,
    IFNULL(SUM(CASE WHEN o.pay_type = 1 THEN o.pay_amount ELSE 0 END), 0) AS cash_amount,
    IFNULL(SUM(CASE WHEN o.pay_type IN (2, 3) THEN o.pay_amount ELSE 0 END), 0) AS mobile_amount
FROM t_sale_order o
WHERE o.status = 1
GROUP BY DATE(o.created_at)
ORDER BY sale_date DESC;

-- ------------------------------------------------------------
-- 商品销售排行：供 /api/v_product_rank 与 renderProductRank
-- 字段：name, category, order_count, total_sold
-- ------------------------------------------------------------
CREATE VIEW v_product_rank AS
SELECT
    p.id AS product_id,
    p.name,
    p.category,
    COUNT(DISTINCT i.sale_order_id) AS order_count,
    IFNULL(SUM(i.quantity), 0) AS total_sold
FROM t_product p
INNER JOIN t_sale_order_item i ON i.product_id = p.id
INNER JOIN t_sale_order o ON o.id = i.sale_order_id AND o.status = 1
GROUP BY p.id, p.name, p.category
ORDER BY total_sold DESC, order_count DESC;
