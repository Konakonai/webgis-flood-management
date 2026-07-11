# Flood GIS 前端

Vue 3 + TypeScript + MapLibre GL JS 前端，包含桌面调度工作台和移动端公众积水上报页。

## 页面

- `/`：JWT 登录、空间查询、资源展示、工单登记、OSRM 派单、抵达与完成；
- `/report`：定位、积水深度、PNG/JPEG 压缩上传、追踪码查询。

## 本地开发

先启动后端与 OSRM，然后运行：

```bash
npm ci
npm run dev
```

Vite 会代理 `/api`、`/ws`、`/uploads` 和 `/osrm`。生产构建：

```bash
npm run build
```

前端始终连接真实后端接口；仓库不再包含会覆盖网络请求的 Mock 入口。地图基础图层保留一组面向徐州城区的模拟业务要素，便于离线交付和空间分析。

首次部署的管理员账号为 `admin / admin123`。交付或共享部署前应修改默认账号与后端密钥。
