<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { Map, Popup, NavigationControl } from 'maplibre-gl'
import { useMapStore } from '../store/map'
import { useTheme } from '../composables/useTheme'
import { useMap } from '../composables/useMap'
import { xuzhouBoundary, pipeNetwork, waterStations } from '../mock/geojson'

const mapStore = useMapStore()
const { isDark } = useTheme()
const { registerLayer } = useMap()

const mapElement = ref<HTMLElement | null>(null)
let mapInstance: Map | null = null

// 挂载时初始化地图
onMounted(() => {
  if (!mapElement.value) return

  // 初始化 MapLibre GL Map
  mapInstance = new Map({
    container: mapElement.value,
    style: {
      version: 8,
      sources: {
        'carto-light': {
          type: 'raster',
          // 亮色底图：使用 CartoDB Positron 瓦片，高清晰、无偏色、免 Key 访问，契合亮色主题
          tiles: [
            'https://basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png'
          ],
          tileSize: 256,
          attribution: '&copy; OpenStreetMap &copy; CartoDB'
        },
        'carto-dark': {
          type: 'raster',
          // 暗色底图：使用 CartoDB Dark Matter 瓦片，低饱和度、适合大屏可视化、免 Key 访问，契合暗色主题
          tiles: [
            'https://basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png'
          ],
          tileSize: 256,
          attribution: '&copy; OpenStreetMap &copy; CartoDB'
        }
      },
      layers: [
        {
          id: 'basemap-light',
          type: 'raster',
          source: 'carto-light',
          layout: {
            visibility: isDark.value ? 'none' : 'visible'
          }
        },
        {
          id: 'basemap-dark',
          type: 'raster',
          source: 'carto-dark',
          layout: {
            visibility: isDark.value ? 'visible' : 'none'
          }
        }
      ]
    },
    center: [117.1848, 34.2610], // 徐州市中心经纬度
    zoom: 12,
    minZoom: 9,
    maxZoom: 18,
    pitchWithRotate: false,
    dragRotate: false // 禁止旋转以保障二维管控直观性
  })

  // 添加导航控件（缩放、指南针）
  mapInstance.addControl(new NavigationControl({ showCompass: false }), 'top-left')

  mapInstance.on('load', () => {
    if (!mapInstance) return
    mapStore.setMapInstance(mapInstance)
    mapStore.setMapLoaded(true)

    // 1. 注册核心城区范围图层
    registerLayer('xuzhou-boundary', {
      name: '徐州市核心城区',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: xuzhouBoundary
      },
      layers: [
        {
          id: 'boundary-fill',
          type: 'fill',
          paint: {
            'fill-color': '#1890ff',
            'fill-opacity': isDark.value ? 0.08 : 0.05
          }
        },
        {
          id: 'boundary-line',
          type: 'line',
          paint: {
            'line-color': '#1890ff',
            'line-width': 2,
            'line-dasharray': [4, 4]
          }
        }
      ]
    })

    // 2. 注册市政排水管网图层
    registerLayer('pipe-network', {
      name: '市政排水管网',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: pipeNetwork
      },
      layers: [
        {
          id: 'pipe-line',
          type: 'line',
          paint: {
            // 根据状态渲染不同颜色：超负荷为红色，预警为橙色，正常为绿色
            'line-color': [
              'match',
              ['get', 'status'],
              '超负荷', '#ff4d4f',
              '预警', '#faad14',
              '#52c41a'
            ],
            'line-width': 4,
            'line-opacity': 0.8
          }
        }
      ]
    })

    // 3. 注册积水与水位监测站图层
    registerLayer('water-stations', {
      name: '水位与积水监测站',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: waterStations
      },
      layers: [
        {
          id: 'station-point',
          type: 'circle',
          paint: {
            // 根据监测状态渲染点颜色：红色为超警戒值，橙色为预警，绿色为正常
            'circle-color': [
              'match',
              ['get', 'status'],
              '超警戒', '#ff4d4f',
              '预警', '#faad14',
              '#52c41a'
            ],
            'circle-radius': 9,
            'circle-stroke-width': 2,
            'circle-stroke-color': '#ffffff'
          }
        }
      ]
    })

    // 4. 绑定交互事件：点击监测点展示气泡弹窗 (Popup)
    mapInstance.on('click', 'station-point', (e) => {
      if (!mapInstance || !e.features || !e.features[0]) return
      const feature = e.features[0]
      const props = feature.properties
      const coordinates = (feature.geometry as any).coordinates.slice()

      // 调整经纬度以防在拉伸缩放时偏离
      while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
        coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360
      }

      const statusColor = props.status === '正常' ? '#52c41a' : props.status === '预警' ? '#faad14' : '#ff4d4f'
      
      const htmlContent = `
        <div class="map-popup-card">
          <div class="popup-title">${props.name}</div>
          <div class="popup-item"><strong>站点编码:</strong> <span>${props.id}</span></div>
          <div class="popup-item"><strong>站点类型:</strong> <span>${props.type}</span></div>
          <div class="popup-item"><strong>当前水位:</strong> <span class="highlight-val" style="color: ${statusColor}">${props.waterLevel}</span></div>
          <div class="popup-item"><strong>警戒水位:</strong> <span>${props.warningLevel}</span></div>
          <div class="popup-item"><strong>当前流量:</strong> <span>${props.flowRate}</span></div>
          <div class="popup-item"><strong>测报状态:</strong> <span class="status-badge" style="background: ${statusColor}22; color: ${statusColor}; border: 1px solid ${statusColor}44;">${props.status}</span></div>
          <div class="popup-item popup-addr"><strong>详细地址:</strong> <span>${props.address}</span></div>
        </div>
      `

      new Popup({ className: 'custom-webgis-popup' })
        .setLngLat(coordinates as [number, number])
        .setHTML(htmlContent)
        .addTo(mapInstance)
    })

    // 悬浮在监测点上时鼠标变小手
    mapInstance.on('mouseenter', 'station-point', () => {
      if (mapInstance) mapInstance.getCanvas().style.cursor = 'pointer'
    })
    mapInstance.on('mouseleave', 'station-point', () => {
      if (mapInstance) mapInstance.getCanvas().style.cursor = ''
    })
  })
})

// 监听主题状态变化，切换底图
watch(isDark, (val) => {
  if (!mapInstance) return
  
  if (val) {
    if (mapInstance.getLayer('basemap-light')) {
      mapInstance.setLayoutProperty('basemap-light', 'visibility', 'none')
    }
    if (mapInstance.getLayer('basemap-dark')) {
      mapInstance.setLayoutProperty('basemap-dark', 'visibility', 'visible')
    }
    // 同时更新边界线的填充透明度以适配暗色对比
    if (mapInstance.getLayer('boundary-fill')) {
      mapInstance.setPaintProperty('boundary-fill', 'fill-opacity', 0.08)
    }
  } else {
    if (mapInstance.getLayer('basemap-light')) {
      mapInstance.setLayoutProperty('basemap-light', 'visibility', 'visible')
    }
    if (mapInstance.getLayer('basemap-dark')) {
      mapInstance.setLayoutProperty('basemap-dark', 'visibility', 'none')
    }
    if (mapInstance.getLayer('boundary-fill')) {
      mapInstance.setPaintProperty('boundary-fill', 'fill-opacity', 0.05)
    }
  }
})

// 销毁时清理地图实例
onUnmounted(() => {
  if (mapInstance) {
    mapInstance.remove()
    mapInstance = null
    mapStore.setMapLoaded(false)
  }
})
</script>

<template>
  <div class="map-container-wrapper">
    <!-- 地图挂载容器 -->
    <div ref="mapElement" class="map-canvas"></div>
  </div>
</template>

<style>
.map-container-wrapper {
  width: 100%;
  height: 100%;
  position: relative;
}

.map-canvas {
  width: 100%;
  height: 100%;
}

/* MapLibre 弹窗美化样式 */
.custom-webgis-popup .maplibregl-popup-content {
  border-radius: 8px !important;
  padding: 12px 14px !important;
  box-shadow: var(--shadow) !important;
  border: 1px solid var(--border-color) !important;
  background: var(--bg-card) !important;
  color: var(--text-primary) !important;
  backdrop-filter: blur(8px);
  min-width: 240px;
  font-family: inherit;
  transition: all 0.3s;
}

.custom-webgis-popup .maplibregl-popup-tip {
  border-top-color: var(--bg-card) !important;
  border-bottom-color: var(--bg-card) !important;
}

.map-popup-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.map-popup-card .popup-title {
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 6px;
  margin-bottom: 4px;
  color: var(--primary-color);
}

.map-popup-card .popup-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  line-height: 1.5;
}

.map-popup-card .popup-item strong {
  color: var(--text-secondary);
  font-weight: 500;
}

.map-popup-card .popup-item .highlight-val {
  font-weight: 600;
}

.map-popup-card .popup-item .status-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.map-popup-card .popup-addr {
  flex-direction: column;
  align-items: flex-start;
  margin-top: 4px;
  gap: 2px;
  border-top: 1px dashed var(--border-color);
  padding-top: 4px;
}

.map-popup-card .popup-addr span {
  font-size: 11px;
  color: var(--text-secondary);
}
</style>
