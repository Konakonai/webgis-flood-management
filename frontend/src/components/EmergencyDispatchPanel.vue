<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { 
  NCard, NTabs, NTabPane, NList, NListItem, NThing, NTag, NButton, 
  NSpace, NForm, NFormItem, NInput, NSelect, NModal, NDivider, 
  NStatistic, NEmpty, NText, NIcon, useMessage, useNotification
} from 'naive-ui'
import {
  CircleCheck,
  ClipboardList,
  Clock3,
  LocateFixed,
  MapPin,
  Package,
  Route,
  Truck,
  Waves,
  Zap
} from 'lucide-vue-next'
import * as turf from '@turf/turf'
import maplibregl from 'maplibre-gl'
import { useMap } from '../composables/useMap'
import { useEmergencyStore, type ResourcePoint, type PumpStation, type AlarmEvent } from '../store/emergency'
import { useMapStore } from '../store/map'
import { errorMessage } from '../services/api'
import { escapeHtml } from '../utils/html'

// 初始化提示工具
const message = useMessage()
const notification = useNotification()

// 初始化地图 Composable 和 应急调度 Store
const { map, isLoaded } = useMap()
const store = useEmergencyStore()
const mapStore = useMapStore()
const actionLoading = ref(false)

// 过滤筛选与状态字段
const activeTab = ref('workorders')
const statusFilter = ref<'全部' | '待派单' | '处置中' | '已完成'>('全部')

// 右键新增报警事件相关状态
const showRegisterModal = ref(false)
const registerForm = ref({
  reporter: '',
  phone: '',
  description: '',
  severity: 'medium' as 'low' | 'medium' | 'high',
  lng: 0,
  lat: 0
})

const rules = {
  reporter: { required: true, message: '请输入报警人姓名', trigger: 'blur' },
  phone: { 
    required: true, 
    validator(_rule: any, value: string) {
      if (!value) return new Error('请输入联系电话')
      if (!/^\d{11}$/.test(value)) return new Error('请输入11位手机号码')
      return true
    },
    trigger: ['input', 'blur'] 
  },
  description: { required: true, message: '请简要描述积水灾情', trigger: 'blur' }
}

// 缓存地图 Markers 实例 (使用非响应式数组以避免 Vue 响应式代理导致 maplibregl.Marker 类型失效及内存泄漏)
let resourceMarkers: maplibregl.Marker[] = []
let stationMarkers: maplibregl.Marker[] = []
let alarmMarkers: maplibregl.Marker[] = []

// 动画相关变量
let animationFrameId: number | null = null
let contextMenuBound = false

// 过滤后的工单列表
const filteredAlarms = computed(() => {
  if (statusFilter.value === '全部') {
    return store.alarmEvents
  }
  return store.alarmEvents.filter(e => e.status === statusFilter.value)
})

const maskPhone = (phone: string) => phone ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '未提供'

// 计算当前选中工单的详细信息
const selectedAlarm = computed(() => {
  return store.alarmEvents.find(e => e.id === store.selectedAlarmId)
})

// 使用 Turf.js 计算当前选中工单最近的泵车驻地（直线距离）
const nearestPumpInfo = computed(() => {
  if (!selectedAlarm.value) return null
  const nearest = store.findNearestPump(selectedAlarm.value.lng, selectedAlarm.value.lat)
  if (!nearest) return null
  
  const from = turf.point([selectedAlarm.value.lng, selectedAlarm.value.lat])
  const to = turf.point([nearest.lng, nearest.lat])
  const distance = turf.distance(from, to, { units: 'kilometers' })
  
  return {
    pump: nearest,
    distance: distance.toFixed(2)
  }
})

// ==================== Marker HTML 模板定义 ====================

// 防汛物资点 Marker 样式
const getResourceMarkerHTML = (resource: ResourcePoint) => {
  const statusColor = resource.status === '充足' ? '#10b981' : '#ef4444'
  return `
    <div class="custom-gis-marker resource-marker" style="background-color: ${statusColor};">
      <svg viewBox="0 0 24 24" width="16" height="16" stroke="white" stroke-width="2" fill="none">
        <path d="M21 10V21H3V10"/><path d="M23 8L12 2L1 8L12 14L23 8Z"/>
      </svg>
    </div>
  `
}

// 泵车驻地 Marker 样式
const getPumpMarkerHTML = (pump: PumpStation) => {
  const statusColor = pump.status === '空闲' ? '#3b82f6' : '#9ca3af'
  return `
    <div class="custom-gis-marker pump-marker" style="background-color: ${statusColor};">
      <svg viewBox="0 0 24 24" width="16" height="16" stroke="white" stroke-width="2" fill="none">
        <rect x="1" y="3" width="15" height="13" rx="2" ry="2"/>
        <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
        <circle cx="5.5" cy="18.5" r="2.5"/>
        <circle cx="18.5" cy="18.5" r="2.5"/>
      </svg>
    </div>
  `
}

// 报警事件 Marker 样式（带动画）
const getAlarmMarkerHTML = (alarm: AlarmEvent) => {
  let statusClass = 'pending'
  let iconSvg = `
    <svg viewBox="0 0 24 24" width="14" height="14" stroke="white" stroke-width="2.5" fill="none">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
      <line x1="12" y1="9" x2="12" y2="13"/>
      <line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
  `
  
  if (alarm.status === '处置中') {
    statusClass = 'processing'
    iconSvg = `
      <svg viewBox="0 0 24 24" width="14" height="14" stroke="white" stroke-width="2.5" fill="none">
        <rect x="1" y="3" width="15" height="13" rx="2" ry="2"/>
        <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
        <circle cx="5.5" cy="18.5" r="2.5"/>
        <circle cx="18.5" cy="18.5" r="2.5"/>
      </svg>
    `
  } else if (alarm.status === '已完成') {
    statusClass = 'completed'
    iconSvg = `
      <svg viewBox="0 0 24 24" width="14" height="14" stroke="white" stroke-width="2.5" fill="none">
        <polyline points="20 6 9 17 4 12"/>
      </svg>
    `
  }
  
  return `
    <div class="alarm-marker-wrapper alarm-${statusClass}">
      <div class="alarm-marker-pulse"></div>
      <div class="alarm-marker-core">
        ${iconSvg}
      </div>
    </div>
  `
}

// ==================== Marker 绘制逻辑 ====================

const clearMarkers = (markers: maplibregl.Marker[]) => {
  markers.forEach(m => m.remove())
}

// 绘制防汛物资点
const drawResourceMarkers = () => {
  const mapInst = map.value
  if (!mapInst) return
  clearMarkers(resourceMarkers)
  resourceMarkers = []
  
  store.resourceList.forEach(res => {
    const el = document.createElement('div')
    el.innerHTML = getResourceMarkerHTML(res)
    
    const popup = new maplibregl.Popup({ offset: 12 })
      .setHTML(`
        <div class="map-popup-card">
          <h4 class="popup-title">防汛物资点</h4>
          <div class="popup-item"><strong>名称:</strong><span>${escapeHtml(res.name)}</span></div>
          <div class="popup-item"><strong>物资储备:</strong><span>${escapeHtml(res.details)}</span></div>
          <div class="popup-item"><strong>地址:</strong><span>${escapeHtml(res.address)}</span></div>
          <div class="popup-item"><strong>状态:</strong><span class="badge ${res.status === '充足' ? 'green' : 'red'}">${res.status}</span></div>
        </div>
      `)
      
    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([res.lng, res.lat])
      .setPopup(popup)
      .addTo(mapInst)
      
    resourceMarkers.push(marker)
  })
}

// 绘制泵车驻地
const drawStationMarkers = () => {
  const mapInst = map.value
  if (!mapInst) return
  clearMarkers(stationMarkers)
  stationMarkers = []
  
  store.pumpStations.forEach(pump => {
    const el = document.createElement('div')
    el.innerHTML = getPumpMarkerHTML(pump)
    
    const popup = new maplibregl.Popup({ offset: 12 })
      .setHTML(`
        <div class="map-popup-card">
          <h4 class="popup-title">泵车驻地</h4>
          <div class="popup-item"><strong>名称:</strong><span>${escapeHtml(pump.name)}</span></div>
          <div class="popup-item"><strong>装备车辆:</strong><span>${escapeHtml(pump.vehicle)}</span></div>
          <div class="popup-item"><strong>负责人:</strong><span>${escapeHtml(pump.contact)}</span></div>
          <div class="popup-item"><strong>地址:</strong><span>${escapeHtml(pump.address)}</span></div>
          <div class="popup-item"><strong>状态:</strong><span class="badge ${pump.status === '空闲' ? 'blue' : 'gray'}">${pump.status}</span></div>
        </div>
      `)
      
    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([pump.lng, pump.lat])
      .setPopup(popup)
      .addTo(map.value!)
      
    stationMarkers.push(marker)
  })
}

// 绘制报警点 Markers
const drawAlarmMarkers = () => {
  const mapInst = map.value
  if (!mapInst) return
  clearMarkers(alarmMarkers)
  alarmMarkers = []
  
  store.alarmEvents.forEach(alarm => {
    const el = document.createElement('div')
    el.innerHTML = getAlarmMarkerHTML(alarm)
    
    // 点击 Marker 在面板中选中，并且地图做平移缩放
    el.addEventListener('click', (e) => {
      e.stopPropagation()
      store.selectedAlarmId = alarm.id
      mapInst.easeTo({
        center: [alarm.lng, alarm.lat],
        zoom: 14,
        duration: 800
      })
    })
    
    const severityText = alarm.severity === 'high' ? '高' : (alarm.severity === 'medium' ? '中' : '低')
    const severityClass = alarm.severity
    
    const popup = new maplibregl.Popup({ offset: 15 })
      .setHTML(`
        <div class="map-popup-card">
          <h4 class="popup-title">警情登记卡</h4>
          <div class="popup-item"><strong>报警人:</strong><span>${escapeHtml(alarm.reporter)} (${escapeHtml(maskPhone(alarm.phone))})</span></div>
          <div class="popup-item"><strong>严重程度:</strong><span class="badge ${severityClass}">${severityText}</span></div>
          <div class="popup-item"><strong>描述:</strong><span>${escapeHtml(alarm.description)}</span></div>
          <div class="popup-item"><strong>工单状态:</strong><span class="badge status-${alarm.status}">${alarm.status}</span></div>
          <div class="popup-item"><strong>报警时间:</strong><span>${alarm.time}</span></div>
        </div>
      `)
      
    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([alarm.lng, alarm.lat])
      .setPopup(popup)
      .addTo(mapInst)
      
    alarmMarkers.push(marker)
  })
}

// ==================== 智能路径规划与流光动画 ====================

// 启动粒子流光动画
const startRouteAnimation = (coordinates: [number, number][]) => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  
  const mapInst = map.value
  if (!mapInst) return
  
  const line = turf.lineString(coordinates)
  const routeLength = turf.length(line, { units: 'kilometers' })
  let progress = 0
  
  const animate = () => {
    const currentMapInst = map.value
    if (!currentMapInst) return
    
    // 每次更新流光粒子的当前距离进度，循环往复
    progress += routeLength / 100  // 控制粒子流动速度
    if (progress > routeLength) {
      progress = 0
    }
    
    const points: any[] = []
    
    // 渲染 5 个尾随衰减的圆形粒子，形成流光彗星效果
    for (let i = 0; i < 5; i++) {
      const dist = progress - (i * (routeLength / 18))
      if (dist >= 0) {
        const pt = turf.along(line, dist, { units: 'kilometers' })
        points.push({
          type: 'Feature',
          properties: {
            opacity: 1 - (i * 0.18),
            size: 6.5 - i
          },
          geometry: pt.geometry
        })
      }
    }
    
    const source = currentMapInst.getSource('route-particles-source')
    if (source) {
      (source as any).setData({
        type: 'FeatureCollection',
        features: points
      })
    }
    
    animationFrameId = requestAnimationFrame(animate)
  }
  
  animate()
}

// 移除地图路线渲染和流光定时器
const removeRouteFromMap = () => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }
  
  const mapInst = map.value
  if (!mapInst) return
  
  // 安全卸载图层和源
  if (mapInst.getLayer('route-particles-layer')) mapInst.removeLayer('route-particles-layer')
  if (mapInst.getLayer('route-line-core')) mapInst.removeLayer('route-line-core')
  if (mapInst.getLayer('route-line-bg')) mapInst.removeLayer('route-line-bg')
  
  if (mapInst.getSource('route-particles-source')) mapInst.removeSource('route-particles-source')
  if (mapInst.getSource('route-path-source')) mapInst.removeSource('route-path-source')
}

// 渲染线路和动画
const drawRouteOnMap = (coordinates: [number, number][]) => {
  const mapInst = map.value
  if (!mapInst) return
  
  const pathSourceId = 'route-path-source'
  const pathSource = mapInst.getSource(pathSourceId)
  
  const geojson = {
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'LineString',
      coordinates
    }
  } as any
  
  // 1. 如果路径源已存在，更新数据即可；否则添加新源与图层
  if (pathSource) {
    (pathSource as any).setData(geojson)
  } else {
    mapInst.addSource(pathSourceId, {
      type: 'geojson',
      data: geojson
    })
    
    // 宽大的半透明轨迹背景线
    mapInst.addLayer({
      id: 'route-line-bg',
      type: 'line',
      source: pathSourceId,
      paint: {
        'line-color': '#10b981',
        'line-width': 7,
        'line-opacity': 0.35
      },
      layout: {
        'line-join': 'round',
        'line-cap': 'round'
      }
    })
    
    // 中央亮色细轨迹线
    mapInst.addLayer({
      id: 'route-line-core',
      type: 'line',
      source: pathSourceId,
      paint: {
        'line-color': '#34d399',
        'line-width': 3.5,
        'line-opacity': 0.95
      },
      layout: {
        'line-join': 'round',
        'line-cap': 'round'
      }
    })
  }
  
  // 2. 如果流光粒子源已存在，重置即可；否则添加新粒子源与圆图层
  const particlesSourceId = 'route-particles-source'
  const particlesSource = mapInst.getSource(particlesSourceId)
  
  if (!particlesSource) {
    mapInst.addSource(particlesSourceId, {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: []
      }
    })
    
    mapInst.addLayer({
      id: 'route-particles-layer',
      type: 'circle',
      source: particlesSourceId,
      paint: {
        'circle-color': '#ffffff',
        'circle-radius': ['get', 'size'],
        'circle-opacity': ['get', 'opacity'],
        'circle-stroke-width': 1.5,
        'circle-stroke-color': '#059669',
        'circle-stroke-opacity': ['get', 'opacity']
      }
    })
  }
  
  // 3. 启动流光粒子循环渲染
  startRouteAnimation(coordinates)
}

// 自动调整地图视野以自适应完整路线，左侧预留边距以避开悬浮面板
const fitMapToRoute = (coordinates: [number, number][]) => {
  const mapInst = map.value
  if (!mapInst || coordinates.length === 0) return
  
  const bounds = coordinates.reduce((acc, coord) => {
    return [
      [Math.min(acc[0][0], coord[0]), Math.min(acc[0][1], coord[1])],
      [Math.max(acc[1][0], coord[0]), Math.max(acc[1][1], coord[1])]
    ]
  }, [[coordinates[0][0], coordinates[0][1]], [coordinates[0][0], coordinates[0][1]]])
  
  mapInst.fitBounds(bounds as any, {
    padding: { top: 90, bottom: 90, left: 390, right: 90 },
    maxZoom: 14,
    duration: 1000
  })
}

// ==================== 右键地图登记事件 ====================

const handleMapContextMenu = (e: any) => {
  if (mapStore.interactionMode !== 'idle') return
  mapStore.setInteractionMode('register-event')
  const { lng, lat } = e.lngLat
  registerForm.value.lng = Number(lng.toFixed(6))
  registerForm.value.lat = Number(lat.toFixed(6))
  registerForm.value.reporter = ''
  registerForm.value.phone = ''
  registerForm.value.description = ''
  registerForm.value.severity = 'medium'
  showRegisterModal.value = true
}

// 提交登记表单
const handleRegisterSubmit = async () => {
  if (!registerForm.value.reporter.trim()) {
    message.error('请输入报警人姓名')
    return
  }
  if (!/^\d{11}$/.test(registerForm.value.phone)) {
    message.error('请输入正确的11位手机号码')
    return
  }
  if (!registerForm.value.description.trim()) {
    message.error('请输入积水详情描述')
    return
  }
  
  actionLoading.value = true
  try {
    const newEvent = await store.addAlarmEvent({
      reporter: registerForm.value.reporter,
      phone: registerForm.value.phone,
      description: registerForm.value.description,
      severity: registerForm.value.severity,
      lng: registerForm.value.lng,
      lat: registerForm.value.lat
    })
    showRegisterModal.value = false
  
  // 提示用户并默认选中新工单
    notification.success({
      title: '警情登记成功',
      content: `工单编号 ${newEvent.id}，数据已写入服务器。`,
      duration: 3500
    })
  
    store.selectedAlarmId = newEvent.id
  
  // 地图视角定位
    const mapInst = map.value
    if (mapInst) {
      mapInst.easeTo({ center: [newEvent.lng, newEvent.lat], zoom: 14, duration: 1000 })
    }
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    actionLoading.value = false
  }
}

// ==================== 调度派遣与状态操作 ====================

// 派单
const handleDispatch = async (alarmId: string, pumpId: string) => {
  actionLoading.value = true
  try {
    await store.dispatchPump(alarmId, pumpId)
    message.success('应急调度指令已写入服务器，泵车已锁定！')
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    actionLoading.value = false
  }
}

// 智能一键派单（最邻近）
const handleSmartDispatch = async (alarm: AlarmEvent) => {
  if (!nearestPumpInfo.value) {
    message.warning('当前无可用泵车驻点')
    return
  }
  const pump = nearestPumpInfo.value.pump
  await handleDispatch(alarm.id, pump.id)
}

// 标记抵达现场
const handleMarkArrival = async (alarmId: string) => {
  actionLoading.value = true
  try {
    await store.markArrived(alarmId)
    message.info('已在服务器记录抢险队伍抵达现场')
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    actionLoading.value = false
  }
}

// 标记处置完成
const handleMarkCompleted = async (alarmId: string) => {
  actionLoading.value = true
  try {
    await store.updateEventStatus(alarmId, '已完成')
    message.success('险情处置完成，服务器已释放泵车资源！')
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    actionLoading.value = false
  }
}

// 定位资源点
const locateLocation = (lng: number, lat: number, name: string) => {
  const mapInst = map.value
  if (!mapInst) return
  mapInst.easeTo({
    center: [lng, lat],
    zoom: 14,
    duration: 800
  })
  message.info(`已定位至: ${name}`)
}

// ==================== 生命与监听周期 ====================

watch(isLoaded, (loaded) => {
  const mapInst = map.value
  if (loaded && mapInst) {
    drawResourceMarkers()
    drawStationMarkers()
    drawAlarmMarkers()
    
    bindContextMenu(mapInst)
  }
})

const preventDefaultContext = (e: Event) => e.preventDefault()

const bindContextMenu = (mapInst: maplibregl.Map) => {
  if (contextMenuBound) return
  mapInst.on('contextmenu', handleMapContextMenu)
  mapInst.getCanvas().addEventListener('contextmenu', preventDefaultContext)
  contextMenuBound = true
}

// 侦听报警列表和车辆信息变化，实时刷新地图 Markers 颜色状态
watch(() => store.alarmEvents, () => {
  drawAlarmMarkers()
}, { deep: true })

watch(() => store.pumpStations, () => {
  drawStationMarkers()
}, { deep: true })

watch(() => store.resourceList, () => {
  drawResourceMarkers()
}, { deep: true })

// 侦听选中的工单路线，以渲染路径或卸载动画
watch(() => {
  const active = store.alarmEvents.find(e => e.id === store.selectedAlarmId)
  return active ? { geom: active.routeGeometry, status: active.status } : null
}, (activeRoute) => {
  if (activeRoute && activeRoute.geom && activeRoute.geom.length > 0 && activeRoute.status === '处置中') {
    drawRouteOnMap(activeRoute.geom)
    fitMapToRoute(activeRoute.geom)
  } else {
    removeRouteFromMap()
  }
}, { deep: true })

onMounted(async () => {
  try {
    await store.refresh()
    store.connectWorkOrderUpdates()
  } catch (error) {
    message.error(`业务数据加载失败：${errorMessage(error)}`)
  }
  
  const mapInst = map.value
  if (isLoaded.value && mapInst) {
    drawResourceMarkers()
    drawStationMarkers()
    drawAlarmMarkers()
    
    bindContextMenu(mapInst)
  }
})

onUnmounted(() => {
  store.disconnectWorkOrderUpdates()
  mapStore.setInteractionMode('idle')
  const mapInst = map.value
  if (mapInst) {
    mapInst.off('contextmenu', handleMapContextMenu)
    mapInst.getCanvas().removeEventListener('contextmenu', preventDefaultContext)
    contextMenuBound = false
  }
  clearMarkers(resourceMarkers)
  resourceMarkers = []
  clearMarkers(stationMarkers)
  stationMarkers = []
  clearMarkers(alarmMarkers)
  alarmMarkers = []
  removeRouteFromMap()
})

watch(showRegisterModal, (visible) => {
  if (!visible && mapStore.interactionMode === 'register-event') mapStore.setInteractionMode('idle')
})
</script>

<template>
  <div id="emergency-dispatch-panel" class="gis-overlay-container">
    <!-- 应急调度主控制面板 -->
    <n-card id="emergency-dispatch-card" class="dispatch-panel" :bordered="false" size="small">
      <div class="panel-header">
        <h2 class="title panel-title-row"><n-icon :component="Waves" size="18" />应急指挥</h2>
      </div>
      
      <n-tabs v-model:value="activeTab" justify-content="space-evenly" type="line" animated class="tabs-container">
        <!-- 工单管理 Tab -->
        <n-tab-pane name="workorders">
          <template #tab><span class="tab-label"><n-icon :component="ClipboardList" size="15" />工单管理</span></template>
          <div class="tab-scroll-box" tabindex="0" aria-label="工单列表">
            <!-- 状态过滤 -->
            <div class="filter-wrapper">
              <n-space size="small">
                <n-button 
                  v-for="s in ['全部', '待派单', '处置中', '已完成'] as const" 
                  :key="s"
                  size="tiny" 
                  :secondary="statusFilter !== s"
                  :type="statusFilter === s ? 'primary' : 'default'"
                  @click="statusFilter = s"
                >
                  {{ s }}
                </n-button>
              </n-space>
            </div>

            <!-- 工单列表 -->
            <n-list hoverable clickable class="workorder-list">
              <n-list-item 
                v-for="alarm in filteredAlarms" 
                :key="alarm.id" 
                :class="{ 'is-selected': store.selectedAlarmId === alarm.id }"
                tabindex="0"
                role="button"
                @click="store.selectedAlarmId = alarm.id"
                @keydown.enter.space.prevent="store.selectedAlarmId = alarm.id"
              >
                <n-thing>
                  <template #header>
                    <span class="alarm-reporter">{{ alarm.reporter }} ({{ maskPhone(alarm.phone) }})</span>
                  </template>
                  <template #header-extra>
                    <n-space size="small">
                      <n-tag :type="alarm.severity === 'high' ? 'error' : (alarm.severity === 'medium' ? 'warning' : 'info')" size="small">
                        {{ alarm.severity === 'high' ? '严重' : (alarm.severity === 'medium' ? '中度' : '轻微') }}
                      </n-tag>
                      <n-tag :type="alarm.status === '待派单' ? 'error' : (alarm.status === '处置中' ? 'warning' : (alarm.status === '已完成' ? 'success' : 'default'))" size="small">
                        {{ alarm.status }}
                      </n-tag>
                    </n-space>
                  </template>
                  <template #description>
                    <div class="alarm-time"><n-icon :component="Clock3" size="12" />登记时间: {{ alarm.time }}</div>
                    <div class="alarm-desc">{{ alarm.description }}</div>
                  </template>

                  <!-- 选中时的智能调度子卡片 (手风琴展开详情效果) -->
                  <div v-if="store.selectedAlarmId === alarm.id" class="dispatch-details-card" @click.stop>
                    <n-divider style="margin: 8px 0" />
                    
                    <!-- 待派单状态：智能派车 -->
                    <div v-if="alarm.status === '待派单'" class="dispatch-action-box">
                      <div class="nearest-info" v-if="nearestPumpInfo">
                        <div class="info-label inline-icon-label"><n-icon :component="Zap" size="14" />智能匹配最近空闲驻地</div>
                        <div class="info-value">{{ nearestPumpInfo.pump.name }}</div>
                        <div class="info-meta">直线距离: <strong>{{ nearestPumpInfo.distance }} km</strong></div>
                      </div>
                      <n-space vertical style="width: 100%; margin-top: 10px;">
                        <n-button type="primary" size="small" block :loading="actionLoading" @click="handleSmartDispatch(alarm)">
                          <template #icon><n-icon :component="Route" /></template>
                          智能派遣最近泵车
                        </n-button>
                        <n-select 
                          size="small"
                          placeholder="手动选择其他驻地泵车"
                          :disabled="actionLoading"
                          :options="store.pumpStations.filter(p => p.status === '空闲').map(p => ({ label: p.name, value: p.id }))"
                          @update:value="(val) => handleDispatch(alarm.id, val)"
                        />
                      </n-space>
                    </div>

                    <!-- 处置中状态：状态流转及流光路线 -->
                    <div v-else-if="alarm.status === '处置中'" class="dispatch-action-box">
                      <div class="route-info" v-if="alarm.routeDistance">
                        <n-space justify="space-between">
                          <n-statistic label="预估里程" :value="((alarm.routeDistance || 0) / 1000).toFixed(1)">
                            <template #suffix>km</template>
                          </n-statistic>
                          <n-statistic label="预估行驶时间" :value="Math.ceil((alarm.routeDuration || 0) / 60)">
                            <template #suffix>分钟</template>
                          </n-statistic>
                        </n-space>
                        <div class="flow-tip"><span class="live-status-dot"></span>实时导航与路径状态监控中</div>
                      </div>
                      <n-space style="margin-top: 10px;" justify="space-between">
                        <n-button size="small" type="warning" :disabled="Boolean(alarm.arrivedAt)" :loading="actionLoading" @click="handleMarkArrival(alarm.id)">
                          <template #icon><n-icon :component="MapPin" /></template>
                          {{ alarm.arrivedAt ? '已抵达现场' : '抵达现场并抢险' }}
                        </n-button>
                        <n-button size="small" type="success" :loading="actionLoading" @click="handleMarkCompleted(alarm.id)">
                          <template #icon><n-icon :component="CircleCheck" /></template>
                          险情处置完成
                        </n-button>
                      </n-space>
                    </div>

                    <!-- 已完成状态：展示总结 -->
                    <div v-else-if="alarm.status === '已完成'" class="dispatch-action-box text-center">
                      <div class="completed-badge"><n-icon :component="CircleCheck" size="16" />警情处置完成</div>
                      <p class="completed-desc">排水队伍已归建，现场积水已退去。</p>
                    </div>
                  </div>
                </n-thing>
              </n-list-item>
              
              <div v-if="filteredAlarms.length === 0" style="padding: 30px 0;">
                <n-empty description="暂无该状态下的工单列表" />
              </div>
            </n-list>
          </div>
        </n-tab-pane>

        <!-- 抢险资源 Tab -->
        <n-tab-pane name="resources">
          <template #tab><span class="tab-label"><n-icon :component="Truck" size="15" />抢险资源</span></template>
          <div class="tab-scroll-box" tabindex="0" aria-label="抢险资源列表">
            <!-- 泵车驻地列表 -->
            <div class="section-title section-heading"><n-icon :component="Truck" size="15" />移动排水泵车驻地 <span class="section-count">{{ store.pumpStations.length }}</span></div>
            <n-list hoverable class="resource-item-list">
              <n-list-item v-for="pump in store.pumpStations" :key="pump.id">
                <n-thing>
                  <template #header>
                    <span class="res-title">{{ pump.name }}</span>
                  </template>
                  <template #header-extra>
                    <n-tag :type="pump.status === '空闲' ? 'success' : 'default'" size="small">
                      {{ pump.status }}
                    </n-tag>
                  </template>
                  <template #description>
                    <div class="res-desc"><span class="res-label">车辆</span>{{ pump.vehicle }}</div>
                    <div class="res-desc"><span class="res-label">联系</span>{{ pump.contact }}</div>
                    <div class="res-desc"><span class="res-label">地址</span>{{ pump.address }}</div>
                  </template>
                  <template #action>
                    <n-button size="tiny" type="info" secondary @click="locateLocation(pump.lng, pump.lat, pump.name)">
                      <template #icon><n-icon :component="LocateFixed" /></template>
                      定位驻地
                    </n-button>
                  </template>
                </n-thing>
              </n-list-item>
            </n-list>

            <n-divider style="margin: 16px 0" />

            <!-- 防汛物资点列表 -->
            <div class="section-title section-heading"><n-icon :component="Package" size="15" />应急防汛物资储备库 <span class="section-count">{{ store.resourceList.length }}</span></div>
            <n-list hoverable class="resource-item-list">
              <n-list-item v-for="res in store.resourceList" :key="res.id">
                <n-thing>
                  <template #header>
                    <span class="res-title">{{ res.name }}</span>
                  </template>
                  <template #header-extra>
                    <n-tag :type="res.status === '充足' ? 'success' : 'error'" size="small">
                      {{ res.status }}
                    </n-tag>
                  </template>
                  <template #description>
                    <div class="res-desc"><span class="res-label">物料</span>{{ res.details }}</div>
                    <div class="res-desc"><span class="res-label">地址</span>{{ res.address }}</div>
                  </template>
                  <template #action>
                    <n-button size="tiny" type="info" secondary @click="locateLocation(res.lng, res.lat, res.name)">
                      <template #icon><n-icon :component="LocateFixed" /></template>
                      定位物资点
                    </n-button>
                  </template>
                </n-thing>
              </n-list-item>
            </n-list>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- 右键弹出 Naive UI 报警登记表单 -->
    <n-modal 
      v-model:show="showRegisterModal" 
      preset="card" 
      class="register-modal"
      title="新增防汛积水灾情登记"
      style="width: 420px;" 
      :segmented="{ content: 'soft', footer: 'soft' }"
      @after-leave="mapStore.setInteractionMode('idle')"
    >
      <n-form :model="registerForm" :rules="rules" ref="formRef" size="medium" label-placement="left" label-width="85">
        <n-form-item label="坐标位置">
          <n-text code class="coordinate-text">
            LNG: {{ registerForm.lng }} / LAT: {{ registerForm.lat }}
          </n-text>
        </n-form-item>
        <n-form-item label="报告人" path="reporter">
          <n-input v-model:value="registerForm.reporter" placeholder="请输入报警人姓名" />
        </n-form-item>
        <n-form-item label="联系电话" path="phone">
          <n-input v-model:value="registerForm.phone" placeholder="请输入11位移动电话号码" />
        </n-form-item>
        <n-form-item label="灾情描述" path="description">
          <n-input 
            v-model:value="registerForm.description" 
            type="textarea" 
            placeholder="请详细描述该位置的积水高度、受阻程度等情况..." 
            :autosize="{ minRows: 2, maxRows: 4 }"
          />
        </n-form-item>
        <n-form-item label="严重程度" path="severity">
          <n-select 
            v-model:value="registerForm.severity" 
            :options="[
              { label: '严重｜车辆受淹、无法通行', value: 'high' },
              { label: '中度｜缓慢积水、影响交通', value: 'medium' },
              { label: '轻微｜轻微反水、不影响通行', value: 'low' }
            ]" 
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRegisterModal = false">取消</n-button>
          <n-button type="primary" @click="handleRegisterSubmit">确认提交登记</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
/* 悬浮面板基础布局 */
.gis-overlay-container {
  pointer-events: none; /* 让事件可以穿透到底图 */
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 2;
}

.dispatch-panel {
  pointer-events: auto; /* 面板本身恢复点击交互 */
  position: absolute;
  top: 20px;
  right: 20px;
  width: clamp(360px, 24vw, 430px);
  height: calc(100vh - 110px);
  max-height: 900px;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  box-shadow: var(--panel-shadow);
  background: var(--bg);
  border: 1px solid var(--border);
  transition: all 0.3s ease;
  overflow: hidden;
}

.dispatch-panel :deep(.n-card-content) {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
}

@media (min-width: 1800px) {
  .dispatch-panel {
    top: 24px;
    right: 24px;
  }
}

@media (max-width: 980px) {
  .dispatch-panel {
    top: 64px;
    right: 15px;
    width: calc(100vw - 30px);
    height: calc(100% - 79px);
    max-height: none;
  }
}

.panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.panel-header .title {
  margin: 0;
  font-size: 17px;
  font-weight: bold;
  color: var(--text-h);
}

.panel-title-row,
.tab-label,
.inline-icon-label,
.section-heading,
.completed-badge {
  display: flex;
  align-items: center;
  gap: 7px;
}

.panel-title-row :deep(.n-icon),
.section-heading :deep(.n-icon) {
  color: var(--primary-color);
}

.tabs-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:deep(.n-tabs-nav-scroll-content) {
  border-bottom: 1px solid var(--border);
}

:deep(.n-tab-pane) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tab-scroll-box {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 12px;
}

.tab-scroll-box:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}

/* 过滤列表部分 */
.filter-wrapper {
  margin-bottom: 10px;
}

.workorder-list {
  background: transparent;
}

/* 工单列表卡片 */
.workorder-list :deep(.n-list-item) {
  padding: 12px 10px;
  margin-bottom: 8px;
  border-radius: 6px;
  border: 1px solid var(--border);
  transition: all 0.2s ease;
  background-color: rgba(156, 163, 175, 0.05);
}

.workorder-list :deep(.n-list-item:hover) {
  border-color: var(--accent);
  background-color: rgba(170, 59, 255, 0.05);
}

.workorder-list :deep(.n-list-item.is-selected) {
  border-color: var(--accent);
  background-color: var(--accent-bg);
  box-shadow: 0 0 6px var(--accent-border);
}

.alarm-reporter {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-h);
}

.alarm-time {
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.alarm-desc {
  font-size: 13px;
  color: var(--text);
  line-height: 1.5;
}

/* 调度动作手风琴卡片 */
.dispatch-details-card {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 6px;
  border: 1px dashed var(--border);
}

.light-theme .dispatch-details-card {
  background: rgba(0, 0, 0, 0.03);
}

.nearest-info {
  font-size: 12.5px;
  line-height: 1.6;
}

.nearest-info .info-label {
  color: #6b7280;
}

.nearest-info .info-value {
  font-weight: bold;
  color: var(--text-h);
}

.nearest-info .info-meta {
  color: var(--text);
  margin-top: 2px;
}

.route-info {
  margin-bottom: 8px;
}

.route-info :deep(.n-statistic .n-statistic-value__content) {
  font-size: 18px;
  font-weight: bold;
}

.flow-tip {
  font-size: 11px;
  color: #10b981;
  margin-top: 6px;
  display: flex;
  align-items: center;
}

.live-status-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.12);
}

.completed-badge {
  font-size: 13px;
  font-weight: bold;
  color: #10b981;
  margin-bottom: 4px;
}

.completed-desc {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

/* 抢险资源分类列表 */
.section-title {
  font-size: 13px;
  font-weight: bold;
  color: var(--text-h);
  margin: 8px 0 12px 0;
  padding-left: 6px;
  border-left: 3px solid var(--accent);
}

.section-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 11px;
  text-align: center;
}

.resource-item-list :deep(.n-list-item) {
  padding: 10px 8px;
  margin-bottom: 6px;
  border-radius: 6px;
  background-color: rgba(156, 163, 175, 0.03);
  border: 1px solid var(--border);
}

.res-title {
  font-size: 13.5px;
  font-weight: bold;
  color: var(--text-h);
}

.res-desc {
  font-size: 12px;
  color: var(--text);
  margin-bottom: 2px;
}

.res-label {
  display: inline-block;
  width: 34px;
  margin-right: 6px;
  color: var(--text-secondary);
}

.coordinate-text {
  font-size: 13px;
  background-color: rgba(156, 163, 175, 0.1);
  padding: 4px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.text-center {
  text-align: center;
}
</style>

<!-- 地图 Marker 样式定义（注意是非 scoped 全局样式，以作用于动态注入 of DOM 节点） -->
<style>
.custom-gis-marker {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.45);
  cursor: pointer;
  border: 1.5px solid #ffffff;
  transition: transform 0.2s ease;
}

.custom-gis-marker:hover {
  transform: scale(1.18);
  z-index: 100;
}

.resource-marker {
  border-radius: 6px !important;
}

/* 报警事件 Marker 结构样式 */
.alarm-marker-wrapper {
  position: relative;
  width: 32px;
  height: 32px;
}

.alarm-marker-pulse {
  position: absolute;
  top: 0;
  left: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  pointer-events: none;
}

.alarm-marker-core {
  position: absolute;
  top: 4px;
  left: 4px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid #ffffff;
  box-shadow: 0 3px 8px rgba(0,0,0,0.4);
  cursor: pointer;
  transition: transform 0.2s ease;
}

.alarm-marker-core:hover {
  transform: scale(1.15);
}

/* 报警点不同状态的呼吸动效及配色 */
.alarm-pending .alarm-marker-pulse {
  background-color: rgba(239, 68, 68, 0.48);
  animation: alarm-ripple 1.3s infinite ease-out;
}
.alarm-pending .alarm-marker-core {
  background-color: #ef4444;
}

.alarm-processing .alarm-marker-pulse {
  background-color: rgba(245, 158, 11, 0.4);
  animation: alarm-ripple 1.8s infinite ease-out;
}
.alarm-processing .alarm-marker-core {
  background-color: #f59e0b;
}

.alarm-completed .alarm-marker-pulse {
  display: none;
}
.alarm-completed .alarm-marker-core {
  background-color: #10b981;
}

@keyframes alarm-ripple {
  0% {
    transform: scale(0.9);
    opacity: 0.95;
  }
  100% {
    transform: scale(2.4);
    opacity: 0;
  }
}
</style>
