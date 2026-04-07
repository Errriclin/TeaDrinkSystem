# 联调测试：POS 下单模块（事务扣库存）

本测试在 **Nginx 同源联调**环境下进行（前端静态 + `/api` 反代到后端 8080）。

## 一、准备

- 后端启动成功：`http://127.0.0.1:8080/api/health`
- Nginx 启动成功：`http://127.0.0.1/html/Login.html`
- 数据库已执行：
  - `db/schema.sql`
  - `db/views.sql`
  - `db/init_data.sql`（至少要有商品、配方 `t_product_material`、原料库存）

## 二、页面联调步骤（必测）

### 1）登录

打开：`http://127.0.0.1/html/Login.html`  
登录成功后会跳到：`/html/Mainwindow.html?token=...`

### 2）进入收银（POS）并下单

在主界面进入“收银 / 点单”页面：

- 选择任意商品加入购物车（至少 1 件）
- 选择支付方式（现金/微信/支付宝/余额）
- 可选：输入会员手机号点击“查询会员”
- 点击“提交订单”

预期：
- 页面提示“订单提交成功”
- Network 面板看到 `POST /api/sale_order` 返回 200

### 3）验证事务效果（页面）

提交成功后，切换到：

- **工作台**
  - 最近订单出现新订单
  - 今日订单数/今日营收有变化（取决于是否有初始化“今天”的订单）
  - 热销排行可能变化
- **库存**
  - 原料库存减少（若商品配置了配方）
  - 库存流水出现“销售出库”记录

## 三、接口联调断言（可选，用于快速排错）

打开浏览器 F12 → Network：

- `POST /api/sale_order`
  - Request Payload 字段包含：`member_id,total_amount,discount_amount,pay_amount,pay_type,items`
  - Response 为 `{success:true,msg:"ok",data:{order_no:"SO..."}}`
- `GET /api/material/list`
  - 对应原料的 `stock_quantity` 下降
- `GET /api/inventory_log/recent?limit=10`
  - 出现 `type_name = "销售出库"` 的记录

## 四、常见失败与定位

- **返回 401**
  - token 丢失/过期，重新登录即可
- **返回 “库存不足”**
  - 原料库存小于配方消耗（或库存为 0）
  - 这是正确的事务回滚行为：订单与明细不会落库、库存不会变
- **库存没有减少**
  - 该商品在 `t_product_material` 没有配置配方（演示项目允许不扣原料）
  - 想测试扣库存：给该商品补一条配方记录即可

