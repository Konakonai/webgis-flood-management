# API 接口契约

> 项目：WebGIS 内涝监测与应急管理系统
> 文档版本：v2.0
> 更新日期：2026-07-10
> 契约来源：当前 `backend/src/main/java/com/floodgis` 实现

## 一、统一约定

### 1.1 地址与数据格式

- 后端开发地址：`http://localhost:8080`；
- Compose 同源入口：`http://localhost:8088`；
- REST 前缀：`/api`；
- OpenAPI：`GET /v3/api-docs`；
- Swagger UI：`GET /swagger-ui.html`；
- JSON 字段采用 camelCase；
- 时间采用 ISO-8601 本地时间字符串，服务端时区为 Asia/Shanghai；
- 经纬度顺序为 `lng, lat`，空间数据采用 WGS84（EPSG:4326）。

### 1.2 标准响应

除下文明确标注的兼容接口外，REST 接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

常见业务码与 HTTP 状态：

| code / HTTP | 含义 |
|---:|---|
| 200 | 成功 |
| 400 | 参数、GeoJSON 或状态请求不合法 |
| 401 | 未认证、Token 无效/过期、用户已禁用 |
| 403 | 角色权限不足 |
| 404 | 记录不存在 |
| 409 | 状态冲突、资源占用或数据库完整性冲突 |
| 413 | multipart 请求超过 Spring 上传限制 |
| 415 | 请求 Content-Type 不受支持 |
| 429 | 登录、空间查询、匿名上报或图片追加超过 Nginx 单 IP 频率限制 |
| 500 | 未处理的服务端异常 |

客户端应同时检查 HTTP 状态和响应体 `code`；错误响应使用与 `code` 一致的 HTTP 状态。

### 1.3 不使用标准包装的兼容接口

以下接口为适配现有前端，成功时直接返回原始数组或 GeoJSON：

| 接口 | 成功响应 |
|---|---|
| `GET /api/resources`，无查询参数 | `ResourcePointView[]` |
| `GET /api/stations`，无查询参数 | `PumpStationView[]` |
| `POST /api/spatial-query` | GeoJSON `FeatureCollection` |
| `GET /api/reports/nearby` | GeoJSON `FeatureCollection` |

`POST /api/spatial-query` 的参数错误也直接返回 `{"error":"..."}`。

### 1.4 认证

HTTP 请求头：

```http
Authorization: Bearer <jwt>
```

默认 Token 有效期为 24 小时。每次受保护请求都会重新读取用户启用状态和角色，因此禁用用户或调整角色会立即影响旧 Token。

权限缩写：

- **公开**：无需 Token；
- **VIEWER+**：`ROLE_VIEWER`、`ROLE_OPERATOR`、`ROLE_ADMIN`；
- **OPERATOR+**：`ROLE_OPERATOR`、`ROLE_ADMIN`；
- **ADMIN**：仅 `ROLE_ADMIN`。

### 1.5 枚举

| 领域 | 可用值 |
|---|---|
| 站点类型 | `RAIN_GAUGE`、`WATER_GAUGE`、`FLOW_METER`、`PUMP_STATION` |
| 站点状态 | `ACTIVE`、`INACTIVE`、`MAINTENANCE` |
| 资源类型 | `PUMP_TRUCK`、`SANDBAG`、`TEAM`、`WAREHOUSE`、`SHELTER` |
| 资源状态 | `AVAILABLE`、`DISPATCHED`、`DEPLETED` |
| 监测类型 | `WATER_LEVEL`、`RAINFALL`、`FLOW` |
| 工单类型 | `REPORT`、`WARNING`、`DISPATCH` |
| 工单状态 | `PENDING`、`PROCESSING`、`COMPLETED`、`REJECTED` |
| 工单优先级 | `LOW`、`NORMAL`、`HIGH`、`URGENT` |
| 预警状态 | `PENDING`、`CONFIRMED`、`PUBLISHED`、`REVOKED`、`REJECTED` |

## 二、认证接口

### 2.1 登录

`POST /api/auth/login` — **公开**

请求：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

用户名长度 2—50，密码长度 6—100。成功响应的 `data`：

```json
{
  "token": "<jwt>",
  "userId": 1,
  "username": "admin",
  "realName": "系统管理员",
  "roles": ["ROLE_ADMIN"]
}
```

### 2.2 注册

`POST /api/auth/register` — **ADMIN**

```json
{
  "username": "new-user",
  "password": "password123",
  "realName": "新用户",
  "email": "user@example.com",
  "phone": "13800000000"
}
```

仅管理员可创建账号。新用户默认启用并分配 `ROLE_VIEWER`。用户名重复返回 409。返回用户对象时密码字段被清空。

### 2.3 当前用户

`GET /api/auth/me` — **VIEWER+**

返回当前数据库用户，密码字段为空。已禁用或已删除用户的旧 Token 不再建立认证。

## 三、现有前端兼容接口

### 3.1 地图资源数组

`GET /api/resources` — **公开**，必须不带 `page/size/name/resourceType/status`

返回非泵车资源的原始数组：

```json
[
  {
    "id": "R001",
    "name": "鼓楼区救援队",
    "type": "resource",
    "lng": 117.1855,
    "lat": 34.2815,
    "address": "鼓楼区应急救援中心",
    "status": "紧张",
    "details": "20人"
  }
]
```

`id` 是展示兼容 ID；后台写操作使用数据库数值 ID。

### 3.2 泵车驻地数组

`GET /api/stations` — **公开**，必须不带 `page/size/name/stationType/status`

该兼容接口返回 `emergency_resource` 中的 `PUMP_TRUCK`，并非 `monitor_station`：

```json
[
  {
    "id": "P002",
    "name": "云龙区泵车站",
    "type": "pump",
    "lng": 117.2266,
    "lat": 34.2522,
    "address": "云龙区排水泵站",
    "vehicle": "云龙区泵车站 (5台)",
    "contact": "李站长 (13800002222)",
    "status": "空闲"
  }
]
```

派单接口的 `resourceId` 需要数据库数值 ID。业务前端应从分页资源接口取得数值 ID，不能直接提交 `P002` 之类展示 ID。

### 3.3 空间联合查询

`POST /api/spatial-query` — **公开**

请求字段：

| 字段 | 必填 | 说明 |
|---|---|---|
| `bufferGeoJSON` | 否 | Polygon 或 MultiPolygon Geometry；二维 WGS84，最多 100000 个坐标位置 |
| `name` | 否 | 名称包含匹配，trim 后最多 100 字符 |
| `type` | 否 | `all`、`waterlogging`、`pump`；默认 `all` |

示例：

```json
{
  "bufferGeoJSON": {
    "type": "Polygon",
    "coordinates": [[[117.10,34.20],[117.30,34.20],[117.30,34.30],[117.10,34.30],[117.10,34.20]]]
  },
  "name": "",
  "type": "waterlogging"
}
```

成功响应：

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {
        "id": "F001",
        "name": "彭城广场地下通道积水点",
        "type": "waterlogging",
        "typeName": "道路积水点",
        "status": "critical",
        "statusName": "积水严重",
        "manager": "鼓楼区排水处",
        "phone": "13888889901",
        "address": "徐州市鼓楼区中山北路与淮海路交叉口地下通道",
        "waterDepth": "0.25m",
        "capacity": null
      },
      "geometry": {
        "type": "Point",
        "coordinates": [117.186, 34.263]
      }
    }
  ]
}
```

数据库使用 `ST_Intersects`，所有外部条件通过 MyBatis 参数绑定。

## 四、监测站点与应急资源

### 4.1 监测站点

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `GET /api/stations?page=1&size=10&name=&stationType=&status=` | 公开 | 分页查询真正的监测站点；`page` 必须出现，1—100 条/页 |
| `GET /api/stations/{id}` | VIEWER+ | 站点详情 |
| `POST /api/stations` | OPERATOR+ | 新增站点 |
| `PUT /api/stations/{id}` | OPERATOR+ | 全量更新站点 |
| `DELETE /api/stations/{id}` | OPERATOR+ | 删除站点 |

写入请求：

```json
{
  "name": "新水位站",
  "stationType": "WATER_GAUGE",
  "lat": 34.26,
  "lng": 117.20,
  "address": "详细地址",
  "area": "云龙区",
  "status": "ACTIVE",
  "installDate": "2026-07-10",
  "description": "自动水位站"
}
```

`lat` 与 `lng` 必填且分别限制在 [-90,90]、[-180,180]。

### 4.2 应急资源

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `GET /api/resources?page=1&size=10&name=&resourceType=&status=` | 公开 | 分页资源；`page` 必须出现，返回数据库数值 ID |
| `GET /api/resources/{id}` | VIEWER+ | 资源详情 |
| `POST /api/resources` | OPERATOR+ | 新增资源 |
| `PUT /api/resources/{id}` | OPERATOR+ | 全量更新资源 |
| `DELETE /api/resources/{id}` | OPERATOR+ | 删除资源 |

写入请求：

```json
{
  "name": "移动泵车一组",
  "resourceType": "PUMP_TRUCK",
  "lat": 34.25,
  "lng": 117.22,
  "address": "云龙区",
  "area": "云龙区",
  "quantity": 2,
  "unit": "台",
  "contactPerson": "值班员",
  "contactPhone": "13800000000",
  "status": "AVAILABLE",
  "description": "移动泵车"
}
```

数量不得为负数。
`DISPATCHED` 只能由工单派发产生，并由工单完成/拒绝释放；普通资源 CRUD
不能直接进入或退出该状态，正在执行工单的资源也不能删除。

## 五、监测数据与风险预警

### 5.1 监测数据

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `GET /api/monitor/latest` | VIEWER+ | 每个站点最新监测值 |
| `GET /api/monitor/history?stationId=1&dataType=WATER_LEVEL&hours=24` | VIEWER+ | 指定站点历史；`hours` 1—720 |
| `GET /api/monitor/summary?hours=24` | VIEWER+ | 分类型/站点汇总；`hours` 1—720 |

定时模拟默认每 5 秒生成数据，可通过 `MOCK_DATA_ENABLED` 和 `MOCK_DATA_INTERVAL_MS` 配置。

### 5.2 预警接口

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `GET /api/warnings?page=1&size=10&status=&level=&keyword=` | VIEWER+ | 分页查询，size 1—100 |
| `GET /api/warnings/{id}` | VIEWER+ | 预警详情 |
| `GET /api/warnings/stats` | VIEWER+ | 按状态统计 |
| `GET /api/warnings/timeline?start=<ISO>&end=<ISO>&status=` | VIEWER+ | 时间回溯；start < end，范围不超过 366 天 |
| `PUT /api/warnings/{id}/confirm` | OPERATOR+ | `PENDING → CONFIRMED`，body 可省略 |
| `PUT /api/warnings/{id}/reject` | OPERATOR+ | `PENDING → REJECTED`，body 可省略 |
| `PUT /api/warnings/{id}/publish` | OPERATOR+ | `CONFIRMED → PUBLISHED` |
| `PUT /api/warnings/{id}/revoke` | OPERATOR+ | `PUBLISHED → REVOKED`，body 可省略 |

发布请求只使用以下可选字段：

```json
{
  "title": "暴雨内涝橙色预警",
  "content": "预计未来两小时强降雨持续。",
  "measures": "低洼路段临时交通管制。",
  "affectedArea": "{\"type\":\"Polygon\",\"coordinates\":[...]}"
}
```

`affectedArea` 必须是 Polygon/MultiPolygon Geometry JSON 字符串，最大 1 MB。

## 六、公众上报

### 6.1 创建上报

`POST /api/reports` — **公开**

```json
{
  "lng": 117.192,
  "lat": 34.271,
  "depth": 45,
  "description": "桥洞积水，车辆无法通行",
  "reporterName": "张先生",
  "reporterPhone": "13588889999",
  "image": "data:image/jpeg;base64,..."
}
```

约束：

- `lng` [-180,180]，`lat` [-90,90]；
- `depth` 0—1000，单位 cm；
- 描述最多 2000 字符；
- `image` 可省略，只接受 PNG/JPEG Data URL；字符串最多 7000000 字符，解码后最大 5 MB，且宽 × 高不得超过 2500 万像素。

成功响应 `data`：

```json
{
  "id": "FR-A1B2C3D4E5F6",
  "trackingCode": "FR-A1B2C3D4E5F6",
  "status": "PENDING",
  "createdAt": "2026-07-10T12:00:00"
}
```

### 6.2 进度追踪

`GET /api/reports/track/{trackingCode}` — **公开**

返回标题、描述、坐标、水深、状态、结果、时间、完整状态历史和图片 URL。追踪码只允许 6—32 位大写字母、数字和连字符；控制器会先转大写。

### 6.3 补充图片

`POST /api/reports/{trackingCode}/images` — **公开**
`Content-Type: multipart/form-data`，字段名 `file`。

只接受 PNG/JPEG，最大 5 MB，宽 × 高不得超过 2500 万像素；服务端通过 ImageReader 读取格式和尺寸元数据，并校验声明的 Content-Type 与实际格式一致。只有 `REPORT` 类型且处于 `PENDING/PROCESSING` 的工单可匿名追加，每个上报总计最多 5 张（包含创建时附带的图片）。服务端对追踪码对应的工单加行锁后再计数，避免并发绕过上限。成功 `data`：

```json
{
  "id": 1,
  "url": "/uploads/2026/07/<random>.jpg"
}
```

`GET /uploads/**` 公开读取已保存文件。

### 6.4 附近未处置上报

`GET /api/reports/nearby?lng=117.19&lat=34.27&radiusMeters=1000` — **公开**

- `radiusMeters` 默认 1000，范围 50—10000；
- 只返回 `REPORT` 且状态为 `PENDING/PROCESSING` 的记录；
- PostGIS 使用 geography 距离，最多 100 条；
- 成功直接返回 `FeatureCollection`。

## 七、工单与调度

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `GET /api/work-orders?page=1&size=10&type=&status=&keyword=` | OPERATOR+ | 分页查询，size 1—100 |
| `GET /api/work-orders/{id}` | OPERATOR+ | 工单详情 |
| `POST /api/work-orders` | OPERATOR+ | 创建内部工单 |
| `PUT /api/work-orders/{id}` | OPERATOR+ | 更新标题、描述、优先级、结果 |
| `POST /api/work-orders/{id}/dispatch` | OPERATOR+ | 派发泵车或救援队 |
| `PATCH /api/work-orders/{id}/status` | OPERATOR+ | 推荐的状态迁移接口 |
| `PUT /api/work-orders/{id}/status?status=...` | OPERATOR+ | 保留的旧兼容接口 |
| `DELETE /api/work-orders/{id}` | OPERATOR+ | 行锁后仅删除 `PENDING/REJECTED`；同时清理附件文件 |

### 7.1 创建

```json
{
  "type": "DISPATCH",
  "title": "云龙区桥洞排涝",
  "description": "安排泵车处置",
  "lat": 34.271,
  "lng": 117.192,
  "priority": "URGENT",
  "reporterName": "调度中心",
  "reporterPhone": "0516-00000000"
}
```

`title` 必填且最多 200 字符，描述最多 4000 字符。

### 7.2 派单

```json
{
  "resourceId": 2,
  "handlerId": 3,
  "handlerName": "李队长",
  "routeDistance": 8500,
  "routeDuration": 1200
}
```

- `resourceId` 为数据库数值 ID，必填且大于 0；
- 工单必须是 `PENDING`；
- 资源必须是 `AVAILABLE`，类型必须为 `PUMP_TRUCK` 或 `TEAM`；
- 成功后工单进入 `PROCESSING`，资源进入 `DISPATCHED`；
- 并发或重复派发返回 409。

### 7.3 状态迁移

推荐接口：

`PATCH /api/work-orders/{id}/status`

```json
{
  "status": "COMPLETED",
  "result": "积水已排除，道路恢复通行",
  "note": "现场复核完成"
}
```

允许迁移：

```text
PENDING -> PROCESSING | REJECTED
PROCESSING -> COMPLETED | REJECTED
COMPLETED -> 无
REJECTED -> 无
```

进入 `COMPLETED` 或 `REJECTED` 时释放已分配资源。每次迁移写入状态历史。
数据库部分唯一索引保证同一资源最多关联一个 `PROCESSING` 工单。

旧兼容接口：

`PUT /api/work-orders/{id}/status?status=COMPLETED`

可选 body 为工单对象；只读取 `result` 和 `description`，后者作为历史备注。新客户端应使用 PATCH。

## 八、空间图层

| 方法与路径 | 权限 | 说明 |
|---|---|---|
| `POST /api/layers/upload`，multipart | OPERATOR+ | `file`、`name` 必填，`description` 可选 |
| `POST /api/layers/upload?name=...&description=...`，application/json | OPERATOR+ | body 为 FeatureCollection |
| `GET /api/layers/{id}/features` | VIEWER+ | 返回标准包装，`data` 为 FeatureCollection |

当前限制：

- GeoJSON 内容最大 5 MB；
- 单图层最多 10000 个 Feature；
- geometry 支持 Point/MultiPoint、LineString/MultiLineString、Polygon/MultiPolygon、GeometryCollection；
- 每个坐标必须是二维 WGS84，单几何最多 100000 个坐标位置；
- Polygon 环必须闭合；
- properties 必须为对象或 `null`；
- PostGIS `ST_IsValid` 必须通过；
- 写入在单个事务中完成。

当前接口直接接收 GeoJSON，不解析 Shapefile。若前端支持 Shapefile，应先在浏览器转换为 FeatureCollection。

## 九、系统管理

### 9.1 用户

所有 `/api/users/**` 均为 **ADMIN**。

| 方法与路径 | 说明 |
|---|---|
| `GET /api/users?page=1&size=10&keyword=` | 分页查询，size 1—100；密码字段清空 |
| `GET /api/users/{id}` | 用户详情 |
| `PUT /api/users/{id}` | 更新姓名、邮箱、电话、可选密码 |
| `PUT /api/users/{id}/status?enabled=false` | 启用/禁用 |

更新密码长度 8—72。系统拒绝禁用当前登录账号，也拒绝禁用最后一个可用管理员。

### 9.2 配置

所有 `/api/config/**` 均为 **ADMIN**。

| 方法与路径 | 说明 |
|---|---|
| `GET /api/config/{key}` | 返回 `{"key":"...","value":"..."}` |
| `PUT /api/config/{key}` | body 为 `{"value":"..."}` |

`*_warning` 与 `*_danger` 的值必须是合法 JSON，并包含非负数值字段 `value`。

### 9.3 操作日志

所有 `/api/logs/**` 均为 **ADMIN**。

| 方法与路径 | 说明 |
|---|---|
| `GET /api/logs?page=1&size=20&username=&action=&startTime=&endTime=` | 分页筛选 |
| `GET /api/logs/{id}` | 日志详情 |

带 `@LogOperation` 的新增、修改、删除、派单和预警操作由 AOP 记录动作、模块、URI、方法、IP、耗时、成功/失败和错误信息。

## 十、WebSocket / STOMP

### 10.1 连接

- SockJS 端点：`/ws`；
- 原生 WebSocket 地址：`/ws/websocket`；
- STOMP 版本：服务端已验证 1.2；
- Broker 订阅前缀：`/topic`、`/queue`；
- 客户端发送前缀：`/app`。

STOMP `CONNECT` 原生头必须包含：

```text
Authorization:Bearer <jwt>
accept-version:1.2
host:localhost
```

缺少、无效、过期 Token，或数据库用户已禁用时拒绝连接。HTTP 握手路径公开仅用于协议升级，业务连接仍必须通过 STOMP 认证。

### 10.2 主题

| 订阅地址 | 触发时机 | 主要字段 |
|---|---|---|
| `/topic/monitor` | 默认每 5 秒生成并保存监测数据后 | `stationId`、`stationName`、`type`、`value`、`unit`、`warningLevel`、`timestamp`、`lat`、`lng` |
| `/topic/warning` | 自动创建、确认、驳回、发布、撤销后 | `WarningRecord` |
| `/topic/work-orders` | 创建、编辑、派单、状态迁移后 | `WorkOrder` |

预警与工单消息在数据库事务提交后发送。`/topic/work-orders` 仅允许 ADMIN/OPERATOR 订阅，VIEWER 订阅会被拒绝。系统当前仅做服务端推送，所有客户端 STOMP `SEND` 都会被拒绝。

## 十一、部署代理契约

Nginx 生产入口约定：

| 浏览器路径 | 代理目标 |
|---|---|
| `/api/**` | `backend:8080`，保留路径；登录、空间查询、匿名上报和图片追加按客户端 IP 分别限流 |
| `/ws/**` | `backend:8080`，保留 Upgrade/Connection |
| `/uploads/**` | `backend:8080` |
| `/osrm/**` | `osrm:5000`，移除 `/osrm/` 前缀 |

OSRM 示例：

```http
GET /osrm/route/v1/driving/117.1855,34.2815;117.2266,34.2522?overview=full&geometries=geojson
```

OSRM 使用标准响应，不使用 `Result<T>` 包装。

## 十二、兼容与集成说明

1. 无参数 `GET /api/resources` 和 `GET /api/stations` 保留现有前端所需原始数组；带 `page` 时返回后台分页对象。
2. `POST /api/spatial-query` 保留原始 FeatureCollection，前端直接读取 `features`。
3. `PUT /api/work-orders/{id}/status` 保留旧查询参数形式；新代码使用 PATCH JSON。
4. `GET /api/reports/nearby` 保留原始 FeatureCollection，便于直接作为地图 Source。
5. 兼容展示 ID 与数据库操作 ID 不同；派单等写操作必须使用数值 ID。
6. 当前 `frontend/src/main.ts` 无条件加载 Mock，`frontend/src/store/emergency.ts` 和 `MobileReportApp.vue` 仍使用本地状态。后端契约已就绪，但真实页面联调需要按 `docs/前端修改建议.md` 调整前端。
