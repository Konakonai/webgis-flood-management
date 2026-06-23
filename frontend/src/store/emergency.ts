// src/store/emergency.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as turf from '@turf/turf'

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
  reporter: string
  phone: string
  description: string
  severity: 'low' | 'medium' | 'high'
  lng: number
  lat: number
  status: '待派单' | '处置中' | '已完成'
  time: string
  assignedPumpId?: string
  routeGeometry?: [number, number][]
  routeDistance?: number // 米
  routeDuration?: number // 秒
}

export const useEmergencyStore = defineStore('emergency', () => {
  const resourceList = ref<ResourcePoint[]>([])
  const pumpStations = ref<PumpStation[]>([])
  const alarmEvents = ref<AlarmEvent[]>([
    // 初始化两个默认的模拟报警工单
    {
      id: 'A001',
      reporter: '张先生',
      phone: '13588889999',
      description: '大同街交叉路口桥洞下积水严重，已过膝盖，车辆无法通行。',
      severity: 'high',
      lng: 117.192,
      lat: 34.271,
      status: '待派单',
      time: '2026-06-23 15:10'
    },
    {
      id: 'A002',
      reporter: '刘女士',
      phone: '13766667777',
      description: '云龙湖东路一侧路面有轻微积水，排水井盖反水。',
      severity: 'low',
      lng: 117.155,
      lat: 34.225,
      status: '处置中',
      time: '2026-06-23 15:25',
      assignedPumpId: 'P002',
      routeGeometry: [
        [117.152, 34.185],
        [117.152, 34.205],
        [117.155, 34.215],
        [117.155, 34.225]
      ],
      routeDistance: 4500,
      routeDuration: 540
    }
  ])

  // 选中的报警事件ID
  const selectedAlarmId = ref<string | null>(null)

  // 1. 获取防汛物资点列表
  const fetchResources = async () => {
    try {
      const res = await fetch('/api/resources')
      if (res.ok) {
        resourceList.value = await res.json()
      }
    } catch (e) {
      console.error('获取防汛物资点失败:', e)
    }
  }

  // 2. 获取泵车驻地列表
  const fetchStations = async () => {
    try {
      const res = await fetch('/api/stations')
      if (res.ok) {
        pumpStations.value = await res.json()
      }
    } catch (e) {
      console.error('获取泵车驻地失败:', e)
    }
  }

  // 3. 注册新报警事件
  const addAlarmEvent = (event: Omit<AlarmEvent, 'id' | 'status' | 'time'>) => {
    const newEvent: AlarmEvent = {
      ...event,
      id: `A_${Date.now()}`,
      status: '待派单',
      time: new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
    }
    alarmEvents.value.unshift(newEvent)
    return newEvent
  }

  // 4. 使用 Turf.js 计算最近的泵车驻点
  const findNearestPump = (lng: number, lat: number): PumpStation | null => {
    if (pumpStations.value.length === 0) return null
    let nearest: PumpStation | null = null
    let minDist = Infinity
    
    const alarmPt = turf.point([lng, lat])
    
    pumpStations.value.forEach(pump => {
      // 只有“空闲”的泵车，或者就算都已派发也选取绝对物理最近的
      const pumpPt = turf.point([pump.lng, pump.lat])
      const dist = turf.distance(alarmPt, pumpPt, { units: 'kilometers' })
      if (dist < minDist) {
        minDist = dist
        nearest = pump
      }
    })
    
    return nearest
  }

  // 5. 路径生成器 (模拟 OSRM)
  const generateRoute = (start: [number, number], end: [number, number]) => {
    const [x1, y1] = start
    const [x2, y2] = end
    
    // 生成带折弯的多段线 coordinates，增加折角逼真感
    const dx = x2 - x1
    const dy = y2 - y1
    
    const p1: [number, number] = [x1 + dx * 0.3, y1 + dy * 0.1]
    const p2: [number, number] = [x1 + dx * 0.7, y1 + dy * 0.9]
    
    const coordinates = [start, p1, p2, end]
    
    const line = turf.lineString(coordinates)
    const distKm = turf.length(line, { units: 'kilometers' })
    
    return {
      geometry: coordinates,
      distance: Math.round(distKm * 1000), // 米
      duration: Math.round(distKm * 120) // 秒 (按平均30公里/小时车速计算)
    }
  }

  // 6. 派单指令
  const dispatchPump = (eventId: string, pumpId: string) => {
    const event = alarmEvents.value.find(e => e.id === eventId)
    const pump = pumpStations.value.find(p => p.id === pumpId)
    
    if (event && pump) {
      event.assignedPumpId = pumpId
      event.status = '处置中'
      pump.status = '已派发'
      
      // 生成路径规划
      const route = generateRoute([pump.lng, pump.lat], [event.lng, event.lat])
      event.routeGeometry = route.geometry as [number, number][]
      event.routeDistance = route.distance
      event.routeDuration = route.duration
    }
  }

  // 7. 更新工单状态
  const updateEventStatus = (eventId: string, status: AlarmEvent['status']) => {
    const event = alarmEvents.value.find(e => e.id === eventId)
    if (event) {
      event.status = status
      
      // 如果完成了，释放泵车状态为“空闲”
      if (status === '已完成' && event.assignedPumpId) {
        const pump = pumpStations.value.find(p => p.id === event.assignedPumpId)
        if (pump) {
          pump.status = '空闲'
        }
      }
    }
  }

  return {
    resourceList,
    pumpStations,
    alarmEvents,
    selectedAlarmId,
    fetchResources,
    fetchStations,
    addAlarmEvent,
    findNearestPump,
    generateRoute,
    dispatchPump,
    updateEventStatus
  }
})
