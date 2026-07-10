<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue'
import { useMap } from '../composables/useMap'
import { useTheme } from '../composables/useTheme'
import { useMapStore } from '../store/map'
import { errorMessage, rawRequest } from '../services/api'
import { escapeHtml } from '../utils/html'
import * as turf from '@turf/turf'
import maplibregl from 'maplibre-gl'
import {
  NButton,
  NSlider,
  NInput,
  NInputNumber,
  NSelect,
  NForm,
  NFormItem,
  NEmpty,
  NList,
  NListItem,
  NTag,
  NIcon,
  NSpace,
  useMessage
} from 'naive-ui'
import {
  Search,
  Compass,
  Trash2,
  Square,
  Hexagon,
  CheckCircle,
  ChevronDown,
  ChevronUp
} from 'lucide-vue-next'

// 使用地图的 useMap 组合式函数
const { map, isLoaded } = useMap()
const { currentTheme } = useTheme()
const mapStore = useMapStore()
const message = useMessage()

// 面板基础展示与收缩状态
const visible = ref(true)
const isMinimized = ref(false)
const isSearching = ref(false)

// 绘图模式与分析条件
const drawMode = ref<'box' | 'polygon' | null>(null)
const isDrawing = ref(false)
const polygonPoints = ref<[number, number][]>([])
const drawnGeometry = ref<any | null>(null) // 绘制图形的 GeoJSON Geometry
const bufferDistance = ref<number>(500) // 缓冲区距离，单位：米
const bufferGeometry = ref<any | null>(null) // 缓冲区的 GeoJSON Geometry

// 属性过滤表单字段
const queryFilters = reactive({
  name: '',
  type: 'all'
})

const typeOptions = [
  { label: '全部设施', value: 'all' },
  { label: '道路积水点', value: 'waterlogging' },
  { label: '雨水泵站', value: 'pump' }
]

// 检索结果数据与地图关联
const searchResults = ref<any[]>([])
const resultMarkers = ref<any[]>([])
let activePopup: maplibregl.Popup | null = null
let registeredMap: maplibregl.Map | null = null

// 框选临时起始经纬度
let startPoint: [number, number] = [0, 0]

const toggleMinimize = () => {
  isMinimized.value = !isMinimized.value
}

// 关闭面板，顺便清理地图交互和叠加物
const closePanel = () => {
  visible.value = false
  clearDrawing()
  clearResults()
}

// ----------------------------------------------------
// 初始化绘图与缓冲区分析图层
// ----------------------------------------------------
const initLayers = () => {
  if (!map.value) return

  // 1. 临时绘制图形的 Source 和 Layers
  if (!map.value.getSource('spatial-query-draw-source')) {
    map.value.addSource('spatial-query-draw-source', {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: []
      }
    })

    // 面图层 (填充色)
    map.value.addLayer({
      id: 'spatial-query-draw-fill',
      type: 'fill',
      source: 'spatial-query-draw-source',
      paint: {
        'fill-color': '#faad14',
        'fill-opacity': 0.15
      },
      filter: ['==', '$type', 'Polygon']
    })

    // 线图层 (边框)
    map.value.addLayer({
      id: 'spatial-query-draw-line',
      type: 'line',
      source: 'spatial-query-draw-source',
      paint: {
        'line-color': '#faad14',
        'line-width': 2.5,
        'line-dasharray': [4, 3]
      }
    })

    // 顶点圆点图层 (顶点微调)
    map.value.addLayer({
      id: 'spatial-query-draw-point',
      type: 'circle',
      source: 'spatial-query-draw-source',
      paint: {
        'circle-radius': 5.5,
        'circle-color': '#ffffff',
        'circle-stroke-width': 2.5,
        'circle-stroke-color': '#faad14'
      },
      filter: ['==', '$type', 'Point']
    })
  }

  // 2. 缓冲区的 Source 和 Layers
  if (!map.value.getSource('spatial-query-buffer-source')) {
    map.value.addSource('spatial-query-buffer-source', {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: []
      }
    })

    // 填充层
    map.value.addLayer({
      id: 'spatial-query-buffer-fill',
      type: 'fill',
      source: 'spatial-query-buffer-source',
      paint: {
        'fill-color': '#1890ff',
        'fill-opacity': 0.22
      }
    })

    // 边线层
    map.value.addLayer({
      id: 'spatial-query-buffer-line',
      type: 'line',
      source: 'spatial-query-buffer-source',
      paint: {
        'line-color': '#1890ff',
        'line-width': 1.8,
        'line-dasharray': [5, 4]
      }
    })
  }
}

// ----------------------------------------------------
// 禁用与恢复地图原生交互 (拖拽、双击缩放)
// ----------------------------------------------------
const disableMapInteractions = () => {
  if (!map.value) return
  mapStore.setInteractionMode('draw-query')
  map.value.dragPan.disable()
  map.value.doubleClickZoom.disable()
  map.value.boxZoom.disable()
  map.value.getCanvas().style.cursor = 'crosshair'
}

const restoreMapInteractions = () => {
  if (!map.value) return
  map.value.dragPan.enable()
  map.value.doubleClickZoom.enable()
  map.value.boxZoom.enable()
  map.value.getCanvas().style.cursor = ''
  if (mapStore.interactionMode === 'draw-query') mapStore.setInteractionMode('idle')
}

// 开始矩形框选模式
const startBoxSelection = () => {
  clearDrawingDataOnly()
  drawMode.value = 'box'
  disableMapInteractions()
}

// 开始多边形打点绘制模式
const startPolygonSelection = () => {
  clearDrawingDataOnly()
  drawMode.value = 'polygon'
  polygonPoints.value = []
  disableMapInteractions()
}

// 更新绘制图层的数据源
const updateDrawSource = (geojson: any) => {
  if (!map.value) return
  const source = map.value.getSource('spatial-query-draw-source') as maplibregl.GeoJSONSource
  if (source) {
    source.setData(geojson)
  }
}

// ----------------------------------------------------
// 绘图过程中监听的鼠标及按键事件
// ----------------------------------------------------
const handleMapMousedown = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== 'box') return
  isDrawing.value = true
  startPoint = [e.lngLat.lng, e.lngLat.lat]
}

const handleMapMousemove = (e: maplibregl.MapMouseEvent) => {
  if (!map.value) return
  const currentPt: [number, number] = [e.lngLat.lng, e.lngLat.lat]

  if (drawMode.value === 'box' && isDrawing.value) {
    // 构建矩形多边形坐标
    const coords = [
      [
        [startPoint[0], startPoint[1]],
        [currentPt[0], startPoint[1]],
        [currentPt[0], currentPt[1]],
        [startPoint[0], currentPt[1]],
        [startPoint[0], startPoint[1]]
      ]
    ]
    updateDrawSource({
      type: 'Feature',
      geometry: {
        type: 'Polygon',
        coordinates: coords
      },
      properties: {}
    })
  } else if (drawMode.value === 'polygon' && polygonPoints.value.length > 0) {
    // 渲染多边形折线及闭合参考线
    const pts = [...polygonPoints.value]
    let geojson: any

    if (pts.length === 1) {
      geojson = {
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: [pts[0], currentPt]
        },
        properties: {}
      }
    } else {
      geojson = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [[...pts, currentPt, pts[0]]]
        },
        properties: {}
      }
    }
    updateDrawSource(geojson)
  }
}

const handleMapMouseup = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== 'box' || !isDrawing.value) return
  isDrawing.value = false

  const currentPt: [number, number] = [e.lngLat.lng, e.lngLat.lat]
  
  // 验证有效范围大小，防止仅做了简单点击
  const dx = Math.abs(currentPt[0] - startPoint[0])
  const dy = Math.abs(currentPt[1] - startPoint[1])
  if (dx < 0.0001 || dy < 0.0001) {
    clearDrawing()
    return
  }

  const coords = [
    [
      [startPoint[0], startPoint[1]],
      [currentPt[0], startPoint[1]],
      [currentPt[0], currentPt[1]],
      [startPoint[0], currentPt[1]],
      [startPoint[0], startPoint[1]]
    ]
  ]

  drawnGeometry.value = {
    type: 'Polygon',
    coordinates: coords
  }

  restoreMapInteractions()
  drawMode.value = null

  // 触发缓冲区分析
  calculateBuffer()
}

const handleMapClick = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== 'polygon') return
  
  const currentPt: [number, number] = [e.lngLat.lng, e.lngLat.lat]
  polygonPoints.value.push(currentPt)

  const pts = [...polygonPoints.value]
  let geojson: any

  if (pts.length === 1) {
    geojson = {
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: pts[0]
      },
      properties: {}
    }
  } else if (pts.length === 2) {
    geojson = {
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates: pts
      },
      properties: {}
    }
  } else {
    geojson = {
      type: 'Feature',
      geometry: {
        type: 'Polygon',
        coordinates: [[...pts, pts[0]]]
      },
      properties: {}
    }
  }
  updateDrawSource(geojson)
}

// 结束绘制多边形
const finishPolygonDrawing = () => {
  if (polygonPoints.value.length < 3) {
    clearDrawing()
    return
  }

  const coords = [[...polygonPoints.value, polygonPoints.value[0]]]
  drawnGeometry.value = {
    type: 'Polygon',
    coordinates: coords
  }

  restoreMapInteractions()
  drawMode.value = null

  // 触发缓冲区分析
  calculateBuffer()
}

// 拦截双击和右击作为结束多边形的逻辑
const handleMapDblClick = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== 'polygon') return
  e.preventDefault()

  // 剔除由于双击导致重复添加的相近端点
  if (polygonPoints.value.length > 2) {
    const last = polygonPoints.value[polygonPoints.value.length - 1]
    const secondLast = polygonPoints.value[polygonPoints.value.length - 2]
    const dist = Math.hypot(last[0] - secondLast[0], last[1] - secondLast[1])
    if (dist < 0.0005) {
      polygonPoints.value.pop()
    }
  }

  finishPolygonDrawing()
}

const handleMapContextMenu = (e: maplibregl.MapMouseEvent) => {
  if (drawMode.value !== 'polygon') return
  e.preventDefault()
  finishPolygonDrawing()
}

// ----------------------------------------------------
// Turf.js 缓冲区分析与图层渲染
// ----------------------------------------------------
const calculateBuffer = () => {
  if (!drawnGeometry.value) return

  try {
    // 使用 turf.buffer 计算缓冲多边形，距离在滑块调整
    const buffered = turf.buffer(drawnGeometry.value, bufferDistance.value, {
      units: 'meters'
    })

    if (buffered) {
      bufferGeometry.value = (buffered as any).geometry

      // 渲染图层覆盖物至地图
      if (map.value) {
        const source = map.value.getSource('spatial-query-buffer-source') as maplibregl.GeoJSONSource
        if (source) {
          source.setData(buffered as any)
        }
      }
    }
  } catch (error) {
    console.error('Turf 缓冲区计算失败:', error)
  }
}

// 动态响应滑块数值的变化
watch(bufferDistance, () => {
  if (drawnGeometry.value) {
    calculateBuffer()
  }
})

// ----------------------------------------------------
// 要素清除逻辑
// ----------------------------------------------------
const clearDrawingDataOnly = () => {
  drawnGeometry.value = null
  bufferGeometry.value = null
  polygonPoints.value = []
  isDrawing.value = false

  if (map.value) {
    const drawSource = map.value.getSource('spatial-query-draw-source') as maplibregl.GeoJSONSource
    if (drawSource) drawSource.setData({ type: 'FeatureCollection', features: [] })
    
    const bufferSource = map.value.getSource('spatial-query-buffer-source') as maplibregl.GeoJSONSource
    if (bufferSource) bufferSource.setData({ type: 'FeatureCollection', features: [] })
  }
}

const clearDrawing = () => {
  clearDrawingDataOnly()
  restoreMapInteractions()
  drawMode.value = null
}

const clearResults = () => {
  searchResults.value = []
  
  resultMarkers.value.forEach(item => {
    item.marker.remove()
  })
  resultMarkers.value = []

  if (activePopup) {
    activePopup.remove()
    activePopup = null
  }
}

// ----------------------------------------------------
// 属性与空间联合检索逻辑
// ----------------------------------------------------
const handleSearch = async () => {
  isSearching.value = true
  clearResults()

  // 组装 POST 数据包
  const queryData = {
    bufferGeoJSON: bufferGeometry.value, // 缓冲区要素多边形 Geometry
    name: queryFilters.name, // 模糊匹配关键字
    type: queryFilters.type // 类型过滤值
  }

  try {
    const data = await rawRequest<{ features?: any[] }>('/api/spatial-query', {
      method: 'POST',
      body: JSON.stringify(queryData)
    })
    searchResults.value = data.features || []

    // 在地图上为查询结果渲染 Marker 标志
    addResultMarkersToMap()
  } catch (error) {
    console.error('空间检索出错:', error)
    message.error(errorMessage(error))
  } finally {
    isSearching.value = false
  }
}

// ----------------------------------------------------
// 地图上高亮要素渲染 (Teardrop Marker 标记)
// ----------------------------------------------------
const addResultMarkersToMap = () => {
  if (!map.value || searchResults.value.length === 0) return

  searchResults.value.forEach((feature: any) => {
    const coords = feature.geometry.coordinates as [number, number]
    const props = feature.properties

    const el = document.createElement('div')
    el.className = 'query-result-marker'

    const isWaterlogging = props.type === 'waterlogging'
    const pinColor = isWaterlogging ? '#f5222d' : '#1890ff'
    
    let innerIcon = ''
    if (isWaterlogging) {
      innerIcon = `
        <svg viewBox="0 0 24 24" width="15" height="15" stroke="#ffffff" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 22a7 7 0 0 0 7-7c0-4.3-7-11-7-11S5 10.7 5 15a7 7 0 0 0 7 7z"/>
        </svg>
      `
    } else {
      innerIcon = `
        <svg viewBox="0 0 24 24" width="14" height="14" stroke="#ffffff" stroke-width="2.5" fill="none" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      `
    }

    el.innerHTML = `
      <div class="marker-pin" style="background-color: ${pinColor}; width: 28px; height: 28px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); display: flex; align-items: center; justify-content: center; border: 2.2px solid #ffffff; box-shadow: 0 3px 8px rgba(0,0,0,0.3); transition: all 0.2s ease;">
        <div style="transform: rotate(45deg); display: flex; align-items: center; justify-content: center;">
          ${innerIcon}
        </div>
      </div>
      <div class="marker-pulse" style="background-color: ${pinColor}"></div>
    `

    const marker = new maplibregl.Marker({
      element: el,
      anchor: 'bottom'
    })
      .setLngLat(coords)
      .addTo(map.value!)

    el.addEventListener('click', (ev) => {
      ev.stopPropagation()
      onClickResult(feature)
    })

    resultMarkers.value.push({
      id: props.id,
      marker,
      feature
    })
  })
}

// ----------------------------------------------------
// 结果联动交互
// ----------------------------------------------------
const onHoverResult = (id: string) => {
  const target = (resultMarkers.value as any[]).find((item: any) => item.id === id)
  if (target) {
    target.marker.getElement().classList.add('is-flashing')
  }
}

const onLeaveResult = (id: string) => {
  const target = (resultMarkers.value as any[]).find((item: any) => item.id === id)
  if (target) {
    target.marker.getElement().classList.remove('is-flashing')
  }
}

const onClickResult = (feature: any) => {
  if (!map.value) return
  const coords = feature.geometry.coordinates as [number, number]
  
  // 平滑定位到目标位置
  map.value.flyTo({
    center: coords,
    zoom: 15.5,
    speed: 1.2,
    essential: true
  })

  // 气泡窗口显示
  showResultPopup(feature)
}

// 渲染弹出气泡详情窗
const showResultPopup = (feature: any) => {
  if (!map.value) return
  if (activePopup) activePopup.remove()

  const props = feature.properties
  const safe = Object.fromEntries(Object.entries(props).map(([key, value]) => [key, escapeHtml(value)]))
  const isDark = currentTheme.value === 'dark'
  
  const textColor = isDark ? '#f3f4f6' : '#1f2225'
  const statusColor = props.status === 'critical' || props.status === 'warning' ? '#d03050' : '#18a058'

  let detailsHtml = ''
  if (props.type === 'waterlogging') {
    detailsHtml = `
      <div style="display:flex; flex-direction:column; gap:6px; font-size:12px; line-height:1.5; color:${textColor}">
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">积水深度:</span><strong style="color:#d03050">${safe.waterDepth}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">当前状态:</span><strong style="color:${statusColor}">${safe.statusName}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">责任单位:</span><strong>${safe.manager}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">联系电话:</span><span style="font-family:monospace">${safe.phone}</span></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">详细地址:</span><span style="max-width:140px; text-align:right; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${safe.address}">${safe.address}</span></div>
      </div>
    `
  } else if (props.type === 'pump') {
    detailsHtml = `
      <div style="display:flex; flex-direction:column; gap:6px; font-size:12px; line-height:1.5; color:${textColor}">
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">排涝能力:</span><strong>${safe.capacity}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">运转状态:</span><strong style="color:${statusColor}">${safe.statusName}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">管理中心:</span><strong>${safe.manager}</strong></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">值班电话:</span><span style="font-family:monospace">${safe.phone}</span></div>
        <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">泵站地址:</span><span style="max-width:140px; text-align:right; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${safe.address}">${safe.address}</span></div>
      </div>
    `
  }

  const contentHtml = `
    <div style="font-family: sans-serif; min-width: 220px; color:${textColor}">
      <h4 style="margin: 0 0 10px 0; border-bottom: 2px solid ${statusColor}; padding-bottom: 6px; font-size: 13.5px; font-weight: 600; display: flex; justify-content: space-between; align-items: center;">
        <span>${safe.name}</span>
        <span style="font-size: 10px; background:${statusColor}; color:#fff; padding:1px 6px; border-radius: 3px; font-weight: normal;">${safe.typeName}</span>
      </h4>
      ${detailsHtml}
    </div>
  `

  activePopup = new maplibregl.Popup({
    className: 'maplibre-theme-popup',
    maxWidth: '280px',
    anchor: 'bottom',
    offset: 12
  })
    .setLngLat(feature.geometry.coordinates)
    .setHTML(contentHtml)
    .addTo(map.value!)
}

// ----------------------------------------------------
// 侦听与生命周期管理
// ----------------------------------------------------
const registerMapEvents = () => {
  if (!map.value) return
  map.value.on('mousedown', handleMapMousedown)
  map.value.on('mousemove', handleMapMousemove)
  map.value.on('mouseup', handleMapMouseup)
  map.value.on('click', handleMapClick)
  map.value.on('dblclick', handleMapDblClick)
  map.value.on('contextmenu', handleMapContextMenu)
}

const unregisterMapEvents = () => {
  if (!map.value) return
  map.value.off('mousedown', handleMapMousedown)
  map.value.off('mousemove', handleMapMousemove)
  map.value.off('mouseup', handleMapMouseup)
  map.value.off('click', handleMapClick)
  map.value.off('dblclick', handleMapDblClick)
  map.value.off('contextmenu', handleMapContextMenu)
}

watch([isLoaded, () => map.value], ([loaded, mapInst]) => {
  if (!loaded || !mapInst || registeredMap === mapInst) return

  // isMapLoaded 只会在 MapLibre 的 load 回调内置为 true。此时样式已经可用，
  // 若再等待下一次 load 会错过唯一一次加载事件，导致所有绘图事件都未注册。
  initLayers()
  registerMapEvents()
  registeredMap = mapInst
}, { immediate: true })

onUnmounted(() => {
  mapStore.setInteractionMode('idle')
  if (map.value) {
    unregisterMapEvents()
    clearResults()
    
    const layers = [
      'spatial-query-draw-fill',
      'spatial-query-draw-line',
      'spatial-query-draw-point',
      'spatial-query-buffer-fill',
      'spatial-query-buffer-line'
    ]
    layers.forEach(lyr => {
      if (map.value!.getLayer(lyr)) map.value!.removeLayer(lyr)
    })

    const sources = ['spatial-query-draw-source', 'spatial-query-buffer-source']
    sources.forEach(src => {
      if (map.value!.getSource(src)) map.value!.removeSource(src)
    })
  }
  registeredMap = null
})
</script>

<template>
  <!-- 空间检索浮动面板 -->
  <div v-if="visible" id="spatial-query-panel" class="spatial-query-panel floating-card" :class="currentTheme + '-theme'">
    <!-- 头部栏 -->
    <div class="panel-header-row">
      <div class="title-section">
        <n-icon :component="Compass" size="18" />
        <span class="panel-title font-bold">空间查询与检索</span>
      </div>
      <div class="action-section">
        <n-button size="tiny" circle secondary @click="toggleMinimize" title="折叠/展开">
          <template #icon>
            <n-icon :component="isMinimized ? ChevronDown : ChevronUp" />
          </template>
        </n-button>
        <n-button size="tiny" circle secondary @click="closePanel" style="margin-left: 6px;" title="关闭">
          ✕
        </n-button>
      </div>
    </div>

    <!-- 可折叠面板容器 -->
    <div v-show="!isMinimized" class="panel-content">
      <!-- 1. 空间交互图形绘制 -->
      <div class="section-title">1. 空间交互图形绘制</div>
      <n-space vertical size="small">
        <n-space size="small">
          <n-button
            :type="drawMode === 'box' ? 'warning' : 'default'"
            secondary
            size="small"
            @click="startBoxSelection"
          >
            <template #icon><n-icon :component="Square" /></template>
            开始框选
          </n-button>
          
          <n-button
            :type="drawMode === 'polygon' ? 'warning' : 'default'"
            secondary
            size="small"
            @click="startPolygonSelection"
          >
            <template #icon><n-icon :component="Hexagon" /></template>
            多边形绘制
          </n-button>

          <n-button
            v-if="drawMode === 'polygon' && polygonPoints.length >= 3"
            type="primary"
            size="small"
            @click="finishPolygonDrawing"
          >
            完成绘制
          </n-button>

          <n-button
            v-if="drawnGeometry"
            type="tertiary"
            size="small"
            @click="clearDrawing"
          >
            <template #icon><n-icon :component="Trash2" /></template>
            清空范围
          </n-button>
        </n-space>

        <!-- 当前绘制状态提示 -->
        <div v-if="drawMode" class="drawing-tip warn-tip">
          <span class="pulse-dot"></span>
          <span v-if="drawMode === 'box'">按住鼠标左键并在地图上拖拽绘制矩形，释放鼠标完成。</span>
          <span v-else>在地图左键依次打点，双击、右键或点击【完成绘制】结束。</span>
        </div>
        <div v-else-if="drawnGeometry" class="drawing-tip success-tip">
          <n-icon :component="CheckCircle" color="#18a058" />
          <span>空间图形构建成功，可调整缓冲区大小。</span>
        </div>
        <div v-else class="drawing-tip info-tip">
          <span>当前未设定绘制范围，默认将在全图内进行检索。</span>
        </div>
      </n-space>

      <!-- 2. 动态缓冲区计算 -->
      <div class="section-title">2. 动态缓冲区计算</div>
      <n-space vertical size="small" class="buffer-section">
        <div class="slider-row">
          <n-slider
            v-model:value="bufferDistance"
            :min="50"
            :max="2000"
            :step="50"
            :disabled="!drawnGeometry"
            style="flex: 1;"
          />
          <n-input-number
            v-model:value="bufferDistance"
            size="small"
            :min="50"
            :max="2000"
            :step="50"
            :disabled="!drawnGeometry"
            style="width: 90px; margin-left: 10px;"
            :show-button="false"
            suffix="米"
          />
        </div>
      </n-space>

      <!-- 3. 过滤属性条件 -->
      <div class="section-title">3. 过滤属性条件</div>
      <n-form label-placement="left" label-width="70" size="small" :model="queryFilters" class="filter-form">
        <n-form-item label="设施名称">
          <n-input v-model:value="queryFilters.name" placeholder="模糊匹配名称..." clearable />
        </n-form-item>
        <n-form-item label="设施类型">
          <n-select v-model:value="queryFilters.type" :options="typeOptions" />
        </n-form-item>
        
        <div class="submit-row" style="margin-top: 10px;">
          <n-button type="primary" :loading="isSearching" block @click="handleSearch">
            <template #icon><n-icon :component="Search" /></template>
            开始空间联合查询
          </n-button>
        </div>
      </n-form>

      <!-- 4. 检索结果列表展现 -->
      <div class="section-header-row">
        <div class="section-title">4. 检索列表结果 ({{ searchResults.length }})</div>
        <n-button v-if="searchResults.length > 0" text type="primary" size="tiny" @click="clearResults">
          清空结果
        </n-button>
      </div>

      <div class="results-list-container">
        <n-empty v-if="searchResults.length === 0" description="暂无符合筛选条件的水利/市政监控设施" />
        
        <n-list v-else hoverable clickable class="query-results-list">
          <n-list-item
            v-for="item in searchResults"
            :key="item.properties.id"
            @mouseenter="onHoverResult(item.properties.id)"
            @mouseleave="onLeaveResult(item.properties.id)"
            @click="onClickResult(item)"
            class="result-item"
          >
            <div class="result-title-row">
              <span class="facility-name font-bold">{{ item.properties.name }}</span>
              <n-tag
                :type="item.properties.type === 'waterlogging' ? 'error' : 'info'"
                size="small"
                round
              >
                {{ item.properties.typeName }}
              </n-tag>
            </div>
            
            <div class="result-detail-row">
              <div class="result-meta">
                <span class="meta-label">状态:</span>
                <span :class="'status-' + item.properties.status" class="meta-val font-bold">
                  {{ item.properties.statusName }}
                </span>
              </div>
              <div class="result-meta" v-if="item.properties.waterDepth">
                <span class="meta-label">积水深:</span>
                <span class="meta-val text-error font-bold">{{ item.properties.waterDepth }}</span>
              </div>
              <div class="result-meta" v-if="item.properties.capacity">
                <span class="meta-label">排量:</span>
                <span class="meta-val text-primary font-bold">{{ item.properties.capacity }}</span>
              </div>
            </div>
            
            <div class="result-address-row">
              <span class="address-text" :title="item.properties.address">{{ item.properties.address }}</span>
            </div>
          </n-list-item>
        </n-list>
      </div>
    </div>
  </div>

  <!-- 折叠后的入口悬浮球 -->
  <div v-else class="spatial-query-trigger floating-card" @click="visible = true" title="打开空间查询面板">
    <n-icon :component="Compass" size="20" />
    <span class="trigger-text font-bold">空间检索</span>
  </div>
</template>

<style scoped>
/* 浮动面板样式 */
.spatial-query-panel {
  position: absolute;
  top: 80px;
  left: 15px;
  width: clamp(330px, 22vw, 390px);
  max-height: calc(100vh - 110px);
  z-index: 10;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.panel-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  padding-bottom: 8px;
  margin-bottom: 12px;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-title {
  font-size: 14px;
  color: var(--text-primary, #1f2225);
}

.panel-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  flex: 1;
  padding-right: 2px;
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary, #718096);
  margin-top: 4px;
  margin-bottom: 4px;
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 绘图提示样式 */
.drawing-tip {
  font-size: 11px;
  line-height: 1.4;
  padding: 6px 10px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.info-tip {
  background-color: rgba(120, 120, 120, 0.05);
  color: var(--text-secondary, #718096);
}

.warn-tip {
  background-color: #fffbe6;
  color: #d46b08;
  border: 1px solid #ffe58f;
}

.dark-theme .warn-tip {
  background-color: rgba(212, 107, 8, 0.15);
  color: #ffa940;
  border: 1px solid rgba(212, 107, 8, 0.3);
}

.success-tip {
  background-color: #f6ffed;
  color: #389e0d;
  border: 1px solid #b7eb8f;
}

.dark-theme .success-tip {
  background-color: rgba(56, 158, 13, 0.15);
  color: #73d13d;
  border: 1px solid rgba(56, 158, 13, 0.3);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background-color: #fa8c16;
  border-radius: 50%;
  animation: pulse 1.2s infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0% { transform: scale(0.85); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 1; }
  100% { transform: scale(0.85); opacity: 0.5; }
}

/* 缓冲区滑块结构 */
.slider-row {
  display: flex;
  align-items: center;
}

.filter-form :deep(.n-form-item) {
  margin-bottom: 4px;
}

/* 结果列表区域 */
.results-list-container {
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 6px;
  background-color: rgba(120, 120, 120, 0.03);
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  box-sizing: border-box;
}

.query-results-list {
  background-color: transparent !important;
}

.result-item {
  padding: 8px 10px !important;
  border-radius: 4px;
  transition: background-color 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-item:hover {
  background-color: rgba(120, 120, 120, 0.08) !important;
}

.result-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.facility-name {
  font-size: 13px;
  color: var(--text-primary, #1f2225);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.result-detail-row {
  display: flex;
  gap: 12px;
  font-size: 11px;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: var(--text-secondary, #718096);
}

.meta-val {
  color: var(--text-primary, #1f2225);
}

/* 状态色值设置 */
.status-normal { color: #18a058; }
.status-running { color: #18a058; }
.status-standby { color: #2080f0; }
.status-warning { color: #f0a020; }
.status-critical { color: #d03050; }

.result-address-row {
  font-size: 10px;
  color: var(--text-secondary, #718096);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.font-bold {
  font-weight: 600;
}

.text-error {
  color: #d03050 !important;
}

.text-primary {
  color: #1890ff !important;
}

/* 展开触发球样式 */
.spatial-query-trigger {
  position: absolute;
  top: 80px;
  left: 15px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  border-radius: 6px;
  transition: all 0.25s ease;
}

.spatial-query-trigger:hover {
  background-color: var(--primary-color, #1890ff);
  color: #ffffff !important;
}

.trigger-text {
  font-size: 12px;
}

@media (min-width: 1800px) {
  .spatial-query-panel {
    top: 88px;
    left: 22px;
  }
}

@media (max-width: 980px) {
  .spatial-query-panel {
    width: min(330px, calc(100vw - 30px));
  }
}
</style>

<style>
/* 结果高亮样式与呼吸灯动画 (全局，非 scoped) */
.query-result-marker {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

@keyframes marker-pulse-flash {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0px rgba(250, 84, 28, 0.85);
    filter: brightness(1);
  }
  50% {
    transform: scale(1.35);
    box-shadow: 0 0 0 16px rgba(250, 84, 28, 0);
    filter: brightness(1.25);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0px rgba(250, 84, 28, 0);
    filter: brightness(1);
  }
}

/* 列表行 Hover 时，对应地图 Marker 闪烁动画 */
.query-result-marker.is-flashing .marker-pin {
  animation: marker-pulse-flash 0.8s infinite ease-in-out !important;
  border-color: #fa541c !important;
  background-color: #fa541c !important;
  z-index: 999;
}
</style>
