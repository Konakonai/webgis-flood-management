# 同学A：前端GIS与Podman部署工作素材

## 一、职责范围

姓名：冯峥嵘
学号：07231533

按照任务分配，同学A负责地图基础浏览、空间查询检索、应急调度管理和通用辅助交互。本项目最终还由冯峥嵘承担全部前端整合、人机交互完善以及本机Podman容器化部署与验收。

## 二、地图底座与空间信息表达

### 1. 地图实例封装

- 方法：基于Vue 3组合式API封装`useMap`，集中维护MapLibre实例、底图样式、地图生命周期、业务图层、标记和交互事件。
- 效果：地图组件与空间查询、应急调度等业务组件共享同一地图状态，业务图层可按统一方式注册、更新和清理。
- 可引用证据：`frontend/src/composables/useMap.ts`、`frontend/src/components/MapContainer.vue`、`frontend/src/store/map.ts`。

### 2. 底图与业务图层

- 方法：使用MapLibre GL JS加载OSM/Carto瓦片，将监测点、积水点、泵车驻地、物资点、查询结果和路线组织为独立图层或Marker。
- 效果：用户可在同一地图工作台查看多类防汛对象，并通过显隐、定位和属性弹窗完成空间认知。
- 方法：对动态弹窗字段统一进行HTML转义。
- 效果：在展示数据库返回属性的同时控制脚本注入风险。

### 3. 主题与地图联动

- 方法：以CSS变量和`useTheme`集中管理日间、夜间主题，并让地图样式、面板、按钮、弹窗和状态颜色使用同一组主题语义。
- 效果：主题切换时地图与界面同步变化，登录状态、工具按钮和业务面板保持一致的视觉节奏。
- 可引用证据：`frontend/src/composables/useTheme.ts`、`frontend/src/store/theme.ts`、`frontend/src/style.css`。

## 三、空间绘制、缓冲与联合查询

### 1. 绘制交互

- 方法：实现矩形与多边形两种空间范围绘制模式；绘制期间由地图模块独占右键结束事件，普通状态下右键用于灾情登记。
- 效果：用户能够明确地开始、完成或取消空间绘制，空间分析与灾情登记不会争用同一个右键动作。

### 2. 缓冲区计算

- 方法：将绘制结果转换为GeoJSON，使用Turf.js按照用户输入距离生成缓冲区，并在地图上分别绘制查询范围和缓冲范围。
- 效果：空间分析范围能够即时反馈，用户可在提交查询前确认几何位置和覆盖区域。

### 3. 空间与属性联合检索

- 方法：将绘制几何、缓冲几何、设施类型和名称条件发送至`POST /api/spatial-query`；后端返回GeoJSON FeatureCollection后，前端同步生成结果列表和地图标记。
- 效果：用户可用“空间范围+属性条件”筛选积水点、泵站等设施，并从结果列表直接定位到地图对象。
- 可引用证据：`frontend/src/components/SpatialQueryPanel.vue`、`frontend/src/services/api.ts`、`backend/src/main/java/com/floodgis/controller/SpatialQueryController.java`。

## 四、应急调度与路线表达

### 1. 资源和工单工作区

- 方法：通过Pinia管理资源、工单、当前任务和调度状态，从后端加载数据库记录；抢险资源列表使用独立滚动容器。
- 效果：用户可持续浏览不同类型资源，不受面板高度限制，并在同一工作区完成待派单工单选择和状态跟踪。

### 2. 最近资源筛选与OSRM路线

- 方法：从状态为空闲的泵车中筛选候选资源，结合工单位置请求本地OSRM路由服务；将返回的GeoJSON路线、距离和预计时间绘制在地图上。
- 效果：调度员能够比较可用资源并查看符合道路网络的行驶路线，而不是使用简单直线代替实际路径。

### 3. 工单状态联动

- 方法：前端调用创建、派单、抵达和完成接口，并订阅`/topic/work-orders`主题接收STOMP消息。
- 效果：工单状态和资源占用状态由后端事务统一维护，多客户端可在业务变化后及时刷新。
- 可引用证据：`frontend/src/components/EmergencyDispatchPanel.vue`、`frontend/src/store/emergency.ts`、`frontend/src/services/api.ts`。

## 五、公众移动上报

- 方法：提供独立移动端页面，使用HTML5 Geolocation获取位置，也支持以地图中心选点；表单采集积水深度、描述与现场图片。
- 方法：图片在浏览器端限制类型与大小，并将长边压缩至1920像素以内；提交后仅保存服务端返回的追踪码。
- 效果：公众可在手机窄屏完成定位、上报和进度查询，跨设备也能凭追踪码获取服务器保存的状态与处理记录。
- 可引用证据：`frontend/src/components/MobileReportApp.vue`、`frontend/src/services/api.ts`。

## 六、人机交互与响应式布局

- 方法：桌面端采用地图中央工作区和左右业务面板；窄屏将空间查询和应急调度组织为可切换、可收起的底部抽屉，并为地图保留最小可视高度。
- 效果：在窄窗口中，用户可根据当前任务展开一个业务面板，收起后立即恢复地图视野，避免两个长面板同时占满屏幕。
- 方法：新手引导按真实控件位置计算高亮区域，并对滚动、窗口变化和面板切换进行同步；登录状态和工具按钮复用统一过渡时间。
- 效果：引导步骤能指向当前可见控件，主题切换和状态变化保持一致，不产生突兀的局部变化。
- 方法：图标统一采用Lucide与项目内SVG符号，标题栏使用与内涝监测含义相关的图形标识。
- 效果：界面强调方式保持专业一致，不依赖Emoji表达功能或状态。
- 可引用证据：`frontend/src/App.vue`、`frontend/src/components/HelperUI.vue`、`frontend/src/style.css`、`frontend/public/icons.svg`。

## 七、Podman容器化部署

### 1. 四服务编排

- 方法：使用Compose编排PostGIS、Spring Boot后端、OSRM和Nginx前端四个服务；通过命名卷保存数据库、上传文件和OSRM预处理数据。
- 效果：执行`./deploy/up.sh`即可构建并启动完整系统，统一入口为`http://127.0.0.1:8088`。

### 2. 路由数据与反向代理

- 方法：OSRM容器自动下载江苏OpenStreetMap数据并完成extract、partition和customize；Nginx将`/api`、`/ws`、`/uploads`和`/osrm`分别代理至对应服务。
- 效果：浏览器从同一来源访问页面、REST、WebSocket、附件和路径规划服务，降低跨域配置复杂度。

### 3. 运行约束与安全

- 方法：配置健康检查、服务依赖、非root运行、`no-new-privileges`、本机回环地址绑定、限流和安全响应头。
- 效果：系统能够按照依赖顺序启动，并将课程演示环境的外部暴露范围限制在本机。
- 可引用证据：`compose.yaml`、`deploy/*.Dockerfile`、`deploy/nginx/default.conf`、`deploy/osrm/entrypoint.sh`、`deploy/up.sh`。

## 八、验证结果

- 前端TypeScript检查与Vite生产构建通过。
- rootless Podman 5.8.2与podman-compose 1.5.0完成四服务冷启动，四个容器均达到healthy状态。
- OSRM使用江苏道路数据返回`code=Ok`的实际路线。
- 浏览器经8088统一入口完成登录、空间查询、工单派发、抵达、完成、资源释放、公众上报和追踪查询闭环。
