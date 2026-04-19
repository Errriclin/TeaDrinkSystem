# 茶饮店进销存管理系统（Teadrink System）

面向小型茶饮门店的 **进销存 + POS 收银 + 会员 + 报表** 一体化 Web 应用。后端为 Spring Boot，前端为静态 HTML/JS，可通过 Nginx 同源部署；支持 Redis 缓存登录态与部分报表数据。

---

## 功能概览

- **认证**：用户注册 / 登录，Token 鉴权（`X-Token`），会话可存储于 Redis。
- **收银（POS）**：商品点单、配方扣减原料库存、多种支付方式、会员积分/余额。
- **订单**：订单列表、详情、取消（回补库存）。
- **商品**：在售列表、上下架、配方（BOM）维护。
- **会员**：查询、列表、充值、详情（账务与最近订单）。
- **库存**：原料列表、低库存提示、盘点调整、库存流水。
- **采购**：采购单创建、确认入库。
- **报表 / 工作台**：今日汇总、收入趋势、商品销量排行、销售日报视图等。
- **前端**：顶栏支持按 **11 位手机号** 或 **订单号（SO 开头）** 快捷搜索并打开详情弹窗。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 8、Spring Boot 2.7、MyBatis-Plus、Spring Data Redis |
| 数据库 | MySQL 8 |
| 缓存 | Redis（Lettuce） |
| 前端 | HTML、Tailwind CSS（CDN）、原生 JavaScript |
| 部署（可选） | Nginx 静态资源 + `/api` 反向代理 |

---

## 目录结构（简要）

```
teadrinkSystem/
├── backend/                 # Spring Boot 后端源码
├── frontend/                # 前端静态页与 JS（如 html/Login.html、js/index.js）
├── db/                      # 数据库脚本与说明（见 db/README.md）
├── nginx-tea/               # Nginx 示例配置（Windows 路径需按本机修改）
├── deploy/                  # 部署相关说明
└── README.md                # 本文件
```

---

## 环境要求

- **JDK 8+**
- **Maven 3.6+**
- **MySQL 8**（本地或远程实例）
- **Redis**（与 `application.yml` 中地址一致，默认 `127.0.0.1:6379`）

---

## 数据库初始化

详见 **[db/README.md](db/README.md)**。推荐顺序：

1. 创建数据库 `teadrink_system`（utf8mb4）。
2. 依次执行：`schema.sql` → `triggers.sql` → `views.sql` → `init_data.sql`。

演示账号以 `init_data.sql` 为准（示例见 `db/README.md`）。**生产环境请勿使用明文密码**，请在后端改为 BCrypt 等并更新数据库。

---

## 配置说明

主要配置文件：`backend/src/main/resources/application.yml`。

- **MySQL**：`spring.datasource.url`、`username`、`password`
- **Redis**：`spring.redis.host`、`port`
- **应用**：`app.auth.dashboard-path`（登录/注册成功后的前端跳转路径，如 `/html/Mainwindow.html`）、`app.auth.token-ttl-hours`

> **安全提示**：提交到公开 Git 仓库前，请将数据库密码等敏感信息改为环境变量或本地覆盖配置，**勿将真实密码写入仓库**。可使用 `application-local.yml`（并加入 `.gitignore`）或 IDE 运行参数注入。

---

## 本地运行

### 1. 启动 MySQL 与 Redis

确保 Redis 可访问，MySQL 已按上文初始化。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认后端地址：**http://127.0.0.1:8080**  
健康检查：**GET** `/api/health`

### 3. 访问前端（两种方式任选其一）

**方式 A：直接连后端（开发调试）**

- 需处理跨域时，可临时开启后端 CORS（当前项目已配置较宽的跨域，仅建议开发环境使用）。
- 打开 `frontend/html/Login.html` 时，请将其中 API 基础地址配置为可访问的后端（若使用项目内 `Login.html` / `Register.html` 的 `BASE_URL: ''`，则需通过 **同源** 访问，见方式 B）。

**方式 B：Nginx 同源部署（推荐）**

- 参考 `nginx-tea/conf/nginx.conf`，将 `root` 指到本机的 `frontend` 目录，`location /api/` 反代到 `http://127.0.0.1:8080`。
- 浏览器访问：**http://127.0.0.1/html/Login.html**（具体端口以 Nginx 监听为准）。

登录成功后，按配置跳转到工作台（如 `/html/Mainwindow.html?token=...`），前端会将 Token 写入 `localStorage` 并在后续请求中携带 `X-Token`。

---

## Redis 在本项目中的典型用途

- **登录态**：Token 与用户 ID 映射（键形如 `auth:token:{token}`），支持 TTL。
- **缓存**：例如工作台汇总、收入趋势、商品销量 Top10 等（具体键与失效策略以后端实现为准）。

Redis 不可用时，部分逻辑可能降级（以代码与日志为准）。

---

## 构建产物

```bash
cd backend
mvn -DskipTests package
```

生成的 JAR 位于 `backend/target/`。

---

## 常见问题

| 现象 | 建议 |
|------|------|
| 前端调不通 `/api` | 确认后端已启动；若用 Nginx，检查 `proxy_pass` 与 `location` 是否包含 `/api`。 |
| 401 未登录 | 检查请求头是否带 `X-Token`，或重新登录。 |
| 报表/排行无数据 | 确认已执行 `views.sql`，且数据库内有订单等业务数据。 |
| 注册后无法跳转 | 使用 HTTP 访问 Nginx 根路径下的页面，避免 `file://` 打开 HTML。 |

---

## 许可证与声明

本项目多用于课程设计或学习演示。**请勿将含真实商户数据的配置推送到公开仓库。**

如有课程要求的文档（ER 图、接口说明、测试用例等），请与课程材料一并提交。
