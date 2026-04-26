-- ============================================================
-- 库存预警触发器：t_material 库存低于安全库存时写入 t_stock_warn
-- 依赖：已执行 schema.sql
-- MySQL 5.7+ / 8.0+
--
-- 说明：不使用 DELIMITER / BEGIN…END，避免 DataGrip 等工具把 DELIMITER
-- 当 SQL 执行报错。单条 INSERT…SELECT…WHERE 在 FOR EACH ROW 下合法。
-- ============================================================

SET NAMES utf8mb4;

DROP TRIGGER IF EXISTS trg_material_low_stock_after_update;
DROP TRIGGER IF EXISTS trg_material_low_stock_after_insert;

-- 更新库存后：若达到或低于安全库存则插入一条预警（与查询端 is_low 条件一致，条件不满足时 SELECT 0 行，不插入）
CREATE TRIGGER trg_material_low_stock_after_update
AFTER UPDATE ON t_material
FOR EACH ROW
INSERT INTO t_stock_warn (material_id, current_qty, safe_qty, warn_time, handled)
SELECT NEW.id, NEW.stock_quantity, NEW.safety_stock, NOW(), 0
WHERE (NEW.safety_stock > 0 AND NEW.stock_quantity <= NEW.safety_stock)
   OR (NEW.safety_stock = 0 AND NEW.stock_quantity <= 0);

-- 新增原料：若初始库存即达到或低于安全线，同样插入预警
CREATE TRIGGER trg_material_low_stock_after_insert
AFTER INSERT ON t_material
FOR EACH ROW
INSERT INTO t_stock_warn (material_id, current_qty, safe_qty, warn_time, handled)
SELECT NEW.id, NEW.stock_quantity, NEW.safety_stock, NOW(), 0
WHERE (NEW.safety_stock > 0 AND NEW.stock_quantity <= NEW.safety_stock)
   OR (NEW.safety_stock = 0 AND NEW.stock_quantity <= 0);

-- 说明：
-- 1) 每次 UPDATE 后只要仍低于安全库存都会插入一条预警，便于课程演示；
--    若生产环境嫌多，可改为「仅当从高于等于安全库存变为低于时」再插入（需用可写 DELIMITER 的客户端写 OLD/NEW 判断）。
-- 2) 销售扣库存、采购入库等均会更新 t_material.stock_quantity，从而触发本逻辑。
