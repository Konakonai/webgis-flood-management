import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as turf from '@turf/turf'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { apiRequest, AUTH_TOKEN_KEY, rawRequest } from '../services/api'

export interface ResourcePoint {
  id: string
  name: string
  type: 'resource'
  lng: number
  lat: number
  address: string
  status: '充足' | '紧张'
  details: string
}

export interface PumpStation {
  id: string
  resourceId: number
  name: string
  type: 'pump'
  lng: number
  lat: number
  address: string
  vehicle: string
  contact: string
  status: '空闲' | '已派发'
}

export interface AlarmEvent {
  id: string
  backendId: number
  reporter: string
  phone: string
  description: string
  severity: 'low' | 'medium' | 'high'
  lng: number
  lat: number
  status: '待派单' | '处置中' | '已完成' | '已驳回'
  time: string
  assignedPumpId?: string
  arrivedAt?: string
  routeGeometry?: [number, number][]
  routeDistance?: number
  routeDuration?: number
}

interface WorkOrder {
  id: number
  trackingCode: string
  reporterName?: string
  reporterPhone?: string
  description?: string
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  lng: number
  lat: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'REJECTED'
  assignedResourceId?: number
  arrivedAt?: string
  routeDistance?: number
  routeDuration?: number
  createdAt: string
}

interface Page<T> { records: T[] }
interface OsrmRouteResponse {
  code: string
  routes: Array<{ geometry: { coordinates: [number, number][] }; distance: number; duration: number }>
}

const statusLabel = (status: WorkOrder['status']): AlarmEvent['status'] => ({
  PENDING: '待派单',
  PROCESSING: '处置中',
  COMPLETED: '已完成',
  REJECTED: '已驳回'
}[status] as AlarmEvent['status'])

const severityLabel = (priority: WorkOrder['priority']): AlarmEvent['severity'] =>
  priority === 'URGENT' || priority === 'HIGH' ? 'high' : priority === 'NORMAL' ? 'medium' : 'low'

const priorityValue = (severity: AlarmEvent['severity']) =>
  severity === 'high' ? 'URGENT' : severity === 'medium' ? 'NORMAL' : 'LOW'

const formatTime = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

export const useEmergencyStore = defineStore('emergency', () => {
  const resourceList = ref<ResourcePoint[]>([])
  const pumpStations = ref<PumpStation[]>([])
  const alarmEvents = ref<AlarmEvent[]>([])
  const selectedAlarmId = ref<string | null>(null)
  const loading = ref(false)
  let stompClient: Client | null = null
  const routeCache = new Map<number, [number, number][]>()

  const toAlarm = (order: WorkOrder): AlarmEvent => {
    const assignedPump = pumpStations.value.find((pump) => pump.resourceId === order.assignedResourceId)
    return {
      id: order.trackingCode || `WO-${order.id}`,
      backendId: order.id,
      reporter: order.reporterName || '公众用户',
      phone: order.reporterPhone || '',
      description: order.description || '未填写现场描述',
      severity: severityLabel(order.priority),
      lng: order.lng,
      lat: order.lat,
      status: statusLabel(order.status),
      time: formatTime(order.createdAt),
      assignedPumpId: assignedPump?.id,
      arrivedAt: order.arrivedAt,
      routeGeometry: routeCache.get(order.id),
      routeDistance: order.routeDistance,
      routeDuration: order.routeDuration
    }
  }

  const fetchResources = async () => {
    resourceList.value = await rawRequest<ResourcePoint[]>('/api/resources')
  }

  const fetchStations = async () => {
    pumpStations.value = await rawRequest<PumpStation[]>('/api/stations')
  }

  const fetchWorkOrders = async () => {
    const page = await apiRequest<Page<WorkOrder>>('/api/work-orders?page=1&size=100')
    alarmEvents.value = page.records.map(toAlarm)
    if (selectedAlarmId.value && !alarmEvents.value.some((item) => item.id === selectedAlarmId.value)) {
      selectedAlarmId.value = null
    }
  }

  const refresh = async () => {
    loading.value = true
    try {
      await Promise.all([fetchResources(), fetchStations()])
      await fetchWorkOrders()
    } finally {
      loading.value = false
    }
  }

  const addAlarmEvent = async (event: Omit<AlarmEvent, 'id' | 'backendId' | 'status' | 'time'>) => {
    const order = await apiRequest<WorkOrder>('/api/work-orders', {
      method: 'POST',
      body: JSON.stringify({
        type: 'REPORT',
        title: `人工警情登记：${event.reporter}`,
        description: event.description,
        priority: priorityValue(event.severity),
        reporterName: event.reporter,
        reporterPhone: event.phone,
        lng: event.lng,
        lat: event.lat
      })
    })
    const alarm = toAlarm(order)
    alarmEvents.value.unshift(alarm)
    return alarm
  }

  const findNearestPump = (lng: number, lat: number): PumpStation | null => {
    const available = pumpStations.value.filter((pump) => pump.status === '空闲')
    if (available.length === 0) return null
    const alarmPoint = turf.point([lng, lat])
    return available.reduce<PumpStation | null>((nearest, pump) => {
      if (!nearest) return pump
      const current = turf.distance(alarmPoint, turf.point([pump.lng, pump.lat]), { units: 'kilometers' })
      const best = turf.distance(alarmPoint, turf.point([nearest.lng, nearest.lat]), { units: 'kilometers' })
      return current < best ? pump : nearest
    }, null)
  }

  const planRoute = async (start: [number, number], end: [number, number]) => {
    const coordinates = `${start.join(',')};${end.join(',')}`
    const result = await rawRequest<OsrmRouteResponse>(
      `/osrm/route/v1/driving/${coordinates}?overview=full&geometries=geojson&steps=false`
    )
    const route = result.routes?.[0]
    if (result.code !== 'Ok' || !route) throw new Error('OSRM 暂时无法生成行车路线')
    return { geometry: route.geometry.coordinates, distance: route.distance, duration: route.duration }
  }

  const dispatchPump = async (eventId: string, pumpId: string) => {
    const event = alarmEvents.value.find((item) => item.id === eventId)
    const pump = pumpStations.value.find((item) => item.id === pumpId)
    if (!event || !pump) throw new Error('工单或泵车不存在')
    if (pump.status !== '空闲') throw new Error('该泵车当前不可用')

    const route = await planRoute([pump.lng, pump.lat], [event.lng, event.lat])
    const order = await apiRequest<WorkOrder>(`/api/work-orders/${event.backendId}/dispatch`, {
      method: 'POST',
      body: JSON.stringify({
        resourceId: pump.resourceId,
        handlerName: pump.contact || pump.name,
        routeDistance: route.distance,
        routeDuration: route.duration
      })
    })
    routeCache.set(order.id, route.geometry)
    await Promise.all([fetchStations(), fetchWorkOrders()])
  }

  const markArrived = async (eventId: string) => {
    const event = alarmEvents.value.find((item) => item.id === eventId)
    if (!event) throw new Error('工单不存在')
    await apiRequest<WorkOrder>(`/api/work-orders/${event.backendId}/arrive`, { method: 'POST' })
    await fetchWorkOrders()
  }

  const updateEventStatus = async (eventId: string, status: '已完成' | '已驳回') => {
    const event = alarmEvents.value.find((item) => item.id === eventId)
    if (!event) throw new Error('工单不存在')
    await apiRequest<WorkOrder>(`/api/work-orders/${event.backendId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({
        status: status === '已完成' ? 'COMPLETED' : 'REJECTED',
        result: status === '已完成' ? '现场处置完成' : undefined,
        note: status === '已完成' ? '调度台确认处置完成' : '调度台驳回工单'
      })
    })
    routeCache.delete(event.backendId)
    await Promise.all([fetchStations(), fetchWorkOrders()])
  }

  const connectWorkOrderUpdates = () => {
    if (stompClient?.active) return
    const token = localStorage.getItem(AUTH_TOKEN_KEY)
    if (!token) return
    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        stompClient?.subscribe('/topic/work-orders', () => {
          void Promise.all([fetchStations(), fetchWorkOrders()])
        })
      }
    })
    stompClient.activate()
  }

  const disconnectWorkOrderUpdates = () => {
    if (stompClient) void stompClient.deactivate()
    stompClient = null
  }

  return {
    resourceList,
    pumpStations,
    alarmEvents,
    selectedAlarmId,
    loading,
    refresh,
    fetchResources,
    fetchStations,
    fetchWorkOrders,
    addAlarmEvent,
    findNearestPump,
    dispatchPump,
    markArrived,
    updateEventStatus,
    connectWorkOrderUpdates,
    disconnectWorkOrderUpdates
  }
})
