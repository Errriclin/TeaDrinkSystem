# 数据库脚本说明

## 执行顺序（DataGrip / 命令行）

1. 创建库（若尚未创建）并选中：

```sql
CREATE DATABASE IF NOT EXISTS teadrink_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE teadrink_system;
```

2. 依次执行（同一库内）：

| 顺序 | 文件 | 说明 |
|------|------|------|
| 1 | `schema.sql` | 建表（含外键） |
| 2 | `triggers.sql` | 库存预警触发器 |
| 3 | `views.sql` | 报表视图 `v_daily_sales`、`v_product_rank` |
| 4 | `init_data.sql` | 演示数据 |

3. 如果你的库是旧版本（已存在 `t_product` 但没有 `product_tag`），再执行：

| 文件 | 说明 |
|------|------|
| `migrate_add_product_tag.sql` | 为商品表增加标签字段，默认 `常规商品` |

> **重复执行**：`schema.sql` 会先 `DROP TABLE`，可反复执行；`init_data.sql` 重复执行可能主键冲突，需先清空表或按需改写为可重复脚本。

## 演示账号（`init_data.sql`）

- 手机号：`13800138000`（店长） / `13900139000`（收银员） / `13700137000`（仓管）  
- 密码：**明文 `123456`**（仅本地联调；正式环境请在后端改为 BCrypt 等密文并更新该列）

## 逻辑设计依据

表结构以 `ER设计与表结构概要.md`（精简版）为准。
