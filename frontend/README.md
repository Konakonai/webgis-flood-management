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

开发时如确实需要资源与空间查询 Mock，可显式设置 `VITE_USE_MOCK=true`；生产构建不会加载 Mock。

默认课程演示账号为 `admin / admin123`。共享部署前应修改默认账号与后端密钥。
