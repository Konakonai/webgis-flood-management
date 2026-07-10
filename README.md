# WebGIS 内涝监测与应急管理系统

面向徐州市城市内涝场景的课程项目。仓库包含现有 Vue 3 地图前端、完整的 Spring Boot/PostGIS 后端、OSRM 路由服务与 Compose 部署配置。

## 当前完成范围

- JWT 登录、管理员建号与 ADMIN/OPERATOR/VIEWER 权限控制；
- 监测数据、阈值预警、STOMP/WebSocket 实时推送；
- PostGIS 空间查询和 GeoJSON 图层导入；
- 应急资源、公众上报、图片、追踪码和工单状态流转；
- Flyway V1—V3 数据迁移、OpenAPI、健康检查和操作审计；
- PostGIS、后端、OSRM、Nginx 前端四服务 Compose 编排。

前端已完成真实后端联调：生产环境禁用 Mock，调度台使用 JWT、数据库工单、OSRM 路线和 STOMP 实时刷新；移动端上报使用服务端追踪码、受控图片上传和跨设备进度查询。历史问题及处理结果见 [前端修复记录](docs/前端修改建议.md)。

## 一键部署

需要 Docker Compose v2 或 Podman Compose。从仓库根目录运行：

```bash
cp .env.example .env
./deploy/up.sh
```

默认入口：

- 前端：<http://127.0.0.1:8088>
- 后端：<http://127.0.0.1:8080>
- Swagger UI：<http://127.0.0.1:8080/swagger-ui.html>
- OSRM：<http://127.0.0.1:5000>
- PostGIS：`127.0.0.1:5432`

OSRM 首次启动会下载并预处理江苏 OSM 数据，可能需要较长时间。完整配置、数据卷和故障排查见 [部署说明](deploy/README.md)。

## 本地开发

后端要求 Java 17；数据库要求 PostgreSQL 15+ 与 PostGIS。Flyway 会在后端启动时自动执行迁移，不要手工重复导入 SQL。

```bash
# 后端
cd backend
./mvnw spring-boot:run

# 前端（另一个终端）
cd frontend
npm ci
npm run dev
```

默认演示账号密码均为 `admin123`：

| 账号 | 角色 |
|---|---|
| `admin` | 管理员 |
| `operator` | 业务操作员 |
| `viewer` | 只读用户 |

演示密码和 `.env.example` 中的密钥仅适用于本机课程演示，公开部署前必须全部替换。

## 验证

```bash
cd backend && ./mvnw clean verify
cd frontend && npm run build
```

当前后端有 50 项自动化测试。完整四服务冷启动、OSRM 江苏数据预处理、同源代理和浏览器业务闭环均已实测；证据见 [初步开发报告](docs/初步开发报告.md)。接口可通过运行时 OpenAPI 查看，精确契约另见 `docs/API_CONTRACT.md`。
