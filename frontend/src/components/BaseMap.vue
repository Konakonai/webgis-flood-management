<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import * as turf from '@turf/turf'
import {
  NButton,
  NInput,
  NInputGroup,
  NSpace,
  NSlider,
  NSwitch,
  NSelect,
  NIcon,
  NTooltip,
  NBadge,
  NList,
  NListItem
} from 'naive-ui'
import {
  Sun,
  Moon,
  Search,
  Trash2,
  Layers,
  Ruler,
  Eye,
  EyeOff
} from 'lucide-vue-next'

import { useMap } from '../composables/useMap'
import { useTheme } from '../composables/useTheme'
import { useMapStore } from '../store/map'
import {
  xuzhouBoundary,
  pipeNetwork,
  waterStations,
  xuzhouLandmarks
} from '../mock/geojson'

const mapContainerRef = ref<HTMLDivElement | null>(null)
const mapStore = useMapStore()
const { registerLayer, toggleVisibility, updateOpacity, activeLayers } = useMap()
const { currentTheme, toggleTheme, isDark } = useTheme()

// 地图实例
let map: maplibregl.Map | null = null

// 地图底图配置
const currentBasemap = ref('tianditu-vec')

const basemapOptions = [
  { label: '天地图矢量 (推荐)', value: 'tianditu-vec' },
  { label: '天地图影像', value: 'tianditu-img' },
  { label: 'OpenStreetMap', value: 'osm' },
  { label: 'CartoDB 暗色底图', value: 'cartodb-dark' }
]

// 天地图 API Key 轮询池 (包含多个备用 Key 保证可用性)
const TIANDITU_KEYS = [
  '85542b8e390c5c7d0d0e74f37803e05a',
  '7a9a95781a7a030b42f6236fa7c20d78',
  '1d109683f4d84d998e1509157db6ee77',
  'b25752c002ee8109bf15664188b8fa8d'
]

const getTiandituKey = () => {
  const idx = Math.floor(Math.random() * TIANDITU_KEYS.length)
  return TIANDITU_KEYS[idx]
}

// 基础地图数据源定义
const OSM_SOURCE = {
  type: 'raster',
  tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
  tileSize: 256,
  attribution: '© OpenStreetMap 贡献者'
}

const CARTODB_DARK_SOURCE = {
  type: 'raster',
  tiles: ['https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png'],
  tileSize: 256,
  attribution: '© CartoDB'
}

const getTiandituVecSource = (key: string) => ({
  type: 'raster',
  tiles: [
    `https://t0.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`,
    `https://t1.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`,
    `https://t2.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`
  ],
  tileSize: 256,
  attribution: '© 天地图'
})

const getTiandituVecLabelSource = (key: string) => ({
  type: 'raster',
  tiles: [
    `https://t0.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`,
    `https://t1.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`
  ],
  tileSize: 256
})

const getTiandituImgSource = (key: string) => ({
  type: 'raster',
  tiles: [
    `https://t0.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`,
    `https://t1.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`
  ],
  tileSize: 256,
  attribution: '© 天地图'
})

const getTiandituImgLabelSource = (key: string) => ({
  type: 'raster',
  tiles: [
    `https://t0.tianditu.gov.cn/cia_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cia&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`,
    `https://t1.tianditu.gov.cn/cia_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cia&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${key}`
  ],
  tileSize: 256
})

// 地理编码搜索状态
const searchQuery = ref('')
const searchResults = ref<any[]>([])
const showSearchDropdown = ref(false)
let searchMarker: maplibregl.Marker | null = null

// 测距、测面状态
const measureMode = ref<'distance' | 'area' | null>(null)
const measurePoints = ref<[number, number][]>([])
const completedDrawings = ref<any[]>([])
let measureMarkers: maplibregl.Marker[] = []
let tempPopup: maplibregl.Popup | null = null

// 当前交互选择的管网或监测站详情
const activeDetail = ref<any | null>(null)

// 天地图 Key 切换与底图切换
const getFirstUserLayerId = (): string | undefined => {
  if (!map) return undefined
  const layers = map.getStyle().layers
  if (!layers) return undefined
  const firstUserLyr = layers.find(l => !l.id.startsWith('basemap-') && !l.id.startsWith('measure-'))
  return firstUserLyr ? firstUserLyr.id : undefined
}

const changeBasemap = (type: string) => {
  if (!map) return
  
  // 1. 清理已有底图
  const removeBasemap = (layerId: string, sourceId: string) => {
    if (map!.getLayer(layerId)) map!.removeLayer(layerId)
    if (map!.getSource(sourceId)) map!.removeSource(sourceId)
  }
  removeBasemap('basemap-raster-layer', 'basemap-raster-source')
  removeBasemap('basemap-label-layer', 'basemap-label-source')

  // 2. 找到第一个用户渲染层，把底图插在它下方，避免底图遮挡业务图层
  const beforeId = getFirstUserLayerId()
  const tk = getTiandituKey()

  if (type === 'osm') {
    map.addSource('basemap-raster-source', OSM_SOURCE as any)
    map.addLayer({
      id: 'basemap-raster-layer',
      type: 'raster',
      source: 'basemap-raster-source'
    }, beforeId)
  } else if (type === 'cartodb-dark') {
    map.addSource('basemap-raster-source', CARTODB_DARK_SOURCE as any)
    map.addLayer({
      id: 'basemap-raster-layer',
      type: 'raster',
      source: 'basemap-raster-source'
    }, beforeId)
  } else if (type === 'tianditu-vec') {
    map.addSource('basemap-raster-source', getTiandituVecSource(tk) as any)
    map.addLayer({
      id: 'basemap-raster-layer',
      type: 'raster',
      source: 'basemap-raster-source'
    }, beforeId)

    map.addSource('basemap-label-source', getTiandituVecLabelSource(tk) as any)
    map.addLayer({
      id: 'basemap-label-layer',
      type: 'raster',
      source: 'basemap-label-source'
    }, beforeId)
  } else if (type === 'tianditu-img') {
    map.addSource('basemap-raster-source', getTiandituImgSource(tk) as any)
    map.addLayer({
      id: 'basemap-raster-layer',
      type: 'raster',
      source: 'basemap-raster-source'
    }, beforeId)

    map.addSource('basemap-label-source', getTiandituImgLabelSource(tk) as any)
    map.addLayer({
      id: 'basemap-label-layer',
      type: 'raster',
      source: 'basemap-label-source'
    }, beforeId)
  }

  currentBasemap.value = type
}

// 主题切换同步底图
watch(currentTheme, (newTheme) => {
  if (newTheme === 'dark') {
    changeBasemap('cartodb-dark')
  } else {
    changeBasemap('tianditu-vec')
  }
})

// 初始化地图
onMounted(() => {
  if (!mapContainerRef.value) return

  // 实例化 MapLibre 实例
  map = new maplibregl.Map({
    container: mapContainerRef.value,
    center: [117.1848, 34.2618], // 徐州市中心
    zoom: 11.2,
    pitch: 0,
    style: {
      version: 8,
      sources: {},
      layers: []
    },
    doubleClickZoom: true
  })

  // 注册到全局 Store
  mapStore.setMapInstance(map)

  // 添加导航控件
  map.addControl(new maplibregl.NavigationControl({ showCompass: true }), 'bottom-right')

  map.on('load', () => {
    mapStore.setMapLoaded(true)
    
    // 1. 初始化底图 (根据当前主题初始化底图)
    const initialBasemap = currentTheme.value === 'dark' ? 'cartodb-dark' : 'tianditu-vec'
    changeBasemap(initialBasemap)

    // 2. 初始化测量图形的临时期源和图层
    map!.addSource('measure-source', {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: []
      }
    })

    // 填充面图层 (测面)
    map!.addLayer({
      id: 'measure-fill-layer',
      type: 'fill',
      source: 'measure-source',
      paint: {
        'fill-color': '#ff4d4f',
        'fill-opacity': 0.25
      },
      filter: ['==', '$type', 'Polygon']
    })

    // 线条图层 (测距/测面边框)
    map!.addLayer({
      id: 'measure-line-layer',
      type: 'line',
      source: 'measure-source',
      paint: {
        'line-color': '#ff4d4f',
        'line-width': 3,
        'line-dasharray': [3, 2]
      },
      filter: ['==', '$type', 'LineString']
    })

    // 顶点圆点图层
    map!.addLayer({
      id: 'measure-points-layer',
      type: 'circle',
      source: 'measure-source',
      paint: {
        'circle-radius': 6,
        'circle-color': '#ffffff',
        'circle-stroke-width': 2.5,
        'circle-stroke-color': '#ff4d4f'
      },
      filter: ['==', '$type', 'Point']
    })

    // 3. 动态注册徐州边界、雨水管网、监控站点图层
    // 徐州边界层
    registerLayer('xuzhou-boundary', {
      name: '徐州市核心行政边界',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: xuzhouBoundary
      },
      layers: [
        {
          id: 'xuzhou-boundary-fill',
          type: 'fill',
          paint: {
            'fill-color': '#8a2be2',
            'fill-opacity': 0.12
          }
        },
        {
          id: 'xuzhou-boundary-line',
          type: 'line',
          paint: {
            'line-color': '#8a2be2',
            'line-width': 2.5,
            'line-dasharray': [5, 3]
          }
        }
      ]
    })

    // 市政管网层
    registerLayer('pipe-network', {
      name: '模拟排水雨水管网',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: pipeNetwork
      },
      layers: [
        {
          id: 'pipe-network-line',
          type: 'line',
          paint: {
            'line-color': [
              'match',
              ['get', 'status'],
              '正常', '#18a058',
              '预警', '#f0a020',
              '超负荷', '#d03050',
              '#2080f0'
            ],
            'line-width': 4.5
          }
        }
      ]
    })

    // 监控站点层
    registerLayer('water-stations', {
      name: '防汛监控/积水站点',
      type: 'geojson',
      source: {
        type: 'geojson',
        data: waterStations
      },
      layers: [
        {
          id: 'water-stations-circle',
          type: 'circle',
          paint: {
            'circle-radius': 9,
            'circle-color': [
              'match',
              ['get', 'status'],
              '正常', '#18a058',
              '预警', '#f0a020',
              '超警戒', '#d03050',
              '#2080f0'
            ],
            'circle-stroke-width': 2,
            'circle-stroke-color': '#ffffff'
          }
        }
      ]
    })

    // 4. 初始化地图点击监听与鼠标悬浮手势
    initMapEvents()
  })
})

onUnmounted(() => {
  if (map) {
    map.remove()
    map = null
  }
})

// 地图点击与交互处理
const initMapEvents = () => {
  if (!map) return

  // 鼠标悬停变手势
  const hoverLayers = ['water-stations-circle', 'pipe-network-line', 'xuzhou-boundary-fill']
  hoverLayers.forEach(layerId => {
    map!.on('mouseenter', layerId, () => {
      if (measureMode.value) return // 测量模式下保持绘图状态
      map!.getCanvas().style.cursor = 'pointer'
    })
    map!.on('mouseleave', layerId, () => {
      map!.getCanvas().style.cursor = ''
    })
  })

  // 监控站点点击
  map.on('click', 'water-stations-circle', (e) => {
    if (measureMode.value) return
    const features = map!.queryRenderedFeatures(e.point, { layers: ['water-stations-circle'] })
    if (features.length > 0) {
      showFeaturePopup(features[0].properties, e.lngLat, 'station')
    }
  })

  // 排水管线点击
  map.on('click', 'pipe-network-line', (e) => {
    if (measureMode.value) return
    // 优先响应监控站点点击
    const stations = map!.queryRenderedFeatures(e.point, { layers: ['water-stations-circle'] })
    if (stations.length > 0) return

    const features = map!.queryRenderedFeatures(e.point, { layers: ['pipe-network-line'] })
    if (features.length > 0) {
      showFeaturePopup(features[0].properties, e.lngLat, 'pipe')
    }
  })

  // 行政边界点击
  map.on('click', 'xuzhou-boundary-fill', (e) => {
    if (measureMode.value) return
    // 优先响应站点和管网点击
    const stations = map!.queryRenderedFeatures(e.point, { layers: ['water-stations-circle'] })
    const pipes = map!.queryRenderedFeatures(e.point, { layers: ['pipe-network-line'] })
    if (stations.length > 0 || pipes.length > 0) return

    const features = map!.queryRenderedFeatures(e.point, { layers: ['xuzhou-boundary-fill'] })
    if (features.length > 0) {
      showFeaturePopup(features[0].properties, e.lngLat, 'boundary')
    }
  })

  // 测距/测面交互：地图点击事件
  map.on('click', (e) => {
    if (!measureMode.value) return

    // 禁用双击缩放，防止双击结束绘制时放大地图
    map!.doubleClickZoom.disable()

    const pt: [number, number] = [e.lngLat.lng, e.lngLat.lat]
    measurePoints.value.push(pt)

    // 在点击点绘制一个端点 Marker，显示临时数据
    updateMeasureDrawing()
    createMeasurePointMarker(pt)
  })

  // 测距/测面交互：鼠标移动（橡皮筋效果）
  map.on('mousemove', (e) => {
    if (!measureMode.value || measurePoints.value.length === 0) return
    const mousePt: [number, number] = [e.lngLat.lng, e.lngLat.lat]
    updateMeasureDrawing(mousePt)
  })

  // 测距/测面交互：双击结束
  map.on('dblclick', (e) => {
    if (!measureMode.value) return
    e.preventDefault()
    
    // 双击会额外产生两个点，我们需要清理一下
    // 通常倒数第一、第二点跟双击点重合，去除重合点
    if (measurePoints.value.length > 1) {
      measurePoints.value.pop()
    }
    
    finishMeasurement()
  })
}

// 弹出自定义 HTML popup 展示要素详细信息
const showFeaturePopup = (props: any, lngLat: maplibregl.LngLat, type: 'station' | 'pipe' | 'boundary') => {
  if (!map) return

  let contentHtml = ''
  const isDarkTheme = currentTheme.value === 'dark'
  const textColor = isDarkTheme ? '#f3f4f6' : '#1f2225'
  const borderCol = isDarkTheme ? '#3f444e' : '#e0e0e0'

  if (type === 'station') {
    const badgeBg = props.status === '正常' ? '#18a058' : props.status === '预警' ? '#f0a020' : '#d03050'
    contentHtml = `
      <div style="font-family: sans-serif; min-width: 250px; color:${textColor}">
        <h4 style="margin: 0 0 10px 0; border-bottom: 2px solid ${badgeBg}; padding-bottom: 6px; font-size: 14px; font-weight: 600; display: flex; justify-content: space-between; align-items: center;">
          <span>${props.name}</span>
          <span style="font-size: 11px; background:${badgeBg}; color:#fff; padding:2px 8px; border-radius: 4px; font-weight: normal;">${props.status}</span>
        </h4>
        <div style="display:flex; flex-direction:column; gap:6px; font-size: 12px; line-height: 1.5;">
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">点位类型:</span><strong>${props.type}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">当前数值:</span><strong style="color:${props.status !== '正常' ? '#d03050' : 'inherit'}">${props.waterLevel}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">警戒水位:</span><strong>${props.warningLevel}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">排水流量:</span><strong>${props.flowRate}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">位置地址:</span><span style="text-align:right; max-width: 150px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" title="${props.address}">${props.address}</span></div>
          <div style="margin-top:6px; padding-top:6px; border-top:1px solid ${borderCol}; font-size:11px; color:#8c8c8c; display:flex; justify-content:space-between;">
            <span>更新时间:</span>
            <span>${props.time}</span>
          </div>
        </div>
      </div>
    `
  } else if (type === 'pipe') {
    const statusBg = props.status === '正常' ? '#18a058' : props.status === '预警' ? '#f0a020' : '#d03050'
    contentHtml = `
      <div style="font-family: sans-serif; min-width: 230px; color:${textColor}">
        <h4 style="margin: 0 0 10px 0; border-bottom: 2px solid ${statusBg}; padding-bottom: 6px; font-size: 14px; font-weight: 600; display:flex; justify-content:space-between; align-items:center;">
          <span>${props.street}</span>
          <span style="font-size: 11px; background:${statusBg}; color:#fff; padding:2px 8px; border-radius: 4px; font-weight: normal;">管网${props.status}</span>
        </h4>
        <div style="display:flex; flex-direction:column; gap:6px; font-size: 12px; line-height: 1.5;">
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">管道编号:</span><strong style="font-family:monospace">${props.id}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">管径大小:</span><strong>${props.diameter}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">管道材质:</span><strong>${props.material}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">埋设深度:</span><strong>${props.depth}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">当前流量:</span><strong>${props.flowRate}</strong></div>
        </div>
      </div>
    `
  } else if (type === 'boundary') {
    contentHtml = `
      <div style="font-family: sans-serif; min-width: 220px; color:${textColor}">
        <h4 style="margin: 0 0 10px 0; border-bottom: 2px solid #8a2be2; padding-bottom: 6px; font-size: 14px; font-weight: 600; color: #8a2be2;">${props.name}</h4>
        <div style="display:flex; flex-direction:column; gap:6px; font-size: 12px; line-height: 1.5;">
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">行政代码:</span><strong>${props.code}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">核心面积:</span><strong>${props.area}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">辐射人口:</span><strong>${props.population}</strong></div>
          <div style="display:flex; justify-content:space-between;"><span style="color:#8c8c8c">管理中心:</span><strong style="color:#8a2be2">${props.manager}</strong></div>
        </div>
      </div>
    `
  }

  // 关闭上一个详情面板，绑定新弹窗
  activeDetail.value = { type, properties: props }

  new maplibregl.Popup({
    className: 'maplibre-theme-popup',
    maxWidth: '300px',
    anchor: 'bottom',
    offset: 12
  })
    .setLngLat(lngLat)
    .setHTML(contentHtml)
    .addTo(map)
}

// ======================== 地理编码搜索 (Geocoding) ========================
let debounceTimer: any = null

const onSearchInput = (value: string) => {
  searchQuery.value = value
  if (!value.trim()) {
    searchResults.value = []
    showSearchDropdown.value = false
    return
  }

  showSearchDropdown.value = true

  // 1. 本地徐州高危/核心地标快速搜索 (秒响应，保证无网/故障时可用)
  const localResults = xuzhouLandmarks
    .filter(item => item.name.includes(value) || item.address.includes(value))
    .map(item => ({
      ...item,
      source: '本地数据',
      score: 100
    }))
  
  searchResults.value = localResults

  // 2. 远程接口检索防抖 (Nominatim OSM 搜索)
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    try {
      // 限制关键字在徐州市内
      const queryUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent('徐州 ' + value)}&format=json&limit=6&addressdetails=1`
      const response = await fetch(queryUrl, {
        headers: {
          'Accept-Language': 'zh-CN,zh;q=0.9'
        }
      })
      if (response.ok) {
        const data = await response.json()
        const remoteResults = data.map((item: any) => {
          // 截断超长的 display_name 为短地名
          const parts = item.display_name.split(',')
          const shortName = parts[0]
          return {
            name: shortName,
            address: item.display_name,
            lng: parseFloat(item.lon),
            lat: parseFloat(item.lat),
            type: item.type || '地点',
            source: 'OSM 接口'
          }
        })

        // 合并数据并排重 (经纬度过于相近的判定为同一位置)
        const combined = [...searchResults.value]
        remoteResults.forEach((rm: any) => {
          const isExist = combined.some(
            lm => Math.abs(lm.lng - rm.lng) < 0.0005 && Math.abs(lm.lat - rm.lat) < 0.0005
          )
          if (!isExist) {
            combined.push(rm)
          }
        })
        searchResults.value = combined
      }
    } catch (err) {
      console.warn('Geocoding search error, fallback to local data:', err)
    }
  }, 350)
}

const selectSearchResult = (item: any) => {
  if (!map) return
  showSearchDropdown.value = false
  searchQuery.value = item.name

  // 1. 地图平移飞入
  map.flyTo({
    center: [item.lng, item.lat],
    zoom: 14.5,
    speed: 1.2,
    curve: 1.4,
    essential: true
  })

  // 2. 移除旧 Marker 并创建新 Marker
  if (searchMarker) {
    searchMarker.remove()
  }

  const el = document.createElement('div')
  el.className = 'search-pin-marker'
  el.style.width = '24px'
  el.style.height = '24px'
  el.innerHTML = `
    <svg viewBox="0 0 24 24" width="24" height="24" stroke="#ff4d4f" stroke-width="2.5" fill="#ff4d4f" style="animation: bounce 1.2s infinite alternate;">
      <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
      <circle cx="12" cy="10" r="3" fill="#ffffff"></circle>
    </svg>
  `

  searchMarker = new maplibregl.Marker({ element: el })
    .setLngLat([item.lng, item.lat])
    .setPopup(
      new maplibregl.Popup({ offset: 12, className: 'maplibre-theme-popup' })
        .setHTML(`
          <div style="font-size:12px; line-height:1.4; min-width: 160px; color:${currentTheme.value === 'dark' ? '#f3f4f6' : '#1f2225'}">
            <h4 style="margin:0 0 4px; color:#ff4d4f; font-size:13px;">${item.name}</h4>
            <div style="color:#666; margin-bottom: 4px;">${item.address}</div>
            <div style="font-size:10px; color:#9c9c9c; display:flex; justify-content:space-between;">
              <span>数据来源: ${item.source}</span>
              <span>[${item.lng.toFixed(4)}, ${item.lat.toFixed(4)}]</span>
            </div>
          </div>
        `)
    )
    .addTo(map)

  // 延迟自动弹出 popup
  setTimeout(() => {
    searchMarker?.togglePopup()
  }, 300)
}

const clearSearch = () => {
  searchQuery.value = ''
  searchResults.value = []
  showSearchDropdown.value = false
  if (searchMarker) {
    searchMarker.remove()
    searchMarker = null
  }
}

// ======================== Turf.js 测距/测面工具 ========================
const startMeasure = (type: 'distance' | 'area') => {
  clearMeasurement()
  measureMode.value = type
  
  if (map) {
    // 禁用双击缩放
    map.doubleClickZoom.disable()
  }
}

// 更新正在绘制的图形
const updateMeasureDrawing = (mousePt?: [number, number]) => {
  if (!map) return

  const source = map.getSource('measure-source') as maplibregl.GeoJSONSource
  if (!source) return

  const pts = [...measurePoints.value]
  if (mousePt) {
    pts.push(mousePt)
  }

  const features: any[] = []

  // 收集已完成的测量结果
  completedDrawings.value.forEach(feat => {
    features.push(feat)
  })

  // 渲染正在绘制的要素
  if (pts.length > 0) {
    // 1. 绘制点
    pts.forEach(p => {
      features.push({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: p
        },
        properties: {}
      })
    })

    // 2. 绘制线
    if (pts.length >= 2) {
      features.push({
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: pts
        },
        properties: {}
      })
    }

    // 3. 绘制面 (若测面模式，且点数大于等于3，可以构建闭合线/面)
    if (measureMode.value === 'area' && pts.length >= 3) {
      features.push({
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [[...pts, pts[0]]]
        },
        properties: {}
      })
    }
  }

  source.setData({
    type: 'FeatureCollection',
    features
  })

  // 实时显示测量数值
  if (pts.length >= 2 && mousePt) {
    showLiveMeasureLabel(pts)
  }
}

// 实时显示正在绘制过程中的总长度或面积信息
const showLiveMeasureLabel = (pts: [number, number][]) => {
  if (!map) return

  const lastPt = pts[pts.length - 1]
  let displayHtml = ''

  if (measureMode.value === 'distance') {
    const line = turf.lineString(pts)
    const lengthVal = turf.length(line, { units: 'kilometers' })
    displayHtml = `
      <div class="measure-tooltip" style="background:#ff4d4f; color:#fff; padding:3px 8px; border-radius:4px; font-size:11px; font-weight:bold; box-shadow:0 2px 6px rgba(0,0,0,0.3);">
        总长度: ${lengthVal.toFixed(2)} km <span style="font-weight:normal; font-size:9px; opacity:0.8;">(双击结束)</span>
      </div>
    `
  } else if (measureMode.value === 'area' && pts.length >= 3) {
    try {
      const poly = turf.polygon([[...pts, pts[0]]])
      const areaVal = turf.area(poly) / 1000000 // m² -> km²
      displayHtml = `
        <div class="measure-tooltip" style="background:#ff4d4f; color:#fff; padding:3px 8px; border-radius:4px; font-size:11px; font-weight:bold; box-shadow:0 2px 6px rgba(0,0,0,0.3);">
          总面积: ${areaVal.toFixed(2)} km² <span style="font-weight:normal; font-size:9px; opacity:0.8;">(双击结束)</span>
        </div>
      `
    } catch(e) {}
  }

  if (displayHtml) {
    if (!tempPopup) {
      tempPopup = new maplibregl.Popup({
        closeButton: false,
        closeOnClick: false,
        className: 'measure-live-popup',
        offset: 15
      })
    }
    tempPopup.setLngLat(lastPt).setHTML(displayHtml).addTo(map as any)
  }
}

// 为测量中的顶点画个小圆圈标注
const createMeasurePointMarker = (pt: [number, number]) => {
  if (!map) return
  
  const el = document.createElement('div')
  el.style.width = '6px'
  el.style.height = '6px'
  el.style.background = '#ffffff'
  el.style.border = '2.5px solid #ff4d4f'
  el.style.borderRadius = '50%'
  
  const m = new maplibregl.Marker({ element: el })
    .setLngLat(pt)
    .addTo(map as any)
    
  measureMarkers.push(m)
}

// 完成测量
const finishMeasurement = () => {
  if (!map || measurePoints.value.length < 2) {
    clearMeasurement()
    return
  }

  const pts = [...measurePoints.value]
  let resultText = ''
  let labelPosition: [number, number] = pts[pts.length - 1]

  if (measureMode.value === 'distance') {
    const line = turf.lineString(pts)
    const lengthVal = turf.length(line, { units: 'kilometers' })
    resultText = `距离: ${lengthVal.toFixed(3)} km`
    
    // 保存已完成要素
    completedDrawings.value.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates: pts
      },
      properties: {}
    })
  } else if (measureMode.value === 'area') {
    if (pts.length < 3) {
      clearMeasurement()
      return
    }
    const poly = turf.polygon([[...pts, pts[0]]])
    const areaVal = turf.area(poly) / 1000000
    resultText = `面积: ${areaVal.toFixed(3)} km²`
    
    // 面中心放置标注
    const center = turf.centroid(poly)
    labelPosition = center.geometry.coordinates as [number, number]

    completedDrawings.value.push({
      type: 'Feature',
      geometry: {
        type: 'Polygon',
        coordinates: [[...pts, pts[0]]]
      },
      properties: {}
    })
  }

  // 移除实时跟随的临时 Popup
  if (tempPopup) {
    tempPopup.remove()
    tempPopup = null
  }

  // 创建永久结果展示 Popup
  const finalPopup = new maplibregl.Popup({
    closeButton: true,
    closeOnClick: false,
    className: 'measure-result-popup',
    offset: 10
  })
    .setLngLat(labelPosition)
    .setHTML(`
      <div style="padding: 4px 8px; font-weight: bold; font-size:12px; color:#cf1322; background:#fff1f0; border: 1px solid #ffa39e; border-radius: 4px;">
        ${resultText}
      </div>
    `)
    .addTo(map as any)

  // 记录到 markers 列表以便后续一键清空
  const dummyEl = document.createElement('div')
  const popupWrapperMarker = new maplibregl.Marker({ element: dummyEl })
    .setLngLat(labelPosition)
    .addTo(map as any)
  
  // 绑定 Popup 的关闭事件，使手动关闭时也能被管理
  finalPopup.on('close', () => {
    popupWrapperMarker.remove()
  })
  
  measureMarkers.push(popupWrapperMarker)

  // 重置临时绘图状态
  measurePoints.value = []
  measureMode.value = null
  map.doubleClickZoom.enable()

  // 刷新最终在图层上保持的形状
  updateMeasureDrawing()
}

// 清除测量数据
const clearMeasurement = () => {
  measureMode.value = null
  measurePoints.value = []
  completedDrawings.value = []
  
  // 清理所有 Marker 和 Popup
  measureMarkers.forEach(m => m.remove())
  measureMarkers = []

  if (tempPopup) {
    tempPopup.remove()
    tempPopup = null
  }

  const popups = document.querySelectorAll('.measure-result-popup, .measure-live-popup')
  popups.forEach(el => el.remove())

  // 重置 GeoJSON 数据源
  if (map && map.getSource('measure-source')) {
    const source = map.getSource('measure-source') as maplibregl.GeoJSONSource
    source.setData({
      type: 'FeatureCollection',
      features: []
    })
    map.doubleClickZoom.enable()
  }
}
</script>

<template>
  <div class="webgis-layout" :class="currentTheme + '-theme'">
    <!-- 地图主视口 -->
    <div ref="mapContainerRef" class="map-viewport"></div>

    <!-- 1. 左上角：地理编码快速检索面板 -->
    <div class="search-panel floating-card">
      <n-input-group>
        <n-input
          :value="searchQuery"
          @input="onSearchInput"
          placeholder="搜索徐州地标、路网或积水点..."
          clearable
          @clear="clearSearch"
          style="width: 280px;"
          size="medium"
        >
          <template #prefix>
            <n-icon><Search /></n-icon>
          </template>
        </n-input>
        <n-button type="primary" secondary size="medium">检索</n-button>
      </n-input-group>

      <!-- 搜索下拉结果集 -->
      <div v-if="showSearchDropdown && searchResults.length > 0" class="search-results-list">
        <n-list hoverable clickable>
          <n-list-item
            v-for="(item, idx) in searchResults"
            :key="idx"
            @click="selectSearchResult(item)"
            class="search-item-row"
          >
            <div class="search-item-title">{{ item.name }}</div>
            <div class="search-item-address">{{ item.address }}</div>
            <div class="search-item-meta">
              <n-badge :value="item.source" type="info" size="small" />
              <span class="meta-coords">{{ item.lng.toFixed(4) }}, {{ item.lat.toFixed(4) }}</span>
            </div>
          </n-list-item>
        </n-list>
      </div>
    </div>

    <!-- 2. 右上角：工具控制条 (底图切换、主题切换、量测面板开关) -->
    <div class="toolbar-panel floating-card">
      <n-space size="small" align="center">
        <!-- 快速主题切换按钮 -->
        <n-tooltip trigger="hover" placement="bottom">
          <template #trigger>
            <n-button @click="toggleTheme" circle secondary strong size="medium">
              <template #icon>
                <n-icon>
                  <Sun v-if="isDark" />
                  <Moon v-else />
                </n-icon>
              </template>
            </n-button>
          </template>
          切换{{ isDark ? '浅色' : '深色' }}主题
        </n-tooltip>

        <!-- 底图瓦片切换下拉选择器 -->
        <div class="basemap-selector-container">
          <n-select
            :value="currentBasemap"
            @update:value="changeBasemap"
            :options="basemapOptions"
            size="medium"
            style="width: 160px;"
          />
        </div>
      </n-space>
    </div>

    <!-- 3. 右侧中部：防汛图层可视化管理面板 (图层显隐、透明度控制) -->
    <div class="layer-control-panel floating-card">
      <div class="panel-header">
        <n-icon><Layers /></n-icon>
        <span class="panel-title">图层控制面板</span>
      </div>

      <div class="layer-list">
        <div v-for="layer in activeLayers" :key="layer.id" class="layer-card">
          <div class="layer-main-row">
            <span class="layer-name">{{ layer.name }}</span>
            <n-switch
              :value="layer.visible"
              @update:value="(val) => toggleVisibility(layer.id, val)"
              size="small"
            >
              <template #checked-icon>
                <n-icon><Eye /></n-icon>
              </template>
              <template #unchecked-icon>
                <n-icon><EyeOff /></n-icon>
              </template>
            </n-switch>
          </div>
          <!-- 透明度滑动条 -->
          <div class="layer-opacity-row">
            <span class="opacity-label">不透明度</span>
            <n-slider
              :value="layer.opacity"
              @update:value="(val) => updateOpacity(layer.id, val)"
              :min="0"
              :max="1"
              :step="0.05"
              style="width: 100px;"
            />
            <span class="opacity-value">{{ Math.round(layer.opacity * 100) }}%</span>
          </div>
        </div>
        <div v-if="activeLayers.length === 0" class="empty-layers">
          暂无可控业务图层
        </div>
      </div>
    </div>

    <!-- 4. 底部偏左：量测小工具面板 -->
    <div class="measure-panel floating-card">
      <div class="measure-header">
        <n-icon><Ruler /></n-icon>
        <span class="measure-title">地图量测工具</span>
      </div>
      <n-space size="small">
        <n-button
          :type="measureMode === 'distance' ? 'error' : 'default'"
          secondary
          size="small"
          @click="startMeasure('distance')"
        >
          测距 (km)
        </n-button>
        <n-button
          :type="measureMode === 'area' ? 'error' : 'default'"
          secondary
          size="small"
          @click="startMeasure('area')"
        >
          测面 (km²)
        </n-button>
        <n-button
          v-if="measureMode"
          type="warning"
          size="small"
          @click="finishMeasurement"
        >
          完成
        </n-button>
        <n-button
          type="tertiary"
          size="small"
          @click="clearMeasurement"
        >
          <template #icon>
            <n-icon><Trash2 /></n-icon>
          </template>
          清空
        </n-button>
      </n-space>
      <div v-if="measureMode" class="measure-tip">
        <span class="pulse-dot"></span>
        正在进行{{ measureMode === 'distance' ? '线段测距' : '多边形测面' }}：鼠标左键点击绘点，双击或点击【完成】结束。
      </div>
    </div>

    <!-- 5. 底部右侧：要素点击交互展示区 (副面板) -->
    <div v-if="activeDetail" class="detail-sidebar floating-card">
      <div class="detail-header">
        <span class="detail-title">要素属性详情</span>
        <n-button size="tiny" circle @click="activeDetail = null">
          ✕
        </n-button>
      </div>
      <div class="detail-body">
        <!-- 监控站点展示 -->
        <template v-if="activeDetail.type === 'station'">
          <div class="detail-row">
            <span class="label">站点名称</span>
            <span class="value font-bold">{{ activeDetail.properties.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">当前水位/积水</span>
            <span class="value text-warning font-bold">{{ activeDetail.properties.waterLevel }}</span>
          </div>
          <div class="detail-row">
            <span class="label">警戒水位/上限</span>
            <span class="value">{{ activeDetail.properties.warningLevel }}</span>
          </div>
          <div class="detail-row">
            <span class="label">水情状态</span>
            <span class="value">
              <n-badge :value="activeDetail.properties.status" :type="activeDetail.properties.status === '正常' ? 'success' : 'error'" />
            </span>
          </div>
          <div class="detail-row">
            <span class="label">数据更新时间</span>
            <span class="value date">{{ activeDetail.properties.time }}</span>
          </div>
          <div class="detail-row">
            <span class="label">监测点地址</span>
            <span class="value desc">{{ activeDetail.properties.address }}</span>
          </div>
        </template>

        <!-- 排水管段展示 -->
        <template v-if="activeDetail.type === 'pipe'">
          <div class="detail-row">
            <span class="label">所在道路</span>
            <span class="value font-bold">{{ activeDetail.properties.street }}</span>
          </div>
          <div class="detail-row">
            <span class="label">管网编号</span>
            <span class="value code">{{ activeDetail.properties.id }}</span>
          </div>
          <div class="detail-row">
            <span class="label">管径规格</span>
            <span class="value font-bold">{{ activeDetail.properties.diameter }}</span>
          </div>
          <div class="detail-row">
            <span class="label">管线材质</span>
            <span class="value">{{ activeDetail.properties.material }}</span>
          </div>
          <div class="detail-row">
            <span class="label">瞬时排水量</span>
            <span class="value text-info">{{ activeDetail.properties.flowRate }}</span>
          </div>
          <div class="detail-row">
            <span class="label">负荷状态</span>
            <span class="value">
              <n-badge :value="activeDetail.properties.status" :type="activeDetail.properties.status === '正常' ? 'success' : activeDetail.properties.status === '预警' ? 'warning' : 'error'" />
            </span>
          </div>
        </template>

        <!-- 行政边界展示 -->
        <template v-if="activeDetail.type === 'boundary'">
          <div class="detail-row">
            <span class="label">所属名称</span>
            <span class="value font-bold">{{ activeDetail.properties.name }}</span>
          </div>
          <div class="detail-row">
            <span class="label">行政编码</span>
            <span class="value code">{{ activeDetail.properties.code }}</span>
          </div>
          <div class="detail-row">
            <span class="label">估算面积</span>
            <span class="value">{{ activeDetail.properties.area }}</span>
          </div>
          <div class="detail-row">
            <span class="label">常住人口</span>
            <span class="value">{{ activeDetail.properties.population }}</span>
          </div>
          <div class="detail-row">
            <span class="label">直属防汛机构</span>
            <span class="value text-primary">{{ activeDetail.properties.manager }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 全局页面布局 */
.webgis-layout {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  font-family: system-ui, -apple-system, sans-serif;
}

/* 地图视口撑满全屏 */
.map-viewport {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

/* 浮动卡片通用样式 (继承 CSS 变量与主题切换适配) */
.floating-card {
  position: absolute;
  z-index: 10;
  background: var(--bg-card, rgba(255, 255, 255, 0.92));
  border: 1px solid var(--border-color, #e5e4e7);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 12px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  backdrop-filter: blur(8px);
}

/* 深色模式下的浮动卡片变量 */
.dark-theme .floating-card {
  background: rgba(24, 24, 28, 0.9);
  border: 1px solid #333339;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.45);
  --bg-card: rgba(24, 24, 28, 0.9);
  --border-color: #333339;
}

/* 1. 左上角搜索框 */
.search-panel {
  top: 15px;
  left: 15px;
  width: auto;
}

.search-results-list {
  margin-top: 8px;
  max-height: 320px;
  overflow-y: auto;
  border-radius: 4px;
  border: 1px solid var(--border-color, #e0e0e0);
  background: var(--bg-card, #ffffff);
}

.search-item-row {
  padding: 8px 12px !important;
  cursor: pointer;
}

.search-item-title {
  font-weight: bold;
  font-size: 13px;
  color: var(--text-title, #1f2225);
}
.dark-theme .search-item-title {
  color: #f3f4f6;
}

.search-item-address {
  font-size: 11px;
  color: #8c8c8c;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.meta-coords {
  font-size: 10px;
  color: #b2b2b2;
  font-family: monospace;
}

/* 2. 右上角工具条 */
.toolbar-panel {
  top: 15px;
  right: 15px;
  padding: 8px 12px;
}

/* 3. 右侧中部图层控制面板 */
.layer-control-panel {
  top: 75px;
  right: 15px;
  width: 220px;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-color, #eee);
  padding-bottom: 6px;
  font-size: 13px;
  font-weight: bold;
  color: var(--text-title, #1f2225);
}
.dark-theme .panel-header {
  color: #ffffff;
}

.panel-title {
  letter-spacing: 0.5px;
}

.layer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.layer-card {
  padding: 8px;
  background: rgba(120, 120, 120, 0.05);
  border-radius: 6px;
}

.layer-main-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
}

.layer-name {
  color: var(--text-title, #333);
}
.dark-theme .layer-name {
  color: #eee;
}

.layer-opacity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #8c8c8c;
}

.opacity-value {
  font-family: monospace;
  width: 32px;
  text-align: right;
}

.empty-layers {
  text-align: center;
  font-size: 11px;
  color: #8c8c8c;
  padding: 12px 0;
}

/* 4. 底部偏左量测面板 */
.measure-panel {
  bottom: 15px;
  left: 15px;
  width: 320px;
}

.measure-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: bold;
  margin-bottom: 8px;
  color: var(--text-title, #333);
}
.dark-theme .measure-header {
  color: #fff;
}

.measure-title {
  letter-spacing: 0.5px;
}

.measure-tip {
  font-size: 11px;
  color: #ff4d4f;
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  line-height: 1.4;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background: #ff4d4f;
  border-radius: 50%;
  animation: pulse 1.2s infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0% { transform: scale(0.9); opacity: 0.4; }
  50% { transform: scale(1.2); opacity: 1; }
  100% { transform: scale(0.9); opacity: 0.4; }
}

/* 5. 底部右侧要素属性详情副面板 */
.detail-sidebar {
  bottom: 15px;
  right: 15px;
  width: 250px;
  animation: slideUp 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border-color, #eee);
  padding-bottom: 6px;
}

.detail-title {
  font-size: 12px;
  font-weight: bold;
  color: var(--text-title, #333);
}
.dark-theme .detail-title {
  color: #fff;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  line-height: 1.4;
}

.detail-row .label {
  color: #8c8c8c;
}

.detail-row .value {
  text-align: right;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
  color: var(--text-title, #333);
}
.dark-theme .detail-row .value {
  color: #eee;
}

.font-bold {
  font-weight: bold !important;
}

.text-warning {
  color: #f5222d !important;
}

.text-info {
  color: #1890ff !important;
}

.detail-row .code {
  font-family: monospace;
}

.detail-row .date {
  color: #999;
  font-size: 10px;
}

.detail-row .desc {
  font-size: 10px;
  color: #666;
}
.dark-theme .detail-row .desc {
  color: #aaa;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes bounce {
  from { transform: translateY(0); }
  to { transform: translateY(-5px); }
}
</style>

<style>
/* 统一覆写 MapLibre GL 弹窗样式使其匹配系统明暗主题 */
.maplibre-theme-popup .maplibregl-popup-content {
  background: rgba(255, 255, 255, 0.95) !important;
  color: #1f2225 !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15) !important;
  padding: 12px 14px !important;
  border: 1px solid #e2e8f0;
}

.dark-theme .maplibre-theme-popup .maplibregl-popup-content {
  background: rgba(24, 24, 28, 0.96) !important;
  color: #f3f4f6 !important;
  border: 1px solid #333339 !important;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5) !important;
}

.maplibre-theme-popup .maplibregl-popup-tip {
  border-top-color: rgba(255, 255, 255, 0.95) !important;
  border-bottom-color: rgba(255, 255, 255, 0.95) !important;
}

.dark-theme .maplibre-theme-popup .maplibregl-popup-tip {
  border-top-color: rgba(24, 24, 28, 0.96) !important;
  border-bottom-color: rgba(24, 24, 28, 0.96) !important;
}

/* 测量圆点和线样式在全局的自定义支持 */
.search-pin-marker {
  transform: translate(-12px, -24px);
}
</style>
