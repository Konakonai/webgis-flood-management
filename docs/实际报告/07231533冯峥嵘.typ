#import "./typst-template/scripst/main.typ": scripst

#let serif = "SimSun"
#let sans = "Noto Sans CJK SC"
#let image-dir = "./images"
#let typst-image-dir = "./typst-assets"

#show: scripst.with(
  template: "article",
  title: "",
  info: "",
  author: (),
  time: "",
  font-size: 10.5pt,
  contents: false,
  counter-depth: 2,
  header: false,
  lang: "zh",
  par-indent: 2em,
  par-leading: 0.5em,
  par-spacing: 0.62em,
)

#set page(
  paper: "a4",
  margin: (top: 25.4mm, bottom: 25.4mm, left: 31.8mm, right: 31.8mm),
  footer: context align(center)[
    #set text(font: "Times New Roman", size: 9pt)
    #counter(page).display("1")
  ],
)
#set text(font: ("Times New Roman", serif), size: 10.5pt, lang: "zh")
#set par(justify: true, first-line-indent: (amount: 2em, all: true))
#set heading(numbering: none)
#set figure(numbering: none)
#show strong: it => text(font: sans, weight: 400)[#it.body]
#show heading.where(level: 1): set text(font: sans, size: 14pt, weight: 400)
#show heading.where(level: 2): set text(font: sans, size: 12pt, weight: 400)
#show figure.caption: it => {
  set text(font: ("Times New Roman", serif), size: 9pt)
  it
}
#show raw.where(block: true): set text(font: "JetBrains Maple Mono", size: 8pt)
#show raw.where(block: false): set text(font: "Times New Roman")

#let cover-field(label, value) = grid(
  columns: (4.2em, 14em),
  column-gutter: 0.5em,
  align: (right, center),
  [#text(font: sans, weight: 400)[#label]],
  block(
    width: 14em,
    inset: (bottom: 2pt),
    stroke: (bottom: 0.6pt + black),
  )[#align(center)[#value]],
)

#set par(first-line-indent: 0pt)
#grid(
  columns: (100%,),
  rows: (55mm, 125.2mm, 66mm),
  align: center + horizon,
  [
    #text(font: sans, size: 26pt, weight: 400)[《地理信息系统开发实习》报告]
    #v(6mm)
    #text(font: sans, size: 16pt, weight: 400)[地信2023级]
  ],
  [
    #text(size: 14pt)[
      #cover-field[专业：][地理信息科学]
      #v(2.5mm)
      #cover-field[班级：][地理信息科学2023-1班]
      #v(2.5mm)
      #cover-field[姓名：][冯峥嵘]
      #v(2.5mm)
      #cover-field[学号：][07231533]
      #v(2.5mm)
      #cover-field[组长：][李兴俊]
    ]
  ],
  [
    #text(font: sans, size: 14pt, weight: 400)[中国矿业大学环境与测绘学院]
    #v(4mm)
    #text(font: "Times New Roman", size: 14pt)[2026.06]
  ],
)

#pagebreak()
#set par(
  justify: true,
  leading: 0.5em,
  first-line-indent: (amount: 2em, all: true),
)

= 1 实验过程

本轮地理信息系统设计与开发实习于2026年6月23日启动，7月11日完成核心业务闭环、四服务容器化部署与首轮验收；此后根据最终演示需要，于7月14日至15日补充后台管理页面并完成交付修正。项目组采用双人分工协作的模式，按前后端主线分工，并通过接口契约明确协作边界。

作为项目组的前端工程师，我主要负责地图基础底座的设计与封装、前端多模式空间绘制与 Turf.js 拓扑分析、本地 OSRM 路由请求与行驶路线图层渲染，以及移动端公众上报页面的自适应开发。在项目后期，因业务联调和系统演示的需要，我还承担了前端工程的整合与人机交互优化，并完成了基于 rootless Podman Compose 的四服务（Nginx 静态网关、Spring Boot 业务后台、OSRM 路由引擎、PostgreSQL/PostGIS 数据库）编排、启动脚本、健康检查与同源代理配置。

结合 Git 提交与联调记录，整个开发和演示收尾过程可划分为以下四个阶段：

- *需求梳理与前端底座阶段（6月23日—29日）：* 项目组选定徐州市城区内涝应急处置与防汛管理为业务核心，梳理地图工作台、空间查询、应急调度和公众上报等业务模块，并明确前后端分工与接口边界。
- *WebGIS 与移动上报开发阶段（6月30日—7月9日）：* 采用 Vue 3 和 MapLibre GL JS 完善地图工作台，通过 Pinia 和 `useMap` 共享地图状态，结合 Turf.js 实现缓冲区计算与空间查询联动，同时完成移动端定位上报、图片压缩和响应式界面。
- *后端联调与四服务验收阶段（7月10日—11日）：* 对接 Spring Boot/PostGIS 业务接口、OSRM 江苏道路路由与 STOMP 实时刷新，完成 `deploy/up.sh` 与 `compose.yaml` 配置，修复 Vite 生产构建下 SockJS `global` 缺失问题，并完成核心业务闭环验收。
- *演示收尾与交付修正阶段（7月14日—15日）：* 根据最终演示需要补充后台管理中心，修正 OSRM 启动脚本与退出登录后的工作区状态，并同步更新交付文档。

= 2 个人工作介绍

在此次《地理信息系统开发实习》中，我作为核心开发人员之一，积极发挥在前端 GIS 算法与系统部署配置方面的优势，主要承担并完成了以下工作职责：

- *WebGIS 核心底座封装：* 封装组合式函数 `useMap()`，统一控制 MapLibre 地图实例的生命周期、动态图层注册（`registerLayer`）、定位飞行及 Marker 管理，向其他业务组件和移动上报组件提供共享地图服务。
- *空间绘制与 Turf 计算：* 实现矩形和多边形的前端实时空间绘制交互，利用 Turf.js 按照用户输入距离在浏览器生成缓冲区几何多边形并动态绘制渲染，将分析几何发送至后端执行空间相交联合检索。
- *OSRM 路由及流光箭头渲染：* 本地化部署 OSRM 路由服务，发起异步路由请求并解析返回的折线数据，利用 MapLibre 动态图层在道路网络上渲染流光箭头行驶路径动画。
- *独立移动端公众上报页：* 设计适用于手机窄屏的 `MobileReportApp.vue` 页面，内置一键 H5 地理定位和地图中心选点交互，实现 Canvas 图片等比压缩，降低客户端上传体积，并配合后端完成受控存储。
- *Podman 与网关配置：* 使用 Compose 编排 PostgreSQL/PostGIS、OSRM、Spring Boot 和 Nginx 四个容器，配置 rootless 运行、健康检查及本机绑定，通过 Nginx 解决页面与 REST/WebSocket 通信的跨域问题。

= 3 具体工作

本系统名为“WebGIS内涝监测与应急管理系统”，主要面向徐州市城区内涝的监测预警、事件上报、资源调度及处置跟踪业务。以下是我承担的具体功能设计、编码实现与部署验收细节。

== 3.1 地图基础底座与 useMap 封装

为了防止多组件并发操作地图导致实例混乱，我将 MapLibre GL JS 的底层细节进行隔离，使用 Vue 3 组合式 API 封装 `useMap()` 函数。该 Composable 集中管理地图生命周期、业务 Marker、图层注册和底图切换。

在底图选用上，项目起初尝试接入天地图 WMTS 服务，但由于天地图 API 对演示环境域名及请求密钥有严格限制，时常导致日间底图加载失败，进而阻断依托地图加载完成事件（`map.on("load")`）的 Turf 绘制等交互。为了提高演示稳定性，我对底图进行重构，改用不依赖项目密钥的 osm-light 瓦片作为日间模式底图，以 carto-dark 瓦片作为夜间模式底图，使用 `useTheme()` 配合全局 CSS 变量，实现主题切换时地图底图与系统的同步变色联动。同时，对弹窗属性字段统一进行 HTML 转义，降低动态字段中脚本注入的风险。

#figure(
  image(typst-image-dir + "/S1_桌面调度工作台.png", width: 92%),
  caption: [图 3-1 桌面端内涝应急调度工作台],
)

== 3.2 空间绘制、Turf 缓冲区与联合检索

空间查询模块支持矩形和多边形绘制。在绘制模式下，地图模块独占右键结束事件，以防与普通状态下的右键灾情登记产生冲突。当绘制完成后，前端将绘制几何转换为 GeoJSON，并使用 Turf.js 按照用户设定距离即时生成缓冲区面数据，将缓冲多边形与绘制多边形分图层渲染，为用户提供直观的空间范围反馈。

用户确定分析范围后，前端向 `POST /api/spatial-query` 发送携带空间几何与过滤条件（设施名称、类型）的请求。后端解析入参后调用 PostGIS 的 `ST_Intersects` 算子完成联合过滤并返回 GeoJSON 要素集，前端解析要素集并联动高亮展示 Marker 及其列表。

#figure(
  image(typst-image-dir + "/S2_空间联合查询结果.png", width: 92%),
  caption: [图 3-2 空间联合查询结果与地图联动],
)

== 3.3 OSRM 调度路线计算与流光动画

应急调度模块需要展现道路路线规划。当调度员在列表中选择一个待派单的受灾点（工单）时，系统读取该工单的 WGS84 坐标，并向本地部署的 OSRM 发起路由请求。前端先从空闲泵车中筛选候选资源，再请求 OSRM 结合江苏 OSM 道路网计算实际行驶路线，返回道路折线几何、预计时间和行驶距离。

前端直接读取 OSRM 以 GeoJSON 返回的路线坐标并加载至地图。为了提升人机交互质感，我利用 MapLibre 的 Layer 机制和按帧更新的点要素，在道路折线上渲染顺着行驶方向移动的流光粒子，形成直观的路线动画。派单后工单由 PENDING 转为 PROCESSING；抵达现场时写入 `arrivedAt`，状态仍保持 PROCESSING；完成后转为 COMPLETED 并释放资源。后端通过事务与行锁维护并发一致性，并在事务提交后广播刷新。

== 3.4 独立移动端公众上报页

为了方便公众在发生积水时快速上报事件，我开发了 `MobileReportApp.vue` 移动端自适应页面。页面使用 HTML5 Geolocation 获取高精度 GPS 定位，也支持以微缩地图中心取点。表单除了上报积水深度和描述外，还支持调用摄像头拍照上传。

为了防止大尺寸照片占用过多上传带宽和服务器存储，我在浏览器端编写图片压缩逻辑：图片载入后，使用 Canvas 判断其长边；若超过1920像素则进行等比缩放，并以压缩后的 PNG 或 JPEG 文件通过 Multipart 方式上传，从而降低移动网络带宽和服务器存储压力。提交成功后仅保存追踪码，公众可跨设备查询处理状态。

#figure(
  image(image-dir + "/S3_移动端公众上报_裁剪.png", height: 74mm),
  caption: [图 3-3 移动端公众积水上报页面],
)

== 3.5 Podman 多服务容器化编排部署

在部署阶段，为了规避不同开发机环境的冲突，我采用 Podman Compose 设计四服务容器化架构。在 `compose.yaml` 中编排 postgis、backend、osrm 和 frontend（Nginx）四个服务，并设置命名卷实现数据持久化。

部署细节与安全控制如下：

1. *健康检查与依赖链：* 为 postgis、backend、osrm 和 frontend 服务配置 healthcheck，使用 `depends_on` 条件让 backend 在数据库健康后启动；OSRM 完成数据预处理并通过 healthcheck 后才视为健康。
2. *安全运行保护：* backend 和 OSRM 使用专用的非 root 用户运行，backend、OSRM 与 frontend 服务设置 `no-new-privileges`；所有宿主机端口默认绑定在回环地址（127.0.0.1）上，降低本机演示环境的暴露面。
3. *同源网关反向代理：* 前端容器使用 Nginx，将 `/api`、`/ws`、`/uploads` 和 `/osrm` 代理到后端及路由服务，消除浏览器跨域配置，并在 Nginx 中增加速率限制（`limit_req`）和安全响应头（CSP、X-Frame-Options 等）配置。

#figure(
  image(image-dir + "/P3_Compose部署拓扑图.png", width: 92%),
  caption: [图 3-4 Compose 四服务部署拓扑图],
)

#figure(
  image(image-dir + "/P5_应急派单流程图.png", width: 66%),
  caption: [图 3-5 应急派单与 OSRM 路由规划业务流程图],
)

== 关键代码 1：地图动态图层注册

```ts
const registerLayer = (id: string, opts: RegisterLayerOpts) => {
  const mapInst = mapStore.mapInstance
  if (!mapInst) return
  if (!mapInst.getSource(id)) mapInst.addSource(id, opts.source)
  opts.layers.forEach((layer) => {
    if (!mapInst.getLayer(layer.id)) {
      mapInst.addLayer({ ...layer, source: id })
    }
  })
}
```

== 关键代码 2：Turf 缓冲区计算与地图更新

```ts
const buffered = turf.buffer(drawnGeometry.value, bufferDistance.value, {
  units: "meters",
})
if (buffered && map.value) {
  bufferGeometry.value = buffered.geometry
  const source = map.value.getSource("spatial-query-buffer-source")
  if (source) source.setData(buffered)
}
```

== 关键代码 3：OSRM 行驶路线计算

```ts
const planRoute = async (start, end) => {
  const coordinates = start.join(",") + ";" + end.join(",")
  const url = "/osrm/route/v1/driving/" + coordinates
    + "?overview=full&geometries=geojson"
  const result = await rawRequest(url)
  const route = result.routes?.[0]
  if (!route) throw new Error("OSRM 暂时无法生成行车路线")
  return {
    geometry: route.geometry.coordinates,
    distance: route.distance,
  }
}
```

== 关键代码 4：右键结束绘制的事件所有权

```ts
const handleMapContextMenu = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== "polygon") return
  e.preventDefault()

  // MapLibre 会同步调用同一 contextmenu 事件的全部监听器。
  // 若立即恢复 idle，灾情登记监听器会误处理同一次右键。
  finishPolygonDrawing()
  mapStore.setInteractionMode("draw-query")
  queueMicrotask(() => {
    if (mapStore.interactionMode === "draw-query") {
      mapStore.setInteractionMode("idle")
    }
  })
}
```

#pagebreak()

== 关键代码 5：Turf 路线粒子动画

```ts
const startRouteAnimation = (coordinates: [number, number][]) => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
  const line = turf.lineString(coordinates)
  const routeLength = turf.length(line, { units: "kilometers" })
  let progress = 0

  const animate = () => {
    const mapInst = map.value
    if (!mapInst) return
    progress = (progress + routeLength / 100) % routeLength
    const points: any[] = []

    for (let i = 0; i < 5; i++) {
      const dist = progress - i * (routeLength / 18)
      if (dist < 0) continue
      const point = turf.along(line, dist, { units: "kilometers" })
      points.push({
        type: "Feature",
        properties: { opacity: 1 - i * 0.18, size: 6.5 - i },
        geometry: point.geometry,
      })
    }

    const source = mapInst.getSource("route-particles-source")
    if (source) source.setData({
      type: "FeatureCollection",
      features: points,
    })
    animationFrameId = requestAnimationFrame(animate)
  }

  animate()
}
```

== 关键代码 6：路线图层与动画清理

```ts
const removeRouteFromMap = () => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  const mapInst = map.value
  if (!mapInst) return

  const layers = ["route-particles-layer", "route-line-core", "route-line-bg"]
  layers.forEach(id => {
    if (mapInst.getLayer(id)) mapInst.removeLayer(id)
  })
  const sources = ["route-particles-source", "route-path-source"]
  sources.forEach(id => {
    if (mapInst.getSource(id)) mapInst.removeSource(id)
  })
}
```

#pagebreak()

== 关键代码 7：MapLibre 路线双层渲染

```ts
const drawRouteOnMap = (coordinates: [number, number][]) => {
  const mapInst = map.value
  if (!mapInst) return
  const sourceId = "route-path-source"
  const route = {
    type: "Feature",
    properties: {},
    geometry: { type: "LineString", coordinates },
  }

  const source = mapInst.getSource(sourceId)
  if (source) {
    source.setData(route)
    return
  }

  mapInst.addSource(sourceId, { type: "geojson", data: route })
  mapInst.addLayer({
    id: "route-line-bg",
    type: "line",
    source: sourceId,
    paint: {
      "line-color": "#10b981",
      "line-width": 7,
      "line-opacity": 0.35,
    },
    layout: { "line-join": "round", "line-cap": "round" },
  })
  mapInst.addLayer({
    id: "route-line-core",
    type: "line",
    source: sourceId,
    paint: {
      "line-color": "#34d399",
      "line-width": 3.5,
      "line-opacity": 0.95,
    },
    layout: { "line-join": "round", "line-cap": "round" },
  })
}
```

== 关键代码 8：移动端图片等比压缩

```ts
const compressImage = async (file: File): Promise<File> => {
  if (!["image/jpeg", "image/png"].includes(file.type)) {
    throw new Error("仅支持 PNG 或 JPEG 图片")
  }
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, 1920 / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement("canvas")
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  const context = canvas.getContext("2d")
  if (!context) throw new Error("浏览器无法处理该图片")
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()

  const type = file.type === "image/png" ? "image/png" : "image/jpeg"
  const blob = await new Promise<Blob | null>(resolve =>
    canvas.toBlob(resolve, type, 0.82)
  )
  if (!blob || blob.size > 5 * 1024 * 1024) {
    throw new Error("压缩后图片仍超过 5 MB")
  }
  return new File([blob], file.name, { type })
}
```

#pagebreak()

== 关键代码 9：公众上报与附件上传

```ts
const submitReport = async () => {
  if (!Number.isFinite(reportLng.value) || !Number.isFinite(reportLat.value)) {
    message.error("请先获取或拾取上报位置")
    return
  }

  isSubmitting.value = true
  try {
    const created = await apiRequest<{ trackingCode: string }>(
      "/api/reports",
      {
        method: "POST",
        body: JSON.stringify({
          lng: reportLng.value,
          lat: reportLat.value,
          depth: reportDepth.value,
          description: reportDescription.value.trim(),
        }),
      },
    )
    if (selectedImage.value) {
      const form = new FormData()
      form.append("file", selectedImage.value)
      await apiRequest(
        `/api/reports/${created.trackingCode}/images`,
        { method: "POST", body: form },
      )
    }
    trackingQuery.value = created.trackingCode
    message.success(`上报成功，追踪码：${created.trackingCode}`)
  } finally {
    isSubmitting.value = false
  }
}
```

== 关键代码 10：STOMP 工单实时刷新

```ts
const connectWorkOrderUpdates = () => {
  if (stompClient?.active) return
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (!token) return

  stompClient = new Client({
    webSocketFactory: () => new SockJS("/ws"),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      stompClient?.subscribe("/topic/work-orders", () => {
        void Promise.all([fetchStations(), fetchWorkOrders()])
      })
    },
  })
  stompClient.activate()
}
```

#pagebreak()

= 4 总结

本次《地理信息系统设计与开发实习》是对个人软件工程能力和 WebGIS 实践水平的一次综合锻炼。从6月23日核心开发启动到7月15日演示收尾的近三周中，我体验了从需求梳理、前后端接口设计，到代码编写、异常排查和容器化交付的课程项目开发过程，收获如下：

1. *掌握了主流 WebGIS 核心组件的应用。* 通过 `useMap` 封装和 MapLibre 实例的全局状态共享，我熟悉了 MapLibre 基于 WebGL 的地图渲染机制；通过 Turf.js 前端空间拓扑计算和本地 OSRM 路由引擎对接，我理解了保持 WGS84/EPSG:4326 全链路一致、避免坐标系混用造成位置偏移的重要性。
2. *深化了容器化与多服务部署的实践经验。* 通过在 NixOS 上配置和调试 rootless Podman Compose，我解决了用户命名空间（user namespace）重映射等底层权限报错；通过配置健康检查和 Nginx 同源反向代理，对多服务系统的安全边界、请求限流和同源访问控制有了切实体会。
3. *提升了软件排错和组件集成的意识。* 在联调和演示部署中，我解决了 Vite 生产构建中 SockJS 缺失 `global` 变量的问题，并重构天地图底图服务，提高了演示链路的稳定性。这表明软件开发不仅是功能编写，也包括环境适配、错误定位与边界控制。
