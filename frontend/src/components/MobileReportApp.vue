<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  NButton,
  NConfigProvider,
  NInput,
  NInputNumber,
  NMessageProvider,
  NUpload,
  createDiscreteApi
} from 'naive-ui'
import { Camera, Crosshair, LocateFixed, MapPin, Send, X } from 'lucide-vue-next'
import { Popup } from 'maplibre-gl'
import type { GeoJSONSource } from 'maplibre-gl'
import type { Feature, FeatureCollection, Point } from 'geojson'

import MapContainer from './MapContainer.vue'
import { useMapStore } from '../store/map'
import { useTheme } from '../composables/useTheme'

interface MobileReport {
  id: string
  lng: number
  lat: number
  depth: number
  description: string
  image?: string
  createdAt: string
}

type ReportFeature = Feature<Point, {
  id: string
  depth: number
  description: string
  createdAt: string
  hasImage: boolean
}>

const STORAGE_KEY = 'mobile-water-reports'
const SOURCE_ID = 'mobile-reports-source'
const LAYER_ID = 'mobile-reports-layer'

const mapStore = useMapStore()
const { naiveTheme, setTheme } = useTheme()
setTheme('light')

const { message } = createDiscreteApi(['message'], {
  configProviderProps: computed(() => ({
    theme: naiveTheme.value
  }))
})

const reportLng = ref(117.1848)
const reportLat = ref(34.2610)
const reportDepth = ref(20)
const reportDescription = ref('')
const imagePreview = ref('')
const reports = ref<MobileReport[]>([])
const isLocating = ref(false)
const isSubmitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const reportLngText = computed(() => reportLng.value.toFixed(6))
const reportLatText = computed(() => reportLat.value.toFixed(6))
const latestReport = computed(() => reports.value.at(-1))

const escapeHtml = (value: string) => {
  const div = document.createElement('div')
  div.textContent = value
  return div.innerHTML
}

const saveReports = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(reports.value))
}

const loadReports = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    reports.value = raw ? JSON.parse(raw) : []
  } catch {
    reports.value = []
  }
}

const reportFeatures = (): ReportFeature[] => {
  return reports.value.map((report) => ({
    type: 'Feature',
    geometry: {
      type: 'Point',
      coordinates: [report.lng, report.lat]
    },
    properties: {
      id: report.id,
      depth: report.depth,
      description: report.description || '现场积水',
      createdAt: report.createdAt,
      hasImage: Boolean(report.image)
    }
  }))
}

const renderReportLayer = () => {
  const map = mapStore.mapInstance
  if (!map || !mapStore.isMapLoaded) return

  const geojson: FeatureCollection<Point, ReportFeature['properties']> = {
    type: 'FeatureCollection',
    features: reportFeatures()
  }

  if (map.getSource(SOURCE_ID)) {
    const source = map.getSource(SOURCE_ID) as GeoJSONSource
    source.setData(geojson)
    return
  }

  map.addSource(SOURCE_ID, {
    type: 'geojson',
    data: geojson
  })

  map.addLayer({
    id: LAYER_ID,
    type: 'circle',
    source: SOURCE_ID,
    paint: {
      'circle-color': [
        'case',
        ['>=', ['get', 'depth'], 40],
        '#ef4444',
        '#2563eb'
      ],
      'circle-radius': 10,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 3,
      'circle-opacity': 0.95
    }
  })

  map.on('click', LAYER_ID, (event) => {
    const feature = event.features?.[0]
    if (!feature) return

    const id = String(feature.properties?.id || '')
    const report = reports.value.find((item) => item.id === id)
    const coordinates = (feature.geometry as Point).coordinates.slice() as [number, number]
    const description = escapeHtml(report?.description || '现场积水')
    const time = report ? new Date(report.createdAt).toLocaleString('zh-CN') : ''
    const image = report?.image
      ? `<img class="mobile-popup-image" src="${report.image}" alt="上报图片" />`
      : ''

    new Popup({ className: 'custom-webgis-popup mobile-report-popup' })
      .setLngLat(coordinates)
      .setHTML(`
        <div class="map-popup-card">
          <div class="popup-title">移动端积水上报</div>
          ${image}
          <div class="popup-item"><strong>积水深度:</strong><span>${report?.depth ?? feature.properties?.depth} cm</span></div>
          <div class="popup-item"><strong>现场描述:</strong><span>${description}</span></div>
          <div class="popup-item"><strong>上报时间:</strong><span>${time}</span></div>
        </div>
      `)
      .addTo(map)
  })

  map.on('mouseenter', LAYER_ID, () => {
    map.getCanvas().style.cursor = 'pointer'
  })
  map.on('mouseleave', LAYER_ID, () => {
    map.getCanvas().style.cursor = ''
  })
}

const flyToReportLocation = () => {
  const map = mapStore.mapInstance
  if (!map) return

  map.flyTo({
    center: [reportLng.value, reportLat.value],
    zoom: Math.max(map.getZoom(), 15),
    essential: true
  })
}

const useCurrentLocation = () => {
  if (!navigator.geolocation) {
    message.error('当前设备不支持定位')
    return
  }

  isLocating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      reportLng.value = position.coords.longitude
      reportLat.value = position.coords.latitude
      flyToReportLocation()
      isLocating.value = false
      message.success('已获取当前位置')
    },
    () => {
      isLocating.value = false
      message.error('定位失败，请检查定位权限')
    },
    {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 30000
    }
  )
}

const pickMapCenter = () => {
  const map = mapStore.mapInstance
  if (!map) {
    message.warning('地图尚未加载完成')
    return
  }

  const center = map.getCenter()
  reportLng.value = center.lng
  reportLat.value = center.lat
  message.success('已拾取地图中心坐标')
}

const openImagePicker = () => {
  fileInputRef.value?.click()
}

const handleImageChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    imagePreview.value = String(reader.result || '')
  }
  reader.onerror = () => {
    message.error('图片读取失败')
  }
  reader.readAsDataURL(file)
}

const clearImage = () => {
  imagePreview.value = ''
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const submitReport = async () => {
  if (!Number.isFinite(reportLng.value) || !Number.isFinite(reportLat.value)) {
    message.error('请先获取或拾取上报位置')
    return
  }

  isSubmitting.value = true
  const report: MobileReport = {
    id: `MR-${Date.now()}`,
    lng: reportLng.value,
    lat: reportLat.value,
    depth: reportDepth.value,
    description: reportDescription.value.trim(),
    image: imagePreview.value || undefined,
    createdAt: new Date().toISOString()
  }

  reports.value.push(report)
  saveReports()
  renderReportLayer()
  flyToReportLocation()

  await nextTick()
  reportDescription.value = ''
  reportDepth.value = 20
  clearImage()
  isSubmitting.value = false
  message.success('上报成功，已在地图上标记')
}

onMounted(() => {
  loadReports()
  renderReportLayer()
})

watch(
  () => mapStore.isMapLoaded,
  (loaded) => {
    if (loaded) renderReportLayer()
  }
)

watch(reports, saveReports, { deep: true })
</script>

<template>
  <n-config-provider :theme="naiveTheme">
    <n-message-provider>
      <main class="mobile-report-app light-theme">
        <section class="mobile-map-shell">
          <MapContainer :show-business-layers="false" />
          <div class="center-reticle" aria-hidden="true">
            <MapPin :size="34" :stroke-width="2.6" />
          </div>
          <div class="map-status-pill">
            <span>经度 {{ reportLngText }}</span>
            <span>纬度 {{ reportLatText }}</span>
          </div>
        </section>

        <section class="report-sheet" aria-label="积水上报表单">
          <header class="sheet-header">
            <div>
              <p class="eyebrow">移动端积水上报</p>
              <h1>现场情况</h1>
            </div>
            <div v-if="latestReport" class="local-count">
              已保存 {{ reports.length }} 条
            </div>
          </header>

          <div class="quick-actions">
            <n-button
              type="primary"
              secondary
              :loading="isLocating"
              @click="useCurrentLocation"
            >
              <template #icon>
                <LocateFixed :size="18" />
              </template>
              当前位置
            </n-button>
            <n-button secondary @click="pickMapCenter">
              <template #icon>
                <Crosshair :size="18" />
              </template>
              拾取中心
            </n-button>
          </div>

          <div class="field-grid">
            <label class="field-card">
              <span>经度</span>
              <strong>{{ reportLngText }}</strong>
            </label>
            <label class="field-card">
              <span>纬度</span>
              <strong>{{ reportLatText }}</strong>
            </label>
          </div>

          <label class="form-field">
            <span>积水深度（cm）</span>
            <n-input-number
              v-model:value="reportDepth"
              :min="0"
              :max="300"
              :step="5"
              button-placement="both"
              size="large"
            />
          </label>

          <label class="form-field">
            <span>现场描述</span>
            <n-input
              v-model:value="reportDescription"
              type="textarea"
              placeholder="例如：路口积水较深，车辆通行缓慢"
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
          </label>

          <div class="photo-section">
            <input
              ref="fileInputRef"
              class="native-file-input"
              type="file"
              accept="image/*"
              capture="environment"
              @change="handleImageChange"
            />
            <n-upload :show-file-list="false" :custom-request="() => {}">
              <n-button strong secondary @click.prevent="openImagePicker">
                <template #icon>
                  <Camera :size="18" />
                </template>
                拍照或上传图片
              </n-button>
            </n-upload>

            <div v-if="imagePreview" class="image-preview">
              <img :src="imagePreview" alt="上报图片预览" />
              <button class="clear-image-button" type="button" aria-label="移除图片" @click="clearImage">
                <X :size="18" />
              </button>
            </div>
          </div>

          <n-button
            type="primary"
            size="large"
            block
            :loading="isSubmitting"
            class="submit-button"
            @click="submitReport"
          >
            <template #icon>
              <Send :size="18" />
            </template>
            提交上报
          </n-button>
        </section>
      </main>
    </n-message-provider>
  </n-config-provider>
</template>

<style scoped>
.mobile-report-app {
  width: 100vw;
  height: 100vh;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  overflow: hidden;
  background: #f7f9fc;
  color: #111827;
}

.mobile-map-shell {
  position: relative;
  min-height: 0;
}

.center-reticle {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 5;
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  color: #ef4444;
  transform: translate(-50%, -100%);
  filter: drop-shadow(0 3px 8px rgba(0, 0, 0, 0.28));
  pointer-events: none;
}

.map-status-pill {
  position: absolute;
  top: max(12px, env(safe-area-inset-top));
  left: 12px;
  right: 12px;
  z-index: 5;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.report-sheet {
  z-index: 10;
  display: flex;
  max-height: 58vh;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px calc(16px + env(safe-area-inset-bottom));
  overflow-y: auto;
  border-top: 1px solid #e5e7eb;
  border-radius: 8px 8px 0 0;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -10px 28px rgba(15, 23, 42, 0.14);
}

.sheet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 3px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
}

.sheet-header h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.15;
  letter-spacing: 0;
}

.local-count {
  flex: 0 0 auto;
  padding: 5px 8px;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.quick-actions,
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.field-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  padding: 9px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.field-card span,
.form-field span {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.field-card strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.photo-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.native-file-input {
  display: none;
}

.image-preview {
  position: relative;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.image-preview img {
  display: block;
  width: 100%;
  max-height: 180px;
  object-fit: cover;
}

.clear-image-button {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: white;
}

.submit-button {
  flex: 0 0 auto;
  font-weight: 700;
}

:global(.mobile-report-popup .mobile-popup-image) {
  width: 100%;
  max-height: 150px;
  margin-bottom: 8px;
  border-radius: 6px;
  object-fit: cover;
}

@media (min-width: 640px) {
  .mobile-report-app {
    grid-template-rows: minmax(0, 1fr) auto;
  }

  .report-sheet {
    width: min(520px, calc(100vw - 32px));
    max-height: 52vh;
    margin: 0 auto;
    border: 1px solid #e5e7eb;
    border-bottom: 0;
  }
}
</style>
