<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  NConfigProvider,
  NMessageProvider,
  NDialogProvider,
  NButton,
  NInput,
  NSlider,
  createDiscreteApi
} from 'naive-ui'
import { Popup } from 'maplibre-gl'

import HelperUI from './components/HelperUI.vue'
import MapContainer from './components/MapContainer.vue'
import SpatialQueryPanel from './components/SpatialQueryPanel.vue'
import EmergencyDispatchPanel from './components/EmergencyDispatchPanel.vue'
import { useTheme } from './composables/useTheme'
import { useMapStore } from './store/map'

const { isDark, naiveTheme } = useTheme()
const mapStore = useMapStore()

// 使用 Naive UI 的离散 API，以便在根组件 <script setup> 阶段便捷调用提示消息
const { message } = createDiscreteApi(['message'], {
  configProviderProps: computed(() => ({
    theme: naiveTheme.value
  }))
})



/* ==========================================================================
   4. 公众灾情上报状态与逻辑
   ========================================================================== */
const reportLng = ref(117.1848)
const reportLat = ref(34.2610)
const reportDepth = ref(20)
const reportDetails = ref('')

const reportLngStr = computed({
  get: () => reportLng.value.toFixed(5),
  set: (val) => {
    const num = parseFloat(val)
    if (!isNaN(num)) reportLng.value = num
  }
})

const reportLatStr = computed({
  get: () => reportLat.value.toFixed(5),
  set: (val) => {
    const num = parseFloat(val)
    if (!isNaN(num)) reportLat.value = num
  }
})

// 获取当前地图视口中心经纬度
const getMapCenterForReport = () => {
  const map = mapStore.mapInstance
  if (map) {
    const center = map.getCenter()
    reportLng.value = center.lng
    reportLat.value = center.lat
    message.info(`已捕获当前视口中心坐标: [${center.lng.toFixed(5)}, ${center.lat.toFixed(5)}]`)
  }
}

const userReports = ref<any[]>([])

// 提交灾情上报并渲染至地图上
const submitReport = () => {
  if (!reportLng.value || !reportLat.value) {
    message.error('未绑定有效上报位置，请先点击拾取中心')
    return
  }

  const reportId = `UR-${Date.now()}`
  const newReportFeature = {
    type: 'Feature',
    geometry: {
      type: 'Point',
      coordinates: [reportLng.value, reportLat.value]
    },
    properties: {
      id: reportId,
      name: `市民报灾: ${reportDetails.value.trim() || '路段积水点'}`,
      type: '突发灾情上报',
      waterLevel: `${reportDepth.value} cm`,
      warningLevel: '0 cm',
      flowRate: '0 m³/s',
      status: reportDepth.value >= 40 ? '超警戒' : '预警',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
      address: `上报地点 (坐标: ${reportLng.value.toFixed(4)}, ${reportLat.value.toFixed(4)})`
    }
  }

  userReports.value.push(newReportFeature)
  renderUserReportLayer()

  // 视角平滑移入
  const map = mapStore.mapInstance
  if (map) {
    map.flyTo({
      center: [reportLng.value, reportLat.value],
      zoom: 14,
      essential: true
    })
  }

  message.success('市民报灾信息上报成功！感谢您为城市排水做出的反馈。')

  // 重置表单
  reportDetails.value = ''
  reportDepth.value = 20
}

// 动态将用户上报图层挂载至 MapLibre GL 实例上
const renderUserReportLayer = () => {
  const map = mapStore.mapInstance
  if (!map) return

  const sourceId = 'user-reports-source'
  const layerId = 'user-reports-layer'
  const geojson = {
    type: 'FeatureCollection' as const,
    features: userReports.value
  }

  if (map.getSource(sourceId)) {
    (map.getSource(sourceId) as any).setData(geojson as any)
  } else {
    map.addSource(sourceId, {
      type: 'geojson',
      data: geojson as any
    })
    map.addLayer({
      id: layerId,
      source: sourceId,
      type: 'circle',
      paint: {
        'circle-color': '#8a2be2', // 紫色标识市民上报点
        'circle-radius': 10,
        'circle-stroke-width': 2.5,
        'circle-stroke-color': '#ffffff'
      }
    })

    // 绑定市民上报点点击 Popup
    map.on('click', layerId, (e) => {
      if (!map || !e.features || !e.features[0]) return
      const feature = e.features[0]
      const props = feature.properties
      const coordinates = (feature.geometry as any).coordinates.slice()

      const htmlContent = `
        <div class="map-popup-card">
          <div class="popup-title" style="color: #8a2be2;">市民灾害报送</div>
          <div class="popup-item"><strong>灾情简述:</strong> <span>${props.name.replace('市民报灾: ', '')}</span></div>
          <div class="popup-item"><strong>估算水深:</strong> <span class="highlight-val" style="color: #ff4d4f">${props.waterLevel}</span></div>
          <div class="popup-item"><strong>上报时间:</strong> <span>${props.time}</span></div>
          <div class="popup-item"><strong>处理进度:</strong> <span class="status-badge" style="background: rgba(138, 43, 226, 0.1); color: #8a2be2; border: 1px solid rgba(138, 43, 226, 0.2);">已登记</span></div>
        </div>
      `

      new Popup({ className: 'custom-webgis-popup' })
        .setLngLat(coordinates as [number, number])
        .setHTML(htmlContent)
        .addTo(map)
    })

    map.on('mouseenter', layerId, () => {
      map.getCanvas().style.cursor = 'pointer'
    })
    map.on('mouseleave', layerId, () => {
      map.getCanvas().style.cursor = ''
    })
  }
}
</script>

<template>
  <n-config-provider :theme="naiveTheme">
    <n-message-provider>
      <n-dialog-provider>
        <!-- 主布局容器，根据全局主题动态绑定类名 -->
        <div class="app-layout" :class="{ 'dark-theme': isDark, 'light-theme': !isDark }">
          
          <!-- 系统顶栏 -->
          <HelperUI />

          <!-- 地图区域 + 悬浮控制面板 -->
          <main class="main-content">
            <!-- 底层 WebGIS 地图画布 -->
            <div id="map-container" class="map-viewport">
              <MapContainer />
            </div>

            <!-- 左侧面板：空间查询检索 -->
            <SpatialQueryPanel />

            <!-- 右侧面板：应急调度管理（模块五核心组件） -->
            <EmergencyDispatchPanel />

            <!-- 浮动面板：市民定位上报模拟器 -->
            <section id="public-report-panel" class="dashboard-panel panel-float">
              <div class="panel-header">
                <svg class="panel-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                </svg>
                <h2 class="panel-title">突发积水点定位上报 (公众端模拟)</h2>
              </div>

              <div class="panel-body">
                <!-- 坐标拾取区 -->
                <div class="report-coords-wrapper">
                  <div class="coord-box">
                    <span class="box-label">上报经度</span>
                    <n-input v-model:value="reportLngStr" size="small" placeholder="点击拾取或输入" />
                  </div>
                  <div class="coord-box">
                    <span class="box-label">上报纬度</span>
                    <n-input v-model:value="reportLatStr" size="small" placeholder="点击拾取或输入" />
                  </div>
                  <n-button type="info" size="small" secondary style="margin-top: 18px;" @click="getMapCenterForReport">
                    拾取视口中心
                  </n-button>
                </div>

                <!-- 水位滑动选择 -->
                <div class="report-field" style="margin-top: 12px;">
                  <div class="field-header">
                    <span class="box-label">估算积水深度</span>
                    <span class="badge-val">{{ reportDepth }} cm</span>
                  </div>
                  <n-slider v-model:value="reportDepth" :min="5" :max="120" :step="5" />
                </div>

                <!-- 简述输入 -->
                <div class="report-field" style="margin-top: 12px;">
                  <span class="box-label">现场险情描述</span>
                  <n-input v-model:value="reportDetails" placeholder="描述现场积水情况，如：下水堵塞，水淹过胎..." size="small" />
                </div>

                <!-- 提交按钮 -->
                <n-button type="primary" block size="small" style="margin-top: 14px; font-weight: 600;" @click="submitReport">
                  立即报送灾情
                </n-button>
              </div>
            </section>
          </main>


        </div>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
/* 全局页面布局 */
.app-layout {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);
  color: var(--text-primary);
  overflow: hidden;
  transition: background-color 0.3s, color 0.3s;
}

.main-content {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.map-viewport {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

/* ==========================================================================
   仪表盘通用卡片/面板设计
   ========================================================================== */
.dashboard-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  box-shadow: var(--shadow);
  backdrop-filter: blur(14px) saturate(120%);
  -webkit-backdrop-filter: blur(14px) saturate(120%);
  z-index: 10;
  display: flex;
  flex-direction: column;
  padding: 16px;
  box-sizing: border-box;
  color: var(--text-primary);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 8px;
  margin-bottom: 12px;
}

.panel-icon {
  width: 18px;
  height: 18px;
  color: var(--primary-color);
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  letter-spacing: 0.5px;
}

.panel-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-section {
  display: flex;
  flex-direction: column;
}

.flex-expand {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

/* ==========================================================================
   具体各个面板定位
   ========================================================================== */
.panel-left {
  position: absolute;
  left: 20px;
  top: 20px;
  bottom: 20px;
  width: 360px;
}

.panel-right {
  position: absolute;
  right: 20px;
  top: 20px;
  bottom: 20px;
  width: 360px;
}

.panel-float {
  position: absolute;
  left: 396px;
  bottom: 20px;
  width: 360px;
}

/* ==========================================================================
   内部微组件与修饰样式
   ========================================================================== */
.search-input-icon {
  width: 14px;
  height: 14px;
  color: var(--text-secondary);
}

.search-result-list {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  margin-top: 6px;
  background: var(--bg-card);
  padding: 2px;
}

.search-item {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color);
  transition: background-color 0.2s;
}

.search-item:last-child {
  border-bottom: none;
}

.search-item:hover {
  background: rgba(24, 144, 255, 0.08);
}

.search-item .item-name {
  font-size: 13px;
  font-weight: 600;
}

.search-item .item-addr {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.radius-controller {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
}

.radius-controller .label {
  color: var(--text-secondary);
}

.radius-controller .val {
  color: var(--primary-color);
  font-weight: 600;
}

.empty-placeholder {
  font-size: 12px;
  color: var(--text-secondary);
  text-align: center;
  padding: 30px 14px;
  border: 1px dashed var(--border-color);
  border-radius: 6px;
  line-height: 1.6;
}

.result-list-scroll {
  flex: 1;
}

.result-item {
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  background: rgba(120, 120, 120, 0.02);
  transition: all 0.2s;
}

.result-item:hover {
  border-color: var(--primary-color);
  background: rgba(24, 144, 255, 0.04);
  transform: translateX(1px);
}

.result-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-item-name {
  font-size: 13px;
  font-weight: 600;
}

.result-item-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-top: 6px;
  color: var(--text-secondary);
}

/* 应急指挥态势图 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 4px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 4px;
  background: rgba(120, 120, 120, 0.03);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.stat-num {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
}

.stat-label {
  font-size: 10.5px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.color-red { color: #ff4d4f; }
.color-orange { color: #faad14; }
.color-green { color: #52c41a; }

.order-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.order-list-scroll {
  flex: 1;
}

.order-item {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  background: rgba(120, 120, 120, 0.03);
  transition: all 0.25s;
}

.order-item:hover {
  border-color: var(--primary-color);
  background: rgba(24, 144, 255, 0.04);
  transform: translateY(-1px);
}

.order-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.order-item-id {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
}

.order-item-loc {
  font-size: 13.5px;
  font-weight: 600;
  margin: 6px 0;
}

.order-item-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
}

.order-time {
  color: var(--primary-color);
  font-weight: 500;
}

/* 公众灾情报送样式 */
.report-coords-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.coord-box {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.box-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 500;
}

.report-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.badge-val {
  font-size: 12px;
  color: var(--primary-color);
  font-weight: 600;
}
</style>
