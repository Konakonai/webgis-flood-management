# 全栈容器部署

该编排同时启动 PostGIS、Spring Boot 后端、OSRM 和 Nginx 前端。数据库、上传图片与 OSRM 预处理结果均使用命名卷持久化，重启不会重新初始化。默认所有宿主机端口仅绑定 `127.0.0.1`。

## 启动

需要 Podman 4+ 与 Compose provider，或 Docker 24+ 与 Compose v2。从仓库根目录执行：

```bash
cp .env.example .env
./deploy/up.sh
```

也可直接使用原生命令：

```bash
podman compose up -d --build
# 或
docker compose up -d --build
```

启动后默认入口：

- 前端：<http://127.0.0.1:8088>
- 后端调试端口：<http://127.0.0.1:8080>
- OSRM 直连：<http://127.0.0.1:5000>
- PostGIS：`127.0.0.1:5432`

Nginx 已将 `/api/**`、`/ws/**` 和 `/uploads/**` 转发至后端，并将 `/osrm/**` 去掉前缀后转发至 OSRM。因此浏览器可以全程同源访问，无需修改现有前端的 `/api` 相对路径。

## OSRM 首次准备

默认使用 Geofabrik 的 [Jiangsu OSM PBF](https://download.geofabrik.de/asia/china/jiangsu.html)。首次启动会自动执行：

1. 下载 `jiangsu-latest.osm.pbf`；
2. 使用 `/opt/car.lua` 执行 `osrm-extract`；
3. 执行 MLD 的 `osrm-partition` 与 `osrm-customize`；
4. 启动 `osrm-routed`。

此过程可能需要数分钟，期间 OSRM 容器显示为 `starting`。后续启动会从 `osrm_data` 卷复用结果。查看进度：

```bash
podman compose logs -f osrm
# 或 docker compose logs -f osrm
```

使用自己裁剪的徐州 `.osm.pbf` 时，将其放在可通过 HTTP(S) 访问的位置，然后在 `.env` 中设置：

```dotenv
OSRM_DATA_URL=https://example.invalid/xuzhou.osm.pbf
OSRM_DATA_FILE=xuzhou.osm.pbf
OSRM_FORCE_REBUILD=true
```

首次成功后将 `OSRM_FORCE_REBUILD` 改回 `false`。可选 profile 为 `car`、`bicycle` 或 `foot`；更换 profile 会自动触发重建。对外路由 API 路径仍使用 OSRM 标准的 `/route/v1/driving/...`。

路由烟雾测试：

```bash
curl 'http://127.0.0.1:8088/osrm/route/v1/driving/117.1855,34.2815;117.2266,34.2522?overview=full&geometries=geojson'
```

## 数据与安全边界

- Spring Boot 启动时由 Flyway 执行 `db/migration` 中的版本化 SQL；新增迁移文件会自动套用到已有数据卷，不要改写已执行的迁移。
- 公众上报图片保存在 `uploads_data` 命名卷，并通过 `/uploads/**` 同源路径提供访问。
- 图片仅支持 PNG/JPEG，默认同时限制为 5 MB 和 2500 万像素；可通过 `UPLOAD_MAX_IMAGE_BYTES` 和 `UPLOAD_MAX_IMAGE_PIXELS` 调整。
- Nginx 对登录、空间查询、匿名 `POST /api/reports` 和图片追加分别按 IP 限流，匿名上报默认为平均 6 次/分钟、图片追加为 20 次/分钟，超限返回 HTTP 429。
- Nginx 会覆盖上游 `X-Forwarded-For` 为直连客户端地址；如果 Nginx 前还有可信负载均衡器，需先配置 Nginx real-IP 模块，不要直接改回信任任意请求头。
- `.env.example` 中的密码与 JWT 密钥仅用于本机演示。若将 `BIND_ADDRESS` 改为 `0.0.0.0`，必须同时替换它们、将实际浏览器入口写入 `CORS_ALLOWED_ORIGINS`，并通过防火墙/反向代理限制访问。
- 前端以 Nginx 静态文件运行；后端以 UID `10001`、OSRM 以 UID `10002` 的非 root 用户运行主进程。
- OSM 数据来自 OpenStreetMap contributors，对外展示或发布时需保留 [ODbL attribution](https://www.openstreetmap.org/copyright)。

## 运维命令

```bash
# 状态与健康检查
podman compose ps

# 查看全部日志
podman compose logs -f --tail=200

# 停止，保留数据
podman compose down

# 停止并删除数据（会永久删除业务数据和 OSRM 缓存）
podman compose down -v
```

Docker 用户将上述 `podman compose` 替换为 `docker compose`。

## 故障排查

- `postgis` 不健康：检查端口占用和 `.env` 中的数据库变量；查看 `postgis` 日志是否有初始化 SQL 错误。
- `backend` 不健康：先确认 `postgis` 已健康，再查看 Java 启动日志。
- OSRM 下载中断：直接重启服务，脚本会删除未完成的 `.part` 文件并重试。
- 数据库端口已被本机 PostgreSQL 占用：在 `.env` 中修改 `POSTGRES_PORT`，容器内部连接不受影响。
