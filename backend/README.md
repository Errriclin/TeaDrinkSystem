# 后端骨架（Spring Boot 2.7 + JDK 8 + MyBatis-Plus）

## IDEA 打开方式

1. **File → Open**，选择本目录 `backend`（含 `pom.xml` 的文件夹）。
2. 等待 Maven 依赖下载完成。
3. 修改 `src/main/resources/application.yml` 中的 **`spring.datasource.username` / `password`**，与本地 MySQL 一致。
4. 确认已按 `db/README.md` 创建库 **`teadrink_system`** 并执行过 SQL 脚本。
5. 运行 **`com.teadrink.TeadrinkApplication`** 主类。

## 已提供的接口示例

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/product?status=1` | 上架商品列表（三层架构示例，连真实表 `t_product`） |

浏览器或 Postman 访问：`http://127.0.0.1:8080/api/health`

## 包结构（三层）

```
com.teadrink
├── TeadrinkApplication
├── controller    ← 控制层
├── service       ← 业务接口
│   └── impl      ← 业务实现（后续事务加在此层）
├── mapper        ← MyBatis-Plus Mapper
├── entity        ← 表实体
├── dto           ← 前后端交互对象（待扩展）
├── config        ← MyBatis / Jackson / CORS 等
└── common        ← 统一返回 Result、全局异常
```

## 持久层约定（MyBatis）

- 本项目使用 **MyBatis-Plus**（在 **MyBatis** 之上）：`BaseMapper<T>`、`LambdaQueryWrapper`、分页插件等，减少样板代码。
- **简单 CRUD**：优先 Mapper + 注解/Wrapper，不写 XML。
- **复杂统计、多表关联、报表 SQL**：在 `src/main/resources/mapper/` 下增加 **XML**（如 `XxxMapper.xml`），在对应 `Mapper` 接口里声明方法，由 MyBatis 映射执行。
- 实体与表字段用 **`@TableName` / `@TableId`** 等与 `db/schema.sql` 保持一致；下划线列名由 **`map-underscore-to-camel-case: true`** 映射到驼峰属性。

## JSON 命名

已通过 `JacksonConfig` 使用 **snake_case**，与 `front-end/js/index.js` 中的 `sale_price` 等字段一致。

## 命令行编译（可选）

```bash
cd backend
mvn -DskipTests compile
```

打包运行：

```bash
mvn -DskipTests package
java -jar target/teadrink-backend-0.0.1-SNAPSHOT.jar
```
