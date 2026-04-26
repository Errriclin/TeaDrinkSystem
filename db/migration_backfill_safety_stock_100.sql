-- 将“未设安全库存”(0) 的原料统一设为 100；已有非 0 值的不变。
-- 在已有环境执行一次：mysql -u... -p... teadrink < db/migration_backfill_safety_stock_100.sql

SET NAMES utf8mb4;
UPDATE t_material SET safety_stock = 100.000 WHERE safety_stock = 0.000;
